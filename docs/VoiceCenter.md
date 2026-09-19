# Voice Center Implementation Analysis & Improvement Plan

## Current Voice Implementation Analysis

### 1. Voice Flow Overview

The current voice system is triggered when the dashboard loads and handles announcements through the following flow:

**Dashboard Load → DashboardViewModel → VoiceManager → Audio Output**

### 2. Current Implementation Details

#### 2.1 VoiceManager.kt - Core Voice Management
**Location**: `app/src/main/java/com/baroness/app/utils/VoiceManager.kt`

**Current Architecture**:
- **Dual TTS System**: Uses both remote TTS and system TTS as fallback
- **Remote TTS**: Points to `https://thats-baroness-p.vercel.app/api/speak` (legacy PWA endpoint)
- **Fallback System**: Android's built-in TextToSpeech when remote fails
- **Audio Playback**: Uses MediaPlayer for remote audio files
- **Message Building**: Custom message construction for announcements

**Key Components**:
```kotlin
class VoiceManager(private val context: Context) {
    private var tts: TextToSpeech? = null           // System TTS fallback
    private var mediaPlayer: MediaPlayer? = null  // For remote audio
    private val remoteTtsUrl = "https://thats-baroness-p.vercel.app/api/speak" // Legacy PWA endpoint
    private val pendingText = mutableListOf<String>() // Queue for TTS not ready
}
```

**Current Voice Flow**:
1. `speak(text)` called from DashboardViewModel
2. Checks if already playing (`isPlaying` flag)
3. Attempts remote TTS first via HTTP POST to legacy Vercel endpoint (`/api/speak`)
4. Falls back to system TTS if remote fails
5. Audio played through MediaPlayer (remote) or TextToSpeech (system)

#### 2.2 DashboardViewModel.kt - Voice Trigger Logic
**Location**: `app/src/main/java/com/baroness/app/viewmodels/DashboardViewModel.kt`

**Voice Trigger Points**:
- **Initial Load**: Lines 96, 108 - Triggers announcement when dashboard first loads
- **Manual Refresh**: Line 215 - Triggers announcement on pull-to-refresh
- **Weather Update**: Line 166 - Triggers announcement when weather data refreshes

**Session Management**:
```kotlin
companion object {
    private var hasSpokenInSession = false  // Prevents repeated announcements
}
```

**Message Construction**:
```kotlin
private fun triggerAnnouncement(profile: UserProfile?, weatherSuggestion: String?, isManual: Boolean) {
    if (isManual || !hasSpokenInSession) {
        val message = voiceManager.buildAnnouncementMessage(
            userProfile = profile,
            greeting = _greeting.value,
            weatherSuggestion = weatherSuggestion ?: "stay in your zone"
        )
        voiceManager.speak(message)
        hasSpokenInSession = true
    }
}
```

#### 2.3 Message Building Logic
**Location**: `VoiceManager.kt` lines 54-81

**Current Message Structure**:
- Greeting based on user profile
- Dynamic greeting from VibeManager (time-based persona greetings)
- Date and time formatting
- Weather suggestion integration
- Emoji removal from weather text

**Example Message**:
```
"Hi [username]. [greeting]... [intro] it's [day], [date]. The time is [time]. Just so you know, [weather suggestion]."
```

### 3. Current Implementation Issues

#### 3.1 Architecture Limitations
- **Tightly Coupled**: VoiceManager is directly instantiated in DashboardViewModel
- **No Voice Settings**: No user preferences for voice settings
- **Limited Scope**: Only used for dashboard announcements
- **No Reusability**: Cannot be easily used in other parts of the app
- **Hardcoded URL**: Remote TTS endpoint is hardcoded (legacy PWA endpoint)
- **Wrong Endpoint**: Currently using `/api/speak` (reserved for PWA) instead of dedicated `/api/baroness` endpoint
- **No Voice Selection**: Users cannot choose between different voices
- **No Speed/Pitch Control**: Voice parameters are hardcoded (pitch: 0.9f, rate: 1.0f)

#### 3.2 Technical Limitations
- **Basic Error Handling**: Limited fallback mechanisms
- **No Smart Caching**: No intelligent caching strategy - everything treated equally
- **Storage Inefficiency**: Would cache dynamic content (time, weather) unnecessarily if implemented
- **No Queue Management**: Simple boolean flag prevents overlapping speech
- **No Progress Tracking**: Limited speech progress monitoring
- **Memory Management**: Potential memory leaks with MediaPlayer
- **Network Dependency**: Remote TTS requires network connectivity

