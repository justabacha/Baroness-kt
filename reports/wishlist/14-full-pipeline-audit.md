# Wishlist Sync Pipeline — Full State Audit and Rollback Decision

**Scope:** Read-only audit of the current source. No application source, schema, or policy files were changed. The requested audit report is the only file added.

## 1. Executive summary

| Reported regression | Code-based finding | Confidence |
|---|---|---|
| Pending create changes position after sync/refresh | Position is the current index in a Room list ordered by `createdAt ASC`. The temp-ID swap changes only ID and sync status, not `createdAt`; therefore that swap alone cannot move it. A remote upsert can replace `createdAt` when its `updatedAt` wins, or a fetch can add previously absent older rows and shift indexes. Which occurred cannot be determined from code or the supplied symptoms alone. | Medium-low |
| Dusted wish briefly disappears on reconnect | Dusting changes the local row and queue, not its existence. The screen renders all wishes and does not filter dusted items. A stale remote row can replace local state if its `updatedAt` wins, but that upsert includes the wish ID and therefore cannot make it a reconciliation candidate in the same fetch. Ordinary reconciliation also excludes its `pending_update` state. No deterministic code path from dust to disappearance is established; a Realtime DELETE or another local deletion would be required by the inspected paths. | Low |
| Delete waits for cold start; owner row reappears | A synced delete physically removes the Room row, then queues a remote delete. A fetch before queue processing can insert the still-remote row again because no delete tombstone is retained. Separately, `KEEP` can discard a new enqueue while a unique worker is still active; if that worker has already passed its final queue scan, the delete can remain queued until another trigger. This explains the reported pattern and is the strongest identified regression mechanism. | High |

**Recommendation: fix forward (C), not rollback.** The work-queue trigger has a plausible lost-wakeup race, and fetch/reconciliation does not protect pending local operations or deleted rows. Reverting Phase 4 reintroduces the previously confirmed duplicate-worker and fetch/reconcile overlap hazards; reverting Phase 3 removes the requested status UI and still does not guarantee safe delete or dust handling. Preserve those features while fixing queue scheduling, optimistic-write reconciliation, and creation-time stability.

## 2. Current pipeline inventory

### `WishlistRepository`

- The singleton owns `syncMutex` (`WishlistRepository.kt:75-89`). `initialSync()` acquires it before making the three remote fetches and holds it through upserts and reconciliation (`:222-276`). The public `processSyncQueue()` acquires the same mutex before calling `SyncManager.processQueue()` (`:216-220`). Both locks cover the full network calls as well as local writes; this is the current serialization boundary and can delay the waiting operation for the duration of those requests.
- `initialSync()` is called by `fetchRemoteWishes()` (`:212-214`), reached from network availability (`:121-135`), app resume (`:138-151`), `WishlistViewModel.refreshWishes()` / `syncInBackground()` (`WishlistViewModel.kt:195-215`), and the screen's first composition (`WishlistScreen.kt:72-77`).
- The queue processor is called by `SyncWorker.doWork()` through `repository.processSyncQueue()` (`SyncWorker.kt:18-29`). `syncNow()` enqueues work from insert, synced-delete, status-update, reaction, rating, retry, network-available, and app-resume paths (`WishlistRepository.kt:655, 677, 700, 716, 732, 758, 131-132, 145-146`).
- `syncNow()` returns early without an auth token and uses the network-constrained worker and unique name `"WishlistSync"` with `ExistingWorkPolicy.KEEP` (`WishlistRepository.kt:761-780`). No other wishlist unique-work name exists; `ChatSync` is a separate feature.
- Realtime change handlers write fetched/received server changes directly to Room; they do not enqueue queue work (`WishlistRepository.kt:461-519`).

### `SyncManager` and `SyncWorker`

