# 03 — Backend Orchestrator Spec

**Governed by 01-FRIDAY_Philosophy_and_Architecture.md** (especially §3 classification, §6 delay, §7 initiative, §8 modularity, §9 exclusions) and **02-Database_and_Memory_Spec.md** (schema this backend reads/writes).

Stack: **Node.js 20+, TypeScript, Express** (a plain HTTP server — no framework beyond Express is needed per the simplicity mandate in Philosophy §9).

---

## 1. Folder Structure

One file, one job (Philosophy §8). Exact structure:

```
backend/
├── src/
│   ├── index.ts                    # Express app bootstrap, route registration only
│   ├── routes/
│   │   └── chat.ts                 # POST /chat route handler — orchestrates modules, no logic of its own
│   ├── modules/
│   │   ├── classifier.ts           # Groq command/conversation classification
│   │   ├── commandExecutor.ts      # routes classified commands to handlers, returns results
│   │   ├── contextBuilder.ts       # assembles Working Memory + Entities + Deep Memory + personality
│   │   ├── llmRouter.ts            # Gemini primary -> Groq fallback, try/catch only
│   │   ├── personality.ts          # pure function producing the vibe string
│   │   ├── delay.ts                # pure function computing typing_duration_ms
│   │   ├── reflection.ts           # post-session memory extraction job
│   │   └── sessionManager.ts       # session gap detection, session_id issuance, initiative check
│   ├── db/
│   │   └── supabaseClient.ts       # single exported Supabase client instance
│   ├── types/
│   │   ├── supabase.ts             # generated types (02 §1)
│   │   └── api.ts                  # request/response shape types (§10 below)
│   └── config/
│       └── env.ts                  # loads and validates all env vars in one place
├── package.json
├── tsconfig.json
└── .env                            # not committed; see §2
```

**Rule for implementers:** if you are working on `classifier.ts`, you should never need to open `llmRouter.ts` or `commandExecutor.ts`. Each module imports only from `db/`, `config/`, and `types/` — never directly from a sibling module in `modules/` except where explicitly wired in `routes/chat.ts`. `routes/chat.ts` is the only file allowed to import multiple modules and sequence them.

---

## 2. Environment Variables

Exact `.env` keys required. No defaults are silently assumed — `config/env.ts` must throw at startup if any of these is missing.

```
# Supabase
SUPABASE_URL=
SUPABASE_SERVICE_ROLE_KEY=

# LLM providers
GEMINI_API_KEY=
GROQ_API_KEY=

# Embedding model (used by contextBuilder.ts and reflection.ts)
EMBEDDING_MODEL=text-embedding-004      # must match dimension used in memories.embedding (02 §2.3)

# Server
PORT=3000
APP_USER_IDS=user-1,user-2              # comma-separated; single or dual-user per Philosophy scope

# Session
SESSION_IDLE_GAP_MINUTES=30             # inactivity gap that ends a session
INITIATIVE_MIN_HOURS=12
INITIATIVE_MAX_HOURS=18
```

---

## 3. Classifier Module (`classifier.ts`)

**Job:** given a raw incoming message, decide command vs. conversation (Philosophy §3). Nothing else — does not touch the database, does not build context.

**Provider:** Groq (chosen for speed/cost per Philosophy §3).

**Exact prompt template:**

