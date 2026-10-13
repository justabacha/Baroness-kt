# Splash Screen — Custom Circle Icon & Branding Implementation Record

## Context

The cold-start pre-warm phase established the `androidx.core:core-splashscreen` pipeline and splash hold gate in `MainActivity.kt`. Previously, the splash screen used `@drawable/icon`, which rendered with the OS's default adaptive-icon mask (producing a non-circular shape on certain devices) and lacked a branding image strip.

This implementation generates custom placeholder drawable assets (`splash_icon.png` and `splash_branding.png`) and wires them into `Theme.Baroness.Splash` with API 31+ platform compatibility.

---

## 1. Placeholder Asset Generation

Placeholder assets were generated using **Method 1 (Python with PIL/Pillow)**:

- `app/src/main/res/drawable/splash_icon.png`: 512×512 canvas, RGBA transparent background, centered solid white circle with radius 180px (~70% canvas width).
- `app/src/main/res/drawable/splash_branding.png`: 512×128 canvas, RGBA transparent background, centered solid white rounded rectangle with 12px corner radius.

---

## 2. Splash Theme Update & Platform Limitation Comment

Updated `Theme.Baroness.Splash` in `app/src/main/res/values/themes.xml`:

**Before:**
```xml
    <style name="Theme.Baroness.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#0B0B0E</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/icon</item>
        <item name="postSplashScreenTheme">@style/Theme.Baroness</item>
    </style>
```

**After:**
```xml
    <style name="Theme.Baroness.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#0B0B0E</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>
        <!-- Branding image only renders on Android 12 (API 31)+. On older versions
             the icon displays but this branding strip does not appear. -->
        <item name="android:windowSplashScreenBrandingImage" tools:targetApi="31">@drawable/splash_branding</item>
        <item name="postSplashScreenTheme">@style/Theme.Baroness</item>
    </style>
```

---

## 3. Scope & Preservation Confirmation

- **Launcher & Adaptive Icons**: Confirmed that `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` were NOT touched or altered.
- **Kotlin Source & MainActivity**: No Kotlin files or pre-warm logic were touched.
- **AndroidManifest.xml**: Remained unchanged.

---

## 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 5. Manual Verification Checklist

- [ ] Force-stop the app. Tap the launcher icon.
- [ ] Splash appears with the solid white circular placeholder icon centered on dark background (`#0B0B0E`).
- [ ] On Android 12+ (API 31+), a white rounded rectangle branding strip appears at the bottom of the splash screen.
- [ ] Splash transitions cleanly to Dashboard once pre-warm completes.
- [ ] Launcher icon on the Android home screen remains unchanged.
- [ ] On Android 11 or lower (API < 31), the centered icon displays cleanly without the branding strip (expected OS platform behavior).

## Grey Background and Branding Attribute Cleanup

### 1. Theme Change

**Before:**
```xml
    <style name="Theme.Baroness.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#0B0B0E</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>
        <!-- Branding image only renders on Android 12 (API 31)+. On older versions
             the icon displays but this branding strip does not appear. -->
        <item name="android:windowSplashScreenBrandingImage" tools:targetApi="31">@drawable/splash_branding</item>
        <item name="postSplashScreenTheme">@style/Theme.Baroness</item>
    </style>
```

**After:**
```xml
    <style name="Theme.Baroness.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#1E1E1E</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>
        <!-- Branding image only renders on Android 12 (API 31)+. On older versions
             the icon displays but this branding strip does not appear. -->
        <item name="android:windowSplashScreenBrandingImage" tools:targetApi="31">@drawable/splash_branding</item>
        <item name="postSplashScreenTheme">@style/Theme.Baroness</item>
    </style>
```

### 2. Branding Attribute Analysis & Retention

- **Background Color**: Updated `windowSplashScreenBackground` from `#0B0B0E` (pitch black) to `#1E1E1E` (soft dark grey).
- **Attribute Prefix & `tools:targetApi="31"` Retention**:
  - Testing non-prefixed `windowSplashScreenBrandingImage` caused an AAPT2 resource linking failure (`error: style attribute 'attr/windowSplashScreenBrandingImage' not found`).
  - Reason: The `androidx.core:core-splashscreen` library backports `windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon`, and `postSplashScreenTheme`, but does **not** declare an `attr/windowSplashScreenBrandingImage` custom attribute in its `attrs.xml`.
  - Branding image remains a native Android 12 framework feature (`android:windowSplashScreenBrandingImage`). On `minSdk 24`, the `android:` namespace prefix and `tools:targetApi="31"` annotation are required so AAPT2 can compile the theme while safely ignoring the attribute on Android 11 and lower.

### 3. Modified Files Confirmation

Only the following file was modified in this update:
- `app/src/main/res/values/themes.xml`

No Kotlin source files, drawables, manifests, or other themes were touched.

### 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

### 5. Manual Verification Checklist

- [ ] Force-stop the app. Tap the launcher icon.
- [ ] Splash background is a soft dark grey (`#1E1E1E`), noticeably lighter than pitch black (`#0B0B0E`).
- [ ] Solid white circular placeholder icon renders centered as before.
- [ ] On Android 12+ (API 31+), the white rounded rectangle branding strip appears at the bottom of the splash screen.
- [ ] Splash transitions smoothly to Dashboard once pre-warming completes.

