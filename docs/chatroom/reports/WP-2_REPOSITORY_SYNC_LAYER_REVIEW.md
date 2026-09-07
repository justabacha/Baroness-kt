# WP-2: Repository & Sync Layer — Review Report

- **Status**: **APPROVED**

## 1. Executive Summary
The revised WP-2 blueprint successfully addresses all architectural and technical gaps identified in the previous review. It now provides the necessary "surgical details" for implementing a robust, offline-first sync layer that is properly decoupled from the existing Wishlist infrastructure.

## 2. Review Checklist Verification

### 2.1 Architecture Alignment
- [x] **Optimistic Updates**: Explicitly mandates the 3-step write process (Local -> Permanent Store -> Sync Pipe).
- [x] **Real-time Isolation**: Uses the `chat_` prefix for channels to avoid collisions with Wishlist.
- [x] **Participant Fetching**: Added task for `getParticipant` to support WP-3 (ViewModels).

### 2.2 Supabase & Sync Logic
- [x] **Decoupling**: Explicitly forbids reusing the Wishlist-coupled `SyncManager.kt`.
- [x] **Worker Implementation**: `ChatSyncWorker` will handle chat-specific logic independently.
- [x] **Storage RLS**: Risks and Manual Steps now cover both bucket creation and RLS policy requirements.

### 2.3 Backup & Restore
- [x] **Pathing**: Correctly uses the `backups/{persona}_official_backup.json` per-persona pathing.
- [x] **Format**: Confirmed as a flat JSON array excluding deleted messages.

## 3. Final Recommendations
The blueprint is now highly detailed and technically accurate. The executor should pay special attention to:
1.  **Supabase Config**: Ensuring the `SupabaseConfig.kt` correctly initializes `Storage` and `Realtime` for the new repository.
2.  **RLS Policies**: The manual setup of RLS for the `chat_backups` bucket is a hard blocker for the `BackupManager`.

## 4. Conclusion
WP-2 is ready for execution. The transition from WP-1's database foundation to a functional repository layer is well-mapped.

**READY FOR EXECUTION.**
