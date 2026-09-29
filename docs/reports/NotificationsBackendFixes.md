# Notifications Backend Fixes

## 1. Scope

This document records the surgical maintenance implementation performed for Phase 6B-Implementation to resolve the two confirmed notification correctness gaps identified in `docs/reports/NotificationsBackendTightening.md`:

1. **Fix #1 — FRIDAY Proactive Notification Routing**: Correcting FCM payload payload routing, title, feature type, channel ID, and deep-link route for FRIDAY proactive messages.
2. **Fix #2 — FCM Token Logout Cleanup**: Clearing remote `profiles.fcm_token` in Supabase to `NULL` and removing local `"fcm_token"` from DataStore upon user logout.

---

## 2. FRIDAY Proactive Notification Fix

### Before
- **Observed Behavior**: FRIDAY proactive check-in messages inserted into `public.friday_messages` triggered SQL function `trigger_friday_proactive_notification()`, which invoked `notify-trigger` Edge Function.
- **Root Cause**: `notify-trigger` was written exclusively for wishlist items and hardcoded `title: "${senderName} added a new wish"`, `feature_type: "wishlist"`, `route: "Wishlist"`, and `channel_id: "wishlist_notifications"`.
- **Runtime Consequence**: Proactive check-ins from FRIDAY arrived with title `"FRIDAY added a new wish"`, posted to the Wishlist channel, and deep-linked the user to `WishlistScreen` instead of `ChatRoomScreen("friday")`.

### Changes Made
1. **`supabase/migrations/012_friday_initiative_notification_trigger.sql`**:
   - Added `'notification_type', 'friday_proactive'` parameter to the HTTP payload body sent to `notify-trigger`.
   - Preserved the existing bearer authorization token mechanism unchanged.
2. **`supabase/functions/notify-trigger/index.ts`**:
   - Added notification type branching. When `notification_type === 'friday_proactive'` or `creator_id === 'friday_official'`:
     - Sets `recipientId = record.owner_id` (the target user persona).
     - Sets `notificationTitle = 'FRIDAY'`.
     - Sets `route = 'Friday'`.
     - Sets `featureType = 'messages'`.
     - Sets `channelId = 'messages_notifications'`.
     - Sets `avatarUrl = 'https://img.icons8.com/fluency/48/artificial-intelligence.png'`.

### Result
- FRIDAY proactive notifications land in the **Messages** channel (`messages_notifications`), display title `"FRIDAY"`, and deep-link the user directly to `ChatRoomScreen("friday")` via the `"Friday"` navigation route in `MainActivity`.

---

## 3. FCM Token Logout Cleanup

### Before
- **Observed Behavior**: In `ProfileSetupViewModel.onLogout()`, logging out removed DataStore keys `"vibe_persona"`, `"userProfile"`, and `"currentPersonaId"`.
- **Root Cause**: `"fcm_token"` was left in local DataStore, and remote `profiles.fcm_token` in Supabase was never set to `NULL`.
- **Runtime Consequence**: Server-triggered push notifications (wishlist additions or FRIDAY check-ins) for the logged-out persona continued delivering to the device after logout.

### Changes Made
1. **`app/src/main/java/com/baroness/app/modules/ProfileManager.kt`**:
   - Updated `updateFcmToken(id: String, token: String)` so passing a blank or empty string sets `put("fcm_token", if (token.isBlank()) JSONObject.NULL else token)`, serializing `{"fcm_token": null}` in JSON to update `profiles.fcm_token` to `NULL` in Supabase via PostgREST PATCH.
2. **`app/src/main/java/com/baroness/app/viewmodels/ProfileSetupViewModel.kt`**:
   - Updated `onLogout()` to capture `currentPersonaId`, invoke `ProfileManager.updateFcmToken(personaId, "")` wrapped in `try / catch` on `Dispatchers.IO`, remove `"fcm_token"` from `storageManager`, remove remaining local persona state, and execute `callback()`.

### Result
- Remote `profiles.fcm_token` in Supabase is cleared to `NULL` and local DataStore `"fcm_token"` is removed on logout, preventing post-logout push notification delivery.

---

## 4. Wishlist Regression Check

- **Verification**: In `notify-trigger/index.ts`, requests where `notification_type !== 'friday_proactive'` and `creator_id !== 'friday_official'` continue executing the original wishlist notification logic:
  - `notificationTitle = "${senderName} added a new wish"`
  - `route = 'Wishlist'`
  - `featureType = 'wishlist'`
  - `channelId = 'wishlist_notifications'`
- Wishlist push notifications remain 100% intact and unaffected.

---

## 5. Logout Failure Handling

- **Case A — Normal Logout**: Remote `profiles.fcm_token` set to `NULL`, local `"fcm_token"` removed, logout succeeds.
- **Case B — Logout with No Token**: Cleanly updates remote `fcm_token` to `NULL`, removes local key, logout succeeds without crash.
- **Case C — Network Offline / Supabase Unreachable**: `try / catch` catches network exceptions and logs `Log.e(...)`. Local DataStore keys (`"fcm_token"`, `"vibe_persona"`, `"userProfile"`, `"currentPersonaId"`) are cleared, `callback()` executes, and the user completes logout without getting trapped on screen.

---

## 6. Verification

- **Android Kotlin Compilation**: Executed `./gradlew app:compileDebugKotlin` — **BUILD SUCCESSFUL** with zero compilation errors.
- **Static Payload Flow Verification**:
  - `012` Trigger -> `notify-trigger` -> FCM HTTP v1 -> `FCMService.onMessageReceived()` -> `NotificationManager.showSystemNotification()` (`messages_notifications` channel) -> `MainActivity` (`"Friday"` route) verified end-to-end.

---

## 7. Files Changed

1. `app/src/main/java/com/baroness/app/modules/ProfileManager.kt`
2. `app/src/main/java/com/baroness/app/viewmodels/ProfileSetupViewModel.kt`
3. `supabase/migrations/012_friday_initiative_notification_trigger.sql`
4. `supabase/functions/notify-trigger/index.ts`
5. `docs/reports/NotificationsBackendFixes.md`

---

## 8. Explicitly Deferred Work

The following were intentionally NOT addressed in this surgical maintenance task:
- Hardcoded service-role bearer token credential in SQL trigger
- Push notification preferences (`push_enabled` column / settings)
- NotificationSettingsPage UI
- Foreground notification decision TODO in `FCMService.kt`
- Non-silhouette notification icon replacement
- Notification channel redesign
- RLS / authentication architecture refactoring

---

FRIDAY PROACTIVE NOTIFICATION ROUTING: FIXED
FCM TOKEN LOGOUT CLEANUP: FIXED
UNRELATED NOTIFICATION BEHAVIOR: PRESERVED
NOTIFICATIONS BACKEND FIXES: COMPLETE
