# Phase 0 — Application-Wide Identity, Authentication & Authorization Audit

**Audit Date:** October 2026  
**Scope:** Repository-wide identity lifecycle, Jetpack Compose UI, ViewModels, Repositories, DataStore & SharedPreferences storage, Room database persistence, WorkManager background tasks, Supabase Client configuration (`supabase-kt`), PostgREST, Realtime subscriptions, Edge Functions, SQL migrations, and RLS policies across all application features (HumanChat, Friday AI, Wishlist, Profile & Settings, Notifications, PhestyDrop).  
**Status:** Audit Completed — Architectural Mapping & Evidence Verification (No application code or database objects modified).

---

## A. Executive Summary

The **Baroness** application currently operates on **client-side identity assertion**, not **server-authenticated authorization**. 

While the authentication flow (`AuthManager` + `verify-passkey` Edge Function) successfully verifies user passkeys and issues a signed, 1-year HS256 JWT containing a `persona` claim (e.g., `phesty_official` or `baroness_official`), **that JWT is discarded immediately after being saved to DataStore**. The shared `SupabaseClient` (`SupabaseConfig.kt`) is constructed as a lazy singleton using only the static `SUPABASE_ANON_KEY`. It does not install the Supabase Auth plugin, nor does it dynamically inject the stored JWT into outgoing PostgREST, Realtime, or Storage requests. All network calls execute as an unauthenticated `anon` user.

This architectural disconnect caused Supabase Row-Level Security (RLS) policies enforcing JWT persona claims (Migration 023) to reject all client requests. To restore application functionality, Migration 024 reverted RLS policies back to public permissive access (`FOR ALL USING (true) WITH CHECK (true)`) across `profiles`, `friday_messages`, `friday_memories`, `chat_sync_pipe`, and wishlist tables. Consequently, any anonymous client with the project's public anon key can read, modify, or delete any user's profile, messages, AI memories, sync pipe events, or wishlist items.

Furthermore, local state is dangerously unpartitioned. Room persistence (`AppDatabase` v8) stores all messages, wishlist items, and sync queues in a single SQLite database without persona ownership constraints or account isolation. Logging out clears local preferences but leaves all pending outbox messages, sync queue items, and cached rows intact in SQLite. If a user logs out and a different persona logs in, background WorkManager workers (`ChatSyncWorker` and `SyncWorker`) execute pending operations from the previous user under the newly logged-in persona's identity.

Enforcing JWT-backed authentication is required to secure the platform, but doing so tomorrow without client-side propagation updates would immediately break every server-backed feature in the application.

---

## B. Current Identity Architecture

### 1. Identity Source & Credential Verification
Identity originates at the `GateScreen` where the user selects a target persona (`phesty` or `baroness`) and enters a 6+ character passkey. `GateViewModel` delegates to `AuthManager.checkGate()`, which sends an anonymous HTTP POST request to the `verify-passkey` Supabase Edge Function (`/functions/v1/verify-passkey`).

`verify-passkey` uses the privileged `SUPABASE_SERVICE_ROLE_KEY` to query `public.access_keys`. If the passkey matches, it signs an HS256 JWT containing:
```json
{
  "role": "authenticated",
  "persona": "phesty_official",
  "sub": "phesty_official",
  "iat": 1777823760,
  "exp": 1809359760
}
```
The function returns this JWT along with `currentPersonaId` and `userProfile`.

### 2. Identity & Token Storage
Upon receiving `verify-passkey`'s success payload:
- `AuthManager` saves `token` into DataStore (`baroness_prefs` key `"auth_token"`).
- `GateViewModel` saves `vibe_persona`, `currentPersonaId`, and `userProfile` (JSON string) into DataStore (`baroness_prefs`).
- `GateViewModel` also calls `UserSessionManager(context).setUser(currentPersonaId)`, which writes `current_user_id` and `current_user_key` into **SharedPreferences** (a separate storage engine also named `"baroness_prefs"`).

### 3. Supabase Client Configuration & Propagation Gap
The application maintains a single lazy singleton `SupabaseClient` in `SupabaseConfig.kt`:
```kotlin
val supabase: SupabaseClient by lazy {
    createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}
```
- **Auth Plugin Missing:** `install(Auth)` is omitted.
- **No Bearer Token Injection:** The stored `"auth_token"` is never read or set as an Authorization header on `SupabaseClient`, PostgREST requests, or OkHttpClient builders in `ProfileManager` or `ChatApi`.
- **Result:** Every API call to Supabase carries `Authorization: Bearer SUPABASE_ANON_KEY`.

