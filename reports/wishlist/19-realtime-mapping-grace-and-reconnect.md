# Wishlist — Realtime INSERT Mapping, Grace Period Scope, and Realtime Reconnect Report

## 1. Realtime INSERT Mapping Investigation
- **Handler**: `handleWishChange()` in [WishlistRepository.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/repository/WishlistRepository.kt) (`PostgresAction.Insert`).
- **Extraction**: Extracts `created_at` and `updated_at` via `record["created_at"]?.jsonPrimitive?.content` and parses them using `parseIsoToLong()`.
- **Finding**: The mapping is **correct** (`created_at` matches server schema snake_case). The position #1 rendering glitch on peer insert is mitigated by proper timestamp ordering and now reinforced by connection-established re-fetches.

---

## 2. Reconciliation Grace Period Scoping
- **Change**: Updated `reconcileSyncedWishes()` in `WishlistRepository.kt`.
- **New Predicate**: A local row is skipped by the grace period ONLY if both are true:
  1. `wish.createdAt >= cutoff` (within the last 60 seconds), AND
  2. `wish.syncStatus == "pending_create"` (created locally and not yet acknowledged by the server).
- **Result**: Synced rows (`syncStatus == "synced"`), even if recent (e.g. pushed from peer or newly synced), are valid reconciliation candidates and won't incorrectly block deletion/reconciliation. Added log output showing skipped rows and their `syncStatus`.

---

## 3. Realtime Reconnect Fetch Trigger
- **Location**: `subscribeToRealtest()` in `WishlistRepository.kt`, immediately after logging `"All realtime channels subscribed successfully"`.
- **Mechanism**: Launches `scope.launch { fetchRemoteWishes() }` non-blocking to reconcile any missed inserts, updates, or deletes that occurred while the client was disconnected.

---

## 4. Preservation Guardrails
- **Untouched**: Tombstone logic, pending-update-purge changes, sync-status UI, avatar loading, and non-wishlist subsystems remain completely unmodified.

---

## 5. Build and Test Results
- **Command**: `./gradlew :app:testDebugUnitTest app:assembleDebug`
- **Result**: **BUILD SUCCESSFUL** (`21 passed, 0 skipped, 0 failed`).
- **APK Path**: `app/build/outputs/apk/debug/app-debug.apk`

---

## 6. Manual Verification Checklist for the Operator
1. **Peer Insert Positioning**: Create a wish on phone A. Watch phone B's list — it should appear at its correct sorted position from the first frame, not at #1.
2. **Offline Peer Deletion**: Delete a wish on phone A while phone B is offline. Bring phone B online without touching it. The wish should disappear within a few seconds upon Realtime reconnect / fetch.
3. **Offline Local Creation**: On phone B, create a wish offline. It should stay visible and correctly positioned. Bring network back. Sync completes normally.
4. **Recent Creation Reconciliation**: Repeat with a wish deleted within 5 seconds of creation (peer offline). Verify reconciliation removes it promptly on reconnect without false grace-period protection.
