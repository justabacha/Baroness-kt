package com.baroness.app.phestydrop.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.baroness.app.BuildConfig
import com.baroness.app.phestydrop.data.PhestyDropInfo
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

object PhestyDropDownloader {
    private const val TAG = "PhestyDropDownloader"
    private const val FILE_PROVIDER_AUTHORITY = "com.baroness.app.fileprovider"
    private const val DOWNLOAD_ID_KEY = "phestydrop_download_id"
    private const val VERSION_NAME_KEY = "phestydrop_download_version_name"
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status: StateFlow<DownloadStatus> = mutableStatus.asStateFlow()

    @Volatile
    private var activeDownloadId: Long? = null
    private var receiverRegistered = false
    private var pollingJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (completedId >= 0L) {
                        scope.launch { handleCompletion(context.applicationContext, completedId) }
                    }
                }
            }
        }
    }

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                }
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                receiverRegistered = true
            }
        }
        scope.launch {
            val cachedFile = scanAndPruneCachedApks(appContext)
            if (cachedFile != null) {
                val info = appContext.packageManager.getPackageArchiveInfo(cachedFile.absolutePath, 0)
                val versionName = info?.versionName ?: "unknown"
                val uri = FileProvider.getUriForFile(
                    appContext,
                    FILE_PROVIDER_AUTHORITY,
                    cachedFile
                )
                mutableStatus.value = DownloadStatus.Ready(uri, versionName)
                try {
                    StorageManager(appContext).saveString(VERSION_NAME_KEY, versionName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist cached versionName", e)
                }
                Log.i(TAG, "Restored state to Ready using cached APK: ${cachedFile.name}")
            } else {
                restoreState(appContext)
            }
        }
    }

    fun startDownload(context: Context, dropInfo: PhestyDropInfo) {
        val appContext = context.applicationContext
        val cachedFile = File(
            File(appContext.getExternalFilesDir(null), "phestydrop"),
            "phesty_drop_v${dropInfo.versionName}.apk"
        )
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            val info = appContext.packageManager.getPackageArchiveInfo(cachedFile.absolutePath, 0)
            if (info != null && extractVersionCode(info) > BuildConfig.VERSION_CODE) {
                val uri = FileProvider.getUriForFile(
                    appContext,
                    FILE_PROVIDER_AUTHORITY,
                    cachedFile
                )
                mutableStatus.value = DownloadStatus.Ready(uri, dropInfo.versionName)
                Log.i(TAG, "Reusing cached APK for version ${dropInfo.versionName}")
                return
            } else {
                Log.i(TAG, "Deleting invalid cached APK for version ${dropInfo.versionName}")
                cachedFile.delete()
            }
        }

        synchronized(lock) {
            if (mutableStatus.value is DownloadStatus.Downloading) return
            pollingJob?.cancel()
            activeDownloadId = null
            mutableStatus.value = DownloadStatus.Downloading(0f, 0L, 0L)
        }

        scope.launch {
            try {
                val downloadManager = appContext.getSystemService(DownloadManager::class.java)
                val storageManager = StorageManager(appContext)
                storageManager.getString(DOWNLOAD_ID_KEY)
                    ?.toLongOrNull()
                    ?.let { previousId -> downloadManager.remove(previousId) }
                clearStoredDownload(storageManager)

                val request = DownloadManager.Request(dropInfo.downloadUrl.toUri())
                    .setTitle("PhestyDrop — Baroness v${dropInfo.versionName}")
                    .setDescription("Downloading update")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setMimeType("application/vnd.android.package-archive")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(
                        appContext,
                        null,
                        "phestydrop/phesty_drop_v${dropInfo.versionName}.apk"
                    )

                val downloadId = downloadManager.enqueue(request)
                storageManager.saveString(DOWNLOAD_ID_KEY, downloadId.toString())
                storageManager.saveString(VERSION_NAME_KEY, dropInfo.versionName)
                activeDownloadId = downloadId
                startPolling(appContext, downloadId, dropInfo.versionName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue PhestyDrop download", e)
                activeDownloadId = null
                mutableStatus.value = DownloadStatus.Error("Download failed")
            }
        }
    }

    fun cancelDownload(context: Context) {
        val appContext = context.applicationContext
        pollingJob?.cancel()
        activeDownloadId = null
        mutableStatus.value = DownloadStatus.Idle
        scope.launch {
            try {
                val storageManager = StorageManager(appContext)
                storageManager.getString(DOWNLOAD_ID_KEY)
                    ?.toLongOrNull()
                    ?.let { appContext.getSystemService(DownloadManager::class.java).remove(it) }
                clearStoredDownload(storageManager)
                activeDownloadId = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel PhestyDrop download", e)
                mutableStatus.value = DownloadStatus.Error("Unable to cancel download")
            }
        }
    }

    fun consumeReady(context: Context) {
        pollingJob?.cancel()
        activeDownloadId = null
        mutableStatus.value = DownloadStatus.Idle
        scope.launch {
            try {
                clearStoredDownload(StorageManager(context.applicationContext))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear consumed PhestyDrop state", e)
                mutableStatus.value = DownloadStatus.Error("Unable to clear download state")
            }
        }
    }

    fun getDownloadUriForCurrent(context: Context): Uri? {
        (mutableStatus.value as? DownloadStatus.Ready)?.let { return it.uri }
        val downloadId = activeDownloadId ?: return null
        return context.applicationContext
            .getSystemService(DownloadManager::class.java)
            .getUriForDownloadedFile(downloadId)
    }

    /**
     * Scans getExternalFilesDir(null)/phestydrop/ for APK files.
     * - Deletes corrupt files (getPackageArchiveInfo returns null or 0 length).
     * - Deletes files whose versionCode <= BuildConfig.VERSION_CODE.
     * - Keeps files whose versionCode > BuildConfig.VERSION_CODE.
     * Returns the highest-versioned remaining file, or null.
     */
    private fun scanAndPruneCachedApks(context: Context): File? {
        val dir = File(context.getExternalFilesDir(null), "phestydrop")
        val files = dir.listFiles { _, name -> name.endsWith(".apk", ignoreCase = true) }
        if (files.isNullOrEmpty()) return null

        var highestVersionCode = -1L
        var bestFile: File? = null

        for (file in files) {
            if (file.length() == 0L) {
                Log.i(TAG, "Deleting 0-byte cached APK: ${file.name}")
                file.delete()
                continue
            }
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (info == null) {
                Log.i(TAG, "Deleting corrupt cached APK (archive info null): ${file.name}")
                file.delete()
                continue
            }
            val versionCode = extractVersionCode(info)
            if (versionCode <= BuildConfig.VERSION_CODE) {
                Log.i(TAG, "Deleting outdated cached APK (${file.name}, vCode=$versionCode <= installed ${BuildConfig.VERSION_CODE})")
                file.delete()
                continue
            }
            Log.i(TAG, "Found valid cached APK: ${file.name} (vCode=$versionCode > installed ${BuildConfig.VERSION_CODE})")
            if (versionCode > highestVersionCode) {
                highestVersionCode = versionCode
                bestFile = file
            }
        }
        return bestFile
    }

    private fun extractVersionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private suspend fun restoreState(context: Context) {
        try {
            val storageManager = StorageManager(context)
            val storedId = storageManager.getString(DOWNLOAD_ID_KEY)
            if (storedId.isNullOrBlank()) {
                activeDownloadId = null
                mutableStatus.value = DownloadStatus.Idle
                return
            }
            val downloadId = storedId.toLongOrNull()
            if (downloadId == null) {
                clearStoredDownload(storageManager)
                activeDownloadId = null
                mutableStatus.value = DownloadStatus.Idle
                return
            }

            val versionName = storageManager.getString(VERSION_NAME_KEY) ?: "unknown"
            activeDownloadId = downloadId
            val record = queryDownload(context, downloadId)
            if (record == null) {
                clearStoredDownload(storageManager)
                activeDownloadId = null
                mutableStatus.value = DownloadStatus.Idle
                return
            }
            when (record.status) {
                DownloadManager.STATUS_SUCCESSFUL -> setReady(context, downloadId, versionName)
                DownloadManager.STATUS_FAILED -> mutableStatus.value = DownloadStatus.Error("Download failed")
                else -> {
                    mutableStatus.value = record.toDownloadingStatus()
                    startPolling(context, downloadId, versionName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore PhestyDrop download state", e)
            mutableStatus.value = DownloadStatus.Error("Unable to restore download state")
        }
    }

    private fun startPolling(context: Context, downloadId: Long, versionName: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            try {
                while (isActive) {
                    if (activeDownloadId != downloadId) return@launch
                    val record = queryDownload(context, downloadId)
                    if (record == null) {
                        clearStoredDownload(StorageManager(context))
                        activeDownloadId = null
                        mutableStatus.value = DownloadStatus.Idle
                        return@launch
                    }
                    when (record.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            setReady(context, downloadId, versionName)
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            mutableStatus.value = DownloadStatus.Error("Download failed")
                            return@launch
                        }
                        else -> {
                            mutableStatus.value = record.toDownloadingStatus()
                            val isPaused = record.status == DownloadManager.STATUS_PAUSED
                            delay(if (isPaused) 2000.milliseconds else 500.milliseconds)
                            continue
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to poll PhestyDrop download status", e)
                mutableStatus.value = DownloadStatus.Error("Unable to check download status")
            }
        }
    }

    private suspend fun handleCompletion(context: Context, downloadId: Long) {
        try {
            val storageManager = StorageManager(context)
            val storedId = storageManager.getString(DOWNLOAD_ID_KEY)?.toLongOrNull()
            if (storedId != downloadId || activeDownloadId != downloadId) return
            val versionName = storageManager.getString(VERSION_NAME_KEY) ?: "unknown"
            when (queryDownload(context, downloadId)?.status) {
                DownloadManager.STATUS_SUCCESSFUL -> setReady(context, downloadId, versionName)
                DownloadManager.STATUS_FAILED -> mutableStatus.value = DownloadStatus.Error("Download failed")
                else -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process PhestyDrop completion", e)
            mutableStatus.value = DownloadStatus.Error("Unable to complete download")
        }
    }

    private fun setReady(context: Context, downloadId: Long, versionName: String) {
        val uri = context.getSystemService(DownloadManager::class.java)
            .getUriForDownloadedFile(downloadId)
        if (uri == null || !canOpenUri(context, uri)) {
            Log.i(TAG, "Downloaded file URI is unavailable; resetting to Idle")
            activeDownloadId = null
            mutableStatus.value = DownloadStatus.Idle
            scope.launch {
                try {
                    clearStoredDownload(StorageManager(context))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear stale download state", e)
                }
            }
            return
        }
        mutableStatus.value = DownloadStatus.Ready(uri, versionName)
    }

    private fun canOpenUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read()
                true
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open downloaded file URI: ${e.message}")
            false
        }
    }

    private fun queryDownload(context: Context, downloadId: Long): DownloadRecord? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return context.getSystemService(DownloadManager::class.java)
            .query(query)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                DownloadRecord(
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    bytesRead = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    ),
                    totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                )
            }
    }

    private suspend fun clearStoredDownload(storageManager: StorageManager) {
        storageManager.remove(DOWNLOAD_ID_KEY)
        storageManager.remove(VERSION_NAME_KEY)
    }

    private data class DownloadRecord(
        val status: Int,
        val bytesRead: Long,
        val totalBytes: Long
    ) {
        fun toDownloadingStatus(): DownloadStatus.Downloading {
            val progress = if (totalBytes > 0L) {
                (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                -1f
            }
            return DownloadStatus.Downloading(progress, bytesRead, totalBytes)
        }
    }
}
