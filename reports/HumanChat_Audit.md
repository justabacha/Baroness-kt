# HumanChat — End-to-End Message Flow & Production Stability Audit

**Audit date:** 2026-10-08  
**Scope:** Android HumanChat path, local persistence and sync, Supabase/PostgREST, Realtime, checked-in database artifacts, identity and authorization boundaries.

## Evidence and limits

This audit follows the checked-in application and Supabase configuration. It is not a live inspection of the deployed Supabase database, publication, policies, or runtime channel status. In particular, `supabase/schema.sql` and `supabase/policies.sql` are descriptive snapshots, not complete reproducible DDL migrations for HumanChat. The checked-in migration history does not define the `messages` table and its complete policy set. Production behavior can therefore differ from the repository snapshot; the deployment must be inspected before treating any described policy as active.

The working tree already contained user changes when this report was written. The current `AuthManager` now calls the `verify-passkey` Edge Function and stores its returned JWT; migration 026 removes public access-key SELECT policies. These existing changes were not modified. The Supabase client configuration still does not apply the stored JWT, and migration 026 does not resolve HumanChat message or sync-pipe authorization.

## A. Executive diagnosis

HumanChat is a **local-first, best-effort sync pipeline**, not one coherent server-authoritative messaging system. The UI reads messages from Room. WorkManager writes messages to the Supabase `messages` table and separately inserts a `chat_sync_pipe` row to notify the recipient. Realtime listens to inserts on that pipe; the receiving client saves the payload to Room and attempts to delete the pipe row. Message-table fetch and pipe recovery are separate paths.

That design can deliver messages on a happy path, but its guarantees do not line up:

- The passkey login flow now obtains and saves a persona JWT, but the shared Supabase client is constructed with the anon key and does not install/configure Auth or apply that JWT. Checked-in RLS artifacts are inconsistent: migration 024 restores public access to `chat_sync_pipe`, while message policies are either broadly public in schema snapshots or fixed-persona policies in the policy snapshot.
- The checked-in message schema snapshot omits fields that the Kotlin DTO and Worker send. No checked-in HumanChat migration adds those fields or fully defines the message schema and policy contract.
- The Worker can overwrite a newer local edit with an older snapshot, associate an old queued message with the currently logged-in persona, and retry a pipe insert after the message row already succeeded.
- Realtime is used as transport, but there is no consistent delivery/read state or dependable reconciliation model. Receipt broadcasts are not persisted, and conversation event collectors are launched in a scope that outlives their screen.
- A later server fetch can replace local rows with stale or incomplete state, including resurrecting deleted messages. Ordering partly depends on device clocks.

**Production assessment: not production-ready.** A two-client happy-path test does not establish correctness under RLS, account switching, edits racing sync, missed Realtime events, or Android lifecycle changes.

## B. Reconstructed message flow

### User 1 sends

1. `ChatInput` passes text to `HumanChatViewModel.onSendMessage`.
2. The ViewModel calls `ChatRepository.sendMessage`. The repository creates a UUID and device timestamp, inserts a `PENDING` Room `MessageEntity`, and enqueues unique `ChatSync` work.
3. Room emits the row through `getMessages(conversationId)`. The ViewModel maps it to `ChatRoomUiState.Success`; Compose renders it immediately. A server acknowledgement is not required for display.
4. `ChatSyncWorker` reads pending Room rows. For HumanChat, it reads the *current* `currentPersonaId`, maps local conversation aliases (`baroness`/`phesty`) to recipient IDs, and upserts a `MessageDto` into `messages`. It writes `conversation_id = "human_chat"` and sends the locally generated timestamp as `created_at`.
5. If the upsert returns success, the Worker inserts a separate `chat_sync_pipe` row with the receiver as `recipient_id` and a JSON payload containing message ID, sender, content, timestamp, and available metadata.
6. The Worker marks the originally fetched Room entity `SENT` only if both remote operations return success.

### User 2 receives

