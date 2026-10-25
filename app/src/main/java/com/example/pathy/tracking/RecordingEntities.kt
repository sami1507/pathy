package com.example.pathy.tracking

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * One recorded outing, saved on the phone. It exists from the moment recording starts, so an
 * outing is never lost to a crash, a killed process or a dead network.
 */
@Entity(tableName = "recordings", indices = [Index("status"), Index("uploaded")])
data class RecordingEntity(
    @PrimaryKey val id: String,
    /** [com.example.pathy.core.track.SportType] name. */
    val sport: String,
    val startedAtMs: Long,
    val pausedTotalMs: Long = 0,
    /** When the current pause began (0 when not paused), so a pause survives a restart. */
    val pauseStartedMs: Long = 0,
    /** ACTIVE while recording or paused, FINISHED once stopped. */
    val status: String = STATUS_ACTIVE,
    /** The recorder state to restore: RECORDING, PAUSED or AUTO_PAUSED. */
    val recorderState: String = "RECORDING",
    val finishedAtMs: Long = 0,
    val title: String = "",
    // Final statistics, filled in when the recording is finished.
    val distanceM: Double = 0.0,
    val elapsedMs: Long = 0,
    val movingMs: Long = 0,
    val elevationGainM: Double = 0.0,
    val elevationLossM: Double = 0.0,
    val averageSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val calories: Int = 0,
    /** True once the outing is stored in the cloud history. */
    val uploaded: Boolean = false,
    val uploadAttempts: Int = 0
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_FINISHED = "FINISHED"
    }
}

@Entity(
    tableName = "track_points",
    primaryKeys = ["recordingId", "seq"],
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TrackPointEntity(
    val recordingId: String,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val timeMs: Long,
    val accuracyM: Float?
)

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity)

    @Update
    suspend fun update(recording: RecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: TrackPointEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: String): RecordingEntity?

    /** The recording that was in progress when the app last stopped, if any. */
    @Query("SELECT * FROM recordings WHERE status = 'ACTIVE' ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun active(): RecordingEntity?

    @Query("SELECT * FROM track_points WHERE recordingId = :id ORDER BY seq")
    suspend fun points(id: String): List<TrackPointEntity>

    @Query("SELECT COALESCE(MAX(seq), -1) FROM track_points WHERE recordingId = :id")
    suspend fun lastSeq(id: String): Int

    @Query("SELECT * FROM recordings WHERE status = 'FINISHED' AND uploaded = 0 ORDER BY startedAtMs")
    suspend fun pendingUploads(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE status = 'FINISHED' ORDER BY startedAtMs DESC LIMIT :limit")
    suspend fun finished(limit: Int): List<RecordingEntity>

    @Query("UPDATE recordings SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: String)

    @Query("UPDATE recordings SET uploadAttempts = uploadAttempts + 1 WHERE id = :id")
    suspend fun countAttempt(id: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)

    /** Uploaded outings older than this are removed to save space (their copy lives in the cloud). */
    @Query("DELETE FROM recordings WHERE uploaded = 1 AND finishedAtMs < :olderThanMs")
    suspend fun deleteUploadedBefore(olderThanMs: Long)
}
