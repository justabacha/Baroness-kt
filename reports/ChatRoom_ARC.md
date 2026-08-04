# ChatRoom Architecture Analysis

This report documents the current state of chat-related logic in the Baroness-kt project.

## 1. NAVIGATION (MainActivity.kt)

- **Chat Room Route:** `"chat_room/{conversationId}"`
  - Current implementation: Uses `PlaceholderScreen`.
  - Arguments: `conversationId` (NavType.StringType).
- **Friday Route:** `"Friday"`
  - Current implementation: Uses `PlaceholderScreen` with title "Friday (AI Companion)".
  - Status: Currently stubbed/dead code as the main entry point from `ChatListScreen` uses the unified `chat_room/{conversationId}` route.
- **Messages Route:** `"Messages"`
  - Current implementation: `MessagesScreen(navController)`.
  - Status: This appears to be a standalone mock screen (possibly ported or a UI prototype) with hardcoded mock conversations ("Sophia Miller", etc.) and its own internal `Conversation` data class. It is NOT currently linked to the actual `ChatListViewModel` or Supabase.

## 2. CHAT LIST DATA MODEL

- **Model Class:** `com.baroness.app.models.Conversation`
- **Fields:**
  - `id: String`
  - `displayName: String`
  - `avatarUrl: String?`
  - `lastMessage: String`
  - `timestamp: String`
  - `unreadCount: Int`
  - `isOnline: Boolean`
  - `personaType: PersonaType` (Enum: `HUMAN`, `AI`)
- **Friday Representation:**
  - `id`: `"friday"`
  - `personaType`: `PersonaType.AI`
  - Handled in `ChatListViewModel.updateConversationList` as a hardcoded entry.
- **Tapping Behavior:**
  - On tapping a `ChatEntry` in `ChatListScreen`, the `conversationId` is derived:
    - If `personaType == PersonaType.AI` -> `"friday"`
    - Else if `id == "baroness_official"` -> `"baroness"`
    - Else -> `"phesty"`
  - Navigates using: `navController.navigate("chat_room/$conversationId")`.

## 3. SUPABASE SCHEMA (schema.sql)

- **`messages` table:** Human-to-human chat.
  - Columns: `id` (int), `sender_id` (text), `receiver_id` (text), `message` (text), `read_at`, `created_at`, `updated_at`, `attachment_url`, `attachment_type`.
- **`friday_messages` table:** AI companion chat.
  - Columns: `id` (bigint), `owner_id` (text), `sender` (text), `message` (text), `message_type`, `created_at`.
  - **Kotlin Status:** No repository or data class exists for this table in the Kotlin codebase yet.
- **`profiles` table:**
  - Columns: `id` (text), `display_name`, `persona`, `updated_at`, `avatar_url`, `fcm_token`.
  - **Friday Status:** Friday is NOT a row in the `profiles` table. It is currently entirely client-side/logic-side.
- **`reactions` table:** Exists for message reactions.
  - Columns: `id`, `message_id`, `user_id`, `reaction`, `created_at`.
- **`typing_status` table:** Real-time typing indicators.
  - Columns: `user_id`, `chat_partner_id`, `is_typing`, `updated_at`.

## 4. VIEWMODEL PATTERN

- **State Management:** Uses `StateFlow` and `MutableStateFlow` (e.g., in `SettingsViewModel`, `ChatListViewModel`).
- **Patterns:** 
  - Repository pattern is used (e.g., `SettingsRepository`, `WishlistRepository`).
  - ViewModels often inject context in their constructors via Factories.
  - `SettingsViewModel` exposes theme/font states as `StateFlow<String>` using `stateIn`.
- **BaseViewModel:** No shared `BaseViewModel` detected.

## 5. EXISTING COMPONENTS

Files in `components/` available for reuse:
- **Emoji Logic:** `Emoji.kt`, `EmojiPicker.kt`.
- **Text:** `PhestyText.kt` (custom font-aware text).
- **Overlays:** `GlobalDrawer.kt`, `TopWarningBanner.kt`, `PhotoViewerOverlay.kt`.
- **Layout/Effects:** `EdgeGlowEffect.kt`, `FloatingMenu.kt`.
- **Specific Screens:** `ChatEntry.kt`, `ConversationItem.kt`, `MessagesTopBar.kt`.
- **Wishlist (Potentially useful patterns):** `WishlistInput.kt` (frosted glass input field).

## 6. THEME INTEGRATION

- **Colors:** Defined in `com.baroness.app.ui.theme.Colors.kt` as an `object Colors`.
- **Observation:** Screens observe the theme by injecting `SettingsViewModel` and collecting the `activeFont`, `activeTheme`, or `activeWallpaper` states.
- **Typography:** `ChatTypography.kt` provides a `rememberChatTypography()` composable that resolves custom fonts based on the `SettingsViewModel` state.
- **Gradients:** No standalone `GradientBackground` component exists. Gradients are currently implemented locally within individual components (e.g., `FloatingMenu.kt`, `PhotosScreen.kt`) using `Brush.verticalGradient` or `Brush.linearGradient`.
