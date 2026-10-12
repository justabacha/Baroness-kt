# About Baroness Page — Capability Audit Report

## 1. Executive Summary

An audit of the Baroness codebase was conducted to evaluate existing content, OTA update infrastructure, brand assets, and reachability for the "About Baroness" settings page (`id = "about"`). The application already possesses a fully written and styled About component (`DrawerAbout.kt`) containing app narrative text, credits, version metadata, and an integrated **PhestyDrop Engine** card (`PhestyDropManager.kt`, `PhestyDropSheet.kt`). The update engine is fully functional, querying the Supabase `phestydrop_releases` table, streaming APK downloads, and launching the Android package installer. Creating the `"settings/about"` page requires mounting this existing content into a Settings Center sub-page and registering the route in `MainActivity.kt`.

---

## 2. DrawerAbout Content Inventory

Defined in `app/src/main/java/com/baroness/app/components/DrawerAbout.kt`:

### 2.1 Composable Structure
- `DrawerAbout`: Wraps `AboutContent()` inside a `GlassCategoryBox(title = "ABOUT")` expandable panel in the side drawer.
- `AboutContent()`: The core content composable.

### 2.2 Text Blocks & Narrative
1. **Title**: `"About Baroness"` (`AppFonts.PlayfairDisplay`, Black 20.sp, White).
2. **Tagline**: `"A private universe for two."` (`AppFonts.PlayfairDisplay`, Normal 14.sp, White 80% alpha).
3. **PhestyDrop Header**: `"PHESTYDROP ENGINE"` (`AppFonts.Gamaamli`, Bold 14.sp) + `"Version ${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}"` (11.sp, White 60% alpha).
4. **Auto-Download Label**: `"Auto-Download Drops"` + `"Auto-fetch low & mandatory drops on start"`.
5. **Main Narrative Story** (`AppFonts.PlayfairDisplay`, Bold 13.sp, White 70% alpha, line height 20.sp):
   - *"No noise. No algorithms. No endless feeds. Just a clean, beautiful room where conversations, wishes, and memories live without distraction..."*
   - *"Two personas. One connection. Phesty and Baroness — each with their own voice, their own presence. This isn't a social network. It's a private dialogue, shared emotions, and a space to dream together..."*
   - *"And then there's Friday — a companion with her own personality, memory, and presence. Built to feel like a friend who actually remembers..."*
   - *"The interface isn't just functional — it's designed to feel calm, warm, and intentional. Every blur, every colour, every font choice is meant to create a vibe, not just a UI..."*
   - *"Baroness is a title, not a person. Strength, elegance, quiet power. The name was chosen because it reflects the energy of the person it was built for — someone who doesn't need to shout to be heard..."*
   - *"Naah, am just kidding. Baroness is her actual name lol :)"*
6. **Author Credits**: `"Built by Phestone with love. For just Baroness and Phesty."` (`AppFonts.PlayfairDisplay`, Normal 12.sp, White 50% alpha).

### 2.3 Interactive Elements
1. **Auto-Download Switch**: Binds to `autoUpdateEnabled` state, persisted via `StorageManager` under Boolean key `"phestydrop_auto_update"`.
2. **"Check for PhestyDrop" Button**:
   - Executes `PhestyDropManager.checkForDrop()` on a background coroutine.
   - While checking: displays `CircularProgressIndicator` and text `"Checking for Drops..."`.
   - If a new drop is returned (`remote versionCode > local VERSION_CODE`): sets `activeDropInfo = drop`, launching `PhestyDropSheet` modal bottom sheet.
   - If no new drop is returned: displays Toast `"Baroness is fully up to date!"`.

---

## 3. PhestyDrop Integration State

- **Current Version Source**: Configured in `app/build.gradle.kts` as `versionCode = 2` and `versionName = "1.0.1"`, exposed at runtime via `BuildConfig.VERSION_CODE` and `BuildConfig.VERSION_NAME`.
- **Auto-Update Preference**: Persisted in DataStore / SharedPreferences via `StorageManager.saveBoolean("phestydrop_auto_update", checked)` (defaults to `true`).
- **Update Check Query**:
  - `PhestyDropManager.checkForDrop()` queries Supabase table `phestydrop_releases` via PostgREST:
    ```kotlin
    SupabaseConfig.supabase.from("phestydrop_releases")
        .select {
            order("version_code", order = Order.DESCENDING)
            limit(1)
        }
        .decodeList<PhestyDropInfo>()
    ```
  - Compares `latestDrop.versionCode > BuildConfig.VERSION_CODE`.
- **Last-Checked Timestamp**: **NOT persisted**. The app checks on launch (if `phestydrop_auto_update` is true) or on manual button tap, but does not store a timestamp.
- **Changelog / Release Notes**: Fetched from `phestydrop_releases.changelog` field and rendered inside `PhestyDropSheet` under `"WHAT'S NEW"`.
- **`PhestyDropSheet`**: Material 3 `ModalBottomSheet` in `phestydrop/ui/PhestyDropSheet.kt`. Handles streaming OkHttp APK downloads into `context.cacheDir/phesty_drop_v<version>.apk` with progress emissions, launching package installation via `Intent.ACTION_VIEW` and `FileProvider` (`${packageName}.fileprovider`).

