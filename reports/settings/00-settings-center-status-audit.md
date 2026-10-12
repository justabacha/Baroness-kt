# Settings Center — Status Audit Report

## 1. Executive Summary

- **Total Settings Screens / Destinations Identified**: 12 (1 Main Hub + 9 Sub-Screens Written + 2 Unwritten Destinations).
- **Implementation State Breakdown**:
  - **COMPLETE**: 9 screens (`AppearanceSettingsPage`, `ThemeSettingsPage`, `FontSettingsPage`, `WallpaperSettingsPage`, `SoundHapticsSettingsPage`, `FridaySettingsPage`, `NotificationSettingsPage`, `PrivacySettingsPage`, `ProfileSettingsPage`).
  - **PARTIAL**: 1 screen (`SettingsCenterScreen` — functional discovery hub, but 2 of its 8 category cards do not navigate anywhere).
  - **PLACEHOLDER**: 0 screens.
  - **NOT WRITTEN**: 2 screens (`Storage & Data` destination screen, `About Baroness` destination screen).
- **Honest State of Settings Center**: The Settings Center is **90% complete and highly functional**, with 9 out of 10 configured sub-pages fully implemented, themed, and persisted; only 2 top-level category cards ("Storage & Data" and "About Baroness") remain unhandled stubs lacking destination screens.

---

## 2. Entry Point and Navigation

- **Entry Point File Path**: `app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt`
- **Composable Name**: `SettingsCenterScreen`
- **Host & Routing Setup**: Hosted in `MainActivity.kt` inside Jetpack Navigation Compose `NavHost` (lines 284–313).
- **Navigation Mechanism**: Route-based navigation via `navController.navigate("<route>") { launchSingleTop = true }`.
- **Complete List of Reachable / Registered Routes**:
  1. `"settings"` — Main hub (`SettingsCenterScreen`)
  2. `"settings/profile"` — Profile & Identity (`ProfileSettingsPage`)
  3. `"settings/appearance"` — Appearance Gateway (`AppearanceSettingsPage`)
  4. `"settings/appearance/theme"` — Theme Customization (`ThemeSettingsPage`)
  5. `"settings/appearance/font"` — Typography & Fonts (`FontSettingsPage`)
  6. `"settings/appearance/wallpaper"` — Wallpaper & Background (`WallpaperSettingsPage`)
  7. `"settings/sound"` — Sound & Haptics (`SoundHapticsSettingsPage`)
  8. `"settings/friday"` — FRIDAY AI Companion (`FridaySettingsPage`)
  9. `"settings/notifications"` — Notifications (`NotificationSettingsPage`)
  10. `"settings/privacy"` — Privacy & Security (`PrivacySettingsPage`)

*(Note: No routes are registered in `MainActivity.kt` for "Storage & Data" or "About Baroness".)*

---

## 3. Full Screen Inventory

| Screen / File | Route (if any) | Reachable from Settings Center? | Primary Purpose | Implementation State |
| :--- | :--- | :--- | :--- | :--- |
| `screens/settings/SettingsCenterScreen.kt` | `"settings"` | **Yes** (Entry Point) | Main discovery hub listing profile card & 8 settings categories | **PARTIAL** |
| `screens/settings/ProfileSettingsPage.kt` | `"settings/profile"` | **Yes** | Profile picture crop/upload, display name, persona ID, FCM sync, logout | **COMPLETE** |
| `screens/settings/AppearanceSettingsPage.kt` | `"settings/appearance"` | **Yes** | Appearance gateway routing to Theme, Font, and Wallpaper sub-pages | **COMPLETE** |
| `screens/settings/ThemeSettingsPage.kt` | `"settings/appearance/theme"` | **Yes** | Theme preset selection (Lavender, Moonlight, Golden, Rose, Ocean), live chat preview, revert/apply | **COMPLETE** |
| `screens/settings/FontSettingsPage.kt` | `"settings/appearance/font"` | **Yes** | Font family & weight selection, live typography preview, revert/apply | **COMPLETE** |
| `screens/settings/WallpaperSettingsPage.kt` | `"settings/appearance/wallpaper"` | **Yes** | Wallpaper carousel gallery, custom photo upload, live background preview, revert/apply | **COMPLETE** |
| `screens/settings/SoundHapticsSettingsPage.kt` | `"settings/sound"` | **Yes** | AVIS TTS voice config (speed/pitch/voice ID/persona optimization), clock voice readout, alarm/timer chimes, haptics | **COMPLETE** |
| `screens/settings/FridaySettingsPage.kt` | `"settings/friday"` | **Yes** | AI companion status overview, core capabilities list, link to Sound & Haptics | **COMPLETE** |
| `screens/settings/NotificationSettingsPage.kt` | `"settings/notifications"` | **Yes** | In-app banner overlay toggle & system notification settings launcher | **COMPLETE** |
| `screens/settings/PrivacySettingsPage.kt` | `"settings/privacy"` | **Yes** | Vault gate security status, active persona/JWT auth info, local session teardown & re-gate | **COMPLETE** |
| *Storage & Data Screen* | *None* | **No** (Card on hub is unhandled) | Database Backup & Restore, Voice Cache management | **NOT WRITTEN** |
| *About Baroness Screen* | *None* | **No** (Card on hub is unhandled) | App Story, Concept Credits, Version Info, PhestyDrop updates | **NOT WRITTEN** |