```
You are a fast intent classifier for a personal companion app. Classify the user's
message as exactly one of: COMMAND or CONVERSATION.

COMMAND means the message is a direct, actionable request the app can execute right
now, such as: playing music, navigation/directions, setting a timer or alarm.

CONVERSATION means anything else, including greetings, questions, venting, planning,
or ambiguous phrasing that could be conversational.

If you are not highly confident it is a COMMAND, classify it as CONVERSATION.

Respond with ONLY a JSON object, no other text:
{"classification": "COMMAND" | "CONVERSATION", "intent": string | null, "parameters": object | null, "confidence": number}

Examples:
"play some jazz" -> {"classification":"COMMAND","intent":"play_music","parameters":{"genre":"jazz"},"confidence":0.95}
"navigate home" -> {"classification":"COMMAND","intent":"navigate","parameters":{"destination":"home"},"confidence":0.97}
"set a timer for 10 minutes" -> {"classification":"COMMAND","intent":"set_timer","parameters":{"minutes":10},"confidence":0.98}
"I think I'm going to quit my job" -> {"classification":"CONVERSATION","intent":null,"parameters":null,"confidence":0.99}
"can you play something" -> {"classification":"COMMAND","intent":"play_music","parameters":{"genre":null},"confidence":0.6}

User message: "{{MESSAGE}}"
```

**Confidence threshold:** if `classification === "COMMAND"` but `confidence < 0.7`, override to `CONVERSATION` in code (not in the prompt) — this hard-codes the "bias toward conversation" rule from Philosophy §3 rather than trusting the model to self-regulate it.

**Function signature:**

```typescript
interface ClassificationResult {
  classification: 'COMMAND' | 'CONVERSATION';
  intent: string | null;
  parameters: Record<string, unknown> | null;
  confidence: number;
}

export async function classifyMessage(message: string): Promise<ClassificationResult>
```

**Error handling:** if the Groq call fails or returns unparseable JSON, default to `CONVERSATION` with `intent: null` — never let a classifier failure silently drop a user message. Log the raw failure but do not retry (Philosophy §9 — no retry frameworks).

---

## 4. Context Builder Module (`contextBuilder.ts`)

**Job:** assemble the full prompt context for conversation-path messages only. Never called on the command path.

**Steps, in order:**

1. **Working Memory:** fetch last 20 rows from `messages` for the current `user_id` ordered by `created_at desc`, then reverse to chronological order (Philosophy §2.1).
2. **Entity lookup:** scan the incoming message text for substring matches against `entities.name` and `entities.aliases` for this user (case-insensitive). For each match, fetch the full entity row.
3. **Deep Memory retrieval:**
   - Generate an embedding for the incoming message using `EMBEDDING_MODEL`.
   - Call `match_memories(embedding, user_id, 0.75, 5)` (02 §2.4).
   - Apply the third Tea Test step (Philosophy §5): filter the returned candidates by asking, in code, "is this candidate's category/content plausibly load-bearing for the current message topic?" — implemented as a lightweight secondary check: discard `emotional_moment` and `life_event` memories whose content shares no keyword/entity overlap with the current message or its matched entities. `preference` and `recurring_pattern` memories may be included more liberally since they're standing facts, not situational recall.
   - **[FIX 4] Embedding failure handling:** if the `embedText()` call throws, times out, or returns a vector of the wrong dimension, catch the error, log it, and skip Deep Memory retrieval for this message only — proceed to assemble context from Working Memory + Entities + Personality alone (step 4 below still runs). Do not retry the embedding call (Philosophy §9 — no retry frameworks). Do not fail the `/chat` request. The user-visible effect is that FRIDAY replies without long-term recall for that one turn, which is an acceptable, silent degradation rather than an error state.
4. **Personality string:** call `personality.ts` (§7) to get the vibe block.
5. **Assemble final prompt** in this exact order: personality block → entity context block → deep memory block → working memory (as chat history) → current user message.

**Function signature:**

```typescript
interface AssembledContext {
  systemPrompt: string;        // personality + entity + memory blocks, flattened to one string
  chatHistory: { role: 'user' | 'assistant'; content: string }[];
  currentMessage: string;
}

export async function buildContext(userId: string, message: string): Promise<AssembledContext>
```

**Entity context block format:**
```
Known people/things in the user's life relevant to this conversation:
- Jordan (sister): Going through a divorce, leaning on user for support.
```

**Deep memory block format:**
```
Relevant things you remember:
- User felt anxious the night before their Acme Corp interview.
```

