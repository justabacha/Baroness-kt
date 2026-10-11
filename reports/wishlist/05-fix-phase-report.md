# Wishlist Fix Phase Report

## Files changed

- `app/src/main/java/com/baroness/app/repository/WishlistRepository.kt`
- `app/src/main/java/com/baroness/app/api/WishlistApi.kt`
- `app/src/main/java/com/baroness/app/data/local/dao/WishDao.kt`
- `app/src/main/java/com/baroness/app/viewmodels/WishlistViewModel.kt`
- `app/src/main/java/com/baroness/app/screens/WishlistScreen.kt`
- `supabase/migrations/20261010123800_profiles_realtime_publication.sql`
- `reports/wishlist/05-fix-phase-report.md`

`WishlistRepository.kt` already contained unrelated uncommitted session/Realtime changes when this phase began. Those changes were preserved and the wishlist fixes were integrated with them.

## Fixes and verification

### 1. Peer avatar freshness

**Cause:** `fetchProfiles()` ran at repository initialization only. The existing process `ON_RESUME` handler refreshed wishes but not profiles, and there was no `profiles` Realtime subscription.

**Change:** The resume handler now refetches profiles. The existing Realtime setup subscribes to `public.profiles` and refetches both persona profiles when an insert, update, or delete concerns either persona. Profile subscription cleanup is included in the existing restart path. The existing timestamped avatar filenames give a changed avatar a new URL, so no Coil cache invalidation was added.

**Backend and verification:** Before applying the migration, `pg_publication_tables` showed `profiles` was absent from `supabase_realtime`. Migration `20261010123800_profiles_realtime_publication.sql` adds it conditionally and was applied successfully; a follow-up query confirmed the table is now present. The live `profiles_select` policy predicate is `(auth.jwt() ->> 'persona') IS NOT NULL`, permitting either authenticated persona to select both profile rows. No policy change was made.

### 2. Wishlist flicker

**Investigation and cause:** The DAO emits wishes in `createdAt ASC` order. The repository combines wish, reaction, and rating DAO flows without sorting the list, and the ViewModel applies no sort. The server fetch also requests ascending creation time. Offline wish sync changes the wish ID from its negative temporary value to the server ID while preserving `createdAt`; `LazyColumn` used that changing `wish.id` as its item key. Compose therefore treated the remapped wish as a removed item plus an inserted item, rebuilding its row even though its position did not change. No second-sort or flow-emission ordering cause was found.

**Change:** `LazyColumn` now keys each wish by its stable creator, creation timestamp, and text hash. Actual `wish.id` values remain in use for actions and data operations. Ordering remains `createdAt ASC`.

**Verification:** Traced the Room query, repository combination, ViewModel, and temp-ID remap path. No order-changing code was introduced; Kotlin compilation and the unit-test task completed successfully.

### 3. Blocking loading spinner

**Cause:** The ViewModel began every screen instance with `isInitialLoading = true`; the screen placed the entire wishlist behind a full-screen progress overlay until wishes arrived or a 300 ms fallback delay elapsed.

**Change:** Removed the full-screen overlay and the initial-loading state, collection, and fallback timer. The screen now renders its Room-backed flow directly while background sync continues independently. The user-initiated pull-to-refresh indicator is unchanged.

**Verification:** Confirmed there are no remaining `isInitialLoading` references or loading overlay in the wishlist screen/ViewModel. The debug APK assembled successfully.

### 4. Deleted wish reconciliation

**Cause:** A full wishlist fetch upserted rows but never removed local synced wishes absent from the server. In addition, the API fetch helpers converted errors into empty lists, making a failed fetch indistinguishable from a successful empty response.

**Change:** The three wishlist full-fetch helpers now log and rethrow fetch/decode errors rather than returning success-shaped empty lists. Initial sync rejects wish responses containing a missing server ID. Once wishes, reactions, and ratings have all been fetched and processed successfully, it compares server IDs with Room rows selected by `syncStatus = 'synced'` and transactionally deletes stale wishes and their reactions/ratings. The shared local deletion helper is also used by ordinary local deletion and Realtime wish deletion. Pending creates, pending deletes, and other non-synced rows are not selected or removed.

**Trigger points:** The existing screen-entry/background sync and pull-to-refresh both call the full fetch. The existing network-available callback and app-resume handler also call it, so a reconnect/resume gets the same reconciliation.

**Safety:** Reconciliation is idempotent: after stale rows are removed, another successful fetch of the same server ID set finds nothing further to delete. A fetch/decode error, invalid wish response, or failure while applying any fetched data exits before reconciliation. Reconciliation is called only after the complete full-fetch cycle, never from a Realtime event.

## Validation and device results

