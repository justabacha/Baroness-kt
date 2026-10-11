package com.baroness.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baroness.app.repository.WishlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

private const val TAG = "SyncWorker"

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val repository = WishlistRepository.getInstance(applicationContext)
            try {
                Log.d(TAG, "SyncWorker started: Processing queue")
                repository.processSyncQueue()
                Log.d(TAG, "SyncWorker finished: Queue processed")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "SyncWorker failed critically: ${e.message}", e)
                Result.retry()
            }
        }
    }
}
