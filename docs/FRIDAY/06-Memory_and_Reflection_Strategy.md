# 06 — Memory and Reflection Strategy

**Status: Strategic Blueprint (On-Hold for UI Completion)**
**Governed by: 01-Philosophy_and_Architecture.md**

---

## 1. The "Memory Secretary" (Reflection Layer)

To achieve high-fidelity companionship without latency during chat, FRIDAY uses an asynchronous reflection pass.

### 1.1 Model Selection: DeepSeek R1
- **Role**: The "Analytical Brain."
- **Logic**: Chosen for its high-reasoning (Chain-of-Thought) capabilities. It excels at identifying non-obvious patterns and subtle emotional cues that standard chat models might miss.
- **Trigger**: Activated after a **30-minute session gap** (user inactivity).

### 1.2 The Distillation Process
DeepSeek R1 reads the raw session transcript and produces a structured JSON output:
1. **Entity Updates**: Spotting new facts about people, places, or items in the user's life.
2. **Memory Extraction**: Identifying "Reflection-Worthy" moments—emotional peaks, preferences, or future plans.

---

## 2. Nailing the Entity Tracker ("Who's Who")

This layer ensures FRIDAY never asks for context she already has.

- **Identity Cards**: Every recurring entity (e.g., Sarah, Mom, Work, The Gym) has a "Card" in the `entities` table.
- **Dynamic Injection**: Before a prompt is sent to the conversational LLM (Gemini/Llama), the system scans the user's message for known entities. 
- **Example**: If the user says "Jordan is late," the system injects: `[Entity Jordan: User's brother, habitually late, works at Acme Corp]`.
- **Result**: FRIDAY can respond: "Again? He really needs to fix that commute from Brooklyn," without being told Jordan is a brother or lives in Brooklyn.

---

## 3. Operationalizing the "Tea Test"

This is the filter that prevents FRIDAY from sounding like a database dump.

### 3.1 Vector Similarity + Relevance Filter
1. **Vector Search**: Fetch top-5 memories similar to the current message.
2. **Relevance Check**: A fast model (Groq/Llama) performs a binary check: 
   - *"User is talking about X. Does memory Y provide emotional support or relevant continuity for X?"*
3. **Execution**: If the memory is just "true" but not "relevant," it is **discarded**.

---

## 4. Proactive Messaging (The Companion Pulse)

Memory isn't just for replying; it's for initiating.

- **Trigger Logic**: The Backend Orchestrator scans the `memories` table for entries with a `future_date` or `follow_up_worthy` flag.
- **Window**: Checks are performed in the **12-18 hour gap** window.
- **Content**: Uses the extracted memory to generate a "thinking of you" text.
- **Example**: 
    - *Memory*: "User has a dentist appointment tomorrow at 10 AM."
    - *Proactive Message*: "Hope the dentist wasn't too brutal today! 🦷 You surviving?"

---

## 5. Implementation Roadmap (Post-UI)

Once the **ChatRoom UI** (WP-4 to WP-8) is pixel-perfect, we will return to this engine:
1. **Module 1**: Supabase Edge Function for session gap detection.
2. **Module 2**: Integration of OpenRouter (DeepSeek R1) for the Reflection Pass.
3. **Module 3**: pgvector setup in Supabase for the "Tea Test" search.
4. **Module 4**: Scheduled CRON job for Proactive Messaging.
