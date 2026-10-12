# Expandable Avatar Feature — Pre-Implementation Audit Report

## 1. Executive Summary

This audit evaluates the codebase of the Baroness Android application in preparation for introducing a tap-to-expand profile avatar feature. 

Key Findings:
- **Call-Site Inventory**: Identified **9 total avatar render sites** across the application. **6 sites** are confirmed targets for the tap-to-expand feature, **2 sites** are avatar pickers (editing previews) which must remain excluded (launching image picker on tap), and **1 site** is an in-app notification banner which navigates to a target route on tap.
- **Coil Sizing & Downsampling Risk**: The global `ImageLoader` is defined in `MainApplication.kt` with a 25MB disk cache. `AsyncImage` and `rememberAsyncImagePainter` default to downsampling bitmaps to match layout constraints (e.g., 36.dp to 54.dp). Reusing these cached models in a full-screen overlay without explicit `size(Size.ORIGINAL)` or `precision(Precision.EXACT)` in `ImageRequest.Builder` will serve a low-resolution bitmap, resulting in blurriness.
- **uCrop Resolution**: Avatar uploads are cropped using `AvatarCrop.kt` configured with `withMaxResultSize(1024, 1024)` at 90% JPEG quality. Source image data on disk and in Supabase storage has high-resolution potential (1024x1024 px).
- **Local vs Remote Models**: `AvatarRepository` downloads and caches remote user avatars locally under `context.filesDir/user_avatars/avatar_<timestamp>.jpg`. `DashboardViewModel` and `SettingsCenterScreen` resolve remote URLs to local file paths when available offline. Coil handles both `https://` URLs and local file paths (`/data/...`) transparently.
- **Overlay Pattern**: Existing modal dialogs (`ConfirmModal`, `RatingModal`, `EmojiPicker`) use `androidx.compose.ui.window.Dialog` with `usePlatformDefaultWidth = false`. Full-screen image viewing (`PhotoViewerOverlay`) uses a full-screen `Box` with `Color.Black.copy(alpha = ...)` scrim, swipe-to-dismiss drag gestures, and enter/exit fade and slide animations.

---

## 2. Full Call-Site Inventory

| File | Screen / Component | Coil API | Avatar Source | Size | Shape | Currently Tappable? | Feature Scope |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `screens/DashboardScreen.kt:181` | Dashboard Screen (Header) | `rememberAsyncImagePainter` | `userProfile?.avatar` | Responsive (`54.dp` to `68.dp`) | `CircleShape` | **No** | **IN SCOPE** |
| `components/ChatEntry.kt:56` | Chat List Item | `AsyncImage` | `conversation.avatarUrl` | `48.dp` | `CircleShape` | **No** (Row click opens room) | **IN SCOPE** |
| `screens/ChatRoomScreen.kt:710` | Chat Room Header | `AsyncImage` | `participant?.avatarUrl` | `38.dp` | `CircleShape` | **No** | **IN SCOPE** |
| `components/chat/MessageBubble.kt:502` | Chat Room Message Bubble (`AvatarIsland`) | `AsyncImage` | `participant?.avatarUrl` | `36.dp` | `CircleShape` | **No** | **IN SCOPE** |
| `components/wishlist/WishlistHeader.kt:50` | Wishlist Header (Phesty Avatar) | `AsyncImage` | `avatarP` | `44.dp` | `CircleShape` | **No** | **IN SCOPE** |
| `components/wishlist/WishlistHeader.kt:68` | Wishlist Header (Baroness Avatar) | `AsyncImage` | `avatarB` | `44.dp` | `CircleShape` | **No** | **IN SCOPE** |
| `screens/ProfileSetupScreen.kt:145` | Profile Setup Screen | `rememberAsyncImagePainter` | `avatarUri` | `100.dp` | `CircleShape` | **Yes** (Launches Image Picker) | **EXCLUDED** |
| `screens/settings/ProfileSettingsPage.kt:187` | Profile Settings Page | `rememberAsyncImagePainter` | `avatarUri` | `96.dp` | `CircleShape` | **Yes** (Launches Image Picker) | **EXCLUDED** |
| `components/notification/InAppNotification.kt:61` | In-App Notification Banner | `AsyncImage` | `data.avatarUrl` | `48.dp` | `CircleShape` | **Yes** (Navigates to route) | **EXCLUDED** |

*Note: Non-avatar image components (such as `FloatingMenu.kt` action icons, `InAppMiniPlayer.kt` album art, `PhotosScreen.kt` gallery items, and `WishlistInput.kt` icons) were inspected and verified not to be user profile avatars.*

---

## 3. Coil Configuration and Decode Behavior

