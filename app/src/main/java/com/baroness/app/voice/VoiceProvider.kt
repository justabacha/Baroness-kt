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
                  "voice": "${voiceConfig.voice.id}",
                  "directorNote": "$escapedNote",
                  "murfRate": ${voiceConfig.murfRate},
                  "murfPitch": ${voiceConfig.murfPitch}
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
        return VoiceRegistry.voices
    }

    override fun isAvailable(): Boolean = true
}