---

## 4. Per-Screen Detail

### `SettingsCenterScreen` (`app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt`)
- **Purpose**: Top-level entry hub displaying active user profile summary card and a list of 8 settings category cards.
- **What Works Today**:
  - Displays `ProfileHeaderCard` with `ExpandableAvatar`, user display name, and version info.
  - Category cards for Profile, Appearance, Sound & Haptics, FRIDAY, Notifications, and Privacy & Security render and navigate to their respective sub-pages when clicked.
- **What Is Incomplete**:
  - Category cards for "Storage & Data" (`id = "storage"`) and "About Baroness" (`id = "about"`) exist in `SETTINGS_CATEGORIES`, but are omitted from the `when (category.id)` click handler in `CategoryCard` (lines 142–151). Tapping either card performs a no-op and does not navigate anywhere.
- **`TODO` / `FIXME` / Stub Markers**: None explicitly in text; unhandled `when` branches.

---

### `ProfileSettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/ProfileSettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: Fully functional profile editor with avatar cropping via uCrop, display name editing with live IME preview bar, read-only persona badge, FCM token sync status indicator, update persistence, and logout confirmation dialog.

---

### `AppearanceSettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/AppearanceSettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: Gateway screen displaying current active theme name, font family name, and wallpaper name, navigating cleanly to Theme, Font, and Wallpaper sub-pages.

---

### `ThemeSettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/ThemeSettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: Renders all 5 theme options with color swatches, live chat bubble preview, and working Revert / Apply state management.

---

### `FontSettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/FontSettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: Scrollable font family list, dynamic weight filter chips, real-time live text preview, and working Revert / Apply state management.

---

### `WallpaperSettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/WallpaperSettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: Horizontal carousel pager for prebundled wallpapers, custom photo picker button, 9:16 live aspect preview card, and working Revert / Apply state management.

---

### `SoundHapticsSettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/SoundHapticsSettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: Full TTS voice controls (master toggle, audio preview button, smart persona optimization, voice selection chips, speed/pitch sliders), spoken clock readout switch, alarm sound options, timer chime options, and vibration pattern chips with audio/haptic previews.

---

### `FridaySettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/FridaySettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: AI companion system status card ("System Active"), core capabilities overview rows, and working navigation button to Sound & Haptics settings.

---

### `NotificationSettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/NotificationSettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: In-app banner overlay toggle persisted in `SettingsViewModel` and "Configure in Android Settings" button launching system notification settings intent.

---

### `PrivacySettingsPage` (`app/src/main/java/com/baroness/app/screens/settings/PrivacySettingsPage.kt`)
- **State**: **COMPLETE**
- **Confirmation**: Displays vault gate security status, active persona info, session token status, FCM push status, DataStore status, and session reset button with confirmation dialog that logs out and returns to the Vault Gate screen.

---

## 5. Menu Inventory

### Settings Center Top-Level Menu (`SettingsCenterScreen.kt`)

