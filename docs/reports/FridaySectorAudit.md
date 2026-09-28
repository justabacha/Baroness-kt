# FRIDAY Sector Audit

## Audit Scope and Method

This audit is repository-grounded and based on the current code in this repository only. Architecture and design documents were reviewed as context, but the code is the final authority. Where documentation describes FRIDAY functionality that the current repository does not actually implement, the discrepancy is called out explicitly.

This task is an audit only. No Kotlin, TypeScript, SQL, Edge Function, UI, database schema, or configuration changes were made.

## Architecture Documents Reviewed

- docs/reports/SettingsCenter.md
- docs/reports/Baroness_Version2.md
- docs/reports/SoundSectorAudit.md
- docs/reports/DrawerAudit.md
- docs/reports/friday_review.md
- docs/FRIDAY/Friday_Architecture.md
- docs/FRIDAY/FridayV1.md
- repository FRIDAY notes and implementation reports

## Report Created

- File created: docs/reports/FridaySectorAudit.md
- Status: audit complete
- Source code modified: no

## Executive Summary

The repository contains a real FRIDAY backend and a real FRIDAY chat flow, but not a fully designed FRIDAY settings domain. The current system is operational in a narrow, code-backed way:

- User messages are sent from Android to a Supabase-backed FRIDAY orchestrator.
- The orchestrator classifies messages as COMMAND or CONVERSATION.
- It assembles identity, memory, session, and entity context for the LLM.
- It persists responses and pushes them through chat_sync_pipe for local UI update.
- A reflection job extracts memory and entity facts from idle chat history.
- A proactive initiative job sends check-in messages based on follow-up-worthy memories.

What is not currently a real settings domain:
- FRIDAY personality configuration is mostly prompt-generated and hardcoded.
- Model/provider selection is internal runtime configuration, not a user setting.
- Memory retention and reset controls do not exist as a persisted user feature.
- Command permissions are not represented as a real user setting or policy layer.
- FRIDAY voice/direction settings are in the sound domain rather than a FRIDAY identity system.

## 1. FRIDAY Code Inventory

### 1.1 Backend / Edge Functions

Files found and their actual roles:

- supabase/functions/friday-orchestrator/index.ts
  - Runtime owner: server-side FRIDAY orchestrator.
  - Actual behavior: session management, message classification, command insertion, context building, LLM response orchestration, response persistence, command payload dispatch.
  - Classification: IMPLEMENTED

- supabase/functions/friday-orchestrator/classifier.ts
  - Runtime owner: intent classification.
  - Actual behavior: sends the user message to Groq and classifies it as COMMAND or CONVERSATION; validates actions against a shared validator.
  - Classification: IMPLEMENTED

- supabase/functions/friday-orchestrator/contextBuilder.ts
  - Runtime owner: prompt-building and memory assembly.
  - Actual behavior: fetches recent FRIDAY messages, session summary, entities, dynamic memory context, vibe, and temporal context before generating a response.
  - Classification: IMPLEMENTED

- supabase/functions/friday-orchestrator/personality.ts
  - Runtime owner: core FRIDAY personality prompt block.
  - Actual behavior: hardcoded identity and tone instructions for FRIDAY, used during prompt construction.
  - Classification: IMPLEMENTED, HARD-CODED, NOT USER CONFIGURABLE

- supabase/functions/friday-orchestrator/llmRouter.ts
  - Runtime owner: reply generation router.
  - Actual behavior: primary Groq route, fallback Gemini route, static fallback message.
  - Classification: IMPLEMENTED

- supabase/functions/friday-reflection/index.ts
  - Runtime owner: background memory reflection.
  - Actual behavior: scans unreflected messages, extracts entities and memory candidates, computes embeddings, writes memories, updates profile vibe, and records history.
  - Classification: IMPLEMENTED

- supabase/functions/friday-initiative/index.ts
  - Runtime owner: proactive outreach.
  - Actual behavior: checks inactive users, selects follow-up-worthy memories, generates a short FRIDAY check-in via Groq, inserts a proactive message into FRIDAY chat.
  - Classification: IMPLEMENTED

- supabase/functions/friday-pending-messages/index.ts
  - Runtime owner: GET endpoint for pending FRIDAY messages.
  - Actual behavior: fetches messages for a user where sender = friday and created_at > since.
  - Classification: IMPLEMENTED

### 1.2 Database / Persistence

Relevant tables and state owners from supabase/schema.sql and migrations:

- public.profiles
  - Contains id, display_name, persona, avatar_url, fcm_token, friday_vibe.
  - Runtime consumer: FRIDAY context assembly and reflection job.
  - Persistence: database-backed profile state.
  - Classification: IMPLEMENTED

- public.friday_messages
  - Core FRIDAY message table.
  - Columns: id, owner_id, sender, message, created_at, session_id, reflected_at, is_pinned, is_deleted, reactions, reply_to_id, status, is_proactive, is_command, sentiment.
  - Runtime consumers: orchestrator, reflection, ChatRepository, realtime sync.
  - Classification: IMPLEMENTED

- public.friday_sessions
  - Created by migration 017_friday_v1_sessions.sql.
  - Columns: id, owner_id, started_at, ended_at, summary, status.
  - Runtime consumer: orchestrator session boundary logic and summary updates.
  - Classification: IMPLEMENTED

- public.friday_memories
  - Columns: id, owner_id, memory_text, emotion_tag, is_pinned, created_at, category, embedding, session_id, follow_up_worthy.
  - Runtime consumer: contextBuilder and proactive initiative logic.
  - Classification: IMPLEMENTED

- public.friday_entities
  - Columns: id, owner_id, name, aliases, relationship, last_known_fact, updated_at, created_at.
  - Runtime consumer: contextBuilder entity lookup.
  - Classification: IMPLEMENTED

- public.friday_memory_history
  - Created by migration 016_friday_v1_memory_history.sql.
  - Runtime consumer: reflection job tracking reinforcement and supersession events.
  - Classification: IMPLEMENTED

- public.friday_action_requests
  - Created by migration 018_friday_v1_action_requests.sql.
  - Columns: id, owner_id, session_id, action_name, parameters, status, created_at.
  - Runtime consumer: orchestrator writes dispatched command actions and stores them for audit/traceability.
  - Classification: IMPLEMENTED but thin

- public.chat_sync_pipe
  - Runtime owner: realtime event bus used by server to push START_TYPING, NEW_MESSAGE, COMMAND, DELETE_MESSAGE.
  - Kotlin consumer: ChatRepository.handlePipeMessage.
  - Classification: IMPLEMENTED

- public.match_friday_memories
  - SQL function created in migration 001 and updated in migration 008.
  - Runtime consumer: vector retrieval of relevant memories during prompt building and reflection.
  - Classification: IMPLEMENTED

### 1.3 Android / Kotlin FRIDAY Surfaces

Files found:

- app/src/main/java/com/baroness/app/repository/ChatRepository.kt
  - Runtime owner of FRIDAY message sync and local persistence.
  - Handles realtime chat_sync_pipe payloads, local message insertion, and command execution through LocalCommandExecutor.
  - Classification: IMPLEMENTED

- app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt
  - Runtime owner of the FRIDAY chat UI state.
  - Uses repository.getMessages("friday") and repository.isFridayTyping.
  - Classification: IMPLEMENTED

- app/src/main/java/com/baroness/app/screens/ChatRoomScreen.kt
  - Runtime owner of the FRIDAY chat room UI.
  - Reads settings state and renders the conversation for conversationId = "friday".
  - Classification: IMPLEMENTED

- app/src/main/java/com/baroness/app/MainActivity.kt
  - Contains navigation route "Friday" that launches ChatRoomScreen(navController, "friday", settingsViewModel = settingsViewModel).
  - Classification: IMPLEMENTED

- app/src/main/java/com/baroness/app/components/chat/AskFridaySheet.kt
  - A helper/support UI, not a FRIDAY settings domain.
  - Classification: INTERNAL FUNCTION / UI ONLY

- app/src/main/java/com/baroness/app/components/chat/actions/ChatFridayActions.kt
  - UI helper for FRIDAY-specific actions.
  - Classification: IMPLEMENTED UI HELPERS, NOT SETTINGS

### 1.4 FRIDAY-Related Settings State

There are generic app settings for voice and speech, but no dedicated FRIDAY settings backend object.

Real persisted values in the app settings layer include:
- voice_enabled
- voice_provider
- voice_id
- voice_speed
- voice_pitch
- voice_director_note
- use_persona_voices
- clock_voice_announce

These belong to the sound / TTS domain and are not a true FRIDAY personality settings system.

No real persisted FRIDAY settings exist for:
- model selection
- personality mode
- memory retention policy
- memory reset
- proactive behavior toggle
- command policy

