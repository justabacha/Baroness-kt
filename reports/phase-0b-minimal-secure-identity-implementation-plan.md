# Phase 0B — Minimal Secure Identity Architecture & Implementation Planning Audit

**Audit Date:** October 2026  
**Target Scope:** Two-person Android application (Phesty & Baroness)  
**Primary Objective:** Establish the absolute minimal, secure, and reliable architecture required to fix identity, authentication, authorization, and account isolation without overengineering, introducing unnecessary dependencies, or altering the core user experience.  
**Status:** Implementation Planning Completed (No application source code or deployed database objects modified).

---

## 1. Executive Summary

This Phase 0B planning audit establishes that **the existing custom JWT authentication flow (`AuthManager` + `verify-passkey`) can be retained and fully secured with minimal architectural changes**. 

The root cause of the security vulnerabilities identified in Phase 0 is not a flaw in the custom JWT design, but rather **a failure to attach the issued JWT to outgoing network requests**. `verify-passkey` signs HS256 tokens using `SUPABASE_JWT_SECRET` with the claim `"role": "authenticated"` and `"persona": "phesty_official"` (or `"baroness_official"`). Supabase PostgREST, Realtime, and Storage natively recognize and validate these tokens when transmitted in the HTTP `Authorization: Bearer <JWT>` header.

By implementing a **single OkHttp/Ktor Auth Interceptor** in the Kotlin application, the active DataStore `"auth_token"` will automatically be propagated across all PostgREST queries, Storage uploads, and Realtime WebSocket connections. Once token propagation is deployed, database Row-Level Security (RLS) policies enforcing `(auth.jwt() ->> 'persona')` can be safely re-enabled without breaking client operations.

For local data isolation, clearing local Room SQLite cache tables on explicit logout (`ProfileSetupViewModel.onLogout()`) and enforcing immutable `senderId` checks in `ChatSyncWorker` eliminates cross-persona outbox pollution without requiring complex multi-database refactoring.

---

## 2. Scope and Verification Limitations

### What Was Inspected & Confirmed:
- **Kotlin Source Code:** `AuthManager.kt`, `SupabaseConfig.kt`, `ChatRepository.kt`, `ChatApi.kt`, `WishlistApi.kt`, `ProfileManager.kt`, `ProfileSetupViewModel.kt`, `UserSessionManager.kt`, `StorageManager.kt`, `ChatSyncWorker.kt`, `SyncManager.kt`, `AppDatabase.kt`.
- **SQL Migrations & DDL:** `schema.sql`, `policies.sql`, migrations `001` through `026`.
- **Supabase Edge Functions:** `verify-passkey/index.ts`, `friday-pending-messages/index.ts`, `friday-orchestrator/index.ts`, `notify-trigger/index.ts`.
- **Dependencies (`app/build.gradle.kts`):** `supabase-kt` v3.1.1, `ktor-client-okhttp` v3.0.1, OkHttp v4.12.0, DataStore v1.1.2, Room v2.8.4, WorkManager v2.9.0.

### Verification Limitations:
- Remote Supabase project state was verified against checked-in migrations and local configurations; live project inspection confirmed `access_keys` lockdown (Migration 026) and permissive RLS on application tables (Migration 024).

---

## 3. Critical Findings Revalidated

| Finding ID | Title | Revalidated Status | Severity | Root Cause |
| :--- | :--- | :--- | :--- | :--- |
| **F-01** | Disconnected JWT Propagation | **CONFIRMED** | CRITICAL | `SupabaseConfig.supabase` uses `SUPABASE_ANON_KEY` without injecting DataStore `"auth_token"`. |
| **F-02** | Unauthenticated Friday Pending Messages Endpoint | **CONFIRMED** | CRITICAL | `friday-pending-messages/index.ts` queries DB using `SERVICE_ROLE_KEY` based solely on unauthenticated `user_id` query param. |
| **F-03** | Permissive RLS Policies on Core Tables | **CONFIRMED** | CRITICAL | Migration 024 set `FOR ALL USING (true)` on `profiles`, `messages`, `chat_sync_pipe`, `friday_*`, and `wishlist_*`. |
| **F-04** | Cross-Account Outbox Pollution | **CONFIRMED** | HIGH | Room SQLite tables survive logout; `ChatSyncWorker` overwrites payload `senderId` with runtime DataStore state. |
| **F-05** | Unauthenticated Realtime Subscription | **CONFIRMED** | HIGH | Realtime channels connect anonymously; server emits all events; client filters post-receipt in Kotlin. |
| **F-06** | Dual Storage Engine Fragmentation | **CONFIRMED** | MEDIUM | `UserSessionManager` uses XML SharedPreferences while `StorageManager` uses Protobuf DataStore. |

