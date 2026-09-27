# Baroness Version 2 — Global Settings Architecture & Refactor Blueprint

## Document Metadata
- **Blueprint Status**: LOCKED ARCHITECTURE BLUEPRINT
- **Design Validation Date**: September 27, 2026
- **Historical Reference Audit**: Forensic Baseline Audit (`docs/reports/KotlinFile_Review.md`)
- **Target Repository Root**: `C:\Baroness_Core\Baroness-kt`
- **Implementation Status**: DESIGN ONLY — NO SOURCE CHANGES PERFORMED

---

## 1. Executive Summary

This document defines the authoritative **Baroness Version 2 Architecture Blueprint** for global settings, user preferences, runtime state resolution, and UI consumer synchronization across the entire Baroness application (`C:\Baroness_Core\Baroness-kt`).

In Version 1 `[VERIFIED]`, while a centralized DataStore persistence layer (`StorageManager`, `SettingsRepository`, `SettingsViewModel`) managed settings for theme, font, wallpaper, voice, clock, and emoji preferences, these preferences were consumed fragmentarily. Chat screens (`ChatListScreen`, `ChatRoomScreen`) observed theme and font state through a local custom `ChatTypography` system, while major app surfaces including `DashboardScreen`, `GateScreen`, `PhotosScreen`, `WishlistScreen`, and global drawers bypassed user preferences and relied on hardcoded fonts, colors, or background images. Furthermore, legacy registries in `SettingsData.kt` conflicted with active font definitions in `Type.kt` and wallpaper assets in `DynamicBackground.kt`.

**Baroness Version 2** `[PROPOSED]` establishes an application-wide settings architecture based on three clean separation boundaries:
1. **Definition Registries**: Immutable Kotlin data registries defining all legal choices (`ThemeDefinition`, `FontFamilyOption`, `WallpaperOption`, `VoiceOption`).
2. **User Preference Persistence**: Single-source DataStore keys managed by `StorageManager` / `SettingsRepository`.
3. **Resolved Runtime Configuration & Composition Local**: An application-scoped state hub (`SettingsViewModel` / `BaronessSettingsHub`) providing resolved Compose objects (`ResolvedTheme`, `Typography`, `WallpaperOption`) exposed through standard `MaterialTheme` and custom `CompositionLocal` providers (`LocalBaronessTheme`, `LocalBaronessTypography`, `LocalBaronessBackground`).

Under Version 2, any preference change made by the user—whether a font choice, color theme, or wallpaper selection—is automatically propagated to every compliant UI surface without requiring manual font bindings or screen-specific styling overrides.

---

## 2. Version 2 Goals

- **Single Source of Truth** `[PROPOSED]`: Establish exactly one persisted preference key and one authoritative runtime state object for every user setting.
- **App-Wide Consumer Obedience** `[PROPOSED]`: Eliminate local settings bypasses in `BaronessAppTheme`, `ChatListScreen`, `WishlistScreen`, `PhotosScreen`, `DashboardScreen`, `GateScreen`, and drawer components.
- **Material 3 Integration** `[PROPOSED]`: Connect the user's selected global font family to `MaterialTheme.typography` so all standard Compose UI components (`Text`, `Button`, `TextField`, `Card`, `TopAppBar`) automatically reflect the chosen font without verbose `fontFamily = ...` parameters.
- **Registry Normalization** `[PROPOSED]`: Deprecate obsolete legacy options in `SettingsData.kt` (`SettingsOptions.fonts` and `SettingsOptions.wallpapers`) and unify all font and background definitions into canonical registries (`Type.kt` and `DynamicBackground.kt`).
- **Deterministic Lifecycle & Zero-Flash Target** `[PROPOSED] / [NEEDS IMPLEMENTATION VALIDATION]`: Ensure cold-start reads synchronously prime StateFlows prior to first recomposition, designed to minimize or prevent visual flashing on app launch and process restoration.
- **Predictable API & Failure Tolerance** `[PROPOSED]`: Implement strict fallback chains so corrupted, missing, or removed preference keys fail safely to well-defined system defaults.

---

## 3. Current Architecture Reconstruction

### End-to-End Control Flow (Current State) `[VERIFIED]`:

```text
Settings Drawers (DrawerTheme / DrawerFont / DrawerWallpaper in GlobalDrawer)
    ↓ (User selects preview option)
SettingsViewModel (_previewTheme, _previewFont, _previewWallpaper)
    ↓ (User taps APPLY)
SettingsRepository (saveTheme, saveFont, saveWallpaper)
    ↓ (Coroutines / DataStore Edit)
StorageManager (Context.dataStore "baroness_prefs")
    ↓ (Disk write: baroness_prefs.preferences_pb)
    ↓
Restoration Phase (on App Launch / ViewModel init)
    ↓
SettingsRepository.getInitialTheme() / getThemeFlow()
    ↓ (runBlocking initial fetch + StateFlow collection)
SettingsViewModel (activeTheme, activeFont, activeWallpaper StateFlows)
    ↓
    ├── Consumer Path A (Chat UI):
    │   rememberChatTypography(settingsViewModel) -> ChatTypography -> ChatRoomScreen / MessageBubble
    │   DynamicBackground(activeWallpaper) -> ChatRoomScreen / DashboardScreen
    │
    └── Consumer Path B (Bypasses / Hardcoded Surfacing):
        Theme.kt (BaronessAppTheme) -> Ignores activeTheme & activeFont -> Standard M3 Light/Dark
        ChatListScreen -> Ignores activeWallpaper -> Hardcoded Color(0xFF0F0F12)
        WishlistScreen -> Ignores activeWallpaper & activeFont -> Hardcoded R.drawable.image_15
        PhotosScreen -> Ignores activeTheme & activeFont -> Standard Scaffold & M3 Typography
        GlobalDrawer headers -> Ignores activeFont -> Hardcoded AppFonts.Gamaamli
```

### Exact Current Code Trace by Layer `[VERIFIED]`:

