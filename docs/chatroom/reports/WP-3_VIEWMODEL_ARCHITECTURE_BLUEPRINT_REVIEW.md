# WP-3: ViewModel Architecture — Blueprint Review

- **Status**: ⚠️ NEEDS_REVISION

## 1. Executive Summary
The WP-3 blueprint establishes the core ViewModel structure required for the ChatRoom feature. While it aligns well with the file structure and general architectural goals, it lacks specific details on the complete functional contract and the interaction between the UI and typing broadcasts. Revisions are needed to ensure the ViewModel is fully ready to support the upcoming UI components (WP-4 & WP-5).

## 2. Section-by-Section Verification

### 2.1 Objective
- [x] Clear and concise.

### 2.2 Entry Criteria
- [x] Correct prerequisites (WP-1 & WP-2).
- [x] Relevant documents listed.

### 2.3 Scope & Tasks
- [ ] **Contract Completeness**: Section 3.1 Task 1 mentions `StateFlows` but doesn't explicitly list all functions defined in `architecture.md` Section 8 (e.g., `onDeleteMessage`, `onEditMessage`, `onReactToMessage`). These are critical for the Context Menu (WP-5).
- [ ] **Typing Logic**: `HumanChatViewModel` needs an explicit task to handle "User is Typing" signals from the UI to trigger the `ChatRepository.broadcastTyping` method.
- [ ] **Participant Logic**: The blueprint mentions fetching profiles but should clarify that `otherParticipant` is a `StateFlow` populated via the repository.

### 2.4 Files to Create/Modify
- [x] File paths match `chatroom_file_structure.md`.
- [x] Responsibilities are appropriate.

### 2.5 Manual Steps
- [x] Realistic and relevant.

### 2.6 Exit Criteria
- [x] Measurable, but should include the implementation of the full message action contract.

### 2.7 Risks & Dependencies
- [x] Realistic risks identified.
- [x] Correct dependency mapping.

## 3. Gaps & Contradictions

### Critical Gaps
1. **Missing Interaction Methods**: The blueprint ignores the message action methods (`onDeleteMessage`, `onEditMessage`, `onReactToMessage`) in the base contract task. These are essential for the `ChatContextMenu` integration in WP-5.
2. **User Typing Hook**: There is no task for providing a hook in `HumanChatViewModel` for the UI to report that the current user is typing.

### Minor Contradictions
- None found.

## 4. Recommendations for Fixes
1. **Expand Task 3.1.1**: List all contract functions: `onSendMessage`, `onMessageLongPress`, `onDeleteMessage`, `onEditMessage`, `onReactToMessage`.
2. **Add Typing Hook Task**: In `HumanChatViewModel`, add a task: "Provide a method (e.g., `setUserTyping(Boolean)`) for the UI to signal typing activity, which delegates to `ChatRepository.broadcastTyping`."
3. **Clarify UI State Mapping**: Add a note that `ChatRoomUiState` should reactively reflect the state of the messages `Flow` (e.g., transitioning to `Success` when data arrives).
4. **Friday Participant**: Reinforce that `otherParticipant` for `FridayChatViewModel` must be the hardcoded AI participant defined in `architecture.md`.

## 5. Conclusion
WP-3 is well-structured but requires these surgical additions to be fully "executable" by an agent without missing core functionality required by subsequent WPs.

**DO NOT PROCEED to execution until these points are integrated into the blueprint.**