- `SyncWorker` is a `CoroutineWorker`; it gets the singleton repository, invokes its mutex-guarded queue entry point, and returns success or retry (`SyncWorker.kt:13-30`).
- `SyncManager.processQueue()` repeatedly loads queue rows, excludes IDs already processed during this run and rows whose `inProgressAt` is less than 60 seconds old, and atomically claims eligible rows through `claimForProcessing()` (`SyncManager.kt:19-20, 45-77`; `SyncQueueDao.kt:10-28`). Success deletes the queue row; failure increments retry count, stores an error, and clears the marker (`SyncManager.kt:79-117`). At five retries it marks the associated wish failed and leaves the queue item in place (`:65-69, 94-112, 119-132`).
- Creates call the API and remap the local temp ID; updates call the API and set sync status to synced on success; deletes call the API (`SyncManager.kt:134-193`). A queue claim narrows concurrent processing but is not remote exactly-once protection: a process crash after the server accepts a create but before the ID swap/queue removal can lead to the create being retried after the marker expires.

### `WishDao`, `WishEntity`, and `SyncQueueItem`

- `WishDao.getAllWishes()` returns every wish ordered by `createdAt ASC`; its displayed number is not persisted (`WishDao.kt:12-15`). The ID swap updates only `id` and `syncStatus`, leaving `createdAt` untouched (`:41-42`). Timestamp-checked remote upsert replaces the full entity only if the incoming `updatedAt` is strictly greater (`:53-64`).
- `WishEntity` has `createdAt`, `updatedAt`, status, and string `syncStatus`; allowed documented values include `synced`, `pending_create`, `pending_update`, `pending_delete`, and `failed` (`WishEntity.kt:6-17`). Dust updates currently change `status` and `syncStatus` through separate DAO calls; `updatedAt` is not updated by `WishDao.updateStatus` (`WishDao.kt:26-30`).
- `SyncQueueItem` stores operation, payload, retry count, last error, and nullable `inProgressAt` (`SyncQueueItem.kt:6-19`). The marker was added in Phase 4 with Room migration `MIGRATION_8_9`, which adds nullable integer `sync_queue.inProgressAt`; database version is 9 (`AppDatabase.kt:16-26, 56-60, 108-119`).

### `WishlistViewModel`, `WishItem`, and `WishlistScreen`

- `WishlistViewModel.wishes` is a `StateFlow` derived directly from repository `getAllWishes()` with an initially empty list (`WishlistViewModel.kt:29-40`). Create generates a negative timestamp ID and a millisecond `createdAt`, then calls repository insert (`:162-193`). Dust/reaction/rating are guarded to synced items (`:230-268`); retry is exposed via `retryWish()` (`:224-228`).
- Repository combines wish, reaction, and rating flows into the domain `Wish`, including `syncStatus` (`WishlistRepository.kt:570-608`); the field was introduced in Phase 3 (`WishModels.kt:3-12`).
- The screen passes the unfiltered `wishes` list to `itemsIndexed`; its key uses creator, `createdAt`, and text hash, and the item index is supplied to `WishItem` (`WishlistScreen.kt:141-154`). `WishItem` renders `index + 1` unless failed (`WishItem.kt:31-54, 96-119`). Planning dust controls and dusted download/reaction/rating actions are gated on synced state; unsynced rows retain delete and show the pending/failed badge (`:156-184, 220-298, 302-339`).
- There is no screen-level filter that removes dusted wishes. The list itself is driven by every Room wish row (`WishDao.kt:14-15`, `WishlistScreen.kt:141-144`).

### Reconciliation

- `initialSync()` maps remote rows to synced entities and calls `upsertAllWithTimestampCheck()`, then calls `reconcileSyncedWishes()` (`WishlistRepository.kt:227-271`).
- Reconciliation selects local synced rows absent from that fetch, exempts those whose `createdAt` is within 60 seconds, logs the skipped count, and deletes older candidates (`:279-298`). Deletion calls `deleteWishAndRelations()` inside the same `db.withTransaction`; reactions and ratings are deleted before the wish (`:279-304`).
- Phase 4 introduced both the grace period and mutex. The grace period protects recent creations only; it does not preserve an older local row with a pending update if an incoming upsert changes its state to synced, nor does it preserve a delete tombstone (none exists).

