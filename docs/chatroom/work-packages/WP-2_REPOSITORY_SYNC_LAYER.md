# WP-2: Repository & Sync Layer

## 1. Objective
Build the data layer that orchestrates local Room storage, Supabase real-time sync, background sync workers, and backup/restore functionality.

## 2. Entry Criteria
- [x] WP-1 ✅ Complete (Database Foundation)
- [x] Docs read: 
  - `docs/chatroom/chatroom_architecture.md`
  - `docs/chatroom/chatroom_database.md`
  - `docs/chatroom/chatroom_file_structure.md`
  - `docs/chatroom/chatroom_implementation.md`
  - `docs/chatroom/reports/WP-1_DATABASE_FOUNDATION_EXECUTION_REPORT.md`
- [ ] Existing files to inspect: 
  - `ChatRepository.kt` (Doesn't exist, will be created)
  - `SyncWorker.kt` (Wishlist pattern to reuse)
  - `Supabase client configuration` (`SupabaseConfig.kt`)
  - `WishlistRepository.kt` (Reference for repository patterns)

## 3. Scope

### 3.1 Tasks
- [x] **Create ChatRepository.kt** — Handle local/remote coordination. UI will observe Room via Flow. 
    - **Optimistic Send Logic**: 1. Write to local Room immediately (status: PENDING). 2. Write to `messages` (permanent remote store). 3. Write to `chat_sync_pipe` (ephemeral delivery pipe).
    - **Participant Fetching**: Implement `getParticipant(id: String)` to fetch profile data (display name, avatar) from the Supabase `profiles` table.
- [x] **Create ChatSyncWorker.kt** — Background sync for `PENDING` messages with exponential backoff.
- [x] **Implement Real-time Broadcast** — Use Supabase Realtime for instant delivery. Use `chat_` prefix for all channel names (e.g., `chat_{conversationId}`) to avoid collisions with Wishlist.
- [x] **Implement Backup Logic** — Create `BackupManager.kt`. 
    - **Requirement**: Use per-persona pathing: `backups/{persona}_official_backup.json`.
    - **Format**: Flat JSON array of message objects, excluding those where `isDeleted = true`.
- [x] **Implement Restore Logic** — Download backup JSON from Storage and unpack into Room.
- [x] **Integrate with SettingsViewModel** — For user identity/persona resolution during sync and backup.

### 3.2 Files to Create
| File | Path | Responsibility |
|------|------|---------------|
| `ChatRepository.kt` | `app/src/main/java/com/baroness/app/repository/ChatRepository.kt` | Coordinates Room and Supabase, handles sync, backup, restore |
| `ChatSyncWorker.kt` | `app/src/main/java/com/baroness/app/workers/ChatSyncWorker.kt` | Background WorkManager worker for `PENDING` message sync |
| `BackupManager.kt` | `app/src/main/java/com/baroness/app/repository/BackupManager.kt` | JSON packaging, Storage upload/download, backup tracking |

### 3.3 Files to Modify
| File | Path | Change |
|------|------|--------|
| `SupabaseConfig.kt` | `app/src/main/java/com/baroness/app/config/SupabaseConfig.kt` | Ensure Storage and Realtime are initialized for chat use. |

### 3.4 Files to Delete
| File | Path | Reason |
|------|------|--------|
| None | | |

## 4. Manual Steps for Owner (Phesty)
| Step | Action | Where | Status |
|------|--------|-------|--------|
| 1 | Create Supabase Storage bucket `chat_backups` with appropriate RLS policies. | Supabase Dashboard → Storage | ⏳ Pending |
| 2 | Add GROQ_API_KEY to local.properties (if not already) | Project root | ⏳ Pending |
| 3 | Test sync by sending message with network off, then on | Device | ⏳ Pending |

## 5. Exit Criteria
- [ ] `ChatRepository` handles all CRUD operations and observes Room.
- [ ] `ChatSyncWorker` retries `PENDING` messages with exponential backoff without affecting Wishlist sync.
- [ ] Real-time broadcast delivers messages instantly using `chat_` prefixed channels.
- [ ] `chat_sync_pipe` handles delivery for offline recipients.
- [ ] Backup creates valid JSON at `backups/{persona}_official_backup.json` and uploads to Supabase.
- [ ] Restore downloads JSON and populates Room correctly.
- [ ] Project compiles successfully.
- [ ] Manual steps verified by owner.

## 6. Complexity
Medium

## 7. Estimated Effort
2 agent passes

## 8. Risks
- 🚩 **Real-time Channel Collisions**: Using generic channel names may intercept Wishlist traffic. **Fix**: Use `chat_` prefix.
- 🚩 **RLS for Storage**: `chat_backups` bucket requires explicit RLS policies (e.g., owner-only access) or the worker will receive 403 Forbidden errors.
- 🚩 **Storage Bucket**: Backup will fail if "chat_backups" bucket is not created manually.
- 🚩 **Large Backups**: JSON format might get large; consider compression or incremental backups in future iterations.
- 🚩 **WorkManager Constraints**: Ensure sync doesn't drain battery by using appropriate network constraints.

## 9. Dependencies
- Blocks: WP-3 (ViewModel Architecture)
- Blocked by: WP-1 (Database Foundation) ✅

## 10. Notes
- Reuse existing Supabase client configuration from `SupabaseConfig.kt`.
- Follow existing repository pattern (`SettingsRepository`, `WishlistRepository` as reference).
- `ChatSyncWorker` should query `MessageDao` for messages with `status = "PENDING"`.
- Backup JSON format: A flat JSON array of message objects, excluding those where `isDeleted = true`.
