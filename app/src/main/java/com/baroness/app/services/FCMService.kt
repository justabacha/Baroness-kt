package com.baroness.app.services

import android.util.Log
import com.baroness.app.data.models.NotificationData
import com.baroness.app.modules.ProfileManager
import com.baroness.app.utils.NotificationManager
import com.baroness.app.utils.SessionManager
import com.baroness.app.utils.StorageManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManager(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "New token: $token")
        
        // Save token locally
        val storageManager = StorageManager(applicationContext)
        
        scope.launch {
            storageManager.saveString("fcm_token", token)

            // Sync with Supabase if persona is set
            val personaId = storageManager.getString("currentPersonaId")
            if (!personaId.isNullOrBlank()) {
                try {
                    ProfileManager.updateFcmToken(personaId, token)
                } catch (e: Exception) {
                    Log.e("FCMService", "Failed to sync token: ${e.message}")
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCMService", "Message received from: ${message.from}")

        val data = message.data
        val notificationData = NotificationData(
            title = data["title"] ?: message.notification?.title ?: "New Notification",
            body = data["body"] ?: message.notification?.body ?: "",
            avatarUrl = data["avatar_url"],
            featureType = data["feature_type"],
            route = data["route"]
        )

        // TODO: Logic to decide between System vs In-App notification
        // For now, always show system notification if app is in background, 
        // or trigger in-app if we can reach the ViewModel.
        // Actually, the simplest way is to always show System notification 
        // and let MainActivity/InAppNotification handle the foreground case separately via Realtime.
        notificationManager.showSystemNotification(notificationData)
    }
}
