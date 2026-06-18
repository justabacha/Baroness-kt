package com.baroness.app.utils

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.baroness.app.models.UserProfile
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.*

class VoiceManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var isPlaying = false
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val pendingText = mutableListOf<String>()
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val remoteTtsUrl = "https://thats-baroness-p.vercel.app"

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(0.9f)
                isTtsReady = true
                pendingText.forEach { speak(it) }
                pendingText.clear()
            } else {
                isTtsReady = false
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { isPlaying = false }

            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) { isPlaying = false }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isPlaying = false
            }
        })
    }

    fun buildAnnouncementMessage(
        userProfile: UserProfile?,
        greeting: String,
        weatherSuggestion: String
    ): String {
        val now = Calendar.getInstance()
        val dayName = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.UK) ?: "Monday"
        val month = now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.UK) ?: "January"
        val dateStr = "${now.get(Calendar.DAY_OF_MONTH)} $month"
        var hours = now.get(Calendar.HOUR_OF_DAY)
        val minutes = now.get(Calendar.MINUTE)
        val amPmStr = if (hours >= 12) "PM" else "AM"
        hours = if (hours % 12 == 0) 12 else hours % 12
        val minutesStr = when (minutes) {
            0 -> "o'clock"
            in 1..9 -> "oh $minutes"
            else -> minutes.toString()
        }
        val period = if (amPmStr == "AM") "morning" else "evening"
        val timeForVoice = "$hours $minutesStr in the $period"

        val welcome = userProfile?.displayName?.let { "Hi $it" } ?: "Hi there"
        val introVariants = listOf("Quick update,", "Here's where we are,", "Right now,")
        val intro = introVariants.random()

        val cleanStatus = weatherSuggestion.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "").trim()
        return "$welcome. $greeting... $intro it's $dayName, $dateStr. The time is $timeForVoice. Just so you know, $cleanStatus."
    }

    fun speak(text: String) {
        if (isPlaying) return
        if (remoteTtsUrl.isNotBlank()) {
            speakRemote(text)
        } else {
            speakSystem(text)
        }
    }

    private fun speakRemote(text: String) {
        isPlaying = true
        scope.launch {
            try {
                val json = "{\"text\":\"$text\"}"
                val request = Request.Builder()
                    .url("$remoteTtsUrl/api/speak")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                val audioBytes = response.body?.bytes()
                if (audioBytes == null || audioBytes.isEmpty()) throw Exception("Empty audio")

                val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { it.write(audioBytes) }

                withContext(Dispatchers.Main) {
                    this@VoiceManager.mediaPlayer?.release()
                    this@VoiceManager.mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            this@VoiceManager.isPlaying = false
                            this@VoiceManager.mediaPlayer?.release()
                            this@VoiceManager.mediaPlayer = null
                            tempFile.delete()
                        }
                        setOnErrorListener { _, _, _ ->
                            this@VoiceManager.isPlaying = false
                            this@VoiceManager.mediaPlayer = null
                            tempFile.delete()
                            speakSystem(text)
                            true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    speakSystem(text)
                }
            }
        }
    }

    private fun speakSystem(text: String) {
        if (!isTtsReady) {
            pendingText.add(text)
            return
        }
        tts?.let {
            isPlaying = true
            it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "baroness_utterance")
        } ?: run {
            isPlaying = false
        }
    }

    fun shutdown() {
        scope.cancel()
        mediaPlayer?.release()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}