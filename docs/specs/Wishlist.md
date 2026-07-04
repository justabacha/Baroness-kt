\# Wishlist Feature Spec



\## Overview

Wishlist management feature for saving and organizing user wishes.



\## Existing Files

\- `viewmodels/WishlistViewModel.kt` — state management

\- `repository/WishlistRepository.kt` — data access layer

\- `api/WishlistApi.kt` — remote API interface

\- `data/local/dao/WishDao.kt` — Room database access

\- `data/local/database/WishEntity.kt` — wish data entity

\- `components/wishlist/ConfirmModal.kt` — confirmation dialog

\- `components/wishlist/CustomCalendar.kt` — date picker

\- `components/wishlist/RatingModal.kt` — rating dialog

\- `components/wishlist/WishItem.kt` — wish list item UI

\- `components/wishlist/WishlistIcons.kt` — icon components



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

\- \[To be filled by review agents]



\## Offline Requirements

\- All wish data stored in Room

\- Pending changes queued in SyncQueue

\- Background sync when connection restored

\- No data loss on app close or crash