### 4. Database & RLS Authorization Model
- **Repository Migrations vs Production State:** Migration 023 attempted to enforce `USING (owner_id = (auth.jwt() ->> 'persona'))`. Because the Kotlin client sent requests as `anon`, all PostgREST writes failed. Migration 024 subsequently added permissive `USING (true) WITH CHECK (true)` policies for `profiles`, `friday_messages`, `friday_memories`, and `chat_sync_pipe`.
- **Current Deployed Access:** Deployed RLS policies grant unrestricted read/write/delete access to any caller holding the public anon key for all core tables except `access_keys` (which was locked down in Migration 026).

### 5. Local Persistence & WorkManager Lifecycle
- **Room Database (`AppDatabase` v8):** Unpartitioned. `MessageEntity`, `WishEntity`, `SyncQueueItem`, `RatingEntity`, and `ReactionEntity` exist in a single SQLite file.
- **Background Workers (`ChatSyncWorker` & `SyncWorker`):** WorkManager enqueues outbox tasks (`ChatSync`). When triggered, `ChatSyncWorker` queries `MessageDao.getPendingMessages()`, reads DataStore's `currentPersonaId` at *execution time*, and overwrites the payload's `senderId`/`ownerId` with whatever persona is currently logged in.

---

## C. Identity Flow Diagram

```text
[ GateScreen / User Input ]
        │ (persona = "phesty", passkey = "******")
        ▼
[ GateViewModel ]
        │
        ▼
[ AuthManager ]
        │ (POST /functions/v1/verify-passkey with SUPABASE_ANON_KEY)
        ▼
[ Edge Function: verify-passkey ]
        │ (Validates against access_keys using SERVICE_ROLE_KEY)
        │ (Mints HS256 JWT with claim: persona = "phesty_official")
        ▼
[ AuthManager Response Handling ]
        ├──> Save JWT token ───────> DataStore ("auth_token")  ──┐
        ├──> Save currentPersonaId ─> DataStore ("currentPersonaId")│
        └──> Save UserId ──────────> SharedPreferences ("current_user_id")
                                                                 │
      ┌──────────────────────────────────────────────────────────┘
      │  CRITICAL PROPAGATION BREAK:
      │  DataStore "auth_token" is NEVER applied to SupabaseClient!
      ▼
[ SupabaseConfig.supabase (Singleton) ]
      │ (Built with SUPABASE_ANON_KEY; Auth plugin NOT installed)
      │
      ├─── PostgREST Queries ──────> [ Supabase DB (Role: anon) ]
      │                              └─ Requires Permissive RLS (Migration 024)
      │
      ├─── Realtime WebSockets ────> [ Supabase Realtime (Role: anon) ]
      │                              └─ Relies on client-side Kotlin filtering
      │
      └─── Profile/Storage HTTP ───> [ Storage / REST (Role: anon) ]
                                     └─ Direct OkHttp calls with anon Bearer token

[ Background WorkManager (ChatSyncWorker) ]
      │
      ├─── Reads pending rows from unpartitioned Room SQLite DB
      ├─── Reads DataStore "currentPersonaId" at EXECUTION TIME
      └─── Sends pending rows under currently active persona identity
```

---

## D. Application-Wide Identity Consumers