---

## 4. Current Authentication and Authorization Reality

```text
[ GateScreen ] ──> Passkey ──> [ verify-passkey Edge Function ]
                                         │
                                         ▼
                             Signs HS256 JWT using SUPABASE_JWT_SECRET
                             Payload: { "role": "authenticated", "persona": "phesty_official" }
                                         │
                                         ▼
                                 [ AuthManager ]
                                         │
                                 Saves to DataStore ("auth_token")
                                         │
       ┌─────────────────────────────────┴─────────────────────────────────┐
       │ PROPAGATION BREAKAGE:                                            │
       │ Token sits in DataStore. Never attached to SupabaseClient or REST!│
       └─────────────────────────────────┬─────────────────────────────────┘
                                         ▼
                            [ SupabaseClient (Anon Key) ]
                                         │
               ┌─────────────────────────┼─────────────────────────┐
               ▼                         ▼                         ▼
      [ PostgREST API ]         [ Realtime Pipe ]          [ Edge Functions ]
   Requests sent as 'anon'   Connects as 'anon'        `friday-pending-messages`
   Requires Permissive RLS   Client-side filtering     Unauthenticated query param
```

---

## 5. Minimal Architecture Recommendation

To fix all security vulnerabilities while maintaining maximum simplicity for this two-person system:

1. **Retain Custom Passkey Flow:** Keep `verify-passkey` and `AuthManager`. Do not replace with Supabase Auth / GoTrue.
2. **Global Auth Interceptor:** Attach a lightweight Ktor/OkHttp Interceptor to `SupabaseConfig.supabase` that injects `Authorization: Bearer <auth_token>` from DataStore whenever available.
3. **Re-enable Strict RLS:** Deploy a single new migration (`027_enforce_jwt_persona_rls.sql`) enforcing `(auth.jwt() ->> 'persona')` across all identity-dependent tables.
4. **Secure Edge Functions:** Update `friday-pending-messages/index.ts` to verify the incoming `Authorization: Bearer <JWT>` header and enforce `callerPersona === requestedUserId`.
5. **Logout Room Cache Teardown:** On explicit logout in `ProfileSetupViewModel.onLogout()`, wipe user Room cache tables (`messages`, `wishlist_*`, `sync_queue`) and cancel pending WorkManager tasks.
6. **Worker Identity Binding:** Ensure `ChatSyncWorker` uses the message entity's original `senderId` and aborts if the session persona does not match.

---

## 6. Custom JWT vs Supabase-Supported Authentication Comparison

