# Avatar Loading Audit — Screens, Loaders, Sources

## 1. Executive Summary
An inventory of avatar rendering across the Baroness application reveals **12 distinct avatar render sites** spanning screens, chat bubbles, notification banners, and wishlist headers. Currently, avatar loading utilizes a mix of Coil's default singleton (`AsyncImage`, `rememberAsyncImagePainter`), custom `ImageLoader` builders (e.g., in `WishlistHeader.kt`), and local file-caching utilities (`AvatarRepository` caching downloaded avatars to `filesDir/user_avatars`). The URL source varies across screens, relying either on DataStore profile JSON (`userProfile`), repository profile flows (`WishlistRepository.profiles`), or chat participant DTOs. To ensure avatars survive Android "Clear cache" and render consistently offline across all screens, avatar loading and profile storage must be consolidated into a unified application-level Coil configuration and shared profile data store.

---

## 2. Render Inventory Table (Section 1)

| Location | Screen / Component | Coil API used | Loader source | URL source |
| :--- | :--- | :--- | :--- | :--- |
| `components/ChatEntry.kt:57` | Chat List Item | `AsyncImage` | Default singleton | Chat conversation participant DTO (`avatarUrl`) |
| `components/chat/MessageBubble.kt:503` | Chat Room Message | `AsyncImage` | Default singleton | Participant DTO (`participant?.avatarUrl`) |
| `components/notification/InAppNotification.kt:62` | Foreground Banner | `AsyncImage` | Default singleton | `NotificationData.avatarUrl` |
| `components/wishlist/WishlistHeader.kt:60,80` | Wishlist Header (P & B) | `AsyncImage` | Custom local `ImageLoader` | `WishlistRepository.profiles` flow (`avatarUrl`) |
| `components/wishlist/WishlistInput.kt:111,162` | Wishlist Input / Footer | `AsyncImage` | Default singleton | Hardcoded fallback or profile avatar |
| `screens/ChatRoomScreen.kt:710,854` | Chat Room Header / Bubble | `AsyncImage` | Default singleton | Participant DTO (`participant?.avatarUrl`) |
| `screens/DashboardScreen.kt:181` | Dashboard Home | `rememberAsyncImagePainter` | Default singleton | DataStore `userProfile` (`userProfile?.avatar`) |
| `screens/ProfileSetupScreen.kt:126` | Profile Setup | `rememberAsyncImagePainter` | Default singleton | Local `avatarUri` / `existingAvatarUrl` |
| `screens/settings/ProfileSettingsPage.kt:168` | Profile Settings | `rememberAsyncImagePainter` | Default singleton | Local `avatarUri` / `existingAvatarUrl` |
| `screens/settings/SettingsCenterScreen.kt:201` | Settings Center | `rememberAsyncImagePainter` | Default singleton | DataStore `userProfile` (`userProfile?.avatar`) |

*Note: All 10 locations use Coil. No screen renders avatars via Glide, Picasso, or manual HTTP streams.*

---

## 3. URL Source per Screen (Section 2)

- **Wishlist Screen & Header**: Reads from `WishlistRepository.profiles` (fetched from Supabase `profiles` table).
- **Dashboard & Settings Center**: Reads from DataStore key `"userProfile"` (serialized JSON containing `avatar` field).
- **Profile Setup & Profile Settings**: Reads from local `avatarUri` (picker URI or cached local file path via `AvatarRepository`).
- **Chat List & Chat Room**: Reads from `Conversation` / `Participant` DTOs populated via Supabase REST/Realtime queries.
- **In-App Notification**: Reads from push notification data payload (`NotificationData`).

---

## 4. DataStore Profile Persistence Coverage (Section 3)

- **Writer**: `ProfileSetupViewModel` and `GateViewModel` write the serialized `UserProfile` JSON to DataStore under the key `"userProfile"` using `StorageManager`. `WishlistRepository` also persists profile records fetched from Supabase.
- **Serialization Format**: Kotlinx Serialization JSON string matching `UserProfile` data class (`displayName`, `avatar`, `persona`, `id`).
- **Reader Coverage**: Dashboard, Settings Center, and Profile Setup read `"userProfile"`. Wishlist reads from `WishlistRepository.profiles`. Chat screens read from their respective participant/conversation state flows.

---

## 5. Coil Configuration Inventory (Section 4)

- **Default Singleton**: Most components rely on Coil's default singleton (`Coil.imageLoader(context)`).
- **Custom Override**: `WishlistHeader.kt` constructs an explicit local `ImageLoader` using `ImageLoader.Builder(appContext)` with custom disk cache configuration.
- **Custom Application Class**: There is **no custom Application class** implementing `ImageLoaderFactory` today.

---

## 6. Manifest Check (Section 5)

- **AndroidManifest.xml**: Does **not** currently declare a custom Application class (`android:name`). It relies on Android's default base `Application` class. To establish a global `ImageLoaderFactory`, `MainApplication` must be declared in `AndroidManifest.xml`.

---

## 7. Other Avatar Storage (Section 6)

- **`AvatarRepository.kt`**: Implements custom file-based avatar caching. It downloads remote avatar URLs to `context.filesDir/user_avatars/avatar_<timestamp>.jpg` and caches the mapping in DataStore (`avatar_local_path`, `avatar_cached_url`).
- **Interaction with Coil**: Used by Dashboard, Profile Setup, and Settings Center to resolve local file paths for offline rendering, bypassing Coil's default cache.

---

## 8. Proposed Consolidation Plan (Section 7)

1. **Manifest & Application Factory**: Declare `MainApplication` in `AndroidManifest.xml` and have it implement `ImageLoaderFactory` to provide a global singleton `ImageLoader` configured with a persistent disk cache in `filesDir`.
2. **Unified Profile DataStore Source**: Ensure all screens query a single centralized repository/store for profile metadata rather than fragmented DataStore keys and DTOs.
3. **AvatarRepository Integration**: Retain `AvatarRepository` for pre-downloading/offline file persistence while letting Coil leverage the global file-backed disk cache.

---

## 9. Operator Questions / Unknowns

1. Should avatar images be prefetched into `filesDir/user_avatars` upon profile load across all screens, or should Coil's disk cache handle all offline rendering?