1. `ChatRepository` owns a process-lifetime subscription for `chat_sync_pipe`. Its topic includes the current persona, but the Postgres-change flow subscribes to the table without a recipient filter; the handler checks `recipient_id` after receiving the event.
2. On a matching insert, `handlePipeMessage` maps the sender to a local conversation alias, inserts/replaces the payload as a Room row using the message UUID, then attempts to delete the pipe record.
3. Room emits the row to active conversation observers, and Compose renders it.
4. Offline recovery fetches up to 100 pipe rows for a recipient, handles them, and attempts deletion. This is invoked during app resume and after the sync-pipe subscription starts. Opening a conversation separately fetches the `messages` table.
5. User 2’s reply repeats the same sequence in reverse.

The server `messages` table and the pipe are distinct writes. The pipe is both durable queue data and a Realtime notification, but no transaction or server-side outbox ties its lifecycle to the message row. Realtime events are therefore neither the sole source of truth nor merely harmless hints: the client uses their payload to create UI-visible messages, while also fetching server history through another path.

## C. Architecture map

| Responsibility | Effective owner today |
|---|---|
| Immediate display and conversation list | Room `MessageEntity` flows |
| Intended durable HumanChat record | Supabase `messages`, if the PostgREST write succeeds |
| Prompt cross-device notification and offline queue | Supabase `chat_sync_pipe` |
| Realtime subscription | Singleton `ChatRepository` process scope |
| Offline outbound retry | Room `PENDING` rows plus WorkManager |
| History catch-up | `fetchMessagesFromServer` on HumanChat ViewModel initialization |
| Typing and read signals | Separate Realtime broadcast channel |
| Delivery/read status shown to user | Room metadata fields, without a complete transition path |
| Identity | DataStore `currentPersonaId`, plus a separately stored JWT that the shared client does not apply |

Room, `messages`, pipe payloads, and UI status are partly independent representations. There is no message revision/version or canonical merge rule defining which representation wins after concurrent updates.

## D. Findings

### 1. Critical — User isolation and client authorization are contradictory

**Area:** Authentication, RLS, Realtime exposure  
**Root cause:** Login now validates the passkey through `verify-passkey` and saves a persona JWT, but `SupabaseConfig` builds the shared client with the anon key and installs PostgREST, Realtime, and Storage only. It does not install/configure Auth or apply the saved JWT. The checked-in authorization artifacts do not define one coherent HumanChat policy set.

**Evidence:** `AuthManager.checkGate` saves `auth_token`; `SupabaseConfig.kt` does not read or apply it. `migrations/024_friday_v1_app_compat_rls.sql` creates `chat_sync_pipe` `FOR ALL USING (true) WITH CHECK (true)`. `schema.sql` describes public access to `messages`, while `policies.sql` lists fixed-persona policies; migration 024 does not reconcile HumanChat message policies. Migration 026 only removes public access-key SELECT policies.

**Failure scenario / current behavior:** An anon client can be rejected by an authenticated-only message insert policy, while permissive sync-pipe access can expose or permit modification of pipe records. The Realtime channel topic is not a database authorization boundary, and checking `recipient_id` after receiving an event does not prevent unauthorized payload exposure.

**Expected behavior:** Every API request and Realtime subscription should carry a current, verifiable identity; database policies must bind message reads, writes, and pipe access to the authenticated sender, recipient, and conversation membership.

**Production impact:** Confidentiality and integrity of conversations are not established. Depending on the deployed policy set, legitimate sends may fail or private payloads may be exposed. This is a release blocker until deployed policies and client auth are verified and aligned.

### 2. High — HumanChat schema is not reproducible and conflicts with the client contract

**Area:** Database schema, PostgREST writes, replies and metadata  
**Root cause:** The checked-in schema is a descriptive snapshot rather than a migration-defined contract. Its `messages` table entry omits fields used by the Android DTO and Worker.

**Evidence:** `ChatApi.MessageDto` serializes reply ID/content/sender, `delivered_at`, and `is_pinned`. `ChatSyncWorker.syncHumanMessage` sends them. The `messages` entry in `supabase/schema.sql` does not list these columns, and checked-in migrations do not add them to `public.messages`.

