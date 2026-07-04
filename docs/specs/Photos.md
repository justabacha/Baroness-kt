\# Photos Feature Spec



\## Overview

Photo browsing and viewing feature for the Baroness app.



\## Existing Files

\- `screens/PhotosScreen.kt` — main photo grid screen

\- `viewmodels/PhotosViewModel.kt` — state management and business logic

\- `repository/PhotoRepository.kt` — data access layer

\- `components/PhotoViewerOverlay.kt` — full-screen overlay viewer

\- `models/PhotoItem.kt` — photo data model



\## Expected Behavior

\- Load photos from local cache first (Room database)

\- Sync with remote (Supabase) when online

\- Support offline viewing of cached photos

\- Tap photo to open full-screen overlay

\- Swipe to dismiss overlay



\## Data Flow

1\. UI requests photos → ViewModel

2\. ViewModel checks local cache → Repository

3\. If online, sync remote changes → Repository

4\. Repository returns Flow of photos → ViewModel

5\. ViewModel exposes StateFlow → UI collects



\## Known Issues

\- \[To be filled by review agents]



\## Offline Requirements

\- All viewed photos must be cached locally

\- Photo metadata stored in Room

\- Thumbnails generated and cached

