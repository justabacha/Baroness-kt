# Dashboard Feature Spec



## Overview

Main dashboard screen displaying user profile, personalized greeting, current time/date, weather information, and daily quotes. Features pull-to-refresh, voice announcements, and quote sharing functionality.



## Existing Files

- `screens/DashboardScreen.kt` — main dashboard composable with profile, greeting, time, weather, and quotes

- `viewmodels/DashboardViewModel.kt` — viewmodel managing data flows for profile, greeting, time, vibe, weather, and refresh

- `data/QuoteRepository.kt` — repository for fetching daily quotes

- `data/AvatarRepository.kt` — repository for caching user avatars

- `utils/VibeManager.kt` — utility for dynamic greetings, date formatting, and weather fetching

- `utils/LocationHelper.kt` — utility for getting device location for weather

- `utils/VoiceManager.kt` — utility for text-to-speech announcements

- `utils/StorageManager.kt` — local storage for caching profile and weather data

- `models/UserProfile.kt` — data model for user profile

- `models/VibeQuote.kt` — data model for daily quotes

- `components/QuoteCard.kt` — composable for displaying quote cards

- `components/DownloadIcon.kt` — custom icon for download action

- `components/SettingsGearIcon.kt` — custom icon for settings action



## Expected Behavior

- Display user profile with avatar and display name

- Show personalized greeting based on time of day and persona

- Display current time and formatted date

- Fetch and display weather data based on location

- Display daily quote with beautiful card UI

- Support pull-to-refresh for all data

- Voice announcement of greeting on load

- Share quote functionality

- Navigate to profile setup or other screens



## Data Flow

1. DashboardScreen loads → DashboardViewModel

2. ViewModel loads profile from StorageManager cache

3. ViewModel loads greeting via VibeManager (time-based, persona-specific)

4. ViewModel updates time/date every second

5. ViewModel fetches weather via LocationHelper and VibeManager

6. ViewModel fetches quote via QuoteRepository

7. All data exposed as StateFlow → UI collects and displays

8. Pull-to-refresh triggers reload of all data

9. VoiceManager announces greeting on initial load



## Known Issues

- Issue #1: Design System Primary color mismatch with Color.kt (Severity: 🔴)
- Issue #2: Design System Background/Surface color mismatch with Color.kt (Severity: 🔴)
- Issue #4: State collection in UI uses `collectAsState()` instead of `collectAsStateWithLifecycle()` (Severity: 🟡)
- Issue #5: Typography not fully configured in Type.kt, hardcoded font sizes in screens (Severity: 🟡)
- Issue #6: Component corner radius inconsistency across various cards and components (Severity: 🟡)
- Issue #7: Button styling inconsistency in DashboardScreen (Severity: 🟡)



## Offline Requirements

- Profile data cached locally

- Weather data cached with fallback to default

- Quote data cached locally with date tracking

- Greeting and time/date work offline

- Avatar images cached locally
