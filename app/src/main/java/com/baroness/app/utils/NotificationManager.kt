package com.baroness.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.baroness.app.MainActivity
import com.baroness.app.R
import com.baroness.app.data.models.NotificationData

class NotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_WISHLIST = "wishlist_notifications"
        const val CHANNEL_MESSAGES = "messages_notifications"
        const val CHANNEL_GENERAL = "general_notifications"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(CHANNEL_WISHLIST, "Wishlist", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_LOW)
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    fun showSystemNotification(data: NotificationData) {
        val channelId = when (data.featureType) {
            "wishlist" -> CHANNEL_WISHLIST
            "messages" -> CHANNEL_MESSAGES
            else -> CHANNEL_GENERAL
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("route", data.route)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // TODO: Use a proper silhouette icon
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(data.timestamp.toInt(), builder.build())
    }
}
