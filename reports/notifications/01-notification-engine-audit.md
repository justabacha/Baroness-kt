# Notification Engine & Preferences — Architecture Audit Report

## 1. Executive Summary

This audit evaluates the architecture required to build a centralized `NotificationDispatcher`. Currently, notification emission is split across `WishlistRepository`, `AlarmReceiver`, `FCMService`, and `NotificationCenter`, while user preferences in `SettingsRepository` only expose a single toggle for in-app banners. There is no central tracking of app foreground/background state, no system notification master switch or per-category toggles, and no push payload routing logic. Establishing a unified dispatcher requires implementing lifecycle state tracking, expanding DataStore preferences, and standardizing dispatch channels.

---

## 2. Preference State (Section 1)

- **Existing Notification Settings**:
  - `KEY_IN_APP_BANNERS_ENABLED` (`"in_app_banners_enabled"`): Exposed via `saveInAppBannersEnabled`, `getInAppBannersEnabledFlow()`, and `getInitialInAppBannersEnabled()` in `SettingsRepository.kt`.
- **Storage**: Persisted as a boolean in DataStore Preferences via `StorageManager`.
- **Consumer**: `NotificationViewModel.showInAppNotification()` checks `repository.getInAppBannersEnabledFlow().first()` before adding an incoming notification to the display queue.
- **Missing Controls**:
  - **Master Notification Switch**: No global system notification toggle exists.
  - **Per-Category Switches**: No toggles exist for Wishlist, Chat/Messages, Alarms/Clock, or General notifications.
  - **Quiet Hours / Sound Overrides**: No time-based or sound-mute preference controls exist for notifications.

---

## 3. App Lifecycle & Foreground Detection (Section 2)

- **`ProcessLifecycleOwner` Observer**:
  - Registered in `MainApplication.kt` (lines 44–55) observing `Lifecycle.Event.ON_RESUME` to trigger session token refresh.
  - Also observed in `WishlistRepository.kt` (line 128) and `ChatRepository.kt` (line 68) to refresh remote data on app resume.
- **Central Foreground Tracking**: **None**. There is currently no global `StateFlow<Boolean>` or singleton tracking whether the app is currently in the Foreground or Background.
- **FCM Foreground vs. Background Behavior (`FCMService.kt`)**:
  - When `onMessageReceived()` receives a push message, it parses `NotificationData` and **always invokes `notificationManager.showSystemNotification(notificationData)`**, regardless of whether the app is in the foreground or background.
  - A TODO comment exists in `FCMService.kt` line 52 noting that routing between system notifications and in-app banners is unhandled.

---

## 4. Current Emission Sources (Section 3)

| Emission Source | Trigger Event | Call Invoked | Styling Responsibility |
| :--- | :--- | :--- | :--- |
| `WishlistRepository.kt:538` | Supabase Realtime wish insert from peer | `notificationViewModel?.showInAppNotification(...)` | Caller builds `NotificationData`; banner handles UI |
| `AlarmReceiver.kt:130` | Alarm / Timer expiration broadcast | `notificationManager.notify(...)` + `NotificationCenter.show(...)` | Hardcoded system builder + in-app banner queue |
| `FCMService.kt:58` | Incoming FCM push message | `notificationManager.showSystemNotification(...)` | `NotificationManager` constructs `NotificationCompat.Builder` |
| `ChatRoomScreen.kt:149` | Live chat connection state change | `settingsViewModel.showWarning("Connecting...")` | `TopWarningBanner` composable styling |
| `WishlistViewModel.kt:151` | Offline delete error | `_warningState.value = WarningState(...)` | `TopWarningBanner` composable styling |

---

## 5. System Notification Styling (Section 4)

- **File**: `utils/NotificationManager.kt` & `receiver/AlarmReceiver.kt`.
- **`NotificationCompat.Builder` Configuration**:
  - Small Icon: `R.drawable.icon` (app icon).
  - Priority: `NotificationCompat.PRIORITY_DEFAULT` (or `PRIORITY_MAX` for Alarms).
  - Auto-Cancel: `setAutoCancel(true)`.
  - Channel Mapping: `CHANNEL_WISHLIST`, `CHANNEL_MESSAGES`, `CHANNEL_GENERAL`, or `CHANNEL_ID` (Clock).
- **PendingIntent & Routing**:
  - Constructs `Intent(context, MainActivity::class.java)` with `putExtra("route", data.route)`.
  - Intent flags: `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`.
- **Grouping Logic**: **None**. No notification grouping keys (`setGroup`) or summary notifications are implemented.

---

## 6. In-App Banner Styling (Section 5)

- **File**: `components/notification/InAppNotification.kt`.
- **Appearance**:
  - Container: `Surface` with `fillMaxWidth()`, `padding(16.dp)`, `statusBarsPadding()`, `clip(RoundedCornerShape(24.dp))`, surface color `#1E1E1E`, 8.dp elevation.
  - Avatar: 48.dp `CircleShape` image loading `data.avatarUrl` via Coil `AsyncImage`.
  - Typography: Title (`titleMedium`, bold white 15.sp), Body (`bodyMedium`, `Colors.textDim` 13.sp).
  - Action Control: Close button `×` dismisses banner; tapping the card body invokes `onClick(data.route)` to navigate.
- **Dismissal Timing**: Managed by `NotificationViewModel` with a **5-second auto-dismiss delay** (`delay(5000)`).

---

## 7. Exceptions & Non-Centralized Alerts (Section 6)

The following alert types **should remain separate** from the central `NotificationDispatcher`:

1. **Android System Toasts (`Toast.makeText`)**: 25+ instances providing instant, non-intrusive feedback (e.g. *"Copied to clipboard"*, *"Saved to Gallery"*, *"Theme applied successfully"*).
2. **Transient UI Connection Banners (`TopWarningBanner`)**: E.g. *"Connecting to live chat..."* or offline error states. These represent reactive UI screen states, not dispatchable notification items.
3. **Media Playback Controls (`BaronessMediaService.kt`)**: Background music player notifications managed by Android Media3 Session API.

---

## 8. Open Questions for Central Dispatcher Design

1. **App Lifecycle Tracking**: Should `MainApplication` maintain a global `StateFlow<AppLifecycleState>` (`Foreground` / `Background`) to allow `NotificationDispatcher` to route incoming events automatically?
2. **Preference Schema**: Should `SettingsRepository` add DataStore keys for `master_notifications_enabled`, `wishlist_notifications_enabled`, `messages_notifications_enabled`, and `alarms_notifications_enabled`?
3. **Dispatch Routing Policy**: When the app is in the Foreground, should events show an **In-App Banner only**, a **System Notification only**, or **both** depending on user preference?
