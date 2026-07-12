# Notification System Audit Report

**Date**: 2026-07-10  
**Time**: 21:45:00 UTC  
**Feature**: Android Notification System  
**Reviewer**: Cascade Agent

---

## 1. Architecture Overview

### Complete Notification Flow

**Step-by-Step Sequence:**

1. **Database Trigger** (Supabase PostgreSQL)
   - User creates a new wish in `wishlist_items` table
   - PostgreSQL trigger `on_wish_inserted_push` fires (defined in `push_notifications.sql:35-38`)
   - Trigger calls `handle_new_wish_push_notification()` function

2. **Edge Function Invocation** (Supabase Edge Functions)
   - Trigger uses `pg_net.http_post()` to call Edge Function asynchronously
   - URL: `https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/notify-trigger`
   - Payload: `{ "event": "INSERT", "record": <wish_data> }`

3. **Edge Function Processing** (`supabase/functions/notify-trigger/index.ts`)
   - Receives trigger payload
   - Determines recipient (the other persona: phesty_official ↔ baroness_official)
   - Fetches recipient's FCM token from `profiles.fcm_token`
   - Fetches sender's display name and avatar URL
   - Generates OAuth2 access token for FCM using service account credentials
   - Constructs FCM v1 API payload

4. **Firebase Cloud Messaging** (Google FCM)
   - Edge Function sends POST to `https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send`
   - FCM routes notification to the target device using the FCM token
   - FCM delivers to Android device

5. **Android FCM Service** (`FCMService.kt`)
   - `FirebaseMessagingService.onMessageReceived()` is called
   - Parses incoming RemoteMessage
   - Extracts title, body, avatar_url, feature_type, route from data payload
   - Calls `NotificationManager.showSystemNotification()`

6. **Notification Display** (`NotificationManager.kt`)
   - Determines appropriate notification channel based on `feature_type`
   - Creates `NotificationCompat.Builder` with basic configuration
   - Sets PendingIntent to MainActivity with route parameter
   - Displays system notification via `NotificationManager.notify()`

