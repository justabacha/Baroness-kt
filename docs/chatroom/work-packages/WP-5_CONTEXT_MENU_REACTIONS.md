# WP-5: Context Menu & Reactions

## 1. Objective
Implement the interactive long-press context menu, including background blurring, bubble lifting, emoji tapbacks, and message actions (Copy, Edit, Delete).

## 2. Entry Criteria
- [x] WP-3 ✅ Complete (ViewModel Contract)
- [x] WP-4 ✅ Complete (Core UI Components)
- [x] Docs read:
    - `docs/chatroom/chatroom_ui_ux.md` (Section 2, 5)
- [x] Existing files to inspect:
    - `app/src/main/java/com/baroness/app/components/EmojiPicker.kt` (Reuse)
    - `app/src/main/java/com/baroness/app/components/chat/MessageBubble.kt` (Target for long-press)

## 3. Scope

### 3.1 Tasks
- [x] **Implement ChatContextMenu.kt** — Blur overlay, darkened background, and lifted "cloned" bubble logic.
- [x] **Implement Tapback Bar** — Floating row of common emojis (❤️ 👍 etc.) above the bubble.
- [x] **Implement ReactionRow.kt** — Small row beneath message bubbles to display existing reactions.
- [x] **Implement Action Menu** — Vertical list for Reply (Stub), Copy, Delete, and Edit.
- [x] **Integrate EmojiPicker** — Ready for final wiring in WP-6.

### 3.2 Files to Create
| File | Path | Responsibility |
|------|------|---------------|
| `ChatContextMenu.kt` | `app/src/main/java/com/baroness/app/components/chat/ChatContextMenu.kt` | The full-screen overlay for long-press interactions. |
| `ReactionRow.kt` | `app/src/main/java/com/baroness/app/components/chat/ReactionRow.kt` | Displays emoji reactions below a bubble. |

### 3.3 Files to Modify
| File | Path | Change |
|------|------|--------|
| `MessageBubble.kt` | `app/src/main/java/com/baroness/app/components/chat/MessageBubble.kt` | Add `ReactionRow` as a child and detect long-press to trigger menu. |

### 3.4 Files to Delete
| File | Path | Reason |
|------|------|--------|
| None | | |

## 4. Manual Steps for Owner (Phesty)
| Step | Action | Where | Status |
|------|--------|-------|--------|
| 1 | Verify haptic feedback strength on long-press. | Device | ⏳ Pending |

## 5. Exit Criteria
- [x] Long-press triggers a blurred overlay and lifts the selected bubble.
- [x] Tapping a reaction in the tapback bar updates local state and Room.
- [x] "Delete" action marks the message as `isDeleted` and closes menu.
- [x] "Edit" action populates the main `ChatInput` with message content.
- [x] Menu correctly dismisses on background tap.

## 6. Complexity
Medium (Estimated Effort: 2 agent passes)

## 7. Risks
- 🚩 **Overlay Z-Index**: Ensuring the context menu appears above all other UI elements (including the top bar).
- 🚩 **Animation Smoothness**: The transition from bubble to "lifted bubble" must be seamless.

## 8. Dependencies
- Blocks: WP-6
- Blocked by: WP-4 ✅

## 9. Notes
- Use `HapticFeedbackType.LongPress` for the vibration.
- Friday reactions stay local (Ref: `architecture.md` Section 6).
- Use `rememberTransition` for the scaling and blur animations to ensure high FPS.
