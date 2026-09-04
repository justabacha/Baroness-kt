# ChatRoom File Structure Document

This document outlines the exact file tree and responsibilities for the ChatRoom feature, following existing project conventions.

---

## 1. File Tree (New Files)

### Screens
- `app/src/main/java/com/baroness/app/screens/ChatRoomScreen.kt`

### Components (Chat Specific)
- `app/src/main/java/com/baroness/app/components/chat/MessageList.kt`
- `app/src/main/java/com/baroness/app/components/chat/MessageBubble.kt`
- `app/src/main/java/com/baroness/app/components/chat/ChatInput.kt`
- `app/src/main/java/com/baroness/app/components/chat/TypingIndicator.kt`
- `app/src/main/java/com/baroness/app/components/chat/ChatContextMenu.kt`
- `app/src/main/java/com/baroness/app/components/chat/ReactionRow.kt`

### ViewModels
- `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModel.kt`
- `app/src/main/java/com/baroness/app/viewmodels/HumanChatViewModel.kt`
- `app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt`
- `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModelFactory.kt`

### Data & Remote (AI)
- `app/src/main/java/com/baroness/app/data/remote/groq/GroqApiService.kt`
- `app/src/main/java/com/baroness/app/data/remote/groq/GroqModels.kt`

### Data & Models (Local)
- `app/src/main/java/com/baroness/app/models/Message.kt`
- `app/src/main/java/com/baroness/app/models/Participant.kt`
- `app/src/main/java/com/baroness/app/models/ChatRoomUiState.kt`
- `app/src/main/java/com/baroness/app/data/local/database/MessageEntity.kt`
- `app/src/main/java/com/baroness/app/data/local/dao/MessageDao.kt`
- `app/src/main/java/com/baroness/app/repository/ChatRepository.kt`

### Workers
- `app/src/main/java/com/baroness/app/workers/ChatSyncWorker.kt`

---

## 2. File Responsibilities

### Screens
- **`ChatRoomScreen.kt`**: The main container for the chat UI, observing ViewModel states and handling navigation events.

### Components
- **`MessageList.kt`**: Manages the inverted `LazyColumn` for message display and pagination triggers.
- **`MessageBubble.kt`**: Renders an individual message with conditional styling for ownership and participant type.
- **`ChatInput.kt`**: A glassmorphic text input field with multiline support and action buttons (Send, Attach).
- **`TypingIndicator.kt`**: Displays animated "typing..." dots when the other participant is active.
- **`ChatContextMenu.kt`**: Provides the long-press overlay for reactions, editing, and message deletion.
- **`ReactionRow.kt`**: A small horizontal list of emoji reactions displayed beneath a message bubble.

### ViewModels
- **`ChatRoomViewModel.kt`**: Abstract base class defining the shared contract for messages, participants, and UI state.
- **`HumanChatViewModel.kt`**: Implementation for human-to-human chat using Supabase Realtime for synchronization.
- **`FridayChatViewModel.kt`**: Implementation for AI chat using the Groq API for responses.
- **`ChatRoomViewModelFactory.kt`**: Instantiates the correct ViewModel implementation based on the `conversationId`.

### Data & Models
- **`GroqApiService.kt`**: Handles network requests to the Groq Chat Completions API using Ktor.
- **`GroqModels.kt`**: Serializable data classes for Groq request and response payloads.
- **`Message.kt`**: The domain-level data class used within the UI layer.
- **`Participant.kt`**: Data model representing a chat user (Human or Friday).
- **`ChatRoomUiState.kt`**: Sealed class defining the various UI states (Loading, Success, Error) for the ChatRoom screen.
- **`MessageEntity.kt`**: The Room database entity for persistent message storage.
- **`MessageDao.kt`**: Data Access Object defining Room queries for messages and sync status.
- **`ChatRepository.kt`**: Orchestrates data flow between the local Room database and Supabase transport.

### Workers
- **`ChatSyncWorker.kt`**: Handles background synchronization of `PENDING` messages with exponential backoff.

---

## 3. Existing Files to Reuse/Modify

- **`app/src/main/java/com/baroness/app/data/local/database/AppDatabase.kt`**: Update to include `MessageEntity` and implement `MIGRATION_3_4` to preserve production data.
- **`app/src/main/java/com/baroness/app/MainActivity.kt`**: Update navigation graph to route `chat_room/{conversationId}` to `ChatRoomScreen`.
- **`app/src/main/java/com/baroness/app/components/PhestyText.kt`**: Used for custom font rendering within message bubbles.
- **`app/src/main/java/com/baroness/app/components/EmojiPicker.kt`**: Reused within the `ChatContextMenu` for full emoji selection.
- **`app/src/main/java/com/baroness/app/ui/theme/ChatTypography.kt`**: Used for resolving font families and weights across the chat UI.
- **`app/src/main/java/com/baroness/app/viewmodels/SettingsViewModel.kt`**: Observed for active theme, font, and wallpaper updates.

---

## 4. Deleted/Obsolete Files

- **`app/src/main/java/com/baroness/app/screens/MessagesScreen.kt`**: Obsolete. Replaced by `ChatListScreen` (Inbox) and the new `ChatRoomScreen`.
- **`PlaceholderScreen` usages**: Any temporary placeholders for the `chat_room` route in `MainActivity.kt` will be removed.

---

## 5. Dependencies

- **WorkManager**: `androidx.work:work-runtime-ktx` (Already present).
- **Serialization**: `org.jetbrains.kotlinx:kotlinx-serialization-json` (Already present).
- **Image Loading**: `io.coil-kt:coil-compose` (Already present).
- **Haze (Glassmorphism)**: `dev.chrisbanes.haze:haze` (Already present).
- **Networking (Groq API)**: `io.ktor:ktor-client-okhttp` (Already present).

---

## 6. Naming Conventions

- **PascalCase**: All files and classes use PascalCase (e.g., `ChatRoomScreen`, `MessageEntity`).
- **No Abbreviations**: Names are descriptive and avoid ambiguous abbreviations (e.g., `HumanChatViewModel` instead of `HChatVM`).
- **Suffixes**: Clear suffixes for architectural roles (`Screen`, `Component`, `ViewModel`, `Repository`, `Worker`, `Entity`, `Dao`).
