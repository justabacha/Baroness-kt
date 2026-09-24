# FRIDAY Architecture & Codebase Audit Review

This document contains a factual audit of the current codebase state against the proposed FRIDAY system architecture specified in `docs/FRIDAY/Friday_Architecture.md`.

---

## 1. Android / Kotlin Chat Surface

### Chat Sending & Receiving
* **API & Repository**: Chat operations are driven by `ChatApi.kt` (`com.baroness.app.api.ChatApi`) and `ChatRepository.kt` (`com.baroness.app.repository.ChatRepository`).
* **ViewModels**: `FridayChatViewModel.kt` handles AI chat state, inheriting from `ChatRoomViewModel.kt`.
* **Request/Response Data Shapes**:
  * **Human Chat**: `MessageDto` containing `id`, `conversation_id`, `sender_id`, `receiver_id`, `content`, `created_at`, `read_at`, `is_deleted`, `reactions`, `reply_to_id`, `reply_to_content`, `reply_to_sender_id`, `delivered_at`, `is_pinned`.
  * **Friday Chat**: `FridayMessageDto` containing `id`, `owner_id`, `sender` (`"user"` or `"friday"`), `message`, `created_at`, `is_pinned`, `is_deleted`, `reactions`.
  * **Realtime Sync Pipe**: `SyncPipeDto` (`recipient_id`, `payload`, `created_at`). `payload` holds JSON objects like `NEW_MESSAGE`, `DELETE_MESSAGE`, `START_TYPING`, and `COMMAND`.

### Conversations & Sessions
* Client-side messages are stored in a local Room database (`AppDatabase.kt`, `MessageDao.kt`, `MessageEntity.kt`).
* Conversations are identified by string IDs (`"friday"`, `"baroness"`, `"phesty"`).
* **Not found**: There is no client-side session model, session boundary tracking, or session summary object.

### Idle Detection & WorkManager Jobs
* **Session Idle Detection**: **Not found** on the Android client.
* **Scheduled WorkManager Jobs**:
  1. `SyncWorker` (`com.baroness.app.workers.SyncWorker`): Registered in `MainActivity.kt` via `enqueueUniquePeriodicWork("periodic_wishlist_sync", KEEP)` running every 15 minutes with `NetworkType.CONNECTED` constraint. Flushes offline wishlist sync queue.
  2. `ChatSyncWorker` (`com.baroness.app.workers.ChatSyncWorker`): Triggered via `OneTimeWorkRequestBuilder<ChatSyncWorker>()` (`enqueueUniqueWork("ChatSync", REPLACE)`) whenever local pending chat messages need syncing or internet connectivity is restored.

### Action Execution Code
* Action execution is implemented in `LocalCommandExecutor.kt` (`com.baroness.app.command`):
  * `play_music`: Parameter `genre` (`String?`). Opens system music player (`MediaStore.INTENT_ACTION_MUSIC_PLAYER`).
  * `navigate`: Parameter `destination` (`String?`). Opens Google Maps (`google.navigation:q=...`).
  * `set_timer`: Parameter `minutes` (`Int?`). Sets system timer (`AlarmClock.ACTION_SET_TIMER`).
  * `set_alarm`: Parameters `hour` (`Int?`), `minute` (`Int?`). Sets system alarm (`AlarmClock.ACTION_SET_ALARM`).
* **Not found**: Code for opening general apps, toggling Bluetooth/Wi-Fi settings, or setting reminders.

### Confirmation UI / Dialog Pattern
* Bottom sheet confirmation patterns exist for deleting messages (`DeleteConfirmationSheet.kt`) and wishlist items (`ConfirmModal.kt`).
* **Not found**: No confirmation UI or dialog pattern exists for risky or destructive action execution.

---

## 2. Supabase — Database

### Tables, Columns & Types
* `profiles`: `id` (text), `display_name` (text), `persona` (text), `avatar_url` (text), `fcm_token` (text), `friday_vibe` (text).
* `friday_messages`: `id` (uuid), `owner_id` (text), `sender` (text), `message` (text), `created_at` (timestamptz), `session_id` (uuid), `reflected_at` (timestamptz), `is_pinned` (boolean), `is_deleted` (boolean), `reactions` (jsonb), `reply_to_id` (uuid), `status` (text), `is_proactive` (boolean), `is_command` (boolean), `sentiment` (text).
* `friday_memories`: `id` (bigint), `owner_id` (text), `memory_text` (text), `emotion_tag` (text), `is_pinned` (boolean), `created_at` (timestamptz), `category` (text), `embedding` (vector(768)), `session_id` (uuid), `follow_up_worthy` (boolean).
* `friday_entities`: `id` (uuid), `owner_id` (text), `name` (text), `aliases` (text[]), `relationship` (text), `last_known_fact` (text), `updated_at` (timestamptz), `created_at` (timestamptz).
* `messages`: `id` (uuid), `conversation_id` (text), `sender_id` (text), `receiver_id` (text), `content` (text), `created_at` (timestamptz), `read_at` (timestamptz), `is_deleted` (boolean), `reactions` (jsonb).
* `chat_sync_pipe`: `id` (uuid), `recipient_id` (text), `payload` (jsonb), `created_at` (timestamptz).
* `access_keys`: `id` (text), `secret_key` (text), `created_at` (timestamptz).
* `backup_log`: `persona_id` (text), `last_backup_at` (timestamptz), `message_count_at_backup` (integer).
* `typing_status`: `user_id` (text), `chat_partner_id` (text), `is_typing` (boolean), `updated_at` (timestamp).
* **Not found**: `conversation_sessions`, `memory_history`, `action_requests`, `personality_config`.

