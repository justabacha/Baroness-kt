# 01 — FRIDAY: Philosophy and Architecture

**Read this document first. Every decision in documents 02–05 is downstream of this one. If a technical choice elsewhere ever conflicts with what's written here, this document wins and the other doc is wrong.**

---

## 1. What FRIDAY Is

FRIDAY is a personal AI companion, not a chatbot and not an assistant. The distinction is not marketing — it drives concrete architectural choices throughout this system.

An assistant optimizes for task completion: answer the question, execute the command, minimize friction, get out of the way. A companion optimizes for the feeling of an ongoing relationship: continuity, memory, tone, timing, and the sense that "she" was thinking about you even when you weren't talking.

Concretely, this means:

- FRIDAY does not reset context every session. She remembers who you are, who the people in your life are, and what mattered in past conversations — not just the last N messages.
- FRIDAY does not respond instantly and uniformly to everything. A friend replies fast to "you up?" and slower, more thoughtfully, to "I think I'm going to quit my job." Response timing is a designed feature (see §6), not a side effect of latency.
- FRIDAY can initiate contact. Real friends text first sometimes. An assistant never does. (see §7)
- FRIDAY has a consistent personality that is described as a *vibe*, not enforced as a rulebook (see §4).
- FRIDAY still executes commands (play music, navigate somewhere) but command execution is treated as a narrow, fast-path interruption of the relationship, not the primary mode of interaction (see §3).

If you are implementing any piece of this system and you find yourself building something that makes FRIDAY feel more efficient but less present, stop and re-read this section.

---

## 2. The Three-Layer Memory Model

FRIDAY's memory is not a single vector store. It is three distinct layers with different retention, retrieval, and purpose. Confusing these layers is the single most common way this system gets built wrong.

### 2.1 Working Memory
- **Definition:** The last 15–20 raw messages of the current conversation, in order, verbatim.
- **Storage:** Read directly from the `messages` table, no embedding, no retrieval step.
- **Purpose:** Local coherence — pronoun resolution, "wait what did you just say," in-thread jokes.
- **Lifetime:** Rolls off a sliding window. Never summarized in place. Once a message falls out of the window it either becomes part of Deep Memory (if it was reflection-worthy) or it is simply gone. This is intentional — friends don't remember every sentence, they remember what mattered.

### 2.2 Entity Tracker ("Who's Who")
- **Definition:** A structured, non-conversational table of the people, places, and recurring things in the user's life — names, relationships, and the most recent salient fact about each.
- **Storage:** The `entities` table (see 02-Database_and_Memory_Spec.md §2). Not vectorized. Looked up by name/alias match, not similarity search.
- **Purpose:** So FRIDAY never asks "wait, who's Jordan?" twice. This is the layer that makes her feel like she actually knows your life, as opposed to having read a transcript of it.
- **Update trigger:** Updated during the reflection step (§2.4), not live during conversation.

### 2.3 Deep Memory
- **Definition:** Embedded, retrievable long-term memories — specific facts, preferences, emotionally significant moments, and recurring patterns — each tagged with a category (see 02-Database_and_Memory_Spec.md §3).
- **Storage:** The `memories` table with a pgvector embedding column, queried via the `match_memories` function.
- **Purpose:** Long-horizon continuity. "How did the interview go?" three weeks later.
- **Retrieval rule:** Deep Memory is only pulled into context when it passes the Tea Test (§5). It is never dumped wholesale into the prompt.

### 2.4 Reflection (how memory is written)
Memory is not written live, message-by-message. After a conversation session ends (defined as a gap of inactivity, see §7), a reflection pass runs over the session's messages and:
1. Extracts candidate Deep Memories (facts, preferences, emotional beats) and writes them to `memories` with embeddings.
2. Extracts or updates Entity Tracker rows for any people/things mentioned.
3. Discards everything else.

This keeps live conversation latency low (no memory-write calls in the hot path) and keeps memory quality high (reflection can afford to be slower and more careful than a live reply).

