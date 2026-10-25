package com.example.pathy.tracking

import com.example.pathy.core.track.RecorderState
import com.example.pathy.core.track.SportType
import com.example.pathy.core.track.TrackPoint
import com.example.pathy.core.track.TrackStats
import java.util.UUID

/** A recording read back from the phone's database, with its points in order. */
data class LoadedRecording(val recording: RecordingEntity, val points: List<TrackPoint>) {
    val sport: SportType
        get() = runCatching { SportType.valueOf(recording.sport) }.getOrDefault(SportType.HIKING)
}

/**
 * The only place that reads and writes recordings. Every accepted point is saved as it arrives, so
 * what is on disk is always what the recorder holds in memory (give or take one point).
 */
class RecordingRepository(private val dao: RecordingDao) {

    suspend fun begin(sport: SportType, startedAtMs: Long, title: String = ""): RecordingEntity {
        val entity = RecordingEntity(
            id = UUID.randomUUID().toString(),
            sport = sport.name,
            startedAtMs = startedAtMs,
            title = title
        )
        dao.insert(entity)
        return entity
    }

    suspend fun append(recordingId: String, seq: Int, p: TrackPoint) {
        dao.insertPoint(TrackPointEntity(recordingId, seq, p.lat, p.lon, p.altitudeM, p.timeMs, p.accuracyM))
    }

    /** Remembers whether the recorder is running or paused, and the pause time so far. */
    suspend fun saveClock(recordingId: String, state: RecorderState, pausedTotalMs: Long, pauseStartedMs: Long = 0L) {
        val current = dao.get(recordingId) ?: return
        dao.update(current.copy(recorderState = state.name, pausedTotalMs = pausedTotalMs, pauseStartedMs = pauseStartedMs))
    }

    suspend fun finish(recordingId: String, stats: TrackStats, finishedAtMs: Long) {
        val current = dao.get(recordingId) ?: return
        dao.update(
            current.copy(
                status = RecordingEntity.STATUS_FINISHED,
                recorderState = RecorderState.FINISHED.name,
                finishedAtMs = finishedAtMs,
                distanceM = stats.distanceM,
                elapsedMs = stats.elapsedMs,
                movingMs = stats.movingMs,
                elevationGainM = stats.elevationGainM,
                elevationLossM = stats.elevationLossM,
                averageSpeedKmh = stats.averageSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh,
                calories = stats.calories
            )
        )
    }

    suspend fun activeId(): String? = dao.active()?.id

    suspend fun loadActive(): LoadedRecording? = dao.active()?.let { load(it.id) }

    suspend fun load(recordingId: String): LoadedRecording? {
        val recording = dao.get(recordingId) ?: return null
        val points = dao.points(recordingId).map { TrackPoint(it.lat, it.lon, it.altitudeM, it.timeMs, it.accuracyM) }
        return LoadedRecording(recording, points)
    }

    suspend fun nextSeq(recordingId: String): Int = dao.lastSeq(recordingId) + 1

    suspend fun pendingUploads(): List<RecordingEntity> = dao.pendingUploads()

    suspend fun recentlyFinished(limit: Int = 30): List<RecordingEntity> = dao.finished(limit)

    suspend fun markUploaded(recordingId: String) = dao.markUploaded(recordingId)

    suspend fun countAttempt(recordingId: String) = dao.countAttempt(recordingId)

    suspend fun discard(recordingId: String) = dao.delete(recordingId)

    /** Keeps the phone tidy: uploaded outings older than [days] are removed from the device. */
    suspend fun pruneUploaded(days: Int = 30, nowMs: Long = System.currentTimeMillis()) {
        dao.deleteUploadedBefore(nowMs - days * 86_400_000L)
    }
}
