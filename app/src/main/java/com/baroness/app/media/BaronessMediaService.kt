package com.baroness.app.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.baroness.app.MainActivity

class BaronessMediaService : MediaSessionService() {

    private val CHANNEL_ID = "baroness_playback_channel"
    private val NOTIFICATION_ID = 1001

    companion object {
        const val ACTION_PLAY_PAUSE = "com.baroness.app.media.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.baroness.app.media.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.baroness.app.media.ACTION_PREVIOUS"
        const val ACTION_TOGGLE_SHUFFLE = "com.baroness.app.media.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.baroness.app.media.ACTION_TOGGLE_REPEAT"
        const val ACTION_STOP = "com.baroness.app.media.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val playerManager = BaronessPlayerManager.getInstance(this)

        when (action) {
            ACTION_PLAY_PAUSE -> playerManager.playPause()
            ACTION_NEXT -> playerManager.skipNext()
            ACTION_PREVIOUS -> playerManager.skipPrevious()
            ACTION_TOGGLE_SHUFFLE -> playerManager.toggleShuffle()
            ACTION_TOGGLE_REPEAT -> playerManager.toggleRepeatMode()
            ACTION_STOP -> {
                playerManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        updateNotification()
        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    private fun updateNotification() {
        val playerManager = BaronessPlayerManager.getInstance(this)
        val currentSong = playerManager.currentSong.value
        val isPlaying = playerManager.isPlaying.value
        val mediaSession = playerManager.getMediaSession()

        if (currentSong == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val title = currentSong.title
        val artist = currentSong.artist

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val shufflePending = PendingIntent.getService(
            this,
            1,
            Intent(this, BaronessMediaService::class.java).apply { this.action = ACTION_TOGGLE_SHUFFLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPending = PendingIntent.getService(
            this,
            2,
            Intent(this, BaronessMediaService::class.java).apply { this.action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPausePending = PendingIntent.getService(
            this,
            3,
            Intent(this, BaronessMediaService::class.java).apply { this.action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextPending = PendingIntent.getService(
            this,
            4,
            Intent(this, BaronessMediaService::class.java).apply { this.action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val repeatPending = PendingIntent.getService(
            this,
            5,
            Intent(this, BaronessMediaService::class.java).apply { this.action = ACTION_TOGGLE_REPEAT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPending = PendingIntent.getService(
            this,
            6,
            Intent(this, BaronessMediaService::class.java).apply { this.action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val albumArtBitmap: Bitmap? = currentSong.albumArtUri?.let { uri ->
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                null
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpenApp)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_rotate, "Shuffle", shufflePending) // Action 0
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending) // Action 1
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePending) // Action 2
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending) // Action 3
            .addAction(android.R.drawable.ic_menu_revert, "Repeat", repeatPending) // Action 4
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending) // Action 5

        if (albumArtBitmap != null) {
            builder.setLargeIcon(albumArtBitmap)
        }

        if (mediaSession != null) {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(1, 2, 3)
            )
        }

        val notification = builder.build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("BaronessMediaService", "Failed to startForeground: ${e.message}")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return BaronessPlayerManager.getInstance(this).getMediaSession()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Media Playback"
            val descriptionText = "Baroness background music controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playerManager = BaronessPlayerManager.getInstance(this)
        if (!playerManager.isPlaying.value) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}