### 3.1 ImageLoader Setup
The application defines a global `ImageLoader` in `MainApplication.kt` implementing `coil.ImageLoaderFactory`:

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

- **Cache Policy**: `respectCacheHeaders(false)` forces disk caching regardless of HTTP cache headers. Disk cache limit is 25MB in `filesDir/image_cache`.
- **Memory Cache**: Standard Coil memory cache configuration.

### 3.2 Downsampling and Resolution Analysis
- **Question**: *If an `AsyncImage` currently renders at 48.dp, does Coil's default behavior serve a downsampled bitmap that would look blurry if reused at larger sizes?*
- **Answer**: **Yes.** When `AsyncImage` or `rememberAsyncImagePainter` renders an image without explicit size overrides, Coil resolves target size from the layout constraints of the target composable (e.g. 48.dp x 48.dp = ~144px at 3x density). Coil downsamples the bitmap in memory during decoding to save RAM.
- **Impact on Expandable Overlay**: If a full-screen overlay uses the same avatar URL or local path model in an `AsyncImage`, it **must** explicitly configure the `ImageRequest.Builder`:
  ```kotlin
  ImageRequest.Builder(LocalContext.current)
      .data(avatarModel)
      .size(coil.size.Size.ORIGINAL)
      .precision(coil.size.Precision.EXACT)
      .crossfade(true)
      .build()
  ```
  This ensures Coil fetches or decodes the full-resolution asset from disk or network instead of reusing a downsampled memory-cached bitmap.

---

## 4. M3 Theme Configuration

### 4.1 Theme Setup
- Defined in `ui/theme/Theme.kt` via `BaronessAppTheme`.
- Wraps M3 `MaterialTheme` alongside custom composition locals `LocalBaronessTheme` (`AppTheme`) and `LocalBaronessTypography` (`ChatTypography`).
- Light & Dark schemes configured using `darkColorScheme` and `lightColorScheme` (Dynamic color is disabled; custom theme presets `LavenderTheme`, `MoonlightTheme`, `GoldenTheme`, `RoseTheme`, `OceanTheme` dictate colors).

### 4.2 Scrim & Color Roles
- `MaterialTheme.colorScheme.scrim` is available via M3 defaults (`Color.Black` / `0xFF000000`).
- Custom theme background colors (`bgStart`, `bgEnd`) and glow colors (`glowColor`) are supplied by `LocalBaronessTheme.current`.
- Existing modal overlays in the app use explicit dark translucent scrims: `Color.Black.copy(alpha = 0.7f)` to `Color.Black.copy(alpha = 0.85f)`.

### 4.3 Edge-To-Edge and Insets
- `enableEdgeToEdge()` is enabled globally in `MainActivity.onCreate()`.
- Screen layout insets use `statusBarsPadding()`, `navigationBarsPadding()`, and `WindowInsets.navigationBars`.

---

## 5. Existing Dialog / Overlay Patterns

The codebase exhibits two distinct overlay/modal patterns:

### Pattern A: Form & Confirmation Modals (`ConfirmModal.kt`, `RatingModal.kt`)
- **Technology**: `androidx.compose.ui.window.Dialog` with `usePlatformDefaultWidth = false`.
- **Card Container**: Centered `Box` with `fillMaxWidth(0.85f)`, background `Color(0xFF1A1A1A)`, `RoundedCornerShape(24.dp)`, and 1.dp border `Color.White.copy(alpha = 0.6f)`.
- **Dismiss Control**: `RatingModal` includes an explicit top-right close "×" button:
  ```kotlin
  Box(
      modifier = Modifier
          .size(28.dp)
          .background(Color(0x4D464545), RoundedCornerShape(14.dp))
          .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
          .clickable(onClick = onDismiss)
  )
  ```

### Pattern B: Full-Screen Media Viewer (`PhotoViewerOverlay.kt`)
- **Technology**: Full-screen `Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = backgroundAlpha)))`.
- **Dismiss Controls**:
  - Top app bar back arrow button.
  - Swipe/drag-down-to-dismiss gesture (`detectDragGestures` tracking vertical offset and adjusting background alpha in real-time).
- **Animations**: `fadeIn()` / `fadeOut()` and `slideInVertically()` / `slideOutVertically()`.

---

## 6. uCrop Output Configuration

Configured in `utils/AvatarCrop.kt`:

```kotlin
object AvatarCrop {
    fun createIntent(context: Context, sourceUri: Uri): Intent {
        val destination = File(
            context.cacheDir,
            "avatar_crop_${System.currentTimeMillis()}.jpg"
        )
        val destinationUri = Uri.fromFile(destination)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
        }

        return UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1024, 1024)
            .withOptions(options)
            .getIntent(context)
    }
}
```

