# Pre-V2.5 — Consumer & Screen Presentation Audit

## 1. Audit Metadata & Scope
- **Audit Date**: Post-V2.4 Architecture Phase
- **Repository Path**: `C:/Baroness_Core/Baroness-kt`
- **Scope**: Comprehensive forensic audit of all UI screens, drawers, modal sheets, dialogs, and reusable components across the Baroness application to evaluate obedience, intentional local styling, or bypass status against the V2 settings foundation.
- **Audit Rule**: **AUDIT ONLY — ZERO SOURCE CODE MODIFICATIONS.**

---

## 2. Review of V2.1 – V2.4 Architecture Integration

The codebase was verified against the implemented V2 setting pipelines:

1. **V2.1 (Settings Foundation & Registry Cleanup)** `[VERIFIED]`:
   - Obsolete registries (`SettingsOptions.fonts` and gradient `SettingsOptions.wallpapers`) were removed.
   - Persistence sanitization in `SettingsRepository.kt` intercepts invalid/legacy keys and falls back safely (`sunrise` for wallpaper, `lavender` for theme).
2. **V2.2 (Theme Foundation & Material 3 Integration)** `[VERIFIED]`:
   - Root composition in `MainActivity.kt` instantiates `SettingsViewModel` and passes it to `BaronessAppTheme`.
   - `BaronessAppTheme` (`Theme.kt`) observes `activeTheme` StateFlow and dynamically updates `MaterialTheme.colorScheme` and `LocalBaronessTheme.current` (`glowColor`, `bubbleFridayColor`, `bubbleUserColor`, `bgStart`, `bgEnd`).
3. **V2.3 (Global Typography Foundation)** `[VERIFIED]`:
   - `BaronessAppTheme` observes `activeFont` StateFlow, resolves `(fontFamily, fontWeight)` via `AppFonts.resolve()`, and dynamically updates `MaterialTheme.typography` across all 15 Material 3 text roles.
   - `LocalBaronessTypography` provides `ChatTypography` tokens for chat sheets and components.
4. **V2.4 (Global Wallpaper & Background Foundation)** `[VERIFIED]`:
   - `SettingsViewModel.activeWallpaper` serves as authoritative StateFlow originating from DataStore.
   - `DynamicBackground.kt` acts as the canonical renderer backed by `prebundledWallpapers` and custom gallery bitmaps with safe exception fallbacks.

---

## 3. Screen-by-Screen & Component Audit

### 3.1 Top-Level Screens

#### 1. `GateScreen.kt`
- **Font**: Inherits global `MaterialTheme.typography`.
- **Theme**: Uses local custom security/auth colors (`Color(0xFF00f8db)`, `Color(0xFF4caf50)`, `Color(0xFF4d94ff)`, `Color(0xFFff4d6d)`).
- **Wallpaper**: Uses hardcoded drawable background (`R.drawable.image_45`) with blur effect.
- **Classification**: `LOCAL / INTENTIONAL`
- **Justification**: Security gate / auth surface intentionally uses a fixed visual atmosphere separate from global chat/dashboard customization.

#### 2. `DashboardScreen.kt`
- **Font**: Inherits global `MaterialTheme.typography`.
- **Theme**: Inherits global `MaterialTheme.colorScheme` and custom accent tokens (`Colors.pinkAccent`, `Colors.purpleAccent`, `Colors.greenAccent`).
- **Wallpaper**: Consumes `DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = true)` driven by `settingsViewModel.activeWallpaper`.
- **Classification**: `GLOBAL`
- **Status**: Already compliant with V2.2–V2.4 foundation.

#### 3. `ChatListScreen.kt`
- **Font**: Inherits global `MaterialTheme.typography` (`chatTypography` via `rememberChatTypography`).
- **Theme**: Inherits `MaterialTheme.colorScheme`.
- **Wallpaper**: Bypasses global wallpaper with hardcoded background `Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F12)))`.
- **Classification**: `BYPASS / NEEDS MIGRATION`
- **V2.5 Action**: Migrate background container from fixed `#0F0F12` to `DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = true)`.

#### 4. `ChatRoomScreen.kt`
- **Font**: Inherits global `MaterialTheme.typography` and `ChatTypography`.
- **Theme**: Consumes `activeThemeId` / `LocalBaronessTheme` for message bubbles, inputs, and pills.
- **Wallpaper**: Consumes `DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = false, hazeState = hazeState)`.
- **Classification**: `GLOBAL`
- **Status**: Already compliant with V2.2–V2.4 foundation.