**Failure scenario / current behavior:** If the deployed schema matches the checked-in snapshot, a message with reply or populated metadata can be rejected by PostgREST. `ChatApi.sendMessage` catches the error and returns `false`, leaving the optimistic Room row visible and pending.

**Expected behavior:** Ordered migrations should define a schema that matches the Kotlin DTO, and CI/release checks should detect drift.

**Production impact:** Features such as replies can fail while plain text appears to work; schema mismatch is hard to diagnose because no authoritative HumanChat DDL exists in the repository.

### 3. High — Concurrent local edits can be lost during sync acknowledgement

**Area:** Concurrency, local state, Worker reconciliation  
**Root cause:** The Worker snapshots pending entities, performs network requests, then writes the old snapshot back with only `status` changed.

**Evidence:** `ChatSyncWorker.doWork` reads `getPendingMessages()` and later calls `updateMessage(message.copy(status = "SENT"))`. `MessageDao.updateMessage` writes the full entity.

**Failure scenario / current behavior:** The Worker reads version A. While the request is in flight, a user edits, reacts to, pins, or deletes the message, producing version B in Room. The Worker then replaces B with A as `SENT`, potentially erasing the newer change and preventing its retry.

**Expected behavior:** Acknowledgement should update status only if the current row still matches the sent version, or merge acknowledgement into newer local state.

**Production impact:** Edits, reactions, or deletion can disappear silently on slow networks or during rapid interaction.

### 4. High — Pending messages can be sent under the wrong persona

**Area:** Identity changes, account isolation, local persistence  
**Root cause:** Pending rows are not scoped by persona, while the Worker chooses `sender_id` from the *current* `currentPersonaId`. The Room database is not partitioned by persona.

**Evidence:** `MessageDao.getPendingMessages()` selects all pending messages. `ChatSyncWorker.syncHumanMessage` reads current identity at execution time. `MessageEntity` stores no immutable account owner or recipient. Logout removes persona preferences but does not clear or partition the Room outbox.

**Failure scenario / current behavior:** A message queued offline by one persona remains pending through logout. A different persona signs in; the Worker can submit that old message as the new persona.

**Expected behavior:** Every queued message must retain immutable owner, sender, and recipient identity, and sync must refuse to send it under a different authenticated identity.

**Production impact:** Cross-account disclosure, misattributed messages, incorrect inbox state, and unauthorized writes.

### 5. High — Message persistence and pipe delivery are non-atomic

**Area:** Delivery reliability, retry and deduplication  
**Root cause:** The Worker performs a message upsert and a separate pipe insert without a transaction, idempotent outbox, or receiver acknowledgement protocol.

**Evidence:** `syncHumanMessage` calls `ChatApi.sendMessage` and then `ChatApi.pushToSyncPipe`. The receiver processes a pipe row and attempts deletion. `fetchSyncPipe` caps each fetch at 100 rows.

**Failure scenario / current behavior:** The message write succeeds but pipe insertion fails, times out, or succeeds without the client receiving its acknowledgement. The local row remains pending and a retry can add another pipe row for the same message. While online, the recipient does not subscribe to `messages`; if pipe delivery is missed, history recovery depends on a later fetch, generally when opening the conversation.

**Expected behavior:** Persistence and notification should be linked through an idempotent server-side outbox or equivalent. Realtime should prompt reconciliation against durable message state rather than be the only immediate delivery path.

**Production impact:** Duplicate pipe events, delayed visibility, and sender/receiver disagreement. The UUID and Room primary-key replacement help avoid duplicate message bubbles in one local database but do not make event processing or side effects exactly once.

### 6. High — Conversation subscriptions leak and connection status is misleading

**Area:** Realtime lifecycle, collectors, UI state  
**Root cause:** Each `subscribeToConversation` call launches a new coroutine in the repository’s process-lifetime scope and returns a `MutableSharedFlow`. That work is not tied to collection or screen lifecycle. The ViewModel calls it independently for typing and read receipts. The displayed `isSubscribed` state describes a different, global sync-pipe channel.

