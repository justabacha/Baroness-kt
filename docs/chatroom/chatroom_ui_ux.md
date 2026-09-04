# ChatRoom UI/UX Design Document

## Overview
The Baroness ChatRoom follows a modern, intimate design language characterized by glassmorphism, soft glows, and premium typography. It leverages the existing theme system to provide a highly personalized experience while maintaining a consistent aesthetic across AI and human conversations.

---

## 1. ChatRoom Screen Layout
The screen is composed of four main functional areas, overlaid on a dynamic background.

- **Top Bar**: 
    - **Back Button**: Standard navigation back arrow.
    - **Avatar**: 36dp circular image (Participant avatar or Friday icon).
    - **Name & Status**: Participant name with an "Online" or "Friday" indicator below.
    - **Glassmorphic Surface**: Semi-transparent background with a subtle border.
- **Background**: 
    - **Wallpaper**: Resolved via `SettingsViewModel.activeWallpaper`.
    - **Overlay**: A dark gradient overlay to ensure text readability and maintain the "Always Dark" app aesthetic.
- **Message List Area**: 
    - **LazyColumn**: Inverted (`reverseLayout = true`) to keep new messages at the bottom.
    - **Infinite Scroll**: Scrolling up triggers loading of older messages from Room.
- **Input Area**: 
    - **Floating Design**: Positioned at the bottom with `imePadding()` to handle native keyboard behavior.
    - **Glassmorphism**: High-blur frosted background following the `WishlistInput` style.

---

## 2. Message Bubble Design
A single `MessageBubble.kt` component renders messages with conditional styling based on ownership and participant type.

- **Shape**: 
    - **Corners**: Rounded (16dp-20dp).
    - **Tails**: Asymmetric; pointing right for "Own", left for "Other".
- **Colors (Theme-Based)**: 
    - **Own**: Uses the current theme's `accent` or `glowColor` with high opacity.
    - **Other**: Low-opacity translucent grey/white (`Colors.textDim.copy(alpha = 0.1f)`).
- **Typography**: 
    - **Content**: Rendered via `PhestyText` to support custom emojis.
    - **Font**: Resolved via `ChatTypography` and `SettingsViewModel`.
- **Metadata**: 
    - **Timestamp**: Small, dimmed text in the bottom-right corner.
    - **Read Receipts**: 
        - Human: Single check (`✓`) for Sent, double blue check (`✓✓`) for Read.
        - Friday: Single check (`✓`) or none.
- **Reactions**: 
    - Floating row slightly overlapping the bottom of the bubble.
    - Human: Persistent and synced via `chat_sync_pipe`.
    - Friday: Persistent locally in Room (`MessageEntity.reactions`), but flagged to skip Supabase transport/sync.

---

## 3. Chat Input Design
The input component is a refined version of the `WishlistInput` glassmorphism reference.

- **Visuals**: Frosted glass background, 1dp white border (low opacity), 40dp corner radius.
- **TextField**: 
    - Multiline support (up to 5-6 lines before internal scrolling).
    - Auto-expanding height based on content.
- **Send Button**: 
    - Active: Glowing icon when text is present.
    - Inactive: Dimmed icon.
- **Attachment Button**: 
    - Paperclip icon on the left.
    - Tap triggers a "Coming Soon" modal overlay.

---

## 4. Typing Indicator
Provides real-time feedback when the other participant is composing a message.

- **Location**: Anchored just above the Input Area.
- **Visual**: "Friday is typing..." or "Name is typing..." with animated jumping dots.
- **Logic**:
    - **Human**: Driven by Supabase Realtime broadcast events.
    - **Friday**: Simulated delay (1.5s - 3s) before the AI response appears.

---

## 5. Context Menu (Long Press)
Triggered by a 350ms long press on any message bubble.

- **Interactions**: Haptic feedback (vibration) + subtle scale-up animation of the bubble.
- **Visual Overlay**: 
    - Background blur (radius = 15dp).
    - Dimming of all surrounding messages.
    - A "cloned" version of the bubble lifted into the foreground.
- **Tapback Bar**: Floating bar above the bubble with ❤️ 👍 👎 😂 ‼️ ❓ and a `+` button for the full `EmojiPicker`.
- **Action Menu**: Vertical menu below/beside the bubble:
    - **Reply**: (Stub for v1).
    - **Copy**: Copies content to clipboard.
    - **Delete**: Soft delete (updates `isDeleted` flag).
    - **Edit**: Only visible for own messages; populates input for correction.

---

## 6. Theme & Integration
The ChatRoom is a reactive consumer of the `SettingsViewModel`.

- **Active Theme**: Dictates the "Glow" color of own bubbles and the accent of the Send button.
- **Active Font**: All text components resolve their `FontFamily` and `Weight` via the `ChatTypography` resolver.
- **Active Wallpaper**: The screen background updates instantly when the wallpaper is changed in the Drawer.
- **Static Assets**: The Drawer itself retains its `image_39` background as per the design system.

---

## 7. Empty States & Loading
- **New Chat**: "Start your conversation with [Name]..." centered in the list area.
- **Loading History**: A small, white `CircularProgressIndicator` at the top of the list when fetching older messages.
- **First Message**: "Beginning of your conversation" footer when the very first message is reached.

---

## 8. Animations & Interactions
- **Optimistic Send**: Message appears instantly with a fade-in + slight upward slide; checkmarks update as sync progresses.
- **New Message Scroll**: Automatically scrolls to the bottom when a new message is received or sent.
- **Context Menu Entrance**: Spring animation for the bubble lift and menu appearance.

---

## 9. Responsive Design
- **Bubble Width**: Maximum of 75% of total screen width to avoid stretching on large devices.
- **Avatars**: Fixed at 36dp for consistency.
- **Paddings**: 
    - 8dp between bubbles of the same sender.
    - 16dp between bubbles of different senders.
    - 16dp horizontal screen padding.

---

## 10. Component Reuse Checklist
- [x] **PhestyText.kt**: Used for all message content (Emoji support).
- [x] **EmojiPicker.kt**: Full reaction selection.
- [x] **TopWarningBanner.kt**: Displaying connection errors or sync failures.
- [x] **ChatTypography.kt**: Font resolution for title, body, and meta text.
- [x] **WishlistInput.kt**: Visual reference for the glassmorphic input area.