| Menu Label | Destination | State |
| :--- | :--- | :--- |
| Profile & Identity | `settings/profile` (`ProfileSettingsPage`) | **NAVIGATES** |
| Appearance | `settings/appearance` (`AppearanceSettingsPage`) | **NAVIGATES** |
| Sound & Haptics | `settings/sound` (`SoundHapticsSettingsPage`) | **NAVIGATES** |
| FRIDAY | `settings/friday` (`FridaySettingsPage`) | **NAVIGATES** |
| Notifications | `settings/notifications` (`NotificationSettingsPage`) | **NAVIGATES** |
| Privacy & Security | `settings/privacy` (`PrivacySettingsPage`) | **NAVIGATES** |
| Storage & Data | *None* (Unhandled in `SettingsCenterScreen.kt`) | **STUB** |
| About Baroness | *None* (Unhandled in `SettingsCenterScreen.kt`) | **STUB** |

---

### Appearance Sub-Menu (`AppearanceSettingsPage.kt`)

| Menu Label | Destination | State |
| :--- | :--- | :--- |
| Theme Customization | `settings/appearance/theme` (`ThemeSettingsPage`) | **NAVIGATES** |
| Typography & Fonts | `settings/appearance/font` (`FontSettingsPage`) | **NAVIGATES** |
| Wallpaper & Background | `settings/appearance/wallpaper` (`WallpaperSettingsPage`) | **NAVIGATES** |

---

## 6. Per-Screen Settings Rows

### 1. `SettingsCenterScreen.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| User Profile Header Card | `SettingsCenterScreen.kt:183` | Shows `ExpandableAvatar` & user profile info | **WORKING** |
| Profile & Identity Category Card | `SettingsCenterScreen.kt:82` | `navController.navigate("settings/profile")` | **WORKING** |
| Appearance Category Card | `SettingsCenterScreen.kt:83` | `navController.navigate("settings/appearance")` | **WORKING** |
| Sound & Haptics Category Card | `SettingsCenterScreen.kt:84` | `navController.navigate("settings/sound")` | **WORKING** |
| FRIDAY Category Card | `SettingsCenterScreen.kt:85` | `navController.navigate("settings/friday")` | **WORKING** |
| Notifications Category Card | `SettingsCenterScreen.kt:86` | `navController.navigate("settings/notifications")` | **WORKING** |
| Privacy & Security Category Card | `SettingsCenterScreen.kt:87` | `navController.navigate("settings/privacy")` | **WORKING** |
| Storage & Data Category Card | `SettingsCenterScreen.kt:88` | Omitted in `when (category.id)` switch (no-op) | **VISUAL ONLY** |
| About Baroness Category Card | `SettingsCenterScreen.kt:89` | Omitted in `when (category.id)` switch (no-op) | **VISUAL ONLY** |

---

### 2. `ProfileSettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| Profile Picture Box | `ProfileSettingsPage.kt:180` | `imagePickerLauncher.launch("image/*")` | **WORKING** |
| "Change Profile Picture" Text Button | `ProfileSettingsPage.kt:216` | `imagePickerLauncher.launch("image/*")` | **WORKING** |
| Assigned Persona Read-Only Field | `ProfileSettingsPage.kt:246` | Renders active persona name | **WORKING** |
| Display Name OutlinedTextField | `ProfileSettingsPage.kt:280` | `viewModel.updateName(it)` | **WORKING** |
| FCM Push Notification Sync Status | `ProfileSettingsPage.kt:299` | Renders sync status of `fcm_token` | **WORKING** |
| UPDATE Button | `ProfileSettingsPage.kt:327` | `viewModel.onSave { ... }` | **WORKING** |
| LOG OUT OutlinedButton | `ProfileSettingsPage.kt:387` | Shows confirmation dialog -> `viewModel.onLogout` | **WORKING** |
| IME Live Edit Preview Bar | `ProfileSettingsPage.kt:458` | Displays live text preview above keyboard | **WORKING** |

---

### 3. `AppearanceSettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| Theme Customization Option Card | `AppearanceSettingsPage.kt:101` | `navController.navigate("settings/appearance/theme")` | **WORKING** |
| Typography & Fonts Option Card | `AppearanceSettingsPage.kt:113` | `navController.navigate("settings/appearance/font")` | **WORKING** |
| Wallpaper & Background Option Card | `AppearanceSettingsPage.kt:125` | `navController.navigate("settings/appearance/wallpaper")` | **WORKING** |

---

