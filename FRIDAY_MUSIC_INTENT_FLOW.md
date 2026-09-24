# FRIDAY Music Intent Flow Diagnostic

## Summary

The FRIDAY music command path is implemented locally after a realtime `COMMAND`
event arrives. `ChatRepository` receives the event from the Supabase realtime
pipe, converts `parameters` into a Kotlin map, and dispatches on the main
thread to `LocalCommandExecutor`. `MediaCommandHandler` then launches an
Android activity using `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`.

For a nonblank request such as `play Juice WRLD`, the handler reports success as
soon as `context.startActivity(searchIntent)` returns. It does not verify that a
track was selected or that playback began. Consequently, an app can open its
search surface without starting playback and the handler still considers the
command successful. The fallback code is only reached when launching the
intent throws; it is not reached when the target app accepts the intent but
does not auto-play.

## 1. FRIDAY command ingress and execution context

The relevant path is:

```text
Supabase realtime chat_sync_pipe
  -> ChatRepository.restartSyncPipeSubscription()
  -> handlePipeMessage(record)
  -> payload type == "COMMAND"
  -> LocalCommandExecutor.execute(intent, params)
  -> MediaCommandHandler.execute("play_music", params)
  -> MediaCommandHandler.playMusic(params)
```

In `ChatRepository.handlePipeMessage`, command parameters are decoded from the
JSON payload and the local executor is called on the main dispatcher:

```kotlin
} else if (type == "COMMAND") {
    val intent = payload["intent"]?.jsonPrimitive?.content
    val params = payload["parameters"]?.jsonObject?.let {
        it.mapValues { (_, v) ->
            when (v) {
                is JsonPrimitive -> v.contentOrNull ?: v.longOrNull
                    ?: v.doubleOrNull ?: v.booleanOrNull
                else -> v.toString()
            }
        }
    }

    withContext(Dispatchers.Main) {
        commandExecutor.execute(intent, params)
    }

    ChatApi.deletePipeItem(pipeId)
}
```

`ChatRepository` itself owns an application-context-backed
`LocalCommandExecutor`; the executor constructs `MediaCommandHandler(context)`.
The command is therefore not launched from a foreground service or worker. It
originates in a realtime coroutine, but `startActivity` is deliberately
marshaled to the main thread and uses `FLAG_ACTIVITY_NEW_TASK` because the
handler's context is not an Activity context.

The `FCMService` and `ChatSyncWorker` paths do not call
`LocalCommandExecutor` in the inspected code.

## 2. Intent creation and action type

### Nonblank music query (the affected path)

For `play_music` with a nonblank query, the primary intent is:

```kotlin
val searchIntent =
    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        putExtra(SearchManager.QUERY, searchQuery)
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        putExtra(MediaStore.EXTRA_MEDIA_TITLE, searchQuery)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (packageName != null) {
            setPackage(packageName)
        }
    }
context.startActivity(searchIntent)
```

Exact action:

```text
android.media.action.MEDIA_PLAY_FROM_SEARCH
```

This is an activity intent, not a broadcast. It is implicit when no target app
was resolved, and package-constrained when `app`, `app_name`,
`target_app`, or an `on Spotify`/`on YouTube Music` suffix selects a supported
player.

### Blank query

If the extracted query is blank, the handler does not construct a search
intent. With a selected package it calls
`getLaunchIntentForPackage(packageName)` and opens that package. Without a
selected package it uses:

```kotlin
Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
}
```

This path opens a music player only; it has no query and no play request.

## 3. Intent metadata and extras

For the nonblank `MEDIA_PLAY_FROM_SEARCH` intent, the complete set of extras is:

| Key | Value |
|---|---|
| `SearchManager.QUERY` (`"query"`) | `searchQuery`, after trimming and removing an explicit player suffix |
| `MediaStore.EXTRA_MEDIA_FOCUS` | Literal `"vnd.android.cursor.item/*"` |
| `MediaStore.EXTRA_MEDIA_TITLE` | Same `searchQuery` string |

The implementation does **not** add:

```text
MediaStore.EXTRA_MEDIA_ARTIST
android.intent.extra.focus
```

There is no separate song/artist decomposition. Although the input map accepts
`song`, `artist`, and `title` as alternate sources when `query`/`genre` are
absent, the selected value is still placed into one combined search string
and sent as `EXTRA_MEDIA_TITLE`.

