# PhestyDrop Download Persistence — Implementation

## Files created

- `app/src/main/java/com/baroness/app/phestydrop/download/DownloadStatus.kt` — shared UI state for an idle, active, ready, or failed system download.
- `app/src/main/java/com/baroness/app/phestydrop/download/PhestyDropDownloader.kt` — singleton owner of Android `DownloadManager`, persisted download metadata, receiver, and progress polling.
- `app/src/main/java/com/baroness/app/phestydrop/download/PhestyDropNotificationReceiver.kt` — manifest receiver routing system-notification taps to About, including when the process was stopped.
- `reports/phestydrop/01-download-persistence-implementation.md` — this implementation record.

## Files modified

- `app/src/main/java/com/baroness/app/phestydrop/manager/PhestyDropManager.kt` — removed the old OkHttp stream API and changed `installDrop` from `File` to `Uri`.
- `app/src/main/java/com/baroness/app/phestydrop/ui/PhestyDropSheet.kt` — observes singleton status, starts downloads independently of the sheet, and supports resume, cancel, retry, and install states.
- `app/src/main/java/com/baroness/app/screens/settings/AboutBaronessPage.kt` — observes the same status and offers download progress and installation from the page button.
- `app/src/main/java/com/baroness/app/MainActivity.kt` — initializes the downloader and handles the notification's `settings/about` route, including `onNewIntent`.
- `app/src/main/AndroidManifest.xml` — registers the notification-click receiver as exported so the system can start it after process death.
- `.gitignore` — anchored the existing `PhestyDrop/` output rule to the repository root (`/PhestyDrop/`). On Windows the unanchored rule also matched the lower-case `phestydrop` source and report folders, hiding this implementation from Git status.
- `app/src/main/java/com/baroness/app/phestydrop/data/DownloadState.kt` — removed after confirming its only references were the old download flow and sheet, both migrated.

The source package and the earlier audit report in `reports/phestydrop/` were previously present but ignored by the unanchored ignore rule. Anchoring that rule makes those existing files visible to Git as well; this implementation did not change `PhestyDropInfo.kt` or the audit report.

## Download status

`DownloadStatus.kt`:

```kotlin
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
```

## PhestyDropDownloader source

```kotlin
package com.baroness.app.phestydrop.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import kotlin.time.Duration.Companion.milliseconds

object PhestyDropDownloader {
    private const val TAG = "PhestyDropDownloader"
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
        scope.launch { restoreState(appContext) }
    }

    fun startDownload(context: Context, dropInfo: PhestyDropInfo) {
        synchronized(lock) {
            if (mutableStatus.value is DownloadStatus.Downloading) return
            pollingJob?.cancel()
            activeDownloadId = null
            mutableStatus.value = DownloadStatus.Downloading(0f, 0L, 0L)
        }

        val appContext = context.applicationContext
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
                            if (record.status == DownloadManager.STATUS_PAUSED) return@launch
                        }
                    }
                    delay(500.milliseconds)
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
        if (uri == null) {
            mutableStatus.value = DownloadStatus.Error("Downloaded file is unavailable")
        } else {
            mutableStatus.value = DownloadStatus.Ready(uri, versionName)
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
```

The downloader stores the system download ID as a DataStore string at `phestydrop_download_id`; the companion `phestydrop_download_version_name` string restores the required `Ready` state after process death. Polling runs on a singleton `SupervisorJob() + Dispatchers.IO` scope every 500 ms. Its dynamic receiver handles download completion.

`PhestyDropNotificationReceiver.kt` handles notification taps even when the app process is dead:

