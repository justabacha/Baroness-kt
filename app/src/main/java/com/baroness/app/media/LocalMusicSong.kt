package com.baroness.app.media

import android.net.Uri

data class LocalMusicSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val displayName: String = "",
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri? = null
)
