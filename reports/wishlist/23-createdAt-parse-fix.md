# Wishlist `createdAt` Parse and Merge Fix

## Changes

`DateUtils.kt` now accepts PostgREST and Realtime timestamp forms, converts one- to nine-digit fractional seconds to milliseconds, normalizes UTC and numeric offsets, parses strictly, and logs a warning containing only the timestamp when parsing fails. The fallback remains the current epoch time.

Exact implementation:

```kotlin
private val timestampLogger = Logger.getLogger("DateUtils")
private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private val timestampPattern = Regex(
    "^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}(?::?\\d{2})?)$"
)

fun parseIsoToLong(isoString: String?): Long {
    if (isoString.isNullOrBlank()) return timestampFallback(isoString)

    val match = timestampPattern.matchEntire(isoString) ?: return timestampFallback(isoString)
    val date = match.groupValues[1]
    val time = match.groupValues[2]
    val fraction = match.groupValues[3]
    val offset = match.groupValues[4]
    val millis = fraction.padEnd(3, '0').take(3)
    val normalizedOffset = when {
        offset == "Z" -> "+0000"
        offset.length == 3 -> "${offset}00"
        else -> offset.replace(":", "")
    }
    val normalized = "$date" + "T" + "$time" + "." + "$millis" + normalizedOffset
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val parsed = formatter.parse(normalized, position)
    return if (parsed != null && position.index == normalized.length) {
        parsed.time
    } else {
        timestampFallback(isoString)
    }
}

private fun timestampFallback(isoString: String?): Long {
    timestampLogger.warning("Unable to parse timestamp: $isoString")
    return System.currentTimeMillis()
}
```

Core library desugaring was **not** enabled. The parser uses `SimpleDateFormat` with normalized, explicit UTC offsets, so no build configuration change was needed. The existing `formatLongToIso` implementation remains unchanged.

In `WishlistRepository.applyServerRow`, the existing-local-row copy now uses:

```kotlin
createdAt = local.createdAt,
```

The server timestamp continues to populate `createdAt` when there is no local row. `updatedAt` merge behavior is unchanged.

## Timestamp format evidence

The format divergence is **inferred from the log evidence recorded in** `reports/wishlist/22-pending-create-position-audit.md`: PostgREST/fetch showed space-separated timestamps with numeric offsets (including microseconds), while Realtime showed `T`-separated timestamps ending in `Z`. Supabase MCP schema/catalog inspection cannot observe the HTTP response `Content-Type` or compare the exact serialized value delivered by both transports. Therefore the exact response `Content-Type` was not confirmed here; this report does not claim a measured value.

## Files changed

- `app/src/main/java/com/baroness/app/utils/DateUtils.kt` — robust timestamp parsing and warning on fallback.
- `app/src/main/java/com/baroness/app/repository/WishlistRepository.kt` — preserve local `createdAt` when merging an existing row.
- `app/src/test/java/com/baroness/app/utils/DateUtilsTest.kt` — timestamp format, precision, offset, expected epoch millis, and fallback-warning tests.
- `reports/wishlist/23-createdAt-parse-fix.md` — implementation evidence, validation, and operator checklist.

## Validation

Unit tests cover:

- `"2026-10-11 10:42:19.599+00"` → `1791715339599`
- `"2026-10-11 10:42:19.599000+00"` → `1791715339599`
- `"2026-10-11T10:42:19.599Z"` → `1791715339599`
- `"2026-10-11T10:42:19.599+00:00"` → `1791715339599`
- `null` → non-null fallback and a captured warning containing the timestamp value (`null`)

Build and unit-test results: `:app:testDebugUnitTest` and `:app:assembleDebug` both **passed**.

APK path: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`

No device or adb commands were run.

## Manual verification checklist for the operator

1. Create a wish offline. It renders at the correct position (bottom).
2. Bring network back. The wish stays at the same position throughout sync and `initialSync`.
3. Peer device sees the wish at the correct position.
4. Cold start. Wish is still at the correct position on both devices.
5. A wish already mis-sorted from before this fix may remain mis-sorted; pull to refresh to reapply server data. If it remains mis-sorted afterward, report back.