**Evidence:** `HumanChatViewModel` calls `subscribeToConversation` twice in `init`. `ChatRepository.subscribeToConversation` starts work with repository `scope`, creates a channel, merges broadcast flows, and has no cancellation cleanup or unsubscribe path. Separately, the sync-pipe connection sets `_isSubscribed`; a five-second timeout can force it to `true`.

**Failure scenario / current behavior:** Navigating through chats can leave channels and collectors alive. Broadcasts can occur before a consumer collects the returned flow. A timeout may clear the UI warning even when the relevant conversation channel is not connected.

**Expected behavior:** Each subscription should have a clear lifecycle owner, cancellation/unsubscribe path, accurate status, and reconnect reconciliation.

**Production impact:** Resource growth, missed ephemeral events, and false connection-health UI.

### 7. Medium — Typing and read receipts are not connected end to end

**Area:** Message status, broadcasts, UI interaction  
**Root cause:** The code defines receipt and typing operations but does not wire them to input/visibility interactions or persist their results.

**Evidence:** `ChatInput` updates local text but does not call `setUserTyping`. `HumanChatViewModel.observeReadReceipts` discards received payloads. `markMessagesAsRead` broadcasts but no call site was found. No HumanChat path updates `deliveredAt` or `readAt` from a receipt transition.

**Failure scenario / current behavior:** Typing is not emitted as a user types. Read broadcasts, if sent, are ephemeral and do not update message state. UI details can display delivery/read fields, but HumanChat does not establish those values authoritatively.

**Expected behavior:** If these features are user-visible, implement explicit end-to-end event ownership, persistence, and synchronization. Otherwise do not imply their state is reliable.

**Production impact:** Missing or stale typing/read/delivery behavior despite UI and model support.

### 8. High — Server fetch can resurrect deleted messages

**Area:** Deletion and fetch reconciliation  
**Root cause:** `MessageDto` includes `isDeleted`, but HumanChat fetch conversion does not copy it into `MessageEntity`; the default is `false`, and Room insertion uses `REPLACE`. “Delete for me” and “delete for everyone” also converge on the same global sync operation.

**Evidence:** `ChatApi.MessageDto` maps `is_deleted`. `ChatRepository.fetchMessagesFromServer` maps HumanChat messages without assigning `isDeleted`. `MessageDao.insertMessages` uses `OnConflictStrategy.REPLACE`. Both delete methods set `isDeleted = true`, status `PENDING`, and trigger sync; the Worker remotely marks the shared message deleted.

**Failure scenario / current behavior:** A locally tombstoned message is returned by a later server fetch and replaced with an entity whose `isDeleted` defaults to false. A “delete for me” operation can proceed through the global server-delete path.

**Expected behavior:** Fetch must preserve server deletion state. Local-only deletion must not change shared server state; global deletion must be authorized by the server.

**Production impact:** Deleted content can reappear after reopening/reconnect, and a local deletion may affect the other participant.

### 9. Medium — Ordering and history recovery depend on client clocks and incomplete fetches

**Area:** Ordering, catch-up and scalability  
**Root cause:** The sender supplies the timestamp persisted as `created_at`; message fetch has no explicit ordering or pagination. Pipe recovery is capped at 100 rows.

**Evidence:** `ChatSyncWorker` converts the local device timestamp to `created_at`. `ChatApi.fetchMessages` has no `order` or pagination. `MessageDao` sorts by local `timestamp`. `fetchSyncPipe` orders by `created_at` but applies `limit(100)`.

**Failure scenario / current behavior:** Clock skew puts messages in the wrong order. Large histories can exceed the API row cap with no cursor-based continuation. A pipe backlog above 100 depends on another recovery call. Fetch errors are caught and returned as empty lists, obscuring the difference between no data and failed retrieval.

**Expected behavior:** Use a server-owned ordering key and explicit paginated catch-up, with errors surfaced to state/UI.

**Production impact:** Missing or misplaced history after long offline periods or as usage grows.

### 10. Medium — Retries and errors do not provide clear user recovery

**Area:** Failure handling and observability  
**Root cause:** `ChatApi` catches errors and returns booleans; fetch failures often return empty lists. Optimistic rows have no user-facing failure state or dependable distinction between queued, persisted, and delivered.

