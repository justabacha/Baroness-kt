# Settings Center — Implementation Plan (Finalized)

## 1. Purpose

This document defines the authoritative, implementation-safe **Settings Center Architecture Plan** for the Baroness application (`C:\Baroness_Core\Baroness-kt`).

This plan reconciles all previous architectural reports against the actual current repository source code. It establishes a 3-layer preference architecture consisting of:
1. A visually rich **Settings Center** discovery hub (`com.baroness.app.screens.settings.SettingsCenterScreen`).
2. Lightweight, focused **Category Sub-Screens** (`settings/appearance`, `settings/sound`, etc.).
3. Reusable, side-sliding **Contextual Settings Modules** for individual app surfaces.

This plan preserves 100% of the validated Version 2 preference foundation (V2.1–V2.5) while eliminating unconfirmed assumptions and fictitious features.

---

## 2. Source Material & Repository Verification

This reconciled plan is grounded in a forensic audit of the repository code and architectural reports:

1. **`docs/reports/KotlinFile_Review.md`**: Baseline audit establishing 150 total `.kt` files, `StorageManager` / DataStore (`baroness_prefs`) as persistence source of truth, and `SettingsViewModel` as the reactive state hub.
2. **`docs/reports/Baroness_Version2.md`**: V2 Global Settings Blueprint defining the 4-tier model (Registries -> Persistence -> State Hub -> Consumer Providers).
3. **`docs/reports/PreV2.5_ConsumerAudit.md`**: Audit cataloging compliant surfaces, bypasses, and intentional screen-local design exceptions.
4. **`docs/reports/V2.5_ConsumerMigration.md`**: Verification of consumer migrations (`ChatListScreen` & `ProfileSetupScreen` Material background integration, `WishlistScreen` global wallpaper + primary accent integration).
5. **`docs/reports/DrawerAudit.md`**: Audit establishing the necessity of transitioning from a slide-out drawer overlay (`GlobalDrawer.kt`) to a dedicated Settings Center screen.
6. **Reconciled Source Code Inspection**:
   - `SettingsViewModel.kt`: Confirmed active StateFlows for Theme, Font, Wallpaper, Voice, Clock, and Emoji recents.
   - `SettingsRepository.kt`: Confirmed key sanitization for wallpapers, themes, and legacy font keys.
   - `StorageManager.kt`: Confirmed `baroness_prefs` DataStore keys.
   - `DynamicBackground.kt`: Confirmed 8 prebundled wallpapers + `user_wallpaper` gallery photo option.
   - `Type.kt`: Confirmed 11 custom `AppFonts` families.
   - `ProfileManager.kt` & `ProfileSetupViewModel.kt`: Confirmed profile storage (`displayName`, `avatar`, `persona`, `fcm_token`).
   - `VoiceCenter.kt`: Confirmed ExoPlayer + TTS synthesis engine (Deepgram, Murf, Edge).
   - `ClockSoundPlayer.kt` & `BaronessClockManager.kt`: Confirmed alarm ringtones, timer chimes, and vibration patterns.
   - `BackupManager.kt`: Confirmed existing SQLite database import/export manager.
   - `VoiceCacheManager.kt`: Confirmed existing TTS MP3 disk cache manager.
   - `SessionManager.kt` & `AuthManager.kt`: Confirmed existing gate authentication and session token managers.
   - `NotificationCenter.kt` & `NotificationManager.kt`: Confirmed existing in-app event bus and Android OS channel managers.
   - `DrawerNotifications.kt`, `DrawerPrivacy.kt`, `DrawerStorage.kt`: Confirmed as existing UI drawer placeholders ("Coming soon...").

---

## 3. Feature Audit & Classification Matrix

Every feature and capability mentioned in prior reports has been audited against the actual source code and classified as exactly one of:
- `[CONFIRMED CURRENT]`: Fully implemented in repository code and active today.
- `[PARTIALLY WIRED]`: Backend manager/repository code exists in the project, but UI wiring to the settings surface is pending or incomplete.
- `[PLACEHOLDER / EXISTING UI ONLY]`: UI card exists in the drawer without full backend connection ("Coming soon...").
- `[FUTURE / NOT CURRENTLY IMPLEMENTED]`: Conceptually proposed, but zero code or UI exists in the repository today.
- `[REMOVED / INVENTED]`: Fictitious features identified in earlier plans that do NOT exist in the repository and have been purged.