The only flag is:

```text
Intent.FLAG_ACTIVITY_NEW_TASK
```

No URI, MIME type, data payload, or explicit `ComponentName` is assigned to the
primary search intent.

## 4. Target app selection and chooser handling

The handler derives a package from command parameters or natural-language
suffixes:

```kotlin
var targetApp = (parameters?.get("app")
    ?: parameters?.get("app_name")
    ?: parameters?.get("target_app")) as? String

val packageName = when (targetApp?.lowercase()?.trim()) {
    "spotify" -> "com.spotify.music"
    "youtube", "yt_music", "youtube_music", "yt" ->
        "com.google.android.apps.youtube.music"
    else -> null
}
```

The suffix parser recognizes:

```text
... on spotify
... on youtube
... on youtube music
... on yt music
```

When a package is selected, `setPackage(packageName)` constrains the
`MEDIA_PLAY_FROM_SEARCH` activity intent to:

```text
Spotify:      com.spotify.music
YouTube Music: com.google.android.apps.youtube.music
```

When no package is selected, no default player preference is read and no
package is set. Android resolves the implicit activity. There is no
`Intent.createChooser(...)` in this path, so the handler does not deliberately
present a player chooser. The only chooser in the inspected app is for
sharing cards in `CaptureHelper`, unrelated to music.

The app's DataStore/SharedPreferences helpers store theme, font, wallpaper,
voice, profile, session, and similar settings, but no music-player preference
is read by `MediaCommandHandler`.

## 5. Deep-link and URI backup flow

The primary music-search path has two exception-only fallbacks:

1. YouTube web/app search:

   ```kotlin
   Intent(
       Intent.ACTION_VIEW,
       Uri.parse(
           "https://www.youtube.com/results?search_query=${
               Uri.encode(searchQuery)
           }"
       )
   ).apply {
       setPackage("com.google.android.youtube")
       flags = Intent.FLAG_ACTIVITY_NEW_TASK
   }
   ```

2. YouTube Music web search:

   ```kotlin
   Intent(
       Intent.ACTION_VIEW,
       Uri.parse(
           "https://music.youtube.com/search?q=${Uri.encode(searchQuery)}"
       )
   ).apply {
       flags = Intent.FLAG_ACTIVITY_NEW_TASK
   }
   ```

There is no `spotify:search:<query>` URI, Spotify HTTP deep link, or other
Spotify fallback. The YouTube URI fallbacks are only attempted if the primary
`startActivity(searchIntent)` throws. If YouTube Music opens a search page
without playing, no fallback is attempted because no exception occurred.

## 6. Why the symptom is possible

The code requests the generic Android
`MEDIA_PLAY_FROM_SEARCH` contract, but the target app's handling of that
contract determines whether it searches, queues, or starts playback. The
implementation does not send an explicit track URI or a player-specific
playback command, and it does not confirm a playback state/result.

The following behavior makes the reported symptom expected for an app that
accepts the intent as a search/open request:

```kotlin
context.startActivity(searchIntent)
true
```

The `true` means only that Android accepted the activity launch. It does not
mean that the requested song was resolved or started. In addition, the broad
focus value (`"vnd.android.cursor.item/*"`) and the absence of
`EXTRA_MEDIA_ARTIST` leave the target app with only a free-form query/title.
Different versions of Spotify and YouTube Music may therefore open search
rather than auto-playing.

## Source locations

- `app/src/main/java/com/baroness/app/repository/ChatRepository.kt` —
  realtime command decoding and main-thread dispatch.
- `app/src/main/java/com/baroness/app/command/LocalCommandExecutor.kt` —
  handler lookup and execution logging.
- `app/src/main/java/com/baroness/app/command/handlers/MediaCommandHandler.kt` —
  all music intent construction, package targeting, and fallbacks.
- `app/src/main/java/com/baroness/app/utils/StorageManager.kt` and
  `app/src/main/java/com/baroness/app/repository/SettingsRepository.kt` —
  persisted settings; neither defines a music-player preference.
- `app/src/main/AndroidManifest.xml` —
  declares visibility for the media-play-from-search action and the app's
  services/activity. No music player component is declared or owned by
  Baroness.
