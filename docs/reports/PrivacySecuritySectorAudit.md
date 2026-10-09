# Privacy & Security Sector Audit

## 1. Executive Summary

A comprehensive forensic audit of the Privacy, Security, Authentication, Vault Gate, Session Management, and Data Protection subsystem in the Baroness application (`C:\Baroness_Core\Baroness-kt`) was performed across Android Kotlin source files, Android DataStore persistence layers, Supabase database schemas/policies, Edge Functions, and Node.js integration tests.

### Key Audit Findings:
1. **Vault Gate Authentication System**:
   - The application enforces a Vault Gate check on startup when no valid session is detected (`SessionManager.kt`).
   - The Gate UI (`GateScreen.kt` / `GateViewModel.kt`) prompts for a 6+ character secret key and persona selection (`phesty` vs `baroness`).
2. **Passkey Verification Mechanism**:
   - `AuthManager.kt` executes direct HTTP REST queries against Supabase: `GET /rest/v1/access_keys?id=eq.{persona}&select=secret_key` using the published Supabase anonymous API key.
   - It performs regex parsing (`extractSecretKey`) on the returned JSON payload to compare plaintext secret keys (`mr.nice_guy` for Phesty, `mrs.nice_guy` for Baroness).
3. **Supabase RLS & Passkey Security Gap**:
   - Migration `024_friday_v1_app_compat_rls.sql` currently grants `CREATE POLICY "Allow public select on access_keys" ON public.access_keys FOR SELECT USING (true);`.
   - Consequently, any anonymous client possessing the published `SUPABASE_ANON_KEY` can directly query `access_keys` over HTTP and read plain-text passkeys.
   - A dedicated Edge Function (`supabase/functions/verify-passkey/index.ts`) already exists on Supabase. It uses the service-role key to validate passkeys server-side and mints an HS256 JWT, but `AuthManager.kt` on Android does not yet invoke this endpoint.
4. **Session & State Persistence**:
   - `SessionManager.kt` verifies authentication status by checking for the presence of `userProfile` and `vibe_persona` in DataStore (`StorageManager.kt`).
   - `UserSessionManager.kt` manages local persona ID (`current_user_id`) via `SharedPreferences` ("baroness_prefs").
   - Explicit logout (`ProfileSetupViewModel.onLogout()`) clears local session state (`vibe_persona`, `currentPersonaId`, `userProfile`, `fcm_token`) and nullifies remote `profiles.fcm_token` in Supabase, forcing a return to the Gate.
5. **Current Settings Surface State**:
   - `DrawerPrivacy.kt` renders static `"Coming soon..."` text inside a `GlassCategoryBox`.
   - `SettingsCenterScreen.kt` contains a category item `SettingsCategoryItem("privacy", "Privacy & Security", ...)`, but its `onClick` callback currently leaves `"privacy"` unhandled.
   - **Zero privacy/security preference keys currently exist in DataStore or `SettingsRepository.kt`.**
6. **Audit-Only Declaration**:
   - This task is strictly an audit and investigation. **Zero Kotlin source code, UI components, ViewModels, database schemas, or Edge Functions were modified or created.**

---

## 2. Privacy & Security Architecture

The application's security and session lifecycle is structured around the Vault Gate check and DataStore session tokens:

