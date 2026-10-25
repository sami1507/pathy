package com.example.pathy.tracking

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pathy.data.DatabaseProvider
import com.example.pathy.history.ActivityHistoryManager
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * Sends finished recordings to the cloud history when there is a connection. Hikers are often
 * offline, so a recording is saved on the phone first and uploaded later, here. A failed upload is
 * retried with a growing delay; nothing is ever deleted from the phone before it is safely stored.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Not signed in yet: leave the recordings on the phone. They go up after the next sign-in.
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val repository = RecordingRepository(DatabaseProvider.getDatabase(applicationContext).recordingDao())
        val history = ActivityHistoryManager()

        var needsRetry = false
        for (pending in repository.pendingUploads()) {
            val loaded = repository.load(pending.id) ?: continue
            if (loaded.points.size < 2) {
                // Nothing worth keeping (started and stopped at once): do not retry it forever.
                repository.markUploaded(pending.id)
                continue
            }
            history.saveRecording(user.uid, loaded)
                .onSuccess { repository.markUploaded(pending.id) }
                .onFailure { e ->
                    Log.w(TAG, "Upload of ${pending.id} failed: ${e.message}")
                    repository.countAttempt(pending.id)
                    needsRetry = true
                }
        }
        repository.pruneUploaded()
        return if (needsRetry) Result.retry() else Result.success()
    }

    private companion object {
        const val TAG = "UploadWorker"
    }
}

object UploadScheduler {
    private const val UNIQUE_NAME = "upload-recordings"

    /** Queue an upload of everything waiting. Safe to call often: waiting recordings go in one run. */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        try {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        } catch (e: IllegalStateException) {
            // WorkManager is not ready (for example in a unit test). The next app start tries again,
            // and the recording itself is already safe on the phone.
            Log.w("UploadScheduler", "Could not schedule the upload: ${e.message}")
        }
    }
}
