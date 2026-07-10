# Push Notification Implementation Summary

This report details the implementation of the push notification system for Baroness-kt, addressing the missing server-side triggers and client-side runtime permissions.

## Changes Implemented

### 1. Android Runtime Permissions
- **File:** `app/src/main/java/com/baroness/app/MainActivity.kt`
- **Action:** Added `POST_NOTIFICATIONS` runtime permission request for Android 13+ (API 33).
- **Details:** The app now checks for notification permissions on startup and uses a `ActivityResultLauncher` to request them if missing.

### 2. Supabase Edge Function: `notify-trigger`
- **File:** `supabase/functions/notify-trigger/index.ts`
- **Action:** Created/Updated the Edge Function to handle wish notifications.
- **Tech Stack:** Deno, Supabase SDK, `google-auth-library`.
- **Logic:**
    - Triggered by an `INSERT` event on `wishlist_items`.
    - Identifies the recipient persona (the one who didn't create the wish).
    - Fetches the recipient's FCM token from the `profiles` table.
    - Authenticates with Google FCM HTTP v1 API using service account credentials.
    - Sends a high-priority push notification with a deep-link route to the Wishlist.

### 3. Database Trigger
- **File:** `supabase/push_notifications.sql`
- **Action:** SQL script to create a PostgreSQL trigger.
- **Details:** 
    - Uses `pg_net` extension for asynchronous non-blocking calls.
    - Invokes the `notify-trigger` Edge Function whenever a new row is inserted into `wishlist_items`.

## Setup Instructions

### Step 1: Set Supabase Secrets
You must set the following secrets in your Supabase project for the Edge Function to authenticate with Firebase:

```bash
supabase secrets set FCM_PROJECT_ID="your-project-id"
supabase secrets set FCM_CLIENT_EMAIL="your-firebase-service-account-email"
supabase secrets set FCM_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

*Note: Ensure the private key includes the `\n` characters for line breaks.*

### Step 2: Deploy the Edge Function
Deploy the function using the Supabase CLI:

```bash
supabase functions deploy notify-trigger
```

### Step 3: Apply Database Trigger
Run the contents of `supabase/push_notifications.sql` in the Supabase SQL Editor. 
**Important:** Replace `YOUR_SERVICE_ROLE_KEY` in the script with your actual service role key to authorize the trigger to call the function.

## How to Test
1. **Grant Permissions:** Launch the app on an Android 13+ device/emulator and grant the notification permission when prompted.
2. **Add a Wish:** Add a new wish in the app.
3. **Verify:**
    - Check the Supabase Edge Function logs for "Processing INSERT" and "FCM Success".
    - The recipient device (logged in as the other persona) should receive a push notification titled " [Sender Name] added a new wish".
    - Tapping the notification should open the app and navigate to the Wishlist screen.

## Remaining Issues / Considerations
- **Environment Variables:** The database trigger currently requires the `service_role` key to be hardcoded or managed. For production, consider using a more secure way to inject this or making the function internal-only if the network configuration allows.
- **Deep Linking:** Ensure the `MainActivity` correctly handles the `route` extra from the FCM intent (implemented in `MainActivity.kt`).