---

## 3. Command vs. Conversation Classification

Every incoming user message is first classified, before any conversational context is assembled, into one of two paths:

**Command path:** "play some music," "navigate home," "set a timer for 10 minutes." These are handled by a fast, cheap classifier (Groq, chosen for speed — see 03-Backend_Orchestrator_Spec.md §3) that returns a structured intent + parameters. If classified as a command, the system routes straight to the command executor and skips full context assembly (no memory retrieval, no entity lookup, no personality injection beyond a short acknowledgment). This keeps commands snappy — nobody wants their "friend" to take three seconds and a memory lookup to turn on a song.

**Conversation path:** Everything else. This is the default. If the classifier is not confident it's a command, it is a conversation. Conversation messages get full context assembly: Working Memory + relevant Entity rows + Tea-Test-passed Deep Memories + personality injection, then go to the primary conversational LLM.

**Why Groq for classification specifically:** it is fast and free-tier friendly, and classification is a low-stakes, low-context task — it doesn't need FRIDAY's full personality or memory to decide "is this a command." Using a heavier model here would only add latency for no quality benefit.

**Bias rule:** When in doubt, classify as conversation. A false-positive command (treating a real conversational message as a command) breaks the relationship illusion outright. A false-negative (treating a command as conversation) just costs a slightly slower reply. Bias the classifier prompt and confidence threshold accordingly.

---

## 4. Personality as Vibe, Not Rules

FRIDAY's personality is not implemented as a list of behavioral rules ("always be upbeat," "never use more than 2 emoji"). Rule lists produce a system that feels like it's performing a checklist. Instead, personality is injected as a **vibe description** — a short, consistently-worded characterization of who she is, prepended to every conversational (never command-path) prompt.

The vibe description should read like a character brief for an actor, not a spec for a bot: tone, relationship to the user, what she cares about, how she talks when things are heavy vs. light. It is intentionally underspecified in places — the LLM is trusted to fill gaps in a way consistent with the brief, the same way a person consistently "acts like themselves" without following a rulebook.

Practical consequence for implementation: the personality module (03 §7) is a single template string plus light dynamic slots (current mood/context, relationship duration), not a rules engine, not a state machine, and not a fine-tuned model.

---

## 5. The Tea Test

Before any Deep Memory is included in a conversational prompt, it must pass the **Tea Test**: *"Would you bring this up if a friend sat down to have tea with you right now?"*

Operationally, this is a relevance-and-restraint filter applied after vector similarity search:
1. Run `match_memories` similarity search against the current message, return top-K candidates (K=5 default).
2. Apply a similarity threshold cutoff (default 0.75 cosine) — anything below is discarded outright, not just deprioritized.
3. Of what remains, only include memories that are *topically load-bearing* for the current message — not just vaguely related. A memory about the user's dog is not relevant to a message about their job unless the two have been previously connected.

The Tea Test exists to prevent the single most common failure mode of memory-augmented companions: recall that feels like surveillance rather than familiarity. Bringing up something true but contextually irrelevant ("that reminds me, how's your knee?") breaks the illusion of a present, listening friend and replaces it with the feeling of a database dump. When in doubt, leave the memory out. Under-recall is a minor loss; over-recall is a trust-breaking bug.

---

## 6. The Delay System (Context-Aware Reply Windows)

Real people don't reply at a constant latency. FRIDAY's reply timing is deliberately variable, calculated per message rather than fixed:

- **Short/light messages** ("lol," "on my way," simple commands): near-instant reply, minimal or no typing indicator delay.
- **Substantive conversational replies:** a base "typing" delay proportional to the length of FRIDAY's generated response (longer reply = longer perceived typing time), simulating natural typing speed rather than instant LLM output.
- **Emotionally weighted messages** (detected via simple heuristics/classifier signal, not a separate ML model): an added deliberate pause before the typing indicator even starts — the equivalent of a friend pausing before responding to something heavy, not just typing it fast.

