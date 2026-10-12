# PhestyDrop Cached APK Detection — Architecture Audit Report

## 1. Executive Summary

An architectural audit of PhestyDrop’s download storage and APK detection capabilities was conducted to evaluate how the app can detect and reuse already-downloaded update APKs without re-downloading from server storage. Currently, `PhestyDropDownloader` always enqueues a fresh DownloadManager request and writes to `getExternalFilesDir(null)/phestydrop/phesty_drop_v<versionName>.apk`. Two critical gaps exist: (1) `file_paths.xml` lacks an `<external-files-path>` declaration, meaning custom FileProvider URIs cannot be generated for files in external files storage unless added or storage is re-rooted to internal cache/files; and (2) state restoration relies entirely on DownloadManager's internal database rather than scanning disk storage. Implementing cached APK detection requires adding `<external-files-path>` to `file_paths.xml` (or relocating APKs to `cacheDir`), adding a helper to parse APK version codes via `PackageManager.getPackageArchiveInfo`, and checking disk storage during `initialize` or download requests.

---

## 2. Current PhestyDropDownloader State

Defined in `app/src/main/java/com/baroness/app/phestydrop/download/PhestyDropDownloader.kt`:

- **Destination Path Expression**:
  ```kotlin
  .setDestinationInExternalFilesDir(
      appContext,
      null,
      "phestydrop/phesty_drop_v${dropInfo.versionName}.apk"
  )
  ```
- **`restoreState` Logic**:
  - Reads `DOWNLOAD_ID_KEY` and `VERSION_NAME_KEY` from `StorageManager`.
  - Queries `DownloadManager` for the active download ID.
  - Never inspects the raw file system (`File(...)`), relying strictly on DownloadManager's database and shared preferences keys.
- **Stability Across Restarts**: The destination path is deterministic and stable for a given `versionName`.
- **`consumeReady` Behavior**: Clears stored preferences (`DOWNLOAD_ID_KEY`, `VERSION_NAME_KEY`) and resets status to `Idle`, but **does NOT delete** the downloaded APK file from disk.

---

## 3. DownloadManager Destination Path Behavior

- **Absolute Path on Device**:
  `<external-files-dir>/phestydrop/phesty_drop_v<versionName>.apk`
  (Typically: `/storage/emulated/0/Android/data/com.baroness.app/files/phestydrop/phesty_drop_v<versionName>.apk`).
- **App-Private vs Shared**: App-private external storage (`getExternalFilesDir()`). Files reside in the app's dedicated external sandbox folder.
- **Persistence Lifecycle**:
  - Survives app kill: **Yes**.
  - Survives app uninstall: **No** (Android wipes `Android/data/<package>` on uninstall).
  - Survives device reboot: **Yes**.
  - Survives OS "Clear cache": **No** (depending on OEM, external files may or may not be cleared by cache cleaners).
  - Survives OS "Clear data": Can be preserved or wiped depending on Android version/OEM.
- **FileProvider Configuration Gap**:
  - `file_paths.xml` (`res/xml/file_paths.xml`) currently declares `<cache-path>`, `<external-cache-path>`, and `<files-path>`, but **does NOT declare `<external-files-path>`**.
  - Consequently, generating a FileProvider URI for `getExternalFilesDir()` throws `IllegalArgumentException` unless `<external-files-path name="external_files" path="." />` is added to `file_paths.xml` (or downloads are saved to `cacheDir` / `filesDir`).

---

## 4. PackageManager — Reading APK Version

- **API**: `context.packageManager.getPackageArchiveInfo(filePath, flags)`
  - Fully available on `minSdk = 24`.
- **Flags**: Passing `0` returns a `PackageInfo` containing `versionCode` (or `longVersionCode` on API 28+) and `versionName`.
- **Permissions**: **None required**.
- **Silent Failure**: Returns `null` if the file does not exist, is corrupted, or is not a valid APK.
- **Exceptions**: Can throw runtime exceptions if path parameters are malformed, though `null` is standard on read failures.

---

## 5. Version Comparison Sources

- **Installed App Version**: `BuildConfig.VERSION_CODE` (Integer) and `BuildConfig.VERSION_NAME` (String).
- **Server Drop Version**: `PhestyDropInfo.versionCode` and `PhestyDropInfo.versionName`.
- **Cached APK Validity Logic**:
  1. File exists on disk (`file.exists() && file.length() > 0`).
  2. `packageManager.getPackageArchiveInfo(file.absolutePath, 0)` is non-null.
  3. Cached APK `versionCode >= targetVersionCode` (or `versionCode > BuildConfig.VERSION_CODE`).

---

## 6. Detection Points Inventory

1. **On App Start (`PhestyDropDownloader.initialize`)**: Check `phestydrop/` directory for any APK whose version code > `BuildConfig.VERSION_CODE`. If found, transition directly to `DownloadStatus.Ready`.
2. **On About Page Open (`AboutBaronessPage`)**: Observe `PhestyDropDownloader.status` to reflect cached ready state.
3. **On `PhestyDropSheet` Open**: Check if valid cached APK already exists for `dropInfo.versionName`; if so, skip download and show install action immediately.
4. **On `startDownload`**: Check if valid APK already exists before enqueuing a fresh DownloadManager request.

---

## 7. DownloadManager State Interaction

- If a user manually deletes the downloaded APK file while DownloadManager's DB still holds `STATUS_SUCCESSFUL`, `DownloadManager.Query` returns successful, but `getUriForDownloadedFile()` returns a content URI whose underlying file is missing.
- Handled by validating `File(uri.path!!).exists()` when checking cached or downloaded artifacts.

---

## 8. FileProvider URI Construction

- **Signature**: `FileProvider.getUriForFile(context, "com.baroness.app.fileprovider", file)`
- **Manifest Provider**: Configured with `@xml/file_paths`.
- **Required Fix**: Add `<external-files-path name="external_files" path="." />` to `res/xml/file_paths.xml` to support `getExternalFilesDir()`, OR store downloaded APKs in `context.cacheDir` which is already covered by `<cache-path name="cache" path="/" />`.

---

## 9. Installer Compatibility

- `PhestyDropManager.installDrop(context, uri)` accepts any valid content `Uri` with `FLAG_GRANT_READ_URI_PERMISSION`, making it fully compatible with FileProvider URIs or DownloadManager content URIs.

---

## 10. Stale File Cleanup Scenarios

- **Older than installed app (`cachedVersionCode <= BuildConfig.VERSION_CODE`)**: Prune/delete file immediately upon scan.
- **Corrupt APK (`getPackageArchiveInfo == null`)**: Delete file immediately.
- **Multiple cached APKs**: Prune all files where version code <= current installed version code.

---

## 11. Decision Surface (Choices for Next Phase)

1. **Storage Location**:
   - *Choice A*: Keep `getExternalFilesDir()` and add `<external-files-path>` to `file_paths.xml`.
   - *Choice B*: Relocate APK downloads to `context.cacheDir` (covered by existing `<cache-path>`).
2. **Detection Timing**:
   - Scan disk storage on `PhestyDropDownloader.initialize()`, `startDownload()`, and sheet open.
3. **Stale Cleanup Execution**:
   - Prune obsolete APK files automatically during `initialize()` and download requests.
4. **UI State**:
   - Surface cached APK readiness immediately so the user can install without re-downloading.