```text
                           PRIVACY & SECURITY ARCHITECTURE
                           
  APP LAUNCH / SESSION CHECK                       VAULT GATE AUTHENTICATION
      SessionManager.isLoggedIn()                     GateScreen.kt (Secret Key Input)
                  │                                               │
        ┌─────────┴─────────┐                                     ▼
     [True]              [False]                       GateViewModel.onGateSelected()
        │                   │                                     │
        ▼                   ▼                                     ▼
  DashboardScreen       GateScreen                      AuthManager.checkGate()
                                                                  │
  ┌───────────────────────────────────────────────────────────────┴───────────────────────────────┐
  │                                                                                               │
  │  CURRENT IMPLEMENTATION (Client-Side REST)        PREPARED ENDPOINT (Server-Side Edge)        │
  │  AuthManager.kt                                   supabase/functions/verify-passkey/index.ts  │
  │         │                                                         │                           │
  │         ▼ (HTTP GET /rest/v1/access_keys)                         ▼ (HTTP POST /verify-passkey)
  │  Supabase Postgrest API                           Supabase Edge Function                      │
  │         │                                                         │ (Service-Role bypass)     │
  │         ▼                                                         ▼                           │
  │  access_keys table (Public SELECT RLS)            access_keys table (Locked RLS)              │
  │         │                                                         │                           │
  └─────────┬─────────────────────────────────────────────────────────┴───────────────────────────┘
            │
            ▼
     Passkey Matched?
            │
     ┌──────┴──────┐
  [Success]     [Error]
     │             │
     ▼             ▼
  Save Session   Blink Error
  to DataStore   in Gate UI
  & Sync FCM
     │
     ▼
  DashboardScreen
```

### Authentication Lifecycle:
1. **Startup Check**: `AppEntryPoint` in `MainActivity.kt` calls `SessionManager.getStartDestination()`. If `userProfile` and `vibe_persona` are present in DataStore, the app opens `"dashboard"`; otherwise, it navigates to `"gate"`.
2. **Gate Input**: `GateScreen.kt` presents a blurred backdrop (`R.drawable.image_45`), asking for a 6+ character secret key and persona choice (`Phesty` or `Baroness`).
3. **Passkey Query**: `GateViewModel` delegates to `AuthManager.checkGate(persona, password)`. `AuthManager` executes an OkHttpClient HTTP GET request to Supabase REST endpoint `/rest/v1/access_keys?id=eq.{persona}&select=secret_key` with the anon API key.
4. **Local Session Minting**: On matching passkey, `GateViewModel` saves `vibe_persona`, `currentPersonaId`, and serialized JSON `userProfile` to DataStore via `StorageManager`, syncs FCM token to Supabase `profiles.fcm_token`, and navigates to the app dashboard or setup screen.
5. **Session Teardown**: Logout from `ProfileSettingsPage.kt` or `ProfileSetupScreen.kt` invokes `ProfileSetupViewModel.onLogout()`, clearing DataStore session keys and redirecting to `"gate"`.

---

## 3. Complete Code Inventory

The Privacy & Security domain spans **14 primary files, scripts, and edge functions**:

### 3.1 Android Kotlin Source Files
- **`app/src/main/java/com/baroness/app/screens/GateScreen.kt`**: Vault Gate UI rendering secret key input, visibility toggles, Phesty/Baroness persona entry buttons, and error blinking animation.
- **`app/src/main/java/com/baroness/app/viewmodels/GateViewModel.kt`**: ViewModel validating passkey length (>= 6 chars), invoking `AuthManager.checkGate()`, persisting session keys to DataStore, and initiating immediate FCM push token sync.
- **`app/src/main/java/com/baroness/app/modules/AuthManager.kt`**: Auth business module executing raw HTTP GET requests to Supabase REST API endpoints (`access_keys` and `profiles`) using OkHttpClient and regex JSON parsing.
- **`app/src/main/java/com/baroness/app/utils/SessionManager.kt`**: Helper evaluating `isLoggedIn()` by checking presence of `userProfile` and `vibe_persona` in DataStore.
- **`app/src/main/java/com/baroness/app/utils/UserSessionManager.kt`**: SharedPreferences wrapper managing `current_user_id` (`phesty_official` vs `baroness_official`) and `current_user_key`.
- **`app/src/main/java/com/baroness/app/utils/StorageManager.kt`**: Authoritative `PreferencesDataStore` wrapper ("baroness_prefs") storing session keys, profile JSON, and preferences.
- **`app/src/main/java/com/baroness/app/config/SupabaseConfig.kt`**: Global object storing `SUPABASE_URL` and hardcoded `SUPABASE_ANON_KEY`.
- **`app/src/main/java/com/baroness/app/components/DrawerPrivacy.kt`**: Accordion drawer component in `GlobalDrawer.kt` rendering static `"Coming soon..."` text.
- **`app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt`**: Discovery Hub listing `SettingsCategoryItem("privacy", "Privacy & Security", ...)`.