| Feature / Setting Domain | Repository Evidence & Files | Audit Classification | Action for Settings Center Plan |
| :--- | :--- | :--- | :--- |
| **Theme Selection** | `SettingsViewModel.kt`, `SettingsRepository.kt`, `Theme.kt` (5 themes) | `[CONFIRMED CURRENT]` | Full Layer 2 Sub-Screen (`settings/appearance/theme`). |
| **Font Selection** | `SettingsViewModel.kt`, `Type.kt` (11 `AppFonts` families) | `[CONFIRMED CURRENT]` | Full Layer 2 Sub-Screen (`settings/appearance/font`). |
| **Wallpaper Selection** | `SettingsViewModel.kt`, `DynamicBackground.kt` (8 prebundled + gallery photo) | `[CONFIRMED CURRENT]` | Full Layer 2 Sub-Screen (`settings/appearance/wallpaper`). |
| **Voice / TTS Speech Config** | `SettingsViewModel.kt`, `VoiceCenter.kt` (Deepgram, Murf, Edge, speed, pitch) | `[CONFIRMED CURRENT]` | Included in Sound & Haptics Sub-Screen (`settings/sound`). |
| **Voice Director Notes** | `SettingsViewModel.kt` (`directorNote`), DataStore `"voice_director_note"` | `[CONFIRMED CURRENT]` | Included in FRIDAY Sub-Screen (`settings/friday`). |
| **Persona Voice Optimization**| `SettingsViewModel.kt` (`usePersonaVoices`), DataStore `"use_persona_voices"` | `[CONFIRMED CURRENT]` | Included in FRIDAY Sub-Screen (`settings/friday`). |
| **Clock Voice Readout** | `SettingsViewModel.kt` (`clockVoiceAnnounce`), `AlarmReceiver.kt` | `[CONFIRMED CURRENT]` | Included in Sound & Haptics Sub-Screen (`settings/sound`). |
| **Alarm Sound Selection** | `SettingsViewModel.kt` (`alarmSoundOption`), `ClockSoundPlayer.kt` | `[CONFIRMED CURRENT]` | Included in Sound & Haptics Sub-Screen (`settings/sound`). |
| **Timer Chime Selection** | `SettingsViewModel.kt` (`timerChimeOption`), `ClockSoundPlayer.kt` | `[CONFIRMED CURRENT]` | Included in Sound & Haptics Sub-Screen (`settings/sound`). |
| **Vibration Pattern** | `SettingsViewModel.kt` (`alarmVibrationPattern`), `ClockSoundPlayer.kt` | `[CONFIRMED CURRENT]` | Included in Sound & Haptics Sub-Screen (`settings/sound`). |
| **User Profile Data** | `ProfileManager.kt`, `ProfileSetupViewModel.kt` (`displayName`, `avatar`, `persona`) | `[CONFIRMED CURRENT]` | Displayed in Header & Profile Sub-Screen (`settings/profile`). |
| **FCM Token Sync Status**| `ProfileManager.kt` (`updateFcmToken`), DataStore `"fcm_token"` | `[CONFIRMED CURRENT]` | Displayed as read-only status in Profile Sub-Screen. |
| **App Story & Credits** | `DrawerAbout.kt` (`AboutContent()`) | `[CONFIRMED CURRENT]` | Reused in About Sub-Screen (`settings/about`). |
| **Database Backup** | `BackupManager.kt` (Existing manager); `DrawerStorage.kt` ("Coming soon...") | `[PARTIALLY WIRED]` | Wire existing `BackupManager` methods in Storage Sub-Screen (`settings/storage`). |
| **Voice Audio Cache** | `VoiceCacheManager.kt` (Existing manager); `DrawerStorage.kt` ("Coming soon...") | `[PARTIALLY WIRED]` | Wire existing `VoiceCacheManager` methods in Storage Sub-Screen (`settings/storage`). |
| **Session & Auth State**| `SessionManager.kt`, `AuthManager.kt` (Existing managers); `DrawerPrivacy.kt` | `[PARTIALLY WIRED]` | Wire existing session clearing methods in Privacy Sub-Screen (`settings/privacy`). |
| **Notification Banners** | `NotificationCenter.kt`, `NotificationManager.kt` (Existing managers) | `[PARTIALLY WIRED]` | Wire in-app banner status in Notification Sub-Screen (`settings/notifications`). |
| **Quote Card Download** | Screen-local canvas capture in `DashboardScreen.kt` (`captureAndSave`) | `[REMOVED / INVENTED]` | PURGED from Settings Center plan. |
| **Wishlist Accent Preview**| Local `MaterialTheme.colorScheme.primary` in `WishItem.kt` | `[REMOVED / INVENTED]` | PURGED from Settings Center plan. |
| **AI Personality Sliders** | Zero repository code | `[REMOVED / INVENTED]` | PURGED from Settings Center plan. |
| **Model / Provider Selectors**| Zero repository code | `[REMOVED / INVENTED]` | PURGED from Settings Center plan. |

---

## 4. Locked Architectural Principles & 3-Layer Model

