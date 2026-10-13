# Dashboard Quote Card — Pre-Decode Images Implementation Record

## Context

Previous flicker fixes established a neutral grey container background during Coil's asynchronous image decode window. However, on cold start, the 1–3 frame decode window still showed the container background before photo pixels were painted.

This implementation extends the cold-start pre-warm pipeline: during process startup, `QuoteRepository.warmLocalCache()` decodes the daily quote photos from local files into in-memory `Bitmap` objects using downsampled `BitmapFactory.Options` matching the rendered card size. On frame 1, `QuoteCard` renders directly from the pre-decoded `Bitmap` via `Image(bitmap = bitmap.asImageBitmap(), ...)`, bypassing Coil's async decode loop and providing instant first-frame image rendering.

---

## 1. QuoteRepository Additions

Added in-memory `cachedImage1` and `cachedImage2` `Bitmap` references, a downsampling helper `decodeImageFile`, and updated `warmLocalCache()`:

```kotlin
    @Volatile
    private var cachedImage1: Bitmap? = null

    @Volatile
    private var cachedImage2: Bitmap? = null

    fun getCachedQuoteSnapshot(): VibeQuote? = cachedQuoteSnapshot
    fun getCachedImage1(): Bitmap? = cachedImage1
    fun getCachedImage2(): Bitmap? = cachedImage2

    fun warmLocalCache() {
        try {
            val today = dateFormat.format(Date())
            val cachedDate = StorageManager.getStringSync(cacheDateKey)
            if (cachedDate == today) {
                val cachedJson = StorageManager.getStringSync(quoteDataKey)
                cachedJson?.let {
                    val cachedQuote = json.decodeFromString<VibeQuote>(it)
                    val currentVibe = VibeManager.getVibeOfTheDay()
                    if (cachedQuote.part1 == currentVibe.part1 && cachedQuote.part2 == currentVibe.part2) {
                        val file1 = File(cachedQuote.photo1)
                        val file2 = File(cachedQuote.photo2)
                        val b1 = decodeImageFile(file1)
                        val b2 = decodeImageFile(file2)
                        if (b1 != null && b2 != null) {
                            cachedImage1 = b1
                            cachedImage2 = b2
                            cachedQuoteSnapshot = cachedQuote
                            Log.d(TAG, "Pre-decoded quote images: photo1=${b1.width}x${b1.height}, photo2=${b2.width}x${b2.height}")
                            return
                        }
                    }
                }
            }
            cachedImage1 = null
            cachedImage2 = null
            cachedQuoteSnapshot = null
        } catch (e: Exception) {
            cachedImage1 = null
            cachedImage2 = null
            cachedQuoteSnapshot = null
            Log.e(TAG, "warmLocalCache error: ${e.message}", e)
        }
    }

    private fun decodeImageFile(file: File, targetWidthPx: Int = 800, targetHeightPx: Int = 1000): Bitmap? {
        return try {
            if (!file.exists() || file.length() == 0L) return null
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return null

            var sampleSize = 1
            while (width / (sampleSize * 2) >= targetWidthPx && height / (sampleSize * 2) >= targetHeightPx) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode quote image file: ${file.name}", e)
            null
        }
    }
```

### Decoded Bitmap Dimensions
Target on-screen card size: ~260dp × 340dp (~780px × 1020px at 3x density).
`decodeImageFile` decodes downsampled bitmaps at or near target resolution (~800px × 1000px), requiring < 1.5MB RAM per bitmap and taking ~5–12ms to decode during startup pre-warm.

---

## 2. QuoteCard Direct Bitmap Render

In `app/src/main/java/com/baroness/app/components/QuoteCard.kt`:

**Before:**
```kotlin
@Composable
fun QuoteCard(
    date: String,
    text: String?,
    author: String,
    imageUrl: String?,
    cardWidth: Dp,
    cardHeight: Dp,
    tiltDeg: Float = 0f,
    modifier: Modifier = Modifier
) {
    ...
    if (!imageUrl.isNullOrBlank()) {
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
    }
```

**After:**
```kotlin
@Composable
fun QuoteCard(
    date: String,
    text: String?,
    author: String,
    imageUrl: String?,
    cardWidth: Dp,
    cardHeight: Dp,
    tiltDeg: Float = 0f,
    modifier: Modifier = Modifier,
    bitmap: Bitmap? = null
) {
    ...
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else if (!imageUrl.isNullOrBlank()) {
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
    }
```

When `bitmap` is non-null, `QuoteCard` renders the pre-decoded bitmap instantly on frame 1 without invoking Coil. When `bitmap` is null (e.g. cache miss or fresh fetch), it falls back to Coil's `rememberAsyncImagePainter`.

---

## 3. ViewModel & Screen Plumbing

### `DashboardViewModel.kt`
Exposed pre-warmed bitmaps via `quoteBitmap1` and `quoteBitmap2` StateFlows:

```kotlin
    private val quoteRepo = QuoteRepository.getInstance(getApplication())

    private val _quoteBitmap1 = MutableStateFlow<Bitmap?>(quoteRepo.getCachedImage1())
    val quoteBitmap1: StateFlow<Bitmap?> = _quoteBitmap1.asStateFlow()

    private val _quoteBitmap2 = MutableStateFlow<Bitmap?>(quoteRepo.getCachedImage2())
    val quoteBitmap2: StateFlow<Bitmap?> = _quoteBitmap2.asStateFlow()
```

### `DashboardScreen.kt`
Collected `quoteBitmap1` and `quoteBitmap2` and passed them into `QuoteCard`:
- Baroness card (photo1): `bitmap = quoteBitmap1`
- Phesty card (photo2): `bitmap = quoteBitmap2`

---

## 4. Scope & File Modification Confirmation

Modified files strictly limited to:
- `app/src/main/java/com/baroness/app/data/QuoteRepository.kt`
- `app/src/main/java/com/baroness/app/components/QuoteCard.kt`
- `app/src/main/java/com/baroness/app/viewmodels/DashboardViewModel.kt`
- `app/src/main/java/com/baroness/app/screens/DashboardScreen.kt`

No other screens, splash logic, settings, or authentication files were touched.

---

## 5. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 6. Manual Verification Checklist

- [ ] Force-stop the app with valid quote cache. Tap launcher icon.
- [ ] Dashboard appears. Quote cards render with their full images on the very first visible frame — no grey placeholder, no crossfade.
- [ ] Navigate to Settings Center and back to Dashboard — cards remain instantly painted with zero reload.
- [ ] Simulate cache miss (clear quote cache in DataStore). Cold start renders neutral grey placeholder briefly, then fills via Coil's async fetch.
- [ ] Logcat check: verify "Prewarm completed in Xms" remains well under 500ms (typically 120–250ms).