### 3.2 Supabase Edge Functions & SQL Migrations
- **`supabase/functions/verify-passkey/index.ts`**: Supabase Deno Edge Function taking `{ persona, passkey }`, checking `access_keys` using service-role key, fetching profile, and minting 1-year HS256 JWT containing `persona` claim.
- **`supabase/migrations/024_friday_v1_app_compat_rls.sql`**: Migration granting `Allow public select on access_keys` for temporary app compatibility.
- **`supabase/migrations/023_friday_v1_rls_jwt_policies.sql` & `020_friday_v1_security_p0.sql`**: Migrations defining RLS policies and passkey lockdown.
- **`supabase/schema.sql` & `supabase/policies.sql`**: Core database schema definition for `access_keys` (`id text primary key`, `secret_key text`, `created_at timestamptz`).

### 3.3 Integration Tests
- **`tests/authmanager-gate.test.ts`**: Node.js integration test script validating passkey queries against `access_keys` and `profiles` for `phesty` (`mr.nice_guy`) and `baroness` (`mrs.nice_guy`).

---

## 4. Existing Drawer & Settings Surface Audit

- **File**: `app/src/main/java/com/baroness/app/components/DrawerPrivacy.kt`
- **Host**: Accordion card in `GlobalDrawer.kt`.
- **Current UI**: Renders static text `"Coming soon..."` inside a `GlassCategoryBox`.
- **State Read**: None.
- **State Written**: None.
- **Persistence Key**: None.
- **Settings Center Navigation**: `SettingsCenterScreen.kt` includes `SettingsCategoryItem("privacy", "Privacy & Security", "Vault Gate Security, Session Data", Icons.Default.Security)`. However, `CategoryCard.onClick` currently omits a handler for `"privacy"`.
- **Audit Conclusion**: Zero privacy/security preference keys or setting screens exist today.

---

## 5. Passkey & Access Key Security Detailed Audit

### 5.1 Plaintext Passkey Storage
- The `access_keys` table stores secret keys in plaintext (`mr.nice_guy` for phesty, `mrs.nice_guy` for baroness).
- No hashing (e.g. bcrypt, Argon2) or salt is applied in the database layer.

### 5.2 Public Read Access Gap (`024_friday_v1_app_compat_rls.sql`)
- In migration `024_friday_v1_app_compat_rls.sql`, public SELECT access was enabled:
  ```sql
  CREATE POLICY "Allow public select on access_keys" ON public.access_keys FOR SELECT USING (true);
  ```
- Because `AuthManager.kt` queries `access_keys` directly over the public REST API, this policy was required so the app wouldn't fail login.
- **Security Vulnerability**: Any actor with the public `SUPABASE_ANON_KEY` can execute `curl -H "apikey: ANON_KEY" https://{project}.supabase.co/rest/v1/access_keys` and retrieve all secret keys.

### 5.3 Prepared Remediation Path (`verify-passkey` Edge Function)
- The `verify-passkey` Edge Function (`supabase/functions/verify-passkey/index.ts`) resolves this exact gap:
  1. Accepts POST payload `{ persona, passkey }`.
  2. Uses `SUPABASE_SERVICE_ROLE_KEY` to query `access_keys` server-side (bypassing RLS).
  3. Mints a secure JWT token containing the `persona` claim upon successful verification.
- **Required Action for Phase 7**: Update `AuthManager.kt` to POST to `https://{project}.supabase.co/functions/v1/verify-passkey`, allowing `access_keys` public SELECT policy to be revoked (`DROP POLICY "Allow public select on access_keys"`).

---

## 6. Session & Data Persistence Audit

### 6.1 DataStore Keys (`baroness_prefs`)
The privacy and session system currently relies on the following DataStore keys:

| DataStore Key | Type | Stored Content | Persistence Scope |
| :--- | :--- | :--- | :--- |
| `vibe_persona` | String | `"phesty"` or `"baroness"` | Session active |
| `currentPersonaId` | String | `"phesty_official"` or `"baroness_official"` | Session active |
| `userProfile` | String (JSON) | Serialized `UserProfile` (`displayName`, `avatarUrl`, `persona`, `personaId`) | Session active |
| `fcm_token` | String | Firebase Cloud Messaging device push token | Device active |

### 6.2 Session Lifecycle
- **Gate Login**: `GateViewModel` -> `AuthManager.checkGate()` -> writes `vibe_persona`, `currentPersonaId`, `userProfile` -> `SessionManager.isLoggedIn()` evaluates `true`.
- **Active Navigation**: `SessionManager.getStartDestination()` returns `"dashboard"`.
- **Session Teardown**: `ProfileSetupViewModel.onLogout()` removes `fcm_token`, `vibe_persona`, `userProfile`, `currentPersonaId`, clears remote `profiles.fcm_token` in Supabase, and redirects to `"gate"`.

---

## 7. Truthful Scope & Blueprint for Phase 7 Implementation

When Phase 7 UI implementation is authorized, `PrivacySettingsPage.kt` must contain **ONLY truthful settings and session controls**.

### 7.1 Truthful Controls to Include in `PrivacySettingsPage.kt`:
1. **Vault Gate Security Status**:
   - Card displaying Vault Gate protection status (`Gate Protected`).
   - Active assigned persona (`Phesty` / `Baroness`).
2. **Active Session Details**:
   - Card displaying logged-in profile display name, persona ID, and FCM registration status.
   - Session DataStore key status indicator.
3. **Session Data Teardown & Reset**:
   - Outlined action button to clear local session cache (`userProfile`, `vibe_persona`) and force re-authentication at the Gate.
4. **Backend Security Endpoint Migration**:
   - Refactor `AuthManager.kt` to invoke `verify-passkey` Edge Function, allowing public SELECT RLS policy on `access_keys` to be safely dropped.

### 7.2 Untruthful Controls NOT to Invent:
- **Do NOT add**:
  - Biometric / Fingerprint Unlock toggles (until `BiometricPrompt` API is integrated).
  - Passkey Change / Password Reset form (until a secure backend RPC/Edge Function exists to update `access_keys`).
  - False Database Encryption / Vault Cipher switches.
  - False Data Retention / Auto-Delete timers.

---

## 8. Requirements Checklist for Phase 7 Implementation

When Phase 7 UI construction begins, the following deliverables will be required:

- [ ] Create `app/src/main/java/com/baroness/app/screens/settings/PrivacySettingsPage.kt`.
- [ ] Implement Layer 2 Settings category architecture with `MaterialTheme.colorScheme.background`.
- [ ] Connect `"privacy"` navigation in `SettingsCenterScreen.kt` (`CategoryCard.onClick`).
- [ ] Register `composable("settings/privacy")` in `MainActivity.kt` (`AppNavigation`).
- [ ] Migrate `AuthManager.kt` from direct REST select to `verify-passkey` Edge Function POST request.
- [ ] Add session data clearing / re-gate action with confirmation dialog.
- [ ] Document changes in `docs/reports/Phase7PrivacyImplementation.md`.
- [ ] Verify build with `./gradlew app:compileDebugKotlin`.

---

## 9. Final Status

```text
PRIVACY & SECURITY AUDIT: COMPLETE
VAULT GATE SYSTEM: AUDITED & DOCUMENTED
PASSKEY REST SECURITY GAP: AUDITED & DOCUMENTED
VERIFY-PASSKEY EDGE FUNCTION: AUDITED & DOCUMENTED
SESSION PERSISTENCE: AUDITED & DOCUMENTED
SOURCE CODE MODIFICATIONS: ZERO (AUDIT ONLY)
```