#### 5. `PhotosScreen.kt`
- **Font**: Inherits global `MaterialTheme.typography` (`headlineMedium`, `bodySmall`, `titleMedium`).
- **Theme**: Inherits global `MaterialTheme.colorScheme` (`background`, `surface`, `primary`, `onSurfaceVariant`).
- **Wallpaper**: Uses standard `Scaffold` background (`MaterialTheme.colorScheme.background`).
- **Classification**: `LOCAL / INTENTIONAL`
- **Justification**: Photo gallery viewer requires a clean, neutral background so user photographs remain the visual focus.

#### 6. `WishlistScreen.kt`
- **Font**: Uses default `PhestyText` / Material typography.
- **Theme**: Uses fixed local colors (`Color(0xFFff4d6d)`).
- **Wallpaper**: Bypasses global wallpaper with hardcoded `BACKGROUND_IMAGE = R.drawable.image_15`.
- **Classification**: `BYPASS / NEEDS MIGRATION`
- **V2.5 Action**: Update background container from static `R.drawable.image_15` to `DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = true)`.

#### 7. `ProfileSetupScreen.kt`
- **Font**: Inherits global `MaterialTheme.typography`.
- **Theme**: Uses local persona accent colors (`Color(0xFFff4d6d)`).
- **Wallpaper**: Uses hardcoded drawable background (`R.drawable.image_45`).
- **Classification**: `LOCAL / INTENTIONAL`
- **Justification**: Profile creation/vault setup surface shares design language with GateScreen.

---

### 3.2 Drawers & Drawer Sub-Composables

#### 1. `GlobalDrawer.kt`
- **Font**: Headers explicitly use `AppFonts.Gamaamli` and `AppFonts.PlayfairDisplay`.
- **Theme**: Consumes frosted glass blur effects (`HazeState`) and M3 surface treatments.
- **Wallpaper**: Overlay container rendered on top of active screen background.
- **Classification**: `LOCAL / INTENTIONAL`
- **Justification**: Drawer headers intentionally use distinct display typography for navigational hierarchy.

#### 2. `DrawerTheme.kt`
- **Font**: Inherits `AppFonts.PlayfairDisplay` and `AppFonts.Lifesavers` for swatch labels.
- **Theme**: Displays theme swatches and preview controls (`SettingsViewModel.previewTheme`).
- **Classification**: `GLOBAL` (State Provider)
- **Status**: Already compliant.

#### 3. `DrawerFont.kt`
- **Font**: Renders live preview cards using each respective font family from `AppFonts`.
- **Theme**: Inherits M3 surface styling.
- **Classification**: `GLOBAL` (State Provider)
- **Status**: Already compliant.

#### 4. `DrawerWallpaper.kt`
- **Font**: Inherits `AppFonts.PlayfairDisplay` and `AppFonts.Lifesavers`.
- **Theme**: Renders live wallpaper carousel and gallery upload triggers.
- **Classification**: `GLOBAL` (State Provider)
- **Status**: Already compliant.

#### 5. `DrawerAbout.kt`, `DrawerClock.kt`, `DrawerNotifications.kt`, `DrawerPrivacy.kt`, `DrawerSoundHaptics.kt`, `DrawerStorage.kt`
- **Font**: Inherit `AppFonts.PlayfairDisplay`, `AppFonts.Lifesavers`, and `MaterialTheme.typography`.
- **Theme**: Inherit M3 surface and haze blur effects.
- **Classification**: `LOCAL / INTENTIONAL`
- **Status**: Functioning as designed.

---

### 3.3 Modal Sheets & Dialog Overlays

#### 1. `AskFridaySheet.kt`, `TranslationSheet.kt`, `MessageInfoSheet.kt`, `DeleteConfirmationSheet.kt`, `ComingSoonSheet.kt`
- **Font**: Inherit `ChatTypography` via `rememberChatTypography(settingsViewModel)`.
- **Theme**: Consume `activeThemeId` and `LocalBaronessTheme` (`glowColor`, `bubbleFridayColor`).
- **Wallpaper**: Modal sheet overlays rendered over screen background.
- **Classification**: `GLOBAL`
- **Status**: Already compliant.

#### 2. `SongQueueSheet.kt`, `InAppMiniPlayer.kt`
- **Font**: Inherits `MaterialTheme.typography` and `rememberChatTypography()`.
- **Theme**: Consumes `LocalBaronessTheme` / M3 color scheme.
- **Classification**: `GLOBAL`
- **Status**: Already compliant.