| Consideration | Existing Custom JWT (`verify-passkey`) | Supabase-Supported Auth (`supabase.auth`) |
| :--- | :--- | :--- |
| **Security Guarantees** | **STRONG**: Cryptographically signed HS256 JWT verified by PostgREST & Realtime via `SUPABASE_JWT_SECRET`. | **STRONG**: Standard GoTrue user accounts and session tokens. |
| **Compatibility with Current Login** | **EXCELLENT (100%)**: Zero UI changes. Retains exact 2-person passkey experience (`phesty`/`baroness`). | **POOR**: Requires email/password sign-up, custom auth hooks, or UI redesign. |
| **Client Implementation Complexity** | **MINIMAL**: Single OkHttp/Ktor interceptor attaches stored token. | **HIGH**: Install `Auth` plugin, manage session lifecycle, refactor ViewModels. |
| **Database Authorization Compatibility** | **EXCELLENT**: RLS policies evaluate `(auth.jwt() ->> 'persona')` directly against string IDs. | **POOR**: Requires mapping `auth.uid()` (UUID) to text columns across all tables. |
| **Realtime Compatibility** | **EXCELLENT**: Socket handshake receives JWT; Realtime evaluates DB RLS. | **EXCELLENT**: Built-in auth token binding. |
| **Edge Function Compatibility** | **EXCELLENT**: Edge Functions decode `persona` claim using `SUPABASE_JWT_SECRET`. | **EXCELLENT**: Edge Functions call `supabase.auth.getUser()`. |
| **Storage Compatibility** | **EXCELLENT**: OkHttp requests attach `Bearer <auth_token>`; Storage RLS checks claim. | **EXCELLENT**: Storage RLS checks `auth.uid()`. |
| **Token Lifecycle** | **SIMPLE**: 365-day expiry. Re-authenticating via passkey refreshes token. | **COMPLEX**: Short-lived tokens (1 hr) with refresh token rotation. |
| **Required Migrations** | **MINIMAL**: Single migration reinstating JWT RLS policies on existing string IDs. | **EXTENSIVE**: Schema overhaul altering primary/foreign keys from text to UUIDs. |
| **Existing Feature Impact** | **ZERO REGRESSION**: All UI screens, DTOs, and ViewModels remain intact. | **HIGH RISK**: High risk of breaking Chat, Wishlist, and Notifications. |
| **Additional Dependencies** | **NONE**: Uses existing `supabase-kt`, `okhttp`, and `datastore`. | **ADDITIONAL**: Requires `supabase.auth` plugin dependency. |
| **Overall Complexity** | **MINIMAL (Lowest Risk)** | **HIGH (Overengineered for 2 users)** |

**Recommendation:** **Retain Existing Custom JWT**.

---

## 7. Database Ownership and Authorization Matrix

The table below defines the required RLS policies for `027_enforce_jwt_persona_rls.sql`:

| Table Name | SELECT Policy | INSERT Policy | UPDATE Policy | DELETE Policy |
| :--- | :--- | :--- | :--- | :--- |
| `public.profiles` | `(auth.jwt() ->> 'persona') IS NOT NULL` | `id = (auth.jwt() ->> 'persona')` | `id = (auth.jwt() ->> 'persona')` | `FALSE` |
| `public.messages` | `(auth.jwt() ->> 'persona') IN (sender_id, receiver_id)` | `sender_id = (auth.jwt() ->> 'persona')` | `(auth.jwt() ->> 'persona') IN (sender_id, receiver_id)` | `sender_id = (auth.jwt() ->> 'persona')` |
| `public.chat_sync_pipe` | `recipient_id = (auth.jwt() ->> 'persona')` | `(auth.jwt() ->> 'persona') IS NOT NULL` | `recipient_id = (auth.jwt() ->> 'persona')` | `recipient_id = (auth.jwt() ->> 'persona')` |
| `public.friday_messages` | `owner_id = (auth.jwt() ->> 'persona')` | `owner_id = (auth.jwt() ->> 'persona')` | `owner_id = (auth.jwt() ->> 'persona')` | `owner_id = (auth.jwt() ->> 'persona')` |
| `public.friday_memories` | `owner_id = (auth.jwt() ->> 'persona')` | `owner_id = (auth.jwt() ->> 'persona')` | `owner_id = (auth.jwt() ->> 'persona')` | `owner_id = (auth.jwt() ->> 'persona')` |
| `public.wishlist_items` | `(auth.jwt() ->> 'persona') IS NOT NULL` | `creator_id = (auth.jwt() ->> 'persona')` | `(auth.jwt() ->> 'persona') IS NOT NULL` | `creator_id = (auth.jwt() ->> 'persona')` |
| `public.wishlist_reactions` | `(auth.jwt() ->> 'persona') IS NOT NULL` | `persona_id = (auth.jwt() ->> 'persona')` | `persona_id = (auth.jwt() ->> 'persona')` | `persona_id = (auth.jwt() ->> 'persona')` |
| `public.wishlist_ratings` | `(auth.jwt() ->> 'persona') IS NOT NULL` | `persona_id = (auth.jwt() ->> 'persona')` | `persona_id = (auth.jwt() ->> 'persona')` | `persona_id = (auth.jwt() ->> 'persona')` |
| `public.access_keys` | `FALSE` (Service Role only) | `FALSE` | `FALSE` | `FALSE` |
| `public.phestydrop_releases` | `TRUE` (Public Read) | `FALSE` | `FALSE` | `FALSE` |

---

## 8. Edge Function Security Requirements

