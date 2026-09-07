# WP-3: ViewModel Architecture

## 1. Objective
Establish the ViewModel layer to manage chat state, differentiate between AI (Friday) and Human interactions, and provide a unified interface for the UI layer.

## 2. Entry Criteria
- [x] WP-1 ✅ Complete (Database Foundation)
- [x] WP-2 ✅ Complete (Repository & Sync Layer)
- [x] Docs read:
    - `docs/chatroom/chatroom_architecture.md`
    - `docs/chatroom/chatroom_database.md`
    - `docs/chatroom/chatroom_file_structure.md`
    - `docs/chatroom/chatroom_implementation.md`
    - `docs/chatroom/reports/WP-2_REPOSITORY_SYNC_LAYER_EXECUTION_REPORT.md`
- [ ] Existing files to inspect:
    - `ChatRepository.kt`
    - `ChatRoomUiState.kt` (Created in WP-1)
    - `Participant.kt` (Created in WP-1)
    - `SettingsViewModel.kt` (Reference for theme/persona observing)

## 3. Scope

### 3.1 Tasks
- [x] **Create ChatRoomViewModel.kt** — Define the abstract base contract (or interface) for chat ViewModels. 
- [x] **Implement HumanChatViewModel.kt** — Concrete implementation for human-to-human chat.
- [x] **Implement FridayChatViewModel.kt** — Concrete implementation for AI (Friday) chat.
- [x] **Create ChatRoomViewModelFactory.kt** — Implement the logic to instantiate the appropriate ViewModel based on the `conversationId`.
- [x] **Define ViewState Logic** — Ensure `ChatRoomUiState` transitions correctly between `Loading`, `Success`, and `Error`.

### 3.2 Files to Create
| File | Path | Responsibility |
|------|------|---------------|
| `ChatRoomViewModel.kt` | `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModel.kt` | Abstract base class defining the shared chat contract. |
| `HumanChatViewModel.kt` | `app/src/main/java/com/baroness/app/viewmodels/HumanChatViewModel.kt` | ViewModel for Human-to-Human messaging. |
| `FridayChatViewModel.kt` | `app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt` | ViewModel for AI (Friday) messaging. |
| `ChatRoomViewModelFactory.kt` | `app/src/main/java/com/baroness/app/viewmodels/ChatRoomViewModelFactory.kt` | Factory to resolve and create specific ViewModel instances. |

### 3.3 Files to Modify
| File | Path | Change |
|------|------|--------|
| None | | |

### 3.4 Files to Delete
| File | Path | Reason |
|------|------|--------|
| None | | |

## 4. Manual Steps for Owner (Phesty)
| Step | Action | Where | Status |
|------|--------|-------|--------|
| 1 | Verify that `currentPersonaId` is correctly set in local storage for participant filtering. | App Data / Settings | ⏳ Pending |

## 5. Exit Criteria
- [x] `ChatRoomViewModel` provides a unified state for the UI to consume.
- [x] `ChatRoomViewModelFactory` correctly identifies "friday" and returns `FridayChatViewModel`.
- [x] `HumanChatViewModel` observes the repository's `Flow<List<Message>>`.
- [x] ViewModels correctly map `MessageEntity` to domain `Message` models.
- [x] Project compiles successfully.

## 6. Complexity
Medium

## 7. Estimated Effort
2 agent passes

## 8. Risks
- 🚩 **ViewModel Leak**: Ensure the `ChatRoomViewModelFactory` doesn't hold hard references that prevent ViewModel cleanup.
- 🚩 **State Desync**: Ensuring the `uiState` correctly reflects the underlying Room data flow vs. initial loading.
- 🚩 **Domain Mapping**: Potential performance overhead if mapping large lists of messages in the ViewModel (use `map` on Flow).

## 9. Dependencies
- Blocks: WP-6 (Screen Integration)
- Blocked by: WP-2 (Repository & Sync Layer) ✅

## 10. Notes
- Reference `SettingsViewModel` for how to handle global state like theme or persona.
- The `otherParticipant` for `FridayChatViewModel` should be a hardcoded `Participant` object as defined in `architecture.md` Section 4.
- Use `viewModelScope` for all repository observations.
