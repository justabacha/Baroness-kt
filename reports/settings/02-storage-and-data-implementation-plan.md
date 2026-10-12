# Storage & Data Page — Implementation Plan

## 1. Executive Summary

This document provides a complete, zero-scope-creep implementation plan for the **Storage & Data** sub-screen in the Settings Center (`"settings/storage"`). The implementation connects existing, verified backend engines (`BackupManager`, `AvatarRepository`, `VoiceCacheManager`, Coil `DiskCache`) to a new Material 3 settings page without modifying core sync or lifecycle architecture. The feature set includes a read-only storage overview, non-destructive cache clearing, and Cloud Chat Backup/Restore with a non-blocking timestamp readout.

---

## 2. Existing Engine Signatures

| Engine / Class | Exact Method Signature | `suspend` / Threading | Parameters | Return Type | Existing Logcat Tags / Output |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `BackupManager` | `suspend fun createBackup(personaId: String): Boolean` | `suspend` (`Dispatchers.IO`) | `personaId: String` | `Boolean` | `TAG = "BackupManager"`<br>`"Starting backup for $personaId"`<br>`"Backup successful for $personaId"`<br>`"Backup failed: ..."` |
| `BackupManager` | `suspend fun restoreBackup(personaId: String): Boolean` | `suspend` (`Dispatchers.IO`) | `personaId: String` | `Boolean` | `TAG = "BackupManager"`<br>`"Starting restore for $personaId"`<br>`"Restore successful for $personaId..."`<br>`"Restore failed: ..."` |
| `AvatarRepository` | `suspend fun clearCache()` | `suspend` (`Dispatchers.IO`) | *None* | `Unit` | Deletes `user_avatars/avatar_*` files and clears `StorageManager` keys. |
| `VoiceCacheManager` | `fun clearTempCache()` | Synchronous (blocking) | *None* | `Unit` | Deletes `cacheDir/voice_cache/temp_dynamic/*` files. |
| `Coil ImageLoader` | `imageLoader.diskCache?.clear()` | Synchronous (blocking) | *None* | `Unit` | Clears Coil `DiskCache` (`filesDir/image_cache`). Wrap in `Dispatchers.IO`. |

---

## 3. Small Additions Required

### 3.1 Directory Size Helper (`StorageUtils.kt`)
- **Package**: `com.baroness.app.utils`
- **Confirmation**: No general directory file-walking utility currently exists in `com.baroness.app.utils`.
- **Proposed Shape**:
  ```kotlin
  package com.baroness.app.utils

  import java.io.File

  fun File.totalSizeBytes(): Long {
      if (!exists()) return 0L
      if (isFile) return length()
      return listFiles()?.sumOf { it.totalSizeBytes() } ?: 0L
  }

  fun formatBytes(bytes: Long): String {
      if (bytes <= 0) return "0 B"
      val units = arrayOf("B", "KB", "MB", "GB")
      val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
      return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
  }
  ```

### 3.2 Coil Disk Cache Clearing
- Access Coil global `ImageLoader` via `context.imageLoader` (Coil 2.x extension).
- Execution: Wrap in `withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear(); context.imageLoader.memoryCache?.clear() }`.

### 3.3 Backup Progress UI Representation
- `BackupManager.createBackup` and `BackupManager.restoreBackup` return a `Boolean` result without emitting step progress.
- **UI Shape**: While backup/restore is in flight, the button displays an indeterminate `CircularProgressIndicator` and "Backing up..." or "Restoring...".

### 3.4 Last Backup Timestamp Fetch
- **Current Gap**: `ChatApi.kt` contains `updateBackupLog(log: BackupLogDto): Boolean`, but lacks a `fetchBackupLog` query.
- **Addition to `ChatApi.kt`**:
  ```kotlin
  suspend fun fetchBackupLog(personaId: String): BackupLogDto? {
      return try {
          supabase.postgrest["backup_log"]
              .select {
                  filter {
                      eq("persona_id", personaId)
                  }
              }
              .decodeSingleOrNull<BackupLogDto>()
      } catch (e: Exception) {
          Log.e(TAG, "Failed to fetch backup log: ${e.message}")
          null
      }
  }
  ```
