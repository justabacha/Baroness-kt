# Phase 2 — Client-Side JWT Propagation (Kotlin Android) Report

## Executive Summary

Phase 2 (Client-Side JWT Propagation) has been successfully implemented and verified for the Baroness Android application. The active persona JWT stored in DataStore under key `"auth_token"` is now attached to all Supabase-bound network traffic across PostgREST, Storage, Edge Functions, direct OkHttp calls, and the Realtime WebSocket handshake.

End-to-end verification was performed against the live Supabase infrastructure using the Phase 1 deployed `friday-pending-messages` Edge Function probe:
- **Unauthenticated Probe Request (No Token):** HTTP **401 Unauthorized**
- **Authenticated Probe Request (Through `AuthTokenInterceptor` with active persona JWT):** HTTP **200 OK**

No RLS policies, database migrations, Edge Functions, or login/logout UI components were modified.

---

## 1. Discovered HTTP & Network Paths & Coverage Summary

| Path / Subsystem | Transport / Client | Discovery Location | Coverage Mechanism |
| :--- | :--- | :--- | :--- |
| **PostgREST Queries** | `supabase-kt` (Ktor Engine) | `ChatApi.kt`, `WishlistApi.kt`, `ChatRepository.kt`, `WishlistRepository.kt`, `ChatListViewModel.kt`, `ChatSyncWorker.kt` | Configured `accessToken = { SessionTokenCache.getToken() }` on `createSupabaseClient` in `SupabaseConfig.kt`. |
| **Storage Operations** | `supabase-kt` (Ktor Engine) | `BackupManager.kt` (`chat_backups` bucket) | Configured `accessToken = { SessionTokenCache.getToken() }` on `createSupabaseClient` in `SupabaseConfig.kt`. |
| **Realtime WebSocket Handshake & Auth** | `supabase-kt` Realtime Plugin | `ChatRepository.kt`, `WishlistRepository.kt` | `Realtime.Config` resolves token via `SupabaseClient.resolveAccessToken()`, passing `access_token` in `phx_join` payload. `SessionTokenCache` triggers `supabase.realtime.setAuth(token)` on token changes. |
| **Edge Function Calls** | Direct OkHttp | `AuthManager.kt` (`/functions/v1/verify-passkey`) | `AuthManager` uses `SupabaseConfig.client` with `AuthTokenInterceptor`. (Bypasses token overwrite for `verify-passkey` to allow initial unauthenticated gate check). |
| **Profile REST & Avatar Storage** | Direct OkHttp | `ProfileManager.kt` (`/rest/v1/profiles`, `/storage/v1/object/avatars/*`) | Updated `ProfileManager` to use `SupabaseConfig.client` with `AuthTokenInterceptor`, replacing hardcoded anon key headers with `Authorization: Bearer <token>`. |
| **OTA Release Check** | Direct OkHttp | `PhestyDropManager.kt` (`phestydrop_releases`) | Uses `SupabaseConfig.client` with `AuthTokenInterceptor`. |
| **External Non-Supabase APIs** | Ktor / OkHttp | `GroqApiService.kt` (Groq API), `QuoteRepository.kt` (ZenQuotes) | Interceptor checks target URL host against `SUPABASE_URL` host. Non-Supabase URLs remain untouched. |

---

## 2. Token Injection Mechanism & Architecture

### 2.1 Threading Constraint Resolution
OkHttp's `Interceptor.intercept()` is a blocking method, whereas DataStore reads are asynchronous `suspend` functions. Calling `runBlocking` inside an OkHttp interceptor can cause thread starvation and deadlocks on network threads.

**Resolution:**
1. Created `SessionTokenCache` (`com.baroness.app.auth.SessionTokenCache`) as a thread-safe singleton with a `@Volatile var cachedToken: String?`.
2. Initialized `SessionTokenCache.init(context)` in `MainApplication.onCreate()`.
3. `SessionTokenCache` observes `StorageManager.getStringFlow("auth_token")` on `Dispatchers.IO` using a supervisor coroutine scope. Whenever `"auth_token"` changes in DataStore, `cachedToken` is updated in memory immediately.
4. OkHttp interceptors and `supabase-kt` token callbacks read `SessionTokenCache.getToken()` synchronously without suspending or blocking.

