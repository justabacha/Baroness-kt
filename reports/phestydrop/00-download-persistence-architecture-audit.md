# PhestyDrop Download Persistence — Architecture Audit Report

## 1. Executive Summary

An architectural audit of the PhestyDrop update engine was conducted to evaluate how downloads behave during sheet dismissal, how progress is managed, and what platform capabilities exist. Currently, `PhestyDropManager.downloadDrop` returns a `Flow<DownloadState>` whose collection is bound to a composition-scoped `rememberCoroutineScope()` inside `PhestyDropSheet`. Dismissing the sheet cancels the coroutine, which closes the OkHttp byte stream and aborts the APK download. The app lacks an ongoing download notification channel, foreground service for data sync, or cross-screen download state holder. Transitioning to persistent, background-resilient downloads requires selecting a long-lived execution owner (System `DownloadManager`, `WorkManager`, Foreground Service, or Application-scoped Coroutine), adding notification support, and exposing observable state across UI screens.

---

## 2. PhestyDropManager.downloadDrop Internals

Defined in `app/src/main/java/com/baroness/app/phestydrop/manager/PhestyDropManager.kt:62-113`:

```kotlin
fun downloadDrop(context: Context, dropInfo: PhestyDropInfo): Flow<DownloadState> = flow {
    emit(DownloadState.Idle)
    try {
        val request = Request.Builder().url(dropInfo.downloadUrl).build()
        val response = SupabaseConfig.client.newCall(request).execute()
        // ...
        val apkFile = File(context.cacheDir, "phesty_drop_v${dropInfo.versionName}.apk")
        if (apkFile.exists()) { apkFile.delete() }

        body.byteStream().use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = if (contentLength > 0) totalRead.toFloat() / contentLength.toFloat() else 0f
                    emit(DownloadState.Downloading(progress, totalRead, contentLength))
                }
                output.flush()
            }
        }
        emit(DownloadState.Ready(apkFile))
    } catch (e: Exception) {
        emit(DownloadState.Error(...))
    }
}.flowOn(Dispatchers.IO)
```

- **Flow Type**: Cold `flow { ... }` builder executing synchronously upon collection on `Dispatchers.IO`.
- **Cancellation Impact**: When the collecting coroutine is cancelled, the `flow` block throws `CancellationException`. The `use` blocks close `byteStream` and `FileOutputStream`, closing the OkHttp network socket and cancelling the stream immediately.
- **File Storage Path**: `context.cacheDir/phesty_drop_v<versionName>.apk` (`/data/user/0/com.baroness.app/cache/phesty_drop_v1.0.2.apk`).
- **File Staging**: Writes directly to target file name; no temporary `.tmp` file is used.
- **Interrupted File Cleanup**: Does not delete partially downloaded files on `catch`. However, `if (apkFile.exists()) apkFile.delete()` cleans up partial files at the start of the next download attempt.
- **Client Ownership**: Uses `SupabaseConfig.client` singleton.
- **Observable State**: **NONE**. `PhestyDropManager` holds no state variables or flows for active downloads.

---

## 3. PhestyDropSheet Collection Scope

Defined in `app/src/main/java/com/baroness/app/phestydrop/ui/PhestyDropSheet.kt`:

- **Scope**: `val scope = rememberCoroutineScope()` (`line 32`). Tied directly to `PhestyDropSheet` Compose lifecycle.
- **Collection Trigger**: `scope.launch { PhestyDropManager.downloadDrop(context, dropInfo).collect { downloadState = it } }` called inside "Accept Drop" button click listener (`line 193`).
- **Job Storage**: Unstored / fire-and-forget inside `scope`.
- **Dismissal Effect**: When `onDismiss()` is called, `PhestyDropSheet` leaves composition and Compose cancels `scope`. The coroutine collecting `downloadDrop` is cancelled, terminating the OkHttp download stream instantly.
- **State Persistence**: `var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }` dies when the sheet closes.

---

## 4. Android Platform State

- **Build Config**: `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24` (`app/build.gradle.kts`).
- **Manifest Permissions** (`AndroidManifest.xml`):
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
  - `FOREGROUND_SERVICE_DATA_SYNC` is **NOT** declared.
- **Services Declared**:
  - `com.baroness.app.services.FCMService`
  - `com.baroness.app.media.BaronessMediaService` (`foregroundServiceType="mediaPlayback"`)
- **Notification Permission**: `POST_NOTIFICATIONS` is declared and requested at runtime in `MainActivity.onCreate()`.
- **Notification Channels**: Defined in `NotificationManager.kt` (`CHANNEL_WISHLIST`, `CHANNEL_MESSAGES`, `CHANNEL_GENERAL`).

---

## 5. Existing Background Work Patterns

