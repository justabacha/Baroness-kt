\# Wishlist Feature Spec



\## Overview

Wishlist management feature for saving and organizing user wishes.



\## Existing Files

\- `screens/WishlistScreen.kt` — main wishlist screen with input, list, and modals

\- `viewmodels/WishlistViewModel.kt` — state management for wishes, stats, modals, user identity

\- `repository/WishlistRepository.kt` — data access layer with Supabase realtime sync

\- `api/WishlistApi.kt` — remote API interface for CRUD operations

\- `data/local/dao/WishDao.kt` — Room database access for wishes

\- `data/local/dao/ReactionDao.kt` — Room database access for emoji reactions

\- `data/local/dao/RatingDao.kt` — Room database access for star ratings

\- `data/local/dao/SyncQueueDao.kt` — Room database access for sync queue

\- `data/local/database/WishEntity.kt` — wish data entity

\- `data/local/database/ReactionEntity.kt` — reaction data entity

\- `data/local/database/RatingEntity.kt` — rating data entity

\- `data/local/database/SyncQueueItem.kt` — sync queue item entity

\- `data/local/database/AppDatabase.kt` — Room database configuration

\- `components/wishlist/ConfirmModal.kt` — confirmation dialog for delete actions

\- `components/wishlist/CustomCalendar.kt` — custom date picker with animations

\- `components/wishlist/RatingModal.kt` — rating dialog with star selection

\- `components/wishlist/WishItem.kt` — wish list item UI with dust/delete/rate actions

\- `components/wishlist/WishlistHeader.kt` — header with avatars and stats display

\- `components/wishlist/WishlistInput.kt` — frosted glass input field with calendar trigger

\- `components/wishlist/WishlistIcons.kt` — custom icons (TrashIcon, DownloadIcon, SmileyIcon, CheckIcon)

\- `components/EmojiPicker.kt` — emoji picker dialog for reactions

\- `components/Emoji.kt` — emoji rendering component

\- `components/PhestyText.kt` — custom text component

\- `models/WishModels.kt` — Wish and WishStats data models

\- `utils/SyncManager.kt` — offline sync queue processing

\- `utils/StorageManager.kt` — local storage for user identity

\- `utils/DateUtils.kt` — date formatting utilities

\- `config/SupabaseConfig.kt` — Supabase client configuration



\## Expected Behavior

\- Create, read, update, delete wishes

\- Set target dates with calendar picker

\- Rate wishes with priority levels

\- Sync across devices via Supabase

\- Offline support for all CRUD operations



\## Data Flow

1\. User action → ViewModel

2\. ViewModel updates local cache → Repository → Room

3\. If online, queue sync → SyncManager → WorkManager

4\. SyncQueueItem tracks pending changes

5\. UI observes StateFlow for real-time updates



\## Known Issues

\- Issue #1: Design System Primary color mismatch with Color.kt (Severity: 🔴)
\- Issue #2: Design System Background/Surface color mismatch with Color.kt (Severity: 🔴)
\- Issue #3: Missing WorkManager for background sync (Severity: 🔴)
\- Issue #5: Typography not fully configured in Type.kt, hardcoded font sizes in screens (Severity: 🟡)
\- Issue #6: Component corner radius inconsistency across various cards and components (Severity: 🟡)



\## Offline Requirements

\- All wish data stored in Room

\- Pending changes queued in SyncQueue

\- Background sync when connection restored

\- No data loss on app close or crash