### Row Level Security & Policies
* RLS is enabled on all tables, but existing policies are permissive (`public` role, `QUAL: true` or checking hardcoded persona strings like `'phesty_official'`):
  * `profiles`: Public select/insert/update allowed (`"Allow public access to profiles"`).
  * `friday_messages`: Public select/insert/update/delete allowed (`"Allow public access to AI messages"`).
  * `friday_memories`: Public select/insert/update/delete allowed (`"Allow read memories"`, etc.).
  * `messages`: Restricted to senders/receivers matching `ARRAY['phesty_official', 'baroness_official']`.
  * `chat_sync_pipe`: Public access allowed.
  * `access_keys`: Public select allowed.

### Database Extensions
* `vector` (pgvector): Enabled in migration `001_upgrade_friday_vectors.sql`.
* `pg_cron`: Enabled/used in migration `011_fix_friday_worker_cron.sql`.
* **Not found**: `pg_trgm`.

### Existing Indexes
* `idx_friday_messages_session_id` on `friday_messages(session_id)`.
* `idx_friday_messages_reflected_at` on `friday_messages(reflected_at) WHERE reflected_at IS NULL`.
* `idx_friday_messages_owner_created_at` on `friday_messages(owner_id, created_at DESC)`.
* `idx_friday_messages_owner_reflected_created_at` on `friday_messages(owner_id, reflected_at, created_at ASC) WHERE reflected_at IS NULL`.
* `idx_friday_entities_owner_name` on `friday_entities(owner_id, name)`.
* `idx_friday_memories_owner_created_at` on `friday_memories(owner_id, created_at DESC)`.
* `idx_chat_sync_pipe_recipient_created_at` on `chat_sync_pipe(recipient_id, created_at ASC)`.

---

## 3. Supabase Edge Functions

### Function List & Responsibilities
1. `friday-orchestrator/index.ts`: HTTP Webhook entry point for user messages. Handles initial lock, typing indicator push to `chat_sync_pipe`, classification, context assembly, LLM routing, character delay calculations, and realtime payload response.
2. `friday-reflection/index.ts`: Background job extracting entities, memories, and vibe state from unreflected messages (`reflected_at IS NULL`). Uses `gemini-3.5-flash` for extraction and `gemini-embedding-001` for memory embeddings.
3. `friday-initiative/index.ts`: Proactive outreach job checking inactive users (12–18 hours silence), picking follow-up memories, generating check-in messages via Groq, and pushing to `friday_messages` and `chat_sync_pipe`.
4. `friday-pending-messages/index.ts`: REST endpoint fetching pending Friday messages for a given `user_id` since a `since` timestamp.
5. `notify-trigger/index.ts`: Database trigger handler for `wishlist_items` inserts, sending FCM push notifications using Google OAuth2 + Firebase Messaging API.

### Groq & Gemini API Call Structure
* All calls use non-streaming HTTP `fetch` requests.
* `llmRouter.ts` (`friday-orchestrator`):
  * Primary: Groq API `openai/gpt-oss-120b` (`temperature: 0.6`, `max_tokens: 1024`).
  * Fallback: Gemini API `gemini-1.5-flash` (`temperature: 0.7`, `maxOutputTokens: 150`).
* `classifier.ts` (`friday-orchestrator`): Groq API `openai/gpt-oss-20b` (`temperature: 0.1`, JSON object output).
* `contextBuilder.ts` (`friday-orchestrator`): Gemini API `gemini-embedding-001` (`outputDimensionality: 768`).
* `friday-reflection/index.ts`: Gemini API `gemini-3.5-flash` (Structured JSON output with schema) and `gemini-embedding-001` (`outputDimensionality: 768`).
* `friday-initiative/index.ts`: Groq API `openai/gpt-oss-120b` (`temperature: 0.9`).

