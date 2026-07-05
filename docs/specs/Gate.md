# Gate Feature Spec



## Overview

Authentication gate screen requiring users to enter a secret key to access the application. Supports persona selection (Phesty or Baroness) and validates credentials against Supabase backend.



## Existing Files

- `screens/GateScreen.kt` — composable authentication screen with password input and persona selection

- `viewmodels/GateViewModel.kt` — viewmodel handling password input, validation, and authentication logic

- `modules/AuthManager.kt` — authentication manager for validating secret keys against Supabase

- `utils/StorageManager.kt` — local storage for saving persona and user profile after auth

- `models/UserProfile.kt` — data model for user profile returned after auth

- `utils/SessionManager.kt` — session management for authentication state

- `utils/UserSessionManager.kt` — user session management utilities



## Expected Behavior

- Display password input field with visibility toggle

- Display two persona selection buttons (Phesty, Baroness)

- Validate password minimum length (6 characters)

- On submit, validate secret key against Supabase access_keys table

- If valid, check for existing profile in Supabase profiles table

- If profile exists, navigate to dashboard

- If no profile, navigate to profile setup

- Save persona ID and profile locally via StorageManager

- Display loading state during authentication

- Display error messages for invalid credentials



## Data Flow

1. User enters password and selects persona → GateViewModel

2. User submits → GateViewModel calls AuthManager.checkGate()

3. AuthManager fetches secret key from Supabase access_keys table

4. AuthManager compares input with stored secret key

5. If valid, AuthManager fetches profile from Supabase profiles table

6. AuthManager returns GateResult.Success with profile and personaId

7. GateViewModel saves persona and profile via StorageManager

8. GateViewModel navigates to dashboard (if profile) or profile_setup (if no profile)

9. If invalid, GateViewModel displays error message



## Known Issues

- Issue #1: Design System Primary color mismatch with Color.kt (Severity: 🔴)
- Issue #2: Design System Background/Surface color mismatch with Color.kt (Severity: 🔴)
- Issue #5: Typography not fully configured in Type.kt, hardcoded font sizes in screens (Severity: 🟡)
- Issue #6: Component corner radius inconsistency across various cards and components (Severity: 🟡)



## Offline Requirements

- Authentication requires network connection to Supabase

- No offline authentication support currently

- Persona and profile saved locally after successful auth for subsequent use