## 2. FRIDAY Conversation Pipeline

The real current lifecycle is:

User message
→ Android ChatRoomScreen / FridayChatViewModel.onSendMessage
→ ChatRepository.sendMessage("friday", text)
→ Supabase public.friday_messages INSERT
→ supabase/functions/friday-orchestrator/index.ts trigger logic
→ classifyMessage(userMessage) in classifier.ts
→ buildContext(...) in contextBuilder.ts
→ entity + memory retrieval + session summary assembly
→ generateReply(...) in llmRouter.ts
→ Groq primary, Gemini fallback, or static fallback
→ final text persisted back to public.friday_messages with status = SENT
→ chat_sync_pipe NEW_MESSAGE payload inserted
→ ChatRepository.handlePipeMessage saves the message locally and updates UI
→ ChatRoomScreen renders the final response

### Stage-by-stage evidence

- Input to UI: app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt
  - State owner: client ViewModel
  - Data source: user text input
  - Sync behavior: async coroutine sends to repository
  - Failure behavior: blank messages are ignored
  - Classification: IMPLEMENTED

- Message routing: app/src/main/java/com/baroness/app/repository/ChatRepository.kt
  - State owner: repository + Room database + realtime subscription
  - Data source: Supabase realtime and local messages
  - Sync mode: asynchronous, network-reactive
  - Failure behavior: retries subscription and fetches offline messages on reconnect
  - Classification: IMPLEMENTED

- Orchestrator: supabase/functions/friday-orchestrator/index.ts
  - State owner: Edge Function runtime / service role
  - Data source: public.friday_messages insert, public.friday_sessions, public.profiles, public.friday_entities, public.friday_memories, public.chat_sync_pipe
  - Sync mode: asynchronous server-side event-driven execution
  - Failure fallback: ultimately catches errors and attempts to mark failure state; returns 500 on critical error
  - Classification: IMPLEMENTED

- Classification: supabase/functions/friday-orchestrator/classifier.ts
  - State owner: runtime function
  - Data source: user text only
  - Sync mode: async Groq HTTP request
  - Failure fallback: defaults to CONVERSATION if the classifier fails or the output is weak
  - Classification: IMPLEMENTED

- Context builder: supabase/functions/friday-orchestrator/contextBuilder.ts
  - State owner: runtime function
  - Data source: recent FRIDAY messages, active session summary, entity records, memory embeddings, profile vibe
  - Sync mode: async DB + embedding + summary generation calls
  - Failure fallback: degrades to baseline memory retrieval; logs errors without crashing the request
  - Classification: IMPLEMENTED

- Response generation: supabase/functions/friday-orchestrator/llmRouter.ts
  - State owner: runtime function
  - Data source: assembled system prompt + chat history + current message
  - Sync mode: async REST calls to Groq then Gemini fallback
  - Failure fallback: fixed fallback text if both providers fail
  - Classification: IMPLEMENTED

- Local UI sync: app/src/main/java/com/baroness/app/repository/ChatRepository.kt
  - State owner: local Room DAO and UI StateFlow
  - Data source: chat_sync_pipe payloads from server
  - Sync mode: asynchronous, realtime subscription
  - Failure fallback: payloads are dropped if malformed or unparseable
  - Classification: IMPLEMENTED

## 3. FRIDAY Identity and Personality

### Actual identity representation

The repository shows FRIDAY identity is mostly prompt-based and hardcoded, not database-backed as a formal user setting.

Evidence:

- supabase/functions/friday-orchestrator/personality.ts
  - getPersonalityBlock(userName) defines FRIDAY as a close best friend and ride-or-die companion.
  - It specifies voice, slang, boundaries, and action rules.
  - This is static TypeScript prompt logic, not a persisted configuration table.

- supabase/functions/friday-orchestrator/contextBuilder.ts
  - Combines personality prompt, session summary, entities, and memory context into one runtime system prompt.
  - This is dynamic but still generated each request.

- supabase/functions/friday-reflection/index.ts
  - Writes the current vibe (chilled, hyped, salty, concerned, playful) into public.profiles.friday_vibe.

- app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt
  - Hardcodes the other participant as "FRIDAY" with a fixed avatar URL.

### Identity partitioning and relationship === "Self"

The context builder explicitly includes a special entity relationship:

- contextBuilder.ts filters entities by relationship === "Self"
- That block becomes the user identity context for the prompt
- This is a real implementation in the current repository and not just a stale design artifact