#### 3.3 User Experience Issues
- **No Voice Controls**: Users cannot adjust voice settings
- **No On/Off Toggle**: Voice cannot be disabled by users
- **Limited Context**: Only dashboard announcements, no chat message reading
- **No Persona Voices**: Same voice for both Phesty and Baroness personas
- **No Voice History**: No way to replay announcements

#### 3.4 Integration Issues
- **Settings Gap**: DrawerSoundHaptics component shows "Coming soon..." for voice settings
- **No Settings Integration**: VoiceManager not connected to SettingsViewModel
- **No Storage**: Voice preferences not persisted
- **No Permissions Handling**: No explicit audio permission requests for voice

### 4. Current Dependencies
**Build Configuration**: `app/build.gradle.kts`

**Voice-Related Dependencies**:
- No specific voice/TTS libraries (uses Android system TTS)
- OkHttp for remote TTS API calls
- Coroutines for async operations

**Permissions**: `AndroidManifest.xml`
- `RECORD_AUDIO` - Currently unused but present
- `WAKE_LOCK` - For keeping screen on during audio
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - For background audio

## Proposed Voice Center Architecture

### 5. Voice Center Design Goals

1. **Modular & Reusable**: Use anywhere in the app (chat, dashboard, settings)
2. **Provider Agnostic**: Support multiple TTS providers (Deepgram, system TTS, custom)
3. **User Configurable**: Voice settings, speed, pitch, on/off toggle via existing settings architecture
4. **Persona-Aware**: Different voices for Phesty vs Baroness using enum-based persona system
5. **Offline Capable**: Smart hybrid caching with static/dynamic content classification and fallback mechanisms
6. **Production Ready**: Proper error handling, memory management, permissions using modern AndroidX components

### 6. Proposed Architecture Components

#### 6.1 VoiceProvider Interface
```kotlin
interface VoiceProvider {
    suspend fun synthesize(text: String, voiceConfig: VoiceConfig): AudioResult
    fun getAvailableVoices(): List<VoiceOption>
    fun isAvailable(): Boolean
}

data class VoiceConfig(
    val voiceId: String,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val language: String = "en-US"
)

data class AudioResult(
    val audioData: ByteArray,
    val format: AudioFormat,
    val duration: Long
)
```

#### 6.2 TTS Provider Implementation

**Primary Provider: Vercel Proxy with OpenRouter Integration**

```kotlin
class BaronessVoiceProvider(
    private val proxyUrl: String = "https://thats-baroness-p.vercel.app/api/baroness"
) : VoiceProvider {
    
    override suspend fun synthesize(text: String, voiceConfig: VoiceConfig): AudioResult {
        // Implementation using dedicated Baroness endpoint
        // POST to https://thats-baroness-p.vercel.app/api/baroness
        // This endpoint routes to OpenRouter Deepgram Flux TTS with Edge TTS fallback
    }
}
```

**Network Request Protocol**:
- **POST Request URL**: `https://thats-baroness-p.vercel.app/api/baroness`
- **Content-Type**: `application/json`
- **Payload Format**:
```json
{
  "text": "Yoow bestie, good morning!",
  "provider": "deepgram",
  "voice": "flux-alexis-en"
}
```

**Provider Options**:
- `"deepgram"` (default): Uses OpenRouter Deepgram Flux TTS (free)
- `"murf"`: Directly uses Murf AI (skips OpenRouter)
- If not specified, defaults to `deepgram`

**Serverless Endpoint Architecture**:
- **Primary**: OpenRouter Speech API (`deepgram/flux-tts:free`) - FREE tier
- **Secondary Fallback**: Murf AI - Your existing configured provider
- **Final Fallback**: Microsoft Edge TTS (`en-US-JennyNeural`) - No API key required
- **Reserved Legacy**: `/api/speak` endpoint is strictly for PWA web app (DO NOT USE for Android)

