# 04 — Android Client Spec

**Governed by 01-FRIDAY_Philosophy_and_Architecture.md** (§6 delay, §7 initiative) and **03-Backend_Orchestrator_Spec.md** (§10 API contract, which this client must match exactly).

Stack: **Kotlin, Jetpack Compose, Retrofit/OkHttp**.

---

## 1. Package Structure

```
app/src/main/java/com/friday/app/
├── MainActivity.kt                     # single-activity host, sets up NavHost if needed (likely just one screen)
├── ui/
│   ├── chat/
│   │   ├── ChatScreen.kt               # top-level Composable, LazyColumn + input bar
│   │   ├── MessageBubble.kt            # individual message bubble Composable
│   │   ├── TypingIndicator.kt          # animated "..." indicator Composable
│   │   └── ChatViewModel.kt            # state holder, calls repository, drives UI state
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── data/
│   ├── remote/
│   │   ├── FridayApiService.kt         # Retrofit interface, matches backend §10 contract exactly
│   │   ├── ChatDtos.kt                 # request/response data classes
│   │   └── RetrofitClient.kt           # OkHttp + Retrofit singleton setup
│   └── repository/
│       └── ChatRepository.kt           # single entry point ViewModel calls; wraps API + local command dispatch
├── command/
│   └── LocalCommandExecutor.kt         # receives command DTOs, dispatches to system intents (music, maps, etc.)
├── session/
│   └── SessionTracker.kt               # tracks app foreground/background events for session awareness
└── util/
    └── NetworkResult.kt                # sealed class wrapper for success/error/loading states
```

**Rule for implementers:** `ChatViewModel.kt` is the only file that touches both `data/` and `command/`. `MessageBubble.kt` and `TypingIndicator.kt` are pure, stateless Composables — they take data in, render UI out, no ViewModel reference inside them.

---

## 2. Chat UI

### 2.1 `ChatScreen.kt` — structure

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true,
            state = rememberLazyListState()
        ) {
            if (uiState.isTyping) {
                item { TypingIndicator() }
            }
            items(uiState.messages.reversed(), key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }
        MessageInputBar(
            onSend = { text -> viewModel.sendMessage(text) },
            enabled = !uiState.isTyping
        )
    }
}
```

- `reverseLayout = true` with a reversed list is the standard Compose pattern for chat UIs that stay pinned to the bottom without manual scroll management.
- Input bar is disabled while `isTyping` is true — this is a deliberate choice reinforcing the "she's replying" feel rather than allowing message queueing, consistent with a single-threaded conversational feel (Philosophy §1).

### 2.2 `MessageBubble.kt`

```kotlin
data class UiMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long
)

@Composable
fun MessageBubble(message: UiMessage) {
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isFromUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (message.isFromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### 2.3 `TypingIndicator.kt`

A simple three-dot animated indicator, shown for exactly `typing_duration_ms` (from the backend response, §4 below) before the actual reply bubble is inserted. No visual difference between command-path and conversation-path typing indicators — the duration itself is what varies (near-instant for commands per Philosophy §6).

```kotlin
@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    // three dots with staggered alpha animation via animateFloat, standard pattern
    Row(modifier = Modifier.padding(16.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ), label = "dot$index"
            )
            Box(
                Modifier.padding(2.dp).size(8.dp).alpha(alpha)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}
```

---

## 3. Local Command Executor

**Job:** receives the `command` object from the backend's `POST /chat` response (03 §10) and dispatches to the appropriate system intent. The backend never executes commands itself (03 §6) — it only classifies and returns structured intent/parameters; execution is entirely local, since only the phone has access to its music app, maps app, and system alarm/timer APIs.

```kotlin
class LocalCommandExecutor(private val context: Context) {

    fun execute(command: CommandDto) {
        when (command.intent) {
            "play_music" -> playMusic(command.parameters["genre"] as? String)
            "navigate" -> navigate(command.parameters["destination"] as? String)
            "set_timer" -> setTimer((command.parameters["minutes"] as? Number)?.toInt())
            "set_alarm" -> setAlarm(command.parameters)
            "unsupported" -> { /* no-op, rendered as plain chat bubble instead */ }
            else -> Log.w("LocalCommandExecutor", "Unknown intent: ${command.intent}")
        }
    }

    private fun playMusic(genre: String?) {
        val intent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
            genre?.let { putExtra("genre", it) }
        }
        context.startActivity(intent)
        // Note: exact extras depend on the installed music app; a production build
        // should target a specific app's intent scheme (e.g. Spotify's) rather than
        // the generic MediaStore intent, which many devices don't resolve.
    }

    private fun navigate(destination: String?) {
        if (destination == null) return
        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        context.startActivity(mapIntent)
    }

    private fun setTimer(minutes: Int?) {
        if (minutes == null) return
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        context.startActivity(intent)
    }

    private fun setAlarm(parameters: Map<String, Any?>) {
        val hour = (parameters["hour"] as? Number)?.toInt() ?: return
        val minute = (parameters["minute"] as? Number)?.toInt() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        context.startActivity(intent)
    }
}
```

Required manifest permission/query declarations for implicit intent resolution on Android 11+ (package visibility):

```xml
<queries>
    <package android:name="com.google.android.apps.maps" />
    <intent>
        <action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH" />
    </intent>
    <intent>
        <action android:name="android.provider.AlarmClock.ACTION_SET_TIMER" />
    </intent>
    <intent>
        <action android:name="android.provider.AlarmClock.ACTION_SET_ALARM" />
    </intent>
</queries>
```

---

## 4. Session Manager (Client-Side)

**Job:** track app foreground/background transitions so the backend's session logic (03 §9) has accurate activity signal, and poll for proactive/initiative messages (Philosophy §7) when the app is foregrounded after being away.

```kotlin
class SessionTracker(private val repository: ChatRepository) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // App foregrounded — check for any proactive messages sent while backgrounded
        repository.fetchPendingProactiveMessages()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // No explicit "end session" call needed — the backend infers session end
        // purely from message timestamp gaps (03 §9), so the client does not need
        // to notify on backgrounding. This keeps the client dumb per Philosophy §8.
    }
}
```

Registered in `MainActivity.onCreate`:
```kotlin
ProcessLifecycleOwner.get().lifecycle.addObserver(SessionTracker(repository))
```

**Note on push infrastructure:** at this scope, proactive/initiative messages (03 §9) are fetched via a simple poll-on-foreground call (`fetchPendingProactiveMessages`), not push notifications. This is a deliberate simplicity choice — implementing FCM push is a valid future enhancement but is out of scope for the initial build per Philosophy §9's "simplicity over cleverness" mandate. If the app is backgrounded when a proactive message is generated, the user sees it the next time they open the app, not the instant it's generated.

**[FIX 3] Endpoint and local timestamp tracking.** `fetchPendingProactiveMessages()` calls `GET /pending-messages?user_id={id}&since={timestamp}` (03 §10a) — this was previously referenced without a backing endpoint; that gap is now closed. The `since` value is the `created_at` of the last message the client has rendered (from either a `/chat` response or a previous `/pending-messages` response), persisted locally in `SharedPreferences` under a single key (e.g. `last_seen_message_at`) — no complex sync layer or server-side read-receipt tracking is needed at this scope. On each successful fetch, the client updates this stored timestamp to the `created_at` of the newest message returned before appending the results to `uiState.messages`.

---

## 5. Delay System (Client-Side)

The client does **not** compute delay — it only renders whatever `typing_duration_ms` the backend returns (Philosophy §6, 03 §8). Exact client flow in `ChatViewModel.kt`:

```kotlin
fun sendMessage(text: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(messages = it.messages + userMessage(text), isTyping = true) }

        val response = repository.sendChatMessage(text)

        delay(response.typingDurationMs) // exact duration from backend, no client-side calculation

        _uiState.update {
            it.copy(
                messages = it.messages + assistantMessage(response.reply),
                isTyping = false
            )
        }

        if (response.isCommand && response.command != null) {
            localCommandExecutor.execute(response.command)
        }
    }
}
```

The typing indicator (§2.3) is shown for exactly the `delay()` duration and then replaced by the actual message bubble — never shown for a generic/hardcoded duration, and never skipped even for command-path responses (a very short delay per 03 §8's `300ms` floor still renders a brief indicator flash, preserving a consistent interaction rhythm).

---

## 6. API Integration (Retrofit/OkHttp)

### 6.1 DTOs (`ChatDtos.kt`) — must match 03 §10 exactly

```kotlin
data class ChatRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("message") val message: String
)

data class ChatResponse(
    @SerializedName("reply") val reply: String,
    @SerializedName("typing_duration_ms") val typingDurationMs: Long,
    @SerializedName("is_command") val isCommand: Boolean,
    @SerializedName("command") val command: CommandDto? = null,
    @SerializedName("degraded") val degraded: Boolean = false
)

data class CommandDto(
    @SerializedName("intent") val intent: String,
    @SerializedName("parameters") val parameters: Map<String, @JvmSuppressWildcards Any>
)
```

### 6.2 `FridayApiService.kt`

```kotlin
interface FridayApiService {
    @POST("chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse
}
```

### 6.3 `RetrofitClient.kt`

```kotlin
object RetrofitClient {
    private const val BASE_URL = "https://your-backend.example.com/" // set per environment/build variant

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // conversational LLM calls can take a few seconds
        .build()

    val api: FridayApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FridayApiService::class.java)
    }
}
```

**Manual step:** set `BASE_URL` per build variant (debug pointing to a local/staging backend, release pointing to production) via `buildConfigField` in `build.gradle.kts` rather than hardcoding, e.g.:

```kotlin
buildTypes {
    debug {
        buildConfigField("String", "API_BASE_URL", "\"https://staging.your-backend.example.com/\"")
    }
    release {
        buildConfigField("String", "API_BASE_URL", "\"https://your-backend.example.com/\"")
    }
}
```

---

## 7. Error Handling

`ChatRepository.kt` wraps all network calls in a `NetworkResult` sealed class so the ViewModel never handles raw exceptions:

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String) : NetworkResult<Nothing>()
}

suspend fun sendChatMessage(text: String): NetworkResult<ChatResponse> = try {
    NetworkResult.Success(api.sendMessage(ChatRequest(userId = currentUserId, message = text)))
} catch (e: IOException) {
    NetworkResult.Error("network_unavailable")
} catch (e: HttpException) {
    NetworkResult.Error("server_error")
}
```

**Graceful degradation in the ViewModel:** on `NetworkResult.Error`, show a single in-character fallback bubble locally (never a raw error dialog or stack trace) — e.g. "hmm, having trouble reaching you right now, try again in a sec" — with `isTyping` set back to `false` immediately (no fake delay on a genuine failure). This mirrors the backend's own `degraded: true` response shape (03 §10) but is used for the case where the request never reached the backend at all.

If the backend responds successfully but with `degraded: true` (backend's LLM router exhausted both providers, 03 §5), render `response.reply` normally — the backend already supplies an in-character degraded message, so the client does not need special-case UI for this, just normal message rendering.

---

## 8. Manual Steps: Gradle, Manifest, Build

### 8.1 Gradle dependencies (`app/build.gradle.kts`)

```kotlin
dependencies {
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.0") // for ProcessLifecycleOwner

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

### 8.2 Manifest additions (`AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.INTERNET" />

<queries>
    <package android:name="com.google.android.apps.maps" />
    <intent><action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH" /></intent>
    <intent><action android:name="android.provider.AlarmClock.ACTION_SET_TIMER" /></intent>
    <intent><action android:name="android.provider.AlarmClock.ACTION_SET_ALARM" /></intent>
</queries>
```

### 8.3 Build commands

```bash
# From project root
./gradlew assembleDebug        # debug build, uses staging BASE_URL
./gradlew assembleRelease      # release build, uses production BASE_URL
./gradlew installDebug         # install directly to connected device/emulator
```