| Layer | File / Class / Property | Method / Key | Current Functionality `[VERIFIED]` |
| :--- | :--- | :--- | :--- |
| **UI Inputs** | `DrawerTheme.kt`, `DrawerFont.kt`, `DrawerWallpaper.kt` | `onApply()` / `onSelect()` | Triggers `previewTheme()`, `applyTheme()`, `previewFont()`, `applyFont()`. |
| **State Hub** | `SettingsViewModel.kt` | `activeTheme`, `activeFont`, `activeWallpaper` | `StateFlow` holders initialized via `stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)`. |
| **Repository** | `SettingsRepository.kt` | `saveTheme()`, `saveFont()`, `saveWallpaper()` | Dispatches coroutine writes to `StorageManager`. |
| **Persistence** | `StorageManager.kt` | Keys: `"selected_theme"`, `"selected_font"`, `"selected_wallpaper"` | `androidx.datastore.preferences.core` file `baroness_prefs`. |
| **Restoration** | `SettingsRepository.kt` | `getInitialTheme()`, `getThemeFlow()` | `runBlocking` read for initial state + `Flow` mapping for updates. |
| **Resolution** | `ChatTypography.kt` | `rememberChatTypography()` -> `AppFonts.resolve()` | Translates string ID (e.g. `"playfairdisplay_bold"`) into `FontFamily` and `FontWeight`. |
| **Rendering** | `DynamicBackground.kt` | `DynamicBackground(activeWallpaperId)` | Resolves drawable resource or user gallery file path. |

---

## 4. Findings Carried Forward From Forensic Audit

The historical forensic audit (`docs/reports/KotlinFile_Review.md`) established the following core findings:
1. **Total Kotlin Source Count** `[VERIFIED]`: 150 `.kt` source files (148 in `app/src/main`, 1 unit test, 1 instrumentation test). All 148 production source files are active.
2. **Central DataStore Hub** `[VERIFIED]`: `StorageManager` backed by `baroness_prefs` is the true persistence source of truth.
3. **Fragmented Settings Consumption** `[VERIFIED]`: `SettingsViewModel` holds settings intended to be app-wide, but non-chat screens (`DashboardScreen`, `GateScreen`, `PhotosScreen`, `WishlistScreen`) bypass settings and hardcode visual properties.
4. **Font Registry Discrepancy** `[VERIFIED]`: Legacy list `SettingsOptions.fonts` (`"system"`, `"inter"`, `"serif"`, `"monospace"`) in `SettingsData.kt` conflicts with active registry `AppFonts` in `Type.kt` and `fontFamilies` in `DrawerFont.kt` (11 custom families).
5. **Wallpaper Model Discrepancy** `[VERIFIED]`: Gradient definitions in `SettingsOptions.wallpapers` (`SettingsData.kt`) are unused and contradicted by `DynamicBackground.kt` (`prebundledWallpapers` + user photo upload).
6. **Material Theme Disconnect** `[VERIFIED]`: `BaronessAppTheme` in `Theme.kt` wraps the entire app but does not observe user-selected theme or font preferences.

---

## 5. Verified Current-State Corrections

Verification against actual current repository source files confirms and adjusts the audit findings:

1. **`Theme.kt` Verification** `[VERIFIED]`:
   - *Audit Claim*: `BaronessAppTheme` wraps `MainActivity` but ignores `activeTheme`.
   - *Current Code Inspection*: `Theme.kt` defines `BaronessAppTheme` which accepts only `darkTheme: Boolean = isSystemInDarkTheme()`. It constructs `DarkColorScheme` and `LightColorScheme` using fixed purple colors (`0xFFBB86FC`, `0xFF6200EE`) and calls `MaterialTheme(colorScheme = colorScheme, content = content)`. Typography is completely omitted in the `MaterialTheme` call. **VERIFIED CORRECT.**

2. **`Type.kt` & `ChatTypography.kt` Verification** `[VERIFIED]`:
   - *Audit Claim*: `Type.kt` contains `AppFonts.resolve(fontId)` which parses string IDs formatted as `"familyId_weightId"`.
   - *Current Code Inspection*: `Type.kt` defines 11 `FontFamily` top-level properties and `AppFonts.resolve(fontId)` returning `Pair<FontFamily, FontWeight>?`. `ChatTypography.kt` calls `AppFonts.resolve(activeFontId)` and falls back to `(FontFamily.Default, FontWeight.Normal)`. **VERIFIED CORRECT.**

3. **`WishlistScreen.kt` Verification** `[VERIFIED]`:
   - *Audit Claim*: `WishlistScreen` hardcodes a dark background color `#0F0F1A`.
   - *Current Code Inspection*: `WishlistScreen.kt` lines 39 and 82 define `private val BACKGROUND_IMAGE = R.drawable.image_15` and render `Image(painter = painterResource(id = BACKGROUND_IMAGE))` directly. It bypasses both wallpaper and theme settings. **VERIFIED & REFINED.**

4. **`ChatListScreen.kt` Verification** `[VERIFIED]`:
   - *Audit Claim*: `ChatListScreen` overrides background with `#0F0F12`.
   - *Current Code Inspection*: Line 73 in `ChatListScreen.kt` uses `Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F12)))`. `chatTypography` is observed for text, but background wallpaper/theme is hardcoded. **VERIFIED CORRECT.**

5. **`MessageBubble.kt` Verification** `[VERIFIED]`:
   - *Audit Claim*: Message bubbles override metadata timestamps with `FontFamily.SansSerif`.
   - *Current Code Inspection*: `MessageBubble.kt` line 101 contains `val masterMetaStyle = TextStyle(fontFamily = FontFamily.SansSerif, ...)`. This is an intentional local design decision for metadata readability. **VERIFIED CORRECT.**

---

## 6. Version 2 Source-of-Truth Architecture