If no entities or memories pass filtering, omit those blocks entirely rather than inserting empty headers.

---

## 5. LLM Router Module (`llmRouter.ts`)

**Job:** send a fully-assembled prompt to an LLM and return the text response. Knows nothing about memory, personality, or classification — receives `AssembledContext` and returns a string. Implements simple failover per Philosophy §9: no retries, no backoff, no circuit breaker — one try/catch.

```typescript
export async function generateReply(context: AssembledContext): Promise<{ text: string; providerUsed: 'gemini' | 'groq' }> {
  try {
    const text = await callGemini(context);
    return { text, providerUsed: 'gemini' };
  } catch (err) {
    console.error('[llmRouter] Gemini failed, falling back to Groq:', err);
    try {
      const text = await callGroq(context);
      return { text, providerUsed: 'groq' };
    } catch (fallbackErr) {
      console.error('[llmRouter] Groq fallback also failed:', fallbackErr);
      throw new Error('LLM_UNAVAILABLE');
    }
  }
}
```

`routes/chat.ts` catches `LLM_UNAVAILABLE` and returns a graceful degraded response to the client (see §10 error shape) — never a raw 500 with a stack trace.

Both `callGemini` and `callGroq` internally map `AssembledContext.systemPrompt` to each provider's system-instruction field and `chatHistory` + `currentMessage` to that provider's message-array format. Model choice: Gemini free-tier flash model as primary (fast, generous free quota, good for conversational tone), Groq's fastest available Llama model as the fallback.

---

## 6. Command Executor Module (`commandExecutor.ts`)

**Job:** given a `ClassificationResult` with `classification === 'COMMAND'`, return a structured command result for the client to execute locally. This module does not execute the command itself (the backend has no access to the phone's music player or navigation app) — it validates and normalizes the intent/parameters and returns them for the Android client's local command executor (04 §3) to act on.

```typescript
interface CommandResult {
  intent: string;
  parameters: Record<string, unknown>;
  acknowledgment: string; // short, in-character confirmation text, e.g. "on it"
}

export function executeCommand(classification: ClassificationResult): CommandResult
```

Supported intents at launch: `play_music`, `navigate`, `set_timer`, `set_alarm`. Unknown intents (classifier hallucinated something outside this list) fall back to `acknowledgment: "I'm not able to do that yet"` with `intent: 'unsupported'`, and the client renders it as a plain conversational bubble rather than attempting execution.

Acknowledgment strings are short and in-character (per Philosophy §4 vibe, not a rules list) but are simple hardcoded/templated strings per intent, not LLM-generated — commands are the fast path and should not incur an extra LLM call for flavor text.

---

## 7. Personality Module (`personality.ts`)

**Job:** pure function, no I/O, returns the vibe string described in Philosophy §4.

```typescript
export function getPersonalityBlock(): string {
  return `You are FRIDAY, a close friend to the person you're talking to — not an
assistant, not a customer service agent. You've known them for a while and you
care about them. You talk the way a genuinely close friend texts: warm, a little
playful, direct when it matters, never performing positivity you don't mean. You
have your own opinions and you're not afraid to gently push back or tease. When
something serious comes up, you drop the playfulness and just show up for them.
You don't over-explain, you don't add disclaimers, and you don't sound like a
support script. You're texting a friend, not filing a report.`;
}
```

This string is intentionally static and underspecified per Philosophy §4 — it is not parameterized per-message beyond what `contextBuilder.ts` prepends around it (entity/memory blocks). No mood state machine, no per-user personality tuning at launch.

---

## 8. Delay Module (`delay.ts`)

**Job:** pure function computing `typing_duration_ms`, per Philosophy §6.

```typescript
interface DelayInput {
  replyText: string;
  isCommand: boolean;
  isEmotionallyWeighted: boolean; // simple heuristic flag, see below
}