7. **User Interaction**
   - User taps notification
   - PendingIntent launches MainActivity with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`
   - MainActivity navigates to the specified route (e.g., "Wishlist")
   - Notification is auto-cancelled

**Alternative Flow (In-App Notifications):**
- When app is in foreground, Supabase Realtime subscriptions detect changes
- `WishlistRepository.handleWishChange()` processes realtime events
- Repository calls `NotificationViewModel.showInAppNotification()`
- `InAppNotification` Composable displays overlay in UI
- No system notification is shown (foreground-only experience)

---

## 2. Firebase Configuration

### Firebase Messaging Configuration

**File**: `app/build.gradle.kts:105-106`
```kotlin
implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
implementation("com.google.firebase:firebase-messaging-ktx")
```

**File**: `app/google-services.json` (present)
- Contains Firebase project configuration
- Includes project ID, API keys, and other Firebase settings

### FCM Token Registration

**Initial Registration** (`MainApplication.kt:29-53`):
```kotlin
private fun registerFcmToken() {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        val token = task.result
        storageManager.saveString("fcm_token", token)
        val personaId = storageManager.getString("currentPersonaId")
        if (!personaId.isNullOrBlank()) {
            ProfileManager.updateFcmToken(personaId, token)
        }
    }
}
```

**Token Refresh Handling** (`FCMService.kt:26-46`):
```kotlin
override fun onNewToken(token: String) {
    storageManager.saveString("fcm_token", token)
    val personaId = storageManager.getString("currentPersonaId")
    if (!personaId.isNullOrBlank()) {
        ProfileManager.updateFcmToken(personaId, token)
    }
}
```

### Token Storage in Supabase

**Location**: `profiles.fcm_token` column (nullable text)

**Update Method** (`ProfileManager.kt:24-54`):
```kotlin
suspend fun updateFcmToken(id: String, token: String) {
    val jsonBody = JSONObject().apply {
        put("fcm_token", token)
        put("updated_at", <ISO timestamp>)
    }.toString()
    
    val url = "$SUPABASE_URL/rest/v1/profiles?id=eq.$id"
    // PATCH request to update profile
}
```

### Payload Type

**Current Implementation**: **Data Payload Only**

The Edge Function sends a data payload with notification fields:
```json
{
  "message": {
    "token": "<fcm_token>",
    "notification": {
      "title": "Sender added a new wish",
      "body": "Wish text"
    },
    "data": {
      "route": "Wishlist",
      "feature_type": "wishlist",
      "avatar_url": "<url>"
    }
  }
}
```

**Note**: The payload includes both `notification` (for system display) and `data` (for app processing). This is a hybrid approach.

### Notification Channels

**Created in**: `NotificationManager.kt:24-33`

**Channels**:
1. `wishlist_notifications` - Importance: DEFAULT
2. `messages_notifications` - Importance: HIGH
3. `general_notifications` - Importance: LOW

---

## 3. FirebaseMessagingService

### Implementation Details

**File Path**: `app/src/main/java/com/baroness/app/services/FCMService.kt`

**Class Name**: `FCMService`

**Parent Class**: `FirebaseMessagingService`

**Lifecycle**:
- Created when app receives FCM message
- `onCreate()` initializes NotificationManager
- `onMessageReceived()` processes incoming messages
- `onNewToken()` handles token refresh

### Important Methods

**onCreate()** (Line 21-24):
```kotlin
override fun onCreate() {
    super.onCreate()
    notificationManager = NotificationManager(applicationContext)
}
```

**onNewToken()** (Line 26-46):
- Logs new token
- Saves to local storage (StorageManager)
- Syncs to Supabase if persona is set
- Calls ProfileManager.updateFcmToken()

**onMessageReceived()** (Line 48-67):
- Logs message source
- Extracts data from RemoteMessage
- Falls back to notification.title/body if data is missing
- Constructs NotificationData object
- Calls NotificationManager.showSystemNotification()

### Payload Processing

**Extraction Logic** (Line 52-59):
```kotlin
val data = message.data
val notificationData = NotificationData(
    title = data["title"] ?: message.notification?.title ?: "New Notification",
    body = data["body"] ?: message.notification?.body ?: "",
    avatarUrl = data["avatar_url"],
    featureType = data["feature_type"],
    route = data["route"]
)
```

**Priority**: Data payload > Notification payload > Default

### Helper Classes

**NotificationManager** - Handles channel creation and notification building
**ProfileManager** - Handles FCM token sync to Supabase
**StorageManager** - Local token persistence

### NotificationCompat.Builder Usage

**Direct Usage**: YES

The service delegates to `NotificationManager.showSystemNotification()` which uses `NotificationCompat.Builder` directly.

---

## 4. Notification Builder Analysis

### Builder Instance #1

**File**: `app/src/main/java/com/baroness/app/utils/NotificationManager.kt`

**Function**: `showSystemNotification()` (Line 35-61)

**Configuration Summary**:

| Property | Value | Notes |
|----------|-------|-------|
| **Channel** | Dynamic (wishlist/messages/general) | Based on feature_type |
| **Small Icon** | `R.mipmap.ic_launcher` | TODO comment: Use proper silhouette icon |
| **Large Icon** | Not set | Missing |
| **Priority** | `PRIORITY_DEFAULT` | Static |
| **Importance** | Inherited from channel | DEFAULT/HIGH/LOW |
| **Sound** | Default (channel default) | Not customized |
| **Color** | Not set | Missing |
| **Visibility** | Not set | Default (private) |
| **Category** | Not set | Missing |
| **AutoCancel** | `true` | Yes |
| **PendingIntent** | MainActivity with route | FLAG_ACTIVITY_NEW_TASK \| CLEAR_TASK |
| **Actions** | None | Missing |
| **Style** | None (basic) | No MessagingStyle/BigTextStyle |
| **Grouping** | None | Missing |
| **Shortcut ID** | None | Missing |
| **Bubble Support** | None | Missing |
| **Person API** | None | Missing |
| **RemoteInput** | None | Missing |
| **FullScreenIntent** | None | Missing |

**Builder Code**:
```kotlin
val builder = NotificationCompat.Builder(context, channelId)
    .setSmallIcon(R.mipmap.ic_launcher)
    .setContentTitle(data.title)
    .setContentText(data.body)
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setContentIntent(pendingIntent)
    .setAutoCancel(true)
