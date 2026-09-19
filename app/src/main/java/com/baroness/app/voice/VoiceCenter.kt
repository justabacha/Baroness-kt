package com.baroness.app.voice

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

enum class VoiceState {
    IDLE, LOADING, PLAYING
}

class VoiceCenter(private val context: Context) {
    private val client = OkHttpClient()
    private val provider = BaronessVoiceProvider(client)
    private val cacheManager = VoiceCacheManager(context)
    
    private var exoPlayer: ExoPlayer? = null
    private var systemTts: TextToSpeech? = null
    private var isTtsReady = false
    
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentSpeakJob: Job? = null

    init {
        try {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d("VoiceCenter", "onIsPlayingChanged: $isPlaying, playbackState: $playbackState")
                        if (!isPlaying && playbackState == Player.STATE_ENDED) {
                            _state.value = VoiceState.IDLE
                        } else if (isPlaying) {
                            _state.value = VoiceState.PLAYING
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d("VoiceCenter", "onPlaybackStateChanged: $playbackState")
                        if (playbackState == Player.STATE_ENDED) {
                            _state.value = VoiceState.IDLE
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("VoiceCenter", "Player error: ${error.message}")
                        _state.value = VoiceState.IDLE
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("VoiceCenter", "ExoPlayer init failed: ${e.message}")
            e.printStackTrace()
        }
        
        systemTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                systemTts?.language = Locale.UK
                systemTts?.setSpeechRate(1.0f)
                systemTts?.setPitch(0.9f)
                isTtsReady = true
                
                systemTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = VoiceState.PLAYING
                    }
                    override fun onDone(utteranceId: String?) {
                        _state.value = VoiceState.IDLE
                    }
                    override fun onError(utteranceId: String?) {
                        _state.value = VoiceState.IDLE
                    }
                })
                Log.d("VoiceCenter", "System TTS ready")
            } else {
                Log.e("VoiceCenter", "System TTS init failed with status: $status")
            }
        }
    }

    fun chunkText(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
    }

    fun speak(text: String, voiceContext: VoiceContext) {
        Log.d("VoiceCenter", "Speak requested for text: ${text.take(30)}...")
        
        // 1. Cancel any existing speak job BEFORE starting a new one
        currentSpeakJob?.cancel()
        
        // 2. Start the new job
        currentSpeakJob = scope.launch(Dispatchers.Main) {
            try {
                // Stop players but DO NOT call stop() because it would cancel this job
                stopPlayersOnly()
                _state.value = VoiceState.LOADING
                
                val sentences = chunkText(text)
                if (sentences.isEmpty()) {
                    Log.d("VoiceCenter", "No sentences to speak")
                    _state.value = VoiceState.IDLE
                    return@launch
                }

                val player = exoPlayer
                if (player == null) {
                    Log.e("VoiceCenter", "ExoPlayer is null, falling back to system")
                    speakSystem(text)
                    return@launch
                }

                player.clearMediaItems()
                var useSystemFallback = false
                var hasStartedPlaying = false

                for (sentence in sentences) {
                    Log.d("VoiceCenter", "Processing sentence: $sentence")
                    val file = withContext(Dispatchers.IO) {
                        try {
                            if (cacheManager.isStaticContent(sentence)) {
                                val cachedFile = cacheManager.get(sentence, voiceContext.config)
                                if (cachedFile != null) {
                                    Log.d("VoiceCenter", "Cache hit for: $sentence")
                                    return@withContext cachedFile
                                }
                            }
                            
                            Log.d("VoiceCenter", "Cache miss, synthesizing: $sentence")
                            val result = provider.synthesize(sentence, voiceContext.config)
                            if (result != null) {
                                if (cacheManager.isStaticContent(sentence)) {
                                    cacheManager.put(sentence, voiceContext.config, result.audioData)
                                } else {
                                    cacheManager.createTempFile(result.audioData)
                                }
                            } else {
                                Log.e("VoiceCenter", "Synthesis returned null for: $sentence")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e("VoiceCenter", "IO Exception during processing: ${e.message}")
                            null
                        }
                    }

                    if (file != null && file.exists()) {
                        Log.d("VoiceCenter", "Adding media item: ${file.absolutePath}")
                        player.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        if (!hasStartedPlaying) {
                            Log.d("VoiceCenter", "Starting playback")
                            player.prepare()
                            player.play()
                            hasStartedPlaying = true
                        }
                    } else if (!hasStartedPlaying) {
                        // If the VERY FIRST chunk fails, we just go to system fallback immediately
                        useSystemFallback = true
                        break
                    }
                }

                if (useSystemFallback) {
                    Log.d("VoiceCenter", "Using system fallback for the whole text")
                    stopPlayersOnly()
                    speakSystem(text)
                }
            } catch (e: CancellationException) {
                Log.d("VoiceCenter", "Speak job cancelled")
            } catch (e: Exception) {
                Log.e("VoiceCenter", "Unexpected error in speak job: ${e.message}")
                e.printStackTrace()
                _state.value = VoiceState.IDLE
                speakSystem(text)
            }
        }
    }

    private fun stopPlayersOnly() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        systemTts?.stop()
    }

    private fun speakSystem(text: String) {
        if (isTtsReady) {
            Log.d("VoiceCenter", "Speaking via system TTS: ${text.take(30)}...")
            _state.value = VoiceState.PLAYING
            systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "baroness_center_utterance")
        } else {
            Log.e("VoiceCenter", "System TTS not ready for fallback")
            _state.value = VoiceState.IDLE
        }
    }

    fun stop() {
        Log.d("VoiceCenter", "Stop requested")
        currentSpeakJob?.cancel()
        stopPlayersOnly()
        _state.value = VoiceState.IDLE
    }

    fun isPlaying(): Boolean {
        return _state.value != VoiceState.IDLE
    }

    fun shutdown() {
        Log.d("VoiceCenter", "Shutdown requested")
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
        cacheManager.clearTempCache()
        _state.value = VoiceState.IDLE
    }
}