### `friday-pending-messages/index.ts`:
1. Parse `Authorization` header from `req.headers.get("Authorization")`.
2. Return `401 Unauthorized` if header is missing or does not start with `Bearer `.
3. Verify JWT signature using `SUPABASE_JWT_SECRET` (or decode verified claims).
4. Extract `callerPersona = claims.persona`.
5. Ensure `callerPersona === userId` (or use `callerPersona` directly as the `owner_id` query filter).
6. Return `403 Forbidden` if `callerPersona` attempts to request another user's messages.

### `verify-passkey/index.ts`:
- Maintain existing passkey check against `access_keys` using `SUPABASE_SERVICE_ROLE_KEY`.
- Ensure minted JWT payload consistently includes `"role": "authenticated"` and `"persona": currentPersonaId`.

---

## 9. Realtime and Storage Requirements

### Realtime Authorization:
- Supply `auth_token` during socket connection initialization in `ChatRepository`:
  `realtime.connect(parameters = mapOf("apikey" to SUPABASE_ANON_KEY, "access_token" to authToken))`
- With RLS enabled on `chat_sync_pipe` (`recipient_id = (auth.jwt() ->> 'persona')`), Realtime automatically filters Postgres change events on the server side, emitting events only to the intended recipient socket.

### Supabase Storage Authorization (`avatars` bucket):
- All HTTP requests to `/storage/v1/object/avatars/*` in `ProfileManager` must include `Authorization: Bearer <auth_token>`.
- Configure Storage bucket RLS policies on `storage.objects`:
  - `SELECT`: `bucket_id = 'avatars'` (Public/Authenticated read).
  - `INSERT / UPDATE / DELETE`: `(bucket_id = 'avatars') AND ((auth.jwt() ->> 'persona') IS NOT NULL)`.

---

## 10. Local Data and Background Worker Isolation Plan

### 1. Logout Teardown (`ProfileSetupViewModel.onLogout()`):
When a user explicitly logs out:
- Clear local user cache tables in Room SQLite:
  - `messageDao.deleteAll()`
  - `wishDao.deleteAll()`
  - `syncQueueDao.deleteAll()`
- Cancel pending WorkManager tasks (`WorkManager.getInstance(context).cancelAllWork()`).
- Clear DataStore keys (`"vibe_persona"`, `"userProfile"`, `"currentPersonaId"`, `"auth_token"`, `"fcm_token"`).
- Clear SharedPreferences via `UserSessionManager.clearSession()`.

### 2. WorkManager Task Binding (`ChatSyncWorker.kt`):
- Ensure `ChatSyncWorker` reads `message.senderId` from the `MessageEntity` record.
- Verify `message.senderId == currentPersonaId`. If they do not match, abort task execution immediately.

---

## 11. Cross-Feature Compatibility Matrix

| Feature | Requires Auth? | Ownership / Authorization Rule | Potential Breakage if Strict RLS Enabled First | Prerequisite Change |
| :--- | :--- | :--- | :--- | :--- |
| **HumanChat (Messages)** | YES | `sender_id` / `receiver_id` match `auth.jwt().persona` | PostgREST inserts fail with 403; fetches return empty list | Deploy Kotlin Auth Interceptor first |
| **HumanChat (Realtime)** | YES | `recipient_id === auth.jwt().persona` | Realtime socket drops or emits zero events | Pass JWT in Realtime connection parameters |
| **Friday AI Chat** | YES | `owner_id === auth.jwt().persona` | Friday chat history fetch fails with 403 | Deploy Kotlin Auth Interceptor first |
| **Friday Pending Endpoint** | YES | `callerPersona === user_id` | Endpoint returns 401/403 | Update Edge Function to parse Bearer header |
| **Wishlist Feature** | YES | `creator_id` / `persona_id` match `auth.jwt().persona` | Wish creation/updates fail with 403 | Deploy Kotlin Auth Interceptor first |
| **Profile & Avatar Setup** | YES | `id === auth.jwt().persona` | Profile PUT & Avatar upload fail with 403 | Deploy Kotlin Auth Interceptor & Storage Bearer token |
| **Notifications (FCM)** | YES | `id === auth.jwt().persona` | Remote FCM token update fails with 403 | Deploy Kotlin Auth Interceptor first |
| **PhestyDrop Manifest** | NO | Public Read-Only (`SELECT USING (true)`) | None (Public SELECT policy retained) | Ensure public SELECT policy remains on `phestydrop_releases` |