**Evidence:** `ChatApi.sendMessage` returns `false` on exception; `fetchMessages` returns `emptyList()`. The Worker retries. `triggerSync` enqueues unique work with `ExistingWorkPolicy.REPLACE`.

**Failure scenario / current behavior:** RLS or schema rejection leaves a visible pending bubble while retries continue. A successful message write followed by failed pipe insertion is reported as overall failure although the server row exists. Re-enqueuing work may replace in-flight work, making outcomes dependent on cancellation timing.

**Expected behavior:** Surface stage-specific errors and partial success; preserve durable pending state; make retries idempotent and observable.

**Production impact:** Users cannot tell whether a message is queued, sent, or failed, and logs do not provide consistent correlation across stages.

### 11. Medium — Room destructive fallback can discard the offline outbox

**Area:** Local persistence and upgrades  
**Root cause:** Room holds both visible messages and pending sync state, but the builder enables destructive migration fallback.

**Evidence:** `AppDatabase.getInstance` registers migrations through version 8 and also calls `fallbackToDestructiveMigration()`.

**Failure scenario / current behavior:** An upgrade without a complete migration path can drop the local database, including pending messages whose only durable copy is Room.

**Expected behavior:** Preserve pending outbox rows across supported upgrades; fail explicitly or use a data-preserving migration for unsupported schema transitions.

**Production impact:** Offline or unsynced messages can be permanently lost during app upgrades.

## E. Hidden risks

- No HumanChat database-side trigger or transactional outbox was found. Migration 004 adds `messages` and `chat_sync_pipe` to `supabase_realtime`; the checked-in triggers relate to Friday or wishlist behavior.
- The schema snapshot does not establish a conversation/membership table or relational constraints. `conversation_id` is text and the Worker hardcodes `"human_chat"`.
- No HumanChat query indexes or table-creation migrations are present in the checked-in migration history. Index adequacy for sender/receiver filtering cannot be verified.
- The process-lifetime subscription is not explicitly paused when the app backgrounds. Recovery relies on client-library reconnection and subsequent resume/setup fetches; there is no reconciliation on every confirmed reconnect.
- FCM token registration exists, but checked-in notification SQL concerns wishlist notifications. No HumanChat push delivery path was found.
- The test-injection route inserts a local message as the other participant, but the Worker later sends using the currently logged-in persona; it is not a valid remote-sender simulation.
- The checked-in Realtime publication includes `messages` and `chat_sync_pipe`, but publication membership alone does not establish that RLS, replica identity, or recipient filtering are correctly configured in production.

## F. Broken or conflicting logic

- The message table is treated as durable state, but live delivery is driven by a separate pipe payload. Those writes can succeed or fail independently, without a revision-based reconciliation rule.
- The passkey flow obtains a persona JWT, but the shared Supabase client does not apply it. SQL snapshots/migrations describe multiple authorization models, and HumanChat message policies are not reproducibly defined in migrations.
- UI connection status is sourced from the global pipe subscription, not the conversation broadcast subscription; a timer can force the state to “connected.”
- Local deletion, global deletion, and “delete for me” share one tombstone state and sync path.
- DTO/model/UI support delivery and read metadata, but the HumanChat event handler discards read receipts and does not persist status transitions.
- Optimistic Room state is immediately displayed, but the Worker’s later full-row acknowledgement can overwrite newer Room state.

## G. Source-of-truth analysis

| State | Effective owner | Coherence |
|---|---|---|
| Visible message list | Room | Fast and reactive, but may diverge from server |
| Persisted HumanChat message | Supabase `messages`, if the write succeeds | Intended authority; no consistent reconciliation confirms it |
| Live delivery notification | `chat_sync_pipe` | Independent durable path with duplicate, cleanup, and failure modes |
| Message identity | Client-generated UUID | Stable ID helps deduplication; no version identity for edits |
| Message order | Device timestamp copied to server and Room | Not authoritative across devices |
| Delivery/read status | Room metadata and ephemeral broadcasts | No consistent authoritative transition |
| Typing state | Realtime broadcast | Ephemeral and not wired from text input |
| Identity/authorization | DataStore persona and separately saved JWT | Identity is not coherently applied to the Supabase client |

