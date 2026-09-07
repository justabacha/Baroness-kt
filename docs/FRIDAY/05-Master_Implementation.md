# 05 — Master Implementation

This is the execution contract. Every Work Package (WP) below is self-contained: it lists its prerequisites, the exact spec sections it needs, the files it touches, and how to verify it's done. **A developer or AI agent should be able to execute any single WP by reading only that WP plus its listed reference sections — nothing else in this tree.**

Do not skip ahead. Do not combine WPs. Execute in numeric order unless prerequisites explicitly allow parallelism (none do at this scope — this is a small, sequential build).

---

## WP-01: Supabase Project Setup and Extensions

**Objective:** Create the Supabase project and enable required database extensions.

**Prerequisites:** None. This is the first WP.

**Reference docs:** 02-Database_and_Memory_Spec.md §1.

**Target files:** None (this WP is pure infrastructure setup, no repo files created yet).

**Execution requirements:**
1. Create a new Supabase project via the Supabase dashboard (manual, outside this repo).
2. Open the SQL Editor and run exactly the two `create extension` statements from 02 §1.
3. Note the project's URL and generate a service role key from Project Settings → API.

**Acceptance criteria:**
- Running `select * from pg_extension where extname in ('vector','pgcrypto');` in the SQL Editor returns both rows.
- You have the project URL and service role key recorded for use in WP-02 and WP-06.

