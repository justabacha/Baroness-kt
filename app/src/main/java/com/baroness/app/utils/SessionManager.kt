package com.baroness.app.utils

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SessionManager(private val context: Context) {
    private val storage = StorageManager(context)

    suspend fun isLoggedIn(): Boolean {
        val userProfile = storage.getString("userProfile")
        val vibePersona = storage.getString("vibe_persona")
        return !userProfile.isNullOrEmpty() && !vibePersona.isNullOrEmpty()
    }

    suspend fun getStartDestination(): String {
        return if (isLoggedIn()) "dashboard" else "gate"
    }
}