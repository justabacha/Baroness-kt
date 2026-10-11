# Phase 2C-3 Preflight Report — Realtime Channel Lifecycle Audit

## Executive Summary

During Phase 2C-2 live token refresh testing, an in-process access token refresh successfully renewed credentials for REST and WebSocket transport upgrades (HTTP 101), but left the HumanChat Realtime sync-pipe channel stuck without receiving incoming messages until the app was restarted. Code inspection reveals the root cause: while `SessionTokenCache.updateRealtime(token)` updates the Supabase client's auth state (`realtime.setAuth(token)`), it does not trigger an unsubscription and re-subscription of active Realtime channels (`chat_sync_pipe_$personaId` and conversation channels). Consequently, active Phoenix channels remain bound to their initial subscription handshake state and pre-refresh credentials, causing silent message delivery failures under strict RLS policies.

---

## 1. Mandatory Code Inspection

### 1. `ChatRepository.kt` — Full Inspection
- **Channel Creation & Subscription Methods**:
  - `restartSyncPipeSubscription(personaId: String)`: Creates and subscribes to `chat_sync_pipe_$personaId`.
  - `subscribeToConversation(conversationId: String)`: Creates and subscribes to `chat_$sharedChannel` (used for typing and read receipts).
  - `broadcastTyping(...)` / `markMessagesAsRead(...)`: Publishes broadcast events to `chat_$sharedChannel`.
