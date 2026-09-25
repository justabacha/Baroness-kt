package com.baroness.app.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

class LocalMusicRepository(private val context: Context) {

    fun scanLocalSongs(): List<LocalMusicSong> {
        val songs = mutableListOf<LocalMusicSong>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                val artworkUriBase = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val displayName = cursor.getString(displayNameColumn) ?: ""
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val albumArtUri: Uri? = if (albumId > 0) {
                        ContentUris.withAppendedId(artworkUriBase, albumId)
                    } else null

                    songs.add(
                        LocalMusicSong(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            displayName = displayName,
                            duration = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("LocalMusicRepository", "Failed to scan local songs: ${e.message}")
        }

        return songs
    }

    fun searchLocalSongs(query: String): List<LocalMusicSong> {
        val allSongs = scanLocalSongs()
        if (query.isBlank()) return allSongs

        val queryTokens = extractWords(query)
        if (queryTokens.isEmpty()) return allSongs

        return allSongs.filter { song ->
            val songText = "${song.title} ${song.artist} ${song.album} ${song.displayName}"
            val songTokens = extractWords(songText)

            queryTokens.all { qWord ->
                songTokens.any { sWord ->
                    sWord == qWord || sWord.startsWith(qWord) || qWord.startsWith(sWord) ||
                            (qWord.length >= 3 && sWord.contains(qWord))
                }
            }
        }
    }

    private fun extractWords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("\\.(mp3|m4a|flac|wav|aac|ogg|wma)$"), " ")
            .replace(Regex("[^a-z0-9]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
    }
}
