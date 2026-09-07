# WP-7: Friday AI & Realtime Polish

## 1. Objective
Finalize the AI integration with the Groq API and polish the real-time interaction features like typing broadcasts and message receipts.

## 2. Entry Criteria
- [x] WP-6 ✅ Complete (Integration)
- [x] Docs read:
    - `docs/chatroom/chatroom_architecture.md` (Section 6)
    - `docs/chatroom/chatroom_implementation.md` (WP-7 tasks)
- [x] Existing files to inspect:
    - `app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt` (Stubbed)
    - `app/src/main/java/com/baroness/app/repository/ChatRepository.kt` (Sync logic)

## 3. Scope

### 3.1 Tasks
- [x] **Implement GroqApiService.kt** — Handle Ktor network requests to the Groq Chat Completions API.
- [x] **Define GroqModels.kt** — Serializable request/response classes for AI payloads.
- [x] **Connect FridayChatViewModel** — Replace stubs with actual Groq API calls and handle the AI response flow.
- [x] **Implement Human Typing Broadcast** — Use Supabase Realtime to broadcast typing events.
- [x] **Refine Read Receipts** — Broadcast `read_receipt` event on room entry.
- [x] **Simulated Friday Typing** — Natural delay before responding.

### 3.2 Files to Create
| File | Path | Responsibility |
|------|------|---------------|
| `GroqApiService.kt` | `app/src/main/java/com/baroness/app/data/remote/groq/GroqApiService.kt` | Network client for Groq AI. |
| `GroqModels.kt` | `app/src/main/java/com/baroness/app/data/remote/groq/GroqModels.kt` | DTOs for Groq API. |

### 3.3 Files to Modify
| File | Path | Change |
|------|------|--------|
| `FridayChatViewModel.kt` | `app/src/main/java/com/baroness/app/viewmodels/FridayChatViewModel.kt` | Integrate `GroqApiService`. |
| `ChatRepository.kt` | `app/src/main/java/com/baroness/app/repository/ChatRepository.kt` | Add typing broadcast and receipt update methods. |

### 3.4 Files to Delete
| File | Path | Reason |
|------|------|--------|
| None | | |

## 4. Manual Steps for Owner (Phesty)
| Step | Action | Where | Status |
|------|--------|-------|--------|
| 1 | Securely provide `GROQ_API_KEY` for integration testing. | `local.properties` | ⏳ Pending |

## 5. Exit Criteria
- [ ] Sending a message to Friday triggers a "Friday is typing..." indicator.
- [ ] Friday responds with AI-generated text via the Groq API.
- [ ] AI messages are persisted in Room and displayed in the UI.
- [ ] Human participants see "Name is typing..." when the other is active.
- [ ] Message receipts update correctly (Sent/Read).

## 6. Complexity
Large (Estimated Effort: 2 agent passes)

## 7. Risks
- 🚩 **API Rate Limits**: Ensure Groq API calls are handled gracefully if limits are reached.
- 🚩 **Network Latency**: AI responses might take time; UI must remain responsive and informative.
- 🚩 **API Key Exposure**: Ensure the key is never committed to Git.

## 8. Dependencies
- Blocks: WP-8
- Blocked by: WP-6 ✅

## 9. Notes
- For Friday's AI personality, use a system prompt defined in `FridayChatViewModel`.
- Receipts for Friday are simplified (Ref: `ui_ux.md` Section 2).
- Use `Flow` for real-time typing events to ensure reactivity.
