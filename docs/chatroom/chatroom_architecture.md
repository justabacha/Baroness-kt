# ChatRoom Architecture Document

## Overview
This document formalizes the architectural decisions for the Baroness ChatRoom feature. It covers both human-to-human and AI-to-human (Friday) messaging, emphasizing an offline-first approach, robust synchronization, and a modular UI structure aligned with existing project conventions.

---

## 1. Routing & Navigation
- **Unified Screen**: A single `ChatRoomScreen` will handle all conversations.
- **Route**: `chat_room/{conversationId}`
- **Conversation IDs**:
    - `friday`: AI Companion (Groq API integration).
    - `baroness`: Human (Official Baroness persona).
    - `phesty`: Human (Official Phesty persona).
- **Navigation Logic**: The `ChatRoomScreen` receives the `conversationId` and uses `ChatRoomViewModelFactory` to instantiate the appropriate ViewModel (Human vs. AI).

## 2. Data Persistence (Offline-First)
- **Source of Truth**: Room (SQLite) database.
- **UI Layer**: Reads directly from Room using `Flow<List<MessageEntity>>`.
- **Supabase**: Sync pipe and remote backup store.
- **Optimistic Updates**: 
    - Messages assigned a temporary UUID locally.
    - Instantly written to Room with status `PENDING`.
    - UI updates immediately via Flow.
    - Background sync via `ChatSyncWorker` handles the remote push.

## 3. Remote Backup & Restore
- **Storage**: Supabase Storage bucket.
- **Path**: `backups/{persona}_official_backup.json`.
- **Strategy**: Overwrite-only, triggered on backgrounding (>50 new messages), user request, or weekly fallback.
- **JSON Structure**:
    - **Include**: `id`, `conversationId`, `senderId`, `content`, `timestamp`, `serverTimestamp`, `status`, `reactions`.
    - **Exclude**: Messages where `isDeleted = true`.
    - **Format**: A flat JSON array of message objects.
- **Restore**: Dialog triggered on new login or empty local Room.

## 4. Models & Schema

### Domain Model: `Message.kt`
Same fields as `MessageEntity` but without Room annotations.
- `isOwn`: Computed in Composable based on current user ID.
- `formattedTime`: Computed in Composable.

### Room Entity: `MessageEntity.kt`
- **Primary Key**: UUID string (`id`).
- **Fields**:
    - `id`: UUID (String)
    - `conversationId`: String
    - `senderId`: String
    - `content`: String
    - `timestamp`: Long (Local creation)
    - `serverTimestamp`: Long? (Synced time)
    - `status`: String (`PENDING`, `SENT`, `DELIVERED`, `READ`, `FAILED`)
    - `isDeleted`: Boolean
    - `reactions`: String (JSON map of emoji -> List of userIds)

### Participant Model
`data class Participant(id: String, displayName: String, avatarUrl: String?, isOnline: Boolean?)`
- **Friday**: Hardcoded instance (id="friday", displayName="FRIDAY", avatarUrl="https://img.icons8.com/fluency/48/artificial-intelligence.png", isOnline=null).
- **Human**: Fetched from `profiles` table or derived from `conversationId`.

---

## 5. Synchronization & Retries
- **Mechanism**: `ChatSyncWorker` (Distinct from Wishlist `SyncWorker`).
- **Retry Logic**: Exponential backoff (5s → 10s → 30s → 2min → 5min → 15min → 30min → hourly).
- **Failure**: Moves to `FAILED` only after 24 hours of unsuccessful attempts.

## 6. Friday (AI Companion)
- **Integration**: Groq API.
- **API Key**: Stored as a `BuildConfig` field for v1.
- **Flow**: User sends -> Show "Friday is typing..." indicator -> Friday's response generated -> Saved to Room.
- **Reactions**: Pure UI decoration. No backend sync, no persistence. Reactions show locally but are NOT saved to Room or Supabase.
- **Receipts**: Single checkmark (`✓`) for Friday responses.