### Current classification of identity state

- Hardcoded: personality.ts and the FRIDAY system identity structure
- Database-backed: profile.friday_vibe and friday_entities records
- DataStore-backed: no FRIDAY identity table in DataStore
- Prompt-generated: yes
- Runtime-generated: yes
- Configurable: not in a real settings domain today
- Not configurable: yes, for the current repo

### Important discrepancy against docs

Several FRIDAY architecture documents describe a personality_config table and a richer personality model. The current repository does not contain such a table, and the code does not implement a persisted FRIDAY personality system. The system is runtime-prompt-driven plus partly DB-derived vibe/entity state.

## 4. FRIDAY Memory System

### 4.1 Actual memory architecture

The current repository implements a working memory and long-term memory flow:

- public.friday_messages as short-term context/history
- public.friday_sessions with summary per active session
- public.friday_entities as learned facts and person/thing relationships
- public.friday_memories as vector-backed memory facts
- public.friday_memory_history for reinforcement/supersession tracking
- public.match_friday_memories for semantic matching

### 4.2 What the system remembers automatically

Evidence:

- contextBuilder.ts builds a deep memory block using vector similarity and active memory fallback.
- reflection job inserts extracted facts into public.friday_memories.
- entities are created with relationship and last_known_fact.
- vibe is persisted to profiles.friday_vibe.

The system currently remembers:
- user facts extracted from conversation
- people, places, and entity data
- emotional tone/vibe state
- follow-up-worthy memories
- relevant prior memory facts matched by embedding around the current message
- some reinforcement/supersession tracking

### 4.3 Category classification

A. Genuinely implemented memory behavior
- embedding-based retrieval via match_friday_memories
- reflection-based extraction from idle messages
- rolling session summary injection
- entity lookup by name and alias
- active memory fallback when vector results are empty
- memory reinforcement/supersession recording

B. Partially implemented / planned behavior
- session summary generation is implemented, but only as a lightweight live summary and not a large memory-management framework
- memory lifecycle exists only partly through memory_history and status values, not as a user-visible lifecycle engine
- no user-facing memory management UI or admin tools exist

C. Documentation mentioned but not implemented
- formal candidate / validated / active / stale / archived lifecycle as a user-manageable domain
- memory editing, deletion, or forgetting controls exposed in app settings
- explicit personality_config or interaction_style memory model

### 4.4 Key evidence paths

- supabase/functions/friday-reflection/index.ts
  - computes embeddings and calls match_friday_memories to compare candidates against existing memories
  - inserts new memories and records superseded memories

- supabase/functions/friday-orchestrator/contextBuilder.ts
  - loads recent messages, session summary, identity core, entity notes, and memory context before generating a reply

## 5. FRIDAY Model / Provider Routing

### Actual current routing

The model/provider routing is internal and hardcoded, not a user setting.

Evidence:

- supabase/functions/friday-orchestrator/llmRouter.ts
  - primary route: Groq openai/gpt-oss-120b
  - fallback route: Gemini gemini-1.5-flash
  - static fallback if both fail

- supabase/functions/friday-orchestrator/classifier.ts
  - uses Groq openai/gpt-oss-20b with response_format JSON

- supabase/functions/friday-reflection/index.ts
  - uses Gemini 3.5 flash for extraction and Gemini embedding-001 for vector memory generation

- supabase/functions/friday-initiative/index.ts
  - uses Groq openai/gpt-oss-120b for proactive messages

### Classification of model/provider controls

- Current user setting: none
- Internal runtime configuration: yes
- Future candidate: possible, but not implemented
- Not appropriate for Settings Center today: yes

### Evidence of no user-facing selection

- No DataStore keys for model/provider selection exist in the app settings layer.
- No SettingsViewModel state for FRIDAY model/provider selection exists.
- No UI settings screen exposes it.

## 6. FRIDAY Personality / Behavior Controls

The repository includes a few values that look like behavior preferences, but they are not a real FRIDAY settings domain.

Examples found:

- profiles.friday_vibe in the database
- use_persona_voices in app settings
- voice_director_note in app settings
- personality.ts prompt constants

### Current classification

- actual persisted settings: yes, a small set of sound/voice preferences exist
- prompt constants: yes, FRIDAY identity and tone are prompt-defined
- temporary runtime values: yes, active session summary and vibe state are runtime-generated
- unused UI: some FRIDAY-related settings may exist in app state, but there is no dedicated FRIDAY Settings Center domain
- planned concepts: personality sliders, tone controls, custom instructions, and memory settings are not implemented here