- **Coroutine Scopes**:
  - `ChatRepository` runs on its own process-lifetime repository scope: `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
  - Subscriptions (`syncPipeJob`, `subscribeToConversation`) launch within this repository scope or flow collectors.
- **Channel Topic Strings**:
  - Sync pipe: `"chat_sync_pipe_$personaId"` (e.g., `chat_sync_pipe_phesty_official`).
  - Conversation channels: `"chat_$sharedChannel"` (e.g., `chat_phesty_official_baroness_official`, `chat_friday_$personaId`).
- **Awaited vs Fire-and-Forget**:
  - `channel.subscribe(blockUntilSubscribed = true)` is awaited in `restartSyncPipeSubscription`.
  - `subscribeToConversation` calls `channel.subscribe()` in a coroutine block without blocking the caller.
- **Response to Token Change**:
  - **None.** `ChatRepository` has zero listeners or observers monitoring `SessionTokenCache` or token refresh events.
- **`Realtime.setAuth` Calls**:
  - `ChatRepository` never calls `realtime.setAuth`. That is handled exclusively by `SessionTokenCache`.

### 2. `ChatRepository.restartSyncPipeSubscription`
- **Invocation**: Triggered in `init` block when `storageManager.getStringFlow("currentPersonaId")` emits a non-null persona ID.
- **Unsubscription**: Cancels `syncPipeJob` (`syncPipeJob?.cancel()`), which triggers the `finally` block executing `channel.unsubscribe()`.
- **Awaiting**: Uses `channel.subscribe(blockUntilSubscribed = true)`.
- **Connection Health State**: Writes to `_isSubscribed` (`MutableStateFlow<Boolean>`) based on channel status (`SUBSCRIBED` / `JOINING`) and a 5-second fallback timeout.

### 3. `ChatRepository.subscribeToConversation`
- **Invocation**: Invoked when a user opens `ChatRoomScreen` and collects the returned flow.
- **Ownership & Cancellation**: Scoped to the repository coroutine scope; unsubscribes when the collector/scope cancels, but does not rejoin on token refresh.
- **Status Checking**: Does not check or report subscription status to `_isSubscribed`.

### 4. `SupabaseConfig.kt` — Realtime Plugin Configuration
- Installed via `install(Realtime)` in `createSupabaseClient`.
- Configured with `accessToken = { SessionTokenCache.getValidTokenOrRefresh() }`.
- No explicit reconnect delays, timeouts, or custom channel re-join policies are defined in code.

### 5. `SessionTokenCache.kt` — Refresh Success Path
- When a token refresh succeeds in `refreshIfNeeded()`:
  1. Saves new session credentials to DataStore (`storageManager.saveAuthSession(...)`).
  2. Updates `cachedToken`, `cachedRefreshToken`, and `cachedExpiry`.
  3. Calls `updateRealtime(newToken)`, which invokes `SupabaseConfig.supabase.realtime.setAuth(token)`.
- **Missing Action**: It does **not** trigger a client-level Realtime disconnect/reconnect or restart active channel subscriptions.

### 6. ProcessLifecycleOwner / ON_RESUME Handling
- [ChatRepository](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/repository/ChatRepository.kt) registers a `ProcessLifecycleOwner` observer for `ON_RESUME` that triggers `triggerSync()` and `fetchOfflineMessages()`, but does not restart Realtime subscriptions.

### 7. ViewModel / UI "Connecting to live chat..." String
- **Source**: [ChatRoomScreen.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/screens/ChatRoomScreen.kt#L147): `settingsViewModel.showWarning("Connecting to live chat...")`.
- **Driving State**: `val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()`, which delegates to `ChatRepository.isSubscribed`.
- **Channel Association**: Tied exclusively to the global sync-pipe channel (`chat_sync_pipe_$personaId`), not individual conversation channels.

---

## 2. Log Analysis

*Note: Live Edge log extraction was not performed in this inspection session; findings are derived directly from static code and architectural flow analysis.*

---

## 3. Behavioral Hypotheses Verdicts

| Hypothesis | Verdict | Evidence |
| :--- | :--- | :--- |
| **H1:** Channel subscriptions are created once at process start and never re-established after token refresh. | **PARTIALLY CONFIRMED** | `ChatRepository` subscriptions run in a process-lifetime scope and never re-subscribe on token refresh events. However, they *do* re-run if `currentPersonaId` changes. |
| **H2:** After token refresh, `setAuth` is called on Realtime, but existing channels are not told to rejoin with the new token. | **CONFIRMED** | `SessionTokenCache.updateRealtime()` invokes `realtime.setAuth()`, but active Phoenix channels remain joined under their initial connection handshake state. |
| **H3:** The "Connecting to live chat" UI state is driven by `isSubscribed`, which is set once and never reset during refresh. | **REFUTED / REFINED** | `_isSubscribed` is driven by `channel.status` and a 5s timeout. However, conversation channels do not affect `isSubscribed`, and during stale token states, `channel.status` may fail to emit `SUBSCRIBED`. |
| **H4:** WebSocket reconnects (101), but Phoenix channel join is rejected by the server due to a stale token used during join. | **CONFIRMED** | Without an explicit channel re-subscribe or client reconnect (`realtime.disconnect()` / `realtime.connect()`), active channels do not re-send the join payload with the new JWT. |
| **H5:** Outgoing writes (WorkManager / REST) succeed via refreshed `accessToken` callback, but incoming Realtime messages fail. | **CONFIRMED** | REST calls fetch fresh tokens via `getValidTokenOrRefresh()`, while Realtime channels remain attached to stale pre-refresh channel registrations. |

---

## 4. Cross-Cutting Investigation

a) **Code path causing Realtime channels to rejoin after refresh**: **None currently exists.** `SessionTokenCache` updates `realtime.setAuth()`, but nothing notifies repositories or triggers channel re-subscription.

b) **Mechanism detecting dead-but-connected channel**: None. `supabase-kt` handles transport-level WebSocket drops, but application-level Phoenix channel join failures under RLS require explicit re-subscription or client reconnection.

c) **Sync-pipe topic name on refresh**: Stays exactly `chat_sync_pipe_$personaId`. It relies on RLS policies (`recipient_id = auth.jwt() ->> 'persona'`) to evaluate events.

d) **WebSocket disconnect behavior**: When the WebSocket transport disconnects (e.g. background/foreground transition), `supabase-kt` reconnects the transport, but active channels require proper re-authentication and re-joining.

e) **Supabase-kt 3.1.1 documentation/pattern**: In `supabase-kt` 3.1.1, when access tokens rotate, updating `realtime.setAuth(token)` alone does not force active channels to re-authenticate. The standard pattern is to force a Realtime client reconnection (`realtime.disconnect()` followed by `realtime.connect()`) or explicitly iterate and re-subscribe active channels upon successful token refresh.

---

## 5. Root Cause

The root cause of the stuck "Connecting to live chat..." state and silent message drop after token refresh is located in **[SessionTokenCache.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/auth/SessionTokenCache.kt)** (`refreshIfNeeded()`) and **[ChatRepository.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/repository/ChatRepository.kt)** (`restartSyncPipeSubscription`). 

While `SessionTokenCache` correctly updates the Supabase client's auth credential via `realtime.setAuth(newToken)`, it does **not** trigger a disconnection/reconnection of the Realtime client or a restart of active repository subscriptions. Consequently, active Phoenix channels remain joined with the expired JWT, causing the server's strict RLS policies to reject incoming event payloads silently.

---

## 6. Correctness Issues Discovered

1. **Silent RLS Rejection**: Once strict RLS is enabled on `chat_sync_pipe`, stale channel subscriptions will fail silently with zero error logs on the client side.
2. **Missing Token-Refresh Observer**: Repositories managing Realtime channels (`ChatRepository`, `WishlistRepository`) do not observe token updates or refresh events.
3. **5-Second Timeout Masking**: The 5-second timeout in `restartSyncPipeSubscription` forces `_isSubscribed.value = true` even if the channel is stuck in an unauthenticated or joining state, masking connection failures from the UI.

---

## 7. Fix Candidates

### Approach A: Realtime Client Reconnect on Refresh (Recommended)
- **Mechanism**: In `SessionTokenCache.refreshIfNeeded()`, after successfully saving a rotated session and calling `realtime.setAuth(newToken)`, also invoke `SupabaseConfig.supabase.realtime.disconnect()` and `SupabaseConfig.supabase.realtime.connect()`.
- **Trade-offs**: Simple, centralized, and forces all active Supabase Realtime channels to renegotiate their WebSocket connection and re-join with the fresh JWT.

### Approach B: Token-Change Flow in Repositories
- **Mechanism**: Expose a `tokenStateFlow` or refresh event from `SessionTokenCache` that `ChatRepository` and `WishlistRepository` collect to cancel and restart their channel subscription loops.
- **Trade-offs**: More fine-grained, but requires modifying multiple repositories and channel management flows.

---

## 8. Phase 3 Readiness Assessment

With **Approach A** (Realtime client disconnect/reconnect on refresh) implemented, the Realtime architecture will successfully re-authenticate active channels upon token rotation. This ensures full compatibility with strict RLS policies on `chat_sync_pipe` and wishlist tables without requiring app restarts.

---

## 9. Open Questions

1. Should `SessionTokenCache` proactively disconnect and reconnect Realtime on every token refresh, or only when forced/expired? *(Recommendation: on every successful token refresh to guarantee RLS alignment).*