1. **WorkManager (`CoroutineWorker`)**:
   - `SyncWorker.kt` (`class SyncWorker : CoroutineWorker`): Periodic 15-min wishlist queue sync (`MainActivity.kt:240`).
   - `ChatSyncWorker.kt` (`class ChatSyncWorker : CoroutineWorker`): One-time chat outbox flush (`ChatRepository.kt:680`).
2. **Foreground Service**:
   - `BaronessMediaService.kt` (ExoPlayer media session, `foregroundServiceType="mediaPlayback"`).
3. **Android System `DownloadManager`**:
   - **NOT used** anywhere in the project.
4. **Application / Repository Singleton Coroutine Scope**:
   - `WishlistRepository.kt:80` (`private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`).
   - `MainApplication.kt:23` (`private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`).

---

## 6. Existing Notification Infrastructure

- **Channels** (`NotificationManager.kt:14-18`):
  - `CHANNEL_WISHLIST` ("wishlist_notifications", `IMPORTANCE_DEFAULT`)
  - `CHANNEL_MESSAGES` ("messages_notifications", `IMPORTANCE_HIGH`)
  - `CHANNEL_GENERAL` ("general_notifications", `IMPORTANCE_LOW`)
- **Notification Helper**: `NotificationManager(context).showSystemNotification(data)`.
- **Progress Notifications**: **NONE**. No `setProgress(...)` or `setOngoing(true)` calls exist in the project.

---

## 7. Existing PhestyDrop State

- **StorageManager Keys**:
  - `"phestydrop_auto_update"` (Boolean)
  - `"phestydrop_last_checked"` (Long timestamp)
- **Database / In-Memory State**: No Room tables or singleton fields hold in-progress download state.

---

## 8. Post-Dismissal Behavior Trace

1. User taps "Accept Drop" in `PhestyDropSheet`.
2. `scope.launch` collects `downloadDrop(context, dropInfo)`, streaming bytes.
3. User swipes down or taps outside sheet (`onDismiss()`).
4. Sheet leaves composition; Compose cancels `scope`.
5. `downloadDrop` flow collection throws `CancellationException`.
6. OkHttp byte stream closes; file writing stops.
7. Reopening sheet creates a new `PhestyDropSheet` instance with fresh `DownloadState.Idle` state ("Accept Drop" button).

---

## 9. OkHttp Client Ownership

Defined in `config/SupabaseConfig.kt:26-31`:
```kotlin
val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor(AuthTokenInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
```
- **Timeouts**: `connectTimeout = 15s`, `readTimeout = 15s`.
- **Constraint**: If streaming stalls for >15s on poor network connections, OkHttp throws `SocketTimeoutException`.

---

## 10. Android Version-Specific Constraints (API 36 / targetSdk 36)

- **Foreground Services on Android 14+ (API 34/35/36)**:
  - Must declare `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` for data sync types.
  - Enforces a 6-hour runtime execution cap.
  - Background start restrictions apply if launched when app is not in foreground.
- **System `DownloadManager`**:
  - Managed by Android OS (`com.android.providers.downloads`).
  - Bypasses app foreground service declarations and handles background downloads, notifications, and retries natively.

---

## 11. Cross-Screen Awareness Requirements

- To allow `AboutBaronessPage.kt`, `PhestyDropSheet.kt`, and `DrawerAbout.kt` to observe active downloads:
  - Requires a central singleton / manager holding an observable `StateFlow<DownloadState>`.
  - UI components collect this `StateFlow` to display progress or "Downloading..." indicators without re-triggering downloads.

---

## 12. Confirmed Absences

1. No PhestyDrop download notification channel.
2. No progress notification builder (`setProgress`).
3. No `FOREGROUND_SERVICE_DATA_SYNC` permission declared.
4. No system `DownloadManager` integration.
5. No persistent download state across composable or process lifecycles.
6. No temporary `.tmp` file download staging.

---

## 13. Decision Surface (Choices for Next Phase)

The following architectural choices must be settled during implementation design:

1. **Download Execution Owner**:
   - *Choice A*: Android System `DownloadManager` (`context.getSystemService(DownloadManager::class.java)`).
   - *Choice B*: WorkManager `CoroutineWorker` (`OneTimeWorkRequest`).
   - *Choice C*: Application-scoped Coroutine (`SupervisorJob() + Dispatchers.IO` inside `PhestyDropManager`).
   - *Choice D*: Android Foreground Service (`dataSync` type).

2. **Download State Persistence & Visibility**:
   - In-memory `StateFlow<DownloadState>` in `PhestyDropManager` for cross-screen UI reactivity.
   - Persistence in `StorageManager` to survive process death.

3. **Notification Strategy**:
   - Dedicated `CHANNEL_PHESTYDROP` notification channel.
   - Progress notification with `setProgress(100, progress, false)` and ongoing flag.

4. **OkHttp Timeout Configuration**:
   - Dedicated OkHttp client instance without restrictive 15s `readTimeout` for large APK downloads.
