package com.baroness.app.modules

import android.content.Context
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.models.UserProfile
import com.baroness.app.utils.StorageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthManager(private val context: Context) {
    private val storageManager = StorageManager(context)

    private val client = SupabaseConfig.client

    sealed class GateResult {
        data class Success(val userProfile: UserProfile?, val currentPersonaId: String) : GateResult()
        data class Error(val message: String) : GateResult()
    }

    suspend fun checkGate(persona: String, inputPass: String): GateResult {
        return withContext(Dispatchers.IO) {
            try {
                val verifyUrl = "${SupabaseConfig.SUPABASE_URL}/functions/v1/verify-passkey"

                val jsonPayload = JSONObject().apply {
                    put("persona", persona)
                    put("passkey", inputPass)
                }.toString()

                val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(verifyUrl)
                    .post(requestBody)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(responseBody).optString("error").takeIf { it.isNotBlank() }
                    } catch (e: Exception) {
                        null
                    } ?: "Network error: ${response.code}"
                    return@withContext GateResult.Error(errorMsg)
                }

                val jsonObj = JSONObject(responseBody)
                if (jsonObj.has("error") && !jsonObj.isNull("error")) {
                    return@withContext GateResult.Error(jsonObj.getString("error"))
                }

                val token = jsonObj.optString("token").takeIf { it.isNotBlank() }
                if (!token.isNullOrBlank()) {
                    try {
                        val refreshToken = if (jsonObj.isNull("refresh_token")) null else {
                            jsonObj.optString("refresh_token").takeIf { it.isNotBlank() }
                        }
                        val expiresIn = if (jsonObj.has("expires_in") && !jsonObj.isNull("expires_in")) {
                            jsonObj.optLong("expires_in", -1L).takeIf { it > 0L }
                        } else {
                            null
                        }
                        if (refreshToken == null || expiresIn == null) {
                            Log.w(
                                "AuthManager",
                                "Login response omitted refresh_token or expires_in; automatic refresh is unavailable"
                            )
                        }
                        val expiresAt = expiresIn?.let {
                            System.currentTimeMillis() + it * 1000L
                        }
                        storageManager.saveAuthSession(token, refreshToken, expiresAt)
                        com.baroness.app.auth.SessionTokenCache.init(context)
                        com.baroness.app.auth.SessionTokenCache.setSession(
                            token,
                            refreshToken,
                            expiresAt
                        )
                        if (refreshToken != null && expiresAt != null &&
                            expiresAt <= System.currentTimeMillis() + 60 * 60 * 1000L
                        ) {
                            com.baroness.app.auth.SessionTokenCache.refreshIfNeeded(force = true)
                        }
                    } catch (e: Exception) {
                        Log.e("AuthManager", "Failed to persist the Auth session", e)
                    }
                }

                val personaBase = persona.lowercase().replace("_official", "")
                val currentPersonaId = jsonObj.optString("currentPersonaId", "${personaBase}_official")

                val profileObj = jsonObj.optJSONObject("userProfile")
                val userProfile = if (profileObj != null) {
                    val avatar = if (profileObj.isNull("avatarUrl")) null else {
                        profileObj.optString("avatarUrl").takeIf { it.isNotBlank() }
                    }
                    UserProfile(
                        displayName = profileObj.optString("displayName", ""),
                        avatar = avatar,
                        persona = profileObj.optString("persona", personaBase),
                        id = profileObj.optString("personaId", currentPersonaId)
                    )
                } else null

                GateResult.Success(userProfile, currentPersonaId)
            } catch (e: Exception) {
                GateResult.Error(e.message ?: "Unknown error")
            }
        }
    }
}