| Feature / System | Identity Source | Auth Mechanism | Authorization Mechanism | Backend Dependency | Logout Behaviour | Identified Risk |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **HumanChat (Messages)** | DataStore `"currentPersonaId"` | None (`anon` key) | Client-side recipient filter | PostgREST `messages` + `chat_sync_pipe` | Room DB NOT cleared; Outbox remains pending | **HIGH**: Outbox cross-contamination on account switch; spoofable sender IDs |
| **HumanChat (Realtime Pipe)** | DataStore `"currentPersonaId"` | None (`anon` key) | Topic string + post-receipt Kotlin check | Supabase Realtime (`chat_sync_pipe`) | Subscription cancelled on persona null; channel unauthenticated | **HIGH**: Public Realtime topic allows eavesdropping on sync payloads |
| **Friday AI Chat** | DataStore `"currentPersonaId"` | None (`anon` key) | Permissive RLS (`USING (true)`) | PostgREST `friday_messages` + Webhooks | History remains in Room; DataStore key removed | **HIGH**: Private AI chats readable by any client specifying `owner_id` |
| **Friday Pending Messages** | Query Param `user_id` | None (`anon` key) | None (Service role bypass) | Edge Function `friday-pending-messages` | None | **CRITICAL**: Unauthenticated HTTP GET returns full chat history for any `user_id` |
| **Wishlist Items & Reactions** | `creator_id` / DataStore string | None (`anon` key) | Permissive RLS (`USING (true)`) | PostgREST `wishlist_items`, `reactions`, `ratings` | Room DB NOT cleared; `SyncQueue` remains pending | **HIGH**: Any client can modify, delete, or impersonate wish ratings/reactions |
| **Profile Setup & Avatars** | DataStore `"currentPersonaId"` | None (`anon` key) | Permissive RLS (`USING (true)`) | PostgREST `profiles` + Storage `avatars` bucket | Clears FCM token remotely; removes local DataStore keys | **HIGH**: Unauthenticated PATCH can hijack FCM token or avatar URL for any persona |
| **FCM Push Notifications** | DataStore `"fcm_token"` | None (`anon` key) | Service Role in `notify-trigger` | Edge Function `notify-trigger` | Sends empty FCM token update to Supabase | **MEDIUM**: Logout clears token, but offline logout leaves token attached on server |
| **PhestyDrop Releases** | None | None (`anon` key) | Public SELECT policy | PostgREST `phestydrop_releases` | N/A (App-level update checker) | **NONE**: Intended public read-only manifest |
| **Settings & Preferences** | DataStore / SharedPreferences | Local | Local | None | DataStore keys cleared | **LOW**: Dual storage engines (`DataStore` vs `SharedPreferences`) cause desync |

---

## E. Supabase Reality vs Repository

| Area | Repository Migration State | Deployed / Actual DB State | Match? | Risk |
| :--- | :--- | :--- | :--- | :--- |
| **`access_keys` Table RLS** | Migration 026 drops public SELECT and enables RLS. | RLS Enabled; Public SELECT revoked; Service role only. | **YES** | **NONE**: Correctly locked down to `verify-passkey` Edge Function. |
| **`profiles` Table RLS** | Migration 024 sets `FOR ALL USING (true) WITH CHECK (true)`. | Public Permissive RLS active. | **YES** | **CRITICAL**: Any user can modify or overwrite any user's profile and FCM token. |
| **`messages` Table Schema** | Snapshot omits `reply_to_id`, `delivered_at`, `is_pinned`. | Deployed DB includes metadata columns (`reply_to_id`, etc.). | **NO** | **HIGH**: Schema drift; repository DDL is incomplete and non-reproducible. |
| **`messages` Table RLS** | Snapshot lists fixed-persona policies (`sender_id = ANY (...)`). | Public Permissive or fixed-persona array policy active. | **UNCERTAIN** | **HIGH**: Unauthenticated writes permitted if fixed persona array is satisfied. |
| **`chat_sync_pipe` RLS** | Migration 024 sets `FOR ALL USING (true) WITH CHECK (true)`. | Public Permissive RLS active. | **YES** | **CRITICAL**: Eavesdropping or deleting pipe items for arbitrary recipients. |
| **`friday_messages` RLS** | Migration 024 sets `FOR ALL USING (true) WITH CHECK (true)`. | Public Permissive RLS active. | **YES** | **HIGH**: AI message history accessible to any client. |
| **`friday_memories` RLS** | Migration 024 sets `FOR ALL USING (true) WITH CHECK (true)`. | Public Permissive RLS active. | **YES** | **HIGH**: Vector embeddings and personal memories publicly readable. |
| **`wishlist_*` Tables RLS** | `schema.sql` describes permissive `wishlist_*` policies. | Public Permissive RLS active. | **YES** | **HIGH**: Unrestricted mutation of wishlist data across all personas. |
| **`friday-pending-messages`** | Edge function code accepts `user_id` query param. | Deployed Edge Function uses Service Role Key without auth. | **YES** | **CRITICAL**: HTTP endpoint exposes user chat history without JWT validation. |

---

## F. Critical Findings