There is no reliable general reconciliation strategy. Fetches can replace local rows, pipe events can replace the same row, and Worker acknowledgements can overwrite newer edits.

## H. Reliability analysis

| Concern | Assessment |
|---|---|
| Delivery reliability | Best-effort. WorkManager retries, but message persistence and pipe delivery are separate writes. |
| Ordering | Not guaranteed end to end; client clocks and unversioned updates can disagree. |
| Deduplication | UUID and Room primary key help with duplicate message rows, but not duplicate pipe processing or side effects. |
| Reconnection | Partial catch-up on resume and initial subscription; no explicit reconciliation after every reconnect. |
| Lifecycle | Process-scoped subscription outlives screens; background/network suspension is not explicitly managed. |
| Concurrency | Unsafe when an edit races the Worker’s snapshot acknowledgement. |
| Failure recovery | Pending outbound work can retry, but partial success is opaque and inbound recovery depends on later fetches. |
| State consistency | Weak: Room, message rows, pipe payloads, and status can conflict. |

## I. Security analysis

User isolation is **not established by the checked-in implementation**. The client stores a persona JWT but does not configure the Supabase client to use it. The documented sync-pipe policy in migration 024 permits all operations without a row predicate. The message policy snapshot either describes broad public access or fixed persona IDs; neither demonstrates authorization tied to the authenticated caller and actual conversation membership. The fixed-persona insert policy, if active, still does not prove that the JWT subject matches `sender_id`.

Client update/delete operations filter by message ID and rely on RLS rather than including an ownership predicate. The checked-in artifacts do not define a consistent deployed policy for those operations. A channel topic or post-event recipient check is not authorization.

**Release blocker:** Verify deployed policies and Realtime exposure directly, then align them with the client’s actual JWT behavior and a server-enforced conversation membership model.

## J. Production readiness

**Not production-ready.** The issue is not simply whether basic Realtime send/receive works. Authorization, durable state, and recovery do not share one contract. Concrete failure paths include lost concurrent edits, wrong-persona sync, message resurrection, duplicate pipe rows, and stale status. The repository also cannot establish the deployed HumanChat schema or policies. Happy-path testing does not cover these risks.

## K. Recommended remediation strategy

### Must Fix Before Production

1. Establish one verifiable auth path: ensure the Supabase client sends the intended persona JWT and enforce server-side policies binding reads/writes/pipe access to authenticated identity and conversation membership. Remove unrestricted pipe access and inspect deployed policies.
2. Make the HumanChat schema reproducible with ordered migrations matching the Kotlin DTO, including constraints and indexes. Compare deployed schema to migrations.
3. Correct the sync contract: persist immutable owner/sender/recipient identity on queued messages; prevent stale acknowledgements from overwriting newer local state; handle partial message/pipe success idempotently.
4. Reconcile deletion state from server fetches and distinguish local-only from global deletion, with server-side authorization.
5. Provide reliable paginated history reconciliation; do not rely on pipe events as the only live message notification.

### Should Fix

- Tie conversation subscriptions to a lifecycle owner, cancel/unsubscribe on termination, expose accurate per-channel status, and reconcile after reconnect.
- Complete typing/read-receipt flows end to end or remove UI claims that imply they are implemented.
- Define and persist `SENT`, `DELIVERED`, and `READ` semantics from an authoritative source.
- Replace device-clock ordering with a server-owned ordering key and bounded pagination.
- Surface failed sends and partial success with message IDs and stage-specific errors.
- Avoid destructive Room fallback where it can discard unsynced outbox data.

### Future Improvements

- Add tests for multiple devices, concurrency, account switching, offline/resume, duplicate pipe events, RLS rejection, and edits during sync.
- Add structured correlation across local message ID, PostgREST write, pipe event, and receiver persistence.
- Monitor subscription state, pending-outbox age, repeated Worker retries, and unprocessed pipe backlog.