```kotlin
package com.baroness.app.phestydrop.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.baroness.app.MainActivity
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhestyDropNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_NOTIFICATION_CLICKED) return
        val clickedIds =
            intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS) ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val activeId = StorageManager(appContext)
                    .getString("phestydrop_download_id")
                    ?.toLongOrNull()
                if (activeId != null && clickedIds.contains(activeId)) {
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java).apply {
                            putExtra("route", "settings/about")
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("PhestyDropNotificationReceiver", "Failed to open About page from download notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

## Installer signature and call sites

Before:

```kotlin
fun installDrop(context: Context, apkFile: File): Boolean
```

After:

```kotlin
fun installDrop(context: Context, uri: Uri): Boolean {
    // Existing unknown-app-sources permission gate remains.
    return try {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No package installer is available", e)
        false
    } catch (e: Exception) {
        Log.e(TAG, "Error launching package installer: ${e.message}", e)
        false
    }
}
```

The sole old `File` caller was the `Ready` branch in `PhestyDropSheet.kt`; it now passes `DownloadStatus.Ready.uri`. The sheet consumes the ready state and dismisses after `installDrop` returns true. `AboutBaronessPage.kt` adds the second call site, also passing `state.uri` and consuming the ready state when installation launches. No File-based PhestyDrop installer caller remains.

The manifest FileProvider remains declared with authority `${applicationId}.fileprovider` (equivalent to `${packageName}.fileprovider` for this app); it continues to serve unrelated `CaptureHelper` file sharing and was not removed.

## Sheet migration

Before, `PhestyDropSheet` owned `var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }`. “Accept Drop” collected `PhestyDropManager.downloadDrop(context, dropInfo)` in the sheet's coroutine scope, and Ready installed `state.apkFile`.

After, the sheet observes `PhestyDropDownloader.status.collectAsStateWithLifecycle()`:

```kotlin
val downloadState by PhestyDropDownloader.status.collectAsStateWithLifecycle()
```

The Idle action now starts an OS-owned download and dismisses the sheet immediately:

```kotlin
onClick = {
    PhestyDropDownloader.startDownload(context, dropInfo)
    onDismiss()
}
```

When reopened during a download, the sheet displays current progress (or an indeterminate bar when the size is unknown) and a Cancel button. Ready installs from the content URI. Error displays a retry action, which starts a new system download. “Later” and dismissing the sheet do not cancel an active download. The scope remains only for persisting the auto-download switch.

## About page button

Before, button state reflected only `Idle`, `Checking`, `UpToDate`, and `Error`.

After, the page also collects `PhestyDropDownloader.status` and maps the combined state as follows:

| Download state | Button icon/content | Enabled/action |
|---|---|---|
| Idle | Refresh — “Check for PhestyDrop” | Enabled; performs the existing update check |
| Checking | Progress spinner — “Checking…” | Disabled |
| UpToDate | Check — “Up to date” | Disabled during the one-second feedback |
| Check Error | Error outline — “Check failed” | Disabled during the 1.5-second feedback |
| Downloading | Determinate 120.dp × 4.dp bar and “Downloading N%” (or “Downloading…” if size unknown) | Disabled |
| Ready | Check — “Install PhestyDrop Now” | Enabled; calls URI installer and consumes Ready on success |
| Download Error | Error outline — “Download failed” | Enabled; performs the update check so the sheet can be reopened for retry |

The existing button container/content colors and shape are unchanged. The status is observed with `collectAsStateWithLifecycle()`.

## Legacy API cleanup

Before removal, `rg` found `PhestyDropManager.downloadDrop` only in `PhestyDropSheet.kt`; that caller has been migrated. `DownloadState` had no other callers beyond that flow/sheet. Both the old function and sealed class were removed. No `downloadDrop` or `DownloadState` references remain under `app/src/main`.

## Build

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`.
- No device or adb commands were run.

## Manual verification checklist

- [ ] Tap “Check for PhestyDrop” on About; when an update is available, verify the sheet opens.
- [ ] Tap “Accept Drop”; verify the sheet dismisses and the system DownloadManager notification appears.
- [ ] Expand the notification and verify native progress updates.
- [ ] Reopen About and verify the button shows current download progress.
- [ ] Kill and reopen the app; verify the download state is recovered from DownloadManager.
- [ ] Complete the download; verify About shows “Install PhestyDrop Now”.
- [ ] Tap the system notification; verify the app opens to Settings → About Baroness.
- [ ] Install from About and verify the Android package installer opens.
- [ ] Reopen the PhestyDrop sheet through an update check while downloading; verify progress and Cancel are shown, and Accept is absent.
- [ ] Cancel and verify the download is removed and About returns to Idle.
- [ ] In airplane mode, verify DownloadManager pauses/retries or fails; verify the resulting status and that Retry works when connectivity returns.

## B.1 — Bug Fix & Sheet Polish

### 1. Change to `startPolling`

**Before:**
```kotlin
else -> {
    mutableStatus.value = record.toDownloadingStatus()
    if (record.status == DownloadManager.STATUS_PAUSED) return@launch
}
```

**After:**
```kotlin
else -> {
    mutableStatus.value = record.toDownloadingStatus()
    val isPaused = record.status == DownloadManager.STATUS_PAUSED
    delay(if (isPaused) 2000.milliseconds else 500.milliseconds)
    continue
}
```

When network interrupts occur and DownloadManager sets `STATUS_PAUSED`, the polling loop now continues running with a slowed polling rate (2000ms delay) rather than exiting (`return@launch`). When the network recovers and DownloadManager resumes, polling immediately reports advancing progress.

### 2. Change to Accept Drop Button

**Before:**
```kotlin
Button(
    onClick = {
        PhestyDropDownloader.startDownload(context, dropInfo)
        onDismiss()
    },
    modifier = Modifier.weight(1f)
) {
    Text("Accept Drop")
}
```

**After:**
```kotlin
Button(
    onClick = {
        PhestyDropDownloader.startDownload(context, dropInfo)
    },
    modifier = Modifier.weight(1f)
) {
    Text("Accept Drop")
}
```

Tapping "Accept Drop" starts the download without calling `onDismiss()`, allowing the operator to observe progress directly within the sheet.

### 3. New Progress Bar Composable

```kotlin
@Composable
private fun PhestyDropProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val targetProgress = if (progress >= 0f) progress.coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 200),
        label = "PhestyDropProgressAnimation"
    )

    val textColor = if (animatedProgress > 0.6f) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val text = if (progress >= 0f) {
        "Downloading ${(progress * 100).toInt()}%"
    } else {
        "Downloading…"
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val maxWidth = maxWidth
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth * animatedProgress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )
    }
}
```