#### 6.3 VoiceCenter Manager
```kotlin
class VoiceCenter(private val context: Context) {
    private val settingsRepository: VoiceSettingsRepository
    private val activeProvider: VoiceProvider
    private val audioPlayer: VoiceAudioPlayer
    private val cacheManager: VoiceCacheManager
    
    suspend fun speak(text: String, context: VoiceContext)
    suspend fun speakChunked(text: String, context: VoiceContext) // For sentence-level processing
    fun stop()
    fun isPlaying(): Boolean
    fun getAvailableVoices(): List<VoiceOption>
    
    private fun isStaticContent(text: String): Boolean {
        // Pattern matching to detect cacheable static phrases
        // vs dynamic content like time, weather, dates
    }
    
    private fun chunkText(text: String): List<String> {
        // Split by punctuation for sentence-level processing
    }
}
```

#### 6.4 Persona-Based Voice System
```kotlin
data class PersonaVoiceProfile(
    val voiceId: String,
    val pitch: Float,
    val speed: Float,
    val defaultTone: String
)

enum class AppPersona(val profile: PersonaVoiceProfile) {
    PHESTY(PersonaVoiceProfile(
        voiceId = "phesty_voice", 
        pitch = 0.95f, 
        speed = 1.05f, 
        defaultTone = "casual"
    )),
    BARONESS(PersonaVoiceProfile(
        voiceId = "baroness_voice", 
        pitch = 1.05f, 
        speed = 1.0f, 
        defaultTone = "warm"
    ))
}

// SettingsViewModel extension
class VoiceSettings(
    val enabled: Boolean = true,
    val provider: String = "deepgram",
    val globalVoiceId: String = "default",
    val globalSpeed: Float = 1.0f,
    val globalPitch: Float = 1.0f,
    val usePersonaVoices: Boolean = true,
    val phestyVoiceOverride: String? = null,
    val baronessVoiceOverride: String? = null
)
```

### 7. Implementation Plan

#### Phase 1: Core Voice Center Infrastructure
1. **Create VoiceProvider Interface**
   - Define contract for TTS providers
   - Create VoiceConfig and AudioResult data classes
   - Define VoiceOption for available voices

2. **Implement Baroness Voice Provider**
   - Create Vercel proxy client for `/api/baroness` endpoint
   - Implement POST request handling with provider selection (deepgram/murf)
   - Handle OpenRouter Deepgram Flux TTS, Murf AI, and Edge TTS fallbacks
   - Add audio format conversion and error handling
   - Ensure separation from legacy `/api/speak` PWA endpoint
   - Support provider parameter for manual Murf AI selection when needed

3. **Create VoiceCenter Manager**
   - Implement provider selection logic
   - Add audio playback management
   - Implement queue system for multiple texts
   - Add state management (playing, paused, stopped)

4. **Add Voice Cache Manager**
   - Implement smart hybrid caching (static vs dynamic content detection)
   - Add sentence-level chunking for parallel audio processing
   - Implement strict storage limits (20-30MB max) with LRU eviction
   - Add TTL expiration for time-sensitive content
   - Handle offline fallback support

#### Phase 2: Settings Integration
1. **Create VoiceSettingsRepository**
   - Extend existing SettingsRepository using DataStore for voice preferences
   - Add persistence for voice settings (enabled, provider, voice IDs, speed, pitch)
   - Implement default voice configurations for both personas
   - Maintain consistency with existing settings architecture

2. **Update SettingsViewModel**
   - Add voice settings state management using StateFlow
   - Implement voice preview functionality with VoiceCenter
   - Add voice settings validation and conflict resolution
   - Integrate with existing settings preview/apply pattern

3. **Update DrawerSoundHaptics Component**
   - Replace "Coming soon..." with actual voice settings UI
   - Add voice provider selection (Deepgram, System TTS, etc.)
   - Add voice preview and testing functionality
   - Add speed/pitch controls with real-time preview
   - Add persona-specific voice selection (Phesty vs Baroness)
   - Add master on/off toggle for voice features

#### Phase 3: App-Wide Integration
1. **Update DashboardViewModel**
   - Replace VoiceManager with VoiceCenter
   - Add voice context for dashboard announcements
   - Implement persona-based voice selection

2. **Add Chat Voice Features**
   - Implement message reading functionality
   - Add voice commands for chat actions
   - Integrate with ChatRoomScreen

3. **Add Voice to Other Screens**
   - Wishlist screen voice announcements
   - Photo gallery voice descriptions
   - Settings screen voice guidance

