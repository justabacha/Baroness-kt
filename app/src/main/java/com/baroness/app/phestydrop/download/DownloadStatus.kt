package com.baroness.app.phestydrop.download

import android.net.Uri

sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data class Downloading(
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long
    ) : DownloadStatus()
    data class Ready(val uri: Uri, val versionName: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
