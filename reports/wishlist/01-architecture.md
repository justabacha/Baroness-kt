# Wishlist Feature Audit — Architecture & Live Schema

## 1. Resolution of Prior Contradictions

### 1.1 Token Refresh Handling in `WishlistRepository.kt`
- **Prior Audit Claim**: Claimed `WishlistRepository` did not listen for token refresh events.
- **Live Verification**: Verified in [WishlistRepository.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/repository/WishlistRepository.kt#L103-L107) that Phase 3.2 explicitly implemented:
  ```kotlin
  scope.launch {
      SessionTokenCache.tokenRefreshedFlow.collect {
          Log.d(TAG, "Auth token refreshed; restarting wishlist Realtime subscriptions")
          restartRealtimeSubscriptions()
      }
  }
  ```
- **Resolution**: The prior claim is **struck**. Token refresh handling is fully implemented.

### 1.2 Replica Identity Live State
- **Prior Audit Claim**: Claimed tables were set to default (`'d'`).
- **Live Verification**: Executing live query `SELECT c.relname, c.relreplident FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relname IN ('wishlist_items', 'wishlist_reactions', 'wishlist_ratings');` returned:
  - `wishlist_items`: `relreplident` = `'f'` (FULL)
  - `wishlist_ratings`: `relreplident` = `'f'` (FULL)
  - `wishlist_reactions`: `relreplident` = `'f'` (FULL)
- **Resolution**: The prior claim is **struck**. Replica Identity FULL is active on all three tables.

---

## 2. Live Database Schema & RLS Policies

### 2.1 Live Columns & Types
- **`wishlist_items`**: `id` (bigint, PK), `text` (text), `wish_date` (date), `status` (text), `creator_id` (text), `created_at` (timestamptz), `updated_at` (timestamptz).
- **`wishlist_reactions`**: `id` (bigint, PK), `wish_id` (bigint, FK), `persona_id` (text), `emoji` (text), `created_at` (timestamptz). Unique constraint on `(wish_id, persona_id)`.
- **`wishlist_ratings`**: `id` (bigint, PK), `wish_id` (bigint, FK), `persona_id` (text), `rating` (integer), `created_at` (timestamptz). Unique constraint on `(wish_id, persona_id)`.

### 2.2 Live RLS Policies (Verbatim from `pg_policies`)
- **`wishlist_items`**:
  - `wishlist_items_select` (SELECT, authenticated): `((auth.jwt() ->> 'persona'::text) IS NOT NULL)`
  - `wishlist_items_insert` (INSERT, authenticated): `(creator_id = (auth.jwt() ->> 'persona'::text))`
  - `wishlist_items_update` (UPDATE, authenticated): `((auth.jwt() ->> 'persona'::text) IS NOT NULL)`
  - `wishlist_items_delete` (DELETE, authenticated): `((auth.jwt() ->> 'persona'::text) IS NOT NULL)`
- **`wishlist_reactions`**:
  - `wishlist_reactions_select` (SELECT, authenticated): `((auth.jwt() ->> 'persona'::text) IS NOT NULL)`
  - `wishlist_reactions_insert` (INSERT, authenticated): `(persona_id = (auth.jwt() ->> 'persona'::text))`
  - `wishlist_reactions_update` (UPDATE, authenticated): `(persona_id = (auth.jwt() ->> 'persona'::text))`
  - `wishlist_reactions_delete` (DELETE, authenticated): `(persona_id = (auth.jwt() ->> 'persona'::text))`
- **`wishlist_ratings`**:
  - `wishlist_ratings_select` (SELECT, authenticated): `((auth.jwt() ->> 'persona'::text) IS NOT NULL)`
  - `wishlist_ratings_insert` (INSERT, authenticated): `(persona_id = (auth.jwt() ->> 'persona'::text))`
  - `wishlist_ratings_update` (UPDATE, authenticated): `(persona_id = (auth.jwt() ->> 'persona'::text))`
  - `wishlist_ratings_delete` (DELETE, authenticated): `(persona_id = (auth.jwt() ->> 'persona'::text))`

---

## 3. Local Room Schema
- **`wishes`**: `id` (Long, PK), `text`, `wishDate`, `status`, `creatorId`, `createdAt`, `updatedAt`, `syncStatus`.
- **`reactions`**: Composite PK `["wishId", "personaId"]`, `emoji`.
- **`ratings`**: Composite PK `["wishId", "personaId"]`, `rating`.
- **`sync_queue`**: Auto-increment `id` (Long), `tableName`, `recordId`, `operation`, `payload`, `localTempId`, `createdAt`.