- **Execution**: Called inside a non-blocking `LaunchedEffect` on page open. If offline or failed, UI defaults to `"Last backed up: Not available offline"`.

---

## 4. UI Pattern Findings

Mirrors `ProfileSettingsPage.kt`, `WallpaperSettingsPage.kt`, and `PrivacySettingsPage.kt`:

- **Card Container**: `Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)), border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)))`.
- **Top App Bar**:
  ```kotlin
  TopAppBar(
      title = {
          Text(
              text = "STORAGE & DATA",
              color = Color.White,
              fontFamily = AppFonts.Gamaamli,
              fontWeight = FontWeight.Normal,
              fontSize = 20.sp,
              letterSpacing = 1.sp
          )
      },
      navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
              Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
      },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
  )
  ```
- **State Loading**: Non-blocking asynchronous calculation inside `LaunchedEffect(Unit)` on `Dispatchers.IO`. Page renders instantly with loading indicators or standard placeholders.
- **Confirmation Dialogs**: Material 3 `AlertDialog` with dark surface `Color(0xFF1E1E1E)`, `AppFonts.PlayfairDisplay` title, red confirm text button (`Color(0xFFFF5252)`), and cancel button (`Color.White.copy(alpha = 0.7f)`).

---

## 5. Navigation Wiring Points

1. **`MainActivity.kt`**:
   Add to `NavHost` composable routes:
   ```kotlin
   composable("settings/storage") {
       StorageSettingsPage(navController, settingsViewModel = settingsViewModel)
   }
   ```
2. **`SettingsCenterScreen.kt`**:
   Add to `CategoryCard` click listener `when (category.id)` block (line 142):
   ```kotlin
   "storage" -> navController.navigate("settings/storage") { launchSingleTop = true }
   ```
3. **Route Name**: `"settings/storage"`, matching standard settings sub-route conventions.

---

## 6. State Management Plan

- **Architecture**: `StorageSettingsPage` manages transient screen state locally via `remember { mutableStateOf(...) }` and coroutine-scoped execution (`scope.launch(Dispatchers.IO)`).
- **Asynchronous Data Loading**:
  - `LaunchedEffect(Unit)` triggers non-blocking recalculation of Room DB size, Avatar cache size, Image cache size, Voice cache size, and fetches `fetchBackupLog(personaId)`.
  - Cache clearing re-triggers file size calculation and displays a toast upon completion.

---

## 7. File Plan

### 7.1 New Files
1. `app/src/main/java/com/baroness/app/utils/StorageUtils.kt`
   - Purpose: Recursive directory size extension function (`totalSizeBytes()`) and human-readable byte formatter (`formatBytes(bytes)`).
2. `app/src/main/java/com/baroness/app/screens/settings/StorageSettingsPage.kt`
   - Purpose: Storage & Data settings screen containing Storage Overview, Clear Caches, and Backup & Restore sections.

### 7.2 Modified Files
1. `app/src/main/java/com/baroness/app/api/ChatApi.kt`
   - Purpose: Add `fetchBackupLog(personaId: String): BackupLogDto?` method to read last backup metadata from PostgREST.
2. `app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt`
   - Purpose: Wire `"storage"` category card in `when (category.id)` switch to navigate to `"settings/storage"`.
3. `app/src/main/java/com/baroness/app/MainActivity.kt`
   - Purpose: Register `composable("settings/storage")` in `NavHost`.

### 7.3 Files NOT Touched
- `repository/BackupManager.kt`
- `voice/VoiceCacheManager.kt`
- `data/AvatarRepository.kt`
- `MainApplication.kt`
- `auth/SessionLifecycleManager.kt`
- `repository/WishlistRepository.kt`
- `repository/ChatRepository.kt`
- `utils/SyncManager.kt`

---

## 8. Confirmation Dialog Reuse Plan

Reuses the established `AlertDialog` pattern from `ProfileSettingsPage.kt` and `PrivacySettingsPage.kt` for destructive or high-impact actions:

1. **Clear All Caches Confirmation**:
   - Title: `"Clear All Caches?"`
   - Body: `"This will clear temporary image, avatar, and voice caches. Your account data, messages, and settings will remain intact."`
   - Action: Wipes Coil disk cache, `AvatarRepository` cache, and `VoiceCacheManager` temp cache, then refreshes storage metrics.
