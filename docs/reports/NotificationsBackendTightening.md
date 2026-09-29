# Notifications Backend Tightening Report

## 1. Executive Summary

This report presents a forensic backend, ownership, and correctness audit of the Notifications subsystem in the Baroness application (`C:\Baroness_Core\Baroness-kt`).

This task is an **audit and investigation only**. Zero Kotlin, TypeScript, SQL, Gradle, resource, or configuration files were modified.

### Key Audit Findings:
1. **In-App Banner Control Bottleneck**: In-app foreground banners (`InAppNotification.kt`) pass through a single, controllable event bus (`NotificationCenter.kt` / `NotificationViewModel.kt`). Adding an app-owned preference (`"in_app_banners_enabled"`) in DataStore / `SettingsViewModel` will cleanly suppress foreground banners without affecting system push notifications, FCM payloads, or alarm/media channels.
2. **Confirmed FRIDAY Proactive Push Routing Bug (`CONFIRMED BUG`)**: Postgres trigger `012_friday_initiative_notification_trigger.sql` invokes Edge Function `notify-trigger`, which hardcodes `channel_id: 'wishlist_notifications'`, `feature_type: 'wishlist'`, `route: 'Wishlist'`, and title `"${senderName} added a new wish"`. When FRIDAY sends a proactive check-in message, the notification displays "added a new wish", lands in the Wishlist notification channel, and deep-links the user to `WishlistScreen` instead of `ChatRoomScreen("friday")`.
3. **Confirmed FCM Token Logout Gap (`CONFIRMED CORRECTNESS GAP`)**: When a user logs out (`ProfileSetupViewModel.onLogout`), DataStore keys `"vibe_persona"`, `"userProfile"`, and `"currentPersonaId"` are removed, but `"fcm_token"` remains in local DataStore and `profiles.fcm_token` in Supabase is NOT set to `NULL`. As a result, push notifications sent to that persona continue delivering to the device after logout.
4. **Hard-Coded Service-Role Credential (`SECURITY ISSUE`)**: Migration `012_friday_initiative_notification_trigger.sql` embeds a hardcoded administrative Supabase `service_role` JWT string (`[REDACTED — SERVICE-ROLE-LIKE TOKEN]`) in the `Authorization` header parameter of `net.http_post`.
5. **Phase 6 Settings UI Boundary**: The application can truthfully expose two settings on `NotificationSettingsPage.kt`:
   - An **In-App Banner Notifications** toggle (App-Controlled, DataStore-backed).
   - An **Android System Notification Settings** shortcut button (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
   Master ON/OFF switches and notification sound/vibration options are owned by Android OS System Notification Channels or Sound & Haptics and must NOT be duplicated.

---

## 2. In-App Banner Preference Investigation

### Current Flow
```text
Event Source (e.g. AlarmReceiver)
        ↓
NotificationCenter.show(NotificationData)
        ↓
NotificationViewModel.showInAppNotification(data)
        ↓
_currentNotification StateFlow emission
        ↓
MainActivity (renders InAppNotification Compose overlay)
```

### Ownership
- **Owner**: `NotificationViewModel.kt` / `NotificationCenter.kt` (App-Controlled).
- **UI Surface**: `InAppNotification.kt` top Compose banner overlay.

### Findings
- **Observed**: All in-app foreground banners enter through `NotificationCenter.show(data)`, which forwards the payload to `NotificationViewModel.showInAppNotification(data)`.
- **Observed**: In-app banners operate completely independently of system notifications posted via `NotificationManager.showSystemNotification()`.
- **Interpretation**: Adding an `inAppBannersEnabled: StateFlow<Boolean>` preference in `SettingsViewModel.kt` backed by DataStore key `"in_app_banners_enabled"` can intercept calls in `NotificationCenter.show()` or `NotificationViewModel.showInAppNotification()`.
- **Impact**: Suppressing in-app banners will NOT suppress system push notifications, FCM messages, or alarm/timer alerts. It affects only the top Compose UI banner overlay.

### Classification
`READY FOR UI`

### Recommendation
Expose an "In-App Banner Notifications" toggle on `NotificationSettingsPage.kt` backed by DataStore key `"in_app_banners_enabled"`.

---

## 3. FRIDAY Proactive Notification Channel Investigation

### Current Channel Flow
```text
FRIDAY Proactive Message Insert (friday_messages where is_proactive = true)
        ↓
Postgres Trigger on_friday_proactive_message (012 migration)
        ↓
HTTP POST to Edge Function notify-trigger
        ↓
notify-trigger/index.ts (FCM HTTP v1 API)
        ↓
FCMService.onMessageReceived()
        ↓
NotificationManager.showSystemNotification()
        ↓
Android System Shade (Posted to CHANNEL_WISHLIST)
```

### Exact Channel IDs Found
- Edge Function `notify-trigger/index.ts` line 101: `channel_id: 'wishlist_notifications'`
- Edge Function `notify-trigger/index.ts` line 92: `route: 'Wishlist'`, `feature_type: 'wishlist'`
- Android `NotificationManager.kt` line 37: Maps `feature_type = "wishlist"` to `CHANNEL_WISHLIST` (`"wishlist_notifications"`).

### Findings
- **Observed**: `012_friday_initiative_notification_trigger.sql` triggers `notify-trigger`, which was written specifically for wishlist inserts.
- **Observed**: `notify-trigger` constructs an FCM message with hardcoded title `"${senderName} added a new wish"`, `route: "Wishlist"`, `feature_type: "wishlist"`, and `channel_id: "wishlist_notifications"`.

### Runtime Consequence
- When FRIDAY sends a proactive check-in message (e.g. "hey mate, how was that test?"), the notification arrives with title `"FRIDAY added a new wish"`, plays the wishlist channel alert, lands under the **Wishlist** Android Notification Channel, and tapping the notification deep-links the user to `WishlistScreen` instead of `ChatRoomScreen("friday")`.

### Classification
`CONFIRMED BUG`

### Recommendation
In a future backend patch, update `notify-trigger` (or create a dedicated `friday-notify-trigger`) to set `route: "Friday"`, `feature_type: "messages"`, `channel_id: "messages_notifications"`, and title `"FRIDAY"`.

---

## 4. FCM Token Logout Lifecycle

### Token Acquisition
- Executed on app launch in `MainApplication.kt` and on token refresh in `FCMService.onNewToken()`.

### Local Persistence
- Saved in DataStore `baroness_prefs` under key `"fcm_token"`.

### Remote Persistence
- Synced to Supabase `public.profiles` table (`fcm_token` column) via `ProfileManager.updateFcmToken(personaId, token)`.

### Logout Flow
- Inspected `ProfileSetupViewModel.onLogout()`:
  ```kotlin
  fun onLogout(callback: () -> Unit) {
      viewModelScope.launch {
          storageManager.remove("vibe_persona")
          storageManager.remove("userProfile")
          storageManager.remove("currentPersonaId")
          callback()
      }
  }
  ```

### Findings
- **Observed**: `onLogout()` removes `"vibe_persona"`, `"userProfile"`, and `"currentPersonaId"` from DataStore.
- **Observed**: `"fcm_token"` is **NOT** removed from local DataStore.
- **Observed**: `profiles.fcm_token` in Supabase is **NOT** cleared (set to `NULL`).

### Consequence
- After a user logs out, their device token remains attached to `profiles.fcm_token` in Supabase. Server-triggered push notifications (wishlist updates or FRIDAY proactive messages) continue delivering to the device after logout.

### Classification
`CONFIRMED CORRECTNESS GAP`

### Recommendation
Update `ProfileSetupViewModel.onLogout()` in a future backend task to execute `ProfileManager.updateFcmToken(personaId, "")` (or set `fcm_token = NULL` in Supabase) and call `storageManager.remove("fcm_token")`.

---

## 5. Hard-Coded Credential Investigation

### Finding
Migration `012_friday_initiative_notification_trigger.sql` line 28 contains a hardcoded Supabase `service_role` JWT string inside the SQL trigger function definition.

### Evidence
```sql
headers := json_build_object(
    'Content-Type', 'application/json',
    'Authorization', 'Bearer [REDACTED — SERVICE-ROLE-LIKE TOKEN]'
)::jsonb,
```

### Runtime Relevance
- When `friday_messages` receives a proactive insert, Postgres executes `net.http_post` using this hardcoded `service_role` JWT to authenticate against `notify-trigger`.

### Security Classification
`SECURITY ISSUE — SEPARATE SECURITY WORK REQUIRED`

### Recommended Separate Follow-Up
- Do NOT address during Settings UI work.
- In a separate database/security task, store the service key in Supabase Vault or retrieve it via database configuration settings (`current_setting('app.settings.service_role_key', true)`).

---

## 6. Notification Ownership Matrix

| Concern | Current Owner | App-Controlled? | Candidate Settings Domain |
| :--- | :--- | :---: | :--- |
| **Android Notification Permission** | Android OS (`POST_NOTIFICATIONS`) | Yes (System Dialog / Intent) | System Settings Link (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`) |
| **Notification Channels** | Android OS (`NotificationChannel`) | No (OS User-Controlled) | System Channel Settings Link (`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`) |
| **System Notification Sound** | Android OS NotificationChannel | No (Android Channel-Controlled) | Sound & Haptics / System Channel Settings |
| **System Notification Vibration** | Android OS NotificationChannel | No (Android Channel-Controlled) | Sound & Haptics / System Channel Settings |
| **Lockscreen Visibility** | Android OS Security Settings | No (Android OS-Controlled) | Privacy & Security / System Settings |
| **FCM Token** | `MainApplication.kt` / `FCMService.kt` | Internal Infrastructure | Profile & Identity / System Registration |
| **In-App Banners** | `NotificationCenter.kt` / `NotificationViewModel.kt` | Yes (App-Controlled) | Notifications Domain (`settings/notifications`) |
| **Wishlist Push Preference** | Server Supabase `notify-trigger` | No (Unconditional Edge Function) | Notifications Domain (Requires `profiles.push_enabled` column) |
| **FRIDAY Proactive Push Preference**| Server Postgres Trigger `012` | No (Unconditional Trigger) | Notifications Domain (Requires `profiles.push_enabled` column) |
| **Alarm Notifications** | `BaronessClockManager` / `AlarmReceiver` | Yes (App-Controlled) | Sound & Haptics / Clock Domain (`settings/sound`) |
| **Media Playback Notification** | `BaronessMediaService` | Yes (Active Playback State) | Sound & Haptics / Media Domain |

---

## 7. Settings Exposure Matrix

What Baroness can truthfully expose on `NotificationSettingsPage.kt`:

| Candidate Setting | Current Status | Classification | Truthful Settings Center Exposure |
| :--- | :--- | :--- | :--- |
| **In-App Banner Notifications** | Implemented (`NotificationCenter`) | `REAL + APP-CONTROLLABLE NOW` | Expose toggle (`in_app_banners_enabled` in DataStore). |
| **System Notification Channels Link** | Native Intent Available | `REAL BUT ANDROID-CONTROLLED` | Expose button launching `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. |
| **Master Notification Switch (ON/OFF)**| Not Implemented | `REAL BUT ANDROID-CONTROLLED` | **DO NOT EXPOSE** (Misleading; OS permissions govern system notifications). |
| **Wishlist Push Toggle** | Not Implemented in DB | `REAL BUT BACKEND SUPPORT REQUIRED` | **DO NOT EXPOSE** (Requires `profiles.push_enabled` DB column first). |
| **FRIDAY Proactive Push Toggle** | Not Implemented in DB | `REAL BUT BACKEND SUPPORT REQUIRED` | **DO NOT EXPOSE** (Requires `profiles.push_enabled` DB column first). |
| **Notification Sound Selector** | Owned by OS Channel | `OWNED BY SOUND & HAPTICS` | **DO NOT EXPOSE** (Managed via Android Channel Settings / Sound & Haptics). |
| **Notification Vibration Pattern** | Owned by OS Channel | `OWNED BY SOUND & HAPTICS` | **DO NOT EXPOSE** (Managed via Android Channel Settings / Sound & Haptics). |

---

## 8. Confirmed Correctness Gaps

1. **FRIDAY Proactive Push Routing Bug**: Trigger `012` sends FRIDAY proactive notifications with `channel_id: 'wishlist_notifications'` and `route: 'Wishlist'`, displaying "added a new wish" and routing users to Wishlist.
2. **FCM Token Cleanup Missing on Logout**: `ProfileSetupViewModel.onLogout()` removes persona state from DataStore, but leaves `"fcm_token"` in DataStore and `profiles.fcm_token` in Supabase intact.

---

## 9. Potential / Unverified Issues

1. **`FCMService.kt` Foreground Decision Branch**: `FCMService.onMessageReceived()` contains `// TODO: Logic to decide between System vs In-App notification` and currently always posts a system notification even when the app is in the foreground.
2. **Non-Silhouette Notification Small Icon**: `NotificationManager.kt` uses `R.drawable.icon` instead of a white vector silhouette icon, which can render as a solid white square on some Android status bars.

---

## 10. Deferred Security or Architecture Work

1. **Hardcoded Service-Role JWT in Postgres Trigger**: Migration `012` contains a hardcoded bearer token string (`[REDACTED — SERVICE-ROLE-LIKE TOKEN]`). Requires a separate database security task to store credentials in Supabase Vault.
2. **Custom Auth / RLS Architecture**: RLS policies on `profiles`, `friday_messages`, `friday_memories`, `chat_sync_pipe`, and `access_keys` allow public/anon access (`024_friday_v1_app_compat_rls.sql`) because the app uses custom passkeys rather than Supabase Auth JWTs (`auth.users`). Migrating to `auth.uid()` RLS requires a separate, full-app authentication architecture project.

---

## 11. Phase 6 Notification UI Boundary

When `NotificationSettingsPage.kt` is implemented in a future UI phase, it should contain **ONLY**:

1. **Header Card**: Title "NOTIFICATIONS", explaining in-app banner overlays and system channel routing.
2. **In-App Banners Card**:
   - Title: "In-App Banner Overlays"
   - Description: "Show animated banner alerts at the top of the screen while using the app"
   - Control: Switch bound to `SettingsViewModel.inAppBannersEnabled` (DataStore key `"in_app_banners_enabled"`, default `true`).
3. **Android System Notification Settings Card**:
   - Title: "Android System Notifications"
   - Description: "Configure notification channels, system sounds, vibration, and lockscreen previews in Android System Settings"
   - Control: Button launching `Settings.ACTION_APP_NOTIFICATION_SETTINGS` Intent.

---

## 12. Final Recommendation

### Summary Table

| Area | Status | UI Impact | Backend Work |
| :--- | :--- | :--- | :--- |
| **In-App Banner Preference** | `READY FOR UI` | Expose toggle on `NotificationSettingsPage` | Add `"in_app_banners_enabled"` key to DataStore / `SettingsViewModel` |
| **Android System Notification Settings** | `READY FOR UI` | Expose shortcut button on `NotificationSettingsPage` | Launch `Settings.ACTION_APP_NOTIFICATION_SETTINGS` Intent |
| **FRIDAY Notification Channel Bug** | `CONFIRMED BUG` | None (Fixes deep-link routing) | Update `notify-trigger` or trigger payload to send `route: 'Friday'` |
| **FCM Token Cleanup on Logout** | `CONFIRMED GAP` | None (Fixes post-logout push) | Clear `fcm_token` in DataStore and set `profiles.fcm_token = NULL` on logout |
| **Hard-Coded Service-Role Token** | `SECURITY ISSUE` | None (Backend Security) | Separate security task to use Supabase Vault |
| **Master Notification Switch** | `NOT RECOMMENDED` | Misleading to users | None |
| **Push Notification Toggles** | `BACKEND SUPPORT REQUIRED` | Defer until DB column added | Add `push_notifications_enabled` column to `public.profiles` |

---

NOTIFICATIONS BACKEND TIGHTENING AUDIT: COMPLETE — AWAITING REVIEW