The 3-layer architecture remains **LOCKED** as the single structural framework for global preferences:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                      LAYER 1 — SETTINGS CENTER                         │
│  Full-screen discovery hub ("settings").                               │
│  Visually rich: Active wallpaper, Haze/frosted glass, Baroness fonts.  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   LAYER 2 — CATEGORY SUB-SCREENS                       │
│  Focused, lightweight sub-pages ("settings/appearance", etc.).         │
│  Scaffold + Material background + standard top app bar.                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                LAYER 3 — CONTEXTUAL SETTINGS MODULES                   │
│  Side-sliding overlay (~90% width, full height) on host screen.        │
│  Exposes ONLY curated global settings relevant to host context.        │
└────────────────────────────────────────────────────────────────────────┘
```

### Locked Architectural Rule:
> *"Settings Center owns discovery. Settings sub-pages own category presentation. Screens own context. SettingsRepository/ViewModel owns global preference state."*

### Strict Boundary Enforcement:
1. **Single State Engine**: `SettingsViewModel` backed by `SettingsRepository` and DataStore (`baroness_prefs`) is the **sole** state holder for global user preferences. Do NOT create duplicate ViewModels (`ChatRoomSettingsViewModel`, `DashboardSettingsViewModel`) or secondary DataStore files.
2. **Contextual Isolation**: Contextual settings modules on individual screens must NEVER expose unrelated global areas (Profile, Storage, Privacy, About, FRIDAY config, security).
3. **No Forced Wallpaper on Lightweight Surfaces**:
   - **Layer 1 (Settings Center Hub)**: Rich visual treatment allowed (`DynamicBackground` + `HazeState` frosted glass).
   - **Layer 2 (Sub-screens)**: Lightweight presentation (`Scaffold`, `MaterialTheme.colorScheme.background`, standard top app bar). Do NOT render full-screen `DynamicBackground` or `HazeState` on sub-screens.
   - **Layer 3 (Contextual Modules)**: Side-sliding frosted glass overlay matching host screen style.

---

## 5. Reconciled Category Status Matrix

This matrix establishes what is currently implementable based strictly on existing repository managers versus what represents a documented future extension:

| Category Name | Category Status | Confirmed Implemented Settings / Managers | Existing Repository Files | Future Extensions | Notes & Scope |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Profile & Identity** | `[CONFIRMED CURRENT]` | Display Name, Avatar Upload, Persona Label ("Phesty"/"Baroness"), FCM Token Sync | `ProfileManager.kt`, `ProfileSetupViewModel.kt`, `UserProfile.kt` | Account Deletion, Multi-Account Switching | ProfileSetup is onboarding/vault setup; Settings Center Profile page is for profile editing. |
| **Appearance** | `[CONFIRMED CURRENT]` | Theme (5 options), Font (11 families), Wallpaper (8 prebundled + gallery upload) | `SettingsViewModel.kt`, `SettingsData.kt`, `Type.kt`, `DynamicBackground.kt` | Dark/Light auto-schedule, Accent tint customizer | Pure V2 visual architecture. |
| **Sound & Haptics** | `[CONFIRMED CURRENT]` | TTS Voice Speech Config (Provider, Voice ID, Speed, Pitch), Alarm Ringtone, Timer Chime, Vibration Pattern, Clock Voice Announce | `SettingsViewModel.kt`, `ClockSoundPlayer.kt`, `BaronessClockManager.kt`, `VoiceCenter.kt` | Custom ringtone upload, System volume sliders | Controls Android audio, TTS speech, & haptic streams. |
| **FRIDAY** | `[CONFIRMED CURRENT]` | Voice Director Notes (`directorNote`), Persona Voice Optimization (`usePersonaVoices`) | `SettingsViewModel.kt`, `VoiceCenter.kt`, `VoiceModels.kt` | LLM Temperature sliders, Memory inspection, Model picker | FRIDAY AI settings are strictly restricted to currently confirmed Director Note & Persona Voice Opt capabilities. |
| **Notifications** | `[PARTIALLY WIRED]` | In-app notification bus active; channel status | `NotificationCenter.kt`, `NotificationManager.kt`, `DrawerNotifications.kt` | Quiet Hours schedule, Push channel toggles | Sub-page will display channel status and banner toggles using existing `NotificationCenter`. |
| **Privacy & Security** | `[PARTIALLY WIRED]` | Gate Auth active; Session token management | `AuthManager.kt`, `SessionManager.kt`, `GateViewModel.kt`, `DrawerPrivacy.kt` | Vault PIN lock toggle, Fingerprint auth | Sub-page will wire existing `SessionManager` & `AuthManager` state. |
| **Storage & Data** | `[PARTIALLY WIRED]`| Database export/import & TTS audio cache management | `BackupManager.kt`, `VoiceCacheManager.kt`, `DrawerStorage.kt` | Storage usage breakdown chart, Cloud sync toggle | Sub-page will wire existing `BackupManager` and `VoiceCacheManager` methods. |
| **About Baroness** | `[CONFIRMED CURRENT]` | App Story ("A private universe for two"), Credits, Version info | `DrawerAbout.kt` (`AboutContent()`) | System diagnostics export, Changelog viewer | Reuses `AboutContent()` directly. |

---

## 6. Preferred UX & Category Structure

The top-level **Settings Center** (`SettingsCenterScreen.kt`) organizes settings into a clean, grouped layout:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        SETTINGS CENTER HEADER                          │
│  [Avatar]  Display Name ("Phesty" / "Baroness")                        │
│            "Private Universe Settings • Version 2.5"                   │
├────────────────────────────────────────────────────────────────────────┤
│                           CATEGORY GROUPS                              │
│                                                                        │
│  👤 PROFILE & IDENTITY                                                 │
│     Display Name, Avatar, Persona Assignment                           │
│                                                                        │
│  🎨 APPEARANCE                                                         │
│     Theme Customization, Typography & Fonts, Wallpaper & Background    │
│                                                                        │
│  🔊 SOUND & HAPTICS                                                    │
│     TTS Voice Speech Config, Alarm Ringtones, Timer Chimes, Vibration  │
│                                                                        │
│  🤖 FRIDAY AI                                                          │
│     Persona Voice Optimization, Voice Director Notes                   │
│                                                                        │
│  🔔 NOTIFICATIONS                                                      │
│     In-App Banners, System Channels                                    │
│                                                                        │
│  🔒 PRIVACY & SECURITY                                                 │
│     Vault Gate Security, Session Data                                  │
│                                                                        │
│  💾 STORAGE & DATA                                                     │
│     Database Backup & Restore, Voice Cache                             │
│                                                                        │
│  ℹ️ ABOUT BARONESS                                                     │
│     App Story, Concept Credits, Version Info                           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Reconciled Sub-Screen Specifications

Sub-screens (Layer 2) prioritize readability, standard Material 3 navigation, and lightweight rendering:

```text
                               CATEGORY SUB-SCREEN