```

**Gap Analysis**: This is a minimal implementation with no advanced features. Missing:
- Large icon (avatar)
- Custom color
- Actions (reply, mark read)
- MessagingStyle for conversations
- Grouping
- Bubbles
- Person API
- RemoteInput for direct reply

---

## 5. Notification Channels

### Channel #1: wishlist_notifications

**Channel ID**: `wishlist_notifications`

**Name**: "Wishlist"

**Importance**: `IMPORTANCE_DEFAULT`

**Description**: Not set (missing)

**Sound**: Default (not customized)

**Vibration**: Default (not customized)

**Lights**: Default (not customized)

**Lock Screen Visibility**: Default (not customized)

**Location**: `NotificationManager.kt:27`

---

### Channel #2: messages_notifications

**Channel ID**: `messages_notifications`

**Name**: "Messages"

**Importance**: `IMPORTANCE_HIGH`

**Description**: Not set (missing)

**Sound**: Default (not customized)

**Vibration**: Default (not customized)

**Lights**: Default (not customized)

**Lock Screen Visibility**: Default (not customized)

**Location**: `NotificationManager.kt:28`

---

### Channel #3: general_notifications

**Channel ID**: `general_notifications`

**Name**: "General"

**Importance**: `IMPORTANCE_LOW`

**Description**: Not set (missing)

**Sound**: Default (not customized)

**Vibration**: Default (not customized)

**Lights**: Default (not customized)

**Lock Screen Visibility**: Default (not customized)

**Location**: `NotificationManager.kt:29`

---

## 6. Notification Types

### Currently Implemented

#### 1. Wishlist Notifications

**Trigger**: New wish inserted into `wishlist_items` table

**Flow**:
- Database trigger → Edge Function → FCM → FCMService → NotificationManager
- Also: Supabase Realtime → WishlistRepository → NotificationViewModel → InAppNotification

**Payload**:
```json
{
  "title": "Sender added a new wish",
  "body": "Wish text content",
  "avatar_url": "https://...",
  "feature_type": "wishlist",
  "route": "Wishlist"
}
```

**Display**: System notification (background) or in-app overlay (foreground)

**Channel**: `wishlist_notifications`

---

### Not Yet Implemented (Planned)

#### 2. Chat Messages

**Status**: Channel exists but no trigger/Edge Function

**Expected Flow**: Similar to wishlist but for `messages` table

**Channel**: `messages_notifications`

**Gap**: No database trigger, no Edge Function for messages

---

#### 3. Friend Requests

**Status**: Not implemented

**Channel**: Would use `general_notifications`

**Gap**: No friend request system exists

---

#### 4. System Alerts

**Status**: Not implemented

**Channel**: `general_notifications`

**Gap**: No system alert triggers defined

---

#### 5. FRIDAY Notifications

**Status**: Not implemented

**Channel**: Would need new channel or use `general_notifications`

**Gap**: No integration with FRIDAY AI companion

---

#### 6. Photos

**Status**: Not implemented

**Channel**: Would need new channel

**Gap**: No photo upload notifications

---

#### 7. Reminders

**Status**: Not implemented

**Channel**: Would need new channel

**Gap**: No reminder system

---

#### 8. Updates

**Status**: Not implemented

**Channel**: `general_notifications`

**Gap**: No app update notifications

---

## 7. Current UI Capabilities

### Capability Assessment

| Feature | Status | Explanation |
|---------|--------|-------------|
| **MessagingStyle** | NO | Not implemented. Builder uses basic style only. |
| **BigTextStyle** | NO | Not implemented. No long text expansion. |
| **BigPictureStyle** | NO | Not implemented. No image support in notifications. |
| **InboxStyle** | NO | Not implemented. No multi-line summary. |
| **MediaStyle** | NO | Not implemented. No media controls. |
| **Progress notifications** | NO | Not implemented. No progress indicators. |
| **Custom RemoteViews** | NO | Not implemented. Uses standard builder only. |
| **Reply actions** | NO | Not implemented. No RemoteInput for direct reply. |
| **Mark Read actions** | NO | Not implemented. No action buttons. |
| **Notification grouping** | NO | Not implemented. No groupKey or setGroup(). |
| **Conversation notifications** | NO | Not implemented. No MessagingStyle or Person API. |
| **Dynamic shortcuts** | NO | Not implemented. No shortcut manager integration. |
| **Bubble conversations** | NO | Not implemented. No bubble metadata. |
| **Person API** | NO | Not implemented. No Person objects for avatars. |
| **Notification badges** | NO | Not implemented. No badge count support. |

**Summary**: The current implementation is at a **basic level** with minimal features. Only basic title/body notifications are supported.

---

## 8. Android Manifest Review

### Notification Permissions

**File**: `app/src/main/AndroidManifest.xml`

**POST_NOTIFICATIONS Permission** (Line 27-28):
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**Status**: ✅ Declared for Android 13+

**Runtime Permission Handling**: Not checked in code. The app assumes permission is granted.

---

### Firebase Services

**FCMService Declaration** (Line 75-81):
```xml
<service
    android:name=".services.FCMService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