## 7. Director Notes

### What exists today

The current app settings layer includes voice_director_note.

Evidence:

- SettingsViewModel and SettingsRepository keep voice_director_note
- Voice provider requests include directorNote in the synthesis payload
- VoiceCenter / BaronessVoiceProvider sends text, provider, voice, and directorNote to the synthesis backend

### Actual runtime behavior

- It is stored in DataStore and read as part of voice configuration.
- It affects the remote voice synthesis request for app announcements and preview paths.
- It does not affect FRIDAY chat generation in the orchestrator; it is not a FRIDAY backend identity field.
- It is not a general FRIDAY memory or personality override.

### Current classification

- Stored: yes, in DataStore
- Ownership: app voice settings domain, not FRIDAY identity domain
- Runtime consumer: VoiceCenter / BaronessVoiceProvider / dashboard announcement flow
- UI exposure: not clearly exposed as a dedicated FRIDAY setting in the current codebase
- Functional: yes, but only for speech synthesis
- Phase 5 classification: only if treated as a voice/TTS setting, not as a FRIDAY personality setting

## 8. Persona Voice Optimization

### What exists

The app contains a persisted flag named use_persona_voices.

Evidence:

- SettingsViewModel persists it.
- DashboardViewModel.triggerAnnouncement() reads it.
- When enabled, it uses hardcoded persona voice configuration for dashboard announcements.
- When disabled, it uses the persisted provider, voice ID, speed, pitch, and director note.

### Ownership boundary

This belongs to the sound/voice domain, not the FRIDAY conversation backend.

### Current classification

- Stored: yes, DataStore-backed
- Runtime effect: yes, for dashboard announcement speech only
- Backend work needed: low, but not a FRIDAY settings domain
- Phase 5 disposition: if a FRIDAY page exists, it belongs under Sound & Haptics or voice optimization, not as a separate FRIDAY personality system

## 9. FRIDAY Voice / Speech Boundary

The repository establishes a clear boundary: FRIDAY chat is a text conversation layer, while voice synthesis is a separate feature layer.

### What actually uses voice in the FRIDAY path

- FRIDAY chat replies are plain text messages persisted to public.friday_messages.
- The FRIDAY orchestrator does not automatically speak every FRIDAY response.
- VoiceCenter is used for voice preview, announcements, and speech generation outside the FRIDAY reply pipeline.
- Dashboard announcements and timer readouts use VoiceCenter, but they are not the same as FRIDAY chat display logic.

### Distinction in current repo

- FRIDAY conversational text
  - FridayChatViewModel.kt
  - ChatRepository.kt
  - public.friday_messages
  - supabase/functions/friday-orchestrator/index.ts

- Dashboard announcements
  - DashboardViewModel.triggerAnnouncement()
  - VoiceCenter

- Timer announcements
  - AlarmReceiver.kt
  - ClockSoundPlayer.kt
  - VoiceCenter

### Conclusion

FRIDAY conversation is text-first. Voice readout is a separate app behavior, not a default property of every FRIDAY response.

## 10. FRIDAY Commands / Actions

### Actual implemented command capability

The FRIDAY orchestrator explicitly classifies commands and dispatches them for local execution.

Evidence:

- supabase/functions/friday-orchestrator/classifier.ts lists supported commands such as:
  - play_music
  - pause_media
  - resume_media
  - next_track
  - previous_track
  - set_volume
  - volume_up
  - volume_down
  - mute
  - unmute
  - navigate
  - set_timer
  - set_alarm

- supabase/functions/friday-orchestrator/index.ts inserts actions into public.friday_action_requests and sends a COMMAND payload through chat_sync_pipe.

- app/src/main/java/com/baroness/app/repository/ChatRepository.kt processes the COMMAND payload and executes it with LocalCommandExecutor.

### Current classification

- Implemented: playback, navigation, timer, alarm, volume commands
- Partially implemented: command validation and dispatch are real, but not a generalized authorization engine
- Planned / not fully implemented: full device action permission model does not exist

### Settings relevance

Command permissions could be a future setting, but no persisted command policy exists in the repo. This is not a current FRIDAY settings feature.

## 11. FRIDAY Error / Fallback Behavior

### 11.1 Supabase failures

- If the FRIDAY orchestrator fails, it catches errors and attempts failure-state handling.
- Critical errors return HTTP 500.

