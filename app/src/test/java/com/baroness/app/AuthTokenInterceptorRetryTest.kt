package com.baroness.app

import com.baroness.app.auth.AuthTokenInterceptor
import com.baroness.app.config.SupabaseConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AuthTokenInterceptorRetryTest {
    @Test
    fun attachesCachedTokenToSupabaseRequest() {
        var authorization: String? = null
        val client = testClient(
            AuthTokenInterceptor(tokenProvider = { "cached-token" }) { "unused" },
            Interceptor { chain ->
                authorization = chain.request().header("Authorization")
                response(chain, 200)
            }
        )

        client.newCall(supabaseRequest()).execute().close()
        assertEquals("Bearer cached-token", authorization)
    }

    @Test
    fun retries401OnceWithRefreshedToken() {
        val attempts = AtomicInteger()
        val refreshes = AtomicInteger()
        val authorizations = mutableListOf<String?>()
        val client = testClient(
            AuthTokenInterceptor(
                tokenProvider = { "old-token" },
                refreshToken = {
                    refreshes.incrementAndGet()
                    "new-token"
                }
            ),
            Interceptor { chain ->
                authorizations += chain.request().header("Authorization")
                response(chain, if (attempts.incrementAndGet() == 1) 401 else 200)
            }
        )

        val result = client.newCall(supabaseRequest()).execute()
        result.close()

        assertEquals(200, result.code)
        assertEquals(2, attempts.get())
        assertEquals(1, refreshes.get())
        assertEquals(listOf("Bearer old-token", "Bearer new-token"), authorizations)
    }

    @Test
    fun doesNotRetryAfterSecond401() {
        val attempts = AtomicInteger()
        val refreshes = AtomicInteger()
        val client = testClient(
            AuthTokenInterceptor(
                tokenProvider = { "old-token" },
                refreshToken = {
                    refreshes.incrementAndGet()
                    "new-token"
                }
            ),
            Interceptor { chain ->
                attempts.incrementAndGet()
                response(chain, 401)
            }
        )

        val result = client.newCall(supabaseRequest()).execute()
        result.close()

        assertEquals(401, result.code)
        assertEquals(2, attempts.get())
        assertEquals(1, refreshes.get())
    }

    private fun testClient(vararg interceptors: Interceptor): OkHttpClient =
        OkHttpClient.Builder().apply {
            interceptors.forEach(::addInterceptor)
        }.build()

    private fun supabaseRequest(): Request = Request.Builder()
        .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/profiles")
        .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
        .build()

    private fun response(chain: Interceptor.Chain, code: Int): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Unauthorized")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
}
