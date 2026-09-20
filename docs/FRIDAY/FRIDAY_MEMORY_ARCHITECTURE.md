# FRIDAY Memory Architecture Report (Baroness App)

## 1. Database Storage Schema

### Core Tables
- **`friday_messages`**: Stores the raw conversation history.
  - `id`: UUID (Primary Key)
  - `owner_id`: Text (Ties to user persona)
  - `sender`: Text (User ID or 'friday')
  - `message`: Text
  - `session_id`: UUID (Used to group messages for reflection)
  - `reflected_at`: Timestamptz (Null until processed by the memory engine)
  - `reply_to_id`: UUID (Unique constraint to prevent double responses)
  - `status`: Text ('THINKING', 'SENT')

- **`friday_memories`**: Stores distilled long-term facts/vibrations.
  - `id`: Bigint (Primary Key)
  - `memory_text`: Text (Factual distilled string)
  - `embedding`: Vector(768) (For semantic search)
  - `category`: Text (preference, emotional_moment, life_event, etc.)
  - `follow_up_worthy`: Boolean (Triggers proactive check-ins)

- **`friday_entities`**: "Who's Who" tracker for people and places.
  - `name`: Text (Unique per owner)
  - `aliases`: Text Array
  - `last_known_fact`: Text

---

## 2. Supabase Edge Function Execution

### Execution Kernel: `friday-orchestrator`
- **Trigger**: Database Webhook (`AFTER INSERT` on `friday_messages`).
- **Flow**: 
  1. Receives user message.
  2. Sends `START_TYPING` signal to `chat_sync_pipe`.
  3. Classifies intent (Groq).
  4. Builds context (Last 20 msgs + Entity match + Vector search).
  5. Generates reply (Groq primary / Gemini fallback).
  6. Saves reply to DB and sends data packet to `chat_sync_pipe`.

### Extraction Secretary: `friday-reflection`
- **Trigger**: `pg_cron` worker (every 10 mins) or manual POST with `force=true`.
- **Mechanism**: Scans for sessions where `reflected_at IS NULL` and inactivity > 30 mins.
- **Security**: Handshake via `X-Internal-Secret` header (`mr.nice_guy`).

### Proactive Engine: `friday-initiative`
- **Trigger**: `pg_cron` worker (every 10 mins).
- **Mechanism**: Scans `friday_memories` for `follow_up_worthy` items in the 12-18h window.

---

## 3. LLM Extraction Setup

- **Model Hierarchy**:
  - **Chat/Reasoning**: `openai/gpt-oss-120b` (Primary)
  - **Memory Extraction**: `openai/gpt-oss-20b` (Chosen for structured JSON stability).
  - **Embeddings**: `text-embedding-004` (Gemini, 768 dimensions).

- **Output Enforcement**: Strict system prompting with few-shot examples and `JSON_OBJECT` response format where supported. Forces third-person factual extraction.

---

## 4. Android Client Integration

- **Sync Core**: `ChatSyncWorker` pushes local Room records to Supabase PostgREST.
- **Real-time Pipe**: `ChatRepository.kt` maintains a WebSocket connection to `chat_sync_pipe_[persona_id]`.
- **Pacing**: Repository parses `typing_duration_ms` from incoming payloads to drive the `TypingIndicator` state flow in the UI before revealing the `MessageEntity`.
- **UI State**: `FridayChatViewModel` observes a `Flow<List<Message>>` from Room, making the UI 100% reactive to both local and remote changes.