┌────────────────────────────────────────────────────────────────────────┐
│ ← Appearance                                                           │
├────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 🎨 Theme Customization                                           │  │
│  │    Current: Lavender                                           > │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 🔤 Typography & Fonts                                           │  │
│  │    Current: Playfair Display                                   > │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 🖼️ Wallpaper & Background                                        │  │
│  │    Current: Sunrise                                            > │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Sub-Screen Details:

1. **Profile & Identity (`settings/profile`)**:
   - Renders display name input field, avatar image with gallery upload button, persona label ("Phesty" / "Baroness"), and FCM registration status.
   - Refactored from `ProfileSetupViewModel.kt` and `ProfileManager.kt`.
   - *Clarification*: `ProfileSetupScreen` remains the onboarding/vault setup entry point; `settings/profile` is the settings management page. `ProfileSetupScreen` root container ALREADY uses `MaterialTheme.colorScheme.background` (does NOT use `image_45` as a full-screen background) and MUST NOT regain `image_45` as a full-screen background!

2. **Appearance (`settings/appearance`)**:
   - Navigation hub for visual settings.
   - **Theme Page (`settings/appearance/theme`)**: 5 themes (`Lavender`, `Moonlight`, `Golden`, `Rose`, `Ocean`), active theme swatch grid, mock chat bubble preview card (`ThemePreviewChat`), apply/revert buttons.
   - **Font Page (`settings/appearance/font`)**: 11 `AppFonts` families (`Yuyu`, `Lifesavers`, `RobotoMono`, `Gamaamli`, `Matemasie`, `DancingScript`, `MarckScript`, `PlayfairDisplay`, `KaushanScript`, `PermanentMarker`, `ShadowsIntoLight`), font weight selector list (`FontWeightList`), live text preview card (`FontPreviewArea`), apply/revert buttons.
   - **Wallpaper Page (`settings/appearance/wallpaper`)**: 3D horizontal carousel (`WallpaperCarousel`), gallery image upload button (`setUserWallpaper`), 9:16 full-aspect preview card (`WallpaperPreviewArea`), apply/revert buttons.
   - *Canonical Wallpaper IDs*: `sunrise`, `light_hours`, `accent_bulb`, `green_street`, `sky_street`, `beautiful_skies`, `mountain_view`, `phesty_point`, `user_wallpaper`. (Obsolete legacy IDs `default`, `midnight`, `deep_space`, `slate` are purged).

