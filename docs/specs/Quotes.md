# Quotes Feature Spec



## Overview

Daily quotes feature that displays inspirational quotes with background images on the dashboard. Quotes are fetched from a remote source, cached locally, and include associated images.



## Existing Files

- `data/QuoteRepository.kt` — repository for fetching, caching, and downloading daily quotes and images

- `models/VibeQuote.kt` — data model for quotes with two text parts and two image URLs

- `components/QuoteCard.kt` — composable for displaying quote with image background, date, text, and author

- `data/SignatureLoops.kt` — static quote collection with predefined VibeQuote objects

- `utils/VibeManager.kt` — utility for getting vibe of the day based on launch date



## Expected Behavior

- Fetch daily quote from remote source based on current date

- Cache quote data locally using StorageManager

- Download and cache associated images locally

- Display quote with beautiful card UI featuring tilt effects

- Fallback to static quotes if remote fetch fails

- Clean up old cached images to manage storage



## Data Flow

1. DashboardViewModel requests quote → QuoteRepository

2. QuoteRepository checks cache date → if same day, use cached

3. If different day or force refresh, fetch from remote

4. Download associated images to local storage

5. Cache quote data and date via StorageManager

6. Return VibeQuote to ViewModel → UI displays via QuoteCard



## Known Issues

- Issue #1: Design System Primary color mismatch with Color.kt (Severity: 🔴)
- Issue #2: Design System Background/Surface color mismatch with Color.kt (Severity: 🔴)
- Issue #5: Typography not fully configured in Type.kt, hardcoded font sizes in screens (Severity: 🟡)
- Issue #6: Component corner radius inconsistency across various cards and components (Severity: 🟡)



## Offline Requirements

- Cached quotes available offline

- Downloaded images stored locally

- Fallback to SignatureLoops quotes if network unavailable

- Cache date tracking to avoid unnecessary fetches