#### Phase 4: Advanced Features
1. **Persona-Based Voices**
   - Different voice configurations for Phesty and Baroness
   - Persona-specific voice settings
   - Dynamic voice switching based on context

2. **Voice Commands**
   - Implement voice command recognition
   - Add voice navigation
   - Voice-controlled actions

3. **Audio Enhancements**
   - Background music integration
   - Sound effects for interactions
   - Haptic feedback synchronization

### 8. Technical Implementation Details

#### 8.1 TTS API Integration

**Baroness Android App Endpoint**
**Endpoint**: `https://thats-baroness-p.vercel.app/api/baroness`

**Request Format**:
```json
{
  "text": "Yoow bestie, good morning!",
  "voice": "flux-alexis-en"
}
```

**Serverless Implementation Details**:
The Vercel endpoint (`api/baroness.js`) implements a robust triple-fallback chain:

1. **Primary: OpenRouter Deepgram Flux TTS (FREE)**
   - Target: `https://openrouter.ai/api/v1/audio/speech`
   - Model: `deepgram/flux-tts:free`
   - Headers: Authorization, HTTP-Referer, X-Title for OpenRouter API
   - Response: MP3 audio buffer
   - Activates when provider parameter is 'deepgram' (default)

2. **Secondary Fallback: Murf AI**
   - Target: `https://api.murf.ai/v1/speech/generate`
   - Voice: `en-US-marcus` (configurable)
   - Model: `GEN2`
   - Style: `conversational`
   - Format: `MP3`, `MONO`
   - Activates when OpenRouter fails or rate-limits
   - Uses your existing Murf API configuration

3. **Final Fallback: Microsoft Edge TTS**
   - Voice: `en-US-JennyNeural`
   - Format: `audio-24khz-96kbitrate-mono-mp3`
   - Activates when both OpenRouter and Murf fail
   - No API key required

**Android Implementation**:
```kotlin
// OkHttp request to Vercel proxy
val request = Request.Builder()
    .url("https://thats-baroness-p.vercel.app/api/baroness")
    .post(json.toRequestBody("application/json".toMediaType()))
    .build()
```

**Response Handling**:
- Audio data in MP3 format from either OpenRouter or Edge TTS
- Error handling for API limits, network issues, and fallback failures
- Rate limiting and retry logic managed by Vercel endpoint
- Final fallback to Android system TTS if Vercel endpoint completely fails

**Important Notes**:
- Legacy `/api/speak` endpoint is reserved for PWA web app only
- All Android/Kotlin requests MUST use `/api/baroness` endpoint
- OpenRouter API key is managed server-side in Vercel environment variables

#### 8.2 Audio Playback Architecture
**Components**:
- **AndroidX Media3 ExoPlayer** for modern audio playback (replaces deprecated standalone ExoPlayer)
- Audio focus management
- Background service for continuous playback
- Bluetooth/headphone detection

**Features**:
- Play/pause/stop controls
- Seek functionality
- Volume normalization
- Audio session management
- Better integration with AndroidX components
- Long-term support and updates

#### 8.3 Smart Hybrid Caching Strategy

**Core Philosophy**: Cache only what makes sense - static phrases get cached permanently, dynamic content bypasses cache entirely.

**Selective Dynamic Caching (Static vs Dynamic Parsing)**:
- **Static UI Phrases (Cached 100%)**: Recurring phrases like "Good morning, Phesty", "Here is your update for today", "Searching the web now", "Stay in your zone" get cached after first synthesis
- **Dynamic Content (Never Cached)**: Time numbers, live weather degrees, live dates, or LLM-generated content bypass disk cache completely and fetch fresh audio on-demand

**Sentence-Level Chunking & Parallel Playback**:
```
1. Sentence 1: "Good morning, Phesty!" → Cache Hit! (0-10ms playback)
2. Sentence 2: "The time is 7:15 AM and it's currently 22 degrees." → Cache Miss! → API call (parallel while Sentence 1 plays)
3. Sentence 3: "You have no pending tasks." → Cache Hit! (seamless playback after Sentence 2)
```

**Storage Management**:
```
App Storage / cache / voice_cache /
 ├── max_size = 30 MB (strict disk usage limit)
 └── strategy = LRU (Least Recently Used) + TTL (Time to Live)
```

**Cache Behavior by Content Type**:

| Content Type | Example | Cache Behavior |
| --- | --- | --- |
| **Static Greetings / UI States** | "Good morning", "On it, boss" | **Cached permanently** until storage cap reached |
| **Common System Prompts** | "Checking your calendar now" | **Cached permanently** after first playback |
| **Dynamic Dashboard Announcements** | "The time is 7:15 AM" | **Bypasses cache** → Streams live via API or system fallback |
| **LLM Chat Responses** | Multi-paragraph AI answers | **Streamed sentence-by-sentence** → Cleared from cache on app close |

**Implementation Details**:
- **Static Phrase Detection**: Regex/pattern matching to identify cacheable vs non-cacheable content
- **Sentence Chunking**: Split by punctuation (`.`, `!`, `?`) for parallel processing
- **Parallel Audio Fetch**: While cached sentence plays, fetch next sentence's audio in background
- **Strict Storage Limits**: 20-30MB max with automatic LRU eviction
- **TTL Expiration**: Time-sensitive files expire after 1-2 hours automatically
- **Smart Hash Keys**: `MD5("baroness_voice_1.0_good_morning") → a8f9c12.mp3`

#### 8.4 Smart Caching Implementation Details

**Static Content Detection Algorithm**:
```kotlin
private fun isStaticContent(text: String): Boolean {
    // Patterns that indicate static, cacheable content
    val staticPatterns = listOf(
        Regex("^(good morning|good afternoon|good evening)", RegexOption.IGNORE_CASE),
        Regex("^(here's your update|on it|searching|checking)", RegexOption.IGNORE_CASE),
        Regex("^(stay in your zone|you're all set|got it)", RegexOption.IGNORE_CASE)
    )
    
    // Patterns that indicate dynamic, non-cacheable content
    val dynamicPatterns = listOf(
        Regex("\\d{1,2}:\\d{2}\\s*(AM|PM)", RegexOption.IGNORE_CASE), // Time
        Regex("\\d+\\s*degrees?", RegexOption.IGNORE_CASE), // Temperature
        Regex("(monday|tuesday|wednesday|thursday|friday|saturday|sunday)", RegexOption.IGNORE_CASE), // Days
        Regex("(january|february|march|april|may|june|july|august|september|october|november|december)", RegexOption.IGNORE_CASE) // Months
    )
    
    // If contains dynamic patterns, bypass cache
    if (dynamicPatterns.any { it.containsMatchIn(text) }) {
        return false
    }
    
    // If matches static patterns, cache it
    return staticPatterns.any { it.containsMatchIn(text) }
}
```

**Sentence Chunking for Parallel Processing**:
```kotlin
private fun chunkText(text: String): List<String> {
    // Split by sentence-ending punctuation while preserving delimiters
    return text.split(Regex("(?<=[.!?])\\s+"))
        .filter { it.isNotBlank() }
        .map { it.trim() }
}
```

**Parallel Audio Processing Pipeline**:
```kotlin
suspend fun speakChunked(text: String, voiceContext: VoiceContext) {
    val sentences = chunkText(text)
    val audioQueue = ArrayDeque<Pair<String, ByteArray?>>()
    
    sentences.forEachIndexed { index, sentence ->
        if (isStaticContent(sentence)) {
            // Check cache first
            val cachedAudio = cacheManager.get(sentence, voiceContext.config)
            if (cachedAudio != null) {
                audioQueue.add(sentence to cachedAudio)
            } else {
                // Fetch and cache for future use
                val audio = activeProvider.synthesize(sentence, voiceContext.config)
                cacheManager.put(sentence, voiceContext.config, audio.audioData)
                audioQueue.add(sentence to audio.audioData)
            }
        } else {
            // Dynamic content - fetch fresh, don't cache
            val audio = activeProvider.synthesize(sentence, voiceContext.config)
            audioQueue.add(sentence to audio.audioData)
        }
    }
    
    // Play sequentially with parallel fetching for next sentences
    playQueueSequentially(audioQueue)
}
```

**Cache Storage Structure**:
```
context.cacheDir/voice_cache/
├── static_phrases/
│   ├── a8f9c12.mp3 (MD5 of "good_morning")
│   ├── b3d8e45.mp3 (MD5 of "on_it_boss")
│   └── c7f9a23.mp3 (MD5 of "checking_calendar")
├── temp_dynamic/
│   └── (cleared on app close)
└── cache_metadata.json
    ├── size: 25MB
    ├── last_cleanup: timestamp
    └── entry_count: 15
```