### 4. Modified Files Confirmation

Only the following two files were modified in Phase B.1:
- `app/src/main/java/com/baroness/app/phestydrop/download/PhestyDropDownloader.kt`
- `app/src/main/java/com/baroness/app/phestydrop/ui/PhestyDropSheet.kt`

No other files were modified.

### 5. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

### 6. Manual Verification Checklist

- [ ] Start a download. Interrupt the network mid-download (airplane mode). Verify the percentage freezes at the last value.
- [ ] Re-enable network. Verify the percentage resumes advancing within a few seconds without needing to reopen the app or About page.
- [ ] Tap Accept Drop. Verify the sheet stays open and shows live progress in the new unified bar.
- [ ] Swipe the sheet down manually. Verify it dismisses.
- [ ] Reopen the sheet mid-download. Verify the same unified bar shows the current progress.
- [ ] Let the download complete. Verify the bar is replaced by the "Install PhestyDrop Now" state.
- [ ] Verify the new bar fills entirely with the theme's primary color left-to-right, and "Downloading N%" sits right-aligned inside the bar. No thin strip on a track.

## B.1.1 — About Page Progress Unification

### 1. New `DownloadProgressButton` Composable

```kotlin
@Composable
private fun DownloadProgressButton(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val targetProgress = if (progress >= 0f) progress.coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 200),
        label = "AboutDownloadProgress"
    )

    val text = if (progress >= 0f) {
        "Downloading ${(progress * 100).toInt()}%"
    } else {
        "Downloading…"
    }

    val textColor = if (animatedProgress > 0.6f) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxW * animatedProgress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
```

### 2. Change to Button Render Logic

**Before:**
```kotlin
Button(
    onClick = { ... },
    enabled = when (downloadStatus) {
        is DownloadStatus.Downloading -> false
        is DownloadStatus.Ready -> true
        else -> checkState == CheckButtonState.Idle
    },
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(
        containerColor = accentColor.copy(alpha = 0.8f)
    ),
    shape = RoundedCornerShape(10.dp)
) {
    val downloading = downloadStatus as? DownloadStatus.Downloading
    val ready = downloadStatus is DownloadStatus.Ready
    if (downloading != null) {
        LinearProgressIndicator(
            progress = {
                if (downloading.progress < 0f) 0f else downloading.progress
            },
            modifier = Modifier
                .width(120.dp)
                .height(4.dp),
            color = LocalContentColor.current,
            trackColor = LocalContentColor.current.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.size(8.dp))
    } else ...
```

**After:**
```kotlin
val downloading = downloadStatus as? DownloadStatus.Downloading
if (downloading != null) {
    DownloadProgressButton(
        progress = downloading.progress,
        modifier = Modifier.fillMaxWidth()
    )
} else {
    Button(
        onClick = { ... },
        enabled = when (downloadStatus) {
            is DownloadStatus.Ready -> true
            else -> checkState == CheckButtonState.Idle
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        val ready = downloadStatus is DownloadStatus.Ready
        ...
```

### 3. Modified Files Confirmation

Only the following single file was modified in Phase B.1.1:
- `app/src/main/java/com/baroness/app/screens/settings/AboutBaronessPage.kt`

No other files were modified.

### 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

### 5. Manual Verification Checklist

- [ ] On About Baroness, start a PhestyDrop download.
- [ ] While downloading, the About button is replaced by a full-width bar that fills with the theme's primary color from left to right.
- [ ] The bar shows "Downloading N%" text on top, centered inside the bar.
- [ ] The bar is not tappable (no click action, no ripple).
- [ ] When the download completes, the surface reverts to the "Install PhestyDrop Now" button.
- [ ] Compare side-by-side with the sheet's in-progress bar during a download: same visual language, same fill direction, same text style.

## B.1.2 — About Bar Direction Fix

### 1. Change to `DownloadProgressButton`

**Before:**
```kotlin
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxW * animatedProgress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.align(Alignment.Center)
        )
    }
```

**After:**
```kotlin
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val maxW = maxWidth
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxW * animatedProgress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )
    }
```

### 2. Direction & Text Alignment Confirmation

- Removed `contentAlignment = Alignment.Center` from `BoxWithConstraints`. The progress fill `Box` now anchors to top-left / `CenterStart` and expands left-to-right from 0 width to max width instead of expanding from the center.
- Changed `Text` alignment to `Alignment.CenterEnd` with `.padding(end = 12.dp)` and `12.sp` font size to match the sheet's `PhestyDropProgressBar` layout exactly.

### 3. Modified Files Confirmation

Only the following single file was modified in Phase B.1.2:
- `app/src/main/java/com/baroness/app/screens/settings/AboutBaronessPage.kt`

No other files were modified.

### 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

### 5. Manual Verification Checklist

- [ ] Start a PhestyDrop download.
- [ ] On About, the bar fills left-to-right with the theme's primary color. It does not grow from the center.
- [ ] The "Downloading N%" text is right-aligned inside the bar with a 12.dp right padding.
- [ ] Compare side-by-side with the sheet's progress bar — same direction, same text position, same visual language.