---

### 3.4 Chat & Wishlist Components

#### 1. `MessageBubble.kt`
- **Body Font**: Inherits global font via `typography.body`.
- **Metadata Font**: Explicitly uses `FontFamily.SansSerif` (`masterMetaStyle`) for timestamp legibility.
- **Theme**: Consumes `bubbleFridayColor`, `bubbleUserColor`, `glowColor` from active theme.
- **Classification**: `GLOBAL` (with intentional local metadata exception).
- **Status**: Already compliant.

#### 2. `ChatInput.kt`
- **Font**: Inherits `rememberChatTypography()`.
- **Theme**: Consumes `theme.glowColor` for send button and active state.
- **Classification**: `GLOBAL`
- **Status**: Already compliant.

#### 3. `WishItem.kt`, `WishlistHeader.kt`, `WishlistInput.kt`
- **Font**: Uses `PhestyText` / M3 typography.
- **Theme**: Uses fixed local accent `Color(0xFFff4d6d)`.
- **Classification**: `BYPASS / NEEDS MIGRATION`
- **V2.5 Action**: Connect header and accent highlights to `MaterialTheme.colorScheme.primary` / `LocalBaronessTheme.current.glowColor`.

---

## 4. Consumer Decision Matrix

| Screen / Component | Font Preference | Theme Preference | Wallpaper Preference | Typography Scope | Current Classification | Proposed V2.5 Action |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **GateScreen** | M3 Typography | Local Security Accent | Fixed `image_45` | Material3 | `LOCAL / INTENTIONAL` | Retain local security styling |
| **DashboardScreen** | M3 Typography | M3 / LocalBaronessTheme | `DynamicBackground` | Material3 | `GLOBAL` | Already compliant |
| **ChatListScreen** | ChatTypography | M3 ColorScheme | Fixed `#0F0F12` | Material3 / Chat | `BYPASS / NEEDS MIGRATION` | Migrate background to `DynamicBackground` |
| **ChatRoomScreen** | ChatTypography | LocalBaronessTheme | `DynamicBackground` | Material3 / Chat | `GLOBAL` | Already compliant |
| **PhotosScreen** | M3 Typography | M3 ColorScheme | M3 Background | Material3 | `LOCAL / INTENTIONAL` | Retain neutral viewer background |
| **WishlistScreen** | PhestyText | Fixed `#FF4D6D` | Fixed `image_15` | Default | `BYPASS / NEEDS MIGRATION` | Migrate background to `DynamicBackground` |
| **ProfileSetupScreen** | M3 Typography | Local Accent | Fixed `image_45` | Material3 | `LOCAL / INTENTIONAL` | Retain vault profile styling |
| **GlobalDrawer** | AppFonts Display | M3 / Haze Blur | Overlay | Custom Display | `LOCAL / INTENTIONAL` | Retain drawer display headers |
| **AskFridaySheet** | ChatTypography | LocalBaronessTheme | Sheet Overlay | Chat Typography | `GLOBAL` | Already compliant |
| **InAppMiniPlayer** | ChatTypography | LocalBaronessTheme | Bar Overlay | Chat Typography | `GLOBAL` | Already compliant |
| **MessageBubble** | ChatTypography | LocalBaronessTheme | N/A | Local Meta Exception | `GLOBAL` | Retain SansSerif timestamp exception |

---

## 5. Existing Compliant Consumers

The following surfaces and components already fully obey the V2 settings foundation:
1. `DashboardScreen`: Dynamically renders `DynamicBackground` + M3 Typography + M3 ColorScheme.
2. `ChatRoomScreen`: Dynamically renders `DynamicBackground` + `LocalBaronessTheme` colors + `ChatTypography`.
3. `AskFridaySheet`, `TranslationSheet`, `MessageInfoSheet`, `DeleteConfirmationSheet`, `ComingSoonSheet`: Obey theme colors and global typography.
4. `InAppMiniPlayer` & `SongQueueSheet`: Obey dynamic theme accents and typography.
5. `ChatInput` & `PinnedMessagesPill`: Obey `glowColor` theme accents and active typography.

---

## 6. Confirmed Bypasses

