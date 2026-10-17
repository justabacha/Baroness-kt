# Notification Ecosystem — Comprehensive Audit Report

## 1. Executive Summary

This audit evaluates the entire notification and user-alert ecosystem across the Baroness application. The current ecosystem is fragmented across **six distinct mechanisms**: system status bar notifications, in-app top banners (`InAppNotification`), transient status warning banners (`TopWarningBanner`), audio/TTS/haptic alerts (`ClockSoundPlayer`/`VoiceCenter`/`VibrationHelper`), Android `Toast` popups, and UI badge/stat counters. Crucially, **no notification persistence layer exists** — past notifications are kept only in a transient, in-memory queue (`NotificationViewModel`) or system intent extras. Once dismissed, they cannot be viewed again.

---

## 2. Notification Source & Trigger Inventory (Section 2)

| Trigger / Feature | File & Line | Type | User-Visible Behavior | Data Source |
| :--- | :--- | :--- | :--- | :--- |
| **Realtime Wishlist Insert** | `WishlistRepository.kt:538` | In-App Banner & System | Banner slides down: *"[User] added a new wish"* | Supabase Realtime `wishlist_items` |
| **Incoming FCM Message** | `FCMService.kt:58` | System Notification | Status bar notification with title and body | Firebase Cloud Messaging (FCM) |
| **Alarm / Timer Expiration** | `AlarmReceiver.kt:130` | System + In-App + Audio + TTS | High-priority notification, 60s ring, TTS readout, in-app banner | Android `AlarmManager` broadcast |
| **Chat Connection Status** | `ChatRoomScreen.kt:149` | Transient Warning Banner | Cyan glassmorphic banner: *"Connecting to live chat..."* | `SettingsViewModel` StateFlow |
| **Offline Wish Delete Error**| `WishlistViewModel.kt:151` | Transient Warning Banner | Warning banner: *"Cannot delete wish while offline"* | `WishlistViewModel` StateFlow |
| **PhestyDrop Download Click** | `PhestyDropNotificationReceiver.kt:33` | System Notification Intent | Opens `AboutBaronessPage` on click | Android `DownloadManager` |
| **Message & Action Toasts** | `ChatCommunicationActions.kt:19` | Toast | Toast: *"Message unsent"*, *"Deleted for everyone"* | Local user action |
| **Clipboard / Media Capture**| `CaptureHelper.kt:47` | Toast | Toast: *"Saved to Gallery"*, *"Copied to clipboard"* | Local Android API |
| **Settings Confirmation** | `ThemeSettingsPage.kt:309` | Toast | Toast: *"Theme applied successfully"*, *"Caches cleared"* | Local Settings action |
| **Chat Unread Badge** | `ChatEntry.kt:70` | UI Badge | Counter badge on conversation list item | Room `MessageDao` unread query |

---

## 3. System Notification Infrastructure (Section 3)

### 3.1 Channels & Importance
Managed in `utils/NotificationManager.kt` and `receiver/AlarmReceiver.kt`:

- `CHANNEL_WISHLIST` (`"wishlist_notifications"`): Default importance. Wishlist updates.
- `CHANNEL_MESSAGES` (`"messages_notifications"`): High importance. Chat messages.
- `CHANNEL_GENERAL` (`"general_notifications"`): Low importance. System alerts.
- `CHANNEL_ID` (`"baroness_clock_channel"`): High importance (`PRIORITY_MAX`, `CATEGORY_ALARM`). Alarms & Timers.

### 3.2 FCM Handling (`FCMService.kt`)
- Receives remote payload containing `title`, `body`, `avatar_url`, `feature_type`, and `route`.
- Saves FCM token locally in DataStore and syncs to Supabase `profiles.fcm_token`.
- Automatically posts system notification via `NotificationManager.showSystemNotification(...)`.

### 3.3 Grouping & Summarizing
- **Current State**: Zero notification grouping (`groupKey`), stacking, or summary notifications. Each event posts an independent notification ID.

---

## 4. In-App Overlay Infrastructure (Section 4)