**Status**: ✅ Properly declared with intent filter

**Exported**: `false` (correct for security)

---

### BroadcastReceivers

**Status**: None declared

No broadcast receivers for notification-related events.

---

### Intent Filters

**FCM Intent Filter** (Line 78-80):
```xml
<intent-filter>
    <action android:name="com.google.firebase.MESSAGING_EVENT" />
</intent-filter>
```

**Status**: ✅ Correct for FCM

---

### Exported Components

**FCMService**: `exported="false"` ✅ (secure)

**MainActivity**: `exported="true"` ✅ (required for launcher)

---

### Other Relevant Permissions

**Network Permissions** (Line 6-7):
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

**Status**: ✅ Required for FCM and Supabase

**Vibrate Permission** (Line 34):
```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

**Status**: ✅ Available but not used in notification channels

---

## 9. Edge Function Payload

### Payload Structure

**Source**: `supabase/functions/notify-trigger/index.ts:86-107`

### Input Payload (from Database Trigger)

```json
{
  "event": "INSERT",
  "record": {
    "id": 123,
    "text": "Sample wish text",
    "wish_date": "2026-07-10",
    "status": "planning",
    "creator_id": "phesty_official",
    "created_at": "2026-07-10T18:00:00.000Z",
    "updated_at": "2026-07-10T18:00:00.000Z"
  }
}
```

**Required Fields**:
- `event`: String ("INSERT", "UPDATE", "DELETE")
- `record`: Object (database row data)

**Optional Fields**: None (both required by trigger)

---

### FCM Output Payload (to Firebase)

```json
{
  "message": {
    "token": "recipient_fcm_token_here",
    "notification": {
      "title": "Phesty added a new wish",
      "body": "Sample wish text"
    },
    "data": {
      "route": "Wishlist",
      "feature_type": "wishlist",
      "avatar_url": "https://supabase.co/storage/v1/..."
    },
    "android": {
      "priority": "high",
      "notification": {
        "channel_id": "wishlist_notifications",
        "icon": "ic_notification",
        "color": "#6200EE"
      }
    }
  }
}
```

**Required Fields**:
- `message.token`: Recipient's FCM token
- `message.notification.title`: Notification title
- `message.notification.body`: Notification body

**Optional Fields**:
- `message.data.*`: Custom data for app processing
- `message.android.*`: Android-specific configuration
- `message.android.notification.channel_id`: Notification channel
- `message.android.notification.icon`: Small icon resource name
- `message.android.notification.color`: Accent color (ARGB hex)

---

### Example Complete Payload

**Scenario**: Phesty creates a wish, Baroness receives notification

```json
{
  "message": {
    "token": "d7X8Y9Z0aB1cD2eF3gH4iJ5kL6mN7oP8",
    "notification": {
      "title": "Phesty added a new wish",
      "body": "Let's go to Paris next summer!"
    },
    "data": {
      "route": "Wishlist",
      "feature_type": "wishlist",
      "avatar_url": "https://wckluymkbqxdmipzaiff.supabase.co/storage/v1/object/public/avatars/avatar_1720646400000.jpg"
    },
    "android": {
      "priority": "high",
      "notification": {
        "channel_id": "wishlist_notifications",
        "icon": "ic_notification",
        "color": "#6200EE"
      }
    }
  }
}
```

---

## 10. Navigation Behaviour

### PendingIntent Configuration

**File**: `NotificationManager.kt:42-50`

```kotlin
val intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    putExtra("route", data.route)
}