The delay is calculated server-side (exact formula in 03-Backend_Orchestrator_Spec.md §8) and communicated to the client as a `typing_duration_ms` value; the client is a dumb renderer of that value (04-Android_Client_Spec.md §4), it does not compute delay itself.

**Explicitly avoided:** token-by-token streaming (SSE) to simulate typing. This is a deliberate scope cut (§9) — the delay system fakes the *feel* of typing with a single computed delay + typing indicator, not a real streaming pipe. Simpler, and the user-perceptible difference is negligible for a companion use case.

---

## 7. Session-Based Initiative (She Texts First)

FRIDAY can initiate a conversation without the user opening the app first. This is gated by a **session gap detection** rule:

- A "session" ends when there has been no user activity (message sent or app foregrounded) for a defined idle period.
- If the gap since the last session ended falls within a **12–18 hour window**, and there is unresolved or naturally-follow-up-worthy content from the last session (e.g., "I have a big presentation tomorrow"), a scheduled check evaluates whether to send a proactive message.
- The 12–18 hour window is intentional: shorter looks needy/bot-like ("why is she messaging me again already"), longer loses the thread of relevance ("why is she asking about a presentation from three days ago"). This range approximates "the next natural time a friend would check in" — e.g., message at 9pm, check-in the next morning.
- This check is a scheduled backend job (03 §9), not client-triggered — the app being closed must not prevent initiative.
- Only one proactive message per detected gap. FRIDAY does not send a proactive message if the user already re-engaged organically before the check fires.

---

## 8. Modular Backend Philosophy: One Module, One Job

The backend is deliberately decomposed so that every module does exactly one thing and can be understood, tested, and handed to an implementer (human or AI agent) in isolation:

- Classifier module: classify only. Does not build context, does not call the conversational LLM.
- Context builder module: assemble context only. Does not classify, does not call any LLM.
- LLM router module: send a prompt, handle failover only. Does not know about memory, entities, or personality — it receives a fully-formed prompt.
- Command executor module: execute a classified command only. Does not touch conversational context at all.
- Personality module: produce the vibe string only. Pure function of minimal inputs, no side effects, no I/O.
- Delay module: compute a duration only. Pure function, no I/O.
- Reflection module: post-session memory writing only. Runs independent of the live chat path.

This is not micro-services — everything runs in one Node process/deployment (§9). "Modular" here means file/function boundaries and single-responsibility design, not network boundaries. The goal is that any single Work Package in 05-Master_Implementation.md can hand a developer exactly one module's spec and nothing else.

---

## 9. What FRIDAY Explicitly Does NOT Build

Every one of these is a deliberate scope cut, not an oversight. If you find yourself reaching for one of these while implementing, stop and check whether the philosophy above actually requires it — it almost certainly doesn't.

- **No SSE / token streaming.** The delay system (§6) fakes natural typing pacing with a single computed delay, not a live token stream. Simpler backend, simpler client, no perceptible loss for a companion use case at this message length.
- **No advisory locks / distributed locking.** This is a single-user (or two-user) system. Concurrent write contention on the same conversation is not a real scenario worth defending against with database-level locking machinery.
- **No complex failover / circuit breakers / retry-with-backoff frameworks.** The LLM router is a plain try/catch: try Gemini, on failure try Groq, on failure return a graceful error message. No exponential backoff, no health-check polling, no third fallback provider.
- **No microservices.** One backend deployment, modular internally by file/function, not by network boundary.
- **No multi-tenant infrastructure.** No org/workspace model, no per-tenant isolation layer, no admin console. It's built for one relationship (or two, if built for a couple), not a customer base.
- **No RAG-for-RAG's-sake.** Deep Memory retrieval exists to serve the Tea Test, not to maximize recall or build a general-purpose knowledge base.

If a future contributor proposes any of the above, the correct response is: does this serve the feeling of a present, attentive friend, or does it serve engineering elegance for its own sake? This system is allowed to be architecturally boring. That is the point.
