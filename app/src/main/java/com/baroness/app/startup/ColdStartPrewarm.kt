package com.baroness.app.startup

import android.content.Context
import android.util.Log
import com.baroness.app.data.QuoteRepository
import com.baroness.app.repository.WishlistRepository
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ColdStartPrewarm {
    private const val TAG = "ColdStartPrewarm"

    @Volatile
    private var isComplete = false

    fun isPrewarmComplete(): Boolean = isComplete

    suspend fun run(context: Context) {
        if (isComplete) return

        withContext(Dispatchers.IO) {
            // 1. Warm StorageManager mirror
            StorageManager.initSyncMirror(context)

            // 2. Warm Wishlist snapshot from Room
            runCatching {
                val repository = WishlistRepository.getInstance(context)
                repository.warmSnapshotFromDatabase()
            }.onFailure { Log.e(TAG, "Wishlist pre-warm failed", it) }

            // 3. Warm ChatList snapshot from Room
            runCatching {
                ChatListPrewarm.warmFromDatabase(context)
            }.onFailure { Log.e(TAG, "ChatList pre-warm failed", it) }

            // 4. Warm Dashboard quotes cache
            runCatching {
                QuoteRepository.getInstance(context).warmLocalCache()
            }.onFailure { Log.e(TAG, "Quotes pre-warm failed", it) }
        }

        isComplete = true
    }
}