- **Render Host**: Mounted at root level in `MainActivity.kt` overlay layer, observing `NotificationViewModel.currentNotification`.
- **Component**: `InAppNotification.kt` (`components/notification/`).
- **Visual Style**: Dark glassmorphic card (`#1E1E1E`), 24.dp rounded corners, avatar image, title, body, close `×` button.
- **Queue & Dismissal**:
  - `NotificationViewModel` queues incoming `NotificationData` objects.
  - Automatically dismisses after a **5-second delay** (`delay(5000)`).
  - Tapping the banner triggers `onClick(data.route)` navigation and dismisses.
- **Toggle Control**: Enabled/disabled via DataStore preference `getInAppBannersEnabledFlow()` in `SettingsRepository`.

---

## 5. Transient Status & Error States (Section 5)

- **`TopWarningBanner` (`components/TopWarningBanner.kt`)**:
  - Glassmorphic top banner with cyan border (`#00f8db`), dark background blur, and warning icon.
  - Used in `ChatListScreen`, `ChatRoomScreen`, and `WishlistScreen`.
  - Used for connection status (`"Connecting to live chat..."`), offline wishlist actions, and theme/wallpaper save errors.
- **Android `Toast.makeText`**:
  - 25+ call sites across chat actions, image capture, wallpaper updates, font changes, and storage/cache clearing.

---

## 6. Audio & Haptic Alerts (Section 6)

- **Alarm/Timer Ringing**: `ClockSoundPlayer.kt` plays ringtones/chimes (`playSound`) and custom vibration patterns (`playVibration`).
- **AI Voice Readout**: `VoiceCenter.kt` executes TTS announcements using Friday AI voice config when timers expire (`AlarmReceiver.kt`) or when greetings trigger (`DashboardViewModel.kt`).
- **Haptics**: `VibrationHelper.kt` triggers device vibration motor.
- **Configuration**: User-configurable via `SoundHapticsSettingsPage.kt` and `SettingsRepository`.

---

## 7. Badges and Unread Indicators (Section 7)

- **Chat Unread Counts**: Computed per conversation in `ChatListViewModel` and displayed on `ChatEntry.kt`.
- **Wishlist Goal Stats**: Summarized in `WishlistHeader.kt` capsule (`"⭐ TOTAL GOALS ! DUSTED"`).
- **Navigation Badges**: No bottom navigation bar or top bar badge counters exist currently.

---

## 8. Data Model & Persistence Analysis (Section 8)

- **Data Class**: `NotificationData.kt` (`title`, `body`, `avatarUrl`, `featureType`, `route`, `timestamp`).
- **Persistence Assessment**: **NO PERSISTENCE EXISTS**.
  - In-app notifications live strictly in `NotificationViewModel`'s in-memory `queue: MutableList<NotificationData>`.
  - Once a banner auto-dismisses after 5 seconds or is cleared by the user, the data is destroyed.
  - Missed system notifications or in-app alerts cannot be reviewed later.

---

## 9. Cross-Feature Notification Matrix (Section 9)

| Feature | System Notification? | In-App Banner? | Audio / TTS? | Badge / Indicator? |
| :--- | :--- | :--- | :--- | :--- |
| **HumanChat** | Yes (FCM / Messages Channel) | Yes (InAppNotification) | No | Yes (Unread count) |
| **Friday AI** | Yes (FCM / Messages Channel) | Yes (InAppNotification) | Yes (VoiceCenter TTS) | Yes (Unread count) |
| **Wishlist** | Yes (Supabase Realtime) | Yes (InAppNotification) | No | Yes (Stats capsule) |
| **PhestyDrop** | Yes (DownloadManager) | No | No | No |
| **Clock / Alarms**| Yes (PRIORITY_MAX Alarm) | Yes (NotificationCenter) | Yes (Chime + AI Voice) | No |
| **Auth / Session** | No | No (Toast only) | No | No |

---

## 10. Open Questions for Future Notification Hub

1. **Storage Engine**: Should past notifications be persisted in a new Room DAO (`NotificationEntity`) or DataStore list to enable a "Notification History / Inbox" screen?
2. **Read/Unread Tracking**: Should incoming notifications track read status (`isRead: Boolean`)?
3. **Category Filtering**: Should the Notification Hub filter alerts by feature (`Wishlist`, `Messages`, `System`, `Alarms`)?