### Phase 3 additions relative to the earlier pipeline

1. `Wish.syncStatus` exposure and entity failure status; queue exhaustion propagates `"failed"` to the wish (`WishModels.kt:3-12`, `WishEntity.kt:14-16`, `SyncManager.kt:65-69, 119-132`).
2. A retry operation resets queue retries/errors and restores pending status; the ViewModel and failed-item bubble expose the action (`WishlistRepository.kt:735-759`, `WishlistViewModel.kt:224-228`, `WishItem.kt:98-110`).
3. Pending/failed badges, failed retry bubble, and gating of dust/reaction/rating/download actions (`WishItem.kt:49-53, 156-184, 220-298, 326-339`; `WishlistViewModel.kt:230-268`).
4. Unsynced create/failed-create deletion is local-only and also deletes the related queue entries; synced deletion keeps the remote-delete queue behavior (`WishlistRepository.kt:658-684`).
5. Repository/ViewModel action guards reject writes for unsynced wishes (`WishlistRepository.kt:686-733`, `WishlistViewModel.kt:230-268`).

### Phase 4 additions relative to the earlier pipeline

1. Unique WorkManager enqueue using `"WishlistSync"` and `KEEP` (`WishlistRepository.kt:761-780`).
2. `inProgressAt` state, migration, atomic conditional claim, 60-second claim expiry, and queue rescanning (`SyncQueueItem.kt:15-18`, `AppDatabase.kt:56-60`, `SyncQueueDao.kt:19-28`, `SyncManager.kt:45-117`).
3. A repository-singleton mutex around both initial fetch/reconciliation and queue processing (`WishlistRepository.kt:84, 216-224`).
4. A 60-second local-creation grace period for reconciliation (`WishlistRepository.kt:68, 279-298`).

## 3. Trace: offline create → online sync

### Owner device

1. The user taps cast; `WishlistScreen` calls `viewModel.createWish()` (`WishlistScreen.kt:116-125`). The ViewModel creates `id = -System.currentTimeMillis()` and `createdAt = System.currentTimeMillis()` (`WishlistViewModel.kt:162-193`).
2. `WishlistRepository.insertWish()` inserts a `WishEntity` with `syncStatus = "pending_create"` and the supplied `createdAt`, then queues operation `"create"` with `localTempId` equal to the negative ID (`WishlistRepository.kt:613-655`). With a valid auth token, `syncNow()` enqueues constrained unique work; while offline it waits for network (`:761-780`). If auth is blank it returns before enqueueing.
3. Room emits through `getAllWishes()`, sorted by `createdAt ASC`; the ViewModel maps the row and the screen gives its current zero-based index to `WishItem` (`WishDao.kt:14-15`, `WishlistRepository.kt:570-608`, `WishlistScreen.kt:141-148`). The visible bubble is that index plus one, with a pending badge (`WishItem.kt:49-54, 114-119, 326-339`).
4. On connectivity, network callback starts `fetchRemoteWishes()` then calls `syncNow()` (`WishlistRepository.kt:125-133`); the constrained worker can also become runnable at the same time. The mutex ensures fetch and queue processing do not execute simultaneously, but either can acquire it first. The fetch normally does not delete the negative temp row: reconciliation only queries `syncStatus = 'synced'` rows (`WishDao.kt:47-48`; repository `:279-298`).
5. Queue processing posts the create. On success, `updateWishId(oldTempId, newServerId)` swaps the primary key and sets sync status to synced; it does not alter `createdAt` (`SyncManager.kt:145-167`, `WishDao.kt:41-42`). The Room flow emits again; pending badge disappears. If the create has an API-level failure the row stays queued and enters retry/failed handling.

### Peer device

