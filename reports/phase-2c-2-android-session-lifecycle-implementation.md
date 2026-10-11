# Phase 2C-2 — Android Auth Session Lifecycle Implementation

## Summary

Implemented Android-side persistence and renewal for Supabase Auth sessions. Login now stores the access token, refresh token, and absolute expiry; refresh is single-flight, renews the rotated session in DataStore and Realtime, and is triggered proactively on resume or reactively on direct-client 401s. Supabase SDK traffic uses its suspend `accessToken` callback for an expired-token refresh. Unrecoverable refresh failures clear local session state and route the app to GateScreen.

Explicit logout now attempts server revocation, disconnects Realtime, cancels background work, wipes the approved user-scoped Room tables, clears DataStore and SharedPreferences, and clears the in-memory cache. The two logout entry paths share the same lifecycle manager.

## Mandatory Inspection Findings

- `AuthManager.kt` manually parses `verify-passkey` JSON with `JSONObject`. It consumed `token`, persona, and profile, but previously discarded `refresh_token` and `expires_in`.
- `StorageManager.kt` uses Preferences DataStore, exposes individual string and long accessors, and previously had no session-level multi-key save or clear operation.
- `SessionTokenCache.kt` originally collected only `auth_token` asynchronously, had no readiness barrier, expiry, refresh token, or refresh path, and asynchronously called `realtime.setAuth`.
- `SupabaseConfig.kt` creates the supabase-kt client with `accessToken` and a separate OkHttp client for direct requests. The SDK callback was synchronous in behavior; it now suspends for readiness/expired-token refresh. The direct OkHttp client uses `AuthTokenInterceptor`.
- `AuthTokenInterceptor.kt` originally attached a cached access token but did not react to 401 responses.
- Logout is invoked from `ProfileSetupScreen` and `ProfileSettingsPage` through `ProfileSetupViewModel.onLogout`, and separately from the session-reset action in `PrivacySettingsPage`. Both routes now use `SessionLifecycleManager`.
- `MainApplication.onCreate()` initializes `SessionTokenCache`. A process lifecycle observer now attempts proactive renewal on resume. `MainActivity` observes the re-auth state and navigates to GateScreen.
- `AppDatabase.kt` declares six Room entities/tables: `wishes`, `reactions`, `ratings`, `sync_queue`, `messages`, and `clock_items`. The approved five user-session tables are wiped on logout; `clock_items` is intentionally retained as it is not in the requested user-scoped wipe set.
- `ChatSyncWorker` is enqueued under unique work name `ChatSync`, but has no tag. `SyncWorker` is enqueued both as untagged one-time work and unique periodic work named `periodic_wishlist_sync`; it has no tag. No worker tags are applied.
- No material discrepancy from the preflight description was found for token handling, SDK/OkHttp separation, or refresh response shape. The WorkManager inspection established that tag-based cancellation is not available for all relevant work.

## Locked Decisions and Implementation

1. **Proactive refresh:** Threshold remains **60 minutes**. The process `ON_RESUME` observer reads cached absolute expiry and calls `refreshIfNeeded(force = true)` only when expiry is within that window and a refresh token exists. A successful login also performs the same one-time threshold check.
2. **Room wipe:** Explicit logout clears `messages`, `wishes` (the local wishlist items table), `reactions`, `ratings`, and `sync_queue`. Pending unsynced local work is intentionally discarded on explicit logout. `clock_items` is preserved.
3. **WorkManager cancellation:** Uses `WorkManager.cancelAllWork()` because `ChatSyncWorker`/`SyncWorker` do not have tags, and SyncWorker covers both unique periodic and untagged one-time work. `cancelAllWork()` may also cancel unrelated WorkManager work if added elsewhere in future.

## Session Renewal Mechanics

- Login persists `auth_token`, `refresh_token`, and `token_expires_at = now + expires_in * 1000` through a single DataStore edit. If either refresh token or expiry is absent, a warning is logged and login still succeeds without automatic refresh.
- `SessionTokenCache` holds access token, refresh token, and absolute expiry, with a coroutine `Mutex` guarding refresh. Successful refresh parses `access_token`, rotated `refresh_token`, and `expires_in`, persists the new session, updates the cache, calls `realtime.setAuth(newToken)`, and returns the new token.
- Refresh uses the project OkHttp configuration with explicit anon `apikey` and anon bearer headers, posting the refresh token to `/auth/v1/token?grant_type=refresh_token`. A request tag bypasses the direct interceptor's 401 retry, preventing recursion.
- A non-forced refresh returns a token with more than a 60-second safety margin. Network errors preserve the session and return the access token only while it is not expired. Unrecoverable 400/401 refresh responses clear the local session and signal re-authentication. No token values are logged.
- The Supabase SDK `accessToken` callback returns a non-expired cached token without network activity. It calls forced refresh only when the token is expired. Direct OkHttp Supabase requests attach the cached token, then attempt one forced refresh/retry after a 401 if that request had Authorization. The retry is not repeated.
- On unrecoverable refresh, `SessionTokenCache` asks the shared lifecycle manager to clear local session state and emits a `StateFlow` re-auth signal. `AppNavigation` observes it, performs the local teardown, and navigates to GateScreen from the Compose main scope.
- **Cold-start race fix: Option B.** `SessionTokenCache` asynchronously loads the initial DataStore snapshot on `Dispatchers.IO`, then continues collecting changes. SDK callback and direct interceptor await the readiness barrier before reading the cache. No `runBlocking` is used at application startup and the main thread is not blocked.

## Logout Ordering

Both explicit logout routes use this sequence:

1. Best-effort `POST /auth/v1/logout` with the current access token and anon `apikey`, using a separate OkHttp client with a five-second call timeout.
2. Disconnect Realtime.
3. Cancel all WorkManager work and await cancellation completion.
4. Clear the five approved user-scoped Room tables.
5. Clear the seven requested DataStore session keys.
6. Clear `UserSessionManager` SharedPreferences.
7. Clear `SessionTokenCache` and set Realtime auth to null.
8. Navigate to GateScreen.

Each cleanup operation logs failure and continues to local teardown; navigation runs in the caller's `finally` path. Background ChatSync and Wishlist sync enqueue paths now check for a persisted access token before scheduling more work, and periodic wishlist work is registered only when startup resolves to the dashboard.

## Test Results

Command run:

```text
gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.baroness.app.SessionTokenCacheTest --tests com.baroness.app.AuthTokenInterceptorRetryTest
```

**Actual result:** `BUILD SUCCESSFUL`; Android Kotlin compilation passed and all 9 selected unit tests passed.

| Test | Result | Evidence |
|---|---|---|
| Valid token, non-forced refresh makes no network call | Passed | 0 refresh calls; cached token returned |
| Expired token refresh updates cache and rotated credentials | Passed | One refresh call; new access/refresh tokens and future expiry cached |
| Concurrent forced refresh calls are single-flight | Passed | Two callers received the new access token; one network call |
| `invalid_grant` requests re-auth and clears cached session | Passed | Null returned, cache cleared by test re-auth handler, re-auth state set |
| Network failure preserves valid token | Passed | Current token returned and retained |
| Network failure with expired token returns null without clearing cached credentials | Passed | Null returned; expired token remains cached and no re-auth was signaled |
| Interceptor attaches cached token | Passed | Downstream request received `Authorization: Bearer cached-token` |
| Interceptor retries once after 401 using refreshed token | Passed | Two attempts, one refresh, retry returned 200 |
| Interceptor does not retry a second 401 | Passed | Two attempts total, one refresh, final 401 returned |

These are local unit tests using a refresh-call test override; they do not establish that a live Supabase refresh exchange or DataStore persistence works on a device.

## Live / Device Verification

No Android device or emulator was attached (`adb devices` returned only the device-list header). Therefore the requested manual checks could not be performed and are **not claimed as verified**:

| Requested check | Result |
|---|---|
| Login and confirm refresh token/expiry are present in DataStore | Not run; no device available |
| Cold start and confirm no unauthorized requests | Not run; no device/logcat available |
| Wait for 60-minute window, resume, and observe live refresh | Not run; requires a live session/device and elapsed time |
| Expired-token callback against a live request | Not run; no device available |
| Invalid refresh token clears session and returns to GateScreen | Local invalid-grant unit path passed; UI/device behavior not live-verified |
| Logout confirms Auth revocation, Realtime disconnect, worker cancellation, and empty Room tables | Not run; no device/Auth log access |
| Offline logout still returns to GateScreen | Not run; no device/network simulation |

No tokens, refresh tokens, passkeys, or Authorization headers were printed. No live Auth/session request was made.

## Scope and Follow-Up

- No Supabase function, migration, Auth setting, or RLS policy was changed.
- The login UI, GateScreen, passkey flow, DTOs, entity schemas, channel topics, and subscription behavior were not changed.
- No Phase 3 work was performed.
- **Discovered — Out of Scope:** `clock_items` is a sixth Room table but is not part of the requested logout wipe set; it remains on-device across logout. Validate ownership expectations separately before changing that behavior.
- **Known limitation:** Live/device verification is outstanding. In particular, no physical/emulated runtime confirmed token persistence, live refresh, HTTP revocation, Room clearing, or navigation after invalid refresh. Refresh response parsing and control flow have local unit coverage, but DataStore writes are not exercised by those tests.
- **Known limitation:** If a login response omits `expires_in`, expiry cannot be computed and proactive/expired-token detection cannot reliably determine access-token expiration. The client logs the missing session field and retains compatibility by allowing login.

## Files Changed

- `app/src/main/java/com/baroness/app/MainActivity.kt`
- `app/src/main/java/com/baroness/app/MainApplication.kt`
- `app/src/main/java/com/baroness/app/auth/AuthTokenInterceptor.kt`
- `app/src/main/java/com/baroness/app/auth/SessionLifecycleManager.kt`
- `app/src/main/java/com/baroness/app/auth/SessionTokenCache.kt`
- `app/src/main/java/com/baroness/app/config/SupabaseConfig.kt`
- `app/src/main/java/com/baroness/app/data/local/dao/MessageDao.kt`
- `app/src/main/java/com/baroness/app/data/local/dao/RatingDao.kt`
- `app/src/main/java/com/baroness/app/data/local/dao/ReactionDao.kt`
- `app/src/main/java/com/baroness/app/data/local/dao/SyncQueueDao.kt`
- `app/src/main/java/com/baroness/app/modules/AuthManager.kt`
- `app/src/main/java/com/baroness/app/repository/ChatRepository.kt`
- `app/src/main/java/com/baroness/app/repository/WishlistRepository.kt`
- `app/src/main/java/com/baroness/app/screens/settings/PrivacySettingsPage.kt`
- `app/src/main/java/com/baroness/app/utils/StorageManager.kt`
- `app/src/main/java/com/baroness/app/viewmodels/ProfileSetupViewModel.kt`
- `app/src/test/java/com/baroness/app/AuthTokenInterceptorRetryTest.kt`
- `app/src/test/java/com/baroness/app/SessionTokenCacheTest.kt`