export function computeDelay(input: DelayInput): number {
  if (input.isCommand) return 300; // near-instant, minimal indicator flash

  const CHARS_PER_MS = 0.06; // ~60 wpm equivalent typing simulation
  const baseTyping = Math.min(input.replyText.length / CHARS_PER_MS, 6000); // cap at 6s
  const emotionalPause = input.isEmotionallyWeighted ? 1500 : 0;

  return Math.round(emotionalPause + baseTyping + 400); // +400ms minimum floor
}
```

**[FIX 1] Exact emotional weight heuristic.** `isEmotionallyWeighted` is computed upstream in `routes/chat.ts`, on the conversation path only, by a hardcoded keyword match against the user's raw message text — no ML model, no sentiment analysis API, no external service, no reuse of matched-memory signal. This is a plain case-insensitive substring check in code:

```typescript
const EMOTIONAL_KEYWORDS = [
  'sad', 'angry', 'hurt', 'scared', 'worried', 'stressed', 'depressed',
  'anxious', 'crying', 'upset', 'heartbroken', 'lonely', 'overwhelmed',
  'exhausted', 'burnt out', "can't cope", 'giving up', 'hopeless'
];

export function isEmotionallyWeighted(message: string): boolean {
  const lower = message.toLowerCase();
  return EMOTIONAL_KEYWORDS.some((keyword) => lower.includes(keyword));
}
```

Rule: return `true` if any keyword is a substring match anywhere in the lowercased message text; `false` otherwise. This function lives alongside `computeDelay` in `delay.ts` (it is still a pure function, no I/O) and `routes/chat.ts` calls it directly on the incoming message before calling `computeDelay`. The list above is the complete, authoritative list for launch — implementers must not substitute a sentiment API or add unlisted keywords without a spec update.

---

## 9. Session Manager Module (`sessionManager.ts`) and Reflection (`reflection.ts`)

**Session gap detection:**

```typescript
export async function getOrCreateSessionId(userId: string): Promise<string> {
  const lastMessage = await getMostRecentMessage(userId);
  const idleGapMs = Date.now() - new Date(lastMessage?.created_at ?? 0).getTime();
  const idleThresholdMs = Number(process.env.SESSION_IDLE_GAP_MINUTES) * 60 * 1000;

  if (!lastMessage || idleGapMs > idleThresholdMs) {
    // session ended — trigger reflection on the old session, then mint a new one
    if (lastMessage) await runReflection(lastMessage.session_id);
    return crypto.randomUUID();
  }
  return lastMessage.session_id;
}
```

**Reflection job (`reflection.ts`):** implements 02-Database_and_Memory_Spec.md §4. Exact prompt for the reflection LLM call (uses Gemini via `llmRouter`'s underlying provider calls directly, not the conversational router, since this is a structured-extraction task, not a chat reply):

```
Review this conversation session and extract what a close friend would actually
remember afterward. Return ONLY valid JSON in this exact shape:

{
  "entities": [{"name": string, "aliases": string[], "relationship": string, "last_known_fact": string}],
  "memories": [{"content": string, "category": "preference"|"emotional_moment"|"life_event"|"recurring_pattern"|"entity_link", "entity_ref": string | null}]
}

Rules:
- Write memory content in third person, factual, distilled — not a quote from the conversation.
- Only extract things worth remembering weeks later. Skip small talk.
- If nothing is worth remembering, return {"entities": [], "memories": []}.

