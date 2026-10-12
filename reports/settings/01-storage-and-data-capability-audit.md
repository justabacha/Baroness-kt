# Storage & Data Page — Capability Audit Report

## 1. Executive Summary

An audit of the Baroness codebase was conducted to evaluate existing capabilities for disk storage measurement, cache management, database backup/restore, data synchronization, and system reset. The backend storage engine, database schema, and `BackupManager` are **fully written in Kotlin** (`BackupManager.kt`, `VoiceCacheManager.kt`, `AvatarRepository.kt`, `SessionLifecycleManager.kt`), but database import/export and cache clearing operations are **currently unmounted in the user interface**. Creating the "Storage & Data" page in Settings Center requires UI wiring to these existing managers rather than writing new infrastructure.

---

## 2. Storage Usage Measurement Capability

| Cache / Storage Category | Measurable at Runtime? | Storage Location / Path | Method & Citation |
| :--- | :--- | :--- | :--- |
| **Room Database** | **YES** | `context.getDatabasePath("baroness_wishlist.db")` | File size via `context.getDatabasePath("baroness_wishlist.db").length()`. (`AppDatabase.kt:168`) |
| **Avatar Cache** | **YES** (File Walk) | `File(context.filesDir, "user_avatars")` | No helper method in `AvatarRepository.kt`; calculated via directory file tree traversal (`sumOf { it.length() }`). (`AvatarRepository.kt:47,62,69`) |
| **Coil Image Cache** | **YES** (File Walk) | `File(context.filesDir, "image_cache")` | Configured in `MainApplication.kt` (`filesDir.resolve("image_cache")`). Coil 2.x `DiskCache` interface does not expose size property; calculated via file tree traversal. (`MainApplication.kt:30`) |
| **Custom Wallpapers** | **YES** (File Walk) | `File(context.filesDir, "wallpapers")` | Stored as `wallpapers/user_wallpaper.jpg`. Calculated via file length. (`WallpaperSettingsPage.kt:62`, `SettingsViewModel.kt:252`) |
| **Voice Cache** | **YES** (File Walk / Internal) | `File(context.cacheDir, "voice_cache")` | Subdirectories `static_phrases` and `temp_dynamic`. `VoiceCacheManager` defines `maxCacheSize = 30 MB`. (`VoiceCacheManager.kt:9-11,12`) |
| **Total App Storage** | **NO** | N/A | No centralized utility currently computes combined storage across Room, caches, and files. |

---

## 3. Cache Clearing Capability

| Cache Type | Existing Clear Method | Scope Cleared | Exposed in UI Today? |
| :--- | :--- | :--- | :--- |
| **Avatar Cache** | `AvatarRepository.clearCache()` (`AvatarRepository.kt:68`) | Deletes `avatar_*` files in `context.filesDir/user_avatars/` and removes `avatar_local_path` & `avatar_cached_url` keys in `StorageManager`. | **NO** |
| **Coil Image Cache** | **None** | N/A (No `imageLoader.diskCache?.clear()` method exists in codebase). | **NO** |
| **Custom Wallpaper** | **Partial** (`SettingsViewModel.setUserWallpaper`) | `setUserWallpaper(uri)` overwrites `user_wallpaper.jpg`. Reverting wallpaper changes active preference ID to default ("sunrise") but leaves physical file on disk. | **NO** (File deletion not exposed) |
| **Voice Cache** | `VoiceCacheManager.clearTempCache()` (`VoiceCacheManager.kt:98`) | Clears `cacheDir/voice_cache/temp_dynamic/`. `VoiceCacheManager` also auto-trims static cache to 70% when exceeding 30MB. | **NO** |
| **Unified "Clear All"** | **None** | N/A (No single method spans avatars, Coil, wallpapers, and voice cache). | **NO** |

---

## 4. Backup and Restore State

- **`BackupManager.kt`**: Fully written and functional in `app/src/main/java/com/baroness/app/repository/BackupManager.kt`.
- **Public API**:
  ```kotlin
  suspend fun createBackup(personaId: String): Boolean
  suspend fun restoreBackup(personaId: String): Boolean
  ```
- **Supabase Cloud Bucket**: Integrated with Supabase Storage bucket `chat_backups`. Uploads and downloads authenticated JSON files under `backups/${personaId}_official_backup.json`. (`BackupManager.kt:58,87`)
- **`backup_log` Database Table**: Integrated. `BackupManager.createBackup` calls `ChatApi.updateBackupLog(...)` which upserts backup metadata (`personaId`, `lastBackupAt`, `messageCountAtBackup`) to `backup_log` via PostgREST. (`ChatApi.kt:203`)
- **UI Exposure**: **UNMOUNTED**. No screen, button, or dialog in the application UI currently invokes `createBackup` or `restoreBackup`.
- **Scheduled Backup**: **NONE**. No `WorkManager` background job or automated backup schedule exists.
- **Overall State**: **PARTIALLY IMPLEMENTED (Engine written, UI unmounted)**.

---

## 5. Force Re-Sync State

