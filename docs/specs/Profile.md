# Profile Feature Spec



## Overview

User profile setup and management feature allowing users to set their display name, avatar, and persona. Profiles are stored locally and synced with Supabase backend.



## Existing Files

- `screens/ProfileSetupScreen.kt` — composable screen for profile setup with avatar selection and display name input

- `viewmodels/ProfileSetupViewModel.kt` — viewmodel handling profile loading, saving, image selection, and logout

- `data/AvatarRepository.kt` — repository for caching and downloading user avatars from URLs

- `models/UserProfile.kt` — data model for user profile with displayName, avatar, persona, and id

- `modules/ProfileManager.kt` — object handling Supabase backend operations for profile upsert and avatar upload

- `utils/StorageManager.kt` — local storage for caching profile data and avatar paths

- `utils/SessionManager.kt` — session management for authentication state

- `utils/UserSessionManager.kt` — user session management utilities



## Expected Behavior

- Load existing profile from local cache or Supabase

- Allow users to select avatar from device gallery

- Allow users to set display name

- Save profile locally and sync to Supabase

- Upload avatar to Supabase storage

- Logout functionality with confirmation dialog

- Navigate to dashboard or gate based on auth state



## Data Flow

1. ProfileSetupScreen loads → ProfileSetupViewModel

2. ViewModel loads profile from StorageManager cache

3. If no cache, load from Supabase via ProfileManager

4. User selects image → AvatarRepository handles caching

5. User saves profile → ProfileManager upserts to Supabase

6. Avatar uploaded to Supabase storage → URL returned

7. Profile saved locally via StorageManager

8. Logout clears session and navigates to gate



## Known Issues

- [To be filled by review agents]



## Offline Requirements

- Profile data cached locally via StorageManager

- Avatar images cached locally via AvatarRepository

- Can view profile offline

- Profile sync deferred until connection restored
