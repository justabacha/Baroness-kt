package com.baroness.app.modules

import android.content.Context
import com.baroness.app.models.UserProfile
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AuthManager(private val context: Context) {
    @Suppress("unused")
    private val storageManager = StorageManager(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = "https://wckluymkbqxdmipzaiff.supabase.co"
    private val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4MjM3NjAsImV4cCI6MjA5MzM5OTc2MH0.y3murBfcZtgluuPd_uFBut4Ky3Wl8WAHVCp-kA1u9sU"
    private val json = Json { ignoreUnknownKeys = true }

    sealed class GateResult {
        data class Success(val userProfile: UserProfile?, val currentPersonaId: String) : GateResult()
        data class Error(val message: String) : GateResult()
    }

    suspend fun checkGate(persona: String, inputPass: String): GateResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Check access key
                val accessUrl = "$supabaseUrl/rest/v1/access_keys?id=eq.$persona&select=secret_key"
                val accessRequest = Request.Builder()
                    .url(accessUrl)
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .build()

                val accessResponse = client.newCall(accessRequest).execute()
                if (!accessResponse.isSuccessful) {
                    return@withContext GateResult.Error("Network error: ${accessResponse.code}")
                }

                val accessBody = accessResponse.body?.string() ?: "[]"
                val storedKey = extractSecretKey(accessBody)

                if (storedKey == null || storedKey != inputPass) {
                    return@withContext GateResult.Error("Invalid pass key, blud!")
                }

                val currentPersonaId = "${persona}_official"

                // 2. Check profile
                val profileUrl = "$supabaseUrl/rest/v1/profiles?id=eq.$currentPersonaId&select=display_name,avatar_url,persona"
                val profileRequest = Request.Builder()
                    .url(profileUrl)
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .build()

                val profileResponse = client.newCall(profileRequest).execute()
                val profileBody = profileResponse.body?.string() ?: "[]"
                val userProfile = extractProfile(profileBody, currentPersonaId)

                GateResult.Success(userProfile, currentPersonaId)
            } catch (e: Exception) {
                GateResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun extractSecretKey(jsonArray: String): String? {
        val trimmed = jsonArray.trim()
        if (trimmed == "[]") return null
        val keyPattern = "\"secret_key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return keyPattern.find(trimmed)?.groupValues?.get(1)
    }

    private fun extractProfile(jsonArray: String, personaId: String): UserProfile? {
        val trimmed = jsonArray.trim()
        if (trimmed == "[]") return null
        val displayName = extractJsonString(trimmed, "display_name") ?: ""
        val avatarUrl = extractJsonString(trimmed, "avatar_url")
        val persona = extractJsonString(trimmed, "persona") ?: ""
        return UserProfile(displayName, avatarUrl, persona, personaId)
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }
}