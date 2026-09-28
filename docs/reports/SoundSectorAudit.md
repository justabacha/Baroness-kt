# Sound Sector Audit

**Scope:** forensic audit of the repository's SOUND / AUDIO / VOICE / HAPTICS sector.  
**Evidence rule:** statements under **Code evidence** are based on the current repository. Statements under **Documentation claims** quote or summarize existing reports and are not treated as proof.  
**Audit only:** no production code, configuration, resource, or existing report was changed.

## 1. Executive Summary

The repository contains two separate audio domains:

1. A functioning remote voice pipeline in `voice/`, using an HTTP TTS proxy, ExoPlayer, Android `TextToSpeech` fallback, and a disk cache for selected static phrases.
2. A functioning clock/alarm/timer sound path in `clock/` and `receiver/`, using Android `RingtoneManager`, `ToneGenerator`, `AlarmManager`, notification channels, and custom vibration patterns.

There is also a separate local-music player using Media3 ExoPlayer and a foreground `MediaSessionService`. It is an audio subsystem, but it is not connected to the sound preference keys or the Sound & Haptics settings controls.

Voice enabled/provider/voice ID/speed/pitch/persona mode/director note and clock voice/alarm/timer/vibration values are persisted through `SettingsRepository` and `StorageManager` DataStore. Existing drawer controls are wired to those values and invoke real preview/playback calls. However, the remote provider request does not send `VoiceConfig.speed` or `VoiceConfig.pitch`, so the speed and pitch controls are persisted and displayed but do not affect remote synthesized audio. Persona mode deliberately replaces the selected provider, voice, speed, pitch, and director note with hardcoded persona configurations in `DashboardViewModel`.

The current Settings Center is not yet a sound settings screen. Its Sound & Haptics category is listed but only the `profile` and `appearance` category cards navigate; no `settings/sound`, `settings/friday`, `settings/notifications`, or `settings/storage` routes exist in current `MainActivity.kt`. The older `GlobalDrawer` remains the only complete sound settings UI. This makes the sound infrastructure **partially ready** for a future Settings Center surface, not fully migrated.

Clock sounds are generated system tones or the device's default ringtone/notification sound; there are no bundled audio files. Alarm/timer runtime behavior is real, but Android notification channel sound configuration and in-app selected sound behavior are separate: `AlarmReceiver` always creates/uses a channel with a system URI while `ClockSoundPlayer` separately plays the selected option. General FCM notification channels have no app-controlled sound preference and are created without explicit custom sound.

Haptics are fragmented. Alarm/timer vibration is configurable and persisted. UI haptics use Compose `LocalHapticFeedback` or the independent `VibrationHelper`, with no global enable/disable preference. Audio focus handling is not present in the voice player or clock player; Media3 music does configure audio attributes and noisy-device handling. No sound-sector tests were found beyond the default example unit test.

## 2. Sound System Inventory

