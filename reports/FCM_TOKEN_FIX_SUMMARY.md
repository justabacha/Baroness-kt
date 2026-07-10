# FCM Token Fix Summary

## Changes Implemented

### 1. Storage Key Correction
Modified `FCMService.kt` and `MainApplication.kt` to use the correct storage key `currentPersonaId` instead of `vibe_persona`.
- **Reason:** The database uses the `_official` ID format (e.g., `phesty_official`), which is stored under `currentPersonaId`. Using `vibe_persona` resulted in zero rows being updated because it only contained "phesty" or "baroness".

### 2. Enhanced Debug Logging
Added detailed logging to `ProfileManager.updateFcmToken()`:
- Logs the target URL.
- Logs the JSON payload.
- Logs the HTTP response code.
- Logs the full response body (even on success/204).
- **Benefit:** Allows for immediate verification of Supabase PATCH requests in Logcat.

### 3. Immediate Token Sync on Identity Establishment
Integrated FCM token synchronization into the app's critical path:
- **GateViewModel.kt:** After a successful gate selection/login, the app now retrieves the cached `fcm_token` and immediately pushes it to Supabase using the new `currentPersonaId`.
- **ProfileSetupViewModel.kt:** After a user saves their profile (name/avatar), a token sync is triggered to ensure the association between the token and the persona remains fresh.

### 4. Verification Guide
To verify these changes in Logcat:
1. Filter by tag `ProfileManager`.
2. Look for `Updating FCM Token for ...`.
3. Confirm the URL contains `id=eq.phesty_official` or `id=eq.baroness_official`.
4. Confirm `Response Code: 204` (Supabase successful PATCH).

The FCM token is now correctly associated with the database record and synced whenever the user's identity is established or updated.
