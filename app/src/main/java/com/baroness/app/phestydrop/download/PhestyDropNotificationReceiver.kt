package com.baroness.app.phestydrop.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.baroness.app.MainActivity
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhestyDropNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_NOTIFICATION_CLICKED) return
        val clickedIds =
            intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS) ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val activeId = StorageManager(appContext)
                    .getString("phestydrop_download_id")
                    ?.toLongOrNull()
                if (activeId != null && clickedIds.contains(activeId)) {
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java).apply {
                            putExtra("route", "settings/about")
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("PhestyDropNotificationReceiver", "Failed to open About page from download notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