### Finding 1: Disconnected JWT Auth Flow (Authentication / Client Architecture)
- **Severity:** CRITICAL
- **Area:** `AuthManager.kt`, `SupabaseConfig.kt`, `GateViewModel.kt`
- **Root Cause:** `AuthManager` successfully obtains a JWT from `verify-passkey` and writes it to DataStore (`"auth_token"`). However, `SupabaseConfig.supabase` is initialized lazy with `SUPABASE_ANON_KEY` and never receives the JWT. The Supabase Auth plugin is not installed, and no OkHttp interceptor applies the Bearer token.
- **Evidence:** 
  - `SupabaseConfig.kt` lines 12–18: `createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY)`
  - `AuthManager.kt` line 68: `storageManager.saveString("auth_token", token)`
  - Zero usages of `"auth_token"` exist in any networking or repository file.
- **Current Behaviour:** All PostgREST, Realtime, and Storage requests are transmitted anonymously.
- **Expected Behaviour:** `SupabaseClient` should manage user sessions via Supabase Auth or dynamically inject the valid persona JWT into all HTTP/WebSocket headers.
- **Affected Systems:** Entire backend integration (PostgREST, Realtime, Storage, Edge Functions).
- **Production Impact:** Application cannot enforce user-level RLS without breaking all network requests.

---

### Finding 2: Unauthenticated Access to Private User Data via Edge Function
- **Severity:** CRITICAL
- **Area:** Edge Function `supabase/functions/friday-pending-messages/index.ts`
- **Root Cause:** The function extracts `user_id` from the HTTP query string and executes a database query using `SUPABASE_SERVICE_ROLE_KEY`, completely bypassing RLS without validating the caller's JWT or Authorization header.
- **Evidence:** `friday-pending-messages/index.ts` lines 6–18:
  ```typescript
  const userId = url.searchParams.get("user_id");
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
  const { data: messages } = await supabase.from("friday_messages").select("*").eq("owner_id", userId)...
  ```
- **Current Behaviour:** Any HTTP client can issue `GET /functions/v1/friday-pending-messages?user_id=phesty_official&since=1970-01-01` and receive all private messages for `phesty_official`.
- **Expected Behaviour:** The function must parse the incoming `Authorization: Bearer <JWT>` header, verify the token signature, and ensure `jwt.persona === userId`.
- **Affected Systems:** Friday AI Chat, Privacy & Data Confidentiality.
- **Production Impact:** Direct data leak vulnerability exposing AI chat conversations.

---

### Finding 3: Permissive RLS Policies on Production Database (Authorization Boundary)
- **Severity:** CRITICAL
- **Area:** SQL Migrations `023_friday_v1_rls_jwt_policies.sql` & `024_friday_v1_app_compat_rls.sql`
- **Root Cause:** Migration 023 introduced JWT-based RLS (`USING (owner_id = (auth.jwt() ->> 'persona'))`). Because the Kotlin app sent requests anonymously, Migration 024 reverted policies on `profiles`, `friday_messages`, `friday_memories`, and `chat_sync_pipe` to `FOR ALL USING (true) WITH CHECK (true)`.
- **Evidence:** `024_friday_v1_app_compat_rls.sql` lines 18, 25, 34, 41:
  ```sql
  CREATE POLICY "Allow public access to profiles" ON public.profiles FOR ALL USING (true) WITH CHECK (true);
  CREATE POLICY "Allow public access to sync pipe" ON public.chat_sync_pipe FOR ALL USING (true) WITH CHECK (true);
  ```
- **Current Behaviour:** Database tables lack row-level security. Any client with the public anon key can read or mutate any record.
- **Expected Behaviour:** RLS must strictly restrict SELECT, INSERT, UPDATE, and DELETE operations based on the authenticated JWT claim `(auth.jwt() ->> 'persona')`.
- **Affected Systems:** Profiles, HumanChat, Friday AI, Sync Pipe.
- **Production Impact:** Complete collapse of backend security boundaries; trivial identity spoofing and data tampering.

---

### Finding 4: Cross-Account Outbox Pollution during Logout & Persona Switching
- **Severity:** HIGH
- **Area:** WorkManager `ChatSyncWorker.kt`, `SyncManager.kt`, Room `AppDatabase.kt`
- **Root Cause:** The local Room SQLite database is unpartitioned and shared across all personas. Pending outbox rows (`MessageEntity` with `status = "PENDING"`, `SyncQueueItem`) survive logout. When `ChatSyncWorker` executes, it reads `storageManager.getString("currentPersonaId")` at runtime rather than preserving the immutable owner identity established when the message was drafted.
- **Evidence:** `ChatSyncWorker.kt` lines 131–139:
  ```kotlin
  val currentPersonaId = storageManager.getString("currentPersonaId") ?: return false
  val dto = MessageDto(id = message.id, senderId = currentPersonaId)
  ```
