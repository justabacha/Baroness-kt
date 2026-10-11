package com.baroness.app

import com.baroness.app.auth.AuthTokenInterceptor
import com.baroness.app.auth.SessionTokenCache
import com.baroness.app.config.SupabaseConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthTokenInterceptorTest {

    @Before
    fun setUp() {
        SessionTokenCache.clear()
    }

    @After
    fun tearDown() {
        SessionTokenCache.clear()
    }

    @Test
    fun testCacheSetGetAndClear() {
        assertNull(SessionTokenCache.getToken())

        val testToken = "test_jwt_token_12345"
        SessionTokenCache.setToken(testToken)
        assertEquals(testToken, SessionTokenCache.getToken())

        SessionTokenCache.clear()
        assertNull(SessionTokenCache.getToken())
    }

    @Test
    fun testInterceptorModifiesSupabaseRequestHeader() {
        val testToken = "sample_jwt_bearer_token"
        SessionTokenCache.setToken(testToken)

        val interceptor = AuthTokenInterceptor()
        var interceptedHeader: String? = null

        val dummyChain = Interceptor { chain ->
            val request = chain.request()
            interceptedHeader = request.header("Authorization")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(dummyChain)
            .build()

        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/profiles")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .build()

        client.newCall(request).execute().close()

        assertEquals("Bearer sample_jwt_bearer_token", interceptedHeader)
    }

    @Test
    fun testInterceptorDoesNotModifyNonSupabaseRequest() {
        val testToken = "sample_jwt_bearer_token"
        SessionTokenCache.setToken(testToken)

        val interceptor = AuthTokenInterceptor()
        var interceptedHeader: String? = null

        val dummyChain = Interceptor { chain ->
            val request = chain.request()
            interceptedHeader = request.header("Authorization")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(dummyChain)
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .build()

        client.newCall(request).execute().close()

        assertNull(interceptedHeader)
    }

    @Test
    fun testInterceptorPreservesApikeyHeader() {
        val testToken = "sample_jwt_bearer_token"
        SessionTokenCache.setToken(testToken)

        val interceptor = AuthTokenInterceptor()
        var apikeyHeader: String? = null

        val dummyChain = Interceptor { chain ->
            val request = chain.request()
            apikeyHeader = request.header("apikey")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(dummyChain)
            .build()

        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/profiles")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .build()

        client.newCall(request).execute().close()

        assertEquals(SupabaseConfig.SUPABASE_ANON_KEY, apikeyHeader)
    }

    @Test
    fun testLiveFridayEndpointProbe_UnauthenticatedReturns401() {
        SessionTokenCache.clear()

        val probeUrl = "${SupabaseConfig.SUPABASE_URL}/functions/v1/friday-pending-messages?user_id=phesty_official&since=1970-01-01T00:00:00Z"
        val request = Request.Builder()
            .url(probeUrl)
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .build()

        val client = SupabaseConfig.client
        val response = client.newCall(request).execute()
        val statusCode = response.code
        response.close()

        assertEquals("Friday pending messages probe without token must return 401", 401, statusCode)
    }

    @Test
    fun testLiveFridayEndpointProbe_WithValidTokenReturns200() {
        val verifyUrl = "${SupabaseConfig.SUPABASE_URL}/functions/v1/verify-passkey"
        val jsonPayload = """{"persona":"phesty","passkey":"mr.nice_guy"}"""

        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

        val verifyRequest = Request.Builder()
            .url(verifyUrl)
            .post(requestBody)
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .build()

        val verifyResponse = SupabaseConfig.client.newCall(verifyRequest).execute()
        val verifyBody = verifyResponse.body?.string() ?: ""
        verifyResponse.close()

        // Extract token field using regex match
        val tokenMatch = """"token"\s*:\s*"([^"]+)"""".toRegex().find(verifyBody)
        val token = tokenMatch?.groupValues?.get(1)

        assertNotNull("Verify passkey should return valid JWT token. Response body: $verifyBody", token)

        SessionTokenCache.setToken(token)

        val fridayProbeUrl = "${SupabaseConfig.SUPABASE_URL}/functions/v1/friday-pending-messages?user_id=phesty_official&since=1970-01-01T00:00:00Z"
        val fridayRequest = Request.Builder()
            .url(fridayProbeUrl)
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .build()

        val fridayResponse = SupabaseConfig.client.newCall(fridayRequest).execute()
        val fridayStatusCode = fridayResponse.code
        val fridayBody = fridayResponse.body?.string() ?: ""
        fridayResponse.close()

        assertEquals("Friday pending messages probe with valid token must return 200", 200, fridayStatusCode)
        assertTrue("Friday pending messages probe response should be valid JSON array/object", fridayBody.trim().startsWith("[") || fridayBody.trim().startsWith("{"))
    }
}
