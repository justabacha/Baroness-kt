# Real-Time Message Sync Inspection & Architecture Review

**Date:** September 16, 2026  
**Status:** Diagnostic Complete  
**System:** Baroness Chatroom SyncPipe

---

## Executive Summary
The current real-time message synchronization pipeline (SyncPipe) in Baroness follows an "Outbox Pattern" using a dedicated Postgres table (`chat_sync_pipe`) as a transport layer. While the architecture is conceptually sound for offline-first delivery, several critical implementation flaws in the identity handling and subscription lifecycle currently prevent reliable real-time sync between two distinct accounts/devices. Key issues include static persona handling in a singleton repository and hardcoded receiver logic in the synchronization worker.

---

## Data Flow Trace

### 1. Sender Path (The Dispatch)
1. **Action:** User taps "Send".
2. **Optimistic Update:** `ChatRepository.sendMessage` creates a `MessageEntity` with status `PENDING` and inserts it into the local Room database.
3. **Reactive UI:** The `LazyColumn` in `MessageList` observes the Room Flow and immediately displays the pending message (dimmed or with a "Sent" icon).
4. **Outbox Trigger:** `triggerSync()` enqueues `ChatSyncWorker`.
5. **Syncing:** `ChatSyncWorker` picks up the pending row and calls `syncHumanMessage`.
6. **Remote Storage:** The message is POSTed to the Supabase `messages` table.
7. **Pipe Insertion:** A payload containing the message metadata is inserted into the `chat_sync_pipe` table with the `recipient_id` set to the other user's ID.

### 2. Transport Path (The Relay)
1. **Database Event:** Postgres receives the `INSERT` on `chat_sync_pipe`.
2. **Realtime Broadcast:** Supabase Realtime detects the change (assuming Replication is enabled for this table).
3. **WebSocket Delivery:** The payload is pushed over the established WebSocket channel to any client subscribed to the specific user's pipe channel.

### 3. Receiver Path (The Receipt)
1. **Socket Listener:** The `ChatRepository` on the receiver's device receives the `PostgresAction`.
2. **Identity Verification:** The repository checks if the `recipient_id` matches the `currentPersonaId`.
3. **Local Persistence:** `handlePipeMessage` inserts the received message into the receiver's local Room database.
4. **Reactive UI update:** The Room DAO emits a new list of messages. The `ChatRoomViewModel`'s `uiState` updates automatically, and the new message slides into the receiver's view.
5. **Cleanup:** The client calls `ChatApi.deletePipeItem` to remove the consumed message from the server-side pipe.

---

## Identified Failure Points

### 1. Static Identity Binding (The "Login Lock")
**Root Cause:** The `ChatRepository` is a singleton that reads `currentPersonaId` from `StorageManager` only once during its initial `init` block.
**Impact:** If a user logs out and logs in as a different persona, or if the repository initializes before the ID is set, the Realtime subscription will either be missing or bound to the *previous* user's ID. Messages sent to the new user will never be received in real-time.

### 2. Hardcoded Receiver Logic
**Root Cause:** In `ChatSyncWorker.syncHumanMessage`, the `receiverId` is determined using a static `when` block:
```kotlin
val receiverId = if (message.conversationId == "baroness") "baroness_official" else "phesty_official"
```
**Impact:** This logic only works for the two hardcoded test accounts. In any other 1-on-1 conversation, the SyncPipe will send the message to the wrong user (or fail), meaning real-time delivery will never reach the actual participant.

### 3. Lifecycle & Subscription Fragility
**Root Cause:** `subscribeToSyncPipe` is called once. While it has a retry loop for connection errors, it does not have a mechanism to "Re-subscribe" when the `currentPersonaId` changes or when the user switches conversations.
**Impact:** The connection may go stale or be listening to the wrong channel without the system realizing it needs a reset.

### 4. Premature Pipe Cleanup
**Root Cause:** The client deletes the pipe item (`deletePipeItem`) immediately after calling `messageDao.insertMessage`.
**Impact:** If the app crashes or the database insert fails (e.g., disk full), the message is deleted from the server and is lost forever. Consuming should ideally happen *after* successful persistence confirmation.

---

## Recommended Fixes & Code Samples

### Fix A: Reactive Identity Handling
Modify `ChatRepository` to observe the `currentPersonaId` as a Flow and restart the SyncPipe subscription whenever the ID changes.

**File:** `ChatRepository.kt`
```kotlin
// 1. Observe the ID instead of reading it once
private val currentPersonaIdFlow = storageManager.observeString("currentPersonaId")

init {
    scope.launch {
        currentPersonaIdFlow.collect { newId ->
            if (newId != null) {
                restartSyncPipeSubscription(newId)
            }
        }
    }
}
```

### Fix B: Dynamic Receiver Resolution
Update the SyncWorker to fetch the actual recipient from the conversation metadata or the message itself rather than hardcoding it.

**File:** `ChatSyncWorker.kt`
```kotlin
// Determine receiver dynamically from the conversationId 
// (which in Baroness is usually the ID of the other participant)
val receiverId = message.conversationId 
```

### Fix C: Improved Consumption Safety
Ensure the pipe item is only deleted once the local database has confirmed the transaction.

**File:** `ChatRepository.kt`
```kotlin
private suspend fun handlePipeMessage(record: Map<String, JsonElement>) {
    // ... logic to create entity ...
    val success = messageDao.insertMessage(entity)
    if (success) { // Ensure insert succeeded before clearing pipe
        ChatApi.deletePipeItem(pipeId)
    }
}
```

---

## Testing & Verification Steps

1. **Identity Verification:**
   - Log in User A. Verify `chat_sync_pipe_UserA` channel is active in Logcat.
   - Log out and log in as User B. Verify the channel switches to `chat_sync_pipe_UserB`.

2. **Cross-Device Sync:**
   - Device 1 (User A) sends a message to User B.
   - Check Supabase Dashboard: Verify a row appears in `chat_sync_pipe` with `recipient_id = UserB`.
   - Device 2 (User B) should see the message appear *instantly* without a manual refresh.

3. **Status Check:**
   - Verify the `status` column in Room changes from `PENDING` to `SENT` on the sender's side after `ChatSyncWorker` finishes.
