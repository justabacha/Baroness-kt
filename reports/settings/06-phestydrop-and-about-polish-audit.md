# PhestyDrop Engine & About Page Polish — Capability Audit Report

## 1. Executive Summary

An audit of `PhestyDropManager`, `PhestyDropSheet`, `AboutBaronessPage`, and related call sites was conducted to evaluate the technical feasibility of button state feedback and copyright spacing adjustments. The PhestyDrop engine and sheet UI already support full, real-time, 0–100% byte progress reporting during APK streaming (`DownloadState.Downloading`), along with package installer invocation (`PhestyDropManager.installDrop`). The "Check for PhestyDrop" button currently relies on a simple binary state (`isCheckingDrop` true/false) and system Toasts. Enhancing the button with a temporary `"Up to date ✓"` or `"Check failed"` state for ~1 second requires only local UI state handling on `AboutBaronessPage.kt` without modifying `PhestyDropManager` or affecting other call sites (`MainActivity.kt`, `DrawerAbout.kt`).

---

## 2. PhestyDropManager Trace

Defined in `app/src/main/java/com/baroness/app/phestydrop/manager/PhestyDropManager.kt`:

### 2.1 Public API Surface
```kotlin
fun initialize(context: Context): Unit
suspend fun checkForDrop(): PhestyDropInfo?
fun downloadDrop(context: Context, dropInfo: PhestyDropInfo): Flow<DownloadState>
fun installDrop(context: Context, apkFile: File): Boolean
```

### 2.2 Behavior of `checkForDrop()`
- Executes on `Dispatchers.IO`.
- Queries Supabase table `phestydrop_releases` via PostgREST ordered by `version_code DESC` limit 1.
- If `appContext` is initialized, persists `System.currentTimeMillis()` to `StorageManager` key `"phestydrop_last_checked"`.
- Compares `latestDrop.versionCode > BuildConfig.VERSION_CODE`.
- Returns `latestDrop` (`PhestyDropInfo`) if a newer build exists; otherwise returns `null`.
- On exception/catch: logs error and returns `null`.
- **Return Type**: Simple nullable `PhestyDropInfo?` (no custom result wrapper).

### 2.3 Persisted State & Context
- `appContext`: Initialized in `MainActivity.onCreate()` via `PhestyDropManager.initialize(applicationContext)`.
- Persisted keys in `StorageManager`: `"phestydrop_last_checked"` (Long timestamp) and `"phestydrop_auto_update"` (Boolean preference).

### 2.4 Download Progress Stream
- `downloadDrop(...)` returns a `Flow<DownloadState>`.
- Emits:
  1. `DownloadState.Idle`
  2. `DownloadState.Downloading(progress: Float, bytesRead: Long, totalBytes: Long)`
  3. `DownloadState.Ready(apkFile: File)`
  4. `DownloadState.Error(message: String)`

---

## 3. PhestyDropSheet Trace

Defined in `app/src/main/java/com/baroness/app/phestydrop/ui/PhestyDropSheet.kt`:

