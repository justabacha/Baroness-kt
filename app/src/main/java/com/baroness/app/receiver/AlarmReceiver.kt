package com.baroness.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.baroness.app.MainActivity
import com.baroness.app.R
import com.baroness.app.clock.BaronessClockManager
import com.baroness.app.clock.ClockSoundPlayer
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.models.NotificationData
import com.baroness.app.repository.SettingsRepository
import com.baroness.app.voice.VoiceCenter
import com.baroness.app.voice.VoiceConfig
import com.baroness.app.voice.VoiceContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_CLOCK_ID = "extra_clock_id"
        const val EXTRA_CLOCK_TYPE = "extra_clock_type"
        const val EXTRA_CLOCK_LABEL = "extra_clock_label"
        const val CHANNEL_ID = "baroness_clock_channel"
        const val ACTION_DISMISS = "com.baroness.app.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE = "com.baroness.app.ACTION_SNOOZE_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AlarmReceiver", "Received alarm/timer broadcast intent: ${intent.action}")

        val notificationId = intent.getIntExtra("notification_id", -1)
        val clockId = intent.getLongExtra(EXTRA_CLOCK_ID, -1)
        val type = intent.getStringExtra(EXTRA_CLOCK_TYPE) ?: "ALARM"
        val label = intent.getStringExtra(EXTRA_CLOCK_LABEL) ?: "Friday Alert"

        if (intent.action == ACTION_DISMISS) {
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
            ClockSoundPlayer.stopSound()
            return
        }

        if (intent.action == ACTION_SNOOZE) {
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
            ClockSoundPlayer.stopSound()
            val clockManager = BaronessClockManager(context)
            clockManager.snooze(clockId, type, label, 5)
            Log.i("AlarmReceiver", "Snoozed $type (ID=$clockId) for 5 minutes")
            return
        }

        Log.i("AlarmReceiver", "Alarm Fired! ID=$clockId, Type=$type, Label=$label")

        if (clockId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.getInstance(context).clockDao().deactivateClockItem(clockId)
            }
        }

        triggerAlarmNotification(context, clockId, type, label)
    }

    private fun triggerAlarmNotification(context: Context, clockId: Long, type: String, label: String) {
        val settingsRepo = SettingsRepository(context)
        val voiceAnnounce = settingsRepo.getInitialClockVoiceAnnounce()
        val soundOption = if (type == "TIMER") settingsRepo.getInitialTimerChimeOption() else settingsRepo.getInitialAlarmSoundOption()
        val vibrationPatternName = settingsRepo.getInitialAlarmVibrationPattern()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = RingtoneManager.getDefaultUri(
            if (type == "TIMER") RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_ALARM
        ) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarms & Timers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Friday Alarms and Timers"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            clockId.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = if (clockId.toInt() == -1) (System.currentTimeMillis() % Int.MAX_VALUE).toInt() else clockId.toInt()

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("notification_id", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("notification_id", notificationId)
            putExtra(EXTRA_CLOCK_ID, clockId)
            putExtra(EXTRA_CLOCK_TYPE, type)
            putExtra(EXTRA_CLOCK_LABEL, label)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 200000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (type == "TIMER") "Timer Finished!" else "Alarm Ringing!"
        val body = label.ifEmpty { "Friday alert" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Snooze 5m", snoozePendingIntent)

        notificationManager.notify(notificationId, builder.build())
        Log.i("AlarmReceiver", "Notification posted for $type (ID=$notificationId)")

        // Play Sound and Vibration using ClockSoundPlayer
        // For ALARMS: play for 60 seconds (1 minute continuous ring/loop)
        // For TIMERS: play single chime/tone
        val durationMs = if (type == "ALARM") 60000L else 1500L
        ClockSoundPlayer.playSound(context, soundOption, isAlarm = (type == "ALARM"), loopDurationMs = durationMs)
        ClockSoundPlayer.playVibration(context, vibrationPatternName)

        // Voice Readout using Friday's AI Voice if enabled for Timer/Reminder - 2 second delay so voice doesn't rush after chime
        if (type == "TIMER" && voiceAnnounce) {
            CoroutineScope(Dispatchers.Main).launch {
                kotlinx.coroutines.delay(2000)
                speakVoiceAnnouncement(context, label)
            }
        }

        // Trigger In-App notification overlay if app is in foreground
        try {
            com.baroness.app.utils.NotificationCenter.show(
                NotificationData(
                    title = title,
                    body = body,
                    featureType = "clock"
                )
            )
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to show in-app banner: ${e.message}")
        }
    }

    private fun speakVoiceAnnouncement(context: Context, label: String) {
        val settingsRepo = SettingsRepository(context)
        val voiceConfig = VoiceConfig(
            voiceId = settingsRepo.getInitialVoiceId(),
            speed = settingsRepo.getInitialVoiceSpeed(),
            pitch = settingsRepo.getInitialVoicePitch(),
            provider = settingsRepo.getInitialVoiceProvider(),
            directorNote = settingsRepo.getInitialDirectorNote()
        )
        val voiceCenter = VoiceCenter(context.applicationContext)
        val announcementText = "Yoo mate, your $label timer is finished!"
        Log.i("AlarmReceiver", "Friday speaking timer announcement with AI voice (${voiceConfig.provider}/${voiceConfig.voiceId}): $announcementText")
        voiceCenter.speak(announcementText, VoiceContext(voiceConfig))
    }
}
