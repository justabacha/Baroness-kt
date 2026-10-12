# PhestyDrop Cached APK Detection — Implementation

## Context

The audit (`reports/phestydrop/02-cached-apk-detection-audit.md`) confirmed the download path writes to `externalFilesDir/phestydrop/phesty_drop_v<versionName>.apk`. Without cached APK detection, re-checking for updates after downloading (or restarting the app before installing) would re-trigger a full network download. Furthermore, `file_paths.xml` previously lacked `<external-files-path>`, so creating FileProvider URIs for cached files in external files directory would throw an `IllegalArgumentException`.

This implementation adds FileProvider support for external files, cached APK scanning/pruning, early restoration on `initialize()`, and immediate reuse on `startDownload()`.

## 1. FileProvider Manifest Authority & XML Changes

### Authority String
- Declared in `AndroidManifest.xml`: `${applicationId}.fileprovider`
- Evaluated literal authority string: `com.baroness.app.fileprovider`

### `file_paths.xml`

**Before:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="/" />
    <external-cache-path name="external_cache" path="/" />
    <files-path name="files" path="/" />
</paths>
```

**After:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="/" />
    <external-cache-path name="external_cache" path="/" />
    <files-path name="files" path="/" />
    <external-files-path name="external_files" path="." />
</paths>
```

## 2. `scanAndPruneCachedApks` Function

```kotlin
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
```

Version code accessor note: API 28+ uses `PackageInfo.longVersionCode`; API 24-27 falls back to `@Suppress("DEPRECATION") PackageInfo.versionCode.toLong()`.

## 3. `initialize` Change (Scan vs Restore Ordering)

**Before:**
```kotlin
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
```

**After:**
```kotlin
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
```

## 4. `startDownload` Cached-File Pre-Check

```kotlin
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
        ...
```

## 5. `consumeReady` Confirmation

`consumeReady` was **not modified**. It clears `DOWNLOAD_ID_KEY` and `VERSION_NAME_KEY` from `StorageManager` and resets `mutableStatus` to `DownloadStatus.Idle`. The cached APK file on disk remains intact so that if installation is cancelled or deferred, the file is re-detected on next launch. Upon successful installation, the new app's `BuildConfig.VERSION_CODE` exceeds the cached file's `versionCode`, and `scanAndPruneCachedApks` automatically prunes the file on the next initialization.

## 6. Modified Files Confirmation

Only the following two files were modified in this phase:
- `app/src/main/res/xml/file_paths.xml`
- `app/src/main/java/com/baroness/app/phestydrop/download/PhestyDropDownloader.kt`

No other code files or screens were modified.

## 7. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

## 8. Manual Verification Checklist

- [ ] Start a PhestyDrop download. Let it complete. Do NOT install.
- [ ] Kill the app. Reopen. Navigate to About Baroness. The button should show "Install PhestyDrop Now" without any network activity.
- [ ] Tap "Install PhestyDrop Now" → the Android package installer opens with the cached APK.
- [ ] Cancel the installer. Kill the app. Reopen. About should still show "Install PhestyDrop Now" (cached file still detected).
- [ ] Tap "Check for PhestyDrop" while a cached APK exists for a version newer than installed. If the server returns that same version, the sheet opens; tapping Accept should transition directly to Ready without re-downloading.
- [ ] Install the APK. After install completes and the new app version is running, open About. The button should return to "Check for PhestyDrop" (cached file pruned on initialize because its version code is now <= installed).
- [ ] Corrupt a cached file manually (truncate it to 0 bytes) if testable. On next initialize, the file should be pruned silently.

## Install Cancellation Persistence Fix

### 1. Sheet Install Button Change

**Before:**
```kotlin
is DownloadStatus.Ready -> {
    Button(
        onClick = {
            val launched = PhestyDropManager.installDrop(context, state.uri)
            if (launched) {
                PhestyDropDownloader.consumeReady(context)
                onDismiss()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
    ) {
        ...
```

**After:**
```kotlin
is DownloadStatus.Ready -> {
    Button(
        onClick = {
            val ready = downloadState as? DownloadStatus.Ready
            if (ready != null) {
                PhestyDropManager.installDrop(context, ready.uri)
                onDismiss()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
    ) {
        ...
```

### 2. `consumeReady` Removal in About Baroness Page

Removed premature `consumeReady` call in `AboutBaronessPage.kt`:

**Before:**
```kotlin
is DownloadStatus.Ready -> {
    val uri = PhestyDropDownloader.getDownloadUriForCurrent(context)
        ?: state.uri
    if (PhestyDropManager.installDrop(context, uri)) {
        PhestyDropDownloader.consumeReady(context)
    }
}
```

**After:**
```kotlin
is DownloadStatus.Ready -> {
    val uri = PhestyDropDownloader.getDownloadUriForCurrent(context)
        ?: state.uri
    PhestyDropManager.installDrop(context, uri)
}
```

`grep` confirmed no other callers of `consumeReady` remain in the codebase outside `PhestyDropDownloader.kt`.

### 3. `setReady` and `canOpenUri` Changes in `PhestyDropDownloader.kt`

**Before:**
```kotlin
private fun setReady(context: Context, downloadId: Long, versionName: String) {
    val uri = context.getSystemService(DownloadManager::class.java)
        .getUriForDownloadedFile(downloadId)
    if (uri == null) {
        mutableStatus.value = DownloadStatus.Error("Downloaded file is unavailable")
    } else {
        mutableStatus.value = DownloadStatus.Ready(uri, versionName)
    }
}
```

**After:**
```kotlin
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
```

### 4. Modified Files Confirmation

The following files were modified in this fix:
- `app/src/main/java/com/baroness/app/phestydrop/ui/PhestyDropSheet.kt`
- `app/src/main/java/com/baroness/app/screens/settings/AboutBaronessPage.kt`
- `app/src/main/java/com/baroness/app/phestydrop/download/PhestyDropDownloader.kt`

No other files were modified.

### 5. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

### 6. Manual Verification Checklist

- [ ] Download an update. Complete. Tap "Install PhestyDrop Now". The Android installer opens.
- [ ] Cancel the installer (back out). Return to the app (or reopen the sheet mid-download — the state should still be Ready).
- [ ] Reopen the sheet or the About page. Verify the state still shows "Install PhestyDrop Now" rather than a fresh "Accept Drop".
- [ ] Tap "Install PhestyDrop Now" again. Verify the installer opens with the same cached APK.
- [ ] Let the install succeed. After the app restarts with the new version, open About. Verify it now shows "Check for PhestyDrop" (not a stale Install prompt).

