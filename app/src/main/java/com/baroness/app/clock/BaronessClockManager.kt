package com.baroness.app.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.ClockItemEntity
import com.baroness.app.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class BaronessClockManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val clockDao = AppDatabase.getInstance(context).clockDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun setTimer(minutes: Int, label: String = "Timer"): Boolean {
        if (minutes < 1) return false
        val triggerTimeMs = System.currentTimeMillis() + (minutes * 60 * 1000L)

        scope.launch {
            val entity = ClockItemEntity(
                type = "TIMER",
                triggerTimeMs = triggerTimeMs,
                label = label
            )
            val id = clockDao.insertClockItem(entity)
            scheduleSystemAlarm(id, triggerTimeMs, "TIMER", label)
        }
        return true
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "Alarm"): Boolean {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1) // Schedule for tomorrow if time has already passed today
            }
        }
        val triggerTimeMs = calendar.timeInMillis

        scope.launch {
            val entity = ClockItemEntity(
                type = "ALARM",
                triggerTimeMs = triggerTimeMs,
                label = label
            )
            val id = clockDao.insertClockItem(entity)
            scheduleSystemAlarm(id, triggerTimeMs, "ALARM", label)
        }
        return true
    }

    private fun scheduleSystemAlarm(id: Long, triggerTimeMs: Long, type: String, label: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_CLOCK_ID, id)
            putExtra(AlarmReceiver.EXTRA_CLOCK_TYPE, type)
            putExtra(AlarmReceiver.EXTRA_CLOCK_LABEL, label)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
            Log.i("BaronessClockManager", "Successfully scheduled $type (ID: $id, label: '$label') for trigger at $triggerTimeMs ms")
        } catch (e: Exception) {
            Log.e("BaronessClockManager", "Failed to schedule exact alarm: ${e.message}", e)
        }
    }

    fun snooze(clockId: Long, type: String, label: String, snoozeMinutes: Int = 5) {
        val triggerTimeMs = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val snoozedLabel = if (label.contains("(Snoozed)")) label else "$label (Snoozed)"

        scope.launch {
            if (clockId != -1L) {
                clockDao.deactivateClockItem(clockId)
            }
            val entity = ClockItemEntity(
                type = type,
                triggerTimeMs = triggerTimeMs,
                label = snoozedLabel
            )
            val newId = clockDao.insertClockItem(entity)
            scheduleSystemAlarm(newId, triggerTimeMs, type, snoozedLabel)
            Log.i("BaronessClockManager", "Snoozed item $clockId into new item $newId for $snoozeMinutes min")
        }
    }

    fun rescheduleAllActiveAlarms() {
        scope.launch {
            val activeItems = clockDao.getActiveClockItems()
            val now = System.currentTimeMillis()
            for (item in activeItems) {
                if (item.triggerTimeMs > now) {
                    scheduleSystemAlarm(item.id, item.triggerTimeMs, item.type, item.label)
                }
            }
        }
    }
}