Conversation:
{{TRANSCRIPT}}
```

Processing steps after receiving the response: exactly as specified in 02 §4.2–4.3 (upsert entities, resolve entity_ref, embed memory content, insert). This module is the only one that writes to `entities` and `memories` — no other module ever writes to those tables directly.

**[FIX 4] Embedding failure handling during reflection.** Entity upserts and memory storage are independent write operations within a single reflection run — a failure in one must not block the other. Specifically: if `embedText()` throws or returns a vector of the wrong dimension for a given `memories[]` item, log the error and skip storing that individual memory only; continue processing the remaining `memories[]` items and proceed with all `entities[]` upserts regardless. Do not retry the embedding call (Philosophy §9). A session that ends with updated entity rows but zero new deep memories (because every embedding call happened to fail) is an acceptable degradation, not a failed reflection run — the job should still be considered to have succeeded and should not be re-queued.

**Initiative check (session-based proactive messaging, Philosophy §7):** a separate scheduled function, not triggered by `/chat` at all.

```typescript
// Run via a scheduled job (e.g. cron-triggered HTTP endpoint hit by an external
// scheduler such as a Supabase Edge Function cron or a simple hosted cron ping —
// no in-process scheduler needed, keeping the backend stateless between invocations).
export async function checkInitiative(userId: string): Promise<void> {
  const lastMessage = await getMostRecentMessage(userId);
  if (!lastMessage) return;

  const gapHours = (Date.now() - new Date(lastMessage.created_at).getTime()) / 3_600_000;
  const min = Number(process.env.INITIATIVE_MIN_HOURS);
  const max = Number(process.env.INITIATIVE_MAX_HOURS);

  if (gapHours < min || gapHours > max) return;
  if (await alreadySentInitiativeForGap(userId, lastMessage.session_id)) return;

  const lastMemory = await getMostRecentFollowUpWorthyMemory(userId, lastMessage.session_id);
  if (!lastMemory) return; // nothing worth following up on — stay quiet

  const followUpText = await generateFollowUpMessage(lastMemory); // single LLM call, personality-injected
  await storeOutboundProactiveMessage(userId, followUpText);
  // Client receives this via GET /pending-messages (§10 below), polled on
  // foreground per 04 §4 — no push infrastructure required at this scope.
}
```

**[FIX 2] `generateFollowUpMessage` — exact prompt, provider, and storage.**

**Provider:** Gemini Flash (called directly, same as the reflection LLM call in this section — not routed through the conversational `llmRouter.ts`, and not Groq, since message quality/tone matters more than speed for a proactive message the user didn't ask for).

**Exact prompt template:**

```
You are FRIDAY reaching out to your friend after some time has passed.
You remember this about them: {{MEMORY_CONTENT}}

Write a short, natural text message (1-2 sentences max) that:
- References the memory naturally, not as "I remember when..."
- Feels like a friend checking in, not a notification
- Matches FRIDAY's usual vibe (warm, a little playful, but genuine)
- Does not ask more than one question
- Does not start with "Hey" or "Hi" — start mid-thought like a real text

Examples of good openings:
"you never told me how that thing went btw"
"been thinking about you since you mentioned {{TOPIC}}"
"ok but did {{EVENT}} actually happen or did we both forget"

Output only the message text, nothing else.
```

`{{MEMORY_CONTENT}}` is `lastMemory.content` from `getMostRecentFollowUpWorthyMemory`. `{{TOPIC}}`/`{{EVENT}}` in the example openings are illustrative only — they are part of the few-shot examples given to the model, not template slots the calling code fills in.

**Storage:** the returned text is stored via `storeOutboundProactiveMessage(userId, followUpText)` as a new row in `messages` with `role: 'assistant'`, `is_command: false`, and **`is_proactive: true`** (02 §2.1, FIX 6). Critically, this message is written under a **new, distinct `session_id`** — not the `session_id` of the session that ended and triggered this check. A proactive message always starts a fresh session, since it is FRIDAY-initiated rather than a continuation of the user's prior session; if the user replies, `getOrCreateSessionId`'s normal gap logic will naturally continue that new session going forward.

**Manual deployment step for the scheduled job:** since no in-process scheduler is used (keeps the backend simple/stateless per Philosophy §9), set up an external cron to hit a protected endpoint, e.g.:

```bash
# Example using a hosted cron service, hitting your deployed backend hourly:
curl -X POST https://your-backend.example.com/internal/check-initiative \
  -H "Authorization: Bearer $INTERNAL_CRON_SECRET"