**Context boundaries:** Do not create any application tables yet (that's WP-02). Do not touch any backend or client code.

---

## WP-02: Database Schema Migrations

**Objective:** Create all application tables and the vector search function.

**Prerequisites:** WP-01.

**Reference docs:** 02-Database_and_Memory_Spec.md §2 (all subsections: 2.1, 2.2, 2.3, 2.4).

**Target files:** None (SQL run directly in Supabase SQL Editor; optionally save the run SQL as `db/migrations/001_init.sql` in the repo for version history, but this is not required for functionality).

**Execution requirements:**
1. Before running §2.3, confirm the embedding model dimension you intend to use in WP-06 (default assumed: 768 for `text-embedding-004`). If different, adjust the `vector(768)` declarations in both §2.3 and §2.4 to match before running.
2. Run, in exact order: §2.1 (`messages`), §2.2 (`entities`), §2.3 (`memories`), §2.4 (`match_memories` function).
3. Run the verification query at the end of §2.4.

**Acceptance criteria:**
- `messages`, `entities`, and `memories` tables exist and are visible in the Supabase Table Editor.
- The verification query from §2.4 runs without a syntax error (empty result set is expected and fine).

**Context boundaries:** Do not write any backend code yet. Do not insert any test data beyond what the verification query itself requires.

---

## WP-03: Backend Project Scaffold

**Objective:** Create the backend Node/TypeScript project structure with no business logic yet — just the skeleton and config loading.

**Prerequisites:** WP-02.

**Reference docs:** 03-Backend_Orchestrator_Spec.md §1 (folder structure), §2 (env vars), §11 (setup commands).

**Target files:**
- `backend/package.json`, `backend/tsconfig.json`
- `backend/src/index.ts` (bootstrap only — Express app that starts and listens, no routes yet)
- `backend/src/config/env.ts` (loads and validates all env vars from 03 §2, throws on any missing)
- `backend/.env` (populated with real values from WP-01/WP-02; not committed — add to `.gitignore`)
- Empty placeholder files for every module listed in 03 §1's folder tree (`modules/*.ts`, `db/supabaseClient.ts`, `types/api.ts`), each containing only a file-level comment stating its future job (one line, quoting its "Job:" line from 03) — no implementation yet.

**Execution requirements:**
1. Run the exact npm commands from 03 §11.
2. Create the folder structure exactly as in 03 §1.
3. Implement `config/env.ts` to load and validate every variable listed in 03 §2 (throw with a clear message naming the missing variable if any is absent).
4. Implement `db/supabaseClient.ts`: a single exported Supabase client instance constructed from `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`.
5. `index.ts` should start Express on `PORT` and log a startup message. No `/chat` route yet.

**Acceptance criteria:**
- `npm run dev` starts the server without throwing, and logs a listening message.
- Deleting any required env var from `.env` and restarting causes `env.ts` to throw a clear error naming that variable.

**Context boundaries:** Do not implement any module's actual logic (classifier, context builder, etc.) — that's WP-04 through WP-09. Do not create the `/chat` route yet — that's WP-11. [FIX 5: cross-reference corrected from WP-10 to WP-11.]

---

## WP-04: Classifier Module

**Objective:** Implement command-vs-conversation classification.

**Prerequisites:** WP-03.

**Reference docs:** 01-FRIDAY_Philosophy_and_Architecture.md §3 (classification philosophy). 03-Backend_Orchestrator_Spec.md §3 (exact prompt, function signature, confidence threshold, error handling).

**Target files:** `backend/src/modules/classifier.ts` only.

**Execution requirements:**
1. Implement `classifyMessage(message: string): Promise<ClassificationResult>` exactly per 03 §3's function signature.
2. Use the exact prompt template from 03 §3, substituting `{{MESSAGE}}`.
3. Call Groq's API (using `GROQ_API_KEY` from env) for the classification call.
4. Implement the confidence override in code: if `classification === 'COMMAND'` and `confidence < 0.7`, override to `CONVERSATION`.
5. On any Groq call failure or JSON parse failure, default to `{ classification: 'CONVERSATION', intent: null, parameters: null, confidence: 0 }` and log the error — do not throw out of this function.

**Acceptance criteria:**
- Unit test (or manual script) calling `classifyMessage("play some jazz")` returns `classification: 'COMMAND'`, `intent: 'play_music'`.
- `classifyMessage("I think I'm going to quit my job")` returns `classification: 'CONVERSATION'`.
- `classifyMessage("can you play something")` (a borderline case) — confirm the confidence override path is at least reachable by testing with a mocked low-confidence Groq response.
- Simulating a Groq API failure (e.g. invalid API key temporarily) results in a `CONVERSATION` fallback, not a thrown exception.

**Context boundaries:** Do not touch `contextBuilder.ts`, `commandExecutor.ts`, or `routes/chat.ts`. This module has no knowledge of the database or of any other module.

---

## WP-05: Context Builder Module

**Objective:** Implement full context assembly for conversation-path messages.

**Prerequisites:** WP-02, WP-03.

**Reference docs:** 01-FRIDAY_Philosophy_and_Architecture.md §2 (three-layer memory model), §5 (Tea Test). 02-Database_and_Memory_Spec.md §2.1–2.4 (schema and `match_memories`). 03-Backend_Orchestrator_Spec.md §4 (exact steps, function signature, block formats).

**Target files:** `backend/src/modules/contextBuilder.ts` only.

**Execution requirements:**
1. Implement `buildContext(userId: string, message: string): Promise<AssembledContext>` exactly per 03 §4.
2. Step 1 — Working Memory: query `messages` per 02 §2.1's indexed query pattern (last 20, by `user_id`, ordered and reversed).
3. Step 2 — Entity lookup: case-insensitive substring match against `entities.name` and `entities.aliases` for the given `user_id`.
4. Step 3 — Deep Memory: generate an embedding for `message` (using `EMBEDDING_MODEL` from env — implement a small embedding helper inline or in this file, calling the same provider used in WP-10's reflection module for consistency), call `match_memories` via the Supabase client, then apply the Tea Test third-step filter exactly as described in 03 §4 step 3. [FIX 5: cross-reference corrected from WP-09 to WP-10, matching the renumbered Session Manager and Reflection WP.]
5. Step 4/5 — Personality + assembly: import `personality.ts` (WP-07) and assemble the final `systemPrompt` string in the exact block order specified in 03 §4 step 5, using the exact block formats shown there.

**Acceptance criteria:**
- Calling `buildContext` for a user with no prior messages/entities/memories returns a context with an empty `chatHistory`, no entity block, no memory block, and just the personality block as `systemPrompt`.
- Calling it after seeding a test entity row and a test memory row (matching the incoming message's topic) returns a `systemPrompt` containing both the entity block and memory block, correctly formatted per 03 §4.
- Seeding a memory that is topically unrelated to the test message and has low cosine similarity is confirmed absent from the result (threshold cutoff working).

**Context boundaries:** Do not implement `personality.ts` here (WP-07) beyond importing its exported function — if it doesn't exist yet, stub a minimal version returning a placeholder string only for testing, then replace the import once WP-07 lands. Do not touch the classifier, command executor, or LLM router.

---

## WP-06: LLM Router Module

**Objective:** Implement Gemini-primary/Groq-fallback reply generation.

**Prerequisites:** WP-03.

**Reference docs:** 01-FRIDAY_Philosophy_and_Architecture.md §9 (no complex failover — simple try/catch only). 03-Backend_Orchestrator_Spec.md §5 (exact function, error shape).

**Target files:** `backend/src/modules/llmRouter.ts` only.

**Execution requirements:**
1. Implement `generateReply(context: AssembledContext): Promise<{ text: string; providerUsed: 'gemini' | 'groq' }>` exactly per 03 §5's code block — a single try/catch, no retries, no backoff.
2. Implement internal `callGemini` and `callGroq` helpers that map `AssembledContext` to each provider's expected request shape (system instruction + message array).
3. On both providers failing, throw exactly `new Error('LLM_UNAVAILABLE')` — this specific string is relied on by `routes/chat.ts` in WP-11. [FIX 5: cross-reference corrected from WP-10 to WP-11.]

**Acceptance criteria:**
- With valid API keys, `generateReply` against a simple test context returns `providerUsed: 'gemini'`.
- Temporarily using an invalid Gemini key (env override in a test) causes fallback to Groq and returns `providerUsed: 'groq'`.
- Invalidating both keys causes the function to throw `Error('LLM_UNAVAILABLE')`, not any other error shape.

**Context boundaries:** This module must not import from `contextBuilder.ts`, `classifier.ts`, or any database module — it only receives an already-built `AssembledContext` and returns text. No knowledge of memory, personality composition, or entities beyond what's already inside the string it receives.

---

## WP-07: Personality Module

**Objective:** Implement the static vibe-string function.

**Prerequisites:** WP-03.

**Reference docs:** 01-FRIDAY_Philosophy_and_Architecture.md §4 (vibe, not rules). 03-Backend_Orchestrator_Spec.md §7 (exact string).

**Target files:** `backend/src/modules/personality.ts` only.

**Execution requirements:**
1. Implement `getPersonalityBlock(): string` returning exactly the string given in 03 §7 (verbatim is fine — it was written to spec, not placeholder text).

**Acceptance criteria:**
- Calling the function returns a non-empty string with no template placeholders remaining.
- Function has zero I/O — confirm no imports of the Supabase client, `fetch`, or any provider SDK in this file.

**Context boundaries:** Do not add dynamic parameters, mood state, or per-user variation — that's explicitly out of scope at this stage per 01 §4 and 03 §7.

---

## WP-08: Delay Module

**Objective:** Implement the typing-duration calculation.

**Prerequisites:** WP-03.

**Reference docs:** 01-FRIDAY_Philosophy_and_Architecture.md §6 (delay philosophy). 03-Backend_Orchestrator_Spec.md §8 (exact formula).

**Target files:** `backend/src/modules/delay.ts` only.

**Execution requirements:**
1. Implement `computeDelay(input: DelayInput): number` exactly per the code block in 03 §8, including the command-path shortcut (`300`), the characters-per-ms constant, the 6-second cap, and the emotional-pause addition.

**Acceptance criteria:**
- `computeDelay({ replyText: 'hi', isCommand: true, isEmotionallyWeighted: false })` returns `300`.
- `computeDelay({ replyText: 'a'.repeat(1000), isCommand: false, isEmotionallyWeighted: false })` returns a value at the 6000+400 cap boundary (confirm the `Math.min` cap is applied before adding the floor).
- Adding `isEmotionallyWeighted: true` increases the result by exactly 1500ms relative to an otherwise identical input.

**Context boundaries:** Pure function only — no I/O, no imports beyond what's needed for the arithmetic. Do not decide `isEmotionallyWeighted` here; per 03 §8 (FIX 1), that flag is computed by a separate hardcoded keyword-matching function and called from `routes/chat.ts` (WP-11) before `computeDelay` is invoked. [FIX 5: cross-reference corrected from WP-10 to WP-11, matching the renumbered /chat Route WP.]

---

## WP-09: Command Executor Module

**Objective:** Implement command validation/normalization for the command path.

**Prerequisites:** WP-03, WP-04 (needs `ClassificationResult` type).

**Reference docs:** 01-FRIDAY_Philosophy_and_Architecture.md §3 (command path is fast, narrow). 03-Backend_Orchestrator_Spec.md §6 (exact supported intents, fallback behavior).

**Target files:** `backend/src/modules/commandExecutor.ts` only.

**Execution requirements:**
1. Implement `executeCommand(classification: ClassificationResult): CommandResult` per 03 §6.
2. Support exactly these intents: `play_music`, `navigate`, `set_timer`, `set_alarm`. Any other intent value maps to `{ intent: 'unsupported', parameters: {}, acknowledgment: "I'm not able to do that yet" }`.
3. Acknowledgment strings are short, hardcoded/templated per intent (no LLM call here) — e.g. `"on it"` for `play_music`.

**Acceptance criteria:**
- Passing a classification with `intent: 'play_music'` returns a `CommandResult` with matching `intent` and passed-through `parameters`.
- Passing an unrecognized intent string returns the `unsupported` fallback shape exactly as specified.

**Context boundaries:** This module does not execute anything (no phone/system access — that's the Android client, doc 04). It only validates and normalizes. Do not add any LLM call for acknowledgment text generation.

---

## WP-10: Session Manager and Reflection [FIX 5 — renumbered from WP-11]

**Objective:** Implement session gap detection, reflection-on-session-end, and the scheduled initiative check.

**Prerequisites:** WP-02, WP-03, WP-06 (reflection and initiative-message generation use LLM calls directly).

**Reference docs:** 01-FRIDAY_Philosophy_and_Architecture.md §2.4 (reflection), §7 (initiative window). 02-Database_and_Memory_Spec.md §4 (exact reflection process, JSON shape, storage discipline), §2.1 (`is_proactive` column). 03-Backend_Orchestrator_Spec.md §9 (exact code for `getOrCreateSessionId`, reflection prompt, `checkInitiative`, the initiative message prompt, and embedding-failure handling during reflection), §10a (`GET /pending-messages`, which this WP's writes must satisfy).

**Target files:** `backend/src/modules/sessionManager.ts`, `backend/src/modules/reflection.ts`, an internal route for the scheduled check (e.g. `backend/src/routes/internal.ts` registering `POST /internal/check-initiative`), and the `INTERNAL_CRON_SECRET` env var addition.

**Execution requirements:**
1. Implement `getOrCreateSessionId(userId)` exactly per 03 §9's first code block, including the reflection trigger on session end.
2. Implement `runReflection(sessionId)` in `reflection.ts` per 02 §4 and 03 §9's exact reflection prompt: fetch non-command messages for the session, call the reflection LLM prompt, parse JSON, upsert entities, resolve `entity_ref`, embed memory content, insert into `memories` with `source_session_id` set. On malformed/failed response, write nothing and log — no partial writes. Per 03 §9's embedding-failure handling: if embedding a specific memory's content fails, skip only that memory and still complete all entity upserts.
3. Implement `checkInitiative(userId)` per 03 §9's second code block, including the `INITIATIVE_MIN_HOURS`/`MAX_HOURS` window check and the "already sent for this gap" guard.
4. Implement `generateFollowUpMessage(lastMemory)` using the exact prompt template from 03 §9, calling Gemini Flash directly. Store the result via `storeOutboundProactiveMessage` as an assistant-role `messages` row with `is_command: false`, `is_proactive: true`, and a **new** `session_id` distinct from the session that triggered the check.
5. Add the protected `/internal/check-initiative` endpoint checking `INTERNAL_CRON_SECRET` before running `checkInitiative` for each user in `APP_USER_IDS`.

**Acceptance criteria:**
- Seeding a `messages` row with `created_at` older than `SESSION_IDLE_GAP_MINUTES` and then calling `getOrCreateSessionId` returns a new UUID different from the old session, and triggers a reflection call (mockable/verifiable via log or a test double).
- Running `runReflection` against a seeded test session with a fabricated LLM response inserts matching rows into `entities` and `memories`, with correct category values and `entity_id` resolution.
- Simulating an embedding failure for one `memories[]` item during a reflection run confirms that item is skipped, all other memories and all entities are still written, and the run is not marked failed.
- Calling `checkInitiative` with a fabricated last-message timestamp inside the 12–18 hour window, and a plausible follow-up-worthy memory present, results in a new proactive message being stored with `is_proactive: true` and a fresh `session_id`; calling it outside that window results in no action.
- The stored proactive message is confirmed retrievable via a subsequent `GET /pending-messages` call (WP-11) once that endpoint exists — for this WP in isolation, verify directly against the `messages` table instead.
- Hitting `/internal/check-initiative` without the correct `INTERNAL_CRON_SECRET` header returns an auth error and does not run any check.

**Context boundaries:** Do not have this module call `contextBuilder.ts` or `classifier.ts` — reflection and initiative-checking are independent of the live chat path per Philosophy §8. `runReflection` and `checkInitiative` are the only functions in the entire backend permitted to write to `entities` and `memories`.

---

## WP-11: `/chat` Route and `/pending-messages` Route — Orchestration [FIX 5 — renumbered from WP-10]

**Objective:** Wire together classifier, context builder, LLM router, command executor, and delay into the live `/chat` endpoint plus message persistence, and implement the `GET /pending-messages` endpoint for proactive message delivery.

**Prerequisites:** WP-04, WP-05, WP-06, WP-07, WP-08, WP-09, WP-10 (needs `sessionManager.getOrCreateSessionId` and the `is_proactive` writes from WP-10 to exist).

**Reference docs:** 03-Backend_Orchestrator_Spec.md §10 (exact `/chat` API contract, orchestration order, exact emotional-weight keyword list from §8, embedding-failure handling from §4), §10a (exact `/pending-messages` contract).

**Target files:** `backend/src/routes/chat.ts`, `backend/src/routes/pendingMessages.ts`, `backend/src/types/api.ts` (request/response types matching 03 §10 and §10a exactly), registration of both routes in `backend/src/index.ts`.

**Execution requirements:**
1. Implement the exact orchestration order from 03 §10's final paragraph: `sessionManager.getOrCreateSessionId` → `classifier.classifyMessage` → branch on classification → (command path: `commandExecutor.executeCommand` → `delay.computeDelay({isCommand:true})`) or (conversation path: `isEmotionallyWeighted(message)` keyword check (03 §8) → `contextBuilder.buildContext` → `llmRouter.generateReply` → `delay.computeDelay({isCommand:false,...})`) → persist user message and assistant reply to `messages` table (with correct `session_id`, `is_command`, and `is_proactive: false`) → respond with the exact JSON shape from 03 §10.
2. Catch `Error('LLM_UNAVAILABLE')` from the LLM router specifically and respond with the degraded shape from 03 §10, `degraded: true`.
3. Implement `GET /pending-messages` per 03 §10a: query `messages` for `user_id`, `role = 'assistant'`, `is_proactive = true`, `created_at > since`, ordered oldest first; return the exact JSON shape from 03 §10a.
4. Register both routes in `index.ts`.

**Acceptance criteria:**
- A `curl` POST with a command-like message returns the command-path response shape from 03 §10, including a `command` object.
- A `curl` POST with a conversational message returns the conversation-path shape, and both the user's message and FRIDAY's reply appear as new rows in `messages` with matching `session_id` and `is_proactive: false`.
- A `curl` POST with a message containing an emotional keyword (e.g. "I'm feeling really overwhelmed") produces a longer `typing_duration_ms` than an otherwise-similar-length message without one.
- Temporarily breaking both LLM provider keys and retrying `/chat` returns the degraded shape, not a 500 error.
- Temporarily breaking the embedding call and retrying `/chat` with a conversational message still returns a normal (non-degraded) reply, confirming the embedding-failure path from 03 §4 doesn't fail the whole request.
- A `curl` GET to `/pending-messages` with a `since` timestamp before a WP-10-generated proactive message's `created_at` returns that message; a `since` timestamp after it returns an empty array.

**Context boundaries:** Do not add new business logic in either route file — they only sequence existing module calls. If you find yourself writing an if/else that isn't purely about routing between command/conversation paths (in `chat.ts`) or a straightforward filtered query (in `pendingMessages.ts`), that logic belongs in a module, not here.

---

## WP-12: Backend Deployment

**Objective:** Deploy the backend as a persistent process and wire up the external cron for the initiative check.

**Prerequisites:** WP-10, WP-11.

**Reference docs:** 03-Backend_Orchestrator_Spec.md §11 (deployment note), §9 (cron curl example).

**Target files:** A `Dockerfile` (if the chosen host requires one) and deployment platform configuration (outside the repo, e.g. host dashboard settings) — no application source changes.

**Execution requirements:**
1. Deploy to a host supporting a persistent Node process (per 03 §11 — not a serverless-functions-only platform).
2. Set all env vars from 03 §2 plus `INTERNAL_CRON_SECRET` (WP-10) in the host's environment configuration.
3. Set up an external hourly cron hitting `/internal/check-initiative` with the correct Authorization header, per the exact `curl` example in 03 §9.

**Acceptance criteria:**
- The deployed `/chat` endpoint responds correctly to a test `curl` request (same check as WP-11, run against the production URL).
- The deployed `/pending-messages` endpoint responds correctly to a test `curl` request (same check as WP-11).
- The cron job's first scheduled run is confirmed (via logs) to have hit `/internal/check-initiative` successfully.

**Context boundaries:** No code changes in this WP — infrastructure/configuration only.

---

## WP-13: Android Project Scaffold

**Objective:** Create the Android project structure with no chat logic yet.

**Prerequisites:** None (can run in parallel with backend WPs, but needs WP-12's deployed URL before WP-18 can be tested end-to-end).

**Reference docs:** 04-Android_Client_Spec.md §1 (package structure), §8.1–8.2 (Gradle deps, manifest).

**Target files:** New Android Studio project matching the package structure in 04 §1; `app/build.gradle.kts` with dependencies from 04 §8.1; `AndroidManifest.xml` with additions from 04 §8.2 (internet permission and `<queries>` block — the queries block can be added now even though the command executor isn't implemented until WP-16).

**Execution requirements:**
1. Create a new Compose-based Android Studio project.
2. Create the exact folder/package structure from 04 §1 (empty files with a one-line comment stating future purpose, same pattern as WP-03).
3. Add dependencies and manifest entries per 04 §8.1–8.2.

**Acceptance criteria:**
- Project builds successfully (`./gradlew assembleDebug`) with the empty scaffold in place.

**Context boundaries:** Do not implement any Composables, ViewModel logic, or networking yet.

---

## WP-14: Android Chat UI (Static)

**Objective:** Build the chat screen UI with static/mock data — no networking yet.

**Prerequisites:** WP-13.

**Reference docs:** 04-Android_Client_Spec.md §2 (all subsections: ChatScreen, MessageBubble, TypingIndicator).

**Target files:** `ui/chat/ChatScreen.kt`, `ui/chat/MessageBubble.kt`, `ui/chat/TypingIndicator.kt`, `ui/theme/*` (standard Compose theme boilerplate).

**Execution requirements:**
1. Implement `MessageBubble.kt` and `UiMessage` exactly per 04 §2.2.
2. Implement `TypingIndicator.kt` per 04 §2.3.
3. Implement `ChatScreen.kt` per 04 §2.1, wired to a temporary hardcoded list of `UiMessage` (no ViewModel network calls yet — a local `mutableStateOf` list is fine for this WP).

**Acceptance criteria:**
- Running the app shows a scrollable list of mock message bubbles correctly left/right aligned by `isFromUser`, with a working animated typing indicator toggleable via a temporary debug button or preview.

**Context boundaries:** Do not implement `ChatViewModel.kt`'s real logic, Retrofit calls, or command execution yet.

---

## WP-15: Android Networking Layer

**Objective:** Implement Retrofit/OkHttp setup and DTOs matching the backend contract exactly.

**Prerequisites:** WP-13, WP-11 (needs the finalized `/chat` and `/pending-messages` contracts to exist and be stable). [FIX 5: cross-reference corrected from WP-10 to WP-11.]

**Reference docs:** 04-Android_Client_Spec.md §6 (all subsections). 03-Backend_Orchestrator_Spec.md §10 (source of truth for the exact JSON shape — DTOs must match this exactly, field-for-field).

**Target files:** `data/remote/ChatDtos.kt`, `data/remote/FridayApiService.kt`, `data/remote/RetrofitClient.kt`, `build.gradle.kts` `buildConfigField` additions per 04 §6.3's manual step.

**Execution requirements:**
1. Implement `ChatRequest`, `ChatResponse`, `CommandDto` exactly per 04 §6.1, matching every field name/type in 03 §10's JSON examples.
2. Implement `FridayApiService` and `RetrofitClient` per 04 §6.2–6.3, including the debug/release `BASE_URL` build config split.

**Acceptance criteria:**
- A manual test call (e.g. from a temporary debug button or unit test with a mock server) against the deployed backend (WP-12) successfully deserializes both a command-path and conversation-path response into `ChatResponse` without field mismatches.

**Context boundaries:** Do not implement `ChatRepository.kt` or `ChatViewModel.kt` here — this WP is the raw networking layer only.

---

## WP-16: Android Local Command Executor

**Objective:** Implement local dispatch of backend-classified commands to system intents.

**Prerequisites:** WP-13.

**Reference docs:** 04-Android_Client_Spec.md §3 (exact code and manifest queries).

**Target files:** `command/LocalCommandExecutor.kt`, manifest `<queries>` additions if not already present from WP-13.

**Execution requirements:**
1. Implement `LocalCommandExecutor` exactly per 04 §3, supporting `play_music`, `navigate`, `set_timer`, `set_alarm`, and the `unsupported`/unknown no-op cases.

**Acceptance criteria:**
- Manually invoking `execute()` with each supported `CommandDto` on a test device/emulator successfully launches the corresponding system intent (Maps for navigate, system timer UI for set_timer, etc.) — Maps and Clock apps must be present on the test device/emulator for this check.
- Invoking with an `unsupported` intent is confirmed to be a no-op (no crash, no intent launched).

**Context boundaries:** Do not wire this into `ChatViewModel.kt` yet — that's WP-18. This WP only proves the executor works in isolation.

---

## WP-17: Android Session Tracker

**Objective:** Implement foreground/background tracking for proactive message polling.

**Prerequisites:** WP-13, WP-15 (calls into repository, so repository's fetch method must at least have a stub signature).

**Reference docs:** 04-Android_Client_Spec.md §4.

**Target files:** `session/SessionTracker.kt`, registration in `MainActivity.kt`.

**Execution requirements:**
1. Implement `SessionTracker` per 04 §4, calling `repository.fetchPendingProactiveMessages()` on `onStart`.
2. Register it against `ProcessLifecycleOwner` in `MainActivity.onCreate` exactly as shown.
3. If `ChatRepository.fetchPendingProactiveMessages()` doesn't exist yet, add a stub in this WP that will be filled in properly during WP-18.

**Acceptance criteria:**
- Backgrounding and foregrounding the app in a test build is confirmed (via log line) to trigger the fetch call exactly once per foreground transition.

**Context boundaries:** Do not implement the actual proactive-message-fetching network logic here beyond a stub — full repository wiring is WP-18.

---

## WP-18: Android ViewModel and Repository — Full Wiring

**Objective:** Connect UI, networking, command execution, and delay rendering into a fully working chat flow.

**Prerequisites:** WP-14, WP-15, WP-16, WP-17.

**Reference docs:** 04-Android_Client_Spec.md §5 (delay/typing flow, exact `sendMessage` code), §7 (error handling, `NetworkResult`).

**Target files:** `data/repository/ChatRepository.kt`, `ui/chat/ChatViewModel.kt`.

**Execution requirements:**
1. Implement `ChatRepository.sendChatMessage` wrapped in `NetworkResult` per 04 §7.
2. Implement `ChatRepository.fetchPendingProactiveMessages` properly now (replacing WP-17's stub) — polls or fetches any new assistant-originated messages not yet shown locally.
3. Implement `ChatViewModel.sendMessage` exactly per 04 §5's code block: add user message optimistically, set `isTyping = true`, await backend response, `delay(response.typingDurationMs)`, then add assistant message and set `isTyping = false`, then dispatch to `LocalCommandExecutor` if `isCommand`.
4. Implement the `NetworkResult.Error` fallback path per 04 §7: show a local in-character error bubble immediately with no artificial delay, reset `isTyping`.
5. Wire `ChatScreen` (WP-14) to the real `ChatViewModel` instead of mock data.

**Acceptance criteria:**
- End-to-end manual test against the deployed backend (WP-12): sending a conversational message shows the typing indicator for a duration visibly proportional to reply length, then displays the reply.
- Sending a command message (e.g. "set a timer for 5 minutes") results in the system timer UI launching after a near-instant typing indicator flash.
- Disabling network (airplane mode) and sending a message shows the local error fallback bubble immediately, no hung typing indicator.
- Backgrounding the app, using a separate tool (e.g. `curl` directly hitting `/internal/check-initiative` with a manually-seeded old timestamp) to trigger a proactive message, then foregrounding the app shows the proactive message appear.

**Context boundaries:** This is the last WP that touches application logic. No new backend changes should be needed to complete this WP — if one seems necessary, the gap is in the API contract (03 §10) and should be treated as a spec bug to flag, not silently patched around client-side.

---

## WP-19: Android Release Build

**Objective:** Produce a signed, production-configured release build.

**Prerequisites:** WP-18, WP-12 (needs production backend URL).

**Reference docs:** 04-Android_Client_Spec.md §6.3 (release `BASE_URL` config), §8.3 (build commands).

**Target files:** Release signing config in `build.gradle.kts` (standard Android signing config, not detailed further here since it's account/keystore-specific, not FRIDAY-specific).

**Execution requirements:**
1. Confirm `buildTypes.release.buildConfigField` points at the production backend URL from WP-12.
2. Run `./gradlew assembleRelease` per 04 §8.3.

**Acceptance criteria:**
- Release APK/AAB builds successfully and, when installed, connects to the production backend rather than staging.

**Context boundaries:** No application logic changes — build configuration only.

---

## Dependency Graph Summary

**[FIX 5]** Renumbered so the dependency order now reads cleanly top-to-bottom: WP-10 (Session Manager and Reflection) comes before WP-11 (`/chat` + `/pending-messages` Route), since the route depends on the session manager's `getOrCreateSessionId` and the `is_proactive` writes it produces.

```
WP-01 → WP-02 → WP-03 → WP-04, WP-05, WP-06, WP-07, WP-08, WP-09 → WP-10 → WP-11 → WP-12
                                                                              ↓
WP-13 → WP-14                                                                ↓
WP-13 → WP-15 (needs WP-11 contract) ─────────────────────────────────────────┤
WP-13 → WP-16                                                                 │
WP-13, WP-15 → WP-17                                                          │
WP-14, WP-15, WP-16, WP-17 → WP-18 (needs deployed backend, WP-12) ───────────┘
WP-18, WP-12 → WP-19
```
