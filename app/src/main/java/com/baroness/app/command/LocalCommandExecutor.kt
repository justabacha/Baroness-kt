package com.baroness.app.command

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log

class LocalCommandExecutor(private val context: Context) {

    fun execute(intentName: String?, parameters: Map<String, Any?>?) {
        Log.d("LocalCommandExecutor", "Executing intent: $intentName with params: $parameters")
        when (intentName) {
            "play_music" -> playMusic(parameters?.get("genre") as? String)
            "navigate" -> navigate(parameters?.get("destination") as? String)
            "set_timer" -> setTimer((parameters?.get("minutes") as? Number)?.toInt())
            "set_alarm" -> setAlarm(parameters)
            else -> Log.w("LocalCommandExecutor", "Unknown or unsupported intent: $intentName")
        }
    }

    private fun playMusic(genre: String?) {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
                genre?.let { putExtra("genre", it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LocalCommandExecutor", "Failed to play music: ${e.message}")
        }
    }

    private fun navigate(destination: String?) {
        if (destination == null) return
        try {
            val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            Log.e("LocalCommandExecutor", "Failed to navigate: ${e.message}")
        }
    }

    private fun setTimer(minutes: Int?) {
        if (minutes == null) return
        try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LocalCommandExecutor", "Failed to set timer: ${e.message}")
        }
    }

    private fun setAlarm(parameters: Map<String, Any?>?) {
        val hour = (parameters?.get("hour") as? Number)?.toInt() ?: return
        val minute = (parameters?.get("minute") as? Number)?.toInt() ?: 0
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LocalCommandExecutor", "Failed to set alarm: ${e.message}")
        }
    }
}
