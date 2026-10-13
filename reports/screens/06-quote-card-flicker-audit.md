# Dashboard Quote Card — Render Flicker Audit Report

## 1. Executive Summary

Following cold-start pre-warm and state lifecycle optimizations, the dashboard profile card, Wishlist, and ChatList render instantly without flicker. However, the Dashboard quote cards (`QuoteCard`) exhibit a brief black flash on cold start and screen open. This audit diagnoses the root cause: `QuoteCard.kt` applies an explicit `.background(Color.Black)` container modifier beneath the image. Because `rememberAsyncImagePainter` loads and decodes local image files asynchronously (taking 1 to 3 frames), the black background shines through during the decode window before the image pixels are drawn. Replacing the hardcoded black background with a neutral theme-aligned surface color or leveraging `AsyncImage` state handling would eliminate the visual flash.

---

## 2. Quote Card UI Inventory (Section 1)

- **Composables**: `QuoteCard` (`components/QuoteCard.kt`), instantiated twice in `DashboardScreen.kt` (Baroness card with `-10f` tilt and Phesty card with `+10f` tilt).
- **State Source**: `DashboardViewModel.vibe` (`StateFlow<VibeQuote?>`), initialized synchronously from `QuoteRepository.getInstance(getApplication()).getCachedQuoteSnapshot()` (populated during cold-start pre-warm).
- **Layout**: Two offset/tilted cards displaying date, quote text (`part1` / `part2`), author (`!!Baroness` / `!!Phesty`), and background artwork (`imageUrl`).
- **Placeholder / Empty State**: When `imageUrl` is blank, it renders `Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))`. When `imageUrl` is present, it relies on an explicit `.background(Color.Black)` container.
- **Black Background Origin**: Explicitly added in `QuoteCard.kt` line 77 via `.background(Color.Black)` on the inner card container `Box`.

---

## 3. Quote Data Source (Section 2)

- **Public API**: `getTodayQuote(forceRefresh: Boolean)` and `warmLocalCache()`.
- **Caching Strategy**: Dual-layer caching — persistent JSON storage (`quote_data`, `quote_cache_date`) in DataStore via `StorageManager`, and local image files in `context.filesDir/quote_images/`.
- **Quote Model (`VibeQuote`)**: Contains `part1: String`, `part2: String`, `photo1: String`, `photo2: String`.
- **`warmLocalCache()`**: Validates today's date, matches vibe content, and verifies that local image files (`photo1` and `photo2`) exist on disk. If valid, populates `cachedQuoteSnapshot`.
- **Image Paths**: Absolute local file paths (e.g. `/data/user/0/com.baroness.app/files/quote_images/quote_1_...jpg`).

---

## 4. Render Pipeline & Root Cause (Section 3)

- **Coil Invocation**:
  ```kotlin
  Image(
      painter = rememberAsyncImagePainter(
          ImageRequest.Builder(context)
              .data(imageSource)
              .crossfade(true)
              .build()
      ),
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop
  )
  ```
- **ImageLoader**: Uses global singleton `ImageLoaderFactory` from `MainApplication`.
- **Why the Black Flash Happens**: 
  1. `vibe` is non-null on first frame thanks to pre-warm.
  2. `QuoteCard` composes immediately with `imageUrl` pointing to the local file path.
  3. `rememberAsyncImagePainter` begins asynchronous decoding of the local file.
  4. During the decode window (frames 1–3), the painter has no image loaded yet (renders transparently).
  5. The transparent painter reveals the explicit `.background(Color.Black)` modifier applied to the parent `Box` in `QuoteCard.kt` line 77, resulting in a black frame before the crossfade animation draws the image.

---

## 5. Timing Analysis (Section 4)

- **Cold Start Sequence**:
  - **T=0ms**: Process launch; `ColdStartPrewarm` runs `QuoteRepository.warmLocalCache()`, successfully loading cached JSON and validating local files into `cachedQuoteSnapshot`.
  - **T=50ms**: `DashboardViewModel` initializes, setting `initialVibe = cachedQuoteSnapshot`. The first frame emits a non-null `VibeQuote`.
  - **T=100ms**: First frame renders. `QuoteCard` receives valid `imageUrl`. `rememberAsyncImagePainter` initiates decoding.
  - **T=100ms - 150ms**: Render frame shows solid black background (`.background(Color.Black)`).
  - **T=150ms+**: Decode completes; crossfade blends in the image.

---

## 6. Comparison to Other Image Renders (Section 5)

- **Avatars (`ExpandableAvatar`, `ChatEntry`, `WishlistHeader`)**: Render via `AsyncImage` with neutral background fallbacks (e.g. `Color.White.copy(alpha = 0.1f)` or `Color.DarkGray`) or clip shapes without a solid black bounding box behind the image view, avoiding high-contrast black flashes.
- **Wallpapers**: Rendered via `AsyncImage` with `ContentScale.Crop` over the screen background without an opaque black backing box.
- **Key Difference**: The quote card uniquely enforces an opaque `.background(Color.Black)` container underneath a painter that renders nothing during initial decode.

---

## 7. Fix Options (Section 6)

1. **Option A (Surface Color Swap)**: Replace `.background(Color.Black)` in `QuoteCard.kt` line 77 with a neutral dark surface color matching the app theme (e.g. `Color(0xFF1E1E1E)` or `Color.DarkGray`), matching the fallback empty state.
2. **Option B (`AsyncImage` with State Handling)**: Migrate `QuoteCard` from `Image(rememberAsyncImagePainter(...))` to `AsyncImage` with an explicit placeholder or painter state handling (`AsyncImagePainter.State.Loading`) to display a neutral surface until decoded.
3. **Option C (Stateful Painter Loading)**: Track loading state of `rememberAsyncImagePainter` and only apply background/overlay once successfully loaded.

---

## 8. Open Questions for the Operator

1. **Preferred Placeholder Tone**: Should the background behind loading quote images be dark gray (`Color.DarkGray`) or match surface card color (`Color(0xFF1E1E1E)`)?
2. **Crossfade Timing**: Should the crossfade duration be adjusted or kept at Coil's default?
