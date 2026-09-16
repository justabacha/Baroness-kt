# Baroness Context Menu & Reactions: Work-Package 5 State Summary

This document captures the current UI/UX state and architectural specifications for the Baroness ChatRoom Context Menu and Reaction systems as of the latest stable implementation.

## 🌟 Visual Core: "Deep Midnight Glass"
The context menu and associated sheets use a high-end glassmorphic aesthetic:
- **Background**: `0.85f` Black tint with high-radius blur (8dp to 30dp).
- **Border**: Rim-light effect using `1.dp` alpha-white (`0.3f`) and `1.5.dp` black shadow edge.
- **Modularity**: A fixed 5-slot architecture in the `ActionMenu` to maintain user muscle memory.

## 🛠️ Key Components & Specifications

### 1. MessageBubble.kt (The 1:1 Twin Engine)
- **`isPreviewMode`**: A dedicated flag that forces a bubble to follow 1:1 timeline rules (No tail/avatar for `isOwn`, Beak/Avatar for `Others`) but forces **Left Alignment** for modal analysis.
- **Tombstone UI**: Support for `isDeleted` state.
    - **Visual**: "⊘ You deleted this message" or "⊘ This message was deleted".
    - **Logic**: It uses the standard bubble shape/theme for consistency but with italicized, semi-transparent text.
- **Reactions**: Integrated `ReactionRow` that respects the message state.

### 2. ReactionRow.kt (The Hanging Pill)
- **Visual**: Perfect circle footprint (`24.dp`) with a `0.8.dp` alpha-white "thin layer" connection.
- **Placement**: Pinned to bubble corners (`42.dp` start for others, `20.dp` end for own).
- **Physics**: Barely touches the wall (`-2.dp` or `-3.dp` offset).
- **Logic**: 
    - **Single-Slot Policy**: Each user can only have one reaction per message.
    - **Toggle**: Tapping a different emoji replaces the old one; tapping the same one "dusts" it out.

### 3. DeleteConfirmationSheet.kt (The Destruction Dashboard)
- **Geometry**: 70% Height Obsidian Sheet.
- **Layout**: Asymmetric Tension.
    - **Top-Left**: 1:1 message bubble preview inside a thin-border rectangle.
    - **Middle-Left**: Bold "Delete message?" prompt.
    - **Bottom-Right**: Destruction options listed with right-aligned text and minimalist separators.
- **Palette**: Use Light Red (`#FF8A80`) for destructive actions.

### 4. TapbackBar.kt (Standalone Module)
- Shared between the full Context Menu and the **Quick-React** mode.
- Features a **Ghost Layer Vanishing Effect**: Emojis liquid-fade into transparency as they scroll toward the minimalist `+` anchor.

## 📡 Architectural Wiring

### Modular Actions Package
Logic is split into categories to ensure clean maintenance:
- `ChatCommunicationActions.kt`: Social lifecycle (Reply, Edit, Unsend, Delete).
- `ChatUtilityActions.kt`: Text processing (Copy, Translate, Share, Search).
- `ChatRepositoryActions.kt`: Data management (Star, Pin, Info, Wishlist).
- `ChatFridayActions.kt`: AI assistant logic.

### Unsend vs. Delete Logic (Planned)
- **Unsend (Vanish)**: Immediate physical wipe. The message is hard-deleted from local DB and server. A `WIPE_MESSAGE` signal is sent via sync pipe to force a hard-delete on recipients. No traces left.
- **Delete for Everyone (Tombstone)**: Soft-delete. Local state marked `isDeleted = true`. Server row updated. A `DELETE_MESSAGE` signal is sent to recipients to trigger their "Ghost" bubble.

## 🧭 Navigation & Interaction
- **BackHandler**: Implemented across all layers to ensure hitting "Back" closes the Sheet -> Menu -> Quick Pill before ever closing the chat room.
- **Quick-React**: Tapping an existing reaction circle drops a clear-screen (no blur) `TapbackBar` directly onto the bubble, perfectly aligned with the bubble's vertical edges.

## 🧮 Emoji Calibration
- **Emoji.kt**: Strict 0.9x font-to-DP ratio cage to ensure system emojis and custom vault emojis have identical footprints and never bleed out of circles or pickers.
