# ChatRoom Implementation Plan

This document serves as the master blueprint for executing the ChatRoom feature, following the established architecture, database, and UI/UX specifications.

---

## 1. MASTER CHECKLIST

### Database & Models
- [ ] **MessageEntity** — Define Room entity with UUID PK, `editedAt`, and JSON reactions (Reference: `database.md` Section 1 / `file_structure.md` File: `app/src/main/java/com/baroness/app/data/local/database/MessageEntity.kt`)
- [ ] **MessageDao** — Implement queries for Flow lookups, PENDING sync, and CRUD (Reference: `database.md` Section 1 / `file_structure.md` File: `app/src/main/java/com/baroness/app/data/local/dao/MessageDao.kt`)
- [ ] **AppDatabase Update** — Add `MessageEntity`, `MessageDao`, and implement `MIGRATION_3_4` to prevent data loss (Reference: `database.md` Section 1 / `file_structure.md` File: `app/src/main/java/com/baroness/app/data/local/database/AppDatabase.kt`)
- [ ] **Domain Models** — Create `Message.kt`, `Participant.kt`, and `ChatRoomUiState.kt` (Reference: `architecture.md` Section 4 / `file_structure.md`)
- [ ] **Supabase DDL** — Execute SQL to create `chat_sync_pipe` and `backup_log` tables (Reference: `database.md` Section 4)

### Repository & Sync
- [ ] **ChatRepository** — Implement local/remote coordination, Realtime broadcast, and `chat_sync_pipe` logic (Reference: `architecture.md` Section 2 / `file_structure.md` File: `app/src/main/java/com/baroness/app/repository/ChatRepository.kt`)
- [ ] **ChatSyncWorker** — Implement background synchronization for `PENDING` messages with exponential backoff (Reference: `architecture.md` Section 5 / `file_structure.md` File: `app/src/main/java/com/baroness/app/workers/ChatSyncWorker.kt`)
- [ ] **Backup/Restore Logic** — Implement JSON backup to Supabase Storage and restore trigger (Reference: `architecture.md` Section 3 / `database.md` Section 3)

### ViewModels
- [ ] **ChatRoomViewModel (Base)** — Define abstract base class and common contract (Reference: `architecture.md` Section 8 / `file_structure.md`)
- [ ] **HumanChatViewModel** — Implement human-to-human specific logic and typing broadcast (Reference: `architecture.md` Section 8 / `file_structure.md`)
- [ ] **FridayChatViewModel** — Implement AI interaction, `GroqApiService`, and simulated typing (Reference: `architecture.md` Section 6 & 8 / `file_structure.md`)
- [ ] **ChatRoomViewModelFactory** — Implement the factory to instantiate the correct implementation (Reference: `architecture.md` Section 8 / `file_structure.md`)

### UI Components
- [ ] **MessageBubble** — Build unified bubble with asymmetric shapes and theme-based styling (Reference: `ui_ux.md` Section 2 / `file_structure.md`)
- [ ] **MessageList** — Build inverted LazyColumn with infinite scroll and pagination (Reference: `ui_ux.md` Section 1 / `file_structure.md`)
- [ ] **ChatInput** — Build glassmorphic, auto-expanding input field (Reference: `ui_ux.md` Section 3 / `file_structure.md`)
- [ ] **TypingIndicator** — Build animated "is typing" dots (Reference: `ui_ux.md` Section 4 / `file_structure.md`)
- [ ] **ChatContextMenu** — Build long-press blur overlay with tapback and action menu (Reference: `ui_ux.md` Section 5 / `file_structure.md`)
- [ ] **ReactionRow** — Build small row for message reactions (Reference: `ui_ux.md` Section 2 / `file_structure.md`)

### Screen Integration
- [ ] **ChatRoomScreen** — Main container assembly with top bar and dynamic background (Reference: `ui_ux.md` Section 1 / `file_structure.md`)
- [ ] **Theme/Font/Wallpaper Wiring** — Connect `SettingsViewModel` states to ChatRoom UI (Reference: `ui_ux.md` Section 6)

### Navigation & Wiring
- [ ] **Navigation Routes** — Route `chat_room/{conversationId}` to `ChatRoomScreen` in `MainActivity` (Reference: `architecture.md` Section 1 / `file_structure.md`)
- [ ] **ChatList Entry update** — Connect `ChatListScreen` items to the new route (Reference: `file_structure.md`)

### Testing & Polish
- [ ] **Groq AI Flow** — Verify send/reply and simulated delay for Friday via `GroqApiService` (Reference: `architecture.md` Section 6)
- [ ] **Obsolete File Removal** — Delete `MessagesScreen.kt` and `PlaceholderScreen` references (Reference: `file_structure.md` Section 4)
- [ ] **Optimistic Update Verification** — Ensure instant local delivery and background sync (Reference: `architecture.md` Section 2)

---

## 2. WORK PACKAGES

