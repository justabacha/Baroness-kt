package com.baroness.app.voice

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDomainTest {
    @Test
    fun providerAndVoiceListComeFromCanonicalRegistry() {
        assertEquals(VoiceRegistry.voices, BaronessVoiceProvider(OkHttpClient()).getAvailableVoices())
        assertEquals("deepgram", VoiceConfig("aura-zeus-en").provider)
        assertEquals("murf", VoiceConfig("en-US-marcus").provider)
        assertFalse(VoiceRegistry.voices.any { it.provider == "edge" })
    }

    @Test
    fun invalidVoiceIdsResolveToDefault() {
        assertEquals(VoiceRegistry.defaultVoice.id, VoiceRegistry.sanitize("en-US-JennyNeural"))
        val invalidConfig = VoiceConfig("not-a-supported-voice")
        assertEquals(VoiceRegistry.defaultVoice, invalidConfig.voice)
        assertEquals(VoiceRegistry.defaultVoice.provider, invalidConfig.provider)
    }

    @Test
    fun murfSettingsMapToNeutralAndBoundedApiOffsets() {
        assertEquals(0, VoiceConfig("en-US-marcus").murfRate)
        assertEquals(0, VoiceConfig("en-US-marcus").murfPitch)
        assertEquals(-50, VoiceConfig("en-US-marcus", speed = 0.5f, pitch = 0.5f).murfRate)
        assertEquals(-50, VoiceConfig("en-US-marcus", pitch = 0.5f).murfPitch)
        assertEquals(50, VoiceConfig("en-US-marcus", speed = 2.0f).murfRate)
        assertEquals(50, VoiceConfig("en-US-marcus", speed = 2.0f, pitch = 1.5f).murfPitch)
        assertTrue(VoiceConfig("en-US-marcus", speed = 5f, pitch = -2f).murfRate in -50..50)
        assertTrue(VoiceConfig("en-US-marcus", speed = 5f, pitch = -2f).murfPitch in -50..50)
    }

    @Test
    fun cacheIdentityChangesWithAudioConfiguration() {
        val baseline = VoiceConfig("en-US-marcus")
        val baselineKey = VoiceCacheManager.cacheIdentity("hello", baseline)

        assertNotEquals(baselineKey, VoiceCacheManager.cacheIdentity("hello", baseline.copy(speed = 1.2f)))
        assertNotEquals(baselineKey, VoiceCacheManager.cacheIdentity("hello", baseline.copy(pitch = 1.2f)))
        assertNotEquals(baselineKey, VoiceCacheManager.cacheIdentity("hello", baseline.copy(directorNote = "Warm")))
        assertNotEquals(baselineKey, VoiceCacheManager.cacheIdentity("hello", baseline.copy(language = "en-GB")))
        assertNotEquals(baselineKey, VoiceCacheManager.cacheIdentity("hello", VoiceConfig("aura-zeus-en")))
    }
}
