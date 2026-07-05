# Feature: PhotoGallery

\## Overview
Transform the existing PhotosScreen from a basic internet image loader into a full iOS-style glassmorphism photo gallery with local caching, smooth animations, zoom/pan capabilities, and offline viewing support.

\## User Stories
- As a user, I want to browse my photos in a beautiful grid with glassmorphism effects so that the app feels premium and modern
- As a user, I want to view photos offline after they've loaded once so that I don't need constant internet
- As a user, I want to zoom and pan into photos so that I can see details clearly
- As a user, I want photos organized by date/album so that I can find specific memories easily
- As a user, I want smooth transitions between grid and full-screen view so that navigation feels native

\## Screen Layout / Design

\### Main Gallery Screen (Grid View)
- Top bar:
    - Back button (if navigated from elsewhere)
    - Title: "Gallery" or album name
    - Options menu (sort by date/name, view mode toggle grid/list)
- Main content:
    - Staggered grid of photos with glassmorphism cards
    - Each photo shows thumbnail with subtle blur backdrop
    - Date section headers (e.g., "Today", "Yesterday", "July 2026")
    - Pull-to-refresh with glassmorphism spinner
- Bottom section:
    - Floating action button: Add photo (camera/gallery picker)
    - Album selector chip row (All, Favorites, Selfies, etc.)

\### Full-Screen Photo Viewer
- Top bar (auto-hides):
    - Back button / close
    - Share button
    - Favorite toggle (heart)
    - Delete button
- Main content:
    - Full-screen photo with zoom/pan ( pinch to zoom, double-tap to zoom)
    - Glassmorphism overlay for controls
    - Swipe left/right to navigate between photos
- Bottom section (auto-hides):
    - Photo info: date taken, file size, resolution
    - Edit button (crop, filter, rotate)

\### Empty State
- Glassmorphism card with illustration
- Text: "No photos yet"
- Button: "Take your first photo" or "Browse gallery"

\### Loading State
- Shimmer effect on glassmorphism placeholders
- Skeleton grid while photos load

\### Error State
- Glassmorphism error card
- "Failed to load photos"
- Retry button
- "View cached photos" button (if offline)

\## Interactions & Behavior
- Tap photo: Opens full-screen viewer with shared element transition
- Swipe left/right in viewer: Navigate to next/previous photo
- Pinch in viewer: Zoom into photo
- Double-tap viewer: Toggle zoom fit/fill
- Long-press photo in grid: Multi-select mode (checkbox appears)
- Pull-to-refresh: Sync with Supabase, fallback to cached photos
- Back button from viewer: Returns to grid with scroll position preserved

\## Data Requirements
- What data to display:
    - Photo ID (bigint)
    - Image URL (text)
    - Local cached path (text, nullable)
    - Thumbnail URL (text, nullable)
    - Album/folder name (text)
    - Date taken (timestamp)
    - Date added to app (timestamp)
    - Is favorite (boolean)
    - File size (bigint, nullable)
    - Width/height (integer, nullable)
    - MIME type (text, nullable)
- Data source: Both (Room local + Supabase remote)
- Realtime updates needed: Yes (new photos from other users/devices)
- Offline support: All viewed photos cached locally via Room + file system
- Sync strategy:
    - Initial load: Fetch from Room cache
    - If online: Sync with Supabase, download new photos
    - Background: WorkManager sync pending uploads/downloads
    - On new photo: Realtime subscription updates Room, UI refreshes

\## Supabase Database Schema

\### Modified Tables
- `gallery_items` — already exists, needs columns added:
    - `thumbnail_url` text (nullable)
    - `album_name` text (DEFAULT 'All')
    - `is_favorite` boolean (DEFAULT false)
    - `date_taken` timestamp with time zone (nullable)
    - `file_size` bigint (nullable)
    - `width` integer (nullable)
    - `height` integer (nullable)
    - `mime_type` text (nullable)

\### New Tables
sql
-- Table: photo_albums
CREATE TABLE photo_albums (
id bigint PRIMARY KEY,
name text NOT NULL,
cover_photo_id bigint REFERENCES gallery_items(id),
created_by text NOT NULL,
created_at timestamp with time zone DEFAULT now()
);

-- Enable RLS
ALTER TABLE photo_albums ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "photo_albums_select" ON photo_albums
FOR SELECT USING (true);

CREATE POLICY "photo_albums_insert" ON photo_albums
FOR INSERT WITH CHECK (true);

CREATE POLICY "photo_albums_update" ON photo_albums
FOR UPDATE USING (true);

CREATE POLICY "photo_albums_delete" ON photo_albums
FOR DELETE USING (true);

-- Enable realtime
ALTER PUBLICATION supabase_realtime ADD TABLE photo_albums;

\## Files to Create
- `screens/PhotoGalleryScreen.kt` — main gallery grid with glassmorphism
- `screens/PhotoViewerScreen.kt` — full-screen zoom/pan viewer
- `viewmodels/PhotoGalleryViewModel.kt` — gallery state management
- `viewmodels/PhotoViewerViewModel.kt` — viewer state management
- `repository/PhotoGalleryRepository.kt` — data access with caching
- `models/PhotoModels.kt` — Photo, Album, PhotoMetadata data classes
- `components/gallery/GlassmorphismCard.kt` — reusable glassmorphism container
- `components/gallery/ZoomableImage.kt` — pinch-to-zoom image composable
- `components/gallery/PhotoGridItem.kt` — grid thumbnail with glassmorphism
- `components/gallery/AlbumChip.kt` — album selector chip
- `components/gallery/PhotoInfoOverlay.kt` — glassmorphism info panel
- `data/local/dao/PhotoDao.kt` — Room DAO for photos
- `data/local/dao/AlbumDao.kt` — Room DAO for albums
- `data/local/database/PhotoEntity.kt` — Room entity for photos
- `data/local/database/AlbumEntity.kt` — Room entity for albums
- `docs/specs/PhotoGallery.md` — feature specification

\## Architecture Constraints
- Use StateFlow (not LiveData)
- Collect with collectAsStateWithLifecycle()
- State hoisting to ViewModel
- No business logic in Composables
- Follow existing patterns from Photos feature
- Preserve offline-first behavior
- Use Coil for image loading with crossfade
- Cache images to local file system, metadata to Room

\## Do NOT Modify
- `screens/DashboardScreen.kt` — keep dashboard as-is
- `viewmodels/DashboardViewModel.kt` — no dashboard changes
- `repository/WishlistRepository.kt` — unrelated feature
- `ui/theme/Color.kt` — colors already defined
- `ui/theme/Theme.kt` — theme setup unchanged

\## Open Questions
- Should we use Exif data to extract date taken from photos?
- Should albums be user-created or pre-defined categories?
- Do we need video support or photos only for now?