# WP-2: Repository & Sync Layer - Execution Report (REVISED)

## 1. File Listing

| Path | Responsibility | Status |
| :--- | :--- | :--- |
| `app/src/main/java/com/baroness/app/api/ChatApi.kt` | Added `deletePipeItem` and `BackupLogDto`. | Modified |
| `app/src/main/java/com/baroness/app/repository/ChatRepository.kt` | Added real-time receiving (Sync Pipe), network/lifecycle listeners, and purged pipe items after consumption. | Modified |
| `app/src/main/java/com/baroness/app/workers/ChatSyncWorker.kt` | Correctly pushes to `messages` and `chat_sync_pipe`. | Verified |
| `app/src/main/java/com/baroness/app/repository/BackupManager.kt` | Updated to filter backups by `personaId`. | Modified |
| `app/src/main/java/com/baroness/app/data/local/dao/MessageDao.kt` | Updated `getMessagesForBackup` to filter by `personaId`. | Modified |

## 2. Manual Steps for Owner (Phesty)
1. **Supabase Storage**: Create a bucket named `chat_backups`.
2. **Bucket Policies**: Add RLS to `chat_backups` for per-persona access.
3. **Local Properties**: Add `GROQ_API_KEY`.

## 3. Verification Steps
- **Build Status**: Successful execution of `./gradlew :app:assembleDebug`.
- **Real-time Receiving**: `ChatRepository` now subscribes to `chat_sync_pipe` for instant delivery.
- **Pipe Cleanup**: Items in `chat_sync_pipe` are deleted via `ChatApi.deletePipeItem` after being written to Room.
- **Orchestration**: `ChatRepository` now triggers sync on network availability and app resume.
- **Backup**: Filtered by `personaId` to ensure data privacy.
- **WorkManager**: Uses `ExistingWorkPolicy.REPLACE` for immediate sync triggers.

## 4. Assumptions Made
- **Real-time Filter**: Relies on Supabase RLS for initial filtering of `chat_sync_pipe` records based on the JWT `persona_id`, with a secondary Kotlin-side check for `recipient_id`.

## 5. Compilation Status
- **Result**: PASSED