### 3.1 Composable Signature
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhestyDropSheet(
    dropInfo: PhestyDropInfo,
    onDismiss: () -> Unit
)
```

### 3.2 Internal State & Progress Mechanism
- **Managed State**:
  - `downloadState` (`DownloadState`)
  - `autoUpdateEnabled` (`Boolean`)
- **Progress Flow**: Collects `PhestyDropManager.downloadDrop(context, dropInfo)` flow on a coroutine scope when user taps "Accept Drop" or "Retry Download".
- **Progress Type**: **Determinate** (`0.0f` to `1.0f`), rendered via `LinearProgressIndicator(progress = { state.progress })` with percentage text `${(state.progress * 100).toInt()}%`.

### 3.3 Offered Actions & Handlers
- **"Later"**: Calls `onDismiss()`.
- **"Accept Drop"**: Starts collecting `downloadDrop` flow.
- **"Install PhestyDrop Now"** (when `Ready`): Calls `PhestyDropManager.installDrop(context, state.apkFile)`. If `true`, calls `onDismiss()`.
- **"Retry Download"** (when `Error`): Re-launches `downloadDrop` flow collection.
- **Auto-Download Switch**: Toggles `phestydrop_auto_update` preference in `StorageManager`.

---

## 4. AboutBaronessPage Current Button Behavior

Defined in `app/src/main/java/com/baroness/app/screens/settings/AboutBaronessPage.kt`:

### 4.1 State & Handlers
- Local state: `isCheckingDrop: Boolean`, `activeDropInfo: PhestyDropInfo?`, `lastCheckedAt: Long?`.
- On button click:
  1. Sets `isCheckingDrop = true`.
  2. Calls `withContext(Dispatchers.IO) { PhestyDropManager.checkForDrop() }`.
  3. Updates `lastCheckedAt` from `storageManager.getLong(LAST_CHECKED_KEY)`.
  4. If `drop != null`: sets `activeDropInfo = drop` (opens `PhestyDropSheet`).
  5. If `drop == null`: displays `Toast.makeText(context, "Baroness is fully up to date!", Toast.LENGTH_SHORT).show()`.
  6. On exception: displays `Toast.makeText(context, "Unable to check for PhestyDrop.", Toast.LENGTH_SHORT).show()`.
  7. `finally`: sets `isCheckingDrop = false`.
- **Current Button State**: Binary toggle (`isCheckingDrop` true/false). No transient label state ("Up to date ✓") on the button itself.

---

## 5. Sheet Download Progress Wiring

- Tapping "Accept Drop" in `PhestyDropSheet` starts collecting `PhestyDropManager.downloadDrop(context, dropInfo)` on `Dispatchers.IO`.
- `downloadDrop` streams OkHttp bytes into `File(context.cacheDir, "phesty_drop_v${dropInfo.versionName}.apk")`, emitting progress floats.
- Progress is rendered directly inside `PhestyDropSheet` with a progress bar and numeric percentage.
- Tapping "Install PhestyDrop Now" invokes `PhestyDropManager.installDrop`, launching Android's `ACTION_VIEW` intent via `FileProvider`.

---

## 6. Other Callers of PhestyDrop

Grep search for `PhestyDropManager.checkForDrop()`:

1. **`MainActivity.kt:180`**:
   - Startup auto-check in `LaunchedEffect(Unit)` if `phestydrop_auto_update` is enabled.
   - Behavior: Silent if up to date or error; opens `PhestyDropSheet` if a new drop is returned.
2. **`DrawerAbout.kt:174`**:
   - Side drawer "Check for PhestyDrop" button.
   - Behavior: Identical to current `AboutBaronessPage` (shows spinner during check, opens sheet if update found, or shows Toast if up to date).
3. **`AboutBaronessPage.kt:236`**:
   - Settings page "Check for PhestyDrop" button.

*Note: `PhestyDropManager.checkForDrop()` simply returns `PhestyDropInfo?`. UI button state transitions in `AboutBaronessPage` operate strictly in UI state and do not affect `MainActivity.kt` or `DrawerAbout.kt`.*

---

## 7. Toast & Banner Patterns

- All settings pages (`ProfileSettingsPage`, `WallpaperSettingsPage`, `StorageSettingsPage`, `AboutBaronessPage`) use standard Android `Toast.makeText(context, "...", Toast.LENGTH_SHORT).show()`.
- `InAppNotification` banner overlay exists in `MainActivity.kt`, but is reserved for FCM push notifications.
- For button label feedback, introducing a local sealed class/enum state (e.g. `Idle`, `Checking`, `UpToDate`, `Error`) with `delay(...)` in coroutine scope is the standard Compose pattern.

---

## 8. Copyright Spacing Analysis

Current code in `AboutBaronessPage.kt`:
```kotlin
Spacer(modifier = Modifier.height(24.dp))

val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
Text(
    text = "Baroness ©$currentYear · Powered by PhestyDrop",
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
    textAlign = TextAlign.Center,
    color = Color.White.copy(alpha = 0.35f),
    fontFamily = AppFonts.PlayfairDisplay,
    fontSize = 11.sp
)
```
- Total Gap before Copyright Text: `Spacer(24.dp)` + Column `spacedBy(20.dp)` + Text `padding(vertical = 10.dp)` = **54.dp** total vertical offset.
- Comparison with standard settings pages (`ProfileSettingsPage.kt`, `StorageSettingsPage.kt`):
  - Standard settings pages do not use an explicit `Spacer(24.dp)` before footer text, relying on standard Column `spacedBy(20.dp)` or `verticalPadding = 4.dp`.
  - Removing `Spacer(24.dp)` and reducing vertical padding on the copyright `Text` to `vertical = 4.dp` aligns `AboutBaronessPage` with the rest of the Settings Center.

---

## 9. Capability Matrix

| Feature / Behavior | Implementation State | Notes / Evidence |
| :--- | :--- | :--- |
| **Button shows "Checking…" spinner during check** | **SUPPORTED** | Currently driven by `isCheckingDrop = true` |
| **Button shows "Up to date ✓" for ~1s on null return, then reverts** | **SUPPORTED** | Achievable via Compose button state enum + `delay(1000L)` |
| **Button shows "Check failed" for ~1.5s on error, then reverts** | **SUPPORTED** | Achievable via Compose button state enum + `delay(1500L)` |
| **Sheet opens when drop is available** | **SUPPORTED** | Driven by `activeDropInfo = drop` |
| **Sheet shows download progress with percentage during APK download** | **SUPPORTED** | `PhestyDropSheet` renders `LinearProgressIndicator` + `${(progress * 100).toInt()}%` |
| **Sheet shows install trigger after download completes** | **SUPPORTED** | Renders "Install PhestyDrop Now" button on `DownloadState.Ready` |
| **Copyright spacing reduced** | **SUPPORTED** | Removing `Spacer(24.dp)` and adjusting padding in `AboutBaronessPage.kt` |

---

## 10. Open Questions

None. All required managers, UI flows, and state mechanisms are confirmed.
