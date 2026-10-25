package com.example.pathy.tracking

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.robolectric.RuntimeEnvironment
import com.example.pathy.core.track.GpsFix
import com.example.pathy.core.track.RecorderState
import com.example.pathy.core.track.SportType
import com.example.pathy.core.track.TrackRecorderCore
import com.example.pathy.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The recording must survive the app being killed: what is on disk has to rebuild the same outing.
 * These tests run the real Room database (in memory) under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingStorageTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecordingRepository

    @Before
    fun open() {
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = RecordingRepository(db.recordingDao())
    }

    @After
    fun close() = db.close()

    /** A straight walk north at 1.4 m/s, one fix per 2 seconds. */
    private fun fixes(count: Int, fromIndex: Int = 0, startMs: Long = 1_000_000L) = (fromIndex until fromIndex + count).map { i ->
        GpsFix(
            lat = 32.0 + i * 2.8 / 111_320.0,
            lon = 35.0,
            altitudeM = 100.0 + i * 0.1,
            accuracyM = 5f,
            speedMps = 1.4f,
            timeMs = startMs + i * 2_000L
        )
    }

    private fun record(core: TrackRecorderCore, id: String, seqStart: Int, fixes: List<GpsFix>): Int = runBlocking {
        var seq = seqStart
        for (fix in fixes) core.onFix(fix)?.let { repository.append(id, seq++, it) }
        seq
    }

    @Test
    fun anOutingRebuiltFromTheDatabaseMatchesTheOneThatWasInMemory() = runBlocking {
        val entity = repository.begin(SportType.HIKING, startedAtMs = 1_000_000L)
        val live = TrackRecorderCore(SportType.HIKING).also { it.start(1_000_000L) }
        record(live, entity.id, 0, fixes(120))

        // The process dies here. A fresh recorder is built from what was saved.
        val loaded = repository.loadActive()!!
        val rebuilt = TrackRecorderCore(loaded.sport)
        rebuilt.restoreClock(loaded.recording.startedAtMs, loaded.recording.pausedTotalMs, RecorderState.RECORDING)
        loaded.points.forEach { rebuilt.replay(it) }

        val now = 1_000_000L + 240_000L
        val a = live.stats(now)
        val b = rebuilt.stats(now)
        assertEquals(live.points.size, rebuilt.points.size)
        assertEquals(a.distanceM, b.distanceM, 0.5)
        assertEquals(a.elapsedMs, b.elapsedMs)
        assertEquals(a.elevationGainM, b.elevationGainM, 0.5)
    }

    @Test
    fun recordingContinuesAfterARestartWithTheNextPointNumber() = runBlocking {
        val entity = repository.begin(SportType.HIKING, startedAtMs = 1_000_000L)
        val first = TrackRecorderCore(SportType.HIKING).also { it.start(1_000_000L) }
        val next = record(first, entity.id, 0, fixes(30))
        assertEquals(next, repository.nextSeq(entity.id))

        val loaded = repository.loadActive()!!
        val second = TrackRecorderCore(loaded.sport)
        second.restoreClock(loaded.recording.startedAtMs, loaded.recording.pausedTotalMs, RecorderState.RECORDING)
        loaded.points.forEach { second.replay(it) }
        record(second, entity.id, repository.nextSeq(entity.id), fixes(30, fromIndex = 30))

        val all = repository.load(entity.id)!!.points
        assertEquals(second.points.size, all.size)
        assertTrue("points stay in time order", all.zipWithNext().all { (x, y) -> y.timeMs > x.timeMs })
    }

    @Test
    fun aPauseThatWasRunningWhenTheAppDiedKeepsItsOriginalStart() = runBlocking {
        val entity = repository.begin(SportType.HIKING, startedAtMs = 1_000_000L)
        val core = TrackRecorderCore(SportType.HIKING).also { it.start(1_000_000L) }
        record(core, entity.id, 0, fixes(10))
        core.pause(1_100_000L)
        repository.saveClock(entity.id, core.state, core.pausedMs, core.pauseStartedAtMs)

        val loaded = repository.loadActive()!!
        assertEquals(RecorderState.PAUSED.name, loaded.recording.recorderState)
        val rebuilt = TrackRecorderCore(loaded.sport)
        rebuilt.restoreClock(
            loaded.recording.startedAtMs, loaded.recording.pausedTotalMs, RecorderState.PAUSED,
            loaded.recording.pauseStartedMs, nowMs = 1_500_000L
        )
        loaded.points.forEach { rebuilt.replay(it) }
        // 100 s of walking, then paused: the 400 s since the pause began must not count as time spent.
        assertEquals(100_000L, rebuilt.stats(1_500_000L).elapsedMs)
    }

    @Test
    fun finishedOutingsWaitForUploadAndAreCleanedUpAfterwards() = runBlocking {
        val entity = repository.begin(SportType.WALKING, startedAtMs = 1_000_000L)
        val core = TrackRecorderCore(SportType.WALKING).also { it.start(1_000_000L) }
        record(core, entity.id, 0, fixes(40))
        assertNull("nothing to upload while recording", repository.pendingUploads().firstOrNull())

        val end = 1_000_000L + 80_000L
        repository.finish(entity.id, core.stop(end), end)
        assertNull("no active outing once finished", repository.activeId())
        val pending = repository.pendingUploads()
        assertEquals(listOf(entity.id), pending.map { it.id })
        assertTrue(pending.single().distanceM > 50.0)

        repository.countAttempt(entity.id)
        assertEquals(1, repository.load(entity.id)!!.recording.uploadAttempts)

        repository.markUploaded(entity.id)
        assertTrue(repository.pendingUploads().isEmpty())

        // Recently uploaded outings stay; old ones go, and their points go with them.
        repository.pruneUploaded(days = 30, nowMs = end + 86_400_000L)
        assertNotNull(repository.load(entity.id))
        repository.pruneUploaded(days = 30, nowMs = end + 31 * 86_400_000L)
        assertNull(repository.load(entity.id))
        assertTrue(db.recordingDao().points(entity.id).isEmpty())
    }

    @Test
    fun theVersion1DatabaseUpgradesWithoutLosingRecentSearches() {
        val context = RuntimeEnvironment.getApplication() as Context
        val name = "migration-test.db"
        context.deleteDatabase(name)

        // The database as it was shipped: only the recent searches table.
        val v1 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `recent_searches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `timestamp` INTEGER NOT NULL)"
                        )
                        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        )
        v1.writableDatabase.use { db ->
            db.execSQL("INSERT INTO recent_searches (name, latitude, longitude, timestamp) VALUES ('Carmel', 32.7, 35.0, 1)")
        }
        v1.close()

        // Opening with the current schema runs the migration; Room validates every table and index.
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val searches = runBlocking { upgraded.recentSearchDao().getAll() }
            assertEquals(listOf("Carmel"), searches.map { it.name })
            runBlocking {
                val repo = RecordingRepository(upgraded.recordingDao())
                val created = repo.begin(SportType.HIKING, startedAtMs = 5L)
                assertEquals(created.id, repo.activeId())
            }
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
