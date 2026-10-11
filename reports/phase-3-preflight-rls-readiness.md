# Phase 3 Preflight Report — RLS Enforcement Readiness Audit

## 1. Executive Summary

With Phases 2C-1, 2C-2, and 2C-3 successfully implemented and verified, the Baroness Android application correctly authenticates via ES256/HS256 JWTs issued by the `verify-passkey` Edge Function (password grant against Supabase Auth), securely stores and refreshes tokens single-flight, and maintains robust Realtime channel lifecycles. 

Phase 3 transitions the database from permissive (`USING (true)`) RLS policies to **Strict Persona-Scoped RLS**, leveraging the custom JWT claim `auth.jwt() ->> 'persona'` (e.g., `phesty_official` or `baroness_official`). This audit inventories every table, client operation, storage bucket, and realtime channel in the live Supabase project, cross-referencing them against prior audit reports to ensure zero regressions before migration deployment.

---

## 2. Mandatory Pre-Implementation Reading & Cross-Reference

This audit builds upon and reconciles findings from:
- [phase-0-identity-authentication-authorization-audit.md](file:///C:/Baroness_Core/Baroness-kt/reports/phase-0-identity-authentication-authorization-audit.md)
- [phase-0b-minimal-secure-identity-implementation-plan.md](file:///C:/Baroness_Core/Baroness-kt/reports/phase-0b-minimal-secure-identity-implementation-plan.md)
- [phase-2c-1-server-side-auth-migration.md](file:///C:/Baroness_Core/Baroness-kt/reports/phase-2c-1-server-side-auth-migration.md)

### Discrepancy & Coverage Analysis
Prior audit reports correctly identified core tables (`profiles`, `messages`, `chat_sync_pipe`, `friday_messages`, `wishlist_items`, `access_keys`), but did not fully inventory secondary Friday state tables (`friday_memories`, `friday_entities`, `friday_sessions`, `friday_memory_history`, `friday_action_requests`, `friday_api_usage`), collaboration tables (`typing_status`, `gallery_items`), or system tables (`backup_log`, `phestydrop_releases`, `migration_history`). This audit expands coverage to 100% of public schema tables.

---

## 3. Live Table Inventory

| Table Name | RLS Enabled | Current Policy Count | Has Persona Column? | Column Name | In `supabase_realtime` Publication? | Has Client Writes? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `profiles` | Yes | 1 (Permissive) | Yes | `id` | No | Yes (Profile/FCM) |
| `wishlist_items` | Yes | 4 (Permissive) | Yes | `creator_id` | Yes | Yes |
| `wishlist_reactions` | Yes | 4 (Permissive) | Yes | `persona_id` | Yes | Yes |
| `wishlist_ratings` | Yes | 4 (Permissive) | Yes | `persona_id` | Yes | Yes |
| `gallery_items` | Yes | 4 (Permissive) | Yes | `uploaded_by` | No | Yes |
| `friday_memories` | Yes | 1 (Permissive) | Yes | `owner_id` | No | Yes (via Edge) |
| `typing_status` | Yes | 1 (Permissive) | Yes | `user_id` | Yes | Yes |
| `migration_history` | Yes | 0 | No | — | No | No (Migrations) |
| `messages` | Yes | 1 (Permissive) | Yes | `sender_id` / `receiver_id` | Yes | Yes |
| `friday_messages` | Yes | 1 (Permissive) | Yes | `owner_id` | Yes | Yes |
| `chat_sync_pipe` | Yes | 1 (Permissive) | Yes | `recipient_id` | Yes | Yes |
| `backup_log` | Yes | 1 (Permissive) | Yes | `persona_id` | No | Yes |
| `access_keys` | Yes | 0 | No | — | No | No (Edge only) |
| `friday_entities` | Yes | 0 | Yes | `owner_id` | No | Yes |
| `friday_memory_history` | Yes | 0 | Yes (via join) | `memory_id` | No | Yes |
| `friday_sessions` | Yes | 0 | Yes | `owner_id` | No | Yes |
| `friday_action_requests` | Yes | 0 | Yes | `owner_id` | No | Yes |
| `friday_api_usage` | Yes | 0 | Yes | `owner_id` | No | Yes |
| `phestydrop_releases` | Yes | 1 (Public Select) | No | — | No | No |

---

## 4. Client Operation Inventory

| Table | Operation | Caller (Class / Function) | Query Shape / Filter | Expected Authorization |
| :--- | :--- | :--- | :--- | :--- |
| `profiles` | SELECT | `ProfileManager.loadProfile`, `WishlistRepository` | `SELECT * FROM profiles WHERE id = eq.<id>` | Any authenticated persona |
| `profiles` | UPDATE | `ProfileManager.upsertProfile`, `updateFcmToken` | `UPDATE profiles SET ... WHERE id = eq.<id>` | Only own row (`id = jwt.persona`) |
| `messages` | SELECT | `ChatRepository.fetchMessagesFromServer` | `SELECT * FROM messages WHERE (sender_id = X AND receiver_id = Y) OR ...` | Sender or receiver |
| `messages` | INSERT/UPSERT | `ChatApi.sendMessage` | `UPSERT INTO messages ...` | Sender must be `jwt.persona` |
| `messages` | UPDATE | `ChatApi.markMessageAsDeleted` | `UPDATE messages SET is_deleted = true WHERE id = ...` | Sender or receiver |
| `chat_sync_pipe` | SELECT | `ChatRepository.fetchSyncPipe` | `SELECT * FROM chat_sync_pipe WHERE recipient_id = <persona>` | Recipient (`recipient_id = jwt.persona`) |
| `chat_sync_pipe` | INSERT | `ChatApi.pushToSyncPipe` | `INSERT INTO chat_sync_pipe ...` | Any authenticated persona pushing to peer |
| `chat_sync_pipe` | DELETE | `ChatApi.deletePipeItem` | `DELETE FROM chat_sync_pipe WHERE id = ...` | Recipient (`recipient_id = jwt.persona`) |
| `friday_messages` | ALL | `ChatApi.fetchFridayMessages`, `ChatRepository` | Filter by `owner_id` | `owner_id = jwt.persona` |
| `friday_memories` | ALL | Friday Edge Functions / Repository | Filter by `owner_id` | `owner_id = jwt.persona` |
| `wishlist_items` | ALL | `WishlistApi`, `WishlistRepository` | Full table access / upsert | Any authenticated persona (Shared) |
| `wishlist_reactions` | ALL | `WishlistApi`, `WishlistRepository` | Upsert / Delete by `persona_id` | Any authenticated persona (Shared) |
| `wishlist_ratings` | ALL | `WishlistApi`, `WishlistRepository` | Upsert by `persona_id` | Any authenticated persona (Shared) |
| `typing_status` | ALL | `ChatRepository` / Realtime | Upsert by `user_id` | `user_id = jwt.persona` for write; any for read |
| `backup_log` | ALL | `BackupManager`, `ChatApi` | Filter by `persona_id` | `persona_id = jwt.persona` |
| `access_keys` | ALL | `verify-passkey` Edge Function | Service Role Key bypasses RLS | Service Role only |
| `phestydrop_releases` | SELECT | `PhestyDropManager.checkForDrop` | `SELECT * FROM phestydrop_releases ORDER BY version_code DESC` | Public / Anon |

---

## 5. Business Rules Confirmation

1. **Wishlist visibility & mutations**: Both paired personas (`phesty_official` and `baroness_official`) can view, add, edit, react to, rate, and delete wishlist items and reactions. This shared collaborative model is intentional.
2. **HumanChat messages**: Senders own their message content (`sender_id = jwt.persona`). Recipients can update delivery/read timestamps, reactions, or pins, but cannot tamper with sender ID or message text.
3. **Friday AI**: Strictly private to the owning persona (`owner_id = jwt.persona`). Neither persona can read the other's Friday memories, messages, sessions, or entities.
4. **Profiles**: Publicly readable by both authenticated personas. Each persona can update only their own profile row.
5. **PhestyDrop releases**: Publicly readable by any app instance (anon or authenticated) for OTA update checks.
6. **`access_keys`**: Strictly confidential; accessed exclusively via service role inside the `verify-passkey` Edge Function.

---

## 6. Storage RLS (`avatars` bucket)

- **Current State**: `storage.objects` policies allow public read (`SELECT`) and permissive upload/update/delete for the `avatars` bucket.
- **Proposed Policies**:
  - `SELECT`: Publicly accessible for any client reading avatar URLs.
  - `INSERT / UPDATE / DELETE`: Restricted to authenticated users where the folder name matches their persona ID (`(storage.foldername(name))[1] = (auth.jwt() ->> 'persona')`).

---

## 7. Realtime Authorization

Tables in `supabase_realtime`: `wishlist_items`, `wishlist_reactions`, `wishlist_ratings`, `typing_status`, `messages`, `friday_messages`, `chat_sync_pipe`.
- **Enforcement**: With strict RLS enabled on `chat_sync_pipe`, the client subscription (`chat_sync_pipe_$personaId`) will automatically receive *only* events where `recipient_id = auth.jwt() ->> 'persona'`, validated server-side by PostgreSQL RLS.

---

## 8. Proposed RLS Migration

The complete draft migration SQL is located at:
[phase-3-proposed-rls-migration.sql](file:///C:/Baroness_Core/Baroness-kt/reports/phase-3-proposed-rls-migration.sql)

---

## 9. Verification Plan

After applying the migration, verify with curl / Supabase client:
1. **Persona Isolation**: Authenticate as Phesty; attempt to SELECT/UPDATE Baroness's Friday messages -> Expect HTTP 403 / zero rows.
2. **Shared Wishlist**: Authenticate as Phesty; create a wishlist item; authenticate as Baroness; verify item is visible and ratable.
3. **Sync Pipe**: Send a message from Phesty to Baroness; verify row appears in `chat_sync_pipe` for Baroness and arrives via Realtime.
4. **PhestyDrop**: Query `phestydrop_releases` anonymously -> Expect HTTP 200.
5. **App Smoke Test**: Launch Android app with live token refresh and verify HumanChat, Friday AI, Wishlist, Profile, and Notifications operate without error.

---

## 10. Risk and Rollback

- **Risk**: Overly restrictive policies could block sync pipe delivery or avatar uploads.
- **Rollback**: Re-apply migration 024 (or drop strict policies and re-create permissive policies) without data loss.

---

## 11. Corrections After Follow-Up Audit

Following the Phase 3 follow-up audit, the following critical defects in the initial draft migration were corrected:

1. **Exact Policy Name Dropping**: Replaced guessed DROP statements with exact policy names queried from `pg_policies` (e.g. `"Allow public access to messages"`, `"Allow public access to profiles"`, etc.).
2. **Message Immutability Trigger**: Added `enforce_message_immutability` BEFORE UPDATE trigger on `public.messages` to ensure recipients can update metadata (`read_at`, `is_deleted`, `reactions`) without tampering with immutable identifiers (`id`, `conversation_id`, `sender_id`, `receiver_id`, `content`, `created_at`).
3. **Avatar Storage Path Prerequisite**: Documented that avatar uploads in `ProfileManager.kt` must be updated during Phase 3 implementation to use `<persona>/avatar_<timestamp>.jpg` so the foldername-based RLS storage policy validates correctly.
4. **Wishlist INSERT Binding**: Tightened wishlist, reaction, and rating `INSERT` policies to strictly enforce `creator_id = (auth.jwt() ->> 'persona')` and `persona_id = (auth.jwt() ->> 'persona')`.
5. **Removed Redundant `access_keys` Policy**: Removed the redundant `USING (false)` policy, relying on default deny behavior of RLS-enabled tables with zero policies.
6. **Chat Sync Pipe Insert**: Verified schema and restricted INSERT with `WITH CHECK ((auth.jwt() ->> 'persona') IS NOT NULL)`.

---

## 12. Schema and Policy Verification Results

### Live Schema of `public.messages`
Columns:
1. `id` (uuid, NOT NULL)
2. `conversation_id` (text, NOT NULL)
3. `sender_id` (text, NOT NULL)
4. `receiver_id` (text, NOT NULL)
5. `content` (text, NOT NULL)
6. `created_at` (timestamptz, NULLABLE)
7. `read_at` (timestamptz, NULLABLE)
8. `is_deleted` (bool, NULLABLE)
9. `reactions` (jsonb, NULLABLE)

- **`conversation_id` exists?** Yes.
- **`content` exists?** Yes (serves as the message body column).
- **Immutable columns for recipient**: `id`, `conversation_id`, `sender_id`, `receiver_id`, `content`, `created_at`.
- **Mutable columns for recipient**: `read_at`, `is_deleted`, `reactions`.

### Storage Policies (`storage.objects`)
Verified exact live policy names:
- `"Allow Delete"`
- `"Allow Update"`
- `"Allow Upload"`
- `"Anyone can upload an avatar"`
- `"Avatar images are publicly accessible"`
- `"Public Access"`
All 6 are explicitly dropped and replaced in `phase-3-proposed-rls-migration.sql`.

### `auth.jwt()` Availability
Supabase Postgres runtime exposes `auth.jwt()`. In trigger functions, `auth.jwt() ->> 'persona'` correctly extracts custom claims.

### `chat_sync_pipe` Schema
Columns: `id`, `recipient_id`, `payload`, `created_at`. No `sender_id` column exists. The INSERT policy permits any authenticated persona to insert an event into a peer's sync pipe, relying on two-party trust and the payload structure.

---

## 13. Final Corrections

The final preflight correction pass addressed four key technical requirements:
1. **NULL-Safe Trigger Comparisons**: Replaced standard `=` comparisons in `enforce_message_immutability` with `IS NOT DISTINCT FROM` to ensure nullable columns like `created_at` evaluate correctly when null.
2. **Security Invoker Trigger**: Removed `SECURITY DEFINER` from the trigger function, defaulting to `SECURITY INVOKER` since it requires no elevated privileges.
3. **Sender Content Edits Supported**: Configured the trigger to permit message senders to edit their own message content (supporting `ChatRepository.editMessage`), while restricting recipients strictly to mutable metadata updates (`read_at`, `is_deleted`, `reactions`).
4. **Bucket-Scoped Storage DROPs**: Confirmed that all 6 active `storage.objects` policies are explicitly scoped to `bucket_id = 'avatars'`, ensuring safe dropping without impacting other storage buckets.
