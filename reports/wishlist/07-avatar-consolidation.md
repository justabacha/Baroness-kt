# Avatar Loading Consolidation

## Investigation

- An `Application` subclass already exists: `app/src/main/java/com/baroness/app/MainApplication.kt` (`MainApplication`, line 21). Its `onCreate` initializes `SessionTokenCache`, observes process resume for token refresh, creates notification channels, and registers the FCM token (lines 37 onward). It was already registered in `app/src/main/AndroidManifest.xml:65-66` as `.MainApplication`. This differs from the audit's statement that there was no custom Application subclass.
- Before this change, `WishlistHeader.kt:35-39` built a local `ImageLoader` with `respectCacheHeaders(false)`; both avatar `AsyncImage`s passed it explicitly at lines 62 and 81. That header was the only explicit `ImageLoader.Builder`, `ImageLoaderFactory`, or `imageLoader =` override found under `app/src/main`.
- Other `ImageRequest.Builder` uses in `QuoteCard.kt`, `PhotoViewerOverlay.kt`, and `PhotosScreen.kt` customize individual requests and do not supply a different loader. All other avatar Coil composables use the default singleton.
- Some screens use local avatar files from `AvatarRepository`: `ProfileSetupViewModel.kt:53-67` resolves cached paths for Profile Setup, `DashboardViewModel.kt:79,136,195` resolves local paths for Dashboard, and `SettingsCenterScreen.kt:79-92` resolves a path for Settings Center. `ProfileSetupScreen.kt:126` and `ProfileSettingsPage.kt:168` pass their avatar URI to `rememberAsyncImagePainter`. These `filesDir/user_avatars` paths remain ordinary Coil image models and are unaffected by the loader consolidation. `AvatarRepository` and its usages were not changed.
- Coil is `io.coil-kt:coil-compose:2.7.0` (`app/build.gradle.kts:100`); the Coil 2 factory import is `coil.ImageLoaderFactory`.

## Implementation

Modified the existing `MainApplication` at `app/src/main/java/com/baroness/app/MainApplication.kt`; no new Application class or manifest change was needed. It now implements `ImageLoaderFactory` and creates the app-wide loader:

```kotlin
override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .diskCache {
            DiskCache.Builder()
                .directory(filesDir.resolve("image_cache"))
                .maxSizeBytes(25L * 1024 * 1024)
                .build()
        }
        .build()
}
```

The cache is under `filesDir/image_cache`, honors no-cache avatar responses by setting `respectCacheHeaders(false)`, and is capped at 25 MiB. Existing `MainApplication.onCreate` startup behavior was preserved.

The exact loader construction removed from `WishlistHeader.kt` was:

```kotlin
val appContext = LocalContext.current.applicationContext
val imageLoader = remember(appContext) {
    ImageLoader.Builder(appContext)
        .respectCacheHeaders(false)
        .build()
}
```

Also removed `imageLoader = imageLoader` from each of its two `AsyncImage` calls, along with the now-unused `LocalContext`, `remember`, and `ImageLoader` imports. No other wishlist header behavior was changed. The manifest already names `.MainApplication`, so it required no edit.

## Build and test

- `:app:testDebugUnitTest`: **passed**.
- `:app:assembleDebug`: **passed**.
- `git diff --check`: **passed**.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk` (45,902,355 bytes).

## Manual verification checklist for the operator

- While online, open each screen that displays avatars and allow its avatars to load.
- Clear the app cache in Android settings.
- Enable airplane mode and cold-start the app.
- Open each screen that displays avatars.
- Confirm avatars display from the persistent image cache or existing local avatar files.
- Repeat after warming avatars online again, then clear cache and perform another offline cold-start check.
