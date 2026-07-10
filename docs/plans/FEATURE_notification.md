Here's your **Feature Plan for Notifications** – written directly to the agent, with manual steps clearly separated.

---

```markdown
# Feature: Unified Notifications (Push + In-App)

## Overview
A reusable notification system that delivers push notifications (via FCM) when the app is closed or in the background, and in‑app notifications (Snackbar/Banner) when the user is inside the app. Supports rich content (avatar, name, preview, action) and works across multiple features (Wishlist, Messages, and future features). Fire‑and‑forget – no notification history is stored.

## User Stories
- As a user, I want to receive a push notification when the other user interacts with a feature (e.g., casts a wish, sends a message), even if my app is closed or in the background.
- As a user, I want to see a subtle in‑app notification (Snackbar/Banner) when I'm in the app but not on the relevant screen, so I can quickly check the new activity.
- As a user, I want the notification to show the sender's avatar and name, and a preview of the content (e.g., wish text, message preview).
- As a user, tapping the notification should open the app and navigate directly to the relevant screen.
- As a developer, I want a single notification API that any feature can use, so we don't duplicate code.

## Screen Layout / Design
- **Push notification (system):** Android standard notification with:
    - Large icon: sender's avatar (if available)
    - Title: `[Sender] added a new wish` / `[Sender] sent a message`
    - Body: preview of the content (wish text, message text)
    - Action button: "View" → navigates to the relevant screen
    - Lock screen visibility: public (shows content)
    - Channel ID: `wishlist_notifications` for wishlist, `messages_notifications` for messages
- **In‑app notification (Snackbar/Banner):** A modern bottom‑sheet or top‑banner card that appears at the bottom or top of the screen:
    - Left: sender's avatar (circle, 40dp)
    - Middle: title + body preview (2 lines max)
    - Right: dismiss icon (close button)
    - Tap anywhere on the card → navigates to the relevant screen

## Interactions & Behavior
- **Tap push notification:** Opens app → navigates to the relevant screen (e.g., Wishlist, Messages).
- **Tap in‑app notification:** Navigates to the relevant screen.
- **Dismiss in‑app notification:** Closes the card (no action).
- **App is in foreground (on any screen):** In‑app notification appears automatically when a new event is received via realtime subscription.
- **App is in background or closed:** Push notification is delivered via FCM.

## Data Requirements
- What data to display:
    - Sender name
    - Sender avatar URL
    - Title (e.g., `New wish from Baroness`)
    - Body (e.g., `"Visit Bali" 🏝️`)
    - Feature type (e.g., `wishlist`, `messages`)
    - Navigation route (e.g., `wishlist`, `messages`)
- Data source:
    - Remote: Supabase Edge Function triggers FCM push.
    - Local: Realtime subscription provides event data for in‑app notifications.
- Realtime updates needed: Yes – for in‑app notifications when app is in foreground.
- Offline support: Not applicable – notifications are fire‑and‑forget.
- Sync strategy: Push notifications are sent via FCM; in‑app notifications are triggered by realtime events.

## Supabase Database Schema

### New Column (Manual migration required)
```sql
-- Run this in Supabase SQL Editor
ALTER TABLE profiles ADD COLUMN fcm_token TEXT;
```

### Edge Function
- **Name:** `notify-trigger`
- **Purpose:** On `INSERT` to `wishlist_items` (or later, `messages`), fetch the recipient's FCM token and send a push notification via FCM.
- **File:** `supabase/functions/notify-trigger/index.ts` (create this file)

### Enable Realtime
- Ensure these tables are added to the realtime publication:
```sql
ALTER PUBLICATION supabase_realtime ADD TABLE wishlist_items;
ALTER PUBLICATION supabase_realtime ADD TABLE messages;
```

### FCM Setup (Manual)
- Firebase project with FCM enabled.
- FCM server key stored in Supabase secrets: `FCM_SERVER_KEY`.
- Android app has `google-services.json` and FCM configured.

## Files to Create / Modify

### New Folders (Create manually)
- `app/src/main/java/com/baroness/app/components/notification/`
- `app/src/main/java/com/baroness/app/services/`
- `app/src/main/java/com/baroness/app/workers/`

### New Files (Agent will create)
| File | Full Path | Purpose |
|------|-----------|---------|
| `NotificationData.kt` | `app/src/main/java/com/baroness/app/data/models/NotificationData.kt` | Data model for a notification (title, body, avatar, feature, route) |
| `NotificationManager.kt` | `app/src/main/java/com/baroness/app/utils/NotificationManager.kt` | Central API for sending push notifications, creating channels, building intents |
| `NotificationViewModel.kt` | `app/src/main/java/com/baroness/app/viewmodels/NotificationViewModel.kt` | Manages in‑app notification queue and state (StateFlow) |
| `InAppNotification.kt` | `app/src/main/java/com/baroness/app/components/notification/InAppNotification.kt` | Composable UI for in‑app notification card (avatar, title, body, dismiss) |
| `FCMService.kt` | `app/src/main/java/com/baroness/app/services/FCMService.kt` | FirebaseMessagingService implementation – handles incoming push messages and triggers in‑app notification if app is foreground |
| `NotificationWorker.kt` | `app/src/main/java/com/baroness/app/workers/NotificationWorker.kt` | (Optional) Background worker to process notifications when FCM data payload arrives |

### Modified Files (Agent will modify)
| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add FCM service, notification channel meta‑data |
| `MainActivity.kt` | Handle deep‑link from push notification, navigate to the correct screen based on `route` extra |
| `WishlistRepository.kt` | In `subscribeToRealtime()`, when a new wish is inserted, call `NotificationManager.showInAppNotification()` |
| `app/build.gradle.kts` | Add FCM dependency: `implementation("com.google.firebase:firebase-messaging-ktx:24.0.0")` |
| `Application.kt` (or `MainApplication`) | Initialize notification channels and register FCM token |

## Architecture Constraints
- Use `StateFlow` for notification state in `NotificationViewModel`.
- Collect with `collectAsStateWithLifecycle()` in UI.
- No business logic in Composables – all logic in ViewModel/Manager.
- Reuse existing `AsyncImage` for avatar loading.
- Keep notification data model independent of any specific feature (generic).
- In‑app notification must be a composable that can be placed anywhere (e.g., in a `Box` at the bottom of the screen).
- Follow existing patterns from `ARCHITECTURE.md` and `AGENT_RULES.md`.
- Preserve offline‑first behavior – notifications are fire‑and‑forget and do not affect local data.

## Do NOT Modify
- `ui/theme/` – no theme changes.
- Existing Room entities – only add `fcm_token` to Supabase `profiles` table (not to Room).
- Existing realtime subscription logic – only extend with notification callback.
- `SyncManager` or `SyncQueue` – unrelated to this feature.
- Hardcoded colors – leave as they are.

## Open Questions
- None – design is finalised.

## Rules
- Write the spec file following the exact structure in `docs/SPEC_TEMPLATE.md` (if exists, otherwise produce clean spec).
- Be specific — provide exact file paths and code skeletons where needed.
- Reference existing features when similar (e.g., "Follow Wishlist pattern for realtime subscription").
- Include error handling and edge cases (FCM token missing, network failure).
- Think about offline‑first – notifications are fire‑and‑forget, no offline queue needed.
- Consider performance – avoid spamming notifications (debounce if needed).
- Do NOT implement any changes. Wait for human approval before proceeding.
- Write the plan to `docs/plans/FEATURE_Notifications.md`.

## Input
Implementation of a unified notification system (push + in‑app) for the Baroness app, reusable across features (Wishlist, Messages, etc.).
```