3. **Sound & Haptics (`settings/sound`)**:
   - Voice/TTS Speech Configuration: Master voice announcements toggle (`voiceEnabled`), Voice provider selector (`deepgram`, `murf`, `edge`), Voice ID selection (12 Deepgram voices, `en-US-marcus`, `en-US-jenny`), Speed slider (`voiceSpeed`, 0.5f..2.0f), Pitch slider (`voicePitch`, 0.5f..1.5f), Live sample preview audition button (`previewVoice()`).
   - Spoken clock readout toggle (`clockVoiceAnnounce`).
   - Alarm ringtone options: `default_alarm` (System Default), `gentle_chime` (Gentle Chime), `digital_beep` (Digital Beep) with audition triggers via `ClockSoundPlayer.playSound()`.
   - Timer chime options: `chime_chime` (Classic Chime), `soft_bell` (Soft Bell), `marimba` (Marimba) with audition triggers.
   - Vibration pattern options: `wave` (Wave), `pulse` (Pulse), `heartbeat` (Heartbeat) with haptic audition triggers via `ClockSoundPlayer.playVibration()`.

4. **FRIDAY (`settings/friday`)**:
   - Smart persona voice optimization switch (`usePersonaVoices`).
   - Voice director note text field (`directorNote`).
   - *FRIDAY vs Voice Separation*: General TTS voice/speech synthesis controls live under Sound & Haptics. FRIDAY AI settings are strictly restricted to persona optimization and director prompt notes. No fictitious AI model selectors or memory switches are created.

5. **Notifications (`settings/notifications`)**:
   - Displays in-app notification banner status (`NotificationCenter.kt`) and system notification channel status (`NotificationManager.kt`).

6. **Privacy & Security (`settings/privacy`)**:
   - Displays vault gate security status (`AuthManager.kt`) and session data clearing button (wired to `SessionManager.kt`).

7. **Storage & Data (`settings/storage`)**:
   - Local database export and import triggers wired directly to existing `BackupManager.kt`.
   - Voice audio cache clearing trigger wired directly to existing `VoiceCacheManager.kt`.

8. **About Baroness (`settings/about`)**:
   - Reuses `AboutContent()` from `DrawerAbout.kt` unchanged, displaying narrative backstory ("A private universe for two"), concept credits ("Built by Phestone with love"), and version diagnostics.

---

## 8. Side-Sliding Contextual Settings Module Architecture

Contextual modules (Layer 3) follow the principle:
> *"Each host screen intentionally declares a curated subset of existing global settings that are useful in that context."*

### Presentation Specification (LOCKED):
The contextual settings module is a **side-sliding overlay surface**:
- Enters from the side of the host screen.
- Occupies approximately **90% of the host screen width**.
- Spans the **full available screen height**.
- Overlays the host screen using Baroness's frosted glass visual language (`HazeState` blur overlays).
- Exposes ONLY curated global settings relevant to the host screen context.
- Acts as a UI entry point into existing global settings; it MUST NOT create a second settings engine or own separate persistence.

```text
HOST SCREEN WITH SIDE-SLIDING CONTEXTUAL MODULE
┌──────────────────────────────────────────────┐
│ HOST CONTENT                        ┌────────┤
│ (Chat Room /                        │ ⚙️     │
│  Dashboard /                        │ CONTEXT│
│  Wishlist)                          │ SETTING│
│                                     │ MODULE │
│                                     │        │
│                                     │ ~90%   │
│                                     │ WIDTH  │
│                                     │        │
│                                     │ FULL   │
│                                     │ HEIGHT │
│                                     └────────┤
└──────────────────────────────────────────────┘
```

### Reconciled Contextual Module Matrix:

| Host Screen | Candidate Global Settings | Evidence in Repository | Implementation Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **`ChatRoomScreen`** | Theme, Font, Wallpaper, Voice Readout Toggle | High (Chat room is primary consumer of themes, fonts, wallpapers, and spoken voice readouts) | `[PROPOSED]` | Side-sliding overlay (~90% width, full height) overlaying chat. Directly calls `SettingsViewModel`. |
| **`DashboardScreen`** | Wallpaper Selector | Medium (Dashboard consumes `DynamicBackground`) | `[OPEN UX DECISION]` | Optional side-sliding wallpaper customizer. |
| **`WishlistScreen`** | Wallpaper Selector | Medium (Wishlist consumes `DynamicBackground`) | `[OPEN UX DECISION]` | Optional side-sliding wallpaper customizer. |
| **`ChatListScreen`** | None | Low (ChatList uses `MaterialTheme.colorScheme.background`) | `[CONFIRMED EXCLUDED]`| ChatList uses clean Material background; contextual module not required. |
| **`ProfileSetupScreen`**| None | Low (ProfileSetup uses `MaterialTheme.colorScheme.background`) | `[CONFIRMED EXCLUDED]`| ProfileSetup is onboarding/vault setup; contextual module not required. |
| **`PhotosScreen`** | None | Low (Photos uses neutral background) | `[CONFIRMED EXCLUDED]`| Photo viewer remains neutral viewer experience. |
| **`GateScreen`** | None | Low (Gate uses intentional security atmosphere) | `[CONFIRMED EXCLUDED]`| Security gate remains visually independent. |

---

## 9. Global Settings Ownership & Data Model

All global preferences remain strictly owned by `SettingsViewModel`, backed by `SettingsRepository` and `StorageManager` (DataStore `baroness_prefs`):

