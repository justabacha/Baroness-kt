package com.baroness.app.command.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.baroness.app.command.CommandHandler

class NavigationCommandHandler(private val context: Context) : CommandHandler {

    private val supportedIntents = setOf("navigate", "open_app", "open_screen")

    override fun canHandle(intent: String): Boolean {
        return supportedIntents.contains(intent)
    }

    override fun execute(intent: String, parameters: Map<String, Any?>?): Boolean {
        return when (intent) {
            "navigate" -> navigate(parameters?.get("destination") as? String)
            "open_app" -> openApp(parameters?.get("app_name") as? String)
            "open_screen" -> openScreen(parameters?.get("screen_id") as? String)
            else -> false
        }
    }

    private fun navigate(destination: String?): Boolean {
        if (destination.isNullOrBlank()) return false
        return try {
            val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mapIntent)
            true
        } catch (e: Exception) {
            Log.e("NavigationCommandHandler", "Failed to navigate: ${e.message}")
            false
        }
    }

    private fun openApp(appName: String?): Boolean {
        if (appName.isNullOrBlank()) return false
        val targetPackage = when (appName.lowercase().trim()) {
            "spotify" -> "com.spotify.music"
            "youtube" -> "com.google.android.youtube"
            "whatsapp" -> "com.whatsapp"
            "settings" -> "com.android.settings"
            "maps", "google maps" -> "com.google.android.apps.maps"
            else -> null
        }

        return try {
            val pm = context.packageManager
            val launchIntent = if (targetPackage != null) {
                pm.getLaunchIntentForPackage(targetPackage)
            } else {
                // Search installed apps by label
                val installed = pm.getInstalledApplications(0)
                val match = installed.find { app ->
                    val label = pm.getApplicationLabel(app).toString().lowercase()
                    label.contains(appName.lowercase())
                }
                match?.let { pm.getLaunchIntentForPackage(it.packageName) }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                Log.w("NavigationCommandHandler", "App not found: $appName")
                false
            }
        } catch (e: Exception) {
            Log.e("NavigationCommandHandler", "Failed to open app $appName: ${e.message}")
            false
        }
    }

    private fun openScreen(screenId: String?): Boolean {
        if (screenId.isNullOrBlank()) return false
        Log.d("NavigationCommandHandler", "Navigating to internal Baroness screen: $screenId")
        return true
    }
}
