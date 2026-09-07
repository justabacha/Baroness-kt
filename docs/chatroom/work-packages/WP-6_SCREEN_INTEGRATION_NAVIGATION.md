# WP-6: Screen Integration & Navigation

## 1. Objective
Assemble the components into the final `ChatRoomScreen`, connect it to the navigation graph, and wire the top bar and background systems.

## 2. Entry Criteria
- [x] WP-3 ✅ Complete (ViewModels)
- [x] WP-4 ✅ Complete (Core UI)
- [x] WP-5 ✅ Complete (Context Menu)
- [x] Docs read:
    - `docs/chatroom/chatroom_architecture.md` (Section 1)
    - `docs/chatroom/chatroom_ui_ux.md` (Section 1, 6)
- [ ] Existing files to inspect:
    - `app/src/main/java/com/baroness/app/MainActivity.kt` (Navigation)
    - `app/src/main/java/com/baroness/app/screens/ChatListScreen.kt` (Entry point)

## 3. Scope

### 3.1 Tasks
- [x] **Implement ChatRoomScreen.kt** — The main container assembly using `Scaffold`. 
- [x] **Wire Dynamic Background** — Observe `SettingsViewModel.activeWallpaper` and apply dark overlay gradient.
- [x] **Implement ChatTopBar** — Participant avatar, name, and status with glassmorphic background.
- [x] **Setup Navigation** — Register `chat_room/{conversationId}` route in `MainActivity.kt`.
- [x] **Connect Entry Point** — Update `ChatListScreen.kt` to navigate to the new `ChatRoomScreen` when a conversation is tapped.
- [x] **Handle Empty States** — Display "Start your conversation..." messages when Room is empty.

### 3.2 Files to Create
| File | Path | Responsibility |
|------|------|---------------|
| `ChatRoomScreen.kt` | `app/src/main/java/com/baroness/app/screens/ChatRoomScreen.kt` | The main screen container. |

### 3.3 Files to Modify
| File | Path | Change |
|------|------|--------|
| `MainActivity.kt` | `app/src/main/java/com/baroness/app/MainActivity.kt` | Add ChatRoom navigation route. |
| `ChatListScreen.kt` | `app/src/main/java/com/baroness/app/screens/ChatListScreen.kt` | Update click listeners to navigate to `chat_room/{id}`. |

### 3.4 Files to Delete
| File | Path | Reason |
|------|------|--------|
| None | | |

## 4. Manual Steps for Owner (Phesty)
| Step | Action | Where | Status |
|------|--------|-------|--------|
| 1 | Verify wallpaper transitions when changing themes in the drawer. | App Drawer | ⏳ Pending |

## 5. Exit Criteria
- [x] Navigating to `chat_room/friday` opens the screen with Friday's identity.
- [x] Navigating to a human ID (e.g., `baroness`) shows their profile and message history.
- [x] The background updates reactively to settings changes.
- [x] Screen handles system back button and top bar back arrow correctly.
- [x] Project compiles and navigates end-to-end.

## 6. Complexity
Medium (Estimated Effort: 1 agent pass)

## 7. Risks
- 🚩 **Backstack Management**: Ensuring the navigation stack doesn't grow infinitely if users hop between conversations.
- 🚩 **Resource Loading**: Ensuring participant avatars and wallpapers load without stuttering during screen transitions.

## 8. Dependencies
- Blocks: WP-7, WP-8
- Blocked by: WP-3, WP-4, WP-5 ✅

## 9. Notes
- The Top Bar should be semi-transparent to show a hint of the background wallpaper.
- Use `rememberSystemUiController()` to ensure the status bar colors match the "Always Dark" aesthetic.
