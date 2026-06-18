package com.baroness.app.modules

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProfileManager {

    private const val SUPABASE_URL = "https://wckluymkbqxdmipzaiff.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4MjM3NjAsImV4cCI6MjA5MzM5OTc2MH0.y3murBfcZtgluuPd_uFBut4Ky3Wl8WAHVCp-kA1u9sU"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ProfileResult(
        val displayName: String,
        val avatar: String?,
        val persona: String
    )

    suspend fun saveSetup(
        context: Context,
        currentPersonaId: String,
        displayName: String,
        newImageUri: Uri?,
        existingAvatarUrl: String?
    ): ProfileResult {
        require(currentPersonaId.isNotBlank()) { "No ID found!" }
        require(displayName.isNotBlank()) { "Name required" }

        var finalAvatarUrl = existingAvatarUrl ?: ""

        // 1. Upload new avatar if provided
        newImageUri?.let { uri ->
            // Delete old avatar if exists
            existingAvatarUrl?.let { oldUrl ->
                if (oldUrl.contains("storage/v1/object/public/avatars")) {
                    val oldFileName = oldUrl.substringAfterLast("/")
                    deleteAvatar(oldFileName)
                }
            }

            // Upload new file
            val fileName = "avatar_${System.currentTimeMillis()}.jpg"
            val uploadedUrl = uploadAvatar(context, uri, fileName)
            finalAvatarUrl = uploadedUrl
        }

        // 2. Determine persona type
        val assignedPersona = if (currentPersonaId.contains("phesty", ignoreCase = true)) "Phesty" else "Baroness"

        // 3. Upsert profile
        upsertProfile(currentPersonaId, displayName, finalAvatarUrl, assignedPersona)

        return ProfileResult(displayName, finalAvatarUrl, assignedPersona)
    }

    private suspend fun uploadAvatar(context: Context, uri: Uri, fileName: String): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Failed to read image")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/avatars/$fileName")
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Upload failed: ${response.code}")
        }

        return "$SUPABASE_URL/storage/v1/object/public/avatars/$fileName"
    }

    private fun deleteAvatar(fileName: String) {
        val request = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/avatars/$fileName")
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .delete()
            .build()
        client.newCall(request).execute().close()
    }

    private fun upsertProfile(id: String, displayName: String, avatarUrl: String, persona: String) {
        // Format current time as ISO 8601 string
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val now = dateFormat.format(java.util.Date())

        val jsonBody = JSONObject().apply {
            put("id", id)
            put("display_name", displayName)
            put("avatar_url", avatarUrl)
            put("persona", persona)
            put("updated_at", now)
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$id")
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates")
            .put(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to save profile: ${response.code} - ${response.body?.string()}")
        }
    }

    suspend fun loadProfile(currentPersonaId: String): Pair<String?, String?>? {
        val url = "$SUPABASE_URL/rest/v1/profiles?id=eq.$currentPersonaId&select=display_name,avatar_url"
        val request = Request.Builder()
            .url(url)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        if (body == "[]") return null
        val jsonArray = JSONArray(body)
        val obj = jsonArray.getJSONObject(0)
        val displayName = obj.optString("display_name", "").takeIf { it.isNotEmpty() }
        val avatarUrl = obj.optString("avatar_url", "").takeIf { it.isNotEmpty() }
        return Pair(displayName, avatarUrl)
    }
}