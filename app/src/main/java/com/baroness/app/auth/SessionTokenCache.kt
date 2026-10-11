package com.baroness.app.auth

import android.content.Context
import android.util.Log
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.utils.StorageManager
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "SessionTokenCache"

object SessionTokenCache {
    private const val TOKEN_SAFETY_WINDOW_MS = 60_000L

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedRefreshToken: String? = null
    @Volatile private var cachedExpiry: Long? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val _reauthRequired = MutableStateFlow(false)
    val reauthRequired: StateFlow<Boolean> = _reauthRequired.asStateFlow()
    private val _tokenRefreshed = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        extraBufferCapacity = 1
    )
    val tokenRefreshedFlow = _tokenRefreshed.asSharedFlow()

    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null
    @Volatile private var storageManager: StorageManager? = null

    internal var refreshCallOverride: (suspend (String) -> RefreshResult)? = null
    internal var reauthHandlerOverride: (suspend () -> Unit)? = null
    internal var skipRealtimeForTests = false

    data class RefreshResult(
        val statusCode: Int,
        val body: String
    )

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            storageManager = StorageManager(context.applicationContext)
            initialized = true
        }

        val storage = storageManager ?: return
        scope.launch {
            try {
                val storedSession = Triple(
                    storage.getString("auth_token")?.takeIf(String::isNotBlank),
                    storage.getString("refresh_token")?.takeIf(String::isNotBlank),
                    storage.getLong("token_expires_at")
                )
                cachedToken = storedSession.first
                cachedRefreshToken = storedSession.second
                cachedExpiry = storedSession.third
                updateRealtime(storedSession.first)
                ready.complete(Unit)
                combine(
                    storage.getStringFlow("auth_token"),
                    storage.getStringFlow("refresh_token"),
                    storage.getLongFlow("token_expires_at")
                ) { token, refreshToken, expiry ->
                    Triple(token?.takeIf(String::isNotBlank), refreshToken?.takeIf(String::isNotBlank), expiry)
                }.collect { session ->
                    cachedToken = session.first
                    cachedRefreshToken = session.second
                    cachedExpiry = session.third
                    updateRealtime(session.first)
                }
            } catch (e: Exception) {
                ready.completeExceptionally(e)
                Log.e(TAG, "Session cache initialization failed", e)
            }
        }
    }

    suspend fun awaitReady() {
        if (initialized) ready.await()
    }

    fun getToken(): String? = getTokenSync()

    fun getTokenSync(): String? = cachedToken

    fun getExpiry(): Long? = cachedExpiry

    fun getRefreshToken(): String? = cachedRefreshToken

    fun setToken(token: String?) {
        setSession(token, cachedRefreshToken, cachedExpiry)
    }

    fun setSession(token: String?, refreshToken: String?, expiresAt: Long?) {
        cachedToken = token?.takeIf(String::isNotBlank)
        cachedRefreshToken = refreshToken?.takeIf(String::isNotBlank)
        cachedExpiry = expiresAt
        scope.launch { updateRealtime(cachedToken) }
    }

    fun clear() {
        cachedToken = null
        cachedRefreshToken = null
        cachedExpiry = null
        scope.launch { updateRealtime(null) }
    }

    fun clearReauthenticationRequest() {
        _reauthRequired.value = false
    }

    suspend fun getValidTokenOrRefresh(): String? {
        awaitReady()
        val token = cachedToken ?: return null
        val expiry = cachedExpiry ?: return token
        if (expiry > System.currentTimeMillis()) return token
        return refreshIfNeeded(force = true)
    }

    suspend fun refreshIfNeeded(force: Boolean = false): String? {
        awaitReady()
        val observedToken = cachedToken
        return refreshMutex.withLock {
            val token = cachedToken
            val expiry = cachedExpiry
            val now = System.currentTimeMillis()

            if (!force && token != null &&
                (expiry == null || expiry > now + TOKEN_SAFETY_WINDOW_MS)
            ) {
                return@withLock token
            }
            if (force && token != observedToken && token != null &&
                (expiry == null || expiry > now + TOKEN_SAFETY_WINDOW_MS)
            ) {
                return@withLock token
            }

            val refreshToken = cachedRefreshToken
            if (refreshToken.isNullOrBlank()) {
                if (expiry != null && expiry <= now || force && expiry == null) {
                    requireReauthentication()
                    return@withLock null
                }
                return@withLock token
            }

            val result = try {
                refreshCallOverride?.invoke(refreshToken) ?: callRefreshEndpoint(refreshToken)
            } catch (e: Exception) {
                logWarning("Refresh request failed; keeping the current session when still valid", e)
                return@withLock token?.takeIf { expiry == null || expiry > now }
            }

            if (result.statusCode in 200..299) {
                val fields = try {
                    val json = Json.parseToJsonElement(result.body).jsonObject
                    Triple(
                        json["access_token"]?.jsonPrimitive?.contentOrNull
                            ?.takeIf(String::isNotBlank),
                        json["refresh_token"]?.jsonPrimitive?.contentOrNull
                            ?.takeIf(String::isNotBlank),
                        json["expires_in"]?.jsonPrimitive?.longOrNull ?: -1L
                    )
                } catch (e: Exception) {
                    logError("Refresh response was not valid JSON", e)
                    return@withLock token?.takeIf { expiry == null || expiry > now }
                }
                val newToken = fields.first
                val newRefreshToken = fields.second
                val expiresIn = fields.third
                if (newToken == null || newRefreshToken == null || expiresIn <= 0L) {
                    logError("Refresh response omitted required session fields")
                    return@withLock token?.takeIf { expiry == null || expiry > now }
                }

                val newExpiry = System.currentTimeMillis() + expiresIn * 1000L
                try {
                    storageManager?.saveAuthSession(newToken, newRefreshToken, newExpiry)
                } catch (e: Exception) {
                    logError("Could not persist the rotated Auth session", e)
                }
                cachedToken = newToken
                cachedRefreshToken = newRefreshToken
                cachedExpiry = newExpiry
                updateRealtime(newToken)
                _tokenRefreshed.tryEmit(Unit)
                return@withLock newToken
            }

            if (isUnrecoverable(result.statusCode, result.body)) {
                requireReauthentication()
                return@withLock null
            }

            logWarning("Refresh endpoint returned HTTP ${result.statusCode}")
            token?.takeIf { expiry == null || expiry > now }
        }
    }

    internal fun seedForTests(token: String?, refreshToken: String?, expiry: Long?) {
        cachedToken = token
        cachedRefreshToken = refreshToken
        cachedExpiry = expiry
        _reauthRequired.value = false
    }

    internal fun resetTestOverrides() {
        refreshCallOverride = null
        reauthHandlerOverride = null
        cachedToken = null
        cachedRefreshToken = null
        cachedExpiry = null
        _reauthRequired.value = false
        skipRealtimeForTests = false
    }

    private suspend fun callRefreshEndpoint(refreshToken: String): RefreshResult =
        withContext(Dispatchers.IO) {
            val requestBody = buildJsonObject {
                put("refresh_token", refreshToken)
            }.toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
                .post(requestBody)
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                .tag(SkipSessionAuth::class.java, SkipSessionAuth)
                .build()
            val client = SupabaseConfig.client.newBuilder()
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                RefreshResult(response.code, response.body?.string().orEmpty())
            }
        }

    private suspend fun requireReauthentication() {
        if (_reauthRequired.value) return
        val handler = reauthHandlerOverride
        if (handler != null) {
            handler()
        } else {
            clear()
        }
        _reauthRequired.value = true
    }

    private fun isUnrecoverable(statusCode: Int, body: String): Boolean {
        if (statusCode !in setOf(400, 401)) return false
        val normalized = body.lowercase()
        return listOf(
            "invalid_grant",
            "refresh token",
            "refresh_token",
            "token family",
            "revoked",
            "reuse",
            "invalid jwt",
            "jwt expired",
            "unauthorized"
        ).any(normalized::contains)
    }

    private suspend fun updateRealtime(token: String?) {
        if (skipRealtimeForTests) return
        try {
            SupabaseConfig.supabase.realtime.setAuth(token)
        } catch (e: Exception) {
            logWarning("Realtime auth update was unavailable", e)
        }
    }

    private fun logWarning(message: String, error: Exception? = null) {
        if (appContext != null) Log.w(TAG, message, error)
    }

    private fun logError(message: String, error: Exception? = null) {
        if (appContext != null) Log.e(TAG, message, error)
    }
}
