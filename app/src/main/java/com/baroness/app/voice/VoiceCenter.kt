package com.baroness.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

class VoiceCenter(private val context: Context) {
    private val client = OkHttpClient()
    private val provider = BaronessVoiceProvider(client)
    private val cacheManager = VoiceCacheManager(context)
    
    private var exoPlayer: ExoPlayer? = null
    private var systemTts: TextToSpeech? = null
    private var isTtsReady = false
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        scope.launch(Dispatchers.Main) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }
        
        systemTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                systemTts?.language = Locale.UK
                systemTts?.setSpeechRate(1.0f)
                systemTts?.setPitch(0.9f)
                isTtsReady = true
            }
        }
    }

    fun chunkText(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
    }

    fun speak(text: String, voiceContext: VoiceContext) {
        scope.launch(Dispatchers.Main) {
            stop()
            
            val sentences = chunkText(text)
            if (sentences.isEmpty()) return@launch

            // Fetch / synthesize sentences in parallel on IO dispatcher
            val audioFiles = withContext(Dispatchers.IO) {
                sentences.map { sentence ->
                    async {
                        if (cacheManager.isStaticContent(sentence)) {
                            val cachedFile = cacheManager.get(sentence, voiceContext.config)
                            if (cachedFile != null) {
                                return@async sentence to cachedFile
                            }
                        }
                        
                        // Cache miss or dynamic content -> Remote synthesis
                        val result = provider.synthesize(sentence, voiceContext.config)
                        if (result != null) {
                            if (cacheManager.isStaticContent(sentence)) {
                                val savedFile = cacheManager.put(sentence, voiceContext.config, result.audioData)
                                if (savedFile != null) return@async sentence to savedFile
                            } else {
                                val tempFile = cacheManager.createTempFile(result.audioData)
                                if (tempFile != null) return@async sentence to tempFile
                            }
                        }
                        
                        // Fallback to null indicating system TTS needed for this sentence
                        sentence to null
                    }
                }.awaitAll()
            }

            // Play the files sequentially via ExoPlayer, or use system TTS fallback
            playAudioSequence(audioFiles)
        }
    }

    private fun playAudioSequence(audioFiles: List<Pair<String, File?>>) {
        val player = exoPlayer ?: return
        player.stop()
        player.clearMediaItems()

        var containsSystemTts = false
        
        for ((sentence, file) in audioFiles) {
            if (file != null && !containsSystemTts) {
                player.addMediaItem(MediaItem.fromUri(file.absolutePath))
            } else {
                // If any sentence fails, or to keep simplicity, we can play whatever we got,
                // or if it's purely a system fallback scenario, speak via system TTS.
                containsSystemTts = true
            }
        }

        if (containsSystemTts || audioFiles.all { it.second == null }) {
            // Full or partial fallback to system TTS for unbroken flow if network is down
            val fullText = audioFiles.joinToString(" ") { it.first }
            speakSystem(fullText)
        } else {
            player.prepare()
            player.play()
        }
    }

    private fun speakSystem(text: String) {
        if (isTtsReady) {
            systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "baroness_center_utterance")
        }
    }

    fun stop() {
        exoPlayer?.stop()
        systemTts?.stop()
    }

    fun isPlaying(): Boolean {
        return (exoPlayer?.isPlaying == true) || (systemTts?.isSpeaking == true)
    }

    fun shutdown() {
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
        cacheManager.clearTempCache()
    }
}
