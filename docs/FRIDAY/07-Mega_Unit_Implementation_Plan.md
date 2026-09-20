# Master Implementation Plan: FRIDAY Mega-Unit Integration

This checklist outlines the step-by-step strategy for migrating FRIDAY from a synchronous client-side chatbot into a highly sophisticated, offline-first **Native Database Intelligence Layer** inside Supabase, deeply integrated with the Baroness-kt Android sync architecture.

---

## 📋 Phase 1: Database Setup & Vector Foundations (Supabase SQL)
Set up schemas, tables, vector search operations, and triggers to manage entity identity cards and deep long-term memory blocks.

- [ ] **1.1 Enable Extensions & Extensions Verification**
  - [ ] Write a migration script to verify/enable the `vector` extension in Supabase.
- [ ] **1.2 Build Entity Tracker Table (`friday_entities`)**
  - [ ] Create fields: `id`, `owner_id` (ties to `currentPersonaId`), `name`, `aliases` (text array), `relationship`, `last_known_fact`, `updated_at`, `created_at`.
  - [ ] Add compound search indexing on `(owner_id, name)`.
- [ ] **1.3 Build Deep Memory Store Table (`friday_memories`)**
  - [ ] Create fields: `id`, `owner_id`, `content`, `category` (`preference`, `emotional_moment`, `life_event`, `recurring_pattern`, `entity_link`), `embedding` (vector size 1536), `session_id`, `follow_up_worthy`, `created_at`.
- [ ] **1.4 Create Similarity Matching Functions (RPC)**
  - [ ] Implement `match_friday_memories` function in PostgreSQL utilizing cosine similarity (`<=>`) with parameters for `query_embedding`, `user_owner_id`, and `match_threshold`.
- [ ] **1.5 Configure Database Webhooks**
  - [ ] Create a Supabase Database Webhook targeting `friday_messages` on `INSERT` actions where `sender != 'friday'` to auto-trigger the Edge Function orchestration loop.

---

## 🛠️ Phase 2: Edge Function Core Architecture (`supabase/functions/friday-orchestrator`)
Create the serverless processing kernel inside Deno/TypeScript that evaluates, manages, and responds to incoming conversation packets.

- [ ] **2.1 Initialize Edge Function Module**
  - [ ] Create the project directory structure under `supabase/functions/friday-orchestrator/`.
  - [ ] Set up environment configuration parameters (`GROQ_API_KEY`, `GEMINI_API_KEY`, etc.).
- [ ] **2.2 Implement Fast Intent Classifier (`classifier.ts`)**
  - [ ] Write the prompt mapping incoming text into `COMMAND` or `CONVERSATION` structures using Groq/Llama-3 (Fast-Path).
  - [ ] Enforce confidence metric evaluations (fallback to conversation path if lower than `0.7`).
- [ ] **2.3 Implement Intelligent Prompt Context Assembler (`contextBuilder.ts`)**
  - [ ] Extract working memory window (last 15-20 rows from `friday_messages`).
  - [ ] Execute case-insensitive text scanning to fetch matching records out of `friday_entities`.
  - [ ] Generate message vector embedding via Gemini embedding models, execute the matching RPC function, and apply the structural **Tea Test** keyword filter.
- [ ] **2.4 Implement Core Router & Persona Injector (`llmRouter.ts`)**
  - [ ] Prep system definitions mapping out Friday’s authentic best-friend communication profile.
  - [ ] Orchestrate Gemini Flash prompt execution with try/catch routing failovers into Groq.
  - [ ] Compute context-aware typing latencies (`typing_duration_ms`) and save the finalized message bubble text back into `friday_messages`.
  - [ ] Drop the outbound sync delivery data package into `chat_sync_pipe`.

---

## 🧠 Phase 3: Background Asynchronous Processing Modules
Set up detached background logic modules to calculate long-term memory optimizations and initiate proactive contact loops.

- [ ] **3.1 Create Reflection Intelligence Engine (`supabase/functions/friday-reflection`)**
  - [ ] Implement session tracking routines calculating when active conversations hit a 30-minute idle gap.
  - [ ] Construct the reasoning prompt sending full transcript blocks to DeepSeek R1 via OpenRouter.
  - [ ] Parse reasoning extractions to upsert fresh Identity cards or inject newly vectorized entries into `friday_memories`.
- [ ] **3.2 Create Proactive Pulse Engine (`supabase/functions/friday-initiative`)**
  - [ ] Formulate background checks checking for `follow_up_worthy = true` fields within the 12-18 hour inactivity matrix.
  - [ ] Setup message synthesis building a warm check-in block based on past memories.
  - [ ] Transmit the generated output by dropping a record into `chat_sync_pipe` using a fresh, independent `session_id`.

---

## 📱 Phase 4: Android Client Integration & Refinement
Clean client-side processing out of view models and integrate structural command maps and custom typing parameters inside your active sync system.

- [ ] **4.1 Decouple Legacy Elements from `FridayChatViewModel.kt`**
  - [ ] Erase direct references to `GroqApiService` and strip out `generateFridayResponse()`.
  - [ ] Refactor `onSendMessage()` to strictly execute standardized local Room additions via `repository.sendMessage("friday", text)`—leaving full delivery architecture entirely to `ChatSyncWorker`.
- [ ] **4.2 Upgrade `ChatRepository.kt` Stream Handling**
  - [ ] Refactor the realtime `handlePipeMessage()` implementation to inspect incoming type keys (`NEW_MESSAGE` vs `COMMAND`).
  - [ ] Extract and transmit `typing_duration_ms` parameters dynamically to coordinate authentic typing pacing before revealing text bubbles.
- [ ] **4.3 Implement Native Action Map Module (`LocalCommandExecutor.kt`)**
  - [ ] Create a standalone execution routing engine mapping incoming intent payloads (`play_music`, `navigate`, `set_timer`, `set_alarm`) directly to native implicit Android System Intents.
  - [ ] Declare query package element visibilities inside `AndroidManifest.xml` to allow system navigation and player control hooks.
- [ ] **4.4 Interconnect Audio Engine Hooks with `VoiceCenter.kt`**
  - [ ] Establish listeners detecting when incoming text bubbles land inside Room from the sync loop.
  - [ ] Pipe clean textual responses directly into `VoiceCenter.speak(text)` to automatically execute parallel audio file stream processing and ExoPlayer execution.
