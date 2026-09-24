package com.baroness.app.command.handlers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.baroness.app.command.CommandHandler

class ClockCommandHandler(private val context: Context) : CommandHandler {

    private val supportedIntents = setOf("set_timer", "set_alarm")

    override fun canHandle(intent: String): Boolean {
        return supportedIntents.contains(intent)
    }

    override fun execute(intent: String, parameters: Map<String, Any?>?): Boolean {
        return when (intent) {
            "set_timer" -> setTimer((parameters?.get("minutes") as? Number)?.toInt())
            "set_alarm" -> setAlarm(parameters)
            else -> false
        }
    }

    private fun setTimer(minutes: Int?): Boolean {
        if (minutes == null || minutes < 1) return false
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("ClockCommandHandler", "Failed to set timer: ${e.message}")
            false
        }
    }

    private fun setAlarm(parameters: Map<String, Any?>?): Boolean {
        val hour = (parameters?.get("hour") as? Number)?.toInt() ?: return false
        val minute = (parameters?.get("minute") as? Number)?.toInt() ?: 0
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("ClockCommandHandler", "Failed to set alarm: ${e.message}")
            false
        }
    }
}
