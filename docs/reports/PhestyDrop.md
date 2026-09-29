# 💧 PhestyDrop Architecture & Implementation Plan
**Private In-App Software Delivery System for Baroness**

---

## 1. Concept & Vision: "PhestyDrop"

**PhestyDrop** is the custom, intimate in-app update and distribution system designed specifically for **Baroness**. 

Instead of traditional Google Play Store updates or manually bundling and sending `.apk` files over WhatsApp, **PhestyDrop** acts as an AirDrop-style over-the-air delivery service. Whenever Phesty builds a new version of Baroness, the app detects the drop, downloads it in the background with a clean Material 3 download progress sheet, and prompts Baroness to install the latest build seamlessly.

---

## 2. Hybrid Cloud Architecture (Supabase Metadata + GitHub Releases Storage)

Because the compiled Baroness `.apk` binary approaches 50MB+, hosting binary assets on Supabase Storage risks hitting free-tier file size limits. 

We utilize a **Hybrid Cloud Architecture**:
1. **Supabase Database (`phestydrop_releases` table)**: Serves as the fast, real-time metadata catalog (`version_code`, `version_name`, `changelog`, `is_mandatory`, and `download_url`).
2. **GitHub Releases**: Hosts the `.apk` asset binaries (up to 2GB per asset file) with Fastly CDN distribution.

```
                  ┌──────────────────────────────────────────────┐
                  │          Supabase DB (`phestydrop_releases`)  │
                  │   • version_code: 2                          │
                  │   • version_name: "1.0.1"                    │
                  │   • download_url: GitHub Release Asset URL   │
                  └──────────────────────┬───────────────────────┘
                                         │ (Fast Metadata Query)
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       Baroness App                                          │
│                                                                                             │
│  ┌────────────────────────┐      ┌─────────────────────────┐     ┌───────────────────────┐  │
│  │   PhestyDropManager    │ ───► │    PhestyDropSheet      │ ───►│   Package Installer   │  │
│  │ • Version Comparison   │      │ • Material 3 Sheet      │     │ • FileProvider URI    │  │
│  │ • OkHttp Stream Flow   │      │ • M3 Progress Indicator │     │ • ACTION_VIEW Intent  │  │
│  │ • Permission Check     │      │ • Custom Changelog      │     │                       │  │
│  └────────────────────────┘      └─────────────────────────┘     └───────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────────────────────────────────┘
                            │ (Direct APK Download Stream)
                            ▼
                  ┌──────────────────────────────────────────────┐
                  │        GitHub Release Direct Asset URL       │
                  │  • baroness-v1.0.1.apk (CDN Distributed)     │
                  └──────────────────────────────────────────────┘
```

---

## 3. Key Components

### A. Supabase Database Record (`phestydrop_releases`)
```sql
INSERT INTO phestydrop_releases (version_code, version_name, download_url, changelog, is_mandatory)
VALUES (
  2,
  '1.0.1',
  'https://github.com/justabacha/Baroness-kt/releases/download/v1.0.1/baroness-v1.0.1.apk',
  '• Introduces PhestyDrop intimate update engine\n• Optimized Friday voice synthesis response time\n• Visual polish across Settings Center and Glass theme',
  false
);
```

### B. Security & Android Requirements
1. **Manifest Permission**:
   ```xml
   <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
   ```
2. **FileProvider Configuration**:
   Uses the existing `<provider>` and `file_paths.xml` mapping `context.cacheDir` for secure `content://` URI grants.
3. **Unknown Sources Permission Check**:
   Before launching install, check `context.packageManager.canRequestPackageInstalls()`. If `false`, prompt user to toggle "Allow from this source" in Android System Settings.
4. **Keystore Consistency Rule**:
   All builds must be signed with the identical release keystore to prevent `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

---

## 4. Proposed File Structure

```
com.baroness.app.phestydrop/
├── data/
│   ├── PhestyDropInfo.kt         // Data model for manifest payload
│   └── DownloadState.kt          // Idle, Downloading(progress), Ready, Error
├── manager/
│   └── PhestyDropManager.kt      // Supabase query, OkHttp streaming, package installer trigger
└── ui/
    └── PhestyDropSheet.kt        // Material 3 Compose BottomSheet UI
```

---

## 5. Phased Implementation Roadmap

### Phase 1: Manifest & Configuration (COMPLETED)
- Added `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>` to `AndroidManifest.xml`.
- Configured FileProvider paths in `file_paths.xml`.

### Phase 2: Database Migration & Cloud Setup (COMPLETED)
- Applied `025_phestydrop_manifest.sql` creating `phestydrop_releases` table and RLS policies.
- Adopted GitHub Releases strategy for APK asset hosting.

### Phase 3: Core Manager Implementation
- Implement `PhestyDropManager` to query Supabase `phestydrop_releases` table for the latest release where `version_code > BuildConfig.VERSION_CODE`.
- Implement streaming download using OkHttp with byte progress emission into `File(context.cacheDir, "phesty_drop.apk")`.
- Implement `installPackage(context, apkFile)` with `FileProvider` and `canRequestPackageInstalls()` gate.

### Phase 4: UI & Experience (Material 3 Approach)
- Build `PhestyDropSheet.kt` using standard Material 3 (`ModalBottomSheet`, `LinearProgressIndicator`, `Button`, `Text`).
- Keep it lightweight, highly responsive, and clean without heavy Haze blur overlays.
- Add auto-check hook in `MainActivity.kt` on startup.
- Add manual "Check for PhestyDrop" action card in `SettingsCenterScreen` and `DrawerAbout`.

---

*Document generated for Baroness Universe • PhestyDrop Engine*