- **Failure Scenario:**
  1. Persona A drafts a message while offline (`status = "PENDING"` in Room).
  2. Persona A logs out. Room database is NOT cleared.
  3. Persona B logs in on the same device. DataStore `"currentPersonaId"` becomes `"baroness_official"`.
  4. Network reconnects. WorkManager executes `ChatSyncWorker`.
  5. `ChatSyncWorker` reads Persona A's pending message and publishes it to Supabase with `sender_id = "baroness_official"`.
- **Current Behaviour:** Persona A's offline message is sent to the backend under Persona B's name and account.
- **Expected Behaviour:** Pending outbox items must record an immutable `ownerPersonaId`. Workers must refuse to transmit messages if the active session does not match the item's owner. Local Room state must be cleared or strictly partitioned by persona on logout.
- **Affected Systems:** WorkManager, HumanChat, Room Persistence, Account Isolation.
- **Production Impact:** Cross-user message leak, misattributed communications, data corruption.

---

### Finding 5: Insecure Realtime Channel Architecture & Client-Side Filtering
- **Severity:** HIGH
- **Area:** `ChatRepository.kt` (`restartSyncPipeSubscription`, `subscribeToConversation`)
- **Root Cause:** Realtime subscriptions connect using the anon key to public table events (`chat_sync_pipe`). The server sends all row inserts to every connected websocket subscriber. The Android client relies on post-receipt Kotlin checks (`if (recipientId == personaId)`) to filter messages.
- **Evidence:** `ChatRepository.kt` lines 137–142:
  ```kotlin
  pipeFlow.collect { action ->
      if (action is PostgresAction.Insert) {
          val recipientId = action.record["recipient_id"]?.jsonPrimitive?.content
          if (recipientId == personaId) { handlePipeMessage(action.record) }
      }
  }
  ```
- **Current Behaviour:** A compromised or modified client can subscribe to `chat_sync_pipe` without filtering, receiving all real-time message payloads and command intents intended for other users.
- **Expected Behaviour:** Realtime RLS policies must filter Postgres change streams on the server before emitting events over websockets, ensuring clients only receive records where `recipient_id = (auth.jwt() ->> 'persona')`.
- **Affected Systems:** Realtime Sync Pipe, Message Delivery, Confidentiality.
- **Production Impact:** Eavesdropping on active real-time message traffic across all users.

---

### Finding 6: Dual Storage Engine Desynchronization (`DataStore` vs `SharedPreferences`)
- **Severity:** MEDIUM
- **Area:** `UserSessionManager.kt`, `StorageManager.kt`, `SessionManager.kt`
- **Root Cause:** The application utilizes two separate persistence engines with identical names:
  1. `StorageManager`: AndroidX DataStore (`PreferencesDataStore`) targeting file `baroness_prefs.preferences_pb`.
  2. `UserSessionManager`: Legacy Android `SharedPreferences` targeting file `baroness_prefs.xml`.
- **Evidence:**
  - `UserSessionManager.kt` line 9: `context.getSharedPreferences("baroness_prefs", Context.MODE_PRIVATE)`
  - `StorageManager.kt` line 10: `preferencesDataStore(name = "baroness_prefs")`
- **Current Behaviour:** `UserSessionManager` manages `currentUserId` in XML SharedPreferences, while `SessionManager`, `AuthManager`, and `StorageManager` manage `currentPersonaId`, `userProfile`, and `auth_token` in Protobuf DataStore. `SessionManager.isLoggedIn()` checks DataStore keys `userProfile` and `vibe_persona`, ignoring `UserSessionManager` state.
- **Expected Behaviour:** Identity management must use a single unified storage engine (DataStore) to prevent state fragmentation.
- **Affected Systems:** Application Startup, Navigation, Session Verification.
- **Production Impact:** Session state desynchronization, potential startup redirect loops or stale identity reads.

---

## G. Cross-Feature Dependencies

Modifying identity and enforcing mandatory JWT authentication will impact multiple application features. The table below outlines these cross-feature dependencies to prevent unexpected breakages during implementation:

```text
               ┌──────────────────────────────────────────────┐
               │  Mandatory Authenticated JWT (Supabase Auth) │
               └──────────────────────┬───────────────────────┘
                                      │
     ┌──────────────────┬─────────────┼──────────────┬──────────────────┐
     ▼                  ▼             ▼              ▼                  ▼
[ HumanChat ]      [ Friday AI ]  [ Wishlist ]  [ Profiles ]     [ Notifications ]
  Needs JWT on       Needs JWT      Needs JWT    Needs JWT on     Needs JWT on
  PostgREST &        on PostgREST   on PostgREST  PATCH/PUT        FCM Token
  Realtime Pipe      & Pending      & Reactions   & Storage        Registration
                     Endpoint                     Bucket
```

### Affected Feature Details:
1. **HumanChat:**
   - **PostgREST Writes:** `ChatApi.sendMessage` and `pushToSyncPipe` will fail if RLS enforces `sender_id = (auth.jwt() ->> 'persona')` while the client sends `anon`.
   - **Realtime Channels:** Sync pipe subscription (`chat_sync_pipe_$personaId`) will be rejected by Realtime RLS unless the connection handshake includes the JWT.
2. **Friday AI Chat:**
   - **Message History:** `ChatApi.fetchFridayMessages` filters by `owner_id`. Will fail under strict RLS unless `owner_id === auth.jwt().persona`.
   - **Pending Messages Endpoint:** Calling `friday-pending-messages` must transition from query params (`?user_id=...`) to an `Authorization: Bearer <JWT>` header.
3. **Wishlist Feature:**
   - **Items, Reactions & Ratings:** `WishlistApi` performs `insert`, `update`, `delete`, and `upsert` on `wishlist_items`, `wishlist_reactions`, and `wishlist_ratings`. Strict RLS will reject these unless `creator_id` or `persona_id` matches the authenticated JWT.
4. **Profile Management & Avatars:**
   - **Profile Patching:** `ProfileManager.upsertProfile` issues PUT requests to `/rest/v1/profiles?id=eq.$id`. Under JWT RLS, updating another persona's profile will be blocked.
   - **Storage Buckets:** Uploading avatars to Supabase Storage (`avatars` bucket) currently uses the anon key. Storage RLS must be updated to validate the persona JWT.
5. **Background Workers (WorkManager):**
   - **Task Context:** Workers execute in background threads where memory state may be cleared. Workers must read the persisted JWT from encrypted DataStore to attach valid headers to background sync requests.

---

## H. Security Findings

```text
[ ATTACK VECTOR SUMMARY ]

1. Identity Spoofing (PostgREST)
   Anon Client ───> POST /messages { "sender_id": "baroness_official" } ───> Accepted by DB (Permissive RLS)

2. Realtime Payload Eavesdropping
   Anon Client ───> Subscribe "chat_sync_pipe" ───> Receives ALL insert events for ALL users

3. Unauthenticated Data Exfiltration
   Anon Client ───> GET /functions/v1/friday-pending-messages?user_id=phesty ───> Returns full AI chat history

4. Profile & FCM Hijacking
   Anon Client ───> PATCH /profiles?id=eq.phesty_official { "fcm_token": "evil_token" } ───> Profile updated
```

1. **Client-Controlled Identity Fields:** All network requests accept sender, creator, and owner IDs from client-constructed DTOs without server verification against an authenticated token.
2. **Realtime Broadcast Exposure:** Eavesdropping on Realtime sync pipes is possible because subscription topics are unauthenticated strings.
3. **Storage Bucket World-Writeable:** The `avatars` storage bucket allows unauthenticated upload and deletion of image assets.
4. **Stale Session Token Retention:** JWTs issued by `verify-passkey` have a 1-year lifetime with no server-side revocation list or refresh mechanism.
5. **FCM Token Hijacking:** An anonymous user can overwrite the `fcm_token` column of any target profile in `public.profiles`, diverting push notifications to an attacker's device.

---

## I. Lifecycle Findings

1. **Application Startup:**
   - `SessionManager` checks if DataStore keys `"userProfile"` and `"vibe_persona"` are non-empty.
   - **Gap:** No server token validation occurs on launch. If the JWT stored in DataStore is invalid or revoked, the app still routes the user directly to the Dashboard.
2. **Foreground / Background Transitions:**
   - `ChatRepository` registers a `ProcessLifecycleOwner` observer. On `ON_RESUME`, it triggers `ChatSyncWorker` and calls `fetchOfflineMessages()`.
   - **Gap:** Re-fetching offline messages executes using the static anon client, regardless of session age.