### 11.2 LLM provider failures

- llmRouter.ts tries Groq first, then Gemini fallback.
- If both fail, it returns a static fallback message.
- classifier.ts defaults to CONVERSATION on failure.

### 11.3 Memory retrieval failures

- contextBuilder.ts wraps memory retrieval in try/catch.
- If embedding generation fails, it falls back to baseline active memory retrieval.

### 11.4 Embedding failures

- reflection job increments a failure counter and continues without inserting the candidate memory.

### 11.5 Context / malformed data issues

- The orchestrator strips action tags and raw JSON before final response persistence.
- ChatRepository drops malformed pipe payloads silently if they cannot be parsed.

### Findings

The system is resilient in many places, but it is not a complete user-facing FRIDAY operational safety layer. There is graceful fallback, but no robust user-visible error or consent model for command and memory behavior.

## 12. Data / Security Boundary

### Data locations

- App client: local Room DB, DataStore, settings values, app runtime state
- Server: Supabase public schema tables for FRIDAY content, memory, and sync
- Edge Functions: environment secrets for Groq, Gemini, and Supabase service role

### Identity assumptions

The FRIDAY backend uses owner_id text values rather than a strict auth.users-based ownership model. The repo currently assumes persona identifiers such as phesty_official or baroness_official, consistent with custom passkey flows elsewhere in the app, but not with a formal auth.uid-driven model for FRIDAY tables.

### RLS and security posture

The schema comments and policy summaries show permissive public access on several tables, including friday_messages and chat_sync_pipe. This repository's FRIDAY backend is functional but not locked down with a strict auth-based ownership model.

### Important finding

The backend is functional but not fully hardened by strict RLS or auth.uid ownership. This is not a code change task during this audit, but it is a real boundary concern for any future FRIDAY settings design.

## 13. FRIDAY Settings Candidate Matrix

| Candidate | Current Backend | Persistence | Runtime Effect | UI Ready? | Backend Work Needed | Recommended Phase |
|---|---|---|---|---|---|---|
| FRIDAY enabled/disabled | No dedicated FRIDAY switch exists | No | None | No | Needed | FUTURE / BACKEND |
| Director Notes | Partial voice_director_note exists in app settings, but not as FRIDAY identity configuration | DataStore | Only affects voice synthesis, not FRIDAY chat generation | Maybe, but only as sound/TTS config | Low | Phase 5 only if treated as voice setting |
| Persona Voice Optimization | Implemented in dashboard announcement voice path | DataStore | Alters speech settings for announcement voice only | Yes, under sound/voice domain | Low | Phase 5 under Sound & Haptics |
| Memory behavior | Active memory retrieval and reflection pipeline | Database-backed | Affects FRIDAY recall and prompt assembly | No | Moderate | FUTURE UI |
| Memory retention | Partial, but not user-controlled | Database state only | Affects memory lifecycle implicitly | No | Moderate | FUTURE UI |
| Memory management | Minimal user-facing layer does not exist | No user settings model | None | No | Moderate | FUTURE UI |
| Personality/tone | Hardcoded prompt logic only | Not formalized as setting | Runtime prompt behavior | No | High | FUTURE UI |
| Response verbosity | Not implemented | No | None | No | High | FUTURE UI |
| Model/provider selection | Internal runtime config only | No | Runtime routing choice | No | High | FUTURE UI |
| Voice behavior | Implemented in sound settings | DataStore | Affects TTS and announcement voice | Yes | Low | Phase 5 under Sound & Haptics |
| Command permissions | No real persisted permission model | No | None | No | High | FUTURE UI |
| Notification behavior | Not FRIDAY-specific backend | App-level | Separate from FRIDAY backend | No | Medium | Not FRIDAY-specific |
| Proactive behavior | Initiative job exists | DB and implicit state only | Sends proactive follow-up messages | No | Medium | FUTURE UI |
| Context depth | Partially implemented via session summary and message window | Database summary only | Affects prompt context | No | Medium | FUTURE UI |
| Conversation history | Implemented | public.friday_messages | Used in prompt assembly | Yes, but not as a settings control | Low | Internal use only |
| Privacy controls | Not implemented as FRIDAY-specific feature | No | None | No | High | FUTURE UI |
| Memory reset/clear | Not implemented | No | None | No | High | FUTURE UI |

## 14. Placeholder UI Candidates

