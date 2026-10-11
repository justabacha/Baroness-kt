# Avatar Picker & Upload Flow — Audit Before Introducing Crop

## 1. Executive Summary

An audit of the avatar picking and uploading flow reveals **2 distinct entry points**: [ProfileSetupScreen.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/screens/ProfileSetupScreen.kt) and [ProfileSettingsPage.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/screens/settings/ProfileSettingsPage.kt). Both utilize `ActivityResultContracts.GetContent()` to invoke the system photo picker. The picked `Uri` is passed to `ProfileSetupViewModel.setSelectedImage(uri)`, which sets `avatarUri` for immediate local Coil preview. When the user saves, `ProfileManager.saveSetup()` reads the new image stream from the picked `Uri`, uploads it to Supabase Storage at `<persona>/avatar_<timestamp>.jpg`, updates the `profiles` table, and caches the result locally. To introduce uCrop, the recommended integration point is **Option A (Before saving/uploading)**: intercepting the picked URI from `GetContent()`, launching the uCrop activity/fragment to produce a cropped URI, and feeding that cropped URI into the existing `setSelectedImage()` and `saveSetup()` pipeline.

---

## 2. Entry Point Inventory (Section 1)

| Location | Screen / Component | Intent Action / Contract | Callback Receiver |
| :--- | :--- | :--- | :--- |
| `screens/ProfileSetupScreen.kt:61` | Profile Setup Screen | `ActivityResultContracts.GetContent()` ("image/*") | `viewModel.setSelectedImage(uri)` |
| `screens/settings/ProfileSettingsPage.kt:83` | Profile Settings Page | `ActivityResultContracts.GetContent()` ("image/*") | `viewModel.setSelectedImage(uri)` |

---

## 3. URI Lifecycle per Entry Point (Section 2)

- **Permission**: Relies entirely on the system photo picker (`GetContent`), requiring zero runtime storage permissions on modern Android versions.
- **Local Preview**: When `uri` is received from the picker launcher, `ProfileSetupViewModel.setSelectedImage(uri)` updates `newImageUri.value = uri` and `avatarUri.value = uri`. Coil (`rememberAsyncImagePainter`) renders the preview directly from the `Uri`.
- **Preparation for Upload**: When `onSave()` is invoked, `ProfileManager.saveSetup()` opens an input stream from the `Uri` (`context.contentResolver.openInputStream(uri)`) to read raw bytes (`readBytes()`), then builds a multipart request body (`MultipartBody.Builder`) for Supabase Storage.

---

## 4. Upload Pipeline (Section 3)

- **Uploader**: `ProfileManager.saveSetup()` (`ProfileManager.kt`, lines 60-97).
- **Storage Bucket & Path**: Bucket `"avatars"`, object path `<persona>/avatar_<timestamp>.jpg` (e.g. `phesty_official/avatar_1790546062184.jpg`).
- **Old Avatar Cleanup**: `ProfileManager` extracts the old avatar object path if it belongs to Supabase Storage and issues a DELETE request before uploading the new file.
- **Profile Upsert**: Following successful upload, `ProfileManager.upsertProfile()` updates the `profiles` table with the new `avatar_url` and `display_name`.
- **Error Handling**: Failures during upload throw an Exception caught in `ProfileSetupViewModel`, surfacing `errorMessage.value`.

---

## 5. Display-After-Update Flow (Section 4)

- **State Management**: `ProfileSetupViewModel` holds `avatarUri` and `existingAvatarUrl`.
- **Source of Truth**: Upon save completion, the local file/URI preview remains visible, and the updated `UserProfile` JSON is cached in DataStore under `"userProfile"`. On subsequent app launches, `AvatarRepository` caches the remote URL locally to `filesDir/user_avatars`.

---

## 6. `AvatarRepository` Role (Section 5)

- **API**: `getCachedAvatar(avatarUrl)`, `clearCache()`.
- **Function**: Handles offline local file caching of remote avatar URLs in `context.filesDir/user_avatars` to support offline rendering. It does not handle picking or uploading; those are managed by `ProfileSetupViewModel` and `ProfileManager`.

---

## 7. Existing Transformations (Section 6)

- **Compression / Resizing**: Currently, raw bytes from `contentResolver.openInputStream(uri)` are uploaded directly without client-side downscaling, compression, or EXIF rotation handling. Introducing uCrop will naturally address cropping and formatting.

---

## 8. Permission Model (Section 7)

- Uses Android's system photo picker (`ActivityResultContracts.GetContent()`), which runs out-of-process and requires **no storage permissions** (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`), avoiding permission prompt friction.

---

## 9. Recommended Crop Insertion Point (Section 8)

- **Recommendation**: **Option A / B (Intercepting between Pick and Save)**.
- **Workflow**:
  1. User taps "Change Profile Picture".
  2. `GetContent()` returns raw image `Uri`.
  3. Instead of immediately setting `viewModel.setSelectedImage(uri)`, launch uCrop with the source URI and a destination URI in `cacheDir`.
  4. On uCrop success, receive the cropped `Uri` and pass it to `viewModel.setSelectedImage(croppedUri)`.
  5. The existing upload (`ProfileManager.saveSetup()`) and display pipeline process the cropped image seamlessly without modification.

---

## 10. uCrop Compatibility Notes (Section 9)

- **Library**: `com.github.yalantis:ucrop`.
- **MinSdk / TargetSdk**: Fully compatible with `minSdk = 24` and `targetSdk = 36`.
- **Manifest**: Requires declaring `com.yalantis.ucrop.UCropActivity` inside `AndroidManifest.xml`.
- **Compose Integration**: uCrop is built around traditional Android Activities/Intents. In Compose, it is launched via an Activity Result Contract or an Intent launcher.

---

## 11. Operator Questions

1. Should uCrop be configured with a fixed 1:1 aspect ratio lock (square avatar crop) or allow free-form cropping? (Standard practice for profile avatars is square 1:1).