- **Wishlist Data Re-Sync**: `WishlistRepository.kt` has `initialSync()`, `fetchProfiles()`, and queue processing (`SyncManager.processQueue()`). However, **no method exists** to clear local Room tables (`wishes`, `reactions`, `ratings`) and force a clean server fetch.
- **Chat Data Re-Sync**: `ChatRepository.kt` has `fetchOfflineMessages()` and `triggerSync()`. `MessageDao.clearAll()` exists (used on logout), but **no UI-exposed method exists** to clear local chat history and re-download from Supabase.
- **Friday Data Re-Sync**: Shares `ChatRepository` and `MessageDao` (`conversationId = "friday"`).
- **UI Exposure**: **NONE**. No UI path currently triggers a manual force re-sync.

---

## 6. Clear All Local Data / Factory Reset State

- **Session Wiping & Teardown**: Handled by `SessionLifecycleManager.logout(context, revokeServerSession = true)` (`auth/SessionLifecycleManager.kt:19`).
- **Data Layers Wiped During Logout**:
  1. Revokes server auth session via `/auth/v1/logout` API request.
  2. Disconnects Supabase Realtime socket.
  3. Cancels all `WorkManager` background tasks.
  4. Clears Room `messages` table (`database.messageDao().clearAll()`) and `sync_queue` table (`database.syncQueueDao().clearAll()`). *(Wishlist data `wishes`, `reactions`, `ratings` is preserved across logout).*
  5. Clears `StorageManager` session data (`clearSessionData()`).
  6. Clears `UserSessionManager` SharedPreferences.
  7. Clears in-memory `SessionTokenCache`.
- **Layers NOT Wiped During Logout**: Disk cache directories (`user_avatars/`, `image_cache/`, `wallpapers/`, `voice_cache/`) remain on disk across logout.
- **UI Exposure**: Exposed via "LOG OUT" button on `ProfileSettingsPage.kt` and "CLEAR LOCAL SESSION & RE-GATE" button on `PrivacySettingsPage.kt`.

---

## 7. Privacy Page Overlap Analysis

- **`PrivacySettingsPage.kt` Current Scope**:
  - Focuses on **security and session authentication**: Passkey verification status, active persona ID, JWT auth engine status, FCM token status, and Vault Gate re-authentication (`SessionLifecycleManager.logout`).
- **Complementary Relationship with Storage & Data**:
  - `PrivacySettingsPage` manages **authentication session reset**.
  - `StorageSettingsPage` will manage **disk storage metrics, cache clearing, database backup/restore, and PhestyDrop updates**.
  - No functional overlap or conflict exists between the two screens.

---

## 8. Drawer Storage Panel State

- **`components/DrawerStorage.kt`**:
  - Renders a placeholder glass card titled `"STORAGE"` containing `"Coming soon..."`.
  - Referenced in `GlobalDrawer.kt` as an expandable category.
  - Contains no functional code or metrics to promote.

---

## 9. Existing "About" Content State

- **`components/DrawerAbout.kt`**:
  - Fully written and styled component (`AboutContent()`).
  - Contains app title, tagline, narrative story text, credits, and the **PhestyDrop Engine System Card** (version display, `phestydrop_auto_update` toggle, and manual "Check for PhestyDrop" update button).
  - Fully suitable for direct reuse or reference on the Settings Center "About Baroness" page (`"settings/about"`).

---

## 10. Capability Matrix

| Feature / Capability | Implementation State | Existing Codebase Reference |
| :--- | :--- | :--- |
| **Room Database Size Measurement** | **SUPPORTED** | `context.getDatabasePath("baroness_wishlist.db").length()` |
| **Avatar Cache Size Measurement** | **PARTIAL** | Directory file walk on `context.filesDir/user_avatars` |
| **Coil Image Cache Size Measurement** | **PARTIAL** | Directory file walk on `context.filesDir/image_cache` |
| **Voice Cache Size Measurement** | **PARTIAL** | Directory file walk on `context.cacheDir/voice_cache` |
| **Total Storage Combined Calculation** | **NOT SUPPORTED** | No existing class; straightforward directory file walk |
| **Avatar Cache Clearing** | **SUPPORTED** | `AvatarRepository.clearCache()` |
| **Coil Image Cache Clearing** | **NOT SUPPORTED** | Requires adding `imageLoader.diskCache?.clear()` |
| **Wallpaper Cache Clearing** | **NOT SUPPORTED** | File overwrite supported; no file deletion method |
| **Voice Cache Clearing** | **PARTIAL** | `VoiceCacheManager.clearTempCache()` |
| **Database Export (Backup)** | **SUPPORTED** | `BackupManager.createBackup(personaId)` |
| **Database Import (Restore)** | **SUPPORTED** | `BackupManager.restoreBackup(personaId)` |
| **Cloud Storage Backup Bucket** | **SUPPORTED** | Supabase Storage bucket `chat_backups` |
| **Automated Scheduled Backup** | **NOT SUPPORTED** | No `WorkManager` background backup task |
| **Backup / Restore UI Controls** | **NOT SUPPORTED** | Engine written; UI unmounted |
| **Force Wishlist / Chat Re-Sync** | **NOT SUPPORTED** | Realtime & queue sync exist; no clear-and-refetch wrapper |
| **Session Wipe & Teardown** | **SUPPORTED** | `SessionLifecycleManager.logout(context)` |
| **PhestyDrop OTA Update Engine** | **SUPPORTED** | `PhestyDropManager.checkForDrop()` & `PhestyDropSheet` |