## 7. UI Components & UX
- **Inverted List**: `LazyColumn` with `reverseLayout = true`.
- **Message Bubble**: A single `MessageBubble.kt` component with conditional styling for `isOwn`, `isFriday`, and theme variants.
- **Typing Indicators**: Realtime broadcast for humans; simulated delay for Friday.
- **Context Menu**: Long-press triggers a blur overlay with a bubble clone.
    - **Actions**: React (Emoji bar), Reply (stub), Copy, Delete, Edit (own only).
- **Edit Logic**: Editing a message sets status back to `PENDING`, updates the `timestamp`, and triggers a re-sync.
- **Attachments**: "Coming soon" placeholder modal via paperclip button.

---

## 8. ViewModel Architecture

### Base Contract: `ChatRoomViewModel` (Abstract Class/Interface)
Defines the state and interactions required by `ChatRoomScreen`.

**StateFlows:**
- `messages: StateFlow<List<Message>>`
- `uiState: StateFlow<ChatUiState>` (Loading, Content, Error)
- `isTyping: StateFlow<Boolean>`
- `otherParticipant: StateFlow<Participant?>`

**Functions:**
- `onSendMessage(text: String)`
- `onMessageLongPress(Message)`
- `onDeleteMessage(Message)`
- `onEditMessage(Message, String)`
- `onReactToMessage(Message, String)`

### Implementations:
- **`HumanChatViewModel.kt`**: Handles Supabase Realtime typing, message sync, and profile fetching.
- **`FridayChatViewModel.kt`**: Handles Groq API interaction and simulated typing state.

### Factory:
- **`ChatRoomViewModelFactory.kt`**: The single source of truth for AI vs. Human logic. Creates the correct implementation based on `conversationId`.

---

## 9. File Structure
Aligned with existing project conventions.

### Screens
- `app/src/main/java/com/baroness/app/screens/ChatRoomScreen.kt`

### Components
- `app/src/main/java/com/baroness/app/components/chat/`
    - `MessageList.kt`: The inverted list container.
    - `MessageBubble.kt`: Unified bubble component.
    - `ChatInput.kt`: Glassmorphic input field (local `inputText` state).
    - `TypingIndicator.kt`: Animated dots.
    - `ChatContextMenu.kt`: Overlay for long-press actions.
    - `ReactionRow.kt`: Small row for reactions under bubbles.

### ViewModels
- `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModel.kt`
- `app/src/main/java/com/baroness/app/viewmodels/HumanChatViewModel.kt`
- `app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt`
- `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModelFactory.kt`

### Data & Models
- `app/src/main/java/com/baroness/app/models/Message.kt`
- `app/src/main/java/com/baroness/app/models/Participant.kt`
- `app/src/main/java/com/baroness/app/data/local/database/MessageEntity.kt`
- `app/src/main/java/com/baroness/app/data/local/dao/MessageDao.kt`
- `app/src/main/java/com/baroness/app/repository/ChatRepository.kt`
- `app/src/main/java/com/baroness/app/workers/ChatSyncWorker.kt`

---

## 10. Existing Code Reuse
- **Typography**: `ChatTypography.kt` and `rememberChatTypography()`.
- **Fonts**: `PhestyText.kt`.
- **Themes**: `SettingsViewModel.kt` for `activeTheme` and `activeWallpaper`.
- **UI Elements**: `EmojiPicker.kt`, `TopWarningBanner.kt`, `WishlistInput.kt` (visual reference).

---

## 11. Conflicts & Recommendations

### Room Migration
- **Status**: Adding `MessageEntity` requires a schema update. 
- **Recommendation**: Bumping the version with `fallbackToDestructiveMigration()` will **WIPE ALL DATA**, including existing Wishlist items. For v1, this is acceptable for rapid development, but we should switch to proper migrations as soon as the schema stabilizes.

### Realtime Coordination
- Use a dedicated Realtime channel for Chat to avoid collision with Wishlist sync logic.
