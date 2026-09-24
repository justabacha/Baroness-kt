package com.baroness.app.command.handlers

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import com.baroness.app.command.CommandHandler
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MediaCommandHandler(private val context: Context) : CommandHandler {

    private val supportedIntents = setOf(
        "play_music", "play_video", "play_voice_note",
        "pause_media", "resume_media", "stop_media",
        "next_track", "previous_track",
        "set_volume", "volume_up", "volume_down", "mute", "unmute"
    )
    private val executor = Executors.newSingleThreadExecutor()

    override fun canHandle(intent: String): Boolean {
        return supportedIntents.contains(intent)
    }

    override fun execute(intent: String, parameters: Map<String, Any?>?): Boolean {
        return when (intent) {
            "play_music" -> playMusic(parameters)
            "play_video" -> playVideo(parameters?.get("query") as? String)
            "play_voice_note" -> playVoiceNote(parameters?.get("note_id") as? String)
            "pause_media", "stop_media" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            "resume_media" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            "next_track" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "previous_track" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "set_volume" -> setVolume(parameters)
            "volume_up" -> adjustVolume(AudioManager.ADJUST_RAISE)
            "volume_down" -> adjustVolume(AudioManager.ADJUST_LOWER)
            "mute" -> toggleMute(true)
            "unmute" -> toggleMute(false)
            else -> false
        }
    }

    private fun playMusic(parameters: Map<String, Any?>?): Boolean {
        val rawQuery = (parameters?.get("query")
            ?: parameters?.get("song")
            ?: parameters?.get("title")
            ?: parameters?.get("artist")
            ?: parameters?.get("genre")) as? String

        var searchQuery = rawQuery?.trim() ?: ""

        // Strip any platform suffixes like "on spotify" or "on youtube"
        searchQuery = searchQuery
            .replace(Regex("(?i)\\s*\\bon\\s+(spotify|youtube\\s+music|yt\\s+music|youtube)\\b"), "")
            .trim()

        val packageName = resolveYouTubePackage()

        if (isGenericMusicQuery(searchQuery)) {
            return try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
                val intent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e("MediaCommandHandler", "Failed to launch music player: ${e.message}")
                false
            }
        }

        // Direct YouTube / YouTube Music watch URL resolution for instant zero-click auto-play
        val videoId = getYouTubeVideoId(searchQuery)
        val targetUrl = if (videoId != null) {
            if (packageName == "com.google.android.apps.youtube.music") {
                "https://music.youtube.com/watch?v=$videoId"
            } else {
                "https://www.youtube.com/watch?v=$videoId"
            }
        } else {
            "https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                true
            } catch (webEx: Exception) {
                Log.e("MediaCommandHandler", "Failed to play music: ${webEx.message}")
                false
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
            true
        } catch (e: Exception) {
            Log.e("MediaCommandHandler", "Failed to dispatch media key $keyCode: ${e.message}")
            false
        }
    }

    private fun setVolume(parameters: Map<String, Any?>?): Boolean {
        val level = (parameters?.get("level")
            ?: parameters?.get("volume")
            ?: parameters?.get("volume_percent")) as? Number
            ?: return false

        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val rawVal = level.toFloat()
            val targetVolume = (rawVal / 100f * maxVolume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e("MediaCommandHandler", "Failed to set volume: ${e.message}")
            false
        }
    }

    private fun adjustVolume(direction: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e("MediaCommandHandler", "Failed to adjust volume: ${e.message}")
            false
        }
    }

    private fun toggleMute(mute: Boolean): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val flag = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e("MediaCommandHandler", "Failed to toggle mute state: ${e.message}")
            false
        }
    }

    private fun isGenericMusicQuery(query: String): Boolean {
        val q = query.lowercase().trim()
        return q.isBlank() || q == "music" || q == "some music" || q == "a song" || q == "songs" || q == "play music" || q == "something"
    }

    private fun getYouTubeVideoId(query: String): String? {
        val future = executor.submit<String?> {
            try {
                val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                connection.connectTimeout = 2500
                connection.readTimeout = 2500

                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val regex = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
                val match = regex.find(html)
                match?.groupValues?.get(1)
            } catch (e: Exception) {
                Log.e("MediaCommandHandler", "Failed to resolve YouTube video ID: ${e.message}")
                null
            }
        }
        return try {
            future.get(2500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveYouTubePackage(): String {
        return when {
            isPackageInstalled("com.google.android.apps.youtube.music") -> "com.google.android.apps.youtube.music"
            isPackageInstalled("com.google.android.youtube") -> "com.google.android.youtube"
            else -> "com.google.android.apps.youtube.music"
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun playVideo(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val videoId = getYouTubeVideoId(query)
        val targetUrl = if (videoId != null) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                true
            } catch (webEx: Exception) {
                Log.e("MediaCommandHandler", "Failed to play video: ${webEx.message}")
                false
            }
        }
    }

    private fun playVoiceNote(noteId: String?): Boolean {
        if (noteId.isNullOrBlank()) return false
        Log.d("MediaCommandHandler", "Triggering Baroness internal voice note playback for: $noteId")
        return true
    }
}
