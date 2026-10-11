package com.baroness.app.auth

import android.util.Log
import com.baroness.app.config.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

internal object SkipSessionAuth

class AuthTokenInterceptor(
    private val tokenProvider: () -> String? = SessionTokenCache::getTokenSync,
    private val refreshToken: suspend (Boolean) -> String? = { force ->
        SessionTokenCache.refreshIfNeeded(force)
    }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.tag(SkipSessionAuth::class.java) != null) {
            return chain.proceed(originalRequest)
        }

        val url = originalRequest.url.toString()
        if (!isSupabaseUrl(url) || url.contains("/functions/v1/verify-passkey")) {
            return chain.proceed(originalRequest)
        }

        try {
            runBlocking(Dispatchers.IO) { SessionTokenCache.awaitReady() }
        } catch (e: Exception) {
            Log.w("AuthTokenInterceptor", "Session cache was not ready before the request", e)
        }
        val token = tokenProvider()
        val request = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        val firstResponse = chain.proceed(request)
        val authorization = request.header("Authorization")
        if (firstResponse.code != 401 || authorization.isNullOrBlank()) {
            return firstResponse
        }

        val refreshedToken = try {
            runBlocking(Dispatchers.IO) { refreshToken(true) }
        } catch (e: Exception) {
            Log.w("AuthTokenInterceptor", "401 refresh attempt failed", e)
            null
        }
        if (refreshedToken.isNullOrBlank() || refreshedToken == authorization.removePrefix("Bearer ")) {
            return firstResponse
        }

        firstResponse.close()
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $refreshedToken")
                .build()
        )
    }

    private fun isSupabaseUrl(url: String): Boolean {
        return try {
            val targetHost = URI(url).host
            val supabaseHost = URI(SupabaseConfig.SUPABASE_URL).host
            targetHost != null && targetHost.equals(supabaseHost, ignoreCase = true)
        } catch (_: Exception) {
            url.contains(SupabaseConfig.SUPABASE_URL)
        }
    }
}
