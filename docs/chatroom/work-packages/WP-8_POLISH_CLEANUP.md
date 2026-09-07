# WP-8: Polish & Cleanup

## 1. Objective
Perform final cleanup of obsolete code, address minor UI edge cases, and ensure the project is production-ready.

## 2. Entry Criteria
- [x] WP-7 ✅ Complete (AI & Polish)
- [x] Docs read:
    - `docs/chatroom/chatroom_file_structure.md` (Section 4)
- [x] Existing files to inspect:
    - `app/src/main/java/com/baroness/app/screens/MessagesScreen.kt` (Obsolete)

## 3. Scope

### 3.1 Tasks
- [x] **Delete Obsolete Files** — Remove `MessagesScreen.kt` and any associated temporary placeholders.
- [x] **Cleanup Navigation** — Ensure all legacy routes pointing to the old inbox are removed or redirected.
- [x] **UI Polish** — Fine-tune animations, haptics, and scroll behaviors based on final testing.
- [x] **Edge Case Handling** — Ensure the UI handles very long messages, multiple reactions, and connectivity drops gracefully.
- [x] **Code Review & Documentation** — Ensure all new code follows PascalCase naming and clear responsibility patterns.

### 3.2 Files to Create
| File | Path | Responsibility |
|------|------|---------------|
| None | | |

### 3.3 Files to Modify
| File | Path | Change |
|------|------|--------|
| `MainActivity.kt` | `app/src/main/java/com/baroness/app/MainActivity.kt` | Remove legacy `MessagesScreen` route. |

### 3.4 Files to Delete
| File | Path | Reason |
|------|------|--------|
| `MessagesScreen.kt` | `app/src/main/java/com/baroness/app/screens/MessagesScreen.kt` | Obsolete. Replaced by `ChatRoomScreen`. |

## 4. Manual Steps for Owner (Phesty)
| Step | Action | Where | Status |
|------|--------|-------|--------|
| 1 | Final end-to-end sanity check on a clean install. | Device | ⏳ Pending |

## 5. Exit Criteria
- [x] Obsolete files are removed from the project.
- [x] Navigation is clean and error-free.
- [x] All TODOs in ChatRoom related code are addressed.
- [x] Optimistic updates and `ChatSyncWorker` verified for final production state.
- [x] Project builds and runs without warnings related to ChatRoom.

## 6. Complexity
Small (Estimated Effort: 1 agent pass)

## 7. Risks
- 🚩 **Broken Redirects**: Ensure no other part of the app still tries to navigate to `MessagesScreen.kt`.

## 8. Dependencies
- Blocks: None
- Blocked by: WP-7 ✅

## 9. Notes
- This is the final stage before feature completion.
- Focus on performance and battery impact of the new workers and observers.