| Component | File/Class | Purpose | Current State | State Owner | Persistence | Used By | Evidence |
|---|---|---|---|---|---|---|---|
| Remote voice provider | `voice/VoiceProvider.kt`, `BaronessVoiceProvider` | POST text/provider/voice/director note to a fixed HTTPS proxy and return bytes | Active, network-dependent; provider availability is hardcoded `true` | `VoiceConfig` supplied by callers | Indirectly through settings | `VoiceCenter` | `synthesize()` builds the request and returns `AudioResult` |
| Voice models | `voice/VoiceModels.kt` | Defines provider, voice ID, speed, pitch, language, format, duration, director note | Active model layer; not all fields reach the network | Callers and `SettingsViewModel` | Settings fields via repository | Voice pipeline | `VoiceConfig` includes speed/pitch but provider payload omits them |
| Voice playback engine | `voice/VoiceCenter.kt` | Chunks text, fetches/saves audio, queues ExoPlayer media, falls back to Android TTS | Active | Local `VoiceCenter` instance and `VoiceState` | Cache files only | Settings preview, dashboard announcements, alarm timer announcement | `speak()`, `stop()`, `shutdown()` |
| Voice cache | `voice/VoiceCacheManager.kt` | Static phrase cache, temporary generated files, size/age cleanup | Active but only used for static content | Internal cache manager | `context.cacheDir/voice_cache` | `VoiceCenter` | `isStaticContent`, `get`, `put`, `createTempFile` |
| Settings voice state | `viewmodels/SettingsViewModel.kt` | Reactive voice preference flows and preview actions | Active and wired to drawer | `SettingsViewModel` | DataStore through repository | `DrawerSoundHaptics`, `DrawerClock`, callers | `voiceEnabled`, `voiceProvider`, `voiceId`, `voiceSpeed`, `voicePitch` |
| Voice persistence | `repository/SettingsRepository.kt` | Accessors and defaults for voice fields | Active | Repository | `baroness_prefs` DataStore via `StorageManager` | Settings VM, direct runtime readers | `KEY_VOICE_*` accessors |
| Clock scheduler | `clock/BaronessClockManager.kt` | Stores/schedules alarms and timers using exact Android alarms | Active | Room clock DAO plus AlarmManager | Room `clock_items` | `ClockCommandHandler`, boot receiver, alarm receiver | `setTimer`, `setAlarm`, `rescheduleAllActiveAlarms` |
| Clock sound player | `clock/ClockSoundPlayer.kt` | Selected alarm/timer tone preview and runtime playback | Active; system tones/default ringtone, no app audio assets | Arguments from settings/runtime | Selection in DataStore | Drawer, `AlarmReceiver` | `playSound`, `playSingleSound`, `playVibration` |
| Alarm receiver | `receiver/AlarmReceiver.kt` | Receives alarm/timer broadcasts, posts notification, starts sound/vibration, optionally speaks timer label | Active | Reads initial settings directly from `SettingsRepository` | Settings DataStore; clock Room row | AlarmManager/boot/notification actions | `triggerAlarmNotification` |
| General notification manager | `utils/NotificationManager.kt` | Creates wishlist/messages/general channels and posts FCM notifications | Active but no sound controls | Android channel state | Channel state is OS-owned | `MainApplication`, `FCMService` | Channels are created without custom sound |
| In-app notification bus | `utils/NotificationCenter.kt` | Sends foreground banner events to a registered VM | Active, process-local | Static ViewModel reference | None | `MainActivity`, `AlarmReceiver` | `viewModelRef` and `show()` |
| Alarm vibration persistence | `SettingsRepository`, `SettingsViewModel` | Stores selected pattern name | Active for clock alerts | Settings VM/repository | `alarm_vibration_pattern` | Drawer and `AlarmReceiver` | Default `wave`; read at alarm fire |
| Generic vibration helper | `utils/VibrationHelper.kt` | Fixed waveform for wishlist warning | Active but independent | Call site | None | `WishlistViewModel` | No settings lookup |
| Compose UI haptics | `components/chat/MessageBubble.kt` | Long-press haptic feedback | Active, system haptic | Compose runtime | None | Message bubble long press | `LocalHapticFeedback.current` |
| Local music player | `media/BaronessPlayerManager.kt` | Media3 local song playback, queue, visualizer, media session | Active separate subsystem | Singleton manager | No sound preference persistence shown | Media UI/commands/service | Media3 `ExoPlayer`, audio attributes, noisy handling |
| Background music service | `media/BaronessMediaService.kt` | Foreground media playback notification and controls | Active | Media session/player manager | Android service state | Manifest/service | `MediaSessionService`, media playback FGS |
| Sound Settings Center category | `screens/settings/SettingsCenterScreen.kt` | Lists Sound & Haptics category | UI-only/dead navigation | No sound page route | None | Settings Center | Category click handles only `profile` and `appearance` |
| Drawer voice settings | `components/DrawerSoundHaptics.kt` | Voice toggle/provider/voice/speed/pitch/preview | Active UI with partial runtime semantics | Settings VM | DataStore keys | GlobalDrawer | Calls VM setters and preview |
| Drawer clock settings | `components/DrawerClock.kt` | Clock voice, alarm/timer choices, vibration patterns and auditions | Active UI/runtime | Settings VM and ClockSoundPlayer | DataStore keys | GlobalDrawer | Calls setters and player preview |

## 3. TTS / Voice Pipeline

### 3.1 End-to-end path

#### Settings preview

`DrawerSoundHaptics` reads reactive flows from `SettingsViewModel`. `previewVoice()` builds a `VoiceConfig` from current voice ID, speed, pitch, provider, and director note, then calls:

```text
DrawerSoundHaptics
  -> SettingsViewModel.previewVoice()
  -> VoiceCenter.speak(preview text, VoiceContext(config))
  -> VoiceCenter chunkText()
  -> BaronessVoiceProvider.synthesize() for each sentence
  -> cache lookup/write for static phrases
  -> ExoPlayer MediaItem queue
  -> local playback
  -> Android TextToSpeech fallback if remote playback cannot start
```

Evidence: `DrawerSoundHaptics.kt`, `SettingsViewModel.previewVoice()`, `VoiceCenter.speak()`, `VoiceProvider.synthesize()`.

#### Dashboard announcement

`DashboardViewModel.triggerAnnouncement()` reads settings directly from `SettingsRepository`. If voice is disabled, it returns. If persona optimization is enabled, it uses hardcoded configurations:

| Persona | Provider | Voice | Speed | Pitch | Director note |
|---|---|---|---:|---:|---|
| `baroness` | `murf` | `en-US-marcus` | 1.0 | 1.0 | `Warm and sophisticated.` |
| Other persona | `deepgram` | `aura-asteria-en` | 1.1 | 1.0 | `Playful and friendly.` |

If persona optimization is disabled, it reads the persisted provider, voice ID, speed, pitch, and director note. It then calls `VoiceCenter.speak(..., bypassCache = true)`.

Evidence: `DashboardViewModel.triggerAnnouncement()`.

#### Timer voice announcement

