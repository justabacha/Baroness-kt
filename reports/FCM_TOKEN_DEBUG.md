# FCM Token Debug Report

## Findings

### 1. Persona ID Mismatch (Critical)
The app currently stores the selected persona in two storage keys:
- `vibe_persona`: Stores "phesty" or "baroness".
- `currentPersonaId`: Stores "phesty_official" or "baroness_official".

The `profiles` table in Supabase uses the `_official` format for the `id` column. However, both `FCMService.onNewToken()` and `MainApplication.registerFcmToken()` are using the `vibe_persona` key to fetch the ID for the `updateFcmToken` call.

**Impact:** The Supabase patch request is sent to `/rest/v1/profiles?id=eq.phesty`. Since the database contains `phesty_official`, the query matches zero rows and the token is never updated.

### 2. Missing Token Sync after Login/Setup
FCM tokens are typically generated at app startup. If the user is not yet logged in, the persona ID is null, and the token sync is skipped.
Currently, neither `GateViewModel` nor `ProfileSetupViewModel` trigger a token sync after the persona is established. The token is only pushed to Supabase if the app is restarted *after* a successful login.

### 3. RLS Policy Verification
The RLS policies in `policies.sql` for the `profiles` table are very permissive:
`ALL: "Allow public access to profiles" (PERMISSIVE, public, qual: true, with_check: true)`
This means RLS is likely **not** blocking the updates, even with the anon key.

### 4. Lack of Visibility
`ProfileManager.updateFcmToken()` currently swallows details on failure (only throws the response code) and provides no logs on success. This makes it impossible to verify what was sent and what the server returned.

---

## Fix Plan

### Step 1: Fix ID Mismatch
Update `FCMService.kt` and `MainApplication.kt` to fetch the persona ID using the `currentPersonaId` key instead of `vibe_persona`.

### Step 2: Add Logging to ProfileManager
Modify `ProfileManager.updateFcmToken()` to log:
- The target URL.
- The JSON payload.
- The HTTP response code.
- The response body (especially on error).

### Step 3: Trigger Sync on Login/Setup
- **GateViewModel.kt:** After saving `currentPersonaId` to storage, retrieve the cached `fcm_token` and call `ProfileManager.updateFcmToken()`.
- **ProfileSetupViewModel.kt:** After a successful profile save, retrieve the cached `fcm_token` and call `ProfileManager.updateFcmToken()`.

### Step 4: Verification
Verify in Logcat that `ProfileManager` reports a `204 No Content` (standard for Supabase PATCH) or `200 OK`, and check the Supabase dashboard to confirm the `fcm_token` column is populated.