- **Output Aspect Ratio**: `1:1` square.
- **Max Output Resolution**: `1024 x 1024` pixels.
- **Compression**: `JPEG` at `90%` quality.
- **Consolidation**: Single location (`AvatarCrop.kt`).

---

## 7. AvatarRepository Interaction

Defined in `data/AvatarRepository.kt`:

```kotlin
class AvatarRepository(private val context: Context) {
    suspend fun getCachedAvatar(avatarUrl: String?): String?
    suspend fun clearCache()
}
```

- **Storage Directory**: `context.filesDir/user_avatars/avatar_<timestamp>.jpg`.
- **Key Tracking**: Persists `avatar_local_path` and `avatar_cached_url` in `StorageManager`.
- **Usage Site Resolution**: `DashboardViewModel` and `SettingsCenterScreen` execute `avatarRepository.getCachedAvatar(...)` asynchronously to resolve remote URLs to local absolute file paths.
- **Coil Loader Transparency**: Coil accepts `String` model inputs regardless of scheme (`https://...` or `/data/user/0/.../files/user_avatars/...`). The expandable overlay will work identically with both remote URLs and local file paths.

---

## 8. Navigation and Back Handling

- **App Navigation**: Jetpack Navigation Compose (`NavHost`, `rememberNavController()`) in `MainActivity.kt`.
- **Back System**: `BackHandler` (`androidx.activity.compose.BackHandler`) is extensively used in drawers, sheets, and screens (`AskFridaySheet`, `ChatContextMenu`, `ChatListScreen`, `ChatRoomScreen`).
- **Dialog System**: Wrapping the overlay in `androidx.compose.ui.window.Dialog` with `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)` creates an independent window above `NavHost`, `GlobalDrawer`, and `InAppMiniPlayer`, handling system back key events through `onDismissRequest`.

---

## 9. Per-Call-Site Details & Metadata Scope

### 9.1 Dashboard Screen
- **Location**: `screens/DashboardScreen.kt:181`
- **Current Composable**:
  ```kotlin
  Image(
      painter = rememberAsyncImagePainter(userProfile?.avatar),
      contentDescription = "Avatar",
      modifier = Modifier
          .size(avatarSize)
          .clip(CircleShape)
          .border(1.5.dp, Colors.purpleAccent, CircleShape),
      contentScale = ContentScale.Crop
  )
  ```
- **In-Scope Metadata**:
  - User Display Name: `userProfile?.displayName` (e.g. `"Phesty"`)
  - Persona / Identity: `userProfile?.persona`
  - Screen Context: Greeting (`greeting`), current time string (`currentTime`), weather summary (`weather?.suggestion`).

### 9.2 Chat List Item
- **Location**: `components/ChatEntry.kt:56`
- **Current Composable**:
  ```kotlin
  Box(
      modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.1f))
          .border(1.5.dp, Colors.accent, CircleShape),
      contentAlignment = Alignment.Center
  ) {
      if (!conversation.avatarUrl.isNullOrEmpty()) {
          AsyncImage(
              model = conversation.avatarUrl,
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
          )
      } else {
          val initial = conversation.displayName.take(1).uppercase()
          PhestyText(text = initial, style = chatTypography.title, color = Color.White)
      }
  }
  ```
- **In-Scope Metadata**:
  - Participant Display Name: `conversation.displayName`
  - Last Message: `conversation.lastMessage`
  - Timestamp: `conversation.updatedAt`
  - Unread Badge Count: `conversation.unreadCount`

### 9.3 Chat Room Header
- **Location**: `screens/ChatRoomScreen.kt:710`
- **Current Composable**:
  ```kotlin
  Box(
      modifier = Modifier
          .size(38.dp)
          .border(1.5.dp, Color.Black.copy(alpha = 0.75f), CircleShape)
          .padding(0.5.dp)
          .clip(CircleShape)
          .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
  ) {
      if (participant?.avatarUrl != null) {
          AsyncImage(
              model = participant.avatarUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
          )
      } else if (participant?.id == "friday") {
          AsyncImage(
              model = "https://img.icons8.com/fluency/48/artificial-intelligence.png",
              contentDescription = null,
              modifier = Modifier.fillMaxSize()
          )
      }
  }
  ```
- **In-Scope Metadata**:
  - Participant Name: `participant?.displayName`
  - Participant ID: `participant?.id`
  - Presence / Status: Online / Friday status indicator.