The Version 2 Settings Architecture enforces a strict 4-tier model `[PROPOSED]`:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        1. DEFINITION REGISTRY                          │
│  Static, immutable Kotlin definitions of available options.            │
│  Examples: AppTheme, FontFamilyOption, WallpaperOption, VoiceConfig.   │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     2. USER PREFERENCE PERSISTENCE                     │
│  Primitive string keys saved in DataStore (baroness_prefs).            │
│  Managed strictly by StorageManager & SettingsRepository.              │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  3. AUTHORITATIVE RUNTIME STATE HUB                    │
│  SettingsViewModel holding StateFlows & exposing resolved Compose      │
│  token objects (ResolvedTheme, Typography, WallpaperOption).           │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   4. APP-WIDE CONSUMER PROVIDERS                       │
│  BaronessAppTheme binding M3 MaterialTheme & CompositionLocals:        │
│  - MaterialTheme.colorScheme & MaterialTheme.typography                │
│  - LocalBaronessTheme, LocalBaronessTypography, LocalBaronessBackground│
└────────────────────────────────────────────────────────────────────────┘
```

### Separation Rules `[PROPOSED]`:
1. **Registries** answer: *"What options exist in the app?"* They contain static metadata and asset references. They store **no user state**.
2. **Persistence** answers: *"What preference key was stored on disk?"* It stores primitive strings (e.g. `"moonlight"`, `"robotomono_medium"`, `"sunrise"`).
3. **Runtime State Hub** answers: *"What is the active configuration right now?"* It holds reactive flows and converts raw stored IDs into resolved Compose instances.
4. **Consumer Providers** answer: *"How do UI components observe configuration?"*
   - Standard Compose components observe standard **`MaterialTheme.colorScheme`** and **`MaterialTheme.typography`**.
   - Baroness-specific tokens/concepts that `MaterialTheme` does not represent cleanly (e.g., custom glow colors, chat bubble Friday/User colors, custom background drawables) are exposed via **`CompositionLocal`** (`LocalBaronessTheme`, `LocalBaronessTypography`, `LocalBaronessBackground`). CompositionLocals must **not** be created merely to duplicate standard `MaterialTheme` values.

---

## 7. Authoritative Runtime Settings Owner

### Current Implementation `[VERIFIED]`:
In the current code, `SettingsViewModel` is instantiated in `MainActivity.onCreate()` using `viewModel(factory = SettingsViewModelFactory(this))`. This scopes `SettingsViewModel` to the `ComponentActivity` instance. Activity recreations (such as screen rotation or process death restoration) create a new `SettingsViewModel` instance, which re-reads state from DataStore via `SettingsRepository` synchronously during initialization.

### Proposed Version 2 Architecture `[PROPOSED]`:
In Version 2, **`SettingsViewModel`** remains the central runtime settings state owner for user preferences.

> **Architectural Principle**: *One authoritative runtime owner per setting domain, coordinated through the app-wide settings architecture.*

- **State Ownership Boundary**: `SettingsViewModel` owns global visual and audio user preferences (`activeTheme`, `activeFont`, `activeWallpaper`, `voiceConfig`, clock tones). It does **NOT** pull in domain data (User Profile, Wishlist items, Chat history), device registration state (FCM tokens), auth session tokens, or FRIDAY AI conversation state into a giant "God Object." Unrelated feature and domain states remain strictly encapsulated inside their respective domain ViewModels and Repositories.
- **Persistence Ownership**: `SettingsRepository` owns the DataStore interaction layer via `StorageManager`.
- **Consumer Binding**: `MainActivity` instantiates `SettingsViewModel` at the root container level and passes resolved state down through `BaronessAppTheme`.
- **Prevention of Duplicate State**: UI screens must **never** create local state holders for global settings. All screens observe `SettingsViewModel` or read from `CompositionLocal`.

---

## 8. Settings Lifecycle

```text
               User Selects Option in Drawer
                             │
                             ▼
               SettingsViewModel.preview*() [Preview State Updates]
                             │
                             ▼
               User Taps APPLY
                             │
                             ▼
               SettingsViewModel.apply*()
                             │
                             ▼
               SettingsRepository.save*() -> StorageManager.saveString()
                             │
                             ▼
               DataStore Disk File Updated (baroness_prefs)
                             │
                             ▼
               SettingsRepository Flow Emits New Value
                             │
                             ▼
               SettingsViewModel.active* StateFlow Updates
                             │
                             ▼
               BaronessAppTheme Recomposes & Updates CompositionLocals
                             │
                             ▼
               All Screen Consumers Recompose Automatically
```

### Cold-Start & Process Death Lifecycle `[PROPOSED] / [NEEDS IMPLEMENTATION VALIDATION]`:
1. **App Initialization**: `SettingsRepository` executes synchronous initial reads during `SettingsViewModel` instantiation (`runBlocking { storageManager.getString(...) }`). `[VERIFIED]`
2. **Initial State Flow Binding**: `stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)` ensures `activeTheme`, `activeFont`, and `activeWallpaper` hold valid initial values before `setContent {}` runs. `[VERIFIED]`
3. **Minimizing Visual Flash Target**: Because initial state is primed prior to the first Compose pass, UI screens are designed to avoid default-state flickering during initial render. True zero-flash performance must be validated during empirical testing across asset loading and theme switches. `[NEEDS IMPLEMENTATION VALIDATION]`
4. **Process Death Restoration**: Android OS re-creates `MainActivity` and `SettingsViewModel`, re-reading DataStore state synchronously to restore identical UI rendering. `[VERIFIED]`

---

## 9. Theme Architecture

### Current Limitation `[VERIFIED]`:
Custom themes (`lavender`, `moonlight`, `golden`, `rose`, `ocean`) defined in `SettingsOptions` exist only as data instances in `SettingsData.kt` and are manually consumed inside chat components (`MessageBubble`, `ChatInput`). `BaronessAppTheme` in `Theme.kt` ignores them and applies default purple M3 color schemes.

### Version 2 Design `[PROPOSED]`:
1. **Canonical Theme Registry**: `SettingsOptions.themes` in `SettingsData.kt` defines active `AppTheme` instances.
2. **Resolved Theme Object**: `AppTheme` properties mapped into standard Material 3 `ColorScheme` + custom `BaronessThemeColors`.
3. **MaterialTheme vs CompositionLocal Boundary**:
   - `MaterialTheme.colorScheme` provides standard primary, surface, background, and text colors to standard Compose UI components (`Text`, `Button`, `Card`).
   - Custom Baroness tokens (`glowColor`, `bubbleFridayColor`, `bubbleUserColor`) that `MaterialTheme` does not represent natively are exposed via `LocalBaronessTheme`.

```kotlin
data class BaronessThemeColors(
    val bgStart: Color,
    val bgEnd: Color,
    val glowColor: Color,
    val bubbleFridayColor: Color,
    val bubbleUserColor: Color,
    val label: String
)

val LocalBaronessTheme = staticCompositionLocalOf { SettingsOptions.LavenderTheme }
```

4. **`BaronessAppTheme` Integration**:

```kotlin
@Composable
fun BaronessAppTheme(
    settingsViewModel: SettingsViewModel,
    content: @Composable () -> Unit
) {
    val activeThemeId by settingsViewModel.activeTheme.collectAsStateWithLifecycle()
    val activeFontId by settingsViewModel.activeFont.collectAsStateWithLifecycle()

    val currentTheme = remember(activeThemeId) {
        SettingsOptions.themes.find { it.id == activeThemeId } ?: SettingsOptions.LavenderTheme
    }

    val colorScheme = remember(currentTheme) {
        darkColorScheme(
            primary = currentTheme.glowColor,
            surface = currentTheme.bgStart,
            background = currentTheme.bgStart,
            onBackground = AppColors.textPrimary,
            onSurface = AppColors.textPrimary
        )
    }

    val dynamicTypography = rememberBaronessTypography(activeFontId)

    CompositionLocalProvider(
        LocalBaronessTheme provides currentTheme,
        LocalBaronessTypography provides dynamicTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = dynamicTypography.toMaterialTypography(),
            content = content
        )
    }
}
```

---

## 10. Typography / Font Architecture

### Current Limitation `[VERIFIED]`:
`SettingsData.kt` contains obsolete `SettingsOptions.fonts` (`"system"`, `"inter"`, `"serif"`, `"monospace"`). `Type.kt` and `DrawerFont.kt` contain 11 real custom font families (`Yuyu`, `Lifesavers`, `RobotoMono`, `Gamaamli`, `Matemasie`, `DancingScript`, `MarckScript`, `PlayfairDisplay`, `KaushanScript`, `PermanentMarker`, `ShadowsIntoLight`). Font selection is applied only through `rememberChatTypography` on chat screens.

### Version 2 Design `[PROPOSED]`:
1. **Registry Deprecation**: Delete `SettingsOptions.fonts` in `SettingsData.kt`. `AppFonts` in `Type.kt` and `fontFamilies` in `DrawerFont.kt` become the sole canonical font registries.
2. **Dynamic Material 3 Typography**: `rememberBaronessTypography(fontId)` dynamically constructs a full `Typography` suite where all text styles (`headlineLarge`, `titleMedium`, `bodyLarge`, `labelSmall`, etc.) inherit the user's selected `FontFamily` and resolved `FontWeight`.

```kotlin
@Composable
fun rememberBaronessTypography(activeFontId: String): BaronessTypography {
    val (fontFamily, baseWeight) = AppFonts.resolve(activeFontId)
        ?: Pair(FontFamily.Default, FontWeight.Normal)

    return remember(activeFontId) {
        BaronessTypography(fontFamily = fontFamily, baseWeight = baseWeight)
    }
}
```

3. **MaterialTheme Typography Mapping**:
`BaronessTypography.toMaterialTypography()` maps custom token styles (`title`, `header`, `subtitle`, `body`, `meta`) directly into `androidx.compose.material3.Typography`, so standard `Text` widgets automatically observe global typography.
4. **Allowed Overrides**:
`MessageBubble.kt` timestamp metadata retains an explicit override to `FontFamily.SansSerif` (`masterMetaStyle`) to maintain structural readability across decorative fonts (e.g. `PermanentMarker` or `DancingScript`). `[VERIFIED]` All other screens automatically inherit the global selected font through `MaterialTheme.typography`.

---

## 11. Wallpaper / Background Architecture

### Current Limitation `[VERIFIED]`:
`SettingsData.kt` contains gradient options in `SettingsOptions.wallpapers` (`"default"`, `"midnight"`, `"deep_space"`, `"slate"`) that store color lists (`List<Color>`). These are ignored by the actual renderer `DynamicBackground.kt`, which renders prebundled drawables (`image_39`, `sunrise`, etc.) or user gallery photos (`user_wallpaper.jpg`). `ChatListScreen` and `WishlistScreen` bypass `DynamicBackground` and hardcode static backgrounds (`#0F0F12` and `R.drawable.image_15`).

### Version 2 Design `[PROPOSED]`:
1. **Registry Deprecation & Separation**: Deprecate `SettingsOptions.wallpapers` gradient list in `SettingsData.kt`. `prebundledWallpapers` in `DynamicBackground.kt` becomes the canonical wallpaper registry.
2. **Unified Renderer**: `DynamicBackground` is the standard background rendering composable for eligible full-screen UI views.
3. **Screen Adoption & Scope Exceptions**:
   - **Adopting Screens** `[PROPOSED]`: `DashboardScreen`, `ChatRoomScreen`, `ChatListScreen`, and `WishlistScreen` adopt `DynamicBackground(activeWallpaperId)`.
   - **Scope Exceptions** `[PROPOSED]`: `PhotosScreen` (requires clean dark surface contrast for photo thumbnails), `GateScreen` / `ProfileSetupScreen` (brand onboarding identity), and floating sheets/dialogs remain visually independent.
4. **Fallback Handling** `[PROPOSED]`: If a custom user wallpaper file is missing or corrupted on disk, `DynamicBackground` automatically falls back to `sunrise` (`R.drawable.image_39`).

---

## 12. Voice Architecture

- **Registry** `[VERIFIED]`: Remote TTS Providers (`deepgram`, `murf`, `edge`) and voice identifiers (`aura-asteria-en`, `en-US-marcus`, `en-US-JennyNeural`).
- **User Preference** `[VERIFIED]`: DataStore keys: `"voice_enabled"`, `"voice_provider"`, `"voice_id"`, `"voice_speed"`, `"voice_pitch"`, `"use_persona_voices"`, `"voice_director_note"`.
- **Runtime Resolution** `[PROPOSED]`: `SettingsViewModel` constructs a `VoiceConfig` data model and passes it to `VoiceCenter`.
- **Consumers** `[VERIFIED]`: `VoiceCenter`, `AlarmReceiver` (for spoken time/alarm announcements), `ChatInput` (for voice preview).
- **Scope**: `GLOBAL`.
- **Fallback**: System Android `TextToSpeech` (UK locale) if remote provider is unreachable or offline.

---

## 13. Sound Architecture

- **Registry** `[VERIFIED]`: Sound option IDs (`"default_alarm"`, `"chime_chime"`, `"soft_bell"`, `"tone_pulse"`).
- **User Preference** `[VERIFIED]`: DataStore keys: `"alarm_sound_option"`, `"timer_chime_option"`.
- **Runtime Resolution** `[VERIFIED]`: `ClockSoundPlayer.playSound(context, soundOption, isAlarm, durationMs)`.
- **Consumers** `[VERIFIED]`: `AlarmReceiver` (alarm/timer expiration), `DrawerSoundHaptics`.
- **Scope**: `GLOBAL`.
- **Fallback**: System default alarm/notification ringtone via `RingtoneManager`.

---

## 14. Haptics Architecture

- **Registry** `[VERIFIED]`: Vibration pattern IDs (`"default"`, `"double_pulse"`, `"gentle_wave"`, `"strong_alert"`).
- **User Preference** `[VERIFIED]`: DataStore key: `"alarm_vibration_pattern"`.
- **Runtime Resolution** `[VERIFIED]`: `ClockSoundPlayer.playVibration(context, patternName)`.
- **Consumers** `[VERIFIED]`: `AlarmReceiver`, `DrawerSoundHaptics`.
- **UI Haptics**: `VibrationHelper` provides lightweight haptic feedback for UI keypresses and message taps independently of alarm vibration settings.
- **Scope**: `GLOBAL` (Alarms) / `COMPONENT-SCOPED` (UI Feedback).

---

## 15. Notifications Architecture

- **System & Device Registration** `[VERIFIED]`: Android System Notification Channels (`baroness_clock_channel`, `baroness_chat_channel`). FCM Token (`"fcm_token"`) represents **Device Registration State**, NOT a user preference setting.
- **User Preference**: In-app notification toggle preferences where applicable.
- **Runtime Resolution** `[VERIFIED]`: `NotificationManager` registers channel metadata with Android OS on app launch. `NotificationCenter` broadcasts in-app banners via `InAppNotification`.
- **Consumers**: `MainApplication`, `FCMService`, `MainActivity`, `AlarmReceiver`.
- **Scope**: `GLOBAL` (System Channels) / `DEVICE-SCOPED` (FCM Token).

---

## 16. Emoji Architecture

- **Registry** `[VERIFIED]`: `EmojiMap` mapping Unicode strings (e.g. `"❤️"`) to drawable resources (`R.drawable.emoji_*`). `EmojiCategoriesData` registering category groupings.
- **User Preference** `[VERIFIED]`: DataStore key `"recent_emojis"` (comma-separated string).
- **Runtime Resolution** `[VERIFIED]`: `SettingsViewModel.recentEmojis` exposes `StateFlow<List<String>>`.
- **Consumers**: `EmojiPicker`, `ChatInput`, `WishlistInput`, `CustomEmoji`, `PhestyText`.
- **Scope**: `GLOBAL`.

---

## 17. Profile / Persona Architecture

- **Classification** `[VERIFIED]`: User profile (`userProfile`) represents **Domain Identity & User Account Data**, NOT a global visual setting.
- **Persistence** `[VERIFIED]`: DataStore key `"userProfile"` (cached JSON string) & Supabase REST `profiles` table.
- **Runtime Resolution** `[VERIFIED]`: `SessionManager` / `UserSessionManager` manages user identity and authentication destination state.
- **Consumers**: `GateScreen`, `ProfileSetupScreen`, `GateViewModel`, `ProfileSetupViewModel`.
- **Scope**: `DOMAIN DATA / USER IDENTITY`.

---

## 18. Media / Storage / Privacy Architecture

- **Registry** `[VERIFIED]`: Media store queries and backup storage rules.
- **User Preference & Local Storage** `[VERIFIED]`: Device local file storage (`context.filesDir`) + Room database export.
- **Runtime Resolution** `[VERIFIED]`: `BaronessPlayerManager` (ExoPlayer singleton) bound to `BaronessMediaService`. `BackupManager` handles local import/export.
- **Consumers**: `InAppMiniPlayer`, `SongQueueSheet`, `DrawerStorage`, `DrawerPrivacy`.
- **Scope**: `DOMAIN-SCOPED`.

---

## 19. FRIDAY Settings Architecture

- **Registry** `[VERIFIED]`: Groq LLM API models (`llama-3.3-70b-versatile`) and Supabase `friday-orchestrator` edge function.
- **User Preference** `[VERIFIED]`: DataStore key `"voice_director_note"` + persona prompts.
- **Runtime Resolution** `[VERIFIED]`: `FridayChatViewModel` constructs prompt context with active director note, persona configuration, and system instructions.
- **Consumers**: `AskFridaySheet`, `FridayChatViewModel`, `ChatFridayActions`, `LocalCommandExecutor`.
- **Scope**: `GLOBAL` (Director Note) / `FEATURE-SCOPED` (Orchestration).

---

## 20. Settings Scope Model

| Scope Level | Definition | Applicable State |
| :--- | :--- | :--- |
| **GLOBAL** | Applies app-wide to visual styling and system handlers. | Theme, Font, Wallpaper, Voice Options, Alarm/Timer Tones, Clock Announce, Emoji Recents. |
| **FEATURE-SCOPED** | Restricted to specific functional modules. | Wishlist Ratings, Chat Messages/Reactions, Photo Gallery items. |
| **SCREEN-SCOPED** | Active only within a specific screen lifecycle. | Uncommitted settings drawer previews (`previewTheme`, `previewFont`), drawer accordion expansion states. |
| **COMPONENT-SCOPED**| Local design overrides bounded to a single component. | `MessageBubble` timestamp font (`FontFamily.SansSerif`), localized haptic pulses (`VibrationHelper`). |
| **DOMAIN / DEVICE**| Outside visual settings hub; manages identity/system. | Profile identity (`userProfile`), FCM token (`fcm_token`), auth session token. |

---

## 21. Bypass Inventory

| Bypass Location | Current Implementation `[VERIFIED]` | Expected Source `[PROPOSED]` | Version 2 Resolution Plan `[PROPOSED]` |
| :--- | :--- | :--- | :--- |
| **`Theme.kt` (`BaronessAppTheme`)** | Hardcodes purple M3 light/dark color scheme. | `SettingsViewModel.activeTheme` & `activeFont` | Connect `BaronessAppTheme` to `SettingsViewModel` and provide `MaterialTheme.colorScheme` and `MaterialTheme.typography`. |
| **`ChatListScreen.kt` (Background)** | Hardcodes `Color(0xFF0F0F12)` background. | `DynamicBackground(activeWallpaper)` | Replace static hex background with `DynamicBackground(activeWallpaper)`. |
| **`WishlistScreen.kt` (Background)** | Hardcodes `BACKGROUND_IMAGE = R.drawable.image_15`. | `DynamicBackground(activeWallpaper)` | Replace static drawable background with `DynamicBackground(activeWallpaper)`. |
| **`PhotosScreen.kt` (Typography)** | Uses unstyled M3 Scaffold typography. | `MaterialTheme.typography` (Global Font) | Ensure `PhotosScreen` wrapped in `BaronessAppTheme` so top bar and text automatically inherit active font. |
| **`GlobalDrawer.kt` (Header Font)** | Hardcodes `fontFamily = AppFonts.Gamaamli` for "SETTINGS". | `LocalBaronessTypography` / Global Font | Use `LocalBaronessTypography` for drawer titles while allowing branded accent styling where explicitly intended. |

---

## 22. Data Model

### Conceptual Model Interfaces (Version 2) `[PROPOSED]`:

```kotlin
// 1. Definition Objects
data class ThemeDefinition(
    val id: String,
    val name: String,
    val bgStart: Color,
    val bgEnd: Color,
    val glowColor: Color,
    val bubbleFridayColor: Color,
    val bubbleUserColor: Color,
    val label: String
)

data class FontFamilyDefinition(
    val id: String,
    val familyName: String,
    val fontFamily: FontFamily,
    val initial: String,
    val weights: List<FontWeightOption>
)

data class WallpaperDefinition(
    val id: String,
    val name: String,
    val source: WallpaperSource,
    val resId: Int? = null,
    val filePath: String? = null
)

// 2. Resolved Runtime Configuration
data class ResolvedAppSettings(
    val theme: ThemeDefinition,
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
    val wallpaper: WallpaperDefinition,
    val voiceConfig: VoiceConfig,
    val alarmSound: String,
    val clockVoiceAnnounce: Boolean
)
```

---

## 23. Settings API

The Settings API exposed by `SettingsViewModel` to UI components `[PROPOSED]`:

```kotlin
interface SettingsApi {
    // Read State
    val activeTheme: StateFlow<String>
    val activeFont: StateFlow<String>
    val activeWallpaper: StateFlow<String>
    val previewTheme: StateFlow<String>
    val previewFont: StateFlow<String>
    val previewWallpaper: StateFlow<String>

    // Theme Actions
    fun previewTheme(id: String)
    fun applyTheme()
    fun revertTheme()
    fun revertToDefaultTheme()

    // Font Actions
    fun previewFont(familyId: String, weightId: String? = null)
    fun applyFont()
    fun revertFont()
    fun revertFontToDefault()

    // Wallpaper Actions
    fun previewWallpaper(id: String)
    fun applyWallpaper()
    fun revertWallpaper()
    fun revertWallpaperToDefault()
    fun setUserWallpaper(uri: Uri)

    // Voice & Clock Actions
    fun setVoiceProvider(provider: String)
    fun setVoiceId(id: String)
    fun setAlarmSoundOption(option: String)
    fun setClockVoiceAnnounce(enabled: Boolean)
}
```

---

## 24. Defaults / Fallbacks

If a persisted DataStore key is missing, corrupted, or references a deleted asset, the system applies deterministic fallbacks `[PROPOSED]`:

| Setting Domain | Missing / Invalid Key | Fallback Resolution | Status |
| :--- | :--- | :--- | :--- |
| **Theme** | `null` or unknown ID (e.g. `"deleted_theme"`) | `SettingsOptions.LavenderTheme` (`"lavender"`) | `[VERIFIED]` in `SettingsRepository` |
| **Font** | `null` or unknown ID (e.g. `"old_font_id"`) | `AppFonts.PlayfairDisplay` (`"playfairdisplay_regular"`) | `[VERIFIED]` in `SettingsRepository` |
| **Wallpaper** | `null`, unknown ID, or missing user image file | `prebundledWallpapers.first()` (`"sunrise"` / `R.drawable.image_39`) | `[VERIFIED]` in `DynamicBackground` |
| **Voice Provider** | `null` or unknown provider string | `"deepgram"` | `[VERIFIED]` in `SettingsRepository` |
| **Voice ID** | `null` or unknown voice ID | `"aura-asteria-en"` | `[VERIFIED]` in `SettingsRepository` |
| **Alarm Sound** | `null` or invalid sound ID | `"default_alarm"` (Android System Alarm Ringtone) | `[VERIFIED]` in `SettingsRepository` |

---

## 25. Migration Strategy

To safely transition from Version 1 legacy registries to Version 2 canonical models without breaking existing user installs:

1. **Legacy Font Key Mapping** `[PROPOSED / HUMAN DECISION REQUIRED]`:
   - Proposed font key mapping choices for `SettingsRepository.getFontFlow()`:
     - `"system"` -> `"playfairdisplay_regular"` `[PROPOSED / HUMAN DECISION REQUIRED]` (Confirm if `"system"` should map to `PlayfairDisplay` or retain system default `FontFamily.Default`).
     - `"inter"` -> `"robotomono_regular"` `[PROPOSED / HUMAN DECISION REQUIRED]`
     - `"serif"` -> `"playfairdisplay_regular"` `[PROPOSED / HUMAN DECISION REQUIRED]`
     - `"monospace"` -> `"robotomono_regular"` `[PROPOSED / HUMAN DECISION REQUIRED]`
   - On initial read, if a legacy key is detected, `SettingsRepository` updates DataStore to the mapped canonical Version 2 font string after verification during the implementation phase.

2. **Legacy Wallpaper Key Mapping** `[PROPOSED]`:
   - Obsolete gradient keys (`"default"`, `"midnight"`, `"deep_space"`, `"slate"`) map directly to `"sunrise"`.
   - On initial read, `SettingsRepository` sanitizes wallpaper keys to valid IDs in `prebundledWallpapers`.

3. **Registry Cleanup** `[PROPOSED]`:
   - Deprecate and remove `SettingsOptions.fonts` and `SettingsOptions.wallpapers` from `SettingsData.kt`.

---

## 26. File Responsibility Changes

| File Path | Version 2 Status | Action / Responsibility |
| :--- | :--- | :--- |
| `app/.../models/SettingsData.kt` | **REFACTOR** `[PROPOSED]` | Remove obsolete `fonts` and `wallpapers` lists. Retain `AppTheme` and `SettingsOptions.themes`. |
| `app/.../ui/theme/Theme.kt` | **REFACTOR** `[PROPOSED]` | Connect `BaronessAppTheme` to `SettingsViewModel` and dynamic `MaterialTheme` color scheme + typography. |
| `app/.../ui/theme/Type.kt` | **REFACTOR** `[PROPOSED]` | Maintain canonical `AppFonts` registry; add dynamic `Typography` generator function. |
| `app/.../ui/theme/ChatTypography.kt` | **REFACTOR / RENAME** `[PROPOSED]` | Evolve into `BaronessTypography` providing global text styles across all app screens. |
| `app/.../components/DynamicBackground.kt` | **REFACTOR** `[PROPOSED]` | Standardize as sole wallpaper renderer across `Dashboard`, `ChatList`, `ChatRoom`, and `Wishlist`. |
| `app/.../screens/ChatListScreen.kt` | **REFACTOR** `[PROPOSED]` | Replace hardcoded `#0F0F12` background with `DynamicBackground`. |
| `app/.../screens/WishlistScreen.kt` | **REFACTOR** `[PROPOSED]` | Replace static `R.drawable.image_15` background with `DynamicBackground`. |
| `app/.../screens/PhotosScreen.kt` | **REFACTOR** `[PROPOSED]` | Wrap content in global theme context to inherit selected typography. |
| `app/.../components/GlobalDrawer.kt` | **REFACTOR** `[PROPOSED]` | Wire drawer headers to `LocalBaronessTypography`. |
| `app/.../viewmodels/SettingsViewModel.kt` | **REFACTOR** `[PROPOSED]` | Clean up preview states and enforce single-source state dispatches. |
| `app/.../repository/SettingsRepository.kt` | **REFACTOR** `[PROPOSED]` | Add legacy key migration logic for fonts and wallpapers. |

---

## 27. Proposed New Files

| Proposed File Path | Purpose | Status |
| :--- | :--- | :--- |
| `app/src/main/java/com/baroness/app/ui/theme/BaronessTheme.kt` | CompositionLocal definitions (`LocalBaronessTheme`, `LocalBaronessTypography`, `LocalBaronessBackground`) for Baroness-specific tokens not covered by standard `MaterialTheme`. | `[PROPOSED]` |

---

## 28. Proposed Deleted / Deprecated Files

*No files are deleted entirely.* Obsolete properties inside `SettingsData.kt` (`SettingsOptions.fonts` and `SettingsOptions.wallpapers`) will be deprecated and removed during refactoring `[PROPOSED]`.

---

## 29. Implementation Sequence

### Phase 1: Foundations & Registry Cleanup `[PROPOSED]`
- Remove obsolete `SettingsOptions.fonts` and `SettingsOptions.wallpapers` from `SettingsData.kt`.
- Implement legacy key sanitization in `SettingsRepository.kt`.

### Phase 2: Composition Local & Dynamic Theme System `[PROPOSED]`
- Create `BaronessTheme.kt` defining `LocalBaronessTheme`, `LocalBaronessTypography`, and `LocalBaronessBackground`.
- Update `Theme.kt` (`BaronessAppTheme`) to observe `SettingsViewModel` active theme and font, building dynamic Material 3 `ColorScheme` and `Typography`.

### Phase 3: Global Typography Rollout `[PROPOSED]`
- Update `Type.kt` and `ChatTypography.kt` to expose `rememberBaronessTypography(fontId)`.
- Connect `MaterialTheme.typography` in `BaronessAppTheme` so all standard `Text` composables across the app reflect the active font.

### Phase 4: Dynamic Wallpaper Rollout `[PROPOSED]`
- Update `DynamicBackground.kt` to handle fallbacks safely.
- Refactor `ChatListScreen.kt` and `WishlistScreen.kt` to consume `DynamicBackground(activeWallpaper)`.

### Phase 5: Screen & Component Bypass Migration `[PROPOSED]`
- Refactor `PhotosScreen.kt`, `DashboardScreen.kt`, and `GlobalDrawer.kt` to consume global theme colors and typography.
- Verify `MessageBubble.kt` retains its explicit `masterMetaStyle` timestamp exception.

### Phase 6: End-to-End Verification `[PROPOSED]`
- Validate persistence, cold-start restoration, drawer previewing, revert actions, and process death survival.

---

## 30. Testing & Acceptance Criteria

### 1. Font Setting Verification Test `[NEEDS IMPLEMENTATION VALIDATION]`
1. Open settings drawer -> Navigate to FONT drawer box.
2. Select `RobotoMono` -> Tap **APPLY**.
3. Navigate to `ChatListScreen` -> Verify inbox text renders in `RobotoMono`.
4. Open `ChatRoomScreen` -> Verify message body renders in `RobotoMono`.
5. Open `DashboardScreen` -> Verify welcome text renders in `RobotoMono`.
6. Open `WishlistScreen` -> Verify header and wish items render in `RobotoMono`.
7. Kill app process via Task Manager -> Relaunch app.
8. Verify all screens render immediately in `RobotoMono` without visual text jumping or font flickering.

### 2. Theme Setting Verification Test `[NEEDS IMPLEMENTATION VALIDATION]`
1. Open settings drawer -> Select `Moonlight` theme -> Tap **APPLY**.
2. Verify chat bubbles, input bar, and primary buttons update to Moonlight glow/bubble colors.
3. Navigate through `ChatList`, `Dashboard`, and `Wishlist`.
4. Verify theme colors are consistent across surfaces.
5. Restart app -> Verify `Moonlight` theme persists as authoritative.

### 3. Wallpaper Setting Verification Test `[NEEDS IMPLEMENTATION VALIDATION]`
1. Open settings drawer -> Select `Mountain View` wallpaper -> Tap **APPLY**.
2. Verify `DashboardScreen`, `ChatRoomScreen`, `ChatListScreen`, and `WishlistScreen` display the `Mountain View` background image.
3. Select a custom gallery image -> Verify image saves to `wallpapers/user_wallpaper.jpg` and renders correctly.

### 4. Invalid Preference Value Test `[NEEDS IMPLEMENTATION VALIDATION]`
1. Inject malformed key `"invalid_font_xyz"` into DataStore.
2. Launch app -> Verify system gracefully falls back to `Playfair Display` default without crashing.

---

## 31. Performance Considerations

1. **Avoid Duplicate Flow Collection** `[PROPOSED]`: Screen composables collect settings via `SettingsViewModel` or read from `CompositionLocal`. Screens must not create independent DataStore flow readers.
2. **Efficient Font Resolution** `[PROPOSED]`: `AppFonts.resolve(fontId)` uses `remember(fontId)` caching inside `rememberBaronessTypography` to prevent re-allocating `FontFamily` instances during recomposition.
3. **Bitmap Memory Optimization** `[PROPOSED]`: `DynamicBackground` caches bitmap loading for user gallery wallpapers and uses Coil/Painter memory management for drawable resources.
4. **Lifecycle-Aware Flow Collection** `[VERIFIED]`: All UI state collection uses `collectAsStateWithLifecycle()` to suspend collection when screens are stopped or in the background.

---

## 32. Architectural Rules

### Rule 1
A persisted global setting must have exactly one preference key in `baroness_prefs` DataStore. `[PROPOSED]`

### Rule 2
A global setting must have exactly one authoritative `StateFlow` representation in `SettingsViewModel`. `[PROPOSED]`

### Rule 3
Screens must never declare local state copies of global settings (e.g. no `var localTheme` in `WishlistScreen`). `[PROPOSED]`

### Rule 4
UI surfaces must receive global configuration through `MaterialTheme` (for standard M3 styling) or `CompositionLocal` environments (`LocalBaronessTheme`, `LocalBaronessTypography`) for custom Baroness tokens. `[PROPOSED]`

### Rule 5
Registry objects (`AppFonts`, `SettingsOptions`, `prebundledWallpapers`) are static constants. They must never store user preference state. `[PROPOSED]`

### Rule 6
Every preference read must define an explicit fallback to guarantee crash-free startup even if disk data is missing or corrupted. `[PROPOSED]`

---

## 33. Final Architecture Diagram

```text
                           ┌─────────────────────────────────┐
                           │          Settings UI            │
                           │ (GlobalDrawer / Settings Boxes) │
                           └────────────────┬────────────────┘
                                            │
                                            │ preview*() / apply*()
                                            ▼
                           ┌─────────────────────────────────┐
                           │        SettingsViewModel        │
                           │   (Authoritative Runtime Hub)   │
                           └───────┬─────────────────┬───────┘
                                   │                 │
              save*() / Flow reads │                 │ Exposes StateFlows
                                   ▼                 ▼
                           ┌───────────────┐ ┌──────────────────────────────┐
                           │  Settings     │ │     BaronessAppTheme         │
                           │  Repository   │ │   (Root Theme Provider)      │
                           └───────┬───────┘ └──────────────┬───────────────┘
                                   │                        │
                                   ▼                        │ Binds MaterialTheme &
                           ┌───────────────┐                │ CompositionLocals
                           │  DataStore    │                ▼
                           │(baroness_prefs│ ┌──────────────────────────────┐
                           └───────────────┘ │ UI CONSUMERS                 │
                                             │ - ChatListScreen             │
                                             │ - ChatRoomScreen             │
                                             │ - DashboardScreen            │
                                             │ - WishlistScreen             │
                                             │ - PhotosScreen               │
                                             │ - GlobalDrawer               │
                                             └──────────────────────────────┘
```

---

## 34. Risks / Open Questions

1. **Supabase Sync Interruption**: Offline changes to user profiles or settings must safely queue in DataStore without blocking local UI updates.
2. **High-Resolution Custom Wallpapers**: Large camera photos selected as custom wallpapers could cause memory pressure if loaded uncompressed. *Mitigation*: Ensure `DynamicBackground` downsamples custom gallery images during bitmap decoding.
3. **Legacy Font Mapping Choice** `[NEEDS HUMAN DECISION]`: Confirm whether legacy `"system"` font key should auto-migrate to `"playfairdisplay_regular"` or map to standard system `FontFamily.Default`.

---

## 35. Final Implementation Checklist

- [x] Current repository source files inspected.
- [x] Historical forensic audit (`KotlinFile_Review.md`) reviewed and verified.
- [x] Every major global setting assigned a single source of truth.
- [x] Definition registries separated from user preference state.
- [x] Theme architecture and M3 integration defined.
- [x] Dynamic typography system designed.
- [x] Unified wallpaper rendering model specified.
- [x] Bypasses in `Theme.kt`, `ChatListScreen`, `WishlistScreen`, and `PhotosScreen` cataloged and mapped for resolution.
- [x] Legacy registry deprecation & migration strategy defined.
- [x] Cold start and process death lifecycle specified for zero-flash loading target (to be empirically validated during implementation testing).
- [x] Safe implementation sequence and concrete acceptance criteria established.

---

BARONESS VERSION 2 BLUEPRINT: LOCKED

Implementation Status: DESIGN ONLY — NO SOURCE CHANGES PERFORMED

Validation Status: FINAL SURGICAL CORRECTIONS APPLIED

Next Phase: IMPLEMENTATION — V2.1 SETTINGS FOUNDATION

Design Validation Date: September 27, 2026

BARONESS VERSION 2 BLUEPRINT: LOCKED