### 2.2 Token Interceptor Implementation (`AuthTokenInterceptor`)
`AuthTokenInterceptor` (`com.baroness.app.auth.AuthTokenInterceptor`) is attached to `SupabaseConfig.client`:
- Verifies if the request URL host matches `SUPABASE_URL` host (`wckluymkbqxdmipzaiff.supabase.co`).
- Checks if a valid non-empty token exists in `SessionTokenCache`.
- If both conditions are met (and URL is not `/functions/v1/verify-passkey`), attaches `Authorization: Bearer <token>`.
- Preserves the `apikey` header (`apikey: <SUPABASE_ANON_KEY>`) intact.
- Never logs or exposes token values in debug output.

---

## 3. Realtime WebSocket Authentication

`supabase-kt:3.1.1`'s `Realtime` plugin obtains its access token by invoking `SupabaseClient.resolveAccessToken()`, which calls the `accessToken = { SessionTokenCache.getToken() }` provider configured on `createSupabaseClient`.

When joining channels, `realtime-kt` automatically includes `"access_token": "<jwt>"` in the `phx_join` payload. Furthermore, when `SessionTokenCache` detects a token change (or logout), it invokes `SupabaseConfig.supabase.realtime.setAuth(cachedToken)` to push the token update across active channels.

---

## 4. Logout & Invalidation Safety

On logout, session clearing is performed in `ProfileSetupViewModel.kt` and `PrivacySettingsPage.kt`:
1. `storageManager.remove("auth_token")` removes the key from DataStore.
2. `SessionTokenCache.clear()` is called immediately to set `cachedToken = null` and invoke `realtime.setAuth(null)`.
3. The DataStore flow collector receives `null` and confirms the cache is invalidated, preventing stale JWT attachment after logout.

---

## 5. Test Results & Verification

### 5.1 Automated Unit & Integration Test Suite
Executed test command:
```cmd
./gradlew :app:testDebugUnitTest
```

**Results:**
- `11 passed, 0 skipped, 0 failed`

**Test Cases Covered (`AuthTokenInterceptorTest.kt`):**
1. `testCacheSetGetAndClear`: Verified setting, reading, and clearing token in `SessionTokenCache`.
2. `testInterceptorModifiesSupabaseRequestHeader`: Verified `AuthTokenInterceptor` attaches `Authorization: Bearer <token>` to Supabase URLs.
3. `testInterceptorDoesNotModifyNonSupabaseRequest`: Verified requests to external hosts (e.g. `api.groq.com`) do not receive the JWT.
4. `testInterceptorPreservesApikeyHeader`: Verified `apikey` header is untouched.
5. `testLiveFridayEndpointProbe_UnauthenticatedReturns401`: Remote probe against live endpoint without token returned **HTTP 401**.
6. `testLiveFridayEndpointProbe_WithValidTokenReturns200`: Remote probe against live endpoint after logging in via `verify-passkey` returned **HTTP 200**.

### 5.2 Build Verification
Executed build command:
```cmd
./gradlew :app:assembleDebug
```
**Results:** `BUILD SUCCESSFUL`

---

## 6. Scope Restrictions & Non-Modifications Confirmation

- **RLS Policies:** No database RLS policies were modified or enabled (permissive policies remain active for Phase 2).
- **Edge Functions:** Zero Edge Functions were modified in this phase.
- **Login / Logout UI:** UI flows and layout logic were untouched.
- **Dependencies:** No new third-party libraries were added.
- **Schema & DTOs:** No Room database entities or network DTO shapes were changed.

---

## 7. Discovered — Out of Scope

1. **`UserSessionManager` Legacy SharedPreferences (F-06):** `UserSessionManager` maintains `currentUserId` in XML `SharedPreferences` ("baroness_prefs") alongside DataStore. Phase 4 will handle unifying session state.
2. **WorkManager Job Identity Binding:** `ChatSyncWorker` enqueues background sync work without explicitly binding worker execution context to the active DataStore persona. Phase 4 will handle background identity binding.

---

## 8. Recommended Next Phase

Proceed to **Phase 3 — Strict RLS Policy Enforcement & Database Migration**, where permissive database RLS policies will be replaced with strict persona-scoped policies.