#### 8.6 Vercel Endpoint Implementation

**Serverless Function: `api/baroness.js`**

The Vercel endpoint implements a robust serverless TTS service with triple-fallback architecture:

```javascript
// api/baroness.js
export default async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
  const { 
    text, 
    provider = 'deepgram', 
    voice = 'flux-alexis-en' 
  } = body;

  if (!text) {
    return res.status(400).json({ error: 'Missing text field' });
  }

  const openRouterKey = process.env.OPENROUTER_API_KEY;
  const murfKey = process.env.MURF_API_KEY;

  // ---------- 1. PRIMARY: OpenRouter Deepgram Flux TTS (FREE) ----------
  if (provider === 'deepgram' && openRouterKey) {
    try {
      const response = await fetch('https://openrouter.ai/api/v1/audio/speech', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${openRouterKey}`,
          'Content-Type': 'application/json',
          'HTTP-Referer': 'https://thats-baroness-p.vercel.app',
          'X-Title': 'Baroness Android App'
        },
        body: JSON.stringify({
          model: 'deepgram/flux-tts:free',
          input: text,
          voice: voice,
          response_format: 'mp3'
        })
      });

      if (response.ok) {
        const audioBuffer = await response.arrayBuffer();
        res.setHeader('Content-Type', 'audio/mpeg');
        return res.send(Buffer.from(audioBuffer));
      } else {
        const errText = await response.text();
        console.error(`OpenRouter TTS error (${response.status}):`, errText);
      }
    } catch (err) {
      console.error('OpenRouter TTS exception:', err.message);
    }
  }

  // ---------- 2. SECONDARY FALLBACK: Murf AI ----------
  if (murfKey) {
    try {
      const murfResponse = await fetch('https://api.murf.ai/v1/speech/generate', {
        method: 'POST',
        headers: {
          'api-key': murfKey,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          text: text,
          voiceId: "en-US-marcus",     
          modelVersion: "GEN2",        
          style: 'conversational',
          rate: 0,
          pitch: 0,
          format: 'MP3',
          channelType: 'MONO',
          encodeAsBase64: false,
        }),
      });

      if (murfResponse.ok) {
        const murfData = await murfResponse.json();
        const audioResponse = await fetch(murfData.audioFile);
        const audioBuffer = await audioResponse.arrayBuffer();
        res.setHeader('Content-Type', 'audio/mpeg');
        return res.send(Buffer.from(audioBuffer));
      } else {
        const errorText = await murfResponse.text();
        console.error(`Murf AI error (${murfResponse.status}):`, errorText);
      }
    } catch (err) {
      console.error('Murf AI exception:', err.message);
    }
  }

  // ---------- 3. FINAL FALLBACK: Edge TTS ----------
  try {
    const { synthesize: edgeTTS } = await import('@echristian/edge-tts');
    const edgeResult = await edgeTTS({
      text: text,
      voice: 'en-US-JennyNeural',
      outputFormat: 'audio-24khz-96kbitrate-mono-mp3',
    });

    let audioBuffer = Buffer.isBuffer(edgeResult.audio) 
      ? edgeResult.audio 
      : Buffer.from(edgeResult.audio);

    res.setHeader('Content-Type', 'audio/mpeg');
    return res.send(audioBuffer);
  } catch (err) {
    console.error('Edge TTS fallback failed:', err.message);
    return res.status(500).json({ error: 'All TTS engines failed, mate' });
  }
}
```

**Key Features**:
- **Method validation**: Only POST requests accepted
- **Payload validation**: Ensures text field is present
- **Provider selection**: Supports 'deepgram' (default) and 'murf' provider options
- **Triple-fallback architecture**: OpenRouter → Murf AI → Edge TTS
- **Primary OpenRouter integration**: Uses `deepgram/flux-tts:free` model (completely free)
- **Secondary Murf AI fallback**: Leverages your existing Murf configuration
- **Final Edge TTS fallback**: No API key required, always available
- **Proper error handling**: Logs failures and returns appropriate status codes
- **Audio format standardization**: Always returns MP3 format
- **Server-side authentication**: Both OpenRouter and Murf API keys in environment variables

#### 8.7 Error Handling & Fallbacks
**Fallback Chain**:
1. **Vercel Proxy - OpenRouter Deepgram Flux TTS** (primary - server-side, FREE)
2. **Vercel Proxy - Murf AI** (secondary - server-side, your existing provider)
3. **Vercel Proxy - Microsoft Edge TTS** (tertiary - server-side, no API key)
4. **Android System TTS** (quaternary - client-side)
5. **Cached Audio** (quinary - for static phrases)
6. **Silent Mode** (final fallback)

**Server-side Fallbacks (Vercel)**:
- OpenRouter API failures → Murf AI activation
- OpenRouter rate limits → Murf AI activation
- Murf AI failures → Edge TTS activation
- Both API keys missing → Edge TTS activation
- Network issues → Edge TTS activation
- All server-side failures → Android system TTS

**Client-side Fallbacks (Android)**:
- Vercel endpoint completely unreachable → System TTS
- Audio playback failures → System TTS
- Static phrase cache hits → Instant playback (0-10ms)

**Error Scenarios**:
- Network connectivity issues (handled by triple server-side fallbacks + Android system TTS)
- API rate limits (OpenRouter → Murf → Edge TTS → System TTS)
- Invalid voice configurations (fallback to default voice)
- Audio playback failures (MediaPlayer → System TTS)
- Vercel endpoint downtime (direct system TTS)
- Complete provider failure (Edge TTS always available as final server-side fallback)

### 9. Configuration Management

#### 9.1 Build Configuration
**Add to build.gradle.kts**:
```kotlin
// Modern Audio playback using AndroidX Media3
implementation("androidx.media3:media3-exoplayer:1.11.1")
implementation("androidx.media3:media3-ui:1.11.1")
implementation("androidx.media3:media3-session:1.11.1")
```

**Note**: No additional API keys needed in build configuration. The Vercel proxy handles all TTS provider authentication server-side using OpenRouter API key stored in Vercel environment variables.

#### 9.2 Environment Configuration
**Server-side Configuration (Vercel)**:
- **OpenRouter API Key**: `OPENROUTER_API_KEY` in Vercel environment variables
- **Murf AI API Key**: `MURF_API_KEY` in Vercel environment variables
- Both API keys managed server-side for enhanced security
- Vercel endpoint handles authentication and provider routing

**Android Configuration**:
- No additional API keys required in `local.properties`
- The Vercel proxy handles all TTS provider authentication
- Simplified security by keeping API keys server-side only
- Provider selection handled via request payload parameter

#### 9.3 Permissions
**Update AndroidManifest.xml**:
```xml
<!-- Audio playback -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- Background playback -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### 10. Migration Strategy

