package com.baroness.app.modules

import android.content.Context
import android.net.Uri
import com.baroness.app.config.SupabaseConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ProfileManager {

    private const val SUPABASE_URL = SupabaseConfig.SUPABASE_URL
    private const val SUPABASE_KEY = SupabaseConfig.SUPABASE_ANON_KEY

    private val client = SupabaseConfig.client

    suspend fun updateFcmToken(id: String, token: String) {
        val jsonBody = JSONObject().apply {
            put("fcm_token", if (token.isBlank()) JSONObject.NULL else token)
            put("updated_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date()))
        }.toString()

        val url = "$SUPABASE_URL/rest/v1/profiles?id=eq.$id"
        android.util.Log.d("ProfileManager", "Updating FCM Token for $id")
        android.util.Log.d("ProfileManager", "URL: $url")
        android.util.Log.d("ProfileManager", "Payload: $jsonBody")

        val request = Request.Builder()
            .url(url)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("Content-Type", "application/json")
            .patch(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.peekBody(Long.MAX_VALUE).string()
            android.util.Log.d("ProfileManager", "Response Code: ${response.code}")
            android.util.Log.d("ProfileManager", "Response Body: $responseBody")

            if (!response.isSuccessful) {
                throw Exception("Failed to update FCM token: ${response.code} - $responseBody")
            }
        }
    }

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
                val avatarPathMarker = "/storage/v1/object/public/avatars/"
                if (oldUrl.contains(avatarPathMarker)) {
                    val oldObjectPath = oldUrl.substringAfter(avatarPathMarker)
                    if (oldObjectPath.isNotBlank()) {
                        deleteAvatar(oldObjectPath)
                    }
                }
            }

            // Upload new file
            val fileName = "avatar_${System.currentTimeMillis()}.jpg"
            val objectPath = "$currentPersonaId/$fileName"
            val uploadedUrl = uploadAvatar(context, uri, objectPath)
            finalAvatarUrl = uploadedUrl
        }

        // 2. Determine persona type
        val assignedPersona = if (currentPersonaId.contains("phesty", ignoreCase = true)) "Phesty" else "Baroness"

        // 3. Upsert profile
        upsertProfile(currentPersonaId, displayName, finalAvatarUrl, assignedPersona)

        return ProfileResult(displayName, finalAvatarUrl, assignedPersona)
    }

    private suspend fun uploadAvatar(context: Context, uri: Uri, objectPath: String): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Failed to read image")
        val fileName = objectPath.substringAfterLast("/")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/avatars/$objectPath")
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Upload failed: ${response.code}")
        }

        return "$SUPABASE_URL/storage/v1/object/public/avatars/$objectPath"
    }

    private fun deleteAvatar(objectPath: String) {
        val request = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/avatars/$objectPath")
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