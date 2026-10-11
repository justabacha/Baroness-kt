package com.baroness.app.auth

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.UserSessionManager
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "SessionLifecycle"

object SessionLifecycleManager {
    suspend fun logout(context: Context, revokeServerSession: Boolean = true) {
        val appContext = context.applicationContext

        if (revokeServerSession) {
            runStep("Server session revocation") {
                SessionTokenCache.awaitReady()
                val token = SessionTokenCache.getTokenSync()
                if (!token.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        val logoutClient = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .writeTimeout(5, TimeUnit.SECONDS)
                            .callTimeout(5, TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder()
                            .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/logout")
                            .post(ByteArray(0).toRequestBody(null))
                            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer $token")
                            .tag(SkipSessionAuth::class.java, SkipSessionAuth)
                            .build()
                        logoutClient.newCall(request)
                            .execute()
                            .use { response ->
                                if (!response.isSuccessful) {
                                    Log.w(TAG, "Server logout returned HTTP ${response.code}")
                                }
                            }
                    }
                }
            }
        }

        runStep("Realtime disconnect") {
            SupabaseConfig.supabase.realtime.disconnect()
        }

        runStep("WorkManager cancellation") {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(appContext).cancelAllWork().result.get()
            }
        }

        val database = try {
            AppDatabase.getInstance(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Room database was unavailable during logout", e)
            null
        }
        if (database != null) {
            runStep("Clear messages") { database.messageDao().clearAll() }
            // Wishlist data is shared across personas, so preserve it across logout.
            runStep("Clear sync queue") { database.syncQueueDao().clearAll() }
        }

        runStep("Clear DataStore session") {
            StorageManager(appContext).clearSessionData()
        }
        runStep("Clear SharedPreferences session") {
            UserSessionManager(appContext).clearSession()
        }
        runStep("Clear in-memory session") {
            SessionTokenCache.clear()
        }
    }

    private suspend fun runStep(name: String, action: suspend () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.e(TAG, "$name failed; continuing local logout", e)
        }
    }
}
