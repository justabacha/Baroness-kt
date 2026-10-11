# Phase 3.1 Audit Report — Post-Migration Breakage Investigation

## 1. Executive Summary

Following the application of the Phase 3 strict persona-scoped RLS migration, live testing revealed four functional regressions:
1. **Profile Upsert 403**: The `profiles` table lacked an `INSERT` policy, causing `PUT` upserts (`Prefer: resolution=merge-duplicates`) from `ProfileManager` to fail on initial creation.
2. **Avatar Delete 400**: Legacy avatars stored at the bucket root path (`avatar_<ts>.jpg`) failed deletion because the strict storage policy required a persona folder prefix.
3. **Wishlist Realtime Failure**: Realtime broadcasts for `wishlist_items`, `wishlist_reactions`, and `wishlist_ratings` failed to push across clients because `relreplident` was default (`'d'`) and SELECT policies used constant expressions (`IS NOT NULL`) without referencing row columns.
4. **Post-Logout Empty UI on Re-Login**: Wiping shared wishlist tables during logout created a jarring "empty screen" experience upon re-login before network synchronization completed.

---

## 2. Profile 403 on Upsert (Confirmed Root Cause)

### Audit Finding
Querying live `pg_policies` for `profiles` confirmed that only `profiles_select` and `profiles_update` policies existed. No `INSERT` policy was present. When `ProfileManager.upsertProfile` executes `PUT /rest/v1/profiles?id=eq.<id>` with upsert headers, PostgREST attempts an `INSERT` if the row does not exist, triggering an RLS violation (HTTP 403).

### Proposed INSERT Policy SQL
```sql
CREATE POLICY "profiles_insert" ON public.profiles
    FOR INSERT TO authenticated
    WITH CHECK (id = (auth.jwt() ->> 'persona'));
```
*Note: All other client-written tables (`messages`, `chat_sync_pipe`, `wishlist_items`, `wishlist_reactions`, `wishlist_ratings`, `friday_memories`, `gallery_items`, `typing_status`, `backup_log`) were verified to have corresponding `INSERT` policies.*

---

## 3. Avatar Delete 400 on Root-Path Files

### Root Cause
Old avatars reside at the bucket root (`avatar_<ts>.jpg`), while new avatars are uploaded to `<persona>/avatar_<ts>.jpg`. The strict storage DELETE policy enforced `(storage.foldername(name))[1] = (auth.jwt() ->> 'persona')`. For root-path files, foldername is empty, causing RLS rejection (HTTP 400).

### Proposed Resolution (Option B + Root Fallback)
Modify the storage DELETE (and UPDATE) policies to permit deleting legacy root-path files or persona-owned files:
```sql
CREATE POLICY "avatars_delete" ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id = 'avatars' AND (
            (storage.foldername(name))[1] = (auth.jwt() ->> 'persona') OR 
            array_length(storage.foldername(name), 1) IS NULL
        )
    );
```

---

## 4. Wishlist Realtime Diagnosis Correction (Follow-Up)

### Rejected Hypothesis
The initial audit suggestion to change wishlist SELECT policies to `USING (creator_id = ... OR creator_id <> '')` has been **rejected**. The expression `creator_id <> ''` evaluates to true for all non-empty rows, effectively removing persona boundaries. Furthermore, constant-expression policies (`(auth.jwt() ->> 'persona') IS NOT NULL`) are fully valid in Supabase RLS and do not require row-column references for `SELECT`.

### Root Cause Analysis
1. **Channel Lifecycle Isolation**: `WishlistRepository` initializes its Realtime subscription once in its `init` block using timestamped channel names (`wishlist-items-$timestamp`). However, unlike `ChatRepository`, `WishlistRepository` does not listen to token refresh events or restart its subscriptions when tokens rotate.
2. **Token Rotation Disconnect**: When an in-process token refresh occurs, `SessionTokenCache` updates `realtime.setAuth()`, but active wishlist channels established prior to the refresh remain bound to their initial handshake state and can fail under RLS if the underlying WebSocket transport renegotiated without re-joining.
3. **Replica Identity**: While INSERT events function correctly with default replica identity (`'d'`), setting wishlist tables to `REPLICA IDENTITY FULL` ensures robust UPDATE and DELETE event broadcasting.

---

## 5. Logout Local-Wipe Review

### Analysis
The Phase 2C-2 logout implementation wipes 5 Room tables: `messages`, `wishlist_items`, `wishlist_reactions`, `wishlist_ratings`, and `sync_queue`. Because wishlist items are shared collaboration data (visible to both personas), wiping them on logout forces an empty UI state upon re-login until initial network sync completes.

### Recommendation: Option B (Persona-Private vs. Shared Teardown)
- **Keep Wipe For**: Private data (`messages`, `sync_queue`, Friday data).
- **Preserve / Do Not Wipe**: Shared collaborative tables (`wishlist_items`, `wishlist_reactions`, `wishlist_ratings`). Since both personas legitimately share wishlist items, preserving them across logout ensures an instant, responsive UI upon re-login.

---

## 6. Confirmed Corrected Fix Set

