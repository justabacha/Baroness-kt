# WP-5: Context Menu & Reactions — Blueprint Review

- **Status**: ✅ APPROVED

## 1. Executive Summary
The WP-5 blueprint provides a comprehensive plan for implementing the complex interactive layer of the ChatRoom. It correctly integrates with existing components (EmojiPicker) and addresses the high-fidelity UI requirements (blur, bubble lifting) specified in the design documents.

## 2. Section-by-Section Verification

### 2.1 Objective
- [x] Clear and covers all context-related actions.

### 2.2 Entry Criteria
- [x] Correct prerequisite (WP-4 for bubbles).
- [x] Reuse of `EmojiPicker` correctly identified.

### 2.3 Scope & Tasks
- [x] **ChatContextMenu**: Visual specs (blur, dimming) align with `ui_ux.md`.
- [x] **Tapback Bar**: Common emoji row requirement included.
- [x] **ReactionRow**: Asymmetric positioning below bubbles included.
- [x] **Action Menu**: Includes Copy, Delete, and conditional Edit.

### 2.4 Files to Create/Modify
- [x] Paths match `chatroom_file_structure.md`.
- [x] Correct modification of `MessageBubble` to detect triggers.

### 2.5 Manual Steps
- [x] Focuses on haptic feedback, a key UX detail.

### 2.6 Exit Criteria
- [x] Covers both UI state and underlying data updates (Room).

### 2.7 Risks & Dependencies
- [x] **Overlay Z-Index**: Correctly identifies the need to stay above the Top Bar.
- [x] **Friday Logic**: Note 9 respects the local-only reaction constraint for AI.

## 3. Gaps & Contradictions

### Critical Gaps
- None.

### Minor Issues/Observations
1. **Logic Prerequisite**: The blueprint relies on `WP-4` for visuals, but the actions (Delete, React) will require the `ChatRoomViewModel` contract from `WP-3`. While not a hard blocker for UI development, WP-3 should be listed for a fully functional "Exit Criteria" pass.
2. **Reply Stub**: The blueprint omits the "Reply" stub mentioned in `architecture.md` Section 7. It should be included in the menu as a visual placeholder.

## 4. Recommendations for Execution
1. **Bubble Lifting**: Use a `GraphicsLayer` or `ZIndex` modifier to ensure the lifted bubble clone is perfectly aligned with the original before the scale animation begins.
2. **Reaction Logic**: Ensure `ReactionRow` correctly parses the JSON string stored in `MessageEntity.reactions`.

## 5. Conclusion
WP-5 is technically sound and adheres to all UI/UX and architectural constraints. It completes the individual component development phase of the ChatRoom feature.

**READY FOR EXECUTION.**
