# Avatar Crop Integration — uCrop

## Dependency and theme

Added the latest stable uCrop release, 2.2.11:

```kotlin
implementation("com.github.yalantis:ucrop:2.2.11")
```

The project did not previously declare AppCompat directly, and `Theme.Baroness` extends the platform `android:Theme.Material.Light.NoActionBar`, not an AppCompat, Material Components, or Material 3 theme. uCrop 2.2.11's published metadata does not include its runtime dependencies, so AppCompat is declared explicitly because `UCropActivity` requires an AppCompat-compatible theme and activity runtime:

```kotlin
implementation("androidx.appcompat:appcompat:1.6.1")
```

Existing dependencies already provide the uCrop runtime's ExifInterface, Transition, and OkHttp libraries. Added JitPack to `dependencyResolutionManagement.repositories` in `settings.gradle.kts`, because the `com.github.yalantis:ucrop` artifact is published there.

The uCrop AAR does not supply a style named `UCrop.Theme`. Added `app/src/main/res/values/ucrop_themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="UCrop.Theme" parent="Theme.AppCompat.Light.NoActionBar" />
</resources>
```

Registered the crop activity inside `<application>` in `app/src/main/AndroidManifest.xml`:

```xml
<activity
    android:name="com.yalantis.ucrop.UCropActivity"
    android:screenOrientation="portrait"
    android:theme="@style/UCrop.Theme" />
```

## Crop helper

Created `app/src/main/java/com/baroness/app/utils/AvatarCrop.kt`. In uCrop 2.2.11, compression format and quality are configured through `UCrop.Options`:

```kotlin
package com.baroness.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.yalantis.ucrop.UCrop
import java.io.File

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

## Picker entry points

Both screens now launch uCrop after the existing `GetContent` picker returns a URI. A successful result URI is passed to the existing `setSelectedImage`; cancellation leaves the current selection untouched, and uCrop errors are logged without falling back to the original image.

The previous callback in each screen was:

```kotlin
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let { viewModel.setSelectedImage(it) }
}
```

The replacement in `ProfileSetupScreen.kt` is:

```kotlin
val cropLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) { result ->
    val data = result.data
    if (result.resultCode == Activity.RESULT_OK) {
        if (data != null) {
            UCrop.getOutput(data)?.let { viewModel.setSelectedImage(it) }
        }
    } else if (data != null) {
        UCrop.getError(data)?.let { error ->
            Log.e("ProfileSetupScreen", "Avatar crop failed", error)
        }
    }
}

val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let { cropLauncher.launch(AvatarCrop.createIntent(context, it)) }
}
```

`ProfileSettingsPage.kt` uses the same code, with the error log tag `"ProfileSettingsPage"`. Neither `setSelectedImage` nor the downstream save/upload path was changed.

## Build and test

- `:app:testDebugUnitTest`: **passed**.
- `:app:assembleDebug`: **passed**.
- Resolved runtime graph contains uCrop 2.2.11 and AppCompat 1.6.1; existing graph entries provide ExifInterface 1.3.7, Transition 1.6.0, and OkHttp 4.12.0.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`.
- No device, ADB, or screenshot verification was run.

## Manual verification checklist for the operator

- Open Profile Settings → tap change avatar → system picker opens → pick an image → uCrop screen appears → adjust crop → save → preview shows cropped square → upload succeeds → avatar displays square.
- Repeat on Profile Setup.
- Cancel at the picker stage → no change.
- Cancel at the uCrop stage → no change.
- Pick a rotated image → crop screen displays it correctly rotated.