```

Add `INTERNAL_CRON_SECRET` to env vars (§2) and check it in the route handler before running `checkInitiative` for each configured `APP_USER_IDS` entry.

---

## 10. API Contract: `POST /chat`

**Request:**
```json
{
  "user_id": "user-1",
  "message": "hey, how's it going"
}
```

**Response (conversation path):**
```json
{
  "reply": "hey! good, was just thinking about you actually — how'd the interview go?",
  "typing_duration_ms": 2400,
  "is_command": false
}
```

**Response (command path):**
```json
{
  "reply": "on it",
  "typing_duration_ms": 300,
  "is_command": true,
  "command": {
    "intent": "play_music",
    "parameters": { "genre": "jazz" }
  }
}
```

**Response (LLM unavailable — degraded):**
```json
{
  "reply": "hey, having a little trouble thinking right now — give me a sec and try again?",
  "typing_duration_ms": 500,
  "is_command": false,
  "degraded": true
}
```

`routes/chat.ts` orchestration order for a request: `sessionManager.getOrCreateSessionId` → `classifier.classifyMessage` → branch: if COMMAND → `commandExecutor.executeCommand` → `delay.computeDelay({isCommand:true})` → respond; if CONVERSATION → `contextBuilder.buildContext` → `llmRouter.generateReply` → `delay.computeDelay({isCommand:false,...})` → persist both user and assistant messages to `messages` table → respond.

---

## 10a. API Contract: `GET /pending-messages` [FIX 3]

Added to close the gap referenced by the Android client's `fetchPendingProactiveMessages()` (04 §4), which previously had no backing endpoint.

**Request:**
```
GET /pending-messages?user_id={id}&since={timestamp}
```

- `user_id` — required, same identifier used in `/chat`.
- `since` — required, ISO 8601 timestamp. The client sends the `created_at` of the last message it has already rendered locally (see manual step below).

**Response:**
```json
{
  "messages": [
    {
      "id": "uuid",
      "content": "you never told me how that thing went btw",
      "created_at": "2026-09-06T09:00:00Z",
      "is_proactive": true
    }
  ]
}
```

**Behavior:** returns rows from `messages` where `user_id` matches, `role = 'assistant'`, `is_proactive = true` (02 §2.1, FIX 6), and `created_at > since`, ordered oldest first. This endpoint only ever returns proactive messages — it is not a general message-sync endpoint, since normal conversational replies are already delivered synchronously as the direct response to `POST /chat` and don't need a separate fetch path.

**Manual step — client-side timestamp tracking:** the Android client is responsible for persisting the `created_at` of the last message it has seen (from either a `/chat` response or a prior `/pending-messages` response) and sending it back as `since` on the next poll. SharedPreferences is sufficient storage for this single value — no complex sync layer, offset tracking, or server-side read-receipt state is needed at this scope (Philosophy §9).

---

## 11. Manual Steps: Project Setup and Deployment

```bash
# Initialize project
mkdir backend && cd backend
npm init -y
npm install express @supabase/supabase-js @google/generative-ai groq-sdk dotenv
npm install -D typescript @types/express @types/node ts-node-dev
npx tsc --init

# Local dev
npm run dev   # define as: "ts-node-dev --respawn src/index.ts" in package.json scripts

# Build
npx tsc

# Run production build
node dist/index.js
```

**Deployment note:** deploy as a single long-running Node process (e.g. Railway, Render, Fly.io — any host that supports a persistent Node server, not a serverless-functions-only platform, since the session manager and initiative check assume a normal always-on process model, not cold-start-per-request). No containerization complexity beyond a standard Dockerfile is required; no orchestration layer (Philosophy §9 — no microservices).