`AlarmReceiver.triggerAlarmNotification()` reads `clock_voice_announce`. It only schedules a spoken announcement for `type == "TIMER"` and waits two seconds after the chime. `speakVoiceAnnouncement()` creates a new `VoiceCenter` and reads the general persisted voice fields directly from `SettingsRepository`.

Evidence: `AlarmReceiver.kt`, `speakVoiceAnnouncement()`.

### 3.2 Providers and voices

The code declares one Kotlin provider implementation, `BaronessVoiceProvider`, with a fixed proxy URL:

```text
https://thats-baroness-p.vercel.app/api/baroness
```

Its `getAvailableVoices()` list contains:

- Deepgram: `aura-asteria-en`, `aura-luna-en`, `aura-stella-en`, `aura-athena-en`, `aura-hera-en`, `aura-orion-en`, `aura-arcas-en`, `aura-perseus-en`, `aura-angus-en`, `aura-orpheus-en`, `aura-helios-en`, `aura-zeus-en`
- Murf: `en-US-marcus`
- Edge: `en-US-jenny`

The drawer duplicates this list instead of consuming `BaronessVoiceProvider.getAvailableVoices()`. The drawer offers Deepgram and Murf buttons only; the `"edge"` branch exists in its voice list but there is no Edge provider button. `SettingsViewModel.setVoiceProvider("edge")` uses `en-US-JennyNeural`, which does not match the drawer's `en-US-jenny` ID or `VoiceProvider.getAvailableVoices()` ID.

The provider sends `text`, `provider`, `voice`, and `directorNote`. It does **not** send `speed`, `pitch`, or `language`. Therefore:

- Provider and voice ID affect the remote request.
- Director note affects the remote request.
- Speed and pitch are present in local state and `VoiceConfig`, but are not evidenced in the remote request.
- The Android fallback uses hardcoded `Locale.UK`, speech rate `1.0f`, and pitch `0.9f` from `VoiceCenter` initialization. It does not use the selected voice ID, speed, or pitch.

### 3.3 Audio format and playback

The provider infers MP3 unless the response `Content-Type` contains `wav`. `VoiceCenter` always writes `.mp3` filenames, including files whose response was classified as WAV. Playback uses `MediaItem.fromUri(Uri.fromFile(file))` with ExoPlayer.

Remote synthesis is non-streaming: `execute()` reads the complete response body into a byte array before a file is played. Sentences are fetched concurrently with `async(Dispatchers.IO)`, then awaited in sentence-list order and appended to one ExoPlayer queue.

The first failed chunk triggers cancellation of all deferred fetches and system TTS fallback for the entire text. Later failed chunks are skipped if earlier playback has started; there is no per-sentence retry or fallback insertion.

### 3.4 Cache behavior

`VoiceCacheManager` creates:

- `cacheDir/voice_cache/static_phrases`
- `cacheDir/voice_cache/temp_dynamic`

Only phrases matching `isStaticContent()` are read from or written to `static_phrases`. Dynamic text is written to a temporary MP3 file but the returned `File` is not retained by `VoiceCenter`; temporary files are cleared on `VoiceCacheManager` initialization and `VoiceCenter.shutdown()`, not immediately after each item finishes.

Static cache keys include provider, voice ID, and normalized text, but not speed, pitch, language, or director note. Because the provider currently ignores speed/pitch, this does not create a second observed mismatch, but the cache key still does not represent all `VoiceConfig` fields.

Cache limits: 30 MB target, trim oldest files down to 70% when over limit, and delete static files older than 24 hours. Exceptions are logged with `printStackTrace()` and return null in cache writes/reads.

### 3.5 Loading, cancellation, fallback, concurrency

- `VoiceState` is `IDLE`, `LOADING`, or `PLAYING`.
- A new `speak()` cancels `currentSpeakJob`, stops both ExoPlayer and system TTS, and starts a new job.
- `stop()` cancels the job and stops both players.
- `VoiceCenter.shutdown()` cancels its scope, releases ExoPlayer, shuts down Android TTS, and clears temp cache.
- ExoPlayer listener state changes return the state to `IDLE` on end or player error.
- System TTS has an utterance listener that updates state, but system TTS initialization failure leaves the fallback unable to speak and only logs an error.
- There is no explicit audio focus request/abandon path in `VoiceCenter`.
- There is no foreground service for voice/TTS announcements.
- A timer announcement creates a `VoiceCenter` locally and does not call `shutdown()` after the announcement; cleanup is therefore not demonstrated for that instance.

## 4. Voice Settings

| Setting | Exists | Stored value/default | Reads | Writes | Reactive | Changes playback? | Persistence/scope |
|---|---|---|---|---|---|---|---|
| Voice enabled | Yes | `voice_enabled`, default `true` | `DashboardViewModel` and UI; `AlarmReceiver` uses separate clock flag | `SettingsViewModel.setVoiceEnabled()` | Yes in VM; direct initial read in dashboard | Gates dashboard announcements; does not gate `AlarmReceiver` timer voice because that uses `clock_voice_announce` | Persisted, global |
| Provider | Yes | `voice_provider`, default `deepgram` | Preview, dashboard non-persona path, timer announcement | `setVoiceProvider()` | Yes in VM | Affects remote request provider; selecting provider also writes a default voice ID | Persisted, global |
| Voice ID | Yes | `voice_id`, default `aura-asteria-en` | Preview, dashboard non-persona path, timer announcement | `setVoiceId()` and provider setter defaulting | Yes in VM | Affects remote request voice when non-persona mode is used | Persisted, global |
| Speed | Yes | `voice_speed`, default `1.0f` | `VoiceConfig` construction and persona override | `setVoiceSpeed()` | Yes in VM | Not evidenced for remote provider; fallback hardcodes 1.0; persona mode hardcodes values | Persisted, global state but incomplete runtime effect |
| Pitch | Yes | `voice_pitch`, default `1.0f` | `VoiceConfig` construction and persona override | `setVoicePitch()` | Yes in VM | Not evidenced for remote provider; fallback hardcodes 0.9; persona mode hardcodes 1.0 | Persisted, global state but incomplete runtime effect |
| Director note | Yes | `voice_director_note`, default clear/natural/expressive sentence | Provider request and non-persona runtime path | `setDirectorNote()`; no control in `DrawerSoundHaptics` | Yes in VM | Affects provider request when used | Persisted, global; currently not exposed in the current sound drawer |
| Persona voice optimization | Yes | `use_persona_voices`, default `true` | `DashboardViewModel`; UI | `setUsePersonaVoices()` | Yes in VM | Replaces selected provider/voice/speed/pitch/director note for dashboard announcements; timer path does not read it | Persisted, global preference with feature-specific dashboard effect |
| Volume | No app preference | No sound-volume key found | Android system volume only | `MediaCommandHandler` adjusts `STREAM_MUSIC` | Not an app setting | Changes music stream volume through system UI/API, not a voice setting | System-owned |
| TTS enabled for all speech | Partial | Separate `voice_enabled` and `clock_voice_announce` | Dashboard and timer paths read different toggles | Separate setters | Yes in VM | There is no single gate covering all voice uses | Two global DataStore flags |

## 5. Clock / Alarm / Timer Audio

### Clock voice

There is no recurring clock tick voice or periodic spoken clock implementation. `clock_voice_announce` is a persisted toggle labeled “Friday Voice Readout”. It is read by `AlarmReceiver`, but only the timer branch currently calls `speakVoiceAnnouncement()`. The alarm branch does not speak a time or label.

The Drawer Clock “Test Voice Announcement” button calls the general `SettingsViewModel.previewVoice()`, not a dedicated clock announcement path.

### Alarm sound

`BaronessClockManager.setAlarm()` stores an `ALARM` row in Room and schedules an exact `AlarmManager.RTC_WAKEUP` broadcast. At fire time, `AlarmReceiver` reads `alarm_sound_option` and calls:

```text
ClockSoundPlayer.playSound(context, soundOption, isAlarm = true, loopDurationMs = 60000)
```

Supported drawer values:

- `default_alarm`: device alarm ringtone through `RingtoneManager`
- `gentle_chime`: generated `ToneGenerator` sequence
- `digital_beep`: generated `ToneGenerator` sequence

For alarms, the player loops a 2-second sound attempt about every 2.2 seconds for 60 seconds. `stopSound()` cancels the coroutine and stops the retained `Ringtone`; ToneGenerator instances are local to their IO coroutines and are not retained for explicit stop.

### Timer sound

`setTimer()` stores a `TIMER` row and schedules the same receiver. At fire time, `timer_chime_option` is read and passed to `ClockSoundPlayer` with a 1.5-second duration. Supported drawer values:

- `chime_chime`: generated classic chime
- `soft_bell`: generated prompt/ack tones
- `marimba`: generated DTMF tone sequence

The selected timer chime is consumed by runtime code. No audio resource file is loaded.

### Vibration

At every alarm/timer notification, `AlarmReceiver` calls `ClockSoundPlayer.playVibration()` with the persisted `alarm_vibration_pattern`. The three current names are:

- `wave`: default waveform
- `pulse`
- `heartbeat`

The notification channel itself also calls `enableVibration(true)` and the notification builder does not attach a custom vibration pattern. Thus the custom vibration and channel vibration are separate behaviors.

### Selection persistence and missing resources

Sound selections are stored as arbitrary strings without validation/sanitization. Unknown values fall through to the system default ringtone in `ClockSoundPlayer`; there are no bundled resource lookup failures for the generated tone options. The `SettingsRepository` defaults apply only when a key is absent, not when an invalid value is saved.

### Lifecycle/background

Clock scheduling uses exact alarms and `RTC_WAKEUP`, and `BootReceiver` reschedules active Room rows after boot. `AlarmReceiver` performs database deactivation on `Dispatchers.IO`, then posts notification and starts sound/vibration. The code does not demonstrate a wakelock, audio focus request, foreground service, or receiver goAsync lifetime management for the delayed timer voice coroutine.

## 6. Haptics / Vibration

### System haptic feedback

`MessageBubble` uses Compose `LocalHapticFeedback.current` and `HapticFeedbackType.LongPress` for message long-press actions. This is system/Compose haptic feedback, not the persisted alarm pattern and not controlled by a global haptics setting.

### Custom vibration

`ClockSoundPlayer.playVibration()` directly uses `VibratorManager.defaultVibrator` on Android S+ and `Vibrator` on older versions, with `VibrationEffect.createWaveform()` where available. It implements the persisted alarm/timer patterns.

`VibrationHelper.vibrate()` is a separate fixed waveform (`0, 50, 30, 50`) for Android O+ and a duration vibration on older versions. It checks `Manifest.permission.VIBRATE` before vibrating.

### Feature-specific vibration

- Alarm/timer vibration: configurable through `alarm_vibration_pattern`.
- Wishlist warning vibration: fixed `VibrationHelper`, no setting.
- Message long press: Compose haptic, no setting.
- No global haptic enable/disable preference exists.
- No evidence of `performHapticFeedback` elsewhere beyond message bubbles.
- No evidence of a `VibratorManager` abstraction shared by these paths.

The manifest declares `android.permission.VIBRATE`. `ClockSoundPlayer` does not perform the explicit permission check used by `VibrationHelper`; the code relies on the platform call succeeding or logging an exception.

## 7. Sound State Ownership

### Main preference path

```text
DrawerSoundHaptics / DrawerClock
        ↓
SettingsViewModel StateFlows and setter methods
        ↓
SettingsRepository accessors
        ↓
StorageManager
        ↓
DataStore("baroness_prefs")
```

### Runtime bypasses and alternate readers

`DashboardViewModel` and `AlarmReceiver` instantiate/use their own `SettingsRepository` and perform synchronous initial reads rather than consuming the `SettingsViewModel` flows. This is valid for background execution but creates a second read path and means runtime consumers do not observe reactive state directly.

`VoiceCenter` itself owns transient playback state and a `VoiceCacheManager`; it does not own persisted settings.

`ClockSoundPlayer` owns transient ringtone/job state and receives raw string selections from callers.

### Duplicate or stale state

- Voice IDs and voice lists are duplicated between `VoiceProvider.getAvailableVoices()`, `DrawerSoundHaptics`, and `SettingsViewModel.setVoiceProvider()`.
- `VoiceConfig` contains speed, pitch, and language, but the provider payload only uses provider/voice/director note.
- Alarm/timer selections are stored and used, but no validation registry exists.
- `voice_enabled` and `clock_voice_announce` are separate gates; no single global voice policy exists.
- The local music subsystem has its own player/media state and is not connected to settings sound state.

## 8. Existing Sound Settings UI

| Surface | File/composable | Controls | State source | Actual behavior | Duplication/status |
|---|---|---|---|---|---|
| Global drawer voice section | `components/GlobalDrawer.kt` + `DrawerSoundHaptics()` | Voice enabled, persona optimization, Deepgram/Murf buttons, voice IDs, speed, pitch, preview/stop | `SettingsViewModel` StateFlows | Settings persist; preview invokes real `VoiceCenter`; speed/pitch remote effect is incomplete | Primary voice control surface |
| Global drawer clock section | `DrawerClock()` | Friday voice readout, test preview, alarm sound, timer chime, vibration pattern | `SettingsViewModel` StateFlows | Values persist; auditions call real `ClockSoundPlayer`; runtime alarm/timer consumes values | Primary clock sound control surface |
| Settings Center category list | `screens/settings/SettingsCenterScreen.kt` | “Sound & Haptics” descriptive card | No sound page | Card click has no branch and does not navigate | UI-only/dead for sound |
| Notifications drawer | `components/DrawerNotifications.kt` | “Coming soon...” text only | None | No controls or sound setting | Placeholder |
| Storage drawer | `components/DrawerStorage.kt` | “Coming soon...” text only | None | No cache clear control despite cache manager existing | Placeholder |
| Chat message bubbles | `components/chat/MessageBubble.kt` | No visible haptic control | Compose local haptic | Long press emits system haptic | Contextual behavior, not settings |
| Wishlist warnings | `WishlistViewModel` | No visible haptic control | Fixed helper call | Warning can vibrate | Contextual behavior, not settings |
| Music UI/service | `media/*` and consuming screens | Play/pause/queue/volume command behavior | `BaronessPlayerManager` | Real background media playback | Separate audio domain; no sound preferences |

There is no current `SoundSettingsPage.kt`, `FridaySettingsPage.kt`, notification settings page, or storage settings page in `app/src/main/java`.

## 9. FRIDAY Audio Boundary

FRIDAY is represented in navigation as a `ChatRoomScreen` route (`"Friday"`), but the inspected chat input does not call `VoiceCenter`. The actual spoken FRIDAY/persona behavior found in code is:

- `DashboardViewModel` generates dashboard announcements and applies persona voice overrides when `use_persona_voices` is true.
- `AlarmReceiver` labels its timer announcement “Friday speaking timer announcement” and uses general persisted voice settings.
- `VoiceCenter` is a generic voice engine and does not contain FRIDAY-specific provider ownership.
- `VoiceContext` only wraps `VoiceConfig`; it contains no FRIDAY identifier.

There is no separate FRIDAY voice preference store. Persona optimization is a global DataStore preference consumed by dashboard announcements. The user-selected voice/provider is bypassed for dashboard announcements when persona optimization is enabled. No current code proves that ordinary FRIDAY chat responses are spoken.

## 10. Notification Audio

### Clock/alarm channel

`AlarmReceiver` uses channel ID `baroness_clock_channel`, creates an Android `NotificationChannel` named “Alarms & Timers”, sets a system alarm/notification URI, enables vibration, then builds a notification with `.setSound(soundUri)`. The app also separately calls `ClockSoundPlayer.playSound()` using the selected in-app option.

Because Android O+ channel sound is OS/channel state, changing `alarm_sound_option` does not prove that an already-created channel's sound changes. The code does not delete/recreate the channel or update a persisted channel configuration when the in-app selection changes. The selected generated tone is therefore an additional direct playback path, not necessarily the notification channel's sound.

### General FCM channels

`MainApplication` creates:

- `wishlist_notifications`, IMPORTANCE_DEFAULT
- `messages_notifications`, IMPORTANCE_HIGH
- `general_notifications`, IMPORTANCE_LOW

`NotificationManager.showSystemNotification()` selects one of these channels and does not call `.setSound()`. No in-app sound preference controls these channels. Android system channel settings remain the sound authority.

`FCMService` always calls `showSystemNotification()` for received messages; its comments acknowledge incomplete foreground/background routing. `NotificationCenter` separately supports in-app banners with no audio behavior.

## 11. Audio Lifecycle / Background Behavior

### Voice

- `VoiceCenter` uses a Main dispatcher scope with `SupervisorJob`.
- Network calls and file operations run on IO dispatcher child coroutines.
- Repeated `speak()` cancels the previous job and stops both playback engines.
- ExoPlayer and Android TTS are released only by `shutdown()`/`onCleared()` for ViewModel-owned instances.
- No audio focus request or abandon is present.
- No foreground service or wake lock is used for TTS.
- `DashboardViewModel` owns one `VoiceCenter`; `SettingsViewModel` owns another. Timer alarms create another local instance.
- System fallback uses `QUEUE_FLUSH`, so fallback calls replace current system TTS utterances.

### Clock/alarm/timer

- AlarmManager exact broadcasts are used with `RTC_WAKEUP`.
- Boot rescheduling is implemented by `BootReceiver`.
- `ClockSoundPlayer` uses a process-global coroutine scope on Main and a retained `Ringtone`.
- `stopSound()` cancels loop jobs and stops only the retained ringtone.
- ToneGenerator sequences run in separate IO coroutines and do not expose a cancellation handle.
- Delayed timer voice uses `CoroutineScope(Dispatchers.Main).launch` inside `BroadcastReceiver`; no explicit receiver lifetime handoff is shown.

### Local music

- `BaronessPlayerManager` configures Media3 audio attributes with `USAGE_MEDIA`.
- It enables `setHandleAudioBecomingNoisy(true)`.
- `BaronessMediaService` is a media playback foreground service with a `MediaSession`.
- The service stops itself when the player has no current song and handles task removal.
- This subsystem has lifecycle/background infrastructure not shared by TTS or clock sounds.

## 12. Assets & Resources

No `app/src/main/res/raw` directory exists, and no `.mp3`, `.wav`, `.ogg`, `.m4a`, `.aac`, or `.flac` audio files were found in the repository.

Relevant resource observations:

| Resource/location | Apparent purpose | References | Status |
|---|---|---|---|
| `app/src/main/res/drawable/icon.png` | Notification small icon | `AlarmReceiver`, `NotificationManager` | Used; not audio |
| `app/src/main/res/drawable-nodpi/headphones.png` | Visual asset | No sound playback reference found in inspected Kotlin | Resource use for audio not proven |
| `app/src/main/res/drawable/*` JPG/PNG files | Wallpapers/visual assets | Dynamic background/drawer/screens | Not audio |
| No raw audio resources | Bundled alarm/chime files | No `R.raw` references found | Missing entirely |
| `context.cacheDir/voice_cache` | Generated remote voice files | `VoiceCacheManager` | Runtime-generated, not repository asset |

Clock sound names are logical IDs only. Their implementations are `ToneGenerator` sequences or `RingtoneManager` system defaults.

## 13. Dead / Duplicated / Suspicious Code

### Confirmed duplication

- Voice option lists are duplicated in `VoiceProvider.kt` and `DrawerSoundHaptics.kt`.
- `SettingsViewModel.setVoiceProvider()` has a third `"edge"` branch even though the drawer exposes only Deepgram and Murf and uses a different Edge voice ID.
- `SettingsViewModel` and `DashboardViewModel` each own a `VoiceCenter`; `AlarmReceiver` creates another per timer announcement.
- Alarm notification sound URI and selected direct `ClockSoundPlayer` sound are two independent playback paths.
- Haptics have three unrelated mechanisms: notification channel vibration, `ClockSoundPlayer` custom patterns, and UI-specific Compose/`VibrationHelper` calls.

### UI-only, placeholder, or unreachable/incomplete

- Settings Center lists Sound & Haptics, FRIDAY, Notifications, and Storage categories, but only profile and appearance cards navigate. The sound category is currently a descriptive UI card with no route.
- `DrawerNotifications` and `DrawerStorage` are explicit “Coming soon...” placeholders.
- `directorNote` is persisted and sent to remote synthesis but is not exposed in `DrawerSoundHaptics`.
- Edge voice support is inconsistent and not reachable through the current provider selector UI.
- `VoiceProvider.isAvailable()` always returns `true`; no provider health check exists.
- `AudioResult.duration` is always `0L` in `BaronessVoiceProvider`.

### Behavior mismatches

- Speed and pitch controls appear functional but are not serialized into the remote request and are not applied to system fallback.
- `VoiceCacheManager` always uses `.mp3` filenames even when content type is WAV.
- Cache keys omit speed, pitch, language, and director note.
- Unknown alarm/timer option strings silently use the system default ringtone because `ClockSoundPlayer` uses `else` fallback.
- `SettingsRepository` does not validate alarm/timer/vibration option strings.
- `ClockSoundPlayer.playVibration()` does not use the explicit permission guard present in `VibrationHelper`.
- The current default timer path uses `chime_chime`; documentation elsewhere refers to unimplemented/obsolete IDs such as `tone_pulse`.

### Documentation/code contradictions

- `SettingsCenter.md` claims current `settings/sound`, `settings/friday`, `settings/notifications`, and `settings/storage` routes and corresponding pages, but those routes/pages are absent from current `MainActivity.kt` and source tree.
- `SettingsCenter.md` calls Voice/TTS and clock sound controls confirmed in a Sound Settings sub-screen; the actual confirmed UI is still in `GlobalDrawer`.
- `SettingsCenter.md` describes VoiceCenter consumers including `ChatInput`; current `ChatInput.kt` has no `VoiceCenter`/TTS reference.
- `Baroness_Version2.md` lists `en-US-JennyNeural`; current provider/drawer list uses `en-US-jenny`, while `setVoiceProvider("edge")` writes `en-US-JennyNeural`.
- `Baroness_Version2.md` lists sound ID `tone_pulse`; current `ClockSoundPlayer` has no `tone_pulse` branch.
- Existing reports describe alarm/timer sound and vibration as verified at the preference/runtime level, which is supported for direct `ClockSoundPlayer` playback, but they do not distinguish it from the separate Android notification channel sound.

## 14. Phase 4 Readiness

| Domain | Classification | Evidence basis |
|---|---|---|
| Voice enabled | [PARTIALLY READY] | Persisted/reactive and used by dashboard, but separate timer gate and no unified speech policy |
| Provider selection | [PARTIALLY READY] | Persisted and sent to remote proxy; selector omits Edge and provider registry is duplicated/inconsistent |
| Voice selection | [PARTIALLY READY] | Persisted and sent in non-persona path; duplicated lists and inconsistent Edge ID |
| Speech speed | [UI ONLY] | Persisted, reactive, and shown in slider, but not sent to provider and fallback ignores it |
| Speech pitch | [UI ONLY] | Persisted, reactive, and shown in slider, but not sent to provider and fallback ignores it |
| Voice preview/playback | [PARTIALLY READY] | Real remote/ExoPlayer/fallback path exists, with lifecycle and format/cache caveats |
| Voice cache | [PARTIALLY READY] | Working static/temp cache infrastructure, but no current settings/storage UI and incomplete cache key/cleanup semantics |
| Clock voice | [PARTIALLY READY] | Persisted toggle and timer announcement path exist; no spoken alarm/time path and separate preview semantics |
| Alarm sound | [PARTIALLY READY] | Persisted direct playback and audition work; notification channel sound is separate and not synchronized |
| Timer sound | [READY] | Persisted selection is read at timer expiration and direct chime playback is implemented |
| Haptics | [PARTIALLY READY] | Alarm patterns are persisted and used; UI haptics are independent and globally uncontrolled |
| Notification sound | [NOT CURRENTLY IMPLEMENTED] | Channels exist, but no app-controlled notification sound preference or custom general notification sound path |
| Other sound effects | [NOT CURRENTLY IMPLEMENTED] | No app UI sound-effects setting or SoundPool/raw asset implementation found |
| Local music playback | [READY] (separate domain) | Media3 player/service/session/background path exists, but it is not part of current sound preferences |

## 15. Evidence Index

| File path | Class/function/composable | Relevant evidence |
|---|---|---|
| `app/src/main/java/com/baroness/app/voice/VoiceProvider.kt` | `BaronessVoiceProvider.synthesize()` | Fixed proxy, request fields, response bytes, format inference, provider voice list |
| `app/src/main/java/com/baroness/app/voice/VoiceModels.kt` | `VoiceConfig`, `VoiceOption`, `AudioResult` | Declared voice parameters and audio metadata |
| `app/src/main/java/com/baroness/app/voice/VoiceCenter.kt` | `speak`, `speakSystem`, `stop`, `shutdown` | Chunking, concurrent fetch, cache/playback, fallback, cancellation, lifecycle |
| `app/src/main/java/com/baroness/app/voice/VoiceCacheManager.kt` | `isStaticContent`, `get`, `put`, `createTempFile`, `trimCacheIfNeeded` | Static phrase classification, paths, cache key, age/size cleanup |
| `app/src/main/java/com/baroness/app/repository/SettingsRepository.kt` | `KEY_VOICE_*`, `KEY_CLOCK_*`, accessor methods | Defaults and DataStore preference mapping |
| `app/src/main/java/com/baroness/app/utils/StorageManager.kt` | `Context.dataStore`, typed save/get/flow methods | `baroness_prefs` DataStore implementation |
| `app/src/main/java/com/baroness/app/viewmodels/SettingsViewModel.kt` | voice/clock StateFlows and setters | Reactive state ownership and preview construction |
| `app/src/main/java/com/baroness/app/components/DrawerSoundHaptics.kt` | `DrawerSoundHaptics` | Current voice UI, duplicate voice lists, available controls |
| `app/src/main/java/com/baroness/app/components/DrawerClock.kt` | `DrawerClock` | Current clock voice/sound/chime/vibration UI and auditions |
| `app/src/main/java/com/baroness/app/clock/ClockSoundPlayer.kt` | `playSound`, `playSingleSound`, `playVibration`, `stopSound` | ToneGenerator/system ringtone implementation and custom vibration |
| `app/src/main/java/com/baroness/app/clock/BaronessClockManager.kt` | `setTimer`, `setAlarm`, `rescheduleAllActiveAlarms` | Room-backed exact AlarmManager scheduling |
| `app/src/main/java/com/baroness/app/receiver/AlarmReceiver.kt` | `triggerAlarmNotification`, `speakVoiceAnnouncement` | Runtime sound, notification channel, vibration, timer voice |
| `app/src/main/java/com/baroness/app/receiver/BootReceiver.kt` | `onReceive` | Boot rescheduling |
| `app/src/main/java/com/baroness/app/viewmodels/DashboardViewModel.kt` | `triggerAnnouncement` | Voice enabled gate, persona overrides, dashboard speech |
| `app/src/main/java/com/baroness/app/utils/VibrationHelper.kt` | `vibrate` | Fixed wishlist warning vibration and permission check |
| `app/src/main/java/com/baroness/app/components/chat/MessageBubble.kt` | `LocalHapticFeedback`, long-press handlers | Compose system haptic feedback |
| `app/src/main/java/com/baroness/app/utils/NotificationManager.kt` | `createNotificationChannels`, `showSystemNotification` | General channels with no custom sound preference |
| `app/src/main/java/com/baroness/app/services/FCMService.kt` | `onMessageReceived` | FCM notification routing |
| `app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt` | `SETTINGS_CATEGORIES`, `CategoryCard` click handler | Sound category exists, but no sound route/navigation branch |
| `app/src/main/java/com/baroness/app/MainActivity.kt` | `AppNavigation` | Current routes; no sound/friday/notification/storage settings pages |
| `app/src/main/java/com/baroness/app/media/BaronessPlayerManager.kt` | Media3 player initialization | Separate music audio attributes, noisy handling, MediaSession |
| `app/src/main/java/com/baroness/app/media/BaronessMediaService.kt` | `MediaSessionService` lifecycle | Background music foreground service |
| `app/src/main/AndroidManifest.xml` | permissions/services/receivers | INTERNET, VIBRATE, notification, exact alarm, media FGS declarations |
| `app/src/test/java/com/baroness/app/ExampleUnitTest.kt` | default example test | No sound-specific test evidence |
| `docs/reports/SettingsCenter.md` | sound tables/routes/phase plan | Documentation claims of future/current Settings Center sound pages contradicted by current routes |
| `docs/reports/Baroness_Version2.md` | voice/sound/haptics sections | Documentation claims of verified registries and consumers; some IDs/consumers differ from code |
| `docs/reports/DrawerAudit.md` | drawer component/state matrices | Documentation claim that current drawer sound controls are wired; direct controls are supported, but scope distinctions are incomplete |
| `docs/reports/KotlinFile_Review.md` | voice/cache/settings inventory | Documentation inventory of active classes and preference keys; does not prove speed/pitch runtime effect |
| `docs/reports/PreV2.5_ConsumerAudit.md` | deferred work list | Documentation explicitly defers voice/audio refactoring and notification customization |
| `docs/reports/V2.5_ConsumerMigration.md` | migration summary | No sound implementation changes documented |

SOUND SECTOR AUDIT: COMPLETE