3. **Logout Lifecycle:**
   - `ProfileSetupViewModel.onLogout()` clears local DataStore keys (`"vibe_persona"`, `"userProfile"`, `"currentPersonaId"`, `"auth_token"`, `"fcm_token"`) and SharedPreferences.
   - **Gaps:**
     - Room SQLite tables (`messages`, `wishlist_items`, `sync_queue`) are **not cleared**.
     - WorkManager tasks are **not cancelled**.
     - The singleton `SupabaseClient` instance is **not reset**.
     - The server-side JWT is **not invalidated**.
4. **Account / Persona Switching:**
   - Switching from Persona A to Persona B leaves Persona A's local SQLite database state intact.
   - WorkManager workers execute background sync tasks for Persona A's queued items using Persona B's session identity.

---

## J. Hidden Risks

1. **Room Database Destructive Fallback:**
   - `AppDatabase.getInstance()` includes `fallbackToDestructiveMigration()`.
   - If a database schema version increment occurs without an explicit migration script, Room wipes the entire local SQLite database, permanently deleting unsynced outbox messages.
2. **WorkManager Execution Timing & Race Conditions:**
   - `ChatSyncWorker` enqueues work using `ExistingWorkPolicy.REPLACE`.
   - Rapid consecutive sends can cancel an in-flight sync task, leading to duplicate pipe pushes or lost local status updates.
3. **Silent API Error Suppressions:**
   - `ChatApi` and `WishlistApi` catch generic `Exception` types and return `false` or `emptyList()`.
   - Network timeouts, 401 Unauthorized errors, and 403 Forbidden RLS rejections are swallowed, obscuring authentication failures in production logs.

---

## K. Unknowns / Verification Required

1. **Deployed Supabase Realtime Replication Settings:**
   - Unable to verify remote `REPLICA IDENTITY` settings on `messages` and `chat_sync_pipe` via local repository files.
2. **FCM Service Account Key Scope:**
   - Unable to confirm if `FCM_PRIVATE_KEY` stored in Supabase Edge Function secrets is restricted strictly to push notifications or possesses broader Google Cloud permissions.

---

## L. Proposed Target Architecture

```text
[ User Login / Passkey ]
        │
        ▼
[ AuthManager / verify-passkey ]
        │ Returns HS256 Persona JWT
        ▼
[ UserSessionRepository ]
        │ Securely stores JWT & Persona ID in Encrypted DataStore
        │ Updates StateFlow<SessionState> (LoggedOut -> Authenticated)
        ▼
[ SupabaseClientManager ]
        │ Dynamically configures SupabaseClient with Bearer <JWT>
        │ Installs Postgrest, Realtime, Storage, Auth
        ▼
┌──────────────────────────────────────────────────────────┐
│              AUTHENTICATED NETWORK BOUNDARY              │
├────────────────────────────┬─────────────────────────────┤
│ PostgREST Requests         │ Realtime Subscriptions      │
│ Headers: Bearer <JWT>      │ Connect Params: JWT         │
└─────────────┬──────────────┴──────────────┬──────────────┘
              │                             │
              ▼                             ▼
┌──────────────────────────────────────────────────────────┐
│              SERVER-ENFORCED RLS POLICIES                │
├──────────────────────────────────────────────────────────┤
│ profiles: USING (id = auth.jwt() ->> 'persona')         │
│ messages: USING (sender_id = auth.jwt() ->> 'persona')   │
│ sync_pipe: USING (recipient_id = auth.jwt() ->> 'persona')│
└──────────────────────────────────────────────────────────┘
```

1. **Single Source of Auth Truth:**
   - Replace fragmented DataStore/SharedPreferences logic with a single `UserSessionRepository` managing `SessionState` (`Unauthenticated` vs `Authenticated(personaId, jwt)`).
2. **Dynamic Supabase JWT Injection:**
   - Configure `SupabaseClient` with a custom HTTP client plugin or session manager that automatically attaches `Authorization: Bearer <JWT>` to every PostgREST, Realtime, and Storage request.
3. **Strict Server-Enforced RLS (Re-enabling Migration 023 Pattern):**
   - Reinstate RLS policies enforcing `auth.jwt() ->> 'persona'` across all database tables.
   - Remove all `FOR ALL USING (true)` permissive rules.