#### 10.1 Backward Compatibility
- Keep existing VoiceManager during transition
- Gradual migration of voice calls from VoiceManager to VoiceCenter
- Feature flags for new voice system (VoiceCenter vs legacy VoiceManager)
- Enhance existing Vercel proxy to support new TTS providers
- Fallback to old system if needed during transition

#### 10.2 Data Migration
- Migrate existing voice settings to new VoiceSettings format
- Preserve user preferences during SettingsRepository migration
- Clear old cache files and implement new hash-based caching
- Update settings schema to support persona-based voice profiles
- Migrate any existing user voice customizations

#### 10.3 Testing Strategy
- Unit tests for VoiceProvider implementations
- Integration tests for VoiceCenter
- UI tests for voice settings
- Performance testing for audio caching

### 11. Success Metrics

#### 11.1 Technical Metrics
- Voice synthesis latency < 2 seconds
- Cache hit rate > 80%
- Error rate < 1%
- Memory usage < 50MB for voice operations

#### 11.2 User Experience Metrics
- Voice settings usage rate
- Voice feature engagement
- User satisfaction with voice quality
- Reduction in voice-related complaints

### 12. Future Enhancements

#### 12.1 Advanced Voice Features
- Voice cloning for personalized responses
- Emotion-based voice modulation
- Multi-language support
- Voice authentication

#### 12.2 Integration Opportunities
- Voice-controlled navigation
- Voice search functionality
- Voice-activated commands
- Voice-based accessibility features

#### 12.3 Analytics & Improvement
- Voice usage analytics
- Quality feedback collection
- A/B testing for voice providers
- Performance optimization based on usage patterns