1. **`profiles_insert` Policy**: Add missing insert policy for `profiles`.
2. **Storage Policy Root Fallback**: Update storage DELETE and UPDATE policies on `storage.objects` for `avatars` to allow root-path legacy file cleanup (`array_length(storage.foldername(name), 1) IS NULL`).
3. **Wishlist Replica Identity FULL**: Set `REPLICA IDENTITY FULL` on `wishlist_items`, `wishlist_reactions`, and `wishlist_ratings` for reliable INSERT/UPDATE/DELETE change payloads.
4. **Wishlist Channel Token-Refresh Lifecycle**: Update `WishlistRepository` to observe session token refreshes and re-subscribe channels (mirroring `ChatRepository` improvements).
5. **Logout Teardown Adjustment (Option B)**: Preserve shared wishlist Room tables across logout to prevent empty UI states on re-login.

---

## 7. Phase 3.2 Fixes — 2026-10-10

### SQL Migration

- File: `supabase/migrations/20261010031000_phase_3_2_fixes.sql`
- Applied once using Supabase CLI: `supabase db query --linked --file supabase\migrations\20261010031000_phase_3_2_fixes.sql --output-format json`.
- Application timestamp: **2026-10-10T03:15:40.262Z UTC**.
- SHA-256: `457FE7DFE70506C83D6D8B9B33A69EC3DA0F82019AC9093DEE1EEF5CC93A3195`.
- Outcome: successful. Live inspection confirms the authenticated `profiles_insert` policy, avatar DELETE root fallback, avatar UPDATE root fallback with persona-scoped `WITH CHECK`, and `REPLICA IDENTITY FULL` (`relreplident='f'`) for all three wishlist tables.
- No other policies were changed by this migration.

### Wishlist Realtime Lifecycle

- File: `app/src/main/java/com/baroness/app/repository/WishlistRepository.kt`.
- The repository now collects `SessionTokenCache.tokenRefreshedFlow`. On each refresh it cancels and joins its prior subscription job and flow collectors, unsubscribes and removes its three wishlist channels, resets the subscribed state, then starts subscriptions again using the current Supabase Realtime auth token.
- Channel topics remain timestamped `wishlist-items-*`, `reactions-*`, and `ratings-*`; all three existing table filters and Room handlers are unchanged. Stale channel cleanup is limited to those wishlist topics.
- Compile result: **BUILD SUCCESSFUL** (`:app:assembleDebug`).

### Logout Local Cache

- File: `app/src/main/java/com/baroness/app/auth/SessionLifecycleManager.kt`.
- Logout continues clearing `messages` and `sync_queue`.
- `wishlist_items`, `wishlist_reactions`, and `wishlist_ratings` are no longer cleared; a code comment records that wishlist data is shared across personas and should survive logout.
- Server revocation, Realtime disconnect, WorkManager cancellation, DataStore clearing, SharedPreferences clearing, and in-memory session clearing were left unchanged.
- Compile result: **BUILD SUCCESSFUL** (`:app:assembleDebug`).

### Post-Fix REST Verification

- `PUT /rest/v1/profiles?id=eq.phesty_official` using a valid Phesty session, existing profile values unchanged, and `Prefer: resolution=merge-duplicates,return=representation`: **HTTP 200**. The 407-byte profile representation was not copied into logs or this report; it contained existing profile fields and is redacted here.
- Storage policy inspection confirms DELETE permits root-path objects; UPDATE permits legacy root paths in `USING` but its `WITH CHECK` still requires the persona folder.
- The project had 14 root-path objects, of which 2 were referenced by profile URLs and 12 appeared unreferenced. The operator explicitly authorized deleting one unreferenced root object for this test.
- First request used the wrong Storage API shape, `DELETE /storage/v1/object/avatars/<path>` with no body; it returned **HTTP 400**, `{"statusCode":"400","error":"FastifyError","message":"Body cannot be empty when content-type is set to 'application/json'","code":"InvalidRequest"}`. Follow-up query confirmed that object remained.
- Correct Storage remove request used `DELETE /storage/v1/object/avatars` with `{"prefixes":[".emptyFolderPlaceholder"]}`. It returned **HTTP 200**, not 204, with body `[{"name":".emptyFolderPlaceholder","bucket_id":"avatars","owner":"","owner_id":"","version":"89999d37-af20-441b-888e-0c730f256c6f","id":"7f9a6be2-b895-453a-a7c1-acbdf8b74c5c","updated_at":"2026-05-04T07:54:55.550Z","created_at":"2026-05-04T07:54:55.550Z","last_accessed_at":"2026-05-04T07:54:55.550Z","metadata":{"eTag":"\"d41d8cd98f00b204e9800998ecf8427e\"","size":0,"mimetype":"application/octet-stream","cacheControl":"max-age=3600","lastModified":"2026-05-04T07:54:55.550Z","contentLength":0,"httpStatusCode":200},"user_metadata":{},"archived_at":null,"is_delete_marker":false,"is_versioned":false}]`.
- The deleted object was an unreferenced, zero-byte `.emptyFolderPlaceholder`, not an avatar image. A post-request query confirmed that placeholder no longer exists and root-path object count is now 13. No profile-referenced avatar was deleted. The root-path DELETE policy is confirmed through the successful Storage API deletion, but a legacy `avatar_*.jpg` DELETE was not exercised.

### Status

The migration and both Android changes are complete and compile. The profile upsert verification passed. The authorized root-path Storage DELETE succeeded with HTTP 200 (the endpoint's actual response for this request), although no avatar image was deleted. A legacy avatar-image DELETE remains untested; all requested code and migration changes are complete.