```text
┌───────────────────────────┬───────────────────────────┬───────────────────────────┐
│ Setting Domain            │ State Holder              │ Disk Persistence Key      │
├───────────────────────────┼───────────────────────────┼───────────────────────────┤
│ Theme Selection           │ SettingsViewModel         │ "selected_theme"          │
│ Font Selection            │ SettingsViewModel         │ "selected_font"           │
│ Wallpaper Selection       │ SettingsViewModel         │ "selected_wallpaper"      │
│ Voice Enabled             │ SettingsViewModel         │ "voice_enabled"           │
│ Voice Provider            │ SettingsViewModel         │ "voice_provider"          │
│ Voice ID                  │ SettingsViewModel         │ "voice_id"                │
│ Voice Speed               │ SettingsViewModel         │ "voice_speed"             │
│ Voice Pitch               │ SettingsViewModel         │ "voice_pitch"             │
│ Persona Voice Opt         │ SettingsViewModel         │ "use_persona_voices"      │
│ Clock Voice Readout       │ SettingsViewModel         │ "clock_voice_announce"    │
│ Alarm Sound               │ SettingsViewModel         │ "alarm_sound_option"      │
│ Timer Chime               │ SettingsViewModel         │ "timer_chime_option"      │
│ Vibration Pattern         │ SettingsViewModel         │ "alarm_vibration_pattern" │
│ User Profile JSON         │ ProfileSetupViewModel     │ "userProfile" + Supabase  │
└───────────────────────────┴───────────────────────────┴───────────────────────────┘
```

---

## 10. Navigation Architecture

Navigation routes for Settings Center and its sub-screens are declared in `MainActivity.kt` `NavHost`:

```text
NavHost(navController = navController, startDestination = startDestination) {
    // Existing Top-Level Routes
    composable("gate") { GateScreen(...) }
    composable("dashboard") { DashboardScreen(...) }
    composable("profile_setup/{personaId}") { ProfileSetupScreen(...) }
    composable("chat_list") { ChatListScreen(...) }
    composable("chat_room/{conversationId}") { ChatRoomScreen(...) }
    composable("Friday") { ChatRoomScreen(conversationId = "friday", ...) }
    composable("Photos") { PhotosScreen(...) }
    composable("Wishlist") { WishlistScreen(...) }

    // Settings Center Navigation Routes
    composable("settings") { SettingsCenterScreen(navController, settingsViewModel) }
    composable("settings/profile") { ProfileSettingsPage(navController) }
    composable("settings/appearance") { AppearanceSettingsPage(navController, settingsViewModel) }
    composable("settings/appearance/theme") { ThemeSettingsPage(navController, settingsViewModel) }
    composable("settings/appearance/font") { FontSettingsPage(navController, settingsViewModel) }
    composable("settings/appearance/wallpaper") { WallpaperSettingsPage(navController, settingsViewModel) }
    composable("settings/sound") { SoundSettingsPage(navController, settingsViewModel) }
    composable("settings/friday") { FridaySettingsPage(navController, settingsViewModel) }
    composable("settings/notifications") { NotificationSettingsPage(navController) }
    composable("settings/privacy") { PrivacySettingsPage(navController) }
    composable("settings/storage") { StorageSettingsPage(navController) }
    composable("settings/about") { AboutSettingsPage(navController) }
}
```

### Global Entry Points:
1. **Dashboard Gear Icon**: Navigates to `"settings"` (Settings Center hub) instead of jumping directly to `"profile_setup"`.
2. **FloatingMenu Item**: Maps Settings item to navigate to `"settings"`.
3. **ChatList Top Bar**: Top bar settings button navigates directly to `"settings"`.

---

## 11. Visual Architecture Rules

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        VISUAL ARCHITECTURE RULES                       │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 1: SETTINGS CENTER HUB ("settings")                              │
│ - Rich visual treatment allowed: DynamicBackground + Haze frosted glass│
│ - Section titles: AppFonts.PlayfairDisplay & AppFonts.Gamaamli         │
│ - Active theme glow accents                                            │
│                                                                        │
│ Layer 2: CATEGORY SUB-SCREENS ("settings/appearance/theme", etc.)      │
│ - Lightweight Material 3 Scaffold + MaterialTheme.colorScheme.bg      │
│ - Standard TopAppBar with navigation back arrow                        │
│ - Translucent alpha cards (Color.White.copy(alpha = 0.05f))            │
│ - NO full-screen DynamicBackground or Haze rendering on sub-screens    │
│                                                                        │
│ Layer 3: CONTEXTUAL SETTINGS MODULES (Side-sliding overlay)            │
│ - Side-sliding panel (~90% width, full height)                         │
│ - Frosted glass treatment matching host screen overlay                 │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Preview / Apply / Revert & State Lifecycle

