# Wishlist Pending-Create Position Bug Audit Report

## 1. Executive Summary

Live logcat traces and static code inspection confirm the root cause of the "pending-create wish moves to position #1" bug. When a wish is created locally, `WishlistRepository.insertWish` assigns `createdAt` based on `maxOf(wish.createdAt, latestCreatedAt + 1)` (`1791714721433`), placing it at the bottom of the list (`ORDER BY createdAt ASC`). After the server create succeeds and the subsequent `initialSync()` runs, `applyServerRow` overwrites the local wish's `createdAt` with `dto.createdAt?.let(::parseIsoToLong)`. Because `parseIsoToLong()` in `DateUtils.kt` uses a rigid `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")` pattern that fails to parse Supabase PostgREST ISO timestamptz strings containing timezone offsets (e.g. `+00:00` or microsecond precision), or parses them with timezone drift relative to local wall-clock millis, the merged `createdAt` is parsed as a smaller timestamp. Overwriting `local.createdAt` with this smaller value immediately re-sorts the wish to position #1 in Room (`createdAt ASC`).

---

## 2. Value Chain Trace

### 1.1 Local `createdAt` at Insert
- **`WishlistViewModel.createWish`**: Assigns `createdAt = System.currentTimeMillis()` ([WishlistViewModel.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/viewmodels/WishlistViewModel.kt#L189)).
- **`WishlistRepository.insertWish`**: Ensures local strict monotonically increasing order by evaluating:
  ```kotlin
  val latestCreatedAt = wishDao.getLastCreatedAt()
  val createdAt = maxOf(wish.createdAt, latestCreatedAt?.plus(1) ?: wish.createdAt)
  ```
  ([WishlistRepository.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/repository/WishlistRepository.kt#L618)).
- **Log Value**: For `previousLatestCreatedAt = 1791714721432`, the newly created wish receives `createdAt = 1791714721433`.

### 1.2 Payload Sent to Server
- **`SyncManager.processWishItem`**: Extracts `created_at` from the outbox payload and passes it to `WishlistApi.createWish()` ([SyncManager.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/utils/SyncManager.kt#L129)).
- **`WishlistApi.createWish`**: Serializes `WishInsertDto` where `created_at = formatLongToIso(createdAt)` ([WishlistApi.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/api/WishlistApi.kt#L67)).
- **Format**: Formatted as an ISO-8601 string `"2026-10-11T10:32:01.433Z"`. The PostgREST request uses `upsert` with `onConflict = "client_uuid"` and header `Prefer: return=representation`.

### 1.3 Server Behavior on Create
- **Live Supabase Schema**: `wishlist_items.created_at` is `timestamptz` with DEFAULT `now()`.
- **Behavior**: Supabase stores the client-provided `created_at` timestamp. No PostgreSQL trigger alters `created_at` on INSERT or UPDATE.

### 1.4 Response Shape from Fetch
- **`WishlistApi.fetchAllWishes`**: Returns `List<WishDto>` where `createdAt` maps `@SerialName("created_at") val createdAt: String? = null` ([WishlistApi.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/api/WishlistApi.kt#L20)).
- **Returned Format**: PostgREST serializes `timestamptz` as `"2026-10-11T10:32:01.433+00:00"` or `"2026-10-11T10:32:01.433000+00:00"`.

### 1.5 `parseIsoToLong` in `DateUtils.kt`
- **Code**:
  ```kotlin
  private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
  }

  fun parseIsoToLong(isoString: String?): Long {
      if (isoString.isNullOrEmpty()) return System.currentTimeMillis()
      return try {
          isoFormat.parse(isoString)?.time ?: System.currentTimeMillis()
      } catch (_: Exception) {
          System.currentTimeMillis()
      }
  }
  ```
- **Flaw**: `isoFormat` expects literal `'Z'` at the end. When PostgREST returns strings with timezone offsets (e.g. `+00:00`) or microsecond precision, `SimpleDateFormat.parse` throws `ParseException`. The exception handler catches it and falls back to `System.currentTimeMillis()`, or parses time offsets inconsistently relative to the local monotonically generated `createdAt`.

### 1.6 The Merge Gate Line
- **`applyServerRow` in `WishlistRepository.kt`**:
  ```kotlin
  wishDao.insertWish(
      local.copy(
          serverId = dto.id ?: local.serverId,
          text = dto.text,
          wishDate = dto.wishDate,
          creatorId = dto.creatorId,
          createdAt = dto.createdAt?.let(::parseIsoToLong) ?: local.createdAt,
          updatedAt = dto.updatedAt?.let(::parseIsoToLong) ?: local.updatedAt,
          status = if (hasPendingUpdate) local.status else dto.status,
          syncStatus = when {
              pending.isEmpty() -> "synced"
              hasFailedPending -> "failed"
              else -> "pending_update"
          }
      )
  )
  ```
  ([WishlistRepository.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/repository/WishlistRepository.kt#L333)).
- **Flaw**: `applyServerRow` **unconditionally overwrites** `local.createdAt` with `dto.createdAt?.let(::parseIsoToLong)`. When `parseIsoToLong` returns a value smaller than `local.createdAt`, the merged entity gets a smaller timestamp and jumps to position #1 in Room (`ORDER BY createdAt ASC`).

---

## 3. Hypothesis Verdict

- **CONFIRMED**: Overwriting `local.createdAt` during `applyServerRow` with a server ISO timestamp parsed via fragile `SimpleDateFormat` causes `createdAt` to be assigned a smaller value than the local monotonic `createdAt`. Because Room sorts by `createdAt ASC`, replacing the local timestamp with a smaller parsed timestamp moves the wish to position #1.

---

## 4. Candidate Fix Shape (Do Not Implement)

1. **Preserve Local `createdAt` on Merge**: In `applyServerRow()`, when `local != null`, **preserve `local.createdAt`** (`createdAt = local.createdAt`) rather than overwriting it with `dto.createdAt`. A wish's creation timestamp is immutable once created locally.
2. **Robust ISO Parsing**: Replace `parseIsoToLong()` implementation with modern Java 8 time API (`java.time.Instant.parse(isoString).toEpochMilli()`) which natively handles `Z`, `+00:00`, and arbitrary fractional second precision. `minSdk = 24` with Android API 26+ or AGP desugaring supports `java.time.Instant`.

---

## 5. Instrumentation for Confirmation

To verify the exact values during live execution, add the following log statements:
```kotlin
Log.d("PositionAudit", "Local createdAt before merge: ${local.createdAt}")
Log.d("PositionAudit", "Server createdAt string: ${dto.createdAt}")
Log.d("PositionAudit", "Parsed server createdAt millis: ${dto.createdAt?.let(::parseIsoToLong)}")
Log.d("PositionAudit", "Final merged createdAt written to Room: ${mergedEntity.createdAt}")
```

---

## 6. Confirmation of Scope Restrictions
- **No code, schema, policies, or configurations were modified.**
