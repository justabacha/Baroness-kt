# Phase 2C-3 — Realtime Channel Lifecycle Implementation

## Summary

Implemented the SDK-source-evidenced subscription restart path (Approach B). Successful access-token refreshes still update Realtime auth with `setAuth(newToken)`, and now also emit `SessionTokenCache.tokenRefreshedFlow`. `ChatRepository` listens for that event, cancels and joins the existing sync-pipe subscription job, then starts a fresh subscription for the current persona.

Removed the 5-second override that falsely set `_isSubscribed` to true. Its value is now true only when the SDK reports `RealtimeChannel.Status.SUBSCRIBED`.

## SDK Evidence and Approach Selection

The installed dependency is `io.github.jan-tennert.supabase:realtime-kt:3.1.1`. Its cached source archive was inspected at `realtime-kt-android-debug-3.1.1-sources.jar`:

- `commonMain/io/github/jan/supabase/realtime/Realtime.kt` declares `suspend fun connect()` and non-suspending `fun disconnect()`.
- In `RealtimeImpl.kt`, public `connect()` delegates to `connect(false)`. `connect(reconnect)` returns immediately when already `CONNECTED`; `rejoinChannels()` is called only when `reconnect == true`.
- `disconnect()` closes the websocket and sets transport status to `DISCONNECTED`; it does not clear the subscriptions map.
- The internal reconnect path calls `connect(true)`, which rejoins retained channels. A caller's `disconnect()` followed by public `connect()` would instead use `connect(false)` and does not explicitly rejoin them.
- `setAuth(token)` updates the Realtime access token and schedules `updateAuth` on currently subscribed channels. This is retained, but is not a substitute for restarting the affected sync-pipe subscription in the audited failure scenario.

Therefore Approach A was refuted by the installed SDK implementation and was not added. The user directed that Approach B be the primary fix; no attempt was made to live-test Approach A.

## Implementation Details

- Added a buffered, read-only `SharedFlow<Unit>` named `tokenRefreshedFlow` to `SessionTokenCache`. It emits after a successful refresh updates the cached token and invokes `realtime.setAuth(newToken)`.
- Added a `ChatRepository` collector for the refresh signal. It reads the current persona from DataStore and restarts `chat_sync_pipe_$personaId`; the existing persona-flow subscription remains responsible for initial setup and identity changes.
- The restart cancels and joins the prior sync-pipe job before starting another, allowing the old job's `finally` cleanup to unsubscribe before the replacement subscribes.
- Channel state uses the installed SDK enum: `UNSUBSCRIBED`, `SUBSCRIBING`, `SUBSCRIBED`, and `UNSUBSCRIBING`. Only `SUBSCRIBED` maps to `_isSubscribed = true`; all other statuses map to false.
- Removed the 5-second timeout that reported a stuck `JOINING` channel as healthy. A code comment explains that only `SUBSCRIBED` represents a live channel.
- No disconnect/connect call is made on refresh.

## Tests

Command:

```text
gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.baroness.app.SessionTokenCacheTest --tests com.baroness.app.AuthTokenInterceptorRetryTest --no-daemon
```

**Result:** `BUILD SUCCESSFUL`; Kotlin compilation passed and all 10 selected unit tests passed (7 session-cache tests and 3 interceptor tests). The added Phase 2C-3-specific test verifies that a successful token refresh emits the refresh signal. Existing selected tests cover single-flight refresh, invalid-grant handling, and interceptor behavior.

The 5-second status rule was verified by code inspection and the production build; there is no isolated unit test for `ChatRepository` channel-state collection.

## Live Verification — Pending

The required three-cycle, 60-second access-token test has **not** been run. Per the operator's instruction, implementation pauses until their Android phone is connected over USB with USB debugging enabled and visible to `adb devices`. No claim is made about live message continuity, WebSocket/channel logs, reconnect flicker duration, app-resume behavior, or crash-free operation.

The next verification must be performed on the connected phone with the HumanChat screen foregrounded for at least three refresh cycles, sending messages from Baroness's device. Record observed timestamps, message continuity, any "Connecting to live chat..." intervals, and logcat errors without exposing credentials. Restore the hosted token lifetime to 604800 seconds and log out/in again after the test, as specified by the operator's test procedure.

## Scope and Discovered Items

- No server-side code, Supabase functions, migrations, RLS policies, or Auth settings were changed.
- No dependencies were added.
- `WishlistRepository` has its own Realtime channels. They continue to receive the SDK's `setAuth` channel update; this phase only restarts ChatRepository's sync-pipe subscription, as directed. Their post-refresh behavior has not been live-verified.
- The separate `subscribeToConversation` broadcast channels are not explicitly restarted here; the SDK's `setAuth` path sends their subscribed channels an auth update. The required live test should confirm human-chat behavior end-to-end.
- **Known limitation:** The UI's reported subscribed state tracks the sync-pipe channel, not individual conversation broadcast channels.

## Files Changed

- `app/src/main/java/com/baroness/app/auth/SessionTokenCache.kt`
- `app/src/main/java/com/baroness/app/repository/ChatRepository.kt`
- `app/src/test/java/com/baroness/app/SessionTokenCacheTest.kt`
- `reports/phase-2c-3-realtime-channel-lifecycle-implementation.md`