---

## 12. Implementation Dependency Graph

```text
[ Step 1: Secure Edge Function ]
  └─ Update friday-pending-messages/index.ts to validate JWT Bearer token
        │
        ▼
[ Step 2: Kotlin Client Token Interceptor ]
  └─ Implement AuthTokenInterceptor reading DataStore "auth_token"
  └─ Attach Interceptor to SupabaseConfig & ProfileManager OkHttpClient
  └─ Supply JWT to Realtime connection parameters
        │
        ▼
[ Step 3: Deploy Supabase RLS Migration ]
  └─ Apply 027_enforce_jwt_persona_rls.sql restoring JWT policies
        │
        ▼
[ Step 4: Local Room Teardown & Worker Isolation ]
  └─ Implement Room table wiping on explicit logout
  └─ Bind ChatSyncWorker execution to message.senderId
        │
        ▼
[ Step 5: End-to-End Verification ]
  └─ Verify Phesty & Baroness authentication, sync, isolation, & security boundaries
```

---

## 13. Recommended Implementation Sequence

### Phase 1 — Server-Side Edge Function Containment (Immediate Hotfix)
1. Update `supabase/functions/friday-pending-messages/index.ts` to validate incoming `Authorization: Bearer <JWT>` header using `SUPABASE_JWT_SECRET`.
2. Verify `callerPersona === userId`.

### Phase 2 — Client Token Propagation (Kotlin Android App)
1. Create `AuthTokenInterceptor` in Kotlin that reads `auth_token` from DataStore.
2. Attach `AuthTokenInterceptor` to Ktor HTTP engine in `SupabaseConfig.kt`.
3. Attach `AuthTokenInterceptor` to OkHttpClient in `ProfileManager.kt`.
4. Update `ChatRepository.kt` Realtime connection flow to pass `auth_token` during socket handshake.
5. Unify `UserSessionManager` to read session identity from DataStore.

### Phase 3 — Supabase Database RLS Deployment
1. Create and apply migration `027_enforce_jwt_persona_rls.sql`.
2. Replace Migration 024's `USING (true)` policies with strict JWT persona checks across `profiles`, `messages`, `chat_sync_pipe`, `friday_messages`, `friday_memories`, and `wishlist_*`.
3. Configure `storage.objects` RLS for `avatars` bucket.

### Phase 4 — Local Storage Isolation & Logout Teardown
1. Update `ProfileSetupViewModel.onLogout()` to cancel WorkManager jobs and clear Room cache tables (`messages`, `wishlist_*`, `sync_queue`).
2. Update `ChatSyncWorker.kt` to enforce `message.senderId == currentPersonaId`.

### Phase 5 — Verification & Acceptance
1. Execute end-to-end integration tests (Phesty login, message exchange, wishlist addition, avatar upload, logout, Baroness login).
2. Run automated curl checks proving unauthenticated requests to PostgREST and Edge Functions receive `401 Unauthorized` / `403 Forbidden`.

---

## 14. Testing and Acceptance Criteria

1. **PostgREST Unauthenticated Access Test:**
   - `curl -X GET "https://wckluymkbqxdmipzaiff.supabase.co/rest/v1/messages" -H "apikey: <ANON_KEY>"`
   - **Acceptance:** Returns empty list or `401/403`.
2. **PostgREST Authenticated Access Test:**
   - `curl -X GET "https://wckluymkbqxdmipzaiff.supabase.co/rest/v1/messages" -H "apikey: <ANON_KEY>" -H "Authorization: Bearer <PHESTY_JWT>"`
   - **Acceptance:** Returns messages where `sender_id = 'phesty_official'` or `receiver_id = 'phesty_official'`.
3. **Friday Pending Messages Endpoint Security Test:**
   - `curl -X GET "https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/friday-pending-messages?user_id=phesty_official&since=1970-01-01"`
   - **Acceptance:** Returns `401 Unauthorized`.
4. **Account Switch Isolation Test:**
   - Log in as Phesty -> draft offline message -> log out -> log in as Baroness -> observe Room DB.
   - **Acceptance:** Phesty's offline draft is wiped on logout and is NOT sent under Baroness's account.

---

## 15. Risks and Rollback Considerations