### 9.4 Chat Room Message Bubble
- **Location**: `components/chat/MessageBubble.kt:502` (`AvatarIsland`)
- **Current Composable**:
  ```kotlin
  Box(
      modifier = modifier
          .size(36.dp)
          .border(borderWidth, borderColor, CircleShape)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.1f))
  ) {
      if (participant?.avatarUrl != null) {
          AsyncImage(
              model = participant.avatarUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
          )
      } else {
          val fallbackUrl = if (isFriday) "https://img.icons8.com/fluency/48/artificial-intelligence.png" else "https://img.icons8.com/fluency/48/user-male-circle.png"
          AsyncImage(
              model = fallbackUrl,
              contentDescription = null,
              modifier = Modifier.fillMaxSize()
          )
      }
  }
  ```
- **In-Scope Metadata**:
  - Participant Name: `participant?.displayName`
  - Message Details: `message.content`, `message.createdAt` (formatted timestamp), `message.status` (`SENT`, `DELIVERED`, `READ`).

### 9.5 Wishlist Header
- **Location**: `components/wishlist/WishlistHeader.kt:50, 68`
- **Current Composable**:
  ```kotlin
  // Phesty Avatar
  Box(
      modifier = Modifier
          .size(44.dp)
          .border(2.dp, Color(0xFF34C759), CircleShape)
          .clip(CircleShape)
          .background(Color(0xFF2A2A2A)),
      contentAlignment = Alignment.Center
  ) {
      AsyncImage(
          model = avatarP ?: "https://img.icons8.com/color/96/test-account.png",
          contentDescription = "Phesty",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
      )
  }

  // Baroness Avatar
  Box(
      modifier = Modifier
          .size(44.dp)
          .offset(x = (-10).dp)
          .zIndex(1f)
          .border(2.dp, Color(0xFF4285F4), CircleShape)
          .clip(CircleShape)
          .background(Color(0xFF2A2A2A)),
      contentAlignment = Alignment.Center
  ) {
      AsyncImage(
          model = avatarB ?: "https://img.icons8.com/color/96/test-account.png",
          contentDescription = "Baroness",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
      )
  }
  ```
- **In-Scope Metadata**:
  - Persona Identity: `"Phesty"` / `"Baroness"`
  - Wishlist Goal Stats: `stats.total` (TOTAL GOALS), `stats.dusted` (DUSTED).

### 9.6 Settings Center
- **Location**: `screens/settings/SettingsCenterScreen.kt:201`
- **Current Composable**:
  ```kotlin
  if (userProfile?.avatar != null) {
      Image(
          painter = rememberAsyncImagePainter(userProfile.avatar),
          contentDescription = "Avatar",
          modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
          contentScale = ContentScale.Crop
      )
  }
  ```
- **In-Scope Metadata**:
  - User Display Name: `userProfile?.displayName`
  - Identity Label: `userProfile?.persona`

---

## 10. Confirmed Scope Summary

### 10.1 In-Scope Target Sites (6 Sites)
1. Dashboard screen header avatar (`DashboardScreen.kt`)
2. Chat list item peer avatar (`ChatEntry.kt`)
3. Chat room header participant avatar (`ChatRoomScreen.kt`)
4. Chat room message bubble participant avatar (`MessageBubble.kt`)
5. Wishlist header Phesty avatar (`WishlistHeader.kt`)
6. Wishlist header Baroness avatar (`WishlistHeader.kt`)
7. Settings center own avatar (`SettingsCenterScreen.kt`)

### 10.2 Excluded Sites (3 Sites)
1. Profile Setup screen avatar preview (`ProfileSetupScreen.kt` — opens crop/picker on tap)
2. Profile Settings page avatar preview (`ProfileSettingsPage.kt` — opens crop/picker on tap)
3. In-App Notification banner avatar (`InAppNotification.kt` — opens target screen route on tap)

---

## 11. Open Decisions for Operator / Implementation Phase

1. **Touch Propagation on Chat List Item (`ChatEntry.kt`)**:
   - The entire `ChatEntry` container currently has a `.clickable(onClick = onClick)` modifier that navigates into the chat room.
   - Adding a `.clickable` modifier to the 48.dp avatar `Box` will intercept taps on the avatar to expand the image without triggering room navigation.
2. **Overlay Container Choice**:
   - **Option A (`Dialog`)**: Wrap in `androidx.compose.ui.window.Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))`. Ensures top-level window rendering above system bars and bottom navs.
   - **Option B (`Box` + `BackHandler`)**: Render inside screen-level or root-level `Box` with `AnimatedVisibility`.
   - *Recommendation*: **Option A** matches existing modal infrastructure (`ConfirmModal`, `RatingModal`, `EmojiPicker`) and guarantees edge-to-edge full-screen presentation.
3. **High-Res Request Mode**:
   - Use `size(Size.ORIGINAL)` and `precision(Precision.EXACT)` on `ImageRequest` inside the overlay to avoid serving downsampled memory-cached bitmaps.
