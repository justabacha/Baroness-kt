package com.baroness.app

import com.baroness.app.auth.SessionTokenCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionTokenCacheTest {
    @Before
    fun setUp() {
        SessionTokenCache.resetTestOverrides()
        SessionTokenCache.skipRealtimeForTests = true
    }

    @After
    fun tearDown() {
        SessionTokenCache.resetTestOverrides()
    }

    @Test
    fun validTokenIsReturnedWithoutRefresh() = runBlocking {
        val calls = AtomicInteger()
        SessionTokenCache.seedForTests("access", "refresh", System.currentTimeMillis() + 3_600_000L)
        SessionTokenCache.refreshCallOverride = {
            calls.incrementAndGet()
            error("refresh should not run")
        }

        assertEquals("access", SessionTokenCache.refreshIfNeeded())
        assertEquals(0, calls.get())
    }

    @Test
    fun expiredTokenRefreshUpdatesCachedSession() = runBlocking {
        val calls = AtomicInteger()
        SessionTokenCache.seedForTests("old-access", "old-refresh", System.currentTimeMillis() - 1L)
        SessionTokenCache.refreshCallOverride = {
            calls.incrementAndGet()
            SessionTokenCache.RefreshResult(
                200,
                """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}"""
            )
        }

        assertEquals("new-access", SessionTokenCache.refreshIfNeeded())
        assertEquals(1, calls.get())
        assertEquals("new-access", SessionTokenCache.getTokenSync())
        assertEquals("new-refresh", SessionTokenCache.getRefreshToken())
        assertTrue(SessionTokenCache.getExpiry()!! > System.currentTimeMillis())
    }

    @Test
    fun successfulRefreshEmitsTokenRefreshedSignal() = runBlocking {
        SessionTokenCache.seedForTests("old-access", "old-refresh", System.currentTimeMillis() - 1L)
        SessionTokenCache.refreshCallOverride = {
            SessionTokenCache.RefreshResult(
                200,
                """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}"""
            )
        }
        val signal = async(start = CoroutineStart.UNDISPATCHED) {
            SessionTokenCache.tokenRefreshedFlow.first()
        }

        SessionTokenCache.refreshIfNeeded(force = true)

        signal.await()
        assertEquals("new-access", SessionTokenCache.getTokenSync())
    }

    @Test
    fun concurrentForcedRefreshesShareOneNetworkCall() = runBlocking {
        val calls = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        SessionTokenCache.seedForTests("old-access", "old-refresh", System.currentTimeMillis() - 1L)
        SessionTokenCache.refreshCallOverride = {
            calls.incrementAndGet()
            entered.complete(Unit)
            release.await()
            SessionTokenCache.RefreshResult(
                200,
                """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}"""
            )
        }

        val first = async(Dispatchers.Default) { SessionTokenCache.refreshIfNeeded(force = true) }
        entered.await()
        val second = async(Dispatchers.Default) { SessionTokenCache.refreshIfNeeded(force = true) }
        delay(50)
        withContext(Dispatchers.Default) { release.complete(Unit) }

        assertEquals("new-access", first.await())
        assertEquals("new-access", second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun invalidGrantClearsCacheAndSignalsReauthentication() = runBlocking {
        SessionTokenCache.seedForTests("old-access", "revoked-refresh", System.currentTimeMillis() - 1L)
        SessionTokenCache.reauthHandlerOverride = { SessionTokenCache.clear() }
        SessionTokenCache.refreshCallOverride = {
            SessionTokenCache.RefreshResult(400, """{"code":"invalid_grant"}""")
        }

        assertNull(SessionTokenCache.refreshIfNeeded(force = true))
        assertNull(SessionTokenCache.getTokenSync())
        assertTrue(SessionTokenCache.reauthRequired.value)
    }

    @Test
    fun networkFailurePreservesStillValidToken() = runBlocking {
        SessionTokenCache.seedForTests("still-valid", "refresh", System.currentTimeMillis() + 60_000L)
        SessionTokenCache.refreshCallOverride = { throw java.io.IOException("offline") }

        assertEquals("still-valid", SessionTokenCache.refreshIfNeeded(force = true))
        assertEquals("still-valid", SessionTokenCache.getTokenSync())
        assertFalse(SessionTokenCache.reauthRequired.value)
    }

    @Test
    fun networkFailureReturnsNullForExpiredTokenWithoutClearingIt() = runBlocking {
        SessionTokenCache.seedForTests("expired", "refresh", System.currentTimeMillis() - 1L)
        SessionTokenCache.refreshCallOverride = { throw java.io.IOException("offline") }

        assertNull(SessionTokenCache.refreshIfNeeded(force = true))
        assertEquals("expired", SessionTokenCache.getTokenSync())
        assertFalse(SessionTokenCache.reauthRequired.value)
    }
}