## Technical Corrections & Improvements

### Key Architectural Improvements Based on Review

1. **Modern Audio Playback**: Replaced deprecated standalone ExoPlayer with AndroidX Media3 ExoPlayer
   - Better integration with AndroidX components
   - Long-term support and active maintenance
   - Improved performance and feature set

2. **Clarified TTS Provider Strategy**:
   - **OpenRouter Integration**: Confirmed OpenRouter Speech API support for TTS via `deepgram/flux-tts:free`
   - **Murf AI Integration**: Added Murf AI as secondary fallback leveraging existing configuration
   - **Vercel Proxy Architecture**: Dedicated `/api/baroness` endpoint for Android app
   - **Endpoint Separation**: Legacy `/api/speak` reserved for PWA, new `/api/baroness` for Android
   - **Server-side Authentication**: Both OpenRouter and Murf API keys managed in Vercel environment variables
   - **Triple Fallback System**: OpenRouter (free) → Murf AI (existing) → Edge TTS (no key) → System TTS for maximum reliability

3. **Optimized Persona System**:
   - Enum-based persona system with `PersonaVoiceProfile`
   - Reduced memory footprint compared to separate configurations
   - Type-safe persona switching and voice management
   - Centralized voice configuration per persona

4. **Triple-Fallback TTS Architecture**:
   - **Primary**: OpenRouter Deepgram Flux TTS (completely free tier)
   - **Secondary**: Murf AI (leverages existing configuration)
   - **Tertiary**: Microsoft Edge TTS (no API key required)
   - **Final**: Android System TTS (always available)
   - Makes the system practically indestructible with multiple redundancy layers

5. **Enhanced Smart Caching Strategy**:
   - **Hybrid approach**: Static phrases cached permanently, dynamic content bypasses cache
   - **Sentence-level chunking**: Split text for parallel audio processing with latency masking
   - **Strict storage limits**: 20-30MB max with LRU eviction and TTL expiration
   - **Intelligent detection**: Pattern matching to identify cacheable vs non-cacheable content
   - **Latency masking**: Play cached sentences while fetching dynamic content in parallel
   - **Storage optimization**: Prevents cache bloat from time-sensitive dynamic content (time, weather, dates)
   - **Smart content classification**: Static UI phrases cached 100%, dynamic content bypassed completely

6. **Settings Architecture Integration**:
   - Leveraging existing DataStore-based SettingsRepository
   - Consistent with current app settings pattern
   - Proper state management using StateFlow
   - Preview/apply pattern for voice settings

7. **Vercel Proxy Architecture**:
   - **Dedicated Android Endpoint**: `/api/baroness` for all Kotlin app requests
   - **Triple-Fallback Chain**: OpenRouter (free) → Murf AI (existing) → Edge TTS (no key)
   - **Security**: Both OpenRouter and Murf API keys managed server-side, no client-side credentials
   - **Endpoint Separation**: Clear separation between PWA (`/api/speak`) and Android (`/api/baroness`)
   - **Free Tier Optimization**: Primary OpenRouter `deepgram/flux-tts:free` for cost-effective TTS
   - **Existing Provider Integration**: Murf AI leverages your current configuration for enhanced reliability

## Conclusion

The current voice implementation provides basic TTS functionality but lacks the sophistication needed for a production-ready voice system. The proposed Voice Center architecture addresses these limitations by:

1. **Modernizing the Stack**: Moving from hardcoded remote TTS to flexible provider architecture with modern AndroidX Media3
2. **Improving Architecture**: Creating a modular, reusable voice system with proper persona management
3. **Enhancing User Experience**: Adding configurable voice settings and persona support through existing settings UI
4. **Ensuring Reliability**: Implementing proper smart hybrid caching, triple-fallback TTS system, and comprehensive error handling
5. **Enabling Future Growth**: Providing extensible architecture for advanced voice features and multiple TTS providers
6. **Maximizing Redundancy**: Triple-fallback TTS architecture (OpenRouter → Murf AI → Edge TTS → System TTS) makes the system practically indestructible

This plan provides a clear roadmap for transforming the basic voice announcements into a sophisticated voice center that can be used throughout the application while maintaining backward compatibility and ensuring a smooth transition for users. The technical corrections ensure the implementation uses modern, supported libraries and follows best practices for Android development.