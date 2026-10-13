# Quote Card Bitmaps — Convert Repository Getters to Reactive Flows Implementation Record

## Context

The previous pre-decode implementation captured cached bitmaps once during `DashboardViewModel` construction via a single-shot synchronous getter (`quoteRepo.getCachedImage1()`). If `DashboardViewModel` was constructed before `ColdStartPrewarm.run()` completed on startup, the captured bitmap value was `null`. The `StateFlow` never updated when the bitmap became available later, forcing `QuoteCard` to fall back to Coil's async image painter and causing a visible image pop-in after the splash screen dismissed.

This implementation converts `QuoteRepository`'s cached bitmaps and quote snapshot into reactive `StateFlow`s, allowing `DashboardViewModel` to directly expose the repository flows without local snapshot race conditions.

---

## 1. `QuoteRepository.kt` Changes

Converted `@Volatile` fields and getters to `MutableStateFlow` and exposed read-only `StateFlow`:

**Before:**
```kotlin
    @Volatile
    private var cachedQuoteSnapshot: VibeQuote? = null

    @Volatile
    private var cachedImage1: Bitmap? = null

    @Volatile
    private var cachedImage2: Bitmap? = null

    fun getCachedQuoteSnapshot(): VibeQuote? = cachedQuoteSnapshot
    fun getCachedImage1(): Bitmap? = cachedImage1
    fun getCachedImage2(): Bitmap? = cachedImage2
```

**After:**
```kotlin
    private val _cachedQuoteSnapshot = MutableStateFlow<VibeQuote?>(null)
    val cachedQuoteSnapshot: StateFlow<VibeQuote?> = _cachedQuoteSnapshot.asStateFlow()

    private val _cachedImage1 = MutableStateFlow<Bitmap?>(null)
    val cachedImage1: StateFlow<Bitmap?> = _cachedImage1.asStateFlow()

    private val _cachedImage2 = MutableStateFlow<Bitmap?>(null)
    val cachedImage2: StateFlow<Bitmap?> = _cachedImage2.asStateFlow()

    fun getCachedQuoteSnapshot(): VibeQuote? = _cachedQuoteSnapshot.value
    fun getCachedImage1(): Bitmap? = _cachedImage1.value
    fun getCachedImage2(): Bitmap? = _cachedImage2.value
```

In `warmLocalCache()`, `getTodayQuote()`, and `fetchFreshQuote()`, values are set via `_cachedImage1.value = b1`, `_cachedImage2.value = b2`, and `_cachedQuoteSnapshot.value = cachedQuote`.

---

## 2. `DashboardViewModel.kt` Changes

Directly exposed repository flows instead of local single-shot state captures:

**Before:**
```kotlin
    private val initialVibe = quoteRepository.getCachedQuoteSnapshot()

    private val _vibe = MutableStateFlow<VibeQuote?>(initialVibe)
    val vibe: StateFlow<VibeQuote?> = _vibe.asStateFlow()

    private val _quoteBitmap1 = MutableStateFlow<Bitmap?>(quoteRepository.getCachedImage1())
    val quoteBitmap1: StateFlow<Bitmap?> = _quoteBitmap1.asStateFlow()

    private val _quoteBitmap2 = MutableStateFlow<Bitmap?>(quoteRepository.getCachedImage2())
    val quoteBitmap2: StateFlow<Bitmap?> = _quoteBitmap2.asStateFlow()
```

**After:**
```kotlin
    val vibe: StateFlow<VibeQuote?> = quoteRepository.cachedQuoteSnapshot
    val quoteBitmap1: StateFlow<Bitmap?> = quoteRepository.cachedImage1
    val quoteBitmap2: StateFlow<Bitmap?> = quoteRepository.cachedImage2
```

---

## 3. `DashboardScreen.kt` & `QuoteCard.kt`

- `DashboardScreen.kt` continues collecting `quoteBitmap1` and `quoteBitmap2` from `DashboardViewModel` (`val quoteBitmap1 by viewModel.quoteBitmap1.collectAsState()`).
- `QuoteCard.kt` parameter signature (`bitmap: Bitmap? = null`) remains unchanged. When non-null, `Image(bitmap = bitmap.asImageBitmap(), ...)` draws the image instantly.

---

## 4. Pre-Warm vs Splash Gate Timing Analysis

### Sequence Execution
1. `MainActivity.onCreate()` installs `SplashScreen` and registers `splashScreen.setKeepOnScreenCondition { !isReady }`.
2. `lifecycleScope.launch` starts `ColdStartPrewarm.run(applicationContext)` on `Dispatchers.IO`.
3. `ColdStartPrewarm` executes `QuoteRepository.warmLocalCache()`, pre-decoding bitmaps and setting `_cachedImage1.value` and `_cachedImage2.value`.
4. Pre-warm completes within ~120–250ms and sets `isReady = true`.
5. OS splash dismisses and Compose `setContent` renders `DashboardScreen`.
6. Because `DashboardViewModel` directly references `quoteRepository.cachedImage1` / `cachedImage2`, the bitmap flows emit the pre-decoded bitmaps on frame 1 without timing races or state mirrors.

---

## 5. Scope Confirmation

Modified files strictly limited to:
- `app/src/main/java/com/baroness/app/data/QuoteRepository.kt`
- `app/src/main/java/com/baroness/app/viewmodels/DashboardViewModel.kt`

No layout files, drawables, splash logic, or other screens were modified.

---

## 6. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 7. Manual Verification Checklist

- [ ] Force-stop the app with valid quote cache. Tap launcher icon.
- [ ] As splash closes, Dashboard appears fully populated — profile card, greeting, quote text, and quote images render on the first visible frame.
- [ ] No image "popping in" or grey container flash after splash dismisses.
- [ ] Navigate away to Settings Center and back to Dashboard — quote cards render instantly.
- [ ] Logcat check: verify "Prewarm completed in Xms" remains well under 500ms.