The server create emits a wishlist Realtime INSERT when that subscription receives it. `handleWishChange()` creates a synced `WishEntity` using parsed server `created_at`/`updated_at` and timestamp-checked upsert (`WishlistRepository.kt:334-354, 461-509`). A subsequent initial fetch follows the same timestamp-checked upsert path (`:227-249`).

### Position change analysis

The number bubble is `index + 1`, and the ordered query is `createdAt ASC`; there is no persisted position (`WishDao.kt:14-15`, `WishItem.kt:114-119`). With normal increasing millisecond timestamps, a newly cast wish should sort after older wishes, not at #1; #1 immediately would imply that older rows were not yet in the emitted list or the timestamps/order differ from expectation. The successful temp-ID remap does not change `createdAt`, and the LazyColumn key also does not use the ID (`WishDao.kt:41-42`, `WishlistScreen.kt:141-144`). Therefore **the remap itself is not the position-change point**.

The code can change the rendered index if the ordered Room set changes: (a) initial/refresh fetch inserts other previously absent wishes, or (b) `upsertWithTimestampCheck()` replaces an existing row with remote `createdAt` because remote `updatedAt` is greater (`WishDao.kt:53-64`). For a create, the API sends the locally supplied creation time as ISO text (`WishlistApi.kt:52-79`; `DateUtils.kt:6-20`), so the intended timestamp should round-trip. The reported #1-to-correct-position transition is not deterministically explained without observing the local and remote `createdAt` values and the fetched wish IDs/order before and after sync. The likely code location to instrument is the fetch upsert, not the ID swap.

## 4. Trace: offline dust → online sync

1. The ViewModel checks that the row is synced, validates the date, then calls `repository.updateWishStatus(id, "dusted")` (`WishlistViewModel.kt:230-245`).
2. The repository checks synced state, writes `status = "dusted"`, then `syncStatus = "pending_update"` in separate DAO calls. It does **not** update the local `updatedAt`. It queues an `"update"` payload containing the dust status and a fresh `updated_at` timestamp, then calls `syncNow()` (`WishlistRepository.kt:686-700`; `WishDao.kt:26-30`).
3. Room emits the dusty pending row. `WishItem` treats it as an unsynced item, so dusted-side download/reaction/rating controls are replaced by delete plus `[pending]`; it does not remove the row (`WishItem.kt:49-53, 220-298`).
4. On reconnection the network callback begins `initialSync()` and then invokes `syncNow()`. The offline action may already have a constrained WorkManager request. Either fetch or worker can acquire the repository mutex first; they cannot interleave their protected sections (`WishlistRepository.kt:125-133, 216-229`).
5. If fetch wins before the server update is visible, the server may return the old planning row. `upsertWithTimestampCheck()` replaces the local row only if remote `updatedAt` is strictly greater than local `updatedAt`; since dusting did not advance local `updatedAt`, an accepted remote row can replace local status (`dusted` → `planning`) and sync status (`pending_update` → `synced`) (`WishDao.kt:53-58`; remote mapping at `WishlistRepository.kt:236-249`). If the incoming timestamp is not greater, the optimistic local dust state remains. The fetch normally has the same previously synchronized server timestamp as the local row, so replacement is conditional, not guaranteed.
6. That accepted upsert necessarily has the wish ID in the current `remoteWishIds` set. Reconciliation therefore will not delete it in that same fetch. Conversely, if the remote fetch omits the wish, there is no upsert to change its sync status; it remains `"pending_update"` and `getSyncedWishes()` excludes it. Reconciliation consequently does not delete it (`WishDao.kt:47-48`; `WishlistRepository.kt:279-298`). **Thus the inspected fetch/upsert/reconcile path does not explain a deleted dusted row.**
7. If the worker subsequently applies the queued update, `SyncManager` sets sync status to synced but does not restore the wish’s local `status` from the update payload (`SyncManager.kt:173-181`). A Realtime UPDATE or later fetch can restore the server dust status. This can explain a status/badge change or temporarily reverting to planning, but not row disappearance on its own.