- `:app:testDebugUnitTest`: **passed**.
- `:app:assembleDebug`: **passed**; APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`.
- `git diff --check`: **passed**.
- Live publication verification: **passed**; `profiles` is in `supabase_realtime`.
- ADB device check: `adb devices -l` returned no attached devices. The APK could not be installed on either phone, so the requested on-device avatar timing, visual flicker, offline/cold-start behavior, and two-device deletion scenarios (including the pending-create case) were **not run**. Build and database verification do not substitute for those device checks.

## Fix Phase 2

### 1. Avatar offline regression

**Investigation and cause:** The wishlist header gets `userAvatars` from `WishlistViewModel` (`WishlistViewModel.kt:104-113`), mapped from `WishlistRepository.profiles` (`WishlistRepository.kt:75-76`; the mapping at `WishlistViewModel.kt:104-113`). `WishlistScreen.kt:53` collects that state and passes each URL to `WishlistHeader.kt:60,79`, where `AsyncImage` loads the URL. Before this phase, the repository initialized profiles to an empty in-memory map and fetched over the network only (`WishlistRepository.kt:98-105,159-189`); there was no Room or DataStore profile read/write. Offline fetch failures were caught without clearing or overwriting the current state, and resume did not clear state first, so failure modes A and B were not the cause after a successful fetch in the same process. But on a cold offline launch the process-local flow started empty, confirming E and the missing local read in C.

The header used Coil `AsyncImage` without a custom image loader. The Supabase avatar URL returned `Cache-Control: no-cache`; Coil 2.7.0's default `respectCacheHeaders = true` means its `HttpUriFetcher` will not use a cache entry while that response requires revalidation and will not write a non-cacheable response. Thus a cached URL alone did not guarantee an offline image. This was confirmed on-device before the final adjustment: the profile cache reloaded, the network request failed as expected, but both avatars remained blank.

**Fix:** On successful non-empty profile fetches, the repository now serializes both persona profile records (ID, display name, avatar URL) to the existing DataStore through `StorageManager.saveString`; initialization loads that cache before requesting the network (`WishlistRepository.kt:101-104,159-207`; `StorageManager.kt:49-55`). Fetch failures and empty results retain the last known profile state. `WishlistHeader` now uses a wishlist-local Coil `ImageLoader` with `respectCacheHeaders(false)` (`WishlistHeader.kt:33-38`), allowing versioned avatar URLs to be saved and read from Coil's disk cache despite the response header. The scope is restricted to this header; other Coil uses are unchanged. Avatar uploads use timestamped filenames, so a changed avatar gets a distinct URL.

**Verification:** Installed the debug APK on both connected phones. On `SM_A047F`, the online wishlist showed both profile images. With Wi-Fi and mobile data disabled, the app was force-stopped and cold-started; logs showed `Loaded 2 cached profiles` and a failed network fetch, while the wishlist header displayed both actual cached avatar images. Repeated with airplane mode enabled; after cold start and opening Wishlist, both avatars and the cached wishes displayed. Airplane mode, Wi-Fi, and mobile data were restored to their original enabled/disabled state (airplane off, Wi-Fi on, mobile data on). Online profile fetch and subscription setup remained in the logs; an actual peer avatar change event was not generated during this phase.

### 2. Persistent wishlist flicker

**Investigation and cause:** `WishlistRepository.getAllWishes()` combines three Room flows—wishes, reactions, and ratings—using `combine` (`WishlistRepository.kt:551-552`). It maps each wish from the wish DAO result and attaches matching reactions/ratings, with no repository sort (`WishlistRepository.kt:555-579`). The DAO query orders by `createdAt ASC` (`WishDao.kt:14-15`). `WishlistViewModel.wishes` applies `stateIn` with an initial empty list and no sort, `distinctUntilChanged`, or other transformation (`WishlistViewModel.kt:31-37`). `WishlistScreen` collects that flow directly and renders the list in `LazyColumn`; it has no list-state restoration parameter or profile-dependent visibility (`WishlistScreen.kt:50-54,75-77,120-151`). The stable local item key from Phase 1 remains.

Static code inspection found the source of partial list emissions: `initialSync()` previously called the transaction-wrapped `upsertWithTimestampCheck` separately for each remote wish inside a loop. Since `getAllWishes()` observes the DAO query, it could emit intermediate partial lists between those per-row commits. Reactions and ratings are fetched and written afterward; `combine` can therefore emit additional mapped lists as those tables change, but these emissions do not change wish order. Screen-entry sync, resume sync, and network sync can overlap, making row-by-row updates more likely during screen entry. There is no ViewModel-side sort and no conditional render waiting on profiles. The exact historical on-device emission sequence was not logged before this change, so the precise reported pair of frames cannot be reconstructed from static code alone; the per-row Room commits are the code-level cause capable of producing the reported “second item, then first item above it” partial-list sequence. An emission count over the initial two seconds is runtime-dependent: the flow has an initial empty `stateIn` value, then emits after all three DAO flows have emitted, plus any DAO invalidations that occur afterward.

**Fix:** Full remote wish responses are now mapped to entities and passed through `WishDao.upsertAllWithTimestampCheck`, whose `@Transaction` wraps the entire batch (`WishlistRepository.kt:224-237`; `WishDao.kt:58-61`). Room therefore publishes the completed wish batch at commit instead of an intermediate list per remote row. Reaction/rating changes may still re-emit the combined list, but they cannot reorder it. The required order remains `createdAt ASC`.

**Verification:** On `SM_A047F`, three repeated opens showed the items in the same final order (`DYE DATE`, then `mock test`). Four screen captures sampled about 100, 400, 800, and 1,600 ms after opening showed an initial empty `stateIn` frame at 100 ms, then both items in final order at 400 ms and later; there was no item-2-only frame or visible reorder in those samples. Device sampling is not a continuous screen recording, so very brief intermediate frames outside those samples cannot be ruled out.

### Regression and validation

- `:app:testDebugUnitTest`, `:app:assembleDebug`, and `git diff --check`: **passed** after both Phase 2 changes.
- The existing profile Realtime subscription remains wired and successfully subscribed on an online run. No change event was induced, so peer-to-peer avatar update latency was not re-measured.
- Deleted-wish reconciliation and its pending-status guard were not modified in Phase 2; the full-sync code path remains present. A destructive cross-device wish deletion was not performed on the connected phones, so reconciliation was not end-to-end retested.
- The first phone (`SM_A235F`) had no usable internet route during testing. The offline avatar and repeated-open walkthrough was completed on the second phone (`SM_A047F`); the updated APK was installed on both.
