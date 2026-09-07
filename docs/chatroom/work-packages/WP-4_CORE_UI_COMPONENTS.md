# WP-4: Core UI Components

## 1. Objective
Build the foundational UI components for the ChatRoom, ensuring they follow the glassmorphic design system and asymmetric bubble styling.

## 2. Entry Criteria
- [x] WP-1 ✅ Complete (Models established)
- [x] Docs read:
    - `docs/chatroom/chatroom_ui_ux.md` (Sections 1-4, 9)
    - `docs/chatroom/chatroom_file_structure.md` (Components section)
- [x] Existing files to inspect:
    - `app/src/main/java/com/baroness/app/components/PhestyText.kt` (Emoji support)
    - `app/src/main/java/com/baroness/app/ui/theme/ChatTypography.kt` (Font resolution)

## 3. Scope

### 3.1 Tasks
- [x] **Implement MessageBubble.kt** — Asymmetric shapes, theme-based colors for "Own" vs "Other", and timestamp/receipt indicators.
- [x] **Implement MessageList.kt** — Inverted `LazyColumn` container with `reverseLayout = true` and scroll-to-bottom behavior.
- [x] **Implement ChatInput.kt** — Glassmorphic floating input with multiline support and context-aware "Send" button.
- [x] **Implement TypingIndicator.kt** — Animated jumping dots with "is typing..." text.
- [x] **Theme Integration** — Wire components to consume `SettingsViewModel` for fonts, accent colors, and glows.

### 3.2 Files to Create
| File | Path | Responsibility |
|------|------|---------------|
| `MessageBubble.kt` | `app/src/main/java/com/baroness/app/components/chat/MessageBubble.kt` | Renders individual message bubbles with asymmetric tails. |
| `MessageList.kt` | `app/src/main/java/com/baroness/app/components/chat/MessageList.kt` | Scrollable container for messages. |
| `ChatInput.kt` | `app/src/main/java/com/baroness/app/components/chat/ChatInput.kt` | Glassmorphic text input component. |
| `TypingIndicator.kt` | `app/src/main/java/com/baroness/app/components/chat/TypingIndicator.kt` | Animated typing feedback. |

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
| 1 | Review bubble tails on physical device to ensure they don't overlap screen edges. | Device | ⏳ Pending |

## 5. Exit Criteria
- [x] `MessageBubble` supports both "Own" and "Other" participants with distinct styling.
- [x] `MessageList` correctly displays messages in reverse order.
- [x] `ChatInput` handles multiline text and keyboard resizing (`imePadding`).
- [x] `TypingIndicator` animation is smooth.
- [x] Components correctly use `PhestyText` for emoji support.

## 6. Complexity
Medium (Estimated Effort: 2 agent passes)

## 7. Risks
- 🚩 **IME Padding**: Ensuring the input field stays above the keyboard without obscuring the message list.
- 🚩 **Performance**: Efficiently rendering many `MessageBubble` instances (ensure minimal recomposition).

## 8. Dependencies
- Blocks: WP-5, WP-6
- Blocked by: WP-1 (Models) ✅

## 9. Notes
- Use `androidx.compose.foundation.shape.GenericShape` for custom asymmetric bubble tails if standard RoundedCornerShape is insufficient.
- Max bubble width should be 75% of screen width.
- Use `Haze` or standard `blur` modifiers for glassmorphic effects as per existing project patterns.