### Bypass 1: `ChatListScreen.kt` Background
- **File**: `app/src/main/java/com/baroness/app/screens/ChatListScreen.kt`
- **Composable**: `ChatListScreen()`
- **Current Behavior**: `Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F12)))`
- **Bypassed Setting**: Global Wallpaper (`SettingsViewModel.activeWallpaper`)
- **Why Bypass**: Prototype hardcoded a dark hex color before `DynamicBackground` was available.
- **Proposed V2.5 Action**: Replace hardcoded `#0F0F12` background with `DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = true)`.
- **Risks**: Ensure list item contrast remains high over light wallpapers by maintaining `dimmed = true`.

### Bypass 2: `WishlistScreen.kt` Background
- **File**: `app/src/main/java/com/baroness/app/screens/WishlistScreen.kt`
- **Composable**: `WishlistScreen()`
- **Current Behavior**: `Image(painter = painterResource(id = R.drawable.image_15), ...)`
- **Bypassed Setting**: Global Wallpaper (`SettingsViewModel.activeWallpaper`)
- **Why Bypass**: Hardcoded a static background image asset (`image_15`).
- **Proposed V2.5 Action**: Inject `settingsViewModel: SettingsViewModel` and replace static `image_15` with `DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = true)`.
- **Risks**: Pass `settingsViewModel` through `AppNavigation` in `MainActivity.kt`.

---

## 7. Intentional Local Surfaces

1. **`GateScreen.kt`**: Retains `image_45` background and security key color accents for authentication atmosphere.
2. **`ProfileSetupScreen.kt`**: Retains `image_45` background to match GateScreen vault setup theme.
3. **`PhotosScreen.kt`**: Retains standard `MaterialTheme.colorScheme.background` so photo thumbnails are displayed against a clean, neutral surface without wallpaper interference.
4. **`MessageBubble.kt` Metadata**: Retains `FontFamily.SansSerif` (`masterMetaStyle`) for small timestamp legibility.
5. **`GlobalDrawer` Header Fonts**: Retain display fonts (`PlayfairDisplay`, `Gamaamli`) for distinct drawer section styling.

---

## 8. Uncertain Decisions

- **None Identified**: Codebase inspection provided complete, unambiguous evidence for all surfaces.

---

## 9. Architectural Issues Discovered (Documentation Only)

1. **Missing `settingsViewModel` Parameter in Navigation for `WishlistScreen`**: `MainActivity.kt` currently passes `settingsViewModel` to `DashboardScreen`, `ChatListScreen`, and `ChatRoomScreen`, but does not pass it to `WishlistScreen`.
2. **Direct Hardcoded Color Constants in Wishlist Components**: `WishItem.kt` and `WishlistInput.kt` reference `#ff4d6d` directly rather than `MaterialTheme.colorScheme.primary` or `LocalBaronessTheme.current.glowColor`.

---

## 10. V2.5 Migration Map

### A. Already Compliant (No V2.5 Changes Required)
- `DashboardScreen.kt`
- `ChatRoomScreen.kt`
- `AskFridaySheet.kt`
- `TranslationSheet.kt`
- `MessageInfoSheet.kt`
- `DeleteConfirmationSheet.kt`
- `ComingSoonSheet.kt`
- `InAppMiniPlayer.kt`
- `SongQueueSheet.kt`
- `MessageBubble.kt`
- `ChatInput.kt`

### B. Confirmed Migration Candidates (V2.5 Scope)
1. `ChatListScreen.kt`: Update background container to consume `DynamicBackground(activeWallpaperId)`.
2. `WishlistScreen.kt`: Pass `settingsViewModel` in `MainActivity.kt` and update background container to consume `DynamicBackground(activeWallpaperId)`.

### C. Intentional Local Surfaces (Explicitly Leave Untouched)
- `GateScreen.kt`
- `ProfileSetupScreen.kt`
- `PhotosScreen.kt`
- `GlobalDrawer` Header Typography
- `MessageBubble` Timestamp Meta Typography

### D. Uncertain
- *None.*

### E. Future Considerations (Post-V2.5)
- Optional per-screen wallpaper blur depth adjustments.
- High-contrast mode toggles for custom user gallery wallpapers.

---

## 11. Explicitly Deferred / Future Work
- Voice/audio engine refactoring.
- Notifications & FCM payload customization.
- FRIDAY AI Edge Function prompt expansion.
- Wishlist item rating modal redesigns.

---

## 12. Final Audit Confirmation

PRE-V2.5 CONSUMER AUDIT: COMPLETE

Implementation Changes: NONE — AUDIT ONLY

Next Phase: HUMAN REVIEW → V2.5 CONSUMER MIGRATION
