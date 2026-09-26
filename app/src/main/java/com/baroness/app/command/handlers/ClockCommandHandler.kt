package com.baroness.app.command.handlers

import android.content.Context
import android.util.Log
import com.baroness.app.clock.BaronessClockManager
import com.baroness.app.command.CommandHandler

class ClockCommandHandler(context: Context) : CommandHandler {

    private val supportedIntents = setOf("set_timer", "set_alarm")
    private val clockManager = BaronessClockManager(context)

    override fun canHandle(intent: String): Boolean {
        return supportedIntents.contains(intent)
    }

    override fun execute(intent: String, parameters: Map<String, Any?>?): Boolean {
        Log.i("ClockCommandHandler", "Received command: $intent with parameters: $parameters")
        return when (intent) {
            "set_timer" -> setTimer(parameters)
            "set_alarm" -> setAlarm(parameters)
            else -> false
        }
    }

    private fun setTimer(parameters: Map<String, Any?>?): Boolean {
        val minutes = (parameters?.get("minutes") as? Number)?.toInt()
            ?: (parameters?.get("length") as? Number)?.toInt()
            ?: (parameters?.get("minutes") as? String)?.toIntOrNull()
            ?: (parameters?.get("length") as? String)?.toIntOrNull()
            ?: run {
                Log.e("ClockCommandHandler", "Missing or invalid 'minutes' parameter in: $parameters")
                return false
            }

        val label = (parameters?.get("label") as? String) ?: "Timer"

        return try {
            val success = clockManager.setTimer(minutes, label)
            Log.i("ClockCommandHandler", "Set $minutes min timer result: $success")
            success
        } catch (e: Exception) {
            Log.e("ClockCommandHandler", "Failed to set timer: ${e.message}", e)
            false
        }
    }

    private fun setAlarm(parameters: Map<String, Any?>?): Boolean {
        if (parameters == null) {
            Log.e("ClockCommandHandler", "Parameters map is null for set_alarm")
            return false
        }

        val hour = (parameters["hour"] as? Number)?.toInt()
            ?: (parameters["hour"] as? String)?.toIntOrNull()
            ?: run {
                Log.e("ClockCommandHandler", "Missing or invalid 'hour' parameter in: $parameters")
                return false
            }

        val minute = (parameters["minute"] as? Number)?.toInt()
            ?: (parameters["minute"] as? String)?.toIntOrNull()
            ?: 0

        val label = (parameters["label"] as? String) ?: "Alarm"

        return try {
            val success = clockManager.setAlarm(hour, minute, label)
            Log.i("ClockCommandHandler", "Set alarm for $hour:$minute result: $success")
            success
        } catch (e: Exception) {
            Log.e("ClockCommandHandler", "Failed to set alarm: ${e.message}", e)
            false
        }
    }
}