### Current Repository Implementation:
1. **Visual Preview Domains (Theme, Font, Wallpaper)**: `SettingsViewModel.kt` maintains explicit preview StateFlows (`previewTheme`, `previewFont`, `previewWallpaper`) and commit methods (`applyTheme()`, `applyFont()`, `applyWallpaper()`) alongside revert methods (`revertTheme()`, `revertFont()`, `revertWallpaper()`).
2. **Immediate Persistence Domains (Voice, Clock, Vibration)**: Toggles and sliders synchronously write to DataStore upon user interaction via `setVoiceEnabled()`, `setVoiceSpeed()`, `setAlarmSoundOption()`, etc.

### Reconciled Sub-Screen Preview Rule & Integration Concern:
Sub-screens for Theme, Font, and Wallpaper preserve the established preview-then-commit model. Navigating away without tapping **APPLY** automatically invokes `revertTheme()` / `revertFont()` / `revertWallpaper()` via `DisposableEffect` to ensure uncommitted preview state is cleared:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        settingsViewModel.revertTheme()
        settingsViewModel.revertFont()
        settingsViewModel.revertWallpaper()
    }
}
```

---

## 13. Component Reuse vs Replacement Matrix

| Existing Component | Target Destination | Action | Rationale |
| :--- | :--- | :--- | :--- |
| `ThemeExpandedGrid` & `ThemePreviewChat` | `ThemeSettingsPage.kt` | `REUSE AFTER REFACTOR` | Swatch grid and mock chat bubble preview are architecturally sound and highly polished. |
| `FontExpandedGrid`, `FontWeightList`, `FontPreviewArea` | `FontSettingsPage.kt` | `REUSE AFTER REFACTOR` | 11-family font grid and weight selection components are well-designed and fully functional. |
| `WallpaperCarousel` & `WallpaperPreviewArea` | `WallpaperSettingsPage.kt` | `REUSE AFTER REFACTOR` | 3D horizontal pager carousel will render significantly better on a full-screen background page. |
| `VoiceSettingToggle` & `VoiceSlider` | `SoundSettingsPage.kt` & `FridaySettingsPage.kt` | `REUSE AFTER REFACTOR` | Audio controls for TTS speed, pitch, and voice provider selection are fully working. |
| `ClockOptionChip` & `ClockSoundPlayer` | `SoundSettingsPage.kt` | `REUSE AFTER REFACTOR` | Ringtone, chime, and vibration chip controls are fully integrated with Android audio APIs. |
| `AboutContent()` | `AboutSettingsPage.kt` | `REUSE UNCHANGED` | Brand narrative and credits composable requires no structural changes. |
| `GlassCategoryBox` | `SettingsCenterScreen.kt` | `REUSE FOR HUB CARDS` | Glassmorphic container logic is reused on Layer 1 Settings Center discovery hub. |

---

## 14. Implementation Roadmap

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        IMPLEMENTATION ROADMAP                          │
├────────────────────────────────────────────────────────────────────────┤
│ Phase 1: Navigation Architecture & Settings Center Shell               │
│ - Create com.baroness.app.screens.settings.SettingsCenterScreen        │
│ - Add "settings" routes in MainActivity.kt NavHost                      │
│ - Affected: MainActivity.kt, SettingsCenterScreen.kt                   │
│                                                                        │
│ Phase 2: Profile & Identity Subscreen                                  │
│ - Implement ProfileSettingsPage.kt                                     │
│ - Wire to ProfileManager.kt & UserProfile cache                        │
│ - Affected: ProfileSettingsPage.kt, ProfileManager.kt                  │
│                                                                        │
│ Phase 3: Appearance Subscreens Migration                               │
│ - Implement AppearanceSettingsPage.kt, ThemeSettingsPage.kt,          │
│   FontSettingsPage.kt, WallpaperSettingsPage.kt                       │
│ - Refactor UI from DrawerTheme, DrawerFont, DrawerWallpaper            │
│ - Affected: ThemeSettingsPage.kt, FontSettingsPage.kt, Wallpaper...    │
│                                                                        │
│ Phase 4: Sound & Haptics Subscreen Migration                           │
│ - Implement SoundSettingsPage.kt (TTS Speech, Alarm Tones, Chimes)     │
│ - Refactor UI from DrawerClock.kt & ClockSoundPlayer.kt                │
│ - Affected: SoundSettingsPage.kt, ClockSoundPlayer.kt                  │
│                                                                        │
│ Phase 5: FRIDAY Subscreen Migration                                    │
│ - Implement FridaySettingsPage.kt (Director Notes & Persona Voice Opt) │
│ - Refactor UI from DrawerSoundHaptics.kt & VoiceCenter.kt              │
│ - Affected: FridaySettingsPage.kt, VoiceCenter.kt                      │
│                                                                        │
│ Phase 6: Notifications, Privacy, Storage & About Subscreens            │
│ - Wire StorageSettingsPage.kt to existing BackupManager & VoiceCache   │
│ - Wire PrivacySettingsPage.kt to existing AuthManager & SessionManager │
│ - Wire NotificationSettingsPage.kt to existing NotificationCenter      │
│ - Implement AboutSettingsPage.kt (migrated from DrawerAbout)           │
│ - Affected: StorageSettingsPage.kt, AboutSettingsPage.kt, etc.         │
│                                                                        │
│ Phase 7: Contextual Settings Modules Rollout                           │
│ - Implement ChatRoomContextualSettingsModule (~90% side-sliding panel) │
│ - Connect directly to SettingsViewModel without duplicate state        │
│ - Affected: ChatRoomScreen.kt, ChatRoomContextualSettingsModule.kt     │
│                                                                        │
│ Phase 8: GlobalDrawer Retirement & Top-Bar Entry Points Update         │
│ - Retire GlobalDrawer.kt or convert to lightweight quick-nav drawer    │
│ - Update Dashboard, ChatList, FloatingMenu settings triggers          │
│ - Affected: DashboardScreen.kt, ChatListScreen.kt, FloatingMenu.kt     │
│                                                                        │
│ Phase 9: Verification, Regression Testing & Build Validation           │
│ - Validate persistence, cold-start restoration, preview commit/revert  │
│ - Verify V2.1–V2.5 behavior remains 100% intact                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 15. Protected V2 Decisions & Non-Goals

### Protected V2 Architectural Decisions (MUST NOT BE BROKEN):
1. **V2.1 Settings Sanitization**: `SettingsRepository.kt` key fallbacks (`sunrise` for invalid wallpaper, `lavender` for invalid theme) MUST remain active.
2. **V2.2 Dynamic Theme System**: `BaronessAppTheme` in `Theme.kt` MUST continue generating dynamic Material 3 `ColorScheme` from `SettingsViewModel.activeTheme`.
3. **V2.3 Global Typography Engine**: `MaterialTheme.typography` MUST continue inheriting the active font resolved via `AppFonts.resolve()`. The `masterMetaStyle` timestamp exception in `MessageBubble.kt` MUST be preserved.
4. **V2.4 Dynamic Wallpaper Engine**: `DynamicBackground.kt` MUST remain the sole background renderer for prebundled drawables and gallery photos.
5. **V2.5 Consumer Migrations**:
   - `ChatListScreen`: Root container MUST continue using `MaterialTheme.colorScheme.background` (NOT wallpaper).
   - `WishlistScreen`: MUST continue using `DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = false)` (no dimming!) + primary accent `MaterialTheme.colorScheme.primary`.
   - `ProfileSetupScreen`: Root container ALREADY uses `MaterialTheme.colorScheme.background` (does NOT use `image_45` as a full-screen background) and MUST NOT regain `image_45` as a full-screen background!
   - `PhotosScreen`: MUST remain a neutral photograph viewing surface using `MaterialTheme.colorScheme.background`.
   - `GateScreen`: MUST retain its intentional security gate atmosphere.

### Explicit Non-Goals:
- **DO NOT** modify Kotlin source files during this planning phase.
- **DO NOT** create duplicate DataStore preference files or secondary ViewModels for individual screens.
- **DO NOT** invent speculative settings (AI temperature knobs, memory sliders, model pickers) that do not exist in current code.
- **DO NOT** force wallpaper rendering onto `ChatListScreen`, `ProfileSetupScreen`, or `PhotosScreen`.
- **DO NOT** introduce heavy full-screen Haze/wallpaper rendering onto Layer 2 category sub-screens.
- **DO NOT** add new third-party dependencies to `build.gradle.kts`.

---

## 16. Open Decisions Before Implementation

The following genuinely unresolved implementation questions are explicitly documented for resolution during the implementation phase:

1. **`GlobalDrawer.kt` Final Role**:
   - *Status*: `[OPEN IMPLEMENTATION DECISION]`
   - *Options*: Fully retire `GlobalDrawer.kt` OR repurpose it as a lightweight quick-navigation drawer (with screen shortcuts and master voice toggle).
   - *Resolution Phase*: Phase 8.

2. **Contextual Settings Modules on Secondary Screens**:
   - *Status*: `[OPEN UX DECISION]`
   - *Options*: `ChatRoomScreen` contextual side-sliding settings module is confirmed. `DashboardScreen` and `WishlistScreen` quick wallpaper modules remain open UX decisions.
   - *Resolution Phase*: Phase 7.

3. **Layer 2 Sub-Screen Card Layout Variation**:
   - *Status*: `[OPEN UX DECISION]`
   - *Options*: Grouped alpha cards vs single sectioned list.
   - *Resolution Phase*: Phase 3.

---

SETTINGS CENTER IMPLEMENTATION PLAN: FINALIZED