These would make sense visually in a future FRIDAY page, but the backend is not there yet.

### Placeholder candidate: Memory reset / clear
- Why it makes sense: memory is central to FRIDAY behavior and user trust.
- Missing backend layer: no user-controlled memory deletion or reset API.
- Persistence needed: explicit reset state or retention policy.
- Runtime consumer: contextBuilder.ts and memory retrieval logic.
- Classification: FUTURE / PLACEHOLDER

### Placeholder candidate: Personality / tone controls
- Why it makes sense: the prompt logic is obviously central to FRIDAY behavior.
- Missing backend layer: a persisted personality configuration model.
- Persistence needed: a user settings table or DataStore-backed persona profile.
- Runtime consumer: personality.ts / contextBuilder.ts.
- Classification: FUTURE / PLACEHOLDER

### Placeholder candidate: Model / provider selector
- Why it makes sense: Groq and Gemini are already routed in code.
- Missing backend layer: user preference and policy model for which provider or model is active.
- Persistence needed: DataStore or DB preference keyed to the user.
- Runtime consumer: llmRouter.ts and classifier.ts.
- Classification: FUTURE / PLACEHOLDER

### Placeholder candidate: Context depth / history controls
- Why it makes sense: session summary and recent messages clearly affect behavior.
- Missing backend layer: a user-facing context budget and retention policy.
- Persistence needed: preference values and policy support in contextBuilder.ts.
- Runtime consumer: contextBuilder.ts.
- Classification: FUTURE / PLACEHOLDER

### Placeholder candidate: Proactive behavior toggles
- Why it makes sense: proactive check-ins exist and are time-windowed.
- Missing backend layer: user preference for enable/disable cadence and memory triggering.
- Persistence needed: user preference + initiative config.
- Runtime consumer: friday-initiative/index.ts.
- Classification: FUTURE / PLACEHOLDER

### Placeholder candidate: Command permissions / safety policy
- Why it makes sense: commands are real and action requests are tracked.
- Missing backend layer: explicit approval / policy layer before command execution.
- Persistence needed: user safety settings or allowlist tables.
- Runtime consumer: classifier.ts and LocalCommandExecutor.
- Classification: FUTURE / PLACEHOLDER

## 15. Pre-UI Backend Tightening Candidates

These are obvious, direct, bounded correctness issues that matter for future FRIDAY settings.

1. FRIDAY identity is prompt-based and partly database-derived, but not modeled as a real persisted setting.
   - Files: supabase/functions/friday-orchestrator/personality.ts, contextBuilder.ts
   - Finding: the personality is encoded in a hardcoded prompt rather than a dedicated FRIDAY settings schema.
   - Why it matters: any FRIDAY UI that promises custom personality controls would be misleading.
   - Classification: BACKEND TIGHTENING CANDIDATE

2. Voice director note and persona voice optimization are in the generic settings layer, not the FRIDAY backend identity layer.
   - Files: SettingsViewModel, SettingsRepository, VoiceCenter, DashboardViewModel
   - Finding: these values affect speech synthesis but do not define FRIDAY identity or chat behavior.
   - Why it matters: the UI may incorrectly present them as FRIDAY personality controls.
   - Classification: BACKEND TIGHTENING CANDIDATE

3. Model/provider routing is hardcoded and not persisted.
   - Files: llmRouter.ts, classifier.ts, friday-reflection/index.ts
   - Finding: runtime model selection is internal implementation detail, not a user preference.
   - Why it matters: a model selector UI would not be truthful without a storage and validation layer.
   - Classification: BACKEND TIGHTENING CANDIDATE

4. Memory and identity are fragmented across prompt logic, vibe state, entity tables, and embedding retrieval without a cohesive user policy.
   - Files: contextBuilder.ts, friday-reflection/index.ts, schema.sql, public.profiles
   - Finding: memory storage is real but not governed by a single user control plane.
   - Why it matters: future memory settings would be inconsistent if implemented without a backend policy model.
   - Classification: BACKEND TIGHTENING CANDIDATE

5. Command execution is real, but action governance is limited to simple validation and recording.
   - Files: classifier.ts, friday-orchestrator/index.ts, friday_action_requests, action-validator.ts
   - Finding: there is no complete command permission layer.
   - Why it matters: command controls would be misleading without explicit user-state and consent rules.
   - Classification: BACKEND TIGHTENING CANDIDATE

