package com.baroness.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.baroness.app.auth.SessionTokenCache
import com.baroness.app.modules.ProfileManager
import com.baroness.app.utils.NotificationManager
import com.baroness.app.utils.StorageManager
import com.google.firebase.messaging.FirebaseMessaging
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application(), ImageLoaderFactory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("image_cache"))
                    .maxSizeBytes(25L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize StorageManager Sync Mirror
        StorageManager.initSyncMirror(this)

        // Initialize Session Token Cache
        SessionTokenCache.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch {
                        SessionTokenCache.awaitReady()
                        val expiry = SessionTokenCache.getExpiry()
                        if (SessionTokenCache.getRefreshToken() != null &&
                            expiry != null &&
                            expiry <= System.currentTimeMillis() + 60 * 60 * 1000L
                        ) {
                            SessionTokenCache.refreshIfNeeded(force = true)
                        }
                    }
                }
            }
        )

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
            Log.d("MainApplication", "FCM registration token available")

            val storageManager = StorageManager(this)
            scope.launch {
                storageManager.saveString("fcm_token", token)
                
                val personaId = storageManager.getString("currentPersonaId")
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