val pendingIntent = PendingIntent.getActivity(
    context, 0, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

### Navigation Flow

1. **User Taps Notification**
   - System notification is dismissed (autoCancel=true)
   - PendingIntent is triggered

2. **Intent Flags**
   - `FLAG_ACTIVITY_NEW_TASK`: Starts MainActivity as a new task
   - `FLAG_ACTIVITY_CLEAR_TASK`: Clears all existing activities in the task
   - **Result**: Fresh start of the app

3. **Route Parameter**
   - `route` extra is passed to MainActivity
   - Example: `"Wishlist"`, `"Messages"`, etc.

4. **MainActivity Handling**
   - `MainActivity.kt:84-88` processes the route
   ```kotlin
   LaunchedEffect(intent) {
       intent?.getStringExtra("route")?.let { route ->
           navController.navigate(route)
       }
   }
   ```

5. **Navigation**
   - NavController navigates to the specified route
   - User lands on the target screen

### Deep Linking

**Status**: ✅ Implemented

**Method**: Intent extra (not true deep linking with URI schemes)

**Limitations**:
- No URI scheme support (e.g., `baroness://wishlist/123`)
- No web deep linking support
- Route must match exact NavHost destination

### Back Stack Handling

**Current Behaviour**: `FLAG_ACTIVITY_CLEAR_TASK` clears entire back stack

**Result**: User cannot press back to return to previous app state

**Alternative**: Could use `FLAG_ACTIVITY_CLEAR_TOP` to preserve some back stack

### Existing Task Flags

**Current**: `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`

**Effect**: Always starts fresh, no task reuse

---

## 11. Current Limitations

### Architectural Limitations

1. **No Conversation-Level Notifications**
   - Each notification is independent
   - No grouping of messages from same sender
   - No conversation summary

2. **No Person API Integration**
   - Avatars not displayed in notifications
   - No sender identity in notification shade
   - Missing visual context

3. **No MessagingStyle**
   - Cannot show message history
   - No conversation thread view
   - Basic title/body only

4. **No Action Buttons**
   - No reply action
   - No mark as read action
   - No delete action
   - No archive action

5. **No RemoteInput**
   - Cannot reply directly from notification shade
   - Must open app to respond

6. **No Notification Grouping**
   - All notifications are independent
   - No "3 new messages" summary
   - Notification shade clutter

7. **No Bubble Support**
   - Cannot show conversations as bubbles
   - No floating chat heads
   - Missing modern Android feature

8. **No Dynamic Shortcuts**
   - No shortcut to recent conversations
   - No long-press launcher shortcuts
   - Missing quick access

9. **No Badge Count**
   - No unread count on launcher icon
   - No visual indicator of pending messages

10. **No MediaStyle**
    - No media controls in notifications
    - Cannot control audio/video from shade

### Android Limitations

1. **POST_NOTIFICATIONS Runtime Permission**
   - Required for Android 13+
   - Not currently requested at runtime
   - May fail silently on Android 13+

2. **Notification Channel Limits**
   - Cannot modify channel importance after creation
   - User can disable channels
   - No programmatic override of user settings

3. **Doze Mode**
   - Notifications may be delayed in Doze
   - No high-priority channel for urgent messages
   - Missing heads-up notification support

4. **Background Restrictions**
   - FCM may be throttled in background
   - No reliable delivery guarantee
   - Missing foreground service for critical sync

### Implementation Gaps

1. **Missing Message Notifications**
   - Edge Function only handles wishlist
   - No trigger on `messages` table
   - No notification for chat messages

2. **No Notification Preferences**
   - User cannot customize notification types
   - No per-channel settings UI
   - No mute options

3. **No Notification History**
   - No persistence of dismissed notifications
   - Cannot view past notifications
   - No notification log

4. **No Rich Media**
   - No image previews in notifications
   - No BigPictureStyle for photos
   - No media attachments

5. **No Progress Indicators**
   - No upload/download progress
   - No sync status notifications
   - No ongoing operation indicators

6. **No Local Notifications**
   - All notifications are remote (FCM)
   - No local alarm/scheduler
   - No reminder notifications

7. **No Notification Categories**
   - No category for calls, messages, etc.
   - Missing system integration
   - No Doze exemption for critical categories

8. **No Sound/Vibration Customization**
   - Default sounds only
   - No custom vibration patterns
   - No per-channel sound configuration

9. **No Lock Screen Customization**
   - Default lock screen visibility
   - No sensitive content hiding
   - No public/private versions

10. **No Notification Timeout**
    - No auto-dismiss timer
    - No timeout configuration
    - Notifications persist until dismissed

---

## 12. Improvement Opportunities

### High Impact (Priority 1)

#### 1. Implement MessagingStyle for Conversations

**Impact**: Transform basic notifications into conversation-style notifications

**Benefits**:
- Show message history (last 3-5 messages)
- Display sender avatar via Person API
- Group messages by conversation
- Modern, familiar UX (like WhatsApp/Messages)

**Implementation**:
- Add `Person` objects for each user
- Use `NotificationCompat.MessagingStyle`
- Pass message history in payload
- Update Edge Function to include conversation context

**Effort**: 2-3 days

---

#### 2. Add Notification Grouping

**Impact**: Reduce notification shade clutter

**Benefits**:
- Group messages by sender/conversation
- Show "3 new messages" summary
- Expandable/collapsible groups
- Cleaner notification shade

**Implementation**:
- Add `setGroup()` to builder
- Use `setGroupSummary()` for summary notification
- Generate consistent group keys (e.g., `conversation_{sender_id}`)
- Handle group expand/collapse

**Effort**: 1-2 days

---

#### 3. Implement Person API for Avatars

**Impact**: Visual context and modern notification appearance

**Benefits**:
- Show sender avatars in notification shade
- Better visual identity
- Matches modern messaging apps
- Required for MessagingStyle

**Implementation**:
- Create `Person` objects with icon URI
- Add to MessagingStyle
- Cache avatar images locally
- Handle missing avatars gracefully

**Effort**: 1-2 days

---

#### 4. Add Direct Reply (RemoteInput)

**Impact**: Reply without opening app

**Benefits**:
- Quick response from notification shade
- Reduced friction
- Matches WhatsApp/Telegram UX
- Higher engagement

**Implementation**:
- Add `RemoteInput` to notification action
- Create reply PendingIntent
- Handle reply in FCMService or BroadcastReceiver
- Sync reply to Supabase
- Update conversation in UI

**Effort**: 2-3 days

---

#### 5. Implement Action Buttons

**Impact**: Quick actions without opening app

**Benefits**:
- Mark as read, archive, delete from shade
- Quick actions for common tasks
- Better UX efficiency
- Reduced app opens

**Implementation**:
- Add `addAction()` with icons
- Create PendingIntents for each action
- Handle actions in BroadcastReceiver
- Sync state changes to Supabase

**Effort**: 2 days

---

### Medium Impact (Priority 2)

#### 6. Add BigTextStyle for Long Messages

**Impact**: Better display of long content

**Benefits**:
- Expandable long messages
- Show full wish text
- Better readability
- Matches modern app behavior

**Implementation**:
- Check text length before building
- Use `BigTextStyle` for long content
- Set summary text for collapsed state

**Effort**: 0.5 day

---

#### 7. Implement Bubble Conversations

**Impact**: Floating chat heads for ongoing conversations

**Benefits**:
- Modern Android feature
- Multitasking support
- Quick access to conversations
- Persistent chat interface

**Implementation**:
- Add bubble metadata to notification
- Create shortcut ID for conversation
- Handle bubble activity
- Add bubble permission request

**Effort**: 3-4 days

---

#### 8. Add Notification Channels Customization

**Impact**: User control over notification preferences

**Benefits**:
- Per-channel settings UI
- User can mute specific types
- Better user control
- Reduced notification fatigue

**Implementation**:
- Create settings screen
- Allow channel importance changes
- Allow sound/vibration customization
- Persist user preferences

**Effort**: 2-3 days

---

#### 9. Implement Badge Count

**Impact**: Visual indicator of unread messages

**Benefits**:
- Launcher icon badge
- Quick visual cue
- Matches modern apps
- Better engagement

**Implementation**:
- Use ShortcutManager or launcher-specific APIs
- Count unread messages from Supabase
- Update badge on message changes
- Handle launcher limitations

**Effort**: 1-2 days

---

#### 10. Add Dynamic Shortcuts

**Impact**: Quick access to recent conversations

**Benefits**:
- Long-press launcher shortcuts
- Quick access to frequent contacts
- Modern Android feature
- Improved discoverability

**Implementation**:
- Use ShortcutManager
- Create dynamic shortcuts for recent chats
- Update shortcuts on new messages
- Handle shortcut launch

**Effort**: 2 days

---

### Low Impact (Priority 3)

#### 11. Add Sound/Vibration Customization

**Impact**: Personalized notification experience

**Benefits**:
- Custom sounds per channel
- Custom vibration patterns
- Better user personalization
- Distinguish notification types

**Implementation**:
- Add sound resources
- Configure channel sound
- Create vibration patterns
- Add settings UI

**Effort**: 1-2 days

---

#### 12. Implement Lock Screen Customization

**Impact**: Privacy and security

**Benefits**:
- Hide sensitive content on lock screen
- Show public/private versions
- User control over visibility
- Better privacy

**Implementation**:
- Set visibility on channel
- Create public version of notification
- Add settings for lock screen
- Handle secure lock screen

**Effort**: 1 day

---

#### 13. Add Notification History

**Impact**: View past notifications

**Benefits**:
- Notification log in app
- Recover missed notifications
- Better user control
- Debugging aid

**Implementation**:
- Persist notifications to Room
- Create history screen
- Allow re-opening from history
- Auto-expire old entries

**Effort**: 2-3 days

---

#### 14. Implement Progress Notifications

**Impact**: Visual feedback for long operations

**Benefits**:
- Upload/download progress
- Sync status
- Ongoing operation visibility
- Better UX for async tasks

**Implementation**:
- Use `NotificationCompat.Builder.setProgress()`
- Update progress periodically
- Remove on completion
- Handle cancellation

**Effort**: 1-2 days

---

#### 15. Add Rich Media Support

**Impact**: Image previews in notifications

**Benefits**:
- Show photo thumbnails
- Better visual context
- Matches modern apps
- Richer notification experience

**Implementation**:
- Use `BigPictureStyle`
- Download/cache images
- Handle image loading errors
- Add fallback to basic style

**Effort**: 2-3 days

---

## 13. File Map

### Notification-Related Files

| File Path | Purpose | Dependencies |
|-----------|---------|--------------|
| `app/src/main/java/com/baroness/app/services/FCMService.kt` | FCM message receiver and processor | Firebase Messaging SDK, NotificationManager, ProfileManager, StorageManager |
| `app/src/main/java/com/baroness/app/utils/NotificationManager.kt` | System notification builder and channel manager | AndroidX Core, NotificationCompat, MainActivity |
| `app/src/main/java/com/baroness/app/components/notification/InAppNotification.kt` | In-app notification overlay Composable | Coil (image loading), Material3, NotificationData |
| `app/src/main/java/com/baroness/app/viewmodels/NotificationViewModel.kt` | In-app notification state management | Kotlin Coroutines, StateFlow |
| `app/src/main/java/com/baroness/app/data/models/NotificationData.kt` | Notification data model | Kotlin Serialization |
| `app/src/main/java/com/baroness/app/modules/ProfileManager.kt` | FCM token sync to Supabase | OkHttp, JSONObject |
| `app/src/main/java/com/baroness/app/MainApplication.kt` | App initialization, FCM token registration | Firebase Messaging SDK, NotificationManager, StorageManager |
| `app/src/main/java/com/baroness/app/MainActivity.kt` | Navigation handler for notification taps | Navigation Compose, NotificationViewModel |
| `app/src/main/AndroidManifest.xml` | Permissions and service declarations | Android system |
| `app/google-services.json` | Firebase project configuration | Firebase services |
| `supabase/functions/notify-trigger/index.ts` | Edge Function for FCM payload generation | Supabase SDK, Google Auth Library, FCM API |
| `supabase/push_notifications.sql` | Database trigger setup for notifications | PostgreSQL, pg_net extension |
| `supabase/notifications_setup.sql` | Alternative notification trigger setup | PostgreSQL, pg_net extension |
| `supabase/schema.sql` | Database schema including fcm_token column | PostgreSQL |

### Data Flow Dependencies

**FCM Token Flow**:
```
MainApplication → FirebaseMessaging → StorageManager → ProfileManager → Supabase
```

**Notification Flow**:
```
Database Trigger → Edge Function → FCM → FCMService → NotificationManager → System
```

**In-App Notification Flow**:
```
Supabase Realtime → WishlistRepository → NotificationViewModel → InAppNotification Composable
```

---

## 14. Overall Assessment

### Current Maturity Level

**Rating**: **Beginner** (Basic implementation)

**Justification**:
- Only basic title/body notifications
- No advanced Android notification features
- Minimal customization
- No conversation support
- Single notification type (wishlist)
- No action buttons or direct reply
- No grouping or styling

---

### Strengths

1. **Solid Foundation**
   - FCM integration is working
   - Token registration and refresh handled
   - Edge Function architecture is sound
   - Database trigger approach is scalable

2. **Clean Architecture**
   - Separation of concerns (Service → Manager → Builder)
   - Proper use of FirebaseMessagingService
   - In-app notification system for foreground
   - Data model is well-structured

3. **Supabase Integration**
   - FCM token storage in profiles table
   - Edge Function for server-side logic
   - Database trigger automation
   - Realtime fallback for foreground

4. **Code Quality**
   - Proper error handling and logging
   - Coroutine usage for async operations
   - Clean Kotlin code
   - Good separation of layers

---

### Weaknesses

1. **Feature Gaps**
   - No MessagingStyle
   - No Person API
   - No action buttons
   - No direct reply
   - No grouping
   - No bubbles

2. **UX Limitations**
   - Basic title/body only
   - No avatars in notifications
   - No conversation context
   - No quick actions
   - No visual grouping

3. **Missing Notification Types**
   - Only wishlist notifications implemented
   - No message notifications
   - No system alerts
   - No reminder notifications

4. **Customization**
   - No user preferences
   - No channel customization UI
   - No sound/vibration options
   - No lock screen controls

5. **Modern Android Features**
   - No badge count
   - No dynamic shortcuts
   - No bubble conversations
   - No notification history
   - No rich media

6. **Runtime Permission**
   - POST_NOTIFICATIONS not requested at runtime
   - May fail on Android 13+

---

### Effort Estimate to Match Modern Messaging Apps

**Target**: WhatsApp, Google Messages, Telegram, iMessage level

**Estimated Effort**: **15-20 days** (3-4 weeks)

**Breakdown**:
- MessagingStyle + Person API: 3-4 days
- Notification grouping: 1-2 days
- Direct reply (RemoteInput): 2-3 days
- Action buttons: 2 days
- Bubble conversations: 3-4 days
- Badge count: 1-2 days
- Dynamic shortcuts: 2 days
- BigTextStyle + rich media: 2-3 days
- Channel customization UI: 2-3 days
- Message notifications (new Edge Function): 2 days
- Testing and polish: 3-4 days

**Critical Path**:
1. MessagingStyle + Person API (foundational)
2. Notification grouping (UX improvement)
3. Direct reply (core messaging feature)
4. Action buttons (UX improvement)
5. Message notifications (expand to chat)
6. Bubble conversations (modern feature)

**Dependencies**:
- Person API required for MessagingStyle
- Grouping required for bubble conversations
- Message notifications require new database trigger
- Badge count depends on launcher support

---

### Recommended Roadmap

**Phase 1: Core Messaging Features** (Week 1)
- Implement MessagingStyle
- Add Person API for avatars
- Implement notification grouping
- Add direct reply (RemoteInput)
- Add action buttons

**Phase 2: Message Notifications** (Week 2)
- Create database trigger on messages table
- Add Edge Function for message notifications
- Implement message notification payload
- Test end-to-end message flow

**Phase 3: Modern Features** (Week 3)
- Implement bubble conversations
- Add badge count
- Create dynamic shortcuts
- Add BigTextStyle for long messages
- Implement rich media support

**Phase 4: Polish & Customization** (Week 4)
- Add notification channels customization UI
- Implement sound/vibration options
- Add lock screen customization
- Create notification history
- Add progress notifications
- Testing and refinement

---

### Conclusion

The Baroness-kt notification system has a **solid foundation** with proper FCM integration, Supabase Edge Functions, and clean architecture. However, it is currently at a **beginner level** with minimal features compared to modern messaging apps.

The system is **functional** for basic notifications but lacks the **rich features** users expect from a messaging application. With an estimated **15-20 days** of focused development, the notification system can be elevated to match the polish and feature set of apps like WhatsApp, Google Messages, or Telegram.

**Priority Focus**: Implement MessagingStyle, Person API, notification grouping, and direct reply first, as these provide the biggest UX impact and are foundational for other features.

---

**Report End**
