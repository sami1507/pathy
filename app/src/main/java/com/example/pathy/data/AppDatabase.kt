package com.example.pathy.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pathy.tracking.RecordingDao
import com.example.pathy.tracking.RecordingEntity
import com.example.pathy.tracking.TrackPointEntity

@Database(
    entities = [RecentSearch::class, RecordingEntity::class, TrackPointEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        /** Version 2 adds the on-device recordings. Existing recent searches are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `recordings` (
                        `id` TEXT NOT NULL, `sport` TEXT NOT NULL, `startedAtMs` INTEGER NOT NULL,
                        `pausedTotalMs` INTEGER NOT NULL, `pauseStartedMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `recorderState` TEXT NOT NULL,
                        `finishedAtMs` INTEGER NOT NULL, `title` TEXT NOT NULL, `distanceM` REAL NOT NULL,
                        `elapsedMs` INTEGER NOT NULL, `movingMs` INTEGER NOT NULL, `elevationGainM` REAL NOT NULL,
                        `elevationLossM` REAL NOT NULL, `averageSpeedKmh` REAL NOT NULL, `maxSpeedKmh` REAL NOT NULL,
                        `calories` INTEGER NOT NULL, `uploaded` INTEGER NOT NULL, `uploadAttempts` INTEGER NOT NULL,
                        PRIMARY KEY(`id`))"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_status` ON `recordings` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_uploaded` ON `recordings` (`uploaded`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `track_points` (
                        `recordingId` TEXT NOT NULL, `seq` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL,
                        `altitudeM` REAL, `timeMs` INTEGER NOT NULL, `accuracyM` REAL,
                        PRIMARY KEY(`recordingId`, `seq`),
                        FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
            }
        }
    }
}