---

## 4. Version Sources

1. **`BuildConfig.VERSION_NAME` ("1.0.1") & `BuildConfig.VERSION_CODE` (2)**: Official version authority configured in `app/build.gradle.kts`.
2. **`SettingsCenterScreen.kt` Branding Text**: `ProfileHeaderCard` displays static subtitle `"Private Universe Settings • Version 2.5"` (marketing version string).
3. **Existing About Displays**: `DrawerAbout.kt` displays `"Version 1.0.1 • Build 2"` via `BuildConfig`.

---

## 5. Brand Assets

1. **App Icons**:
   - `R.drawable.icon` (`icon.png`)
   - `R.mipmap.ic_launcher` / `ic_launcher_round`
2. **Typography & Fonts**:
   - `AppFonts.Gamaamli` (`font/gamaamli_regular.ttf`) — Title headers
   - `AppFonts.PlayfairDisplay` (`font/playfairdisplay_*.ttf`) — Serif body text
   - `AppFonts.Lifesavers`, `AppFonts.KaushanScript`, `AppFonts.DancingScript`, `AppFonts.RobotoMono`
3. **Brand Illustrations**: `R.drawable.phesty_point.jpg`, `R.drawable.image_39.jpg`, `R.drawable.light_hours.jpg`, `R.drawable.mountain_view.jpg`.

---

## 6. Existing Routes and Reachability

- **`MainActivity.kt` `NavHost`**: Route `"settings/about"` is **NOT currently registered**.
- **Global Drawer Reachability**: `DrawerAbout` **IS reachable** from any main screen by opening the `GlobalDrawer` and expanding the `"ABOUT"` category box.
- **Settings Center Reachability**: Tapping the "About Baroness" category card (`id = "about"`) in `SettingsCenterScreen.kt` currently performs a **no-op** (unhandled in `when (category.id)` switch).

---

## 7. Cross-Feature References

- `SettingsCenterScreen.kt`: Card title `"About Baroness"`, subtitle `"App Story, Concept Credits, Version Info"`.
- `MainActivity.kt`: On app start, `MainActivity` executes `PhestyDropManager.checkForDrop()` if `phestydrop_auto_update` is enabled, popping `PhestyDropSheet` automatically if an update is found.

---

## 8. Existing PhestyDrop UI Analysis

`PhestyDropSheet` (`phestydrop/ui/PhestyDropSheet.kt`) is an interactive update delivery modal:
- **Displays**: Target version, installed vs target version comparison card (`v1.0.1 ➜ v1.0.2`), changelog text, auto-download toggle, and download progress bar.
- **Actions**: "Later", "Accept Drop", "Install PhestyDrop Now", "Retry Download".
- **Preservation Strategy**: `PhestyDropSheet` serves as the interactive installation dialog. The "About Baroness" settings page should host the `AboutContent()` overview card and manual check trigger, launching `PhestyDropSheet` whenever an update is detected.

---

## 9. Capability Matrix

| Feature / Element | Implementation State | Codebase Reference |
| :--- | :--- | :--- |
| **App Name & Tagline** | **SUPPORTED** | `"About Baroness"` / `"A private universe for two."` (`DrawerAbout.kt`) |
| **App Icon Display** | **SUPPORTED** | `R.drawable.icon` / `R.mipmap.ic_launcher` |
| **Version Name & Code** | **SUPPORTED** | `BuildConfig.VERSION_NAME` ("1.0.1") & `BuildConfig.VERSION_CODE` (2) |
| **App Narrative Story** | **SUPPORTED** | Complete text block in `DrawerAbout.kt` |
| **Concept & Author Credits** | **SUPPORTED** | `"Built by Phestone with love. For just Baroness and Phesty."` (`DrawerAbout.kt`) |
| **Auto-Download Toggle** | **SUPPORTED** | `phestydrop_auto_update` key in `StorageManager` |
| **Manual Update Check** | **SUPPORTED** | `PhestyDropManager.checkForDrop()` via Supabase PostgREST |
| **Release Notes / Changelog** | **SUPPORTED** | `phestydrop_releases.changelog` field on `PhestyDropInfo` |
| **Update Installation Sheet** | **SUPPORTED** | `PhestyDropSheet.kt` (Modal BottomSheet with OkHttp streaming & FileProvider) |
| **Last-Checked Timestamp** | **NOT SUPPORTED** | No timestamp stored; check runs on demand or startup |
| **Open Source Licenses** | **NOT SUPPORTED** | No open-source licenses or legal notices module in codebase |
| **External Release Link** | **NOT SUPPORTED** | No external URL or GitHub link configured |

---

## 10. Open Questions

None. All required assets, content blocks, and OTA update handlers are confirmed available.