### WP-1: Database Foundation
- **Entry Criteria**: Read `database.md`, `architecture.md` Section 4.
- **Tasks**: Create `MessageEntity`, `MessageDao`, Update `AppDatabase` with safe migration, execute Supabase DDL.
- **Exit Criteria**: Room version 4 compiled with `MIGRATION_3_4`; Supabase tables exist.
- **Complexity**: Small
- **Files**: `MessageEntity.kt`, `MessageDao.kt`, `AppDatabase.kt`.

### WP-2: Repository & Sync Layer
- **Entry Criteria**: WP-1 Complete. Read `architecture.md` Section 2, 3, 5.
- **Tasks**: Create `ChatRepository`, `ChatSyncWorker`, Implement backup logic.
- **Exit Criteria**: Unit testable sync and storage logic.
- **Complexity**: Medium
- **Files**: `ChatRepository.kt`, `ChatSyncWorker.kt`.

### WP-3: ViewModel Architecture
- **Entry Criteria**: WP-2 Complete. Read `architecture.md` Section 8.
- **Tasks**: Create `ChatRoomViewModel`, `HumanChatViewModel`, `FridayChatViewModel`, `ChatRoomViewModelFactory`, `ChatRoomUiState.kt`.
- **Exit Criteria**: Factory creates correct ViewModel; state flows established.
- **Complexity**: Medium
- **Files**: `ChatRoomViewModel.kt`, `HumanChatViewModel.kt`, `FridayChatViewModel.kt`, `ChatRoomViewModelFactory.kt`, `ChatRoomUiState.kt`.

### WP-4: Core UI Components
- **Entry Criteria**: Read `ui_ux.md` Sections 1-4.
- **Tasks**: Create `MessageBubble.kt`, `MessageList.kt`, `ChatInput.kt`, `TypingIndicator.kt`.
- **Exit Criteria**: Visual components match design specs in isolation.
- **Complexity**: Medium
- **Files**: `components/chat/` folder.

### WP-5: Context Menu & Reactions
- **Entry Criteria**: WP-4 Complete. Read `ui_ux.md` Section 5.
- **Tasks**: Create `ChatContextMenu.kt`, `ReactionRow.kt`, integrate `EmojiPicker.kt`.
- **Exit Criteria**: Long-press interaction works with blur and tapback.
- **Complexity**: Medium
- **Files**: `ChatContextMenu.kt`, `ReactionRow.kt`.

### WP-6: Screen Integration & Navigation
- **Entry Criteria**: WP-3, WP-4, WP-5 Complete.
- **Tasks**: Create `ChatRoomScreen.kt`, Update `MainActivity.kt` routes.
- **Exit Criteria**: ChatRoom is navigable and displays real/simulated data.
- **Complexity**: Medium
- **Files**: `ChatRoomScreen.kt`, `MainActivity.kt`.

### WP-7: Friday AI & Realtime Polish
- **Entry Criteria**: WP-6 Complete. Read `architecture.md` Section 6.
- **Tasks**: `GroqApiService` setup, Groq API integration, simulated typing, human typing broadcast.
- **Exit Criteria**: End-to-end Friday chat functional; human typing works.
- **Complexity**: Large
- **Files**: `FridayChatViewModel.kt`, `ChatRepository.kt`, `GroqApiService.kt`, `GroqModels.kt`.

### WP-8: Polish & Cleanup
- **Entry Criteria**: All WPs Complete.
- **Tasks**: Remove `MessagesScreen.kt`, edge case fixes, final review.
- **Exit Criteria**: Clean codebase; no placeholder screens.
- **Complexity**: Small
- **Files**: `MessagesScreen.kt` (Deleted).

---

## 3. DEPENDENCIES BETWEEN WPs

- **WP-1** is the hard blocker for **WP-2**.
- **WP-2** is the hard blocker for **WP-3**.
- **WP-4** can be developed in parallel with **WP-3** but relies on the Models from **WP-1**.
- **WP-5** relies on **WP-4**.
- **WP-6** is the integration point for **WP-3**, **WP-4**, and **WP-5**.
- **WP-7** and **WP-8** are final stage polish.

---

## 4. RISK FLAGS

- 🚩 **Room Migration**: Ensure `MIGRATION_3_4` is correctly implemented to avoid wiping production data.
- 🚩 **Groq API Key**: Currently stored in `BuildConfig`. Ensure it's not committed to public repositories.
- 🚩 **Supabase Storage**: Requires manual creation of the `chat_backups` bucket before backup logic will work.
- 🚩 **Realtime Collisions**: Ensure chat channels use a unique naming scheme to avoid interference with Wishlist subscriptions.

---

## 5. EXISTING CODE TO LEVERAGE

- **`SettingsViewModel`**: For observing `activeTheme`, `activeFont`, and `activeWallpaper`.
- **`PhestyText.kt`**: For rendering message content with custom emoji support.
- **`WishlistInput.kt`**: As a visual and functional reference for the glassmorphic `ChatInput`.
- **`SyncWorker`**: As a template for the `ChatSyncWorker` retry and connectivity logic.
- **`EmojiPicker.kt`**: Reused for full emoji selection in the context menu.
- **`ChatTypography.kt`**: For consistent font family and weight resolution.