**What code does not establish:** no screen filter removes dusted wishes, and the single-fetch reconciliation path described above cannot delete the pending dust row. If the event was an actual disappearance rather than reverting to planning, capture existing logs in order to identify a different delete path: `Starting initial sync from Supabase`, `Fetched ... wishes`, `Upserted ...`, `Reconciliation grace period skipped ...`, `Removed stale synced wish id=...`, `Processing UPDATE for wish ...`, `Update success`, `Realtime wish action: ...`, `Realtime wish change: id=..., status=...`, and `Realtime wish delete: id=...`. The current logs do not print the target's Room status or timestamps before/after upsert; record those values for the target ID as well.

## 5. Trace: delete → propagation

1. The screen confirmation calls `WishlistViewModel.deleteWish()`, which calls the repository (`WishlistScreen.kt:199-208`; `WishlistViewModel.kt:218-222`).
2. For a synced item, `WishlistRepository.deleteWish()` does not take the local-only branch. It transactionally deletes local reactions, ratings, and the wish, then inserts a `"delete"` queue item and calls `syncNow()` (`WishlistRepository.kt:658-684`). The local deletion happens **before** remote deletion, and no `pending_delete` row/tombstone remains.
3. If no `"WishlistSync"` work is enqueued/running, the unique work is scheduled and the worker waits for `syncMutex` if initial fetch owns it. Once eligible, it calls the remote delete and removes the queue row on success (`WishlistRepository.kt:761-780`; `SyncWorker.kt:18-29`; `SyncManager.kt:79-93, 183-187`).
4. A fetch that wins the mutex after local deletion but before remote deletion can return the still-existing server row. Timestamp-checked upsert sees no local row and inserts it as synced. Since the remote ID is in the fetched ID set, reconciliation does not remove it (`WishlistRepository.kt:227-249, 279-298`). Thus the owner can see the deleted wish reappear until the queued delete succeeds; the next Realtime DELETE event removes it locally (`:510-514`).
5. On the peer, remote deletion propagates through the subscribed wishlist channel’s DELETE action to `handleWishChange()`, which invokes the same local row-and-children deletion (`WishlistRepository.kt:334-354, 461-519`). A cold-start fetch can instead first reinsert the row; the lifecycle observer then calls `syncNow()`, allowing a stranded delete queue entry to be processed and Realtime to finally remove the peer row (`:138-151`).

## 6. Mutex + unique work interaction: does it explain delete propagation?

**Yes, as a credible lost-wakeup path; not every `syncNow()` during fetch is dropped.**

- `initialSync()` is not WorkManager work. A `syncNow()` during an active fetch is **not** dropped just because the repository mutex is held. If no unique worker exists, WorkManager can enqueue one; that worker waits for the mutex and then processes the queue.
- `KEEP` drops the new request if a `"WishlistSync"` request is already enqueued or running. If an active worker is waiting on the mutex, it will normally process the delete after the fetch releases it. If it has already scanned/drained the queue and is in the narrow interval before WorkManager marks it finished, a newly inserted delete can arrive after its last scan while `syncNow()` is dropped. `processQueue()` rescans newly queued items during its run, which reduces this window, but it cannot scan an item inserted after the final empty scan/return (`SyncManager.kt:47-60, 114-117`).
- That leaves the Room queue row orphaned until another event schedules work. Potential later triggers include another user action that calls `syncNow()`, a network-available callback, app resume (including cold start), or manual retry. A screen-only `syncInBackground()` performs a fetch and does not itself call `syncNow()` (`WishlistScreen.kt:72-77`; `WishlistViewModel.kt:208-215`), although the repository lifecycle observer separately calls both fetch and sync on resume (`WishlistRepository.kt:138-151`).
- This matches “local delete queued, owner fetch resurrects remote row, next cold start processes delete, peer finally sees deletion.” The `KEEP` race is sufficient to explain the delay when its timing occurs; independently, the missing tombstone guarantees that a fetch before remote delete can resurrect the row even when the worker is eventually scheduled. Confirm the exact occurrence from logs by correlating queue insert, worker start/finish, initial-sync order, remote delete success, and Realtime DELETE.