6. Session summary and memory context are active, but there is no explicit retention or reset policy for them.
   - Files: friday_sessions, contextBuilder.ts, friday_memories
   - Finding: user memory management is not represented as a real settings feature.
   - Why it matters: future retention or clear-memory UI would require a real backend domain.
   - Classification: BACKEND TIGHTENING CANDIDATE

### Count of backend tightening candidates

6

## 16. Final Classification

### READY FOR PHASE 5 UI

- FRIDAY chat conversation flow is implemented end-to-end from client send to server orchestration to reply persistence and realtime display.
- FRIDAY message persistence in public.friday_messages is active.
- FRIDAY classification into COMMAND vs CONVERSATION is implemented.
- FRIDAY memory retrieval and entity extraction are implemented.
- Proactive FRIDAY outreach is implemented as a time-windowed initiative job.
- Voice/TTS settings that affect speech announcements are implemented in the broader app settings system.

### BACKEND TIGHTENING BEFORE PHASE 5 UI

- FRIDAY personality is hardcoded and not formalized as a persisted setting.
- FRIDAY memory behavior is real but not formalized as a user-manageable settings domain.
- Model/provider routing is internal configuration, not user-state.
- Director notes and persona voice optimization are voice settings, not FRIDAY identity backend.
- Command dispatch exists, but permission policy is not implemented as a user setting.
- Security/ownership boundaries for FRIDAY tables are not strict auth.uid-based controls.

### FUTURE UI / BACKEND PLACEHOLDERS

- FRIDAY enabled/disabled switch
- Memory reset / clear controls
- Personality / tone customization
- Response verbosity controls
- Memory retention settings
- Proactive behavior toggles
- Context-depth controls
- Model/provider selection
- Command permissions / safety toggles
- Privacy controls specific to FRIDAY behavior

### NOT A FRIDAY SETTING

- Generic VoiceCenter TTS configuration
- Dashboard announcement persona voice optimization
- Clock readout and timer alarm sound preferences
- Local device command execution behaviors
- Generic notification and app-level system settings
- Non-FRIDAY chat UI helpers or local helpers

## 17. Final Determination

### READY NOW
- FRIDAY chat and orchestration flow
- FRIDAY memory retrieval and entity extraction
- proactive outreach flow
- sound/voice settings relevant to FRIDAY announcement behavior

### BACKEND TIGHTENING FIRST
- FRIDAY identity and personality model
- memory retention and management policy
- command permission layer
- FRIDAY-specific settings ownership boundary
- model/provider settings/back-end routing policy

### FUTURE UI / BACKEND PLACEHOLDERS
- memory reset controls
- tone/personality controls
- model/provider selection
- proactive behavior toggles
- command safety toggles
- context-depth controls

### NOT A SETTINGS CENTER CONCERN
- FRIDAY chat room and local UI messaging remain a product feature rather than a settings domain
- generic voice settings remain sound-domain features
- device action execution remains a command layer, not a settings UI

### NOT IMPLEMENTED
- formal FRIDAY personality configuration system
- user-administered memory policy
- user-selectable provider/model metadata
- dedicated FRIDAY settings persistence framework for memory and behavior

## 18. Explicit Audit Conclusions

1. What FRIDAY functionality already exists and is genuinely working?
   - A real FRIDAY chat pipeline, orchestration, memory retrieval, reflection, and proactive outreach are active.

2. Which FRIDAY-related settings/configuration are already backed by real persistence and runtime behavior?
   - The app has general voice/TTS settings, but not a real FRIDAY configuration table. The memory system is DB-backed but not user-facing.

3. Which obvious backend/domain corrections should be made before designing the FRIDAY Settings UI?
   - Separate FRIDAY identity from sound settings, add a genuine FRIDAY config schema, and create a permission model before exposing any personality or memory settings.

4. Which useful FRIDAY settings/features could eventually appear in the UI but currently require backend work first?
   - personality controls, memory reset/retention, context depth, proactive toggles, model/provider selection, and command permissions.

## 19. Summary Counts

- Report created: yes
- Major findings: 18+ substantial repo-grounded findings
- Backend tightening candidates: 6
- Phase 5-ready settings: 3-4 when counting FRIDAY conversation flow and voice controls as the only current operational FRIDAY/voice surface; in a strict FRIDAY-only classification, there is not yet a dedicated Phase 5-ready FRIDAY settings domain
- Future placeholder candidates: 6
- Source code modified: no

FRIDAY SECTOR AUDIT: COMPLETE