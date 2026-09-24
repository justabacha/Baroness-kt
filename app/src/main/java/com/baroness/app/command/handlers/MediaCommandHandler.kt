package com.baroness.app.command.handlers

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.baroness.app.command.CommandHandler

class MediaCommandHandler(private val context: Context) : CommandHandler {

    private val supportedIntents = setOf("play_music", "play_video", "play_voice_note")

    override fun canHandle(intent: String): Boolean {
        return supportedIntents.contains(intent)
    }

    override fun execute(intent: String, parameters: Map<String, Any?>?): Boolean {
        return when (intent) {
            "play_music" -> playMusic(parameters)
            "play_video" -> playVideo(parameters?.get("query") as? String)
            "play_voice_note" -> playVoiceNote(parameters?.get("note_id") as? String)
            else -> false
        }
    }

    private fun playMusic(parameters: Map<String, Any?>?): Boolean {
        val rawQuery = (parameters?.get("query")
            ?: parameters?.get("genre")
            ?: parameters?.get("song")
            ?: parameters?.get("artist")
            ?: parameters?.get("title")) as? String

        var targetApp = (parameters?.get("app")
            ?: parameters?.get("app_name")
            ?: parameters?.get("target_app")) as? String

        var searchQuery = rawQuery?.trim() ?: ""

        if (searchQuery.contains(Regex("(?i)\\bon\\s+spotify\\b"))) {
            targetApp = "spotify"
            searchQuery = searchQuery.replace(Regex("(?i)\\s*\\bon\\s+spotify\\b"), "").trim()
        } else if (searchQuery.contains(Regex("(?i)\\bon\\s+(youtube\\s+music|yt\\s+music|youtube)\\b"))) {
            targetApp = "youtube"
            searchQuery = searchQuery.replace(Regex("(?i)\\s*\\bon\\s+(youtube\\s+music|yt\\s+music|youtube)\\b"), "").trim()
        }

        val packageName = when (targetApp?.lowercase()?.trim()) {
            "spotify" -> "com.spotify.music"
            "youtube", "yt_music", "youtube_music", "yt" -> "com.google.android.apps.youtube.music"
            else -> null
        }

        if (searchQuery.isBlank()) {
            return try {
                if (packageName != null) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return true
                    }
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

        return try {
            val searchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(SearchManager.QUERY, searchQuery)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(MediaStore.EXTRA_MEDIA_TITLE, searchQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK

                if (packageName != null) {
                    setPackage(packageName)
                }
            }
            context.startActivity(searchIntent)
            true
        } catch (e: Exception) {
            Log.e("MediaCommandHandler", "Play from search failed for package $packageName: ${e.message}")
            try {
                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}")).apply {
                    setPackage("com.google.android.youtube")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(ytIntent)
                true
            } catch (ytEx: Exception) {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(searchQuery)}")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(webIntent)
                    true
                } catch (webEx: Exception) {
                    Log.e("MediaCommandHandler", "All media fallbacks failed: ${webEx.message}")
                    false
                }
            }
        }
    }

    private fun playVideo(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        return try {
            val encodedQuery = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
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