## 7. Rollback evaluation and recommendation

### A — Roll back Phase 4 only; retain sync-status UI

- **Duplicate creates:** likely return. Phase 4 added unique work and atomic in-progress claims after the audit documented concurrent workers processing the same create. Removing both restores independent workers and does not make the remote create idempotent.
- **Delete/dust/position:** the lost-wakeup behavior caused by `KEEP` may become less likely if ordinary work is enqueued, but there is no guarantee these regressions resolve. Parallel fetch/process can recreate the stale-upsert and reconciliation races; removing the mutex/grace also reopens the audited deletion race. Position has no confirmed Phase 4-only cause.
- **Preserved:** Phase 3 badges, retry, gating, failed state, and unsynced-delete behavior.

### B — Roll back Phases 3 and 4; retain earlier phases

- **All three bugs:** not established to resolve. Removing Phase 4 removes `KEEP` but also restores duplicate worker/fetch overlap. Removing Phase 3 removes UI status/retry/gating and local-only handling for never-synced deletes, but does not add a synced-delete tombstone or make reconciliation respect pending operations. A dust action still needs a sound local-vs-remote merge policy.
- **Lost behavior:** yes. The operator-approved pending/failed UI, explicit retry, action gating, failure propagation, and local-only deletion of never-synced creates are Phase 3 features and would be lost.
- **Preserved:** earlier deletion/reconciliation and rendering fixes remain if rollback is limited to the Phase 3/4 changes.

### C — Keep features and fix forward (recommended)

1. **Delete propagation:** keep a local `pending_delete` tombstone until remote delete succeeds; hide it from the visible list while pending. Ensure fetch upserts and reconciliation preserve rows with pending queue operations, and remove the tombstone only on delete success/Realtime confirmation. Also guarantee a follow-up worker when work arrives during an active run; `KEEP` alone cannot guarantee that. This addresses both fetch resurrection and the dropped-enqueue window without restoring concurrent workers.
2. **Dust state:** update `status`, `updatedAt`, and `syncStatus` atomically using the same timestamp placed in the queued payload. During remote upsert, preserve local rows with pending update queue entries rather than accepting stale server state. This prevents local dust state/badge reversion. The disappearance itself needs the captured DELETE/reconciliation logs before assigning a separate code fix, since the pending row is excluded from the inspected reconciliation path.
3. **Position:** instrument `createdAt` and ordered IDs across insert, ID remap, realtime upsert, and initial fetch first. Preserve the existing local `createdAt` for already-known rows during remote upsert (creation time is immutable) and use a stable deterministic tie-breaker if two rows share the same millisecond. This addresses the only code-supported ways for the ordered index to shift without altering the desired list ordering rule.
4. **Worker scheduling:** replace the lossy active-work `KEEP` trigger with a durable follow-up/drain design. Appending work to the same unique chain (for example `APPEND_OR_REPLACE`) is one option, but it should be checked against the desired “one active worker” invariant and tested for burst coalescing. Do not use a policy that cancels an in-flight create. Retain atomic row claims and the repository mutex.

These corrections address distinct layers: create-time ordering, optimistic update merge, and delete tombstones/worker wakeups. They need not conflict with the Phase 3 UI or with serialized workers. The delete tombstone must be hidden from the list to preserve current immediate-delete UX.

## 8. Operator questions

No rollback decision is required to proceed with the evidence-based fix-forward recommendation. To pin down the exact observed instances before implementation, provide:

1. For the position shift, the wish ID/text and its `createdAt` plus sorted wish IDs before and after refresh.
2. For dust disappearance, the target wish ID and the ordered logs listed in Section 4, especially whether `Removed stale synced wish id=...` or `Realtime wish delete: id=...` appears.
3. For delete delay, whether `syncNow()` was called while a `SyncWorker` was already running, and the worker/queue/API delete logs through the subsequent cold start.
