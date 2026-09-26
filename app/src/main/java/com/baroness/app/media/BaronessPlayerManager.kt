package com.baroness.app.media

import android.content.Context
import android.content.Intent
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

enum class BaronessRepeatMode { OFF, ONE, ALL }

class BaronessPlayerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var mediaSession: MediaSession? = null
    private var visualizer: Visualizer? = null

    private val _currentSong = MutableStateFlow<LocalMusicSong?>(null)
    val currentSong: StateFlow<LocalMusicSong?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(BaronessRepeatMode.OFF)
    val repeatMode: StateFlow<BaronessRepeatMode> = _repeatMode.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<LocalMusicSong>>(emptyList())
    val currentQueue: StateFlow<List<LocalMusicSong>> = _currentQueue.asStateFlow()

    private val _audioAmplitudes = MutableStateFlow(FloatArray(12) { 0.15f })
    val audioAmplitudes: StateFlow<FloatArray> = _audioAmplitudes.asStateFlow()

    private var currentPlaylist: List<LocalMusicSong> = emptyList()

    init {
        try {
            mediaSession = MediaSession.Builder(appContext, exoPlayer).build()
        } catch (e: Exception) {
            Log.e("BaronessPlayerManager", "Failed to create MediaSession: ${e.message}")
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                notifyServiceUpdate()
                if (isPlaying) {
                    setupVisualizer()
                } else {
                    releaseVisualizer()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                if (index in currentPlaylist.indices) {
                    _currentSong.value = currentPlaylist[index]
                    notifyServiceUpdate()
                    Log.d("BaronessPlayerManager", "Auto-transitioned to song: ${currentPlaylist[index].title}")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    releaseVisualizer()
                    notifyServiceUpdate()
                    Log.d("BaronessPlayerManager", "Playback reached end of playlist.")
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("BaronessPlayerManager", "ExoPlayer error: ${error.message}")
                _isPlaying.value = false
                releaseVisualizer()
                notifyServiceUpdate()
            }
        })

        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _currentPositionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                }
                delay(400)
            }
        }
    }

    private fun notifyServiceUpdate() {
        try {
            val serviceIntent = Intent(appContext, BaronessMediaService::class.java)
            appContext.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("BaronessPlayerManager", "Failed to update BaronessMediaService: ${e.message}")
        }
    }

    @OptIn(UnstableApi::class)
    private fun setupVisualizer() {
        try {
            releaseVisualizer()
            val audioSessionId = exoPlayer.audioSessionId
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null && fft.size >= 24) {
                                val amps = FloatArray(12)
                                for (i in 0 until 12) {
                                    val r = fft[i * 2].toInt()
                                    val im = fft[i * 2 + 1].toInt()
                                    val mag = hypot(r.toDouble(), im.toDouble()).toFloat()
                                    amps[i] = (mag / 80f).coerceIn(0.15f, 1f)
                                }
                                _audioAmplitudes.value = amps
                            }
                        }

                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.e("BaronessPlayerManager", "Visualizer setup failed: ${e.message}")
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            Log.e("BaronessPlayerManager", "Failed to release visualizer: ${e.message}")
        }
    }

    fun getMediaSession(): MediaSession? = mediaSession

    fun playLocalSongs(songs: List<LocalMusicSong>, startIndex: Int = 0) {
        if (songs.isEmpty()) {
            Log.w("BaronessPlayerManager", "playLocalSongs called with empty song list")
            return
        }

        currentPlaylist = songs
        _currentQueue.value = songs

        val mediaItems = songs.map { song ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.albumArtUri)
                .build()

            MediaItem.Builder()
                .setUri(song.contentUri)
                .setMediaMetadata(metadata)
                .build()
        }

        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        exoPlayer.setMediaItems(mediaItems, safeIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()

        _currentSong.value = songs[safeIndex]
        _isPlaying.value = true

        try {
            val serviceIntent = Intent(appContext, BaronessMediaService::class.java)
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (e: Exception) {
            Log.e("BaronessPlayerManager", "Failed to start BaronessMediaService: ${e.message}")
        }

        Log.d("BaronessPlayerManager", "Started playing local playlist (${songs.size} songs). Active: ${songs[safeIndex].title}")
    }

    fun toggleShuffle() {
        val newMode = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newMode
        exoPlayer.shuffleModeEnabled = newMode
        notifyServiceUpdate()
    }

    fun toggleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            BaronessRepeatMode.OFF -> BaronessRepeatMode.ONE
            BaronessRepeatMode.ONE -> BaronessRepeatMode.ALL
            BaronessRepeatMode.ALL -> BaronessRepeatMode.OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = when (nextMode) {
            BaronessRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            BaronessRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            BaronessRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        notifyServiceUpdate()
    }

    fun seekToPosition(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun seekToItem(index: Int) {
        if (index in currentPlaylist.indices) {
            exoPlayer.seekTo(index, 0L)
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
        }
    }

    fun playPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0, 0L)
            }
            exoPlayer.play()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        exoPlayer.play()
    }

    fun stop() {
        releaseVisualizer()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _isPlaying.value = false
        _currentSong.value = null
        _currentQueue.value = emptyList()
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        notifyServiceUpdate()
    }

    fun skipNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    fun release() {
        releaseVisualizer()
        mediaSession?.release()
        mediaSession = null
        exoPlayer.release()
        scope.cancel()
    }

    companion object {
        @Volatile
        private var INSTANCE: BaronessPlayerManager? = null

        fun getInstance(context: Context): BaronessPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BaronessPlayerManager(context).also { INSTANCE = it }
            }
        }
    }
}