4. **Partitioned Local Persistence:**
   - Scope Room queries by `ownerPersonaId` or wipe local SQLite user tables on explicit logout.
5. **Secure Background Worker Execution:**
   - Pass immutable `ownerPersonaId` and persisted `jwt` into `WorkManager` input data to ensure background tasks execute under the correct account context.

---

## M. Phase 0 Implementation Plan

### Must Fix (P0 — Security & Correctness Blockers)
1. **Dynamic JWT Injection in Kotlin Client:**
   - Update `SupabaseConfig` / `SupabaseClient` to dynamically inject the stored DataStore `"auth_token"` into all PostgREST, Realtime, and Storage requests.
2. **Re-enable Strict RLS Policies in Supabase:**
   - Deploy a new migration replacing Migration 024's permissive policies with JWT-enforced policies (`(auth.jwt() ->> 'persona')`).
3. **Secure Edge Function `friday-pending-messages`:**
   - Update `friday-pending-messages/index.ts` to require and validate incoming `Authorization: Bearer <JWT>` headers.
4. **Room Database Persona Partitioning & Logout Cleanup:**
   - Add `ownerPersonaId` to local entities (`MessageEntity`, `SyncQueueItem`).
   - Clear user-specific Room tables upon explicit user logout in `ProfileSetupViewModel.onLogout()`.
5. **Fix WorkManager Identity Assignment:**
   - Update `ChatSyncWorker` to use the message's immutable `ownerPersonaId` rather than reading runtime DataStore state.

### Should Fix (P1 — Stability & Architecture)
1. **Unify Local Storage Engines:**
   - Migrate `UserSessionManager` from XML `SharedPreferences` to AndroidX DataStore.
2. **Realtime Auth Handshake:**
   - Update `ChatRepository` Realtime connection flows to supply JWT credentials during socket establishment.
3. **Structured API Error Handling:**
   - Refactor `ChatApi` and `WishlistApi` to log explicit HTTP status codes (e.g., 401 Unauthorized, 403 Forbidden) instead of returning empty fallback values.

### Later / Future Improvements (P2 — Maintenance)
1. **JWT Refresh & Expiration Lifecycle:**
   - Implement short-lived JWTs with automatic token refresh mechanisms.
2. **Database Schema & DDL Synchronization:**
   - Reconcile `supabase/schema.sql` with deployed production column definitions.

---

## 25. Final Audit Question

> **"If we make JWT-backed authenticated identity the mandatory source of truth for Supabase access tomorrow, exactly what breaks, what changes, what remains compatible, and what must be migrated first?"**

### What Breaks:
- **Every PostgREST network call in `ChatApi`, `WishlistApi`, and `ProfileManager`:** Returns HTTP 403 Forbidden / 401 Unauthorized because requests are currently transmitted using `SUPABASE_ANON_KEY` without a persona JWT.
- **Realtime Sync Pipe Subscriptions:** Disconnected or denied by Realtime RLS rules because websocket handshakes lack authenticated JWT claims.
- **Friday AI Pending Messages Endpoint:** Rejects calls unless updated to parse Authorization headers.

### What Changes:
- **`SupabaseConfig.kt`:** Must be refactored from a static anon singleton to a session-aware client that injects the active persona JWT.
- **Supabase RLS Policies:** Migration 024's permissive `USING (true)` rules must be removed and replaced with JWT-enforced rules (`auth.jwt() ->> 'persona'`).
- **`ProfileSetupViewModel.onLogout()`:** Must clear local Room database tables and cancel pending WorkManager jobs.
- **`ChatSyncWorker`:** Must read immutable message owner IDs instead of runtime DataStore state.

### What Remains Compatible:
- **Passkey Authentication Flow (`verify-passkey` Edge Function):** Already generates valid HS256 JWTs with required `persona` claims.
- **Domain Models & DTOs:** Kotlin DTOs (`MessageDto`, `WishDto`, `ProfileDto`) already include necessary identity fields.
- **PhestyDrop Manifest API:** Continues functioning via public read-only SELECT policies.

### What Must Be Migrated First:
1. **Step 1:** Update Kotlin client code to attach `"auth_token"` to all Supabase HTTP and WebSocket requests.
2. **Step 2:** Update Edge Functions to validate incoming JWTs.
3. **Step 3:** Deploy Supabase database migrations enforcing JWT-based RLS policies.
4. **Step 4:** Deploy Room database partitioning and logout teardown logic in the Android application.
