# Storage & Data Page — Implementation Report

## Files created

- `app/src/main/java/com/baroness/app/utils/StorageUtils.kt` — recursive file-tree size calculation and locale-stable byte formatting.
- `app/src/main/java/com/baroness/app/screens/settings/StorageSettingsPage.kt` — Storage Overview, Clear Caches, and Backup & Restore UI and page-local state.

`StorageUtils.kt` adds the requested `File.totalSizeBytes()` extension (missing paths return 0, files return their length, and directories recursively sum children) and `formatBytes(Long)` (B/KB/MB/GB with one decimal place and `Locale.US`).

## Files modified

- `app/src/main/java/com/baroness/app/api/ChatApi.kt` — added `fetchBackupLog(personaId)` using the existing `BackupLogDto` and `backup_log` table, with error logging and `null` fallback.
- `app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt` — wired the existing `"storage"` category to `"settings/storage"` with `launchSingleTop = true`.
- `app/src/main/java/com/baroness/app/MainActivity.kt` — registered the `"settings/storage"` destination and passed the existing navigation controller and settings view model.

`ProfileSettingsPage` reads the persona ID from `UserSessionManager.currentUserId`; the new page uses the same source. The new screen performs independent file size calculations on `Dispatchers.IO`, fetches the backup log asynchronously, and updates each displayed size as it completes.

`ChatApi.fetchBackupLog` queries `backup_log` by `persona_id`, decodes the existing `BackupLogDto` with `decodeSingleOrNull`, and logs query errors before returning `null`. Navigation uses `"storage" -> navController.navigate("settings/storage") { launchSingleTop = true }`; MainActivity registers `composable("settings/storage")` and passes `navController` and `settingsViewModel`.

## Layout and behavior

The page uses the existing transparent settings TopAppBar style and a vertically scrollable column with 16.dp padding. Three 20.dp rounded M3 cards use the settings-card white 5%-alpha background and a 1.dp border using the active theme accent at 50% alpha.

- **Storage Overview:** Database, avatar cache, image cache, and voice cache rows, each showing a formatted size or “Calculating…”. A divider precedes Total; Total is shown only after all four measurements finish.
- **Clear Caches:** Image & avatar, voice temp, and clear-all rows show their corresponding cache sizes. Individual clears run without confirmation; clear-all asks for confirmation. Coil disk and memory caches, avatar cache, and VoiceCacheManager temp cache are cleared as selected, then sizes are recalculated and a toast reports the result.
- **Backup & Restore:** Backup state starts as “Checking…” and then shows the timestamp, “No backup yet”, or the active-operation status. Backup and restore run on IO with indeterminate progress indicators. Operations are mutually disabled. Restore requires confirmation and explicitly warns that messages deleted after the last backup will reappear while current local chats remain.

Timestamps render as “Today HH:mm”, “Yesterday HH:mm”, or “EEEE, d MMM”; unparseable strings are displayed unchanged. `formatBytes` returns one decimal place using `Locale.US`, with units B, KB, MB, and GB.

No engine files were modified. In particular, `BackupManager.kt`, `VoiceCacheManager.kt`, `AvatarRepository.kt`, `MainApplication.kt`, `SessionLifecycleManager.kt`, `WishlistRepository.kt`, `ChatRepository.kt`, and `SyncManager.kt` are unchanged. No database, schema, RLS, migrations, `DrawerStorage.kt`, other settings pages, or About card were modified.

## Build and unit tests

- `:app:testDebugUnitTest` — **passed**.
- `:app:assembleDebug` — **passed**.
- APK path: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`.

## Operator manual verification checklist

- [ ] Open Settings → tap “Storage & Data” → confirm the page opens.
- [ ] Confirm Storage Overview shows four size rows and Total; values populate shortly after opening.
- [ ] Clear image & avatar cache: confirm no dialog, a completion toast, and refreshed sizes.
- [ ] Clear voice temp cache: confirm a completion toast and refreshed sizes.
- [ ] Clear all caches: confirm the dialog text, accept, and verify all selected caches clear and sizes refresh.
- [ ] Backup row shows “Last backed up: <date>” or “No backup yet”.
- [ ] Tap “Back up now”: verify progress appears, backup is disabled in progress, and the timestamp refreshes on success.
- [ ] Tap “Restore”: verify confirmation says: “This will merge your cloud backup into your local chats. Messages you deleted after your last backup will reappear. Your current chats will not be removed.”
- [ ] Confirm restore: verify progress appears, backup/restore controls remain disabled during work, and completion feedback appears.
- [ ] Offline: verify local sizes still calculate; backup status resolves after its request; a failed backup displays “Backup failed — check your connection.”
