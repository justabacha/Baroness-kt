package com.baroness.app.voice

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface VoiceProvider {
    suspend fun synthesize(text: String, voiceConfig: VoiceConfig): AudioResult?
    fun getAvailableVoices(): List<VoiceOption>
    fun isAvailable(): Boolean
}

class BaronessVoiceProvider(private val client: OkHttpClient) : VoiceProvider {
    private val proxyUrl = "https://thats-baroness-p.vercel.app/api/baroness"

    override suspend fun synthesize(text: String, voiceConfig: VoiceConfig): AudioResult? {
        return try {
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
            if (!response.isSuccessful) return null

            val contentType = response.header("Content-Type")
            val bytes = response.body?.bytes()
            if (bytes == null || bytes.isEmpty()) return null

            AudioResult(
                audioData = bytes,
                format = if (contentType?.contains("wav") == true) AudioFormat.WAV else AudioFormat.MP3,
                duration = 0L
            )
        } catch (e: Exception) {
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