2. **Restore Cloud Backup Confirmation**:
   - Title: `"Restore Chat History?"`
   - Body: `"This will download and restore chat messages from the cloud backup for your persona. Existing local messages will be merged."`
   - Action: Triggers `BackupManager.restoreBackup(personaId)`.

---

## 9. Platform & Permission Considerations

- **Private Storage Access**: `context.filesDir` and `context.cacheDir` are app-private sandbox directories and require **no Android runtime permissions**.
- **Offline / Network Failures**: If `ChatApi.fetchBackupLog` fails due to network loss, `lastBackupAt` remains `null` and the UI gracefully renders `"Last backed up: Not available offline"`.
- **Main Thread Protection**: All disk I/O and network operations execute strictly on `Dispatchers.IO`.

---

## 10. Open Questions

None. All required signatures, paths, and patterns are confirmed.

---

## 11. Pre-Implementation Verification

### 11.1 Restore Behavior: Merged or Replaced?
- **Finding**: **MERGED**. `BackupManager.restoreBackup(personaId)` downloads `MessageDto` records from Supabase Storage and calls `messageDao.insertMessages(entities)`. `MessageDao.insertMessages` is annotated with `@Insert(onConflict = OnConflictStrategy.REPLACE)`.
- **Effect**: It does NOT call `messageDao.clearAll()` or wipe local chat data before inserting. New messages from backup are inserted, and matching message IDs are replaced/updated. Existing local messages not in the backup remain present in Room.
- **Dialog Text Requirement**: Confirmation dialog text will explicitly state `"Existing local messages will be merged with the restored backup."`

### 11.2 Determinate Progress Feasibility: Yes or No?
- **Finding**: **NO**. `BackupManager.createBackup` and `restoreBackup` are monolithic `suspend` functions that return `Boolean` upon completion without emitting progress callbacks or `Flow` events.
- **Effect**: Indeterminate `CircularProgressIndicator` (spinning loading bar) is the only available UI representation without modifying `BackupManager`.

### 11.3 BackupLogDto Confirmation
- **Finding**: Confirmed in `app/src/main/java/com/baroness/app/api/ChatApi.kt:62`:
  ```kotlin
  @Serializable
  data class BackupLogDto(
      @SerialName("persona_id") val personaId: String,
      @SerialName("last_backup_at") val lastBackupAt: String? = null,
      @SerialName("message_count_at_backup") val messageCountAtBackup: Int
  )
  ```
- **Effect**: `BackupLogDto` is 100% compatible with the proposed `fetchBackupLog(personaId: String)` query.

### 11.4 Room Database Filename
- **Finding**: Confirmed in `app/src/main/java/com/baroness/app/data/local/database/AppDatabase.kt:168`:
  ```kotlin
  Room.databaseBuilder(
      context.applicationContext,
      AppDatabase::class.java,
      "baroness_wishlist.db"
  )
  ```
- **Effect**: The actual database filename created by Room on device is **`baroness_wishlist.db`**.

### 11.5 Voice Clear Scope
- **Finding**: `VoiceCacheManager.kt` provides `clearTempCache()`, which deletes files in `cacheDir/voice_cache/temp_dynamic/`. Static cached phrases in `cacheDir/voice_cache/static_phrases/` are managed automatically by private LRU trimming (`trimCacheIfNeeded()`, when > 30MB) and 24h expiration (`cleanupOldFiles()`).
- **Effect**:
  - "Clear Voice Temp Cache" button calls `VoiceCacheManager(context).clearTempCache()`.
  - "Clear All Caches" button calls `clearTempCache()` and deletes files in `cacheDir/voice_cache/static_phrases/` directly via file I/O on `Dispatchers.IO`.

### 11.6 Adjustments Implied by Verification
1. Confirmation dialog text for restore explicitly updated to indicate **merging**.
2. Backup/Restore UI button progress verified as **indeterminate**.
3. Room database size calculation verified to target `"baroness_wishlist.db"`.
4. Voice cache size & clearing verified for both `temp_dynamic` and `static_phrases` subdirectories.