### Prompt-Building Logic
* Implemented in `contextBuilder.ts` (`friday-orchestrator`):
  1. Base persona text from `personality.ts` (`getPersonalityBlock`).
  2. Temporal context (current time, silence duration in minutes, flow status).
  3. Emotional vibe state (`profiles.friday_vibe`).
  4. Core identity facts (`friday_entities` with `relationship = 'Self'`).
  5. Relevant external entity notes (`friday_entities` matching message text/aliases).
  6. Stochastic spontaneous memory recall / Gemini vector-matched memories (`friday_memories`).
  7. Working memory: Last 20 messages from `friday_messages`.

### Classification, Intent Detection & Model Routing
* `classifier.ts` classifies input as `COMMAND` or `CONVERSATION`. If `COMMAND`, `friday-orchestrator/index.ts` bypasses deep conversation, updates placeholder status to "On it", and sends a `{ type: "COMMAND", intent, parameters }` payload to `chat_sync_pipe`.
* `llmRouter.ts` tries Groq first, falls back to Gemini on failure/timeout, and returns a static fallback message if both fail.

### Rate-Limiting / Usage-Tracking
* **Not found**. No rate-limiting or usage-tracking code or database tables exist.

---

## 4. Auth & Secrets

### Authentication
* Users are authenticated via custom passkey logic (`AuthManager.kt`). Passkeys (`phesty` or `baroness`) are validated against the `access_keys` table via Supabase REST API, returning persona IDs `phesty_official` or `baroness_official`.
* **Not found**: Supabase Auth (`auth.users` / JWT tokens) is not used.

### API Keys & Secrets Location
* **Server-side (Supabase Edge Function Environment Secrets)**:
  * `GROQ_API_KEY`
  * `GEMINI_API_KEY`
  * `REFLECTION_GROQ_API_KEY`
  * `INTERNAL_CRON_SECRET`
  * `SUPABASE_SERVICE_ROLE_KEY`
  * `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY`
* **Client-side (Android App)**:
  * `GROQ_API_KEY` and `GROQ_API_KEY_1` **are present in the Android APK** via `BuildConfig` (injected from `local.properties` in `app/build.gradle.kts`). These keys are used directly by `GroqApiService.kt` for message translation and the `AskFridaySheet` UI analysis feature.

---

## 5. Personality / Identity

### Existing Persona Definitions
* `supabase/functions/friday-orchestrator/personality.ts`: Defines FRIDAY in `getPersonalityBlock(userName)` as a ride-or-die companion living in the user's phone. Establishes language rules (blending UK Slang, Sheng, Kiswahili, casual English, matching user dialect/energy, strictly lowercase, no corporate assistant phrasing).
* `profiles.friday_vibe`: DB column storing FRIDAY's dynamic mood (`chilled`, `hyped`, `salty`, `concerned`, `playful`), updated by `friday-reflection`.
* `GroqApiService.kt`: System prompt constants for analysis ("You are Friday, the Baroness Analyst...").
* `FridayChatViewModel.kt`: Default participant name (`"FRIDAY"`) and avatar URL.
* **Not found**: No `personality_config` table exists in the database.

---

## 6. Versions & Conventions

### Versions
* Kotlin: `2.2.10`
* Compose BOM: `2026.02.01`
* Supabase Kotlin SDK: `3.1.1` (`supabase-kt`, `postgrest-kt`, `storage-kt`, `realtime-kt`)
* Ktor Client: `3.0.1`
* WorkManager: `2.9.0`
* Android SDK: `minSdk = 24`, `targetSdk = 36`, `compileSdk = 37`

### Edge Function Runtime & Conventions
* Deno HTTP server (`std@0.168.0/http/server.ts`).
* Supabase JS Client (`@supabase/supabase-js@2`).
* Standard ES Modules / TypeScript.
* Self-contained function subdirectories under `supabase/functions/` (no `_shared/` directory currently exists).

### Test Setup
* `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt` contain placeholder tests.
* **Not found**: No unit, integration, or schema tests exist for chat, memory, or edge functions.

---

## 7. Gap & Conflict Mapping

