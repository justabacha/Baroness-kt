package com.baroness.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baroness.app.clock.BaronessClockManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val clockManager = BaronessClockManager(context)
            clockManager.rescheduleAllActiveAlarms()
        }
    }
}