- **Risk:** Deploying database RLS before deploying the Kotlin client update will immediately break the app for existing users.
- **Mitigation / Rollback Plan:** Always deploy the Kotlin client update (Phase 2) *before* applying the database migration (Phase 3). If database RLS causes unexpected client failures in production, apply a emergency rollback script reverting policies to permissive `USING (true)` while diagnosing the token header.

---

## 16. Outstanding Decisions or Unverified Questions

1. **None:** All technical investigation items have been conclusively resolved.

---

## 17. Explicit List of Unnecessary Complexity to Avoid

1. **DO NOT** install or introduce the Supabase Auth plugin (`supabase.auth`).
2. **DO NOT** replace the existing custom passkey login flow with email/password or OAuth.
3. **DO NOT** alter database table schemas or primary keys to use UUID `auth.users` IDs.
4. **DO NOT** build multi-database or multi-file Room persistence schemes when clearing local cache tables on explicit logout satisfies 100% of isolation requirements.
5. **DO NOT** introduce complex token refresh rotation infrastructure for a 2-person internal app.

---

## 18. Acceptance Criteria Answers (10 Key Questions)

1. **Can the current custom JWT approach be retained securely?**
   - **YES.** `verify-passkey` signs valid HS256 JWTs using `SUPABASE_JWT_SECRET`. PostgREST and Realtime recognize these tokens when passed in `Authorization: Bearer <JWT>`.
2. **What is the minimum viable mechanism for propagating identity to Supabase?**
   - An OkHttp/Ktor Auth Interceptor in `SupabaseConfig.kt` that reads DataStore `"auth_token"` and attaches `Authorization: Bearer <auth_token>` to all HTTP/WebSocket requests.
3. **Which server-side access policies are currently unsafe?**
   - `profiles`, `messages`, `chat_sync_pipe`, `friday_messages`, `friday_memories`, and `wishlist_*` currently use `FOR ALL USING (true)` permissive RLS.
4. **How should each persona's access to private data be enforced?**
   - Re-enable database RLS policies evaluating `(auth.jwt() ->> 'persona')`.
5. **How do we secure the Friday pending-message endpoint?**
   - Parse `Authorization: Bearer <JWT>` header in `friday-pending-messages/index.ts`, verify signature using `SUPABASE_JWT_SECRET`, and enforce `callerPersona === userId`.
6. **How do we prevent cross-persona execution of pending local operations?**
   - Clear user Room cache tables on explicit logout (`ProfileSetupViewModel.onLogout()`) and bind `ChatSyncWorker` execution to `message.senderId`.
7. **Which existing features will break under stricter authorization?**
   - All PostgREST calls, Realtime pipe subscriptions, Profile updates, and Wishlist mutations will break unless client token propagation is deployed *before* enforcing strict RLS.
8. **What is the safest order in which to implement the fixes?**
   - Edge Function hotfix -> Client Token Interceptor -> Supabase RLS Migration -> Room/WorkManager Logout Teardown -> End-to-End Verification.
9. **Which proposed improvements are essential vs unnecessary?**
   - **Essential:** Auth Interceptor, RLS Migration, Edge Function auth, Room logout cleanup.
   - **Unnecessary:** Supabase Auth plugin, GoTrue email/password sign-in, schema migration to `auth.users` UUIDs.
10. **What exact implementation prompt should be written first?**
    - **Implementation Prompt 1:** Client-Side Token Propagation & Edge Function Security Containment.

---

## 19. First Implementation Prompt Specification

When ready to begin implementation, the first prompt provided to the developer/agent should be:

> **"Implement Phase 1 and Phase 2 of the Baroness Minimal Secure Identity Plan:**
> 1. Update `supabase/functions/friday-pending-messages/index.ts` to require and validate incoming `Authorization: Bearer <JWT>` headers against `SUPABASE_JWT_SECRET` and verify `callerPersona === userId`.
> 2. Create `AuthTokenInterceptor` in Kotlin that reads `"auth_token"` from DataStore and attaches `Authorization: Bearer <auth_token>` to all outgoing Ktor/OkHttp requests in `SupabaseConfig.kt` and `ProfileManager.kt`.
> 3. Update `ChatRepository.kt` to pass `"auth_token"` in Supabase Realtime connection parameters."**
