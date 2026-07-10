package com.baroness.app

import android.app.Application
import android.util.Log
import com.baroness.app.modules.ProfileManager
import com.baroness.app.utils.NotificationManager
import com.baroness.app.utils.StorageManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Notification Channels
        val notificationManager = NotificationManager(this)
        notificationManager.createNotificationChannels()

        // Register FCM Token
        registerFcmToken()
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainApplication", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("MainApplication", "FCM Token: $token")

            val storageManager = StorageManager(this)
            scope.launch {
                storageManager.saveString("fcm_token", token)
                
                val personaId = storageManager.getString("vibe_persona")
                if (!personaId.isNullOrBlank()) {
                    try {
                        ProfileManager.updateFcmToken(personaId, token)
                    } catch (e: Exception) {
                        Log.e("MainApplication", "Failed to sync token: ${e.message}")
                    }
                }
            }
        }
    }
}