| Section in `Friday_Architecture.md` | Implementation Status | Findings & Conflicts |
|---|---|---|
| **§6 Short-Term Memory** | **Partially Implemented / Conflicts** | **Exists**: `contextBuilder.ts` fetches the last 20 raw messages from `friday_messages`.<br>**Missing**: Rolling summary logic, 10–15 raw turn truncation cutoff, explicit token budget manager.<br>**Conflict**: Architecture calls for `conversation_sessions.summary` + 10–15 messages. Current codebase queries up to 20 raw messages directly from `friday_messages` without a session summary table. |
| **§7 Long-Term Memory** | **Partially Implemented / Conflicts** | **Exists**: `friday_memories` table with 768-dim vector embeddings, `category` column, and `friday_entities` table.<br>**Missing**: `importance` and `confidence` columns, memory lifecycle states (`candidate`, `validated`, `active`, `stale`, etc.), `memory_history` table.<br>**Conflict**: Existing DB categories (`preference`, `emotional_moment`, `life_event`, `recurring_pattern`, `entity_link`) conflict with proposed categories (`identity`, `relationship`, `project`, `habit`, `event`, `conversational`, `interaction_style`). Existing memory lookups rely on vector embeddings rather than category/keyword filtering. |
| **§8 Extraction Pipeline** | **Partially Implemented / Conflicts** | **Exists**: `friday-reflection/index.ts` processes unreflected messages, extracts entities/memories/vibe using Gemini, generates embeddings, and writes to `friday_entities` and `friday_memories`.<br>**Missing**: Modular sub-files (`dedup.ts`, `conflict-resolver.ts`), `pg_trgm` similarity deduplication, `memory_history` conflict tracking, explicit `supersedes` flag logic.<br>**Conflict**: Proposed architecture specifies `extract-memories` with `pg_trgm` text matching and no embeddings in v1. Existing system uses `friday-reflection` with Gemini vector embeddings (`match_friday_memories` RPC). |
| **§9 Memory Retrieval** | **Partially Implemented / Conflicts** | **Exists**: `contextBuilder.ts` performs entity name/alias string matching and 768-dim Gemini vector search (`match_friday_memories` RPC).<br>**Missing**: Groq classifier category tagging for retrieval, ranking by `importance` and recency.<br>**Conflict**: Proposed architecture relies on Groq category classification without embeddings in Phase 1. Existing code relies on Gemini vector embeddings and entity matching. |
| **§11 DB Schema** | **Partially Implemented / Conflicts** | **Exists**: `profiles`, `friday_messages`, `friday_entities`, `friday_memories`, `chat_sync_pipe`.<br>**Missing**: `conversation_sessions`, `memory_history`, `action_requests`, `personality_config`.<br>**Conflict**: Table and key structures differ fundamentally: Primary user keys are `text` persona IDs (`phesty_official`) rather than `uuid` references to `auth.users`. Column names differ (`memory_text` vs `content`, `owner_id` vs `user_id`). |
| **§12 Personality** | **Partially Implemented / Conflicts** | **Exists**: `personality.ts` (`getPersonalityBlock`) and dynamic `profiles.friday_vibe` column.<br>**Missing**: `personality_config` table.<br>**Conflict**: Proposed design edits personality via `personality_config` Postgres table; existing code uses a TypeScript file (`personality.ts`) + `friday_vibe` DB column. |
| **§14 Edge Function Layout** | **Partially Implemented / Conflicts** | **Exists**: `friday-orchestrator`, `friday-reflection`, `friday-initiative`, `friday-pending-messages`, `notify-trigger`.<br>**Missing**: `chat/` folder name, `_shared/` directory, `action-schema.ts`, `action-validator.ts`, `cron-sweep/`.<br>**Conflict**: Edge function directory and file structures differ from the proposed tree layout. |
| **§16 Action Schema** | **Partially Implemented / Conflicts** | **Exists**: Fast-path command intent classification in `classifier.ts`, payload dispatch via `chat_sync_pipe`, and execution in client `LocalCommandExecutor.kt`.<br>**Missing**: `action-schema.ts`, `action-validator.ts`, `action_requests` DB table, server-side allowlist validator, action confirmation workflow.<br>**Conflict**: Proposed design returns action JSON alongside or instead of LLM reply with explicit validation. Existing system intercepts commands before prompt assembly in `classifier.ts`, emits a fixed `"On it"` reply, and pushes a command payload directly to `chat_sync_pipe`. |
| **§17 Background Processing** | **Partially Implemented / Conflicts** | **Exists**: Postgres `pg_cron` job (`friday-worker`) invoking `friday-reflection` for messages idle >30 minutes.<br>**Missing**: Client app idle trigger dispatch, `conversation_sessions.extraction_job_id` idempotency lock.<br>**Conflict**: Existing background mechanism uses Postgres `pg_cron` and `reflected_at` timestamp flags on `friday_messages`, rather than client-triggered sessions with `extraction_job_id`. |
| **§21 Security** | **Partially Implemented / Conflicts** | **Exists**: Edge functions store Groq/Gemini API keys in Supabase environment secrets.<br>**Missing**: RLS policies scoped to `auth.uid()`.<br>**Conflict**: Proposed security model relies on `auth.uid()` RLS and prohibits client-side API keys. Existing system uses custom passkeys (`access_keys` table) with permissive RLS, and the Android client APK contains `GROQ_API_KEY` in `BuildConfig` for `GroqApiService.kt`. |
