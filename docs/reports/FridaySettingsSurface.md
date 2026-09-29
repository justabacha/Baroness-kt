# PHASE 5B — FRIDAY Settings Surface Report

## Document Metadata
- **Phase**: Phase 5B — FRIDAY Settings Surface Decision & Implementation
- **Target Repository Root**: `C:\Baroness_Core\Baroness-kt`
- **Report File**: `docs/reports/FridaySettingsSurface.md`
- **Audit & Implementation Date**: Post-Phase 5A Backend Tightening
- **Decision Status**: **INFORMATIONAL & STATUS SURFACE IMPLEMENTED**

---

## 1. Existing FRIDAY User-Controllable Settings Classification

Every potential setting candidate was audited against current repository source code (`SettingsViewModel.kt`, `SettingsRepository.kt`, `VoiceCenter.kt`, `contextBuilder.ts`, `personality.ts`, `FridayChatViewModel.kt`) and classified according to the 5 mandatory audit categories:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        SETTING CLASSIFICATION                          │
├──────────────────────────┬─────────────────────────────────────────────┤
│ Classification Category  │ Candidate Setting Domains                   │
├──────────────────────────┼─────────────────────────────────────────────┤
│ REAL + USER-CONTROLLABLE │ Display Name, Avatar, Persona Assignment    │
│ NOW (Profile Domain)     │ (Managed under Profile & Identity domain)   │
├──────────────────────────┼─────────────────────────────────────────────┤
│ REAL BUT INTERNAL —      │ Groq/Gemini LLM Routing, Classifier Model,  │
│ DO NOT EXPOSE            │ Reflection Model, Embedding Vector Dim,     │
│                          │ Character Delays, Hardcoded Personality     │
├──────────────────────────┼─────────────────────────────────────────────┤
│ REAL BUT OWNED BY        │ Voice Provider, Voice Selection, Speed,     │
│ ANOTHER SETTINGS DOMAIN  │ Pitch, Voice Preview, Director Notes,       │
│                          │ Persona Voice Opt, Clock Voice Readout      │
├──────────────────────────┼─────────────────────────────────────────────┤
│ BACKEND SUPPORT REQUIRED │ Memory Editing/Deletion, Memory Reset API,  │
│ BEFORE UI                │ Proactive Check-in Toggle, Command          │
│                          │ Permissions Matrix, Context Depth Sliders   │
├──────────────────────────┼─────────────────────────────────────────────┤
│ NOT CURRENTLY            │ Personality/Tone Sliders, AI Model Picker,  │
│ IMPLEMENTED              │ FRIDAY Enable/Disable Global Switch         │
└──────────────────────────┴─────────────────────────────────────────────┘
```

---

## 2. Internal FRIDAY Mechanisms (Must Remain Hidden)

The following internal execution choices were verified as pure runtime implementation details and MUST NOT be exposed as fake user settings:

- **LLM Provider Routing**: Primary Groq `openai/gpt-oss-120b` -> Fallback Gemini `gemini-1.5-flash` -> Static fallback text (`llmRouter.ts`).
- **Classifier Engine**: Groq `openai/gpt-oss-20b` JSON classifier (`classifier.ts`).
- **Reflection Engine**: Gemini `gemini-3.5-flash` entity/memory extractor (`friday-reflection/index.ts`).
- **Vector Embedding Engine**: Gemini `gemini-embedding-001` 768-dimensional vector generator (`contextBuilder.ts`).
- **Typing Delay Calculation**: Dynamic delay calculator based on response length and sentiment (`delay.ts`).
- **Internal Action Request Recording**: Server-side audit logging in `public.friday_action_requests`.

---

## 3. Settings Owned by Sound & Haptics

The forensic audit confirmed that all speech synthesis, TTS audio configuration, and voice settings belong strictly to the **Sound & Haptics** domain (`SettingsViewModel.kt`, `VoiceCenter.kt`, `ClockSoundPlayer.kt`) and are ALREADY fully exposed on `SoundHapticsSettingsPage.kt` (`settings/sound`).

The following settings MUST NOT be duplicated on a FRIDAY page:
- **Voice Provider**: `voiceProvider` (`deepgram` / `murf` / `edge`)
- **Voice Selection**: `voiceId` (Asteria, Luna, Stella, Marcus, Jenny, etc.)
- **Voice Speed Slider**: `voiceSpeed` (0.5x .. 2.0x)
- **Voice Pitch Slider**: `voicePitch` (0.5x .. 1.5x)
- **Voice Sample Audition**: `previewVoice()` / `stopVoice()`
- **Voice Director Note**: `directorNote` (TTS style prompt)
- **Persona Voice Optimization**: `usePersonaVoices` (Auto voice signatures for Phesty / Baroness)
- **Clock Voice Readout**: `clockVoiceAnnounce` (Spoken time readouts)
- **Alarm / Timer Sounds & Vibration**: `alarmSoundOption`, `timerChimeOption`, `alarmVibrationPattern`

---

## 4. Memory Control Status

- `[BACKEND SUPPORT REQUIRED BEFORE UI]`
- **Repository Evidence**: FRIDAY maintains active vector memory retrieval (`match_friday_memories` RPC, 768-dim embeddings), entity tracking (`friday_entities`), and session summaries (`friday_sessions`).
- **Current Limitation**: No safe, user-facing API or DataStore setting exists to edit individual memories, delete specific entity records, or purge all vector memories.
- **Decision**: Destructive controls like "Clear Memory" or "Reset FRIDAY" were **NOT created** in UI during this phase, as no safe backend reset API exists.

---

## 5. Personality Control Status

- `[BACKEND SUPPORT REQUIRED BEFORE UI]` / `[REAL BUT INTERNAL]`
- **Repository Evidence**: FRIDAY core personality (`getPersonalityBlock`) is hardcoded in `supabase/functions/friday-orchestrator/personality.ts`. Dynamic mood is recorded in `profiles.friday_vibe`.
- **Current Limitation**: No user-controlled personality sliders (e.g. friendliness, sarcasm, formality) exist in DataStore or the database.
- **Decision**: Fake personality sliders or tone toggles were **NOT created**.

---

## 6. Proactive Behavior Control Status

- `[BACKEND SUPPORT REQUIRED BEFORE UI]`
- **Repository Evidence**: Proactive outreach is executed by the server-side cron job `friday-initiative/index.ts` based on idle duration and follow-up-worthy memories.
- **Current Limitation**: No user-facing toggle or frequency setting exists in DataStore or the database.
- **Decision**: Proactive behavior toggles were **NOT created**.

---

## 7. Command Permission Control Status

- `[BACKEND SUPPORT REQUIRED BEFORE UI]`
- **Repository Evidence**: Commands are classified by `classifier.ts`, validated against `ACTION_ALLOWLIST` in `_shared/action-schema.ts`, recorded in `friday_action_requests`, and executed locally via `LocalCommandExecutor.kt`.
- **Current Limitation**: No user-controlled permission matrix (e.g. "Allow music commands", "Disallow alarm commands") exists in DataStore.
- **Decision**: Command permission toggles were **NOT created**.

---

## 8. Final Decision on the FRIDAY Settings Surface

### Decision Rationale:
Because all existing user-controllable voice/speech settings belong to **Sound & Haptics**, and all internal AI models, memory vector stores, and command allowlists run automatically without user-facing settings tables, inventing fake sliders or duplicating voice controls on `settings/friday` would violate architectural rules.

Therefore, the **FRIDAY Settings Page (`FridaySettingsPage.kt`)** was implemented as a clean, truthful, Layer 2 **Informational & Capabilities Status Surface**:

1. **System Status Card**: Displays active companion status ("System Active") and explains that conversation orchestration, context retrieval, and command execution run automatically.
2. **Core Capabilities Card**: Displays confirmed operational features with Material vector icons (`Icons.Default.Chat`, `Icons.Default.Psychology`, `Icons.Default.Terminal`):
   - Realtime Conversation
   - Contextual Memory & Entities
   - Command Governance
3. **Voice & Speech Routing Card**: Explains that TTS speech synthesis, voice provider selection, speed/pitch sliders, and spoken readouts are managed centrally under **Sound & Haptics**, providing a direct navigation button (`navController.navigate("settings/sound")`).

---

## 9. Files Changed

1. **`app/src/main/java/com/baroness/app/screens/settings/FridaySettingsPage.kt`**
   - Created clean Layer 2 informational & status page following Material 3 design rules (`Scaffold`, `MaterialTheme.colorScheme.background`, standard `TopAppBar`, lightweight alpha cards, proper Material vector icons, no emoji glyphs, no `Haze`, no `DynamicBackground`).
2. **`app/src/main/java/com/baroness/app/screens/settings/SettingsCenterScreen.kt`**
   - Wired `"friday"` category card click handler to `navController.navigate("settings/friday")`.
3. **`app/src/main/java/com/baroness/app/MainActivity.kt`**
   - Registered `composable("settings/friday") { FridaySettingsPage(navController) }` in `NavHost`.

---

## 10. Verification Results

- **Gradle Kotlin Compilation**: Executed `./gradlew app:compileDebugKotlin` — **BUILD SUCCESSFUL** with zero compilation errors.
- **Navigation Stack**: Verified that tapping the FRIDAY card on `SettingsCenterScreen` smoothly navigates to `settings/friday`, and tapping back returns to `SettingsCenterScreen`.
- **Cross-Domain Integrity**: Verified zero Sound & Haptics settings were duplicated.
- **Single State Engine**: Verified no duplicate ViewModels (`FridaySettingsViewModel`) or DataStore files were created.
- **Visual Compliance**: Verified strict adherence to Layer 2 visual rules (`Scaffold`, `MaterialTheme.colorScheme.background`, no full-screen `DynamicBackground`, no `Haze`).

---

## 11. Deferred FRIDAY Settings Requiring Future Backend Work

The following candidate settings are explicitly documented for future implementation once safe backend APIs and database tables are established:

- **Memory Management API**: Endpoints to view, edit, or purge vector memories.
- **Personality Customization**: Database-backed personality profile table (`friday_personality`).
- **Proactive Initiative Toggle**: User preference in DataStore for enabling/disabling proactive outreach.
- **Command Permission Matrix**: Granular allow/deny toggles per device action intent.
- **Context Depth Budget Sliders**: User controls for working memory turn limits.

---

FRIDAY SETTINGS SURFACE: INFORMATIONAL ONLY

Summary: Because FRIDAY has no user-controllable settings that are distinct from Sound & Haptics or internal orchestrator logic, the FRIDAY page was implemented as a clean, truthful informational & capabilities status surface. It communicates active companion status, vector memory capabilities, and command governance rules while routing speech/TTS controls directly to Sound & Haptics without creating fake controls or duplicating settings domains.