### 4. `ThemeSettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| Theme Option Rows (Lavender, Moonlight, Golden, Rose, Ocean) | `ThemeSettingsPage.kt:104` | `settingsViewModel.previewTheme(theme.id)` | **WORKING** |
| Live Chat Preview Card | `ThemeSettingsPage.kt:173` | Renders `ChatBubble` preview using preview theme colors | **WORKING** |
| REVERT Button | `ThemeSettingsPage.kt:222` | `settingsViewModel.revertTheme()` / `revertToDefault()` | **WORKING** |
| APPLY Button | `ThemeSettingsPage.kt:240` | `settingsViewModel.applyTheme()` | **WORKING** |

---

### 5. `FontSettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| Font Family Selector Rows | `FontSettingsPage.kt:163` | `settingsViewModel.previewFont(family.id, ...)` | **WORKING** |
| Weight Selector Filter Chips | `FontSettingsPage.kt:252` | `settingsViewModel.previewFont(selectedFamily.id, weight.id)` | **WORKING** |
| Live Typography Preview Card | `FontSettingsPage.kt:290` | Renders sample text using resolved font & weight | **WORKING** |
| REVERT Button | `FontSettingsPage.kt:348` | `settingsViewModel.revertFont()` / `revertFontToDefault()` | **WORKING** |
| APPLY Button | `FontSettingsPage.kt:368` | `settingsViewModel.applyFont()` | **WORKING** |

---

### 6. `WallpaperSettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| Wallpaper Gallery Horizontal Pager | `WallpaperSettingsPage.kt:180` | Updates preview wallpaper on swipe/click | **WORKING** |
| "Add from Gallery" OutlinedButton | `WallpaperSettingsPage.kt:228` | `imagePickerLauncher.launch("image/*")` | **WORKING** |
| Wallpaper Live Preview Card | `WallpaperSettingsPage.kt:254` | Displays full-aspect wallpaper image preview | **WORKING** |
| REVERT Button | `WallpaperSettingsPage.kt:302` | `settingsViewModel.revertWallpaper()` / `revertWallpaperToDefault()` | **WORKING** |
| APPLY Button | `WallpaperSettingsPage.kt:322` | `settingsViewModel.applyWallpaper()` | **WORKING** |

---

### 7. `SoundHapticsSettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| Voice Preview/Stop IconButton | `SoundHapticsSettingsPage.kt:111` | `settingsViewModel.previewVoice()` / `stopVoice()` | **WORKING** |
| Voice Announcements Master Switch | `SoundHapticsSettingsPage.kt:164` | `settingsViewModel.setVoiceEnabled(it)` | **WORKING** |
| Smart Persona Voice Optimization Switch | `SoundHapticsSettingsPage.kt:189` | `settingsViewModel.setUsePersonaVoices(it)` | **WORKING** |
| Voice Selection Filter Chips | `SoundHapticsSettingsPage.kt:218` | `settingsViewModel.setVoiceId(voice.id)` | **WORKING** |
| Speed Slider | `SoundHapticsSettingsPage.kt:258` | `settingsViewModel.setVoiceSpeed(it)` | **WORKING** |
| Pitch Slider | `SoundHapticsSettingsPage.kt:292` | `settingsViewModel.setVoicePitch(it)` | **WORKING** |
| Friday Voice Readout Switch | `SoundHapticsSettingsPage.kt:362` | `settingsViewModel.setClockVoiceAnnounce(it)` | **WORKING** |
| Alarm Sound Filter Chips | `SoundHapticsSettingsPage.kt:394` | `settingsViewModel.setAlarmSoundOption(...)` + `ClockSoundPlayer` | **WORKING** |
| Timer Chime Filter Chips | `SoundHapticsSettingsPage.kt:435` | `settingsViewModel.setTimerChimeOption(...)` + `ClockSoundPlayer` | **WORKING** |
| Alarm & Timer Vibration Filter Chips | `SoundHapticsSettingsPage.kt:489` | `settingsViewModel.setAlarmVibrationPattern(...)` + `ClockSoundPlayer` | **WORKING** |

---

### 8. `FridaySettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| AI Companion System Status Card | `FridaySettingsPage.kt:76` | Informational display ("System Active") | **WORKING** |
| Core Capabilities Card | `FridaySettingsPage.kt:133` | Informational rows for Conversation, Memory, Governance | **WORKING** |
| "Open Sound & Haptics Settings" OutlinedButton | `FridaySettingsPage.kt:207` | `navController.navigate("settings/sound")` | **WORKING** |

---

### 9. `NotificationSettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| In-App Banner Overlays Switch | `NotificationSettingsPage.kt:105` | `settingsViewModel.setInAppBannersEnabled(enabled)` | **WORKING** |
| "CONFIGURE IN ANDROID SETTINGS" OutlinedButton | `NotificationSettingsPage.kt:162` | Launches `Settings.ACTION_APP_NOTIFICATION_SETTINGS` | **WORKING** |

---

### 10. `PrivacySettingsPage.kt`
| Row Label | File & Line | Handler | State |
| :--- | :--- | :--- | :--- |
| Gate Protection Status Row | `PrivacySettingsPage.kt:105` | Displays vault gate protection status | **WORKING** |
| Active Persona Read-Only Row | `PrivacySettingsPage.kt:133` | Displays active persona name | **WORKING** |
| Authentication Engine Read-Only Row | `PrivacySettingsPage.kt:159` | Displays auth engine info | **WORKING** |
| Logged-in Profile Row | `PrivacySettingsPage.kt:202` | Displays display name | **WORKING** |
| Persona ID Row | `PrivacySettingsPage.kt:228` | Displays current persona ID | **WORKING** |
| DataStore Session Cache Row | `PrivacySettingsPage.kt:254` | Displays DataStore status | **WORKING** |
| FCM Push Notification Status Row | `PrivacySettingsPage.kt:280` | Displays FCM token status | **WORKING** |
| Auth Token Status Row | `PrivacySettingsPage.kt:306` | Displays auth token status | **WORKING** |
| "CLEAR LOCAL SESSION & RE-GATE" OutlinedButton | `PrivacySettingsPage.kt:350` | Opens dialog -> `SessionLifecycleManager.logout` -> gate | **WORKING** |

---

## 7. TODOs and Stubs List

1. **`SettingsCenterScreen.kt` (lines 142–151)**:
   - **Context**: `when (category.id)` switch inside `CategoryCard` click handler.
   - **Issue**: `"storage"` and `"about"` IDs are absent from the `when` block. Tapping either card performs a no-op.
2. **`MainActivity.kt` (lines 284–313)**:
   - **Context**: `NavHost` composable routes setup.
   - **Issue**: Routes `"settings/storage"` and `"settings/about"` are missing from `NavHost`.
3. **`components/DrawerStorage.kt` (line 25)**:
   - **Context**: Drawer storage panel composable.
   - **Text**: `"Coming soon..."`
4. **`services/FCMService.kt` (line 61)**:
   - **Text**: `// TODO: Logic to decide between System vs In-App notification`
5. **`utils/NotificationManager.kt` (line 53)**:
   - **Text**: `.setSmallIcon(R.drawable.icon) // TODO: Use a proper silhouette icon`

---

## 8. What's Still Open

All incomplete or unwritten items in the Settings Center:

1. **Storage & Data Sub-Screen (`NOT WRITTEN`)**:
   - The "Storage & Data" card exists on `SettingsCenterScreen` (`title = "Storage & Data"`, `description = "Database Backup & Restore, Voice Cache"`), but clicking it has no handler and no destination screen (`"settings/storage"`) exists.
2. **About Baroness Sub-Screen (`NOT WRITTEN`)**:
   - The "About Baroness" card exists on `SettingsCenterScreen` (`title = "About Baroness"`, `description = "App Story, Concept Credits, Version Info"`), but clicking it has no handler and no destination screen (`"settings/about"`) exists. *(Note: `DrawerAbout.kt` exists as a drawer panel, but is not linked as a Settings Center destination screen).*
3. **Drawer Storage Panel (`PLACEHOLDER`)**:
   - `components/DrawerStorage.kt` renders `"Coming soon..."`.

---

## Profile Header Card Flicker Fix

### 1. Exact Source of Each Displayed Value in `ProfileHeaderCard` (Before)

In `SettingsCenterScreen.kt` (lines 205–240 before fix):
- **Display Name**: `userProfile?.displayName ?: "Phesty"` (line 237)
- **Avatar Image**: `userProfile?.avatar` passed to `ExpandableAvatar` (line 222)
- **Avatar Initial Fallback**: `(userProfile?.displayName?.take(1) ?: "P").uppercase()` (line 213)
- **Avatar Display Name Fallback**: `userProfile?.displayName ?: "You"` (line 224)
- **Version**: `BuildConfig.VERSION_NAME` (line 242)

### 2. Identified Cause

**Cause C / D:** `userProfile` was initialized as `var userProfile by remember { mutableStateOf<UserProfile?>(null) }` on line 80.
On initial composition (and initial route entry), `userProfile` starts as `null`.
`ProfileHeaderCard` was called unconditionally on line 142 (`ProfileHeaderCard(userProfile = userProfile, hazeState = hazeState)`).
Because `userProfile` was `null` during initial frame(s) while `LaunchedEffect(currentEntry)` asynchronously read `userProfile` from DataStore via `storage.getString("userProfile")`, `ProfileHeaderCard` evaluated null-coalescing fallbacks (`"Phesty"`, `"P"`, `"You"`), rendering placeholder figures on the first frame before updating to the real user profile when `userProfile` state was populated.

Offending code (before):
```kotlin
// Line 80
var userProfile by remember { mutableStateOf<UserProfile?>(null) }

// Line 142
item {
    ProfileHeaderCard(
        userProfile = userProfile,
        hazeState = hazeState
    )
}
```

### 3. Fix Applied

**`SettingsCenterScreen.kt`:**
1. Seeded `userProfile` state synchronously on first composition using `remember { runBlocking(Dispatchers.IO) { ... } }` to read `userProfile` JSON from DataStore.
2. Updated `ProfileHeaderCard` to accept a non-null `UserProfile` parameter (`userProfile: UserProfile`).
3. Wrapped `ProfileHeaderCard` in `if (userProfile != null)` inside `LazyColumn`.
4. Used string `.ifBlank { ... }` fallbacks for inner fields if empty strings are returned on a non-null `UserProfile` instance.

**Before:**
```kotlin
// Line 80
var userProfile by remember { mutableStateOf<UserProfile?>(null) }

// In LazyColumn:
item {
    ProfileHeaderCard(
        userProfile = userProfile,
        hazeState = hazeState
    )
}

// Composable signature:
@Composable
private fun ProfileHeaderCard(
    userProfile: UserProfile?,
    hazeState: HazeState
) {
    ...
    Text(text = (userProfile?.displayName?.take(1) ?: "P").uppercase(), ...)
    ExpandableAvatar(displayName = userProfile?.displayName ?: "You", ...)
    Text(text = userProfile?.displayName ?: "Phesty", ...)
}
```

**After:**
```kotlin
// Seed state synchronously before first composition frame
val initialProfile = remember {
    try {
        runBlocking(Dispatchers.IO) {
            val profileJson = storage.getString("userProfile")
            val profile = profileJson?.let {
                try { json.decodeFromString<UserProfile>(it) } catch (_: Exception) { null }
            }
            if (profile != null && profile.avatar != null) {
                val localPath = avatarRepository.getCachedAvatar(profile.avatar)
                profile.copy(avatar = localPath ?: profile.avatar)
            } else {
                profile
            }
        }
    } catch (_: Exception) {
        null
    }
}
var userProfile by remember { mutableStateOf(initialProfile) }

// In LazyColumn:
if (userProfile != null) {
    item {
        ProfileHeaderCard(
            userProfile = userProfile!!,
            hazeState = hazeState
        )
    }
}

// Composable signature:
@Composable
private fun ProfileHeaderCard(
    userProfile: UserProfile,
    hazeState: HazeState
) {
    ...
    Text(text = userProfile.displayName.take(1).ifBlank { "U" }.uppercase(), ...)
    ExpandableAvatar(displayName = userProfile.displayName.ifBlank { "You" }, ...)
    Text(text = userProfile.displayName.ifBlank { "User" }, ...)
}
```

### 4. Modified Files Confirmation

Only the following file was modified:
- `app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt`

No other screens, ViewModels, or components were modified.

### 5. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

### 6. Manual Verification Checklist

- [ ] Cold-start the app and open Settings Center. The ProfileHeaderCard shows correct data on first frame — no hardcoded placeholder for a moment.
- [ ] From Settings Center, navigate to Notifications, then back. Same — no flicker.
- [ ] Navigate to About Baroness, then back. Same — no flicker.
- [ ] Navigate to Profile Settings, then back. Same — no flicker.
- [ ] Rotate the device (if rotation is supported). Same — no flicker.
- [ ] Kill the app and reopen. Same — no flicker.

