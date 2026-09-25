package com.baroness.app.command.handlers

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import com.baroness.app.command.CommandHandler
import com.baroness.app.media.BaronessPlayerManager
import com.baroness.app.media.LocalMusicRepository
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private data class CleanedQuery(val query: String, val isExplicitLocal: Boolean)

class MediaCommandHandler(private val context: Context) : CommandHandler {

    private val supportedIntents = setOf(
        "play_music", "play_video", "play_voice_note",
        "pause_media", "resume_media", "stop_media",
        "next_track", "previous_track",
        "set_volume", "volume_up", "volume_down", "mute", "unmute"
    )
    private val executor = Executors.newSingleThreadExecutor()
    private val localRepository = LocalMusicRepository(context)
    private val playerManager by lazy { BaronessPlayerManager.getInstance(context) }

    override fun canHandle(intent: String): Boolean {
        return supportedIntents.contains(intent)
    }

    override fun execute(intent: String, parameters: Map<String, Any?>?): Boolean {
        return when (intent) {
            "play_music" -> playMusic(parameters)
            "play_video" -> playVideo(parameters?.get("query") as? String)
            "play_voice_note" -> playVoiceNote(parameters?.get("note_id") as? String)
            "pause_media" -> handlePause()
            "stop_media" -> handleStop()
            "resume_media" -> handleResume()
            "next_track" -> handleNext()
            "previous_track" -> handlePrevious()
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
            ?: parameters?.get("genre")) as? String ?: ""

        val cleaned = cleanSearchQuery(rawQuery)
        val searchQuery = cleaned.query
        val isExplicitLocal = cleaned.isExplicitLocal

        // 1. Check local music matching query
        if (!searchQuery.isBlank()) {
            val localMatches = localRepository.searchLocalSongs(searchQuery)
            if (localMatches.isNotEmpty()) {
                Log.d("MediaCommandHandler", "Found ${localMatches.size} local songs matching '$searchQuery'. Playing in-app.")
                playerManager.playLocalSongs(localMatches)
                return true
            }
        }

        // 2. Explicit local playlist or offline music requested
        if (isExplicitLocal || isLocalPlaylistQuery(searchQuery)) {
            val allLocalSongs = localRepository.scanLocalSongs()
            if (allLocalSongs.isNotEmpty()) {
                Log.d("MediaCommandHandler", "Playing all local songs (${allLocalSongs.size} tracks).")
                playerManager.playLocalSongs(allLocalSongs.shuffled())
                return true
            }
        }

        val packageName = resolveYouTubePackage()

        // 3. Generic music query ("play music", "play something", etc.)
        if (isGenericMusicQuery(searchQuery)) {
            val allLocalSongs = localRepository.scanLocalSongs()
            if (allLocalSongs.isNotEmpty()) {
                playerManager.playLocalSongs(allLocalSongs.shuffled())
                return true
            }

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

        // 4. Online YouTube / YouTube Music watch URL resolution for instant auto-play
        val videoId = getYouTubeVideoId(searchQuery)
        val targetUrl = if (videoId != null) {
            if (packageName == "com.google.android.apps.youtube.music") {
                "https://music.youtube.com/watch?v=$videoId"
            } else {
                "https://www.youtube.com/watch?v=$videoId"
            }
        } else {
            if (packageName == "com.google.android.apps.youtube.music") {
                "https://music.youtube.com/search?q=${Uri.encode(searchQuery)}"
            } else {
                "https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}"
            }
        }

        Log.d("MediaCommandHandler", "Targeting package=$packageName with url=$targetUrl (videoId=$videoId)")

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("MediaCommandHandler", "Explicit launch to $packageName failed: ${e.javaClass.simpleName} - ${e.message}")
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

    private fun cleanSearchQuery(query: String): CleanedQuery {
        var q = query.trim()
        val isExplicitLocal = q.contains(Regex("(?i)\\b(from|on)\\s+(my\\s+)?(local|phone|offline|files|storage)\\b")) ||
                q.contains(Regex("(?i)\\b(local|offline)\\b"))

        q = q.replace(Regex("(?i)\\s*\\b(from|on)\\s+(my\\s+)?(local\\s+music|local|phone|offline|youtube\\s+music|yt\\s+music|youtube|spotify|files|storage)\\b"), "")
            .replace(Regex("(?i)\\s*\\b(offline|local)\\b"), "")
            .trim()

        return CleanedQuery(q, isExplicitLocal)
    }

    private fun handlePause(): Boolean {
        if (playerManager.isPlaying.value) {
            playerManager.pause()
            return true
        }
        return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    private fun handleResume(): Boolean {
        if (playerManager.currentSong.value != null && !playerManager.isPlaying.value) {
            playerManager.resume()
            return true
        }
        return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    private fun handleStop(): Boolean {
        playerManager.stop()
        return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
    }

    private fun handleNext(): Boolean {
        if (playerManager.currentSong.value != null) {
            playerManager.skipNext()
            return true
        }
        return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    private fun handlePrevious(): Boolean {
        if (playerManager.currentSong.value != null) {
            playerManager.skipPrevious()
            return true
        }
        return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
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

    private fun isLocalPlaylistQuery(query: String): Boolean {
        val q = query.lowercase().trim()
        return q.contains("local") || q.contains("my playlist") || q.contains("my songs") || q.contains("offline") || q.contains("my music") || q.contains("my files")
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
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connection.setRequestProperty("Cookie", "CONSENT=YES+1")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                connection.connectTimeout = 3500
                connection.readTimeout = 3500

                Log.d("MediaCommandHandler", "YouTube search responseCode=${connection.responseCode}")

                val html = connection.inputStream.bufferedReader().use { it.readText() }
                if (html.contains("consent.youtube.com", ignoreCase = true)) {
                    Log.w("MediaCommandHandler", "Hit YouTube's cookie-consent page instead of real results (len=${html.length})")
                }
                
                val videoIdRegexes = listOf(
                    Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})""""),
                    Regex("""/watch\?v=([a-zA-Z0-9_-]{11})"""),
                    Regex(""""watchEndpoint"\s*:\s*\{\s*"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})""""),
                    Regex(""""url"\s*:\s*"/watch\?v=([a-zA-Z0-9_-]{11})"""")
                )

                for (regex in videoIdRegexes) {
                    val match = regex.find(html)
                    if (match != null) {
                        val foundId = match.groupValues[1]
                        Log.d("MediaCommandHandler", "Resolved videoId=$foundId for query='$query'")
                        return@submit foundId
                    }
                }

                Log.w("MediaCommandHandler", "Could not find videoId in YouTube HTML (len=${html.length})")
                null
            } catch (e: Exception) {
                Log.e("MediaCommandHandler", "Failed to resolve YouTube video ID: ${e.message}")
                null
            }
        }
        return try {
            future.get(3500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveYouTubePackage(): String {
        val musicInstalled = isPackageInstalled("com.google.android.apps.youtube.music")
        val youtubeInstalled = isPackageInstalled("com.google.android.youtube")
        Log.d("MediaCommandHandler", "YT Music installed=$musicInstalled, YouTube installed=$youtubeInstalled")
        return when {
            musicInstalled -> "com.google.android.apps.youtube.music"
            youtubeInstalled -> "com.google.android.youtube"
            else -> "com.google.android.apps.youtube.music"
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            Log.w("MediaCommandHandler", "getPackageInfo failed for $packageName: ${e.javaClass.simpleName} - ${e.message}")
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
