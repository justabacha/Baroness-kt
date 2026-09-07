# WP-3: ViewModel Architecture - Execution Report

## 1. File Listing

| Path | Responsibility | Status |
| :--- | :--- | :--- |
| `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModel.kt` | Abstract base ViewModel defining the chat contract. | Created |
| `app/src/main/java/com/baroness/app/viewmodels/HumanChatViewModel.kt` | ViewModel for human-to-human chat with Realtime typing support. | Created |
| `app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt` | ViewModel for AI (Friday) chat with simulated typing support. | Created |
| `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModelFactory.kt` | Factory to instantiate the correct ViewModel based on `conversationId`. | Created |
| `app/src/main/java/com/baroness/app/repository/ChatRepository.kt` | Added stubs for `deleteMessage`, `editMessage`, and `reactToMessage`. | Modified |

## 2. Manual Steps for Owner (Phesty)
1. **Persona Check**: Verify that when you select a persona at the "Gate", the `currentPersonaId` is saved correctly in `StorageManager`. This is used to filter typing indicators and identify the sender.

## 3. Verification Steps
- **Build Status**: Successful execution of `./gradlew :app:assembleDebug`.
- **Factory Logic**: `ChatRoomViewModelFactory` correctly routes "friday" to `FridayChatViewModel`.
- **State Flow**: Both ViewModels use `stateIn` to convert Room `Flow` into a `StateFlow<ChatRoomUiState>`, starting with `Loading`.
- **Realtime Integration**: `HumanChatViewModel` collects typing broadcasts from the repository and filters by the `otherParticipant`'s ID.

## 4. Assumptions Made
- **Other Participant ID**: In `HumanChatViewModel`, the other participant's ID is derived from `conversationId` (e.g., "baroness" -> "baroness_official").
- **Reactions**: Repository stubs for reactions currently treat them as a simple placeholder until the full JSON logic is implemented in WP-5.

## 5. Compilation Status
- **Result**: PASSED
