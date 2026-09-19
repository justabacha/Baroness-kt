package com.baroness.app.voice

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface VoiceProvider {
    suspend fun synthesize(text: String, voiceConfig: VoiceConfig): AudioResult?
    fun getAvailableVoices(): List<VoiceOption>
    fun isAvailable(): Boolean
}

class BaronessVoiceProvider(private val baseClient: OkHttpClient) : VoiceProvider {
    private val proxyUrl = "https://thats-baroness-p.vercel.app/api/baroness"
    
    // Create a client with specific timeouts for TTS
    private val client = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun synthesize(text: String, voiceConfig: VoiceConfig): AudioResult? {
        return try {
            Log.d("VoiceProvider", "Synthesizing text: ${text.take(20)}... with provider: ${voiceConfig.provider}")
            
            val escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            val escapedNote = (voiceConfig.directorNote ?: "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            
            val jsonPayload = """
                {
                  "text": "$escapedText",
                  "provider": "${voiceConfig.provider}",
                  "voice": "${voiceConfig.voiceId}",
                  "directorNote": "$escapedNote"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(proxyUrl)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("X-Title", "AVIS System")
                .build()

            val response = client.newCall(request).execute()
            Log.d("VoiceProvider", "Response received: ${response.code}")
            
            if (!response.isSuccessful) {
                Log.e("VoiceProvider", "Request failed with code: ${response.code}. Body: ${response.body?.string()?.take(100)}")
                return null
            }

            val contentType = response.header("Content-Type")
            val bytes = response.body?.bytes()
            
            if (bytes == null || bytes.isEmpty()) {
                Log.e("VoiceProvider", "Received empty audio bytes")
                return null
            }

            Log.d("VoiceProvider", "Successfully received ${bytes.size} bytes. Format: $contentType")

            AudioResult(
                audioData = bytes,
                format = if (contentType?.contains("wav") == true) AudioFormat.WAV else AudioFormat.MP3,
                duration = 0L
            )
        } catch (e: Exception) {
            Log.e("VoiceProvider", "Exception during synthesis: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override fun getAvailableVoices(): List<VoiceOption> {
        return listOf(
            VoiceOption("aura-asteria-en", "Asteria (F)", "Female", "deepgram"),
            VoiceOption("aura-luna-en", "Luna (F)", "Female", "deepgram"),
            VoiceOption("aura-stella-en", "Stella (F)", "Female", "deepgram"),
            VoiceOption("aura-athena-en", "Athena (F)", "Female", "deepgram"),
            VoiceOption("aura-hera-en", "Hera (F)", "Female", "deepgram"),
            VoiceOption("aura-orion-en", "Orion (M)", "Male", "deepgram"),
            VoiceOption("aura-arcas-en", "Arcas (M)", "Male", "deepgram"),
            VoiceOption("aura-perseus-en", "Perseus (M)", "Male", "deepgram"),
            VoiceOption("aura-angus-en", "Angus (M)", "Male", "deepgram"),
            VoiceOption("aura-orpheus-en", "Orpheus (M)", "Male", "deepgram"),
            VoiceOption("aura-helios-en", "Helios (M)", "Male", "deepgram"),
            VoiceOption("aura-zeus-en", "Zeus (M)", "Male", "deepgram"),
            VoiceOption("en-US-marcus", "Marcus (M)", "Male", "murf"),
            VoiceOption("en-US-jenny", "Jenny (F)", "Female", "edge")
        )
    }

    override fun isAvailable(): Boolean = true
}
