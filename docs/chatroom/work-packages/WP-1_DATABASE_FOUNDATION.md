# WP-1: Database Foundation

## 1. Objective
Establish local Room database entities, DAOs, domain models, and AppDatabase configuration for the ChatRoom feature.

## 2. Entry Criteria
- [x] Previous WPs: None — this is WP-1
- [x] Docs read: 
    - `docs/chatroom/chatroom_architecture.md`
    - `docs/chatroom/chatroom_database.md`
    - `docs/chatroom/chatroom_file_structure.md`
    - `docs/chatroom/chatroom_implementation.md`
- [x] Existing files to inspect: 
    - `app/src/main/java/com/baroness/app/data/local/database/AppDatabase.kt`
    - `app/src/main/java/com/baroness/app/data/local/database/WishEntity.kt` (for style)

## 3. Scope

### 3.1 Tasks
- **Define MessageEntity** — Define Room entity with UUID PK, `editedAt`, JSON reactions, and explicit indices for `conversationId` and `timestamp`. (Ref: `database.md` Section 1)
- **Implement MessageDao** — Implement queries for `Flow<List<MessageEntity>>` filtered by `conversationId`, lookups for `PENDING` sync status messages, and standard CRUD. (Ref: `database.md` Section 1)
- **Update AppDatabase** — Add `MessageEntity`, `MessageDao`, and implement `MIGRATION_3_4`. (Ref: `database.md` Section 1)
- **Create Domain Models** — Create `Message.kt`, `Participant.kt`, and `ChatRoomUiState.kt`. (Ref: `architecture.md` Section 4)

### 3.2 Files to Create
| File | Path | Responsibility |
| :--- | :--- | :--- |
| `MessageEntity.kt` | `app/src/main/java/com/baroness/app/data/local/database/MessageEntity.kt` | Room entity for persistent message storage. |
| `MessageDao.kt` | `app/src/main/java/com/baroness/app/data/local/dao/MessageDao.kt` | Room DAO for message operations. |
| `Message.kt` | `app/src/main/java/com/baroness/app/models/Message.kt` | UI-layer domain model for messages. |
| `Participant.kt` | `app/src/main/java/com/baroness/app/models/Participant.kt` | Data model for chat participants (Human/Friday). |
| `ChatRoomUiState.kt` | `app/src/main/java/com/baroness/app/models/ChatRoomUiState.kt` | Sealed class for ChatRoom UI states. |

### 3.3 Files to Modify
| File | Path | Change |
| :--- | :--- | :--- |
| `AppDatabase.kt` | `app/src/main/java/com/baroness/app/data/local/database/AppDatabase.kt` | Register `MessageEntity`, add `MessageDao`, implement `MIGRATION_3_4`, and ensure `addMigrations(MIGRATION_3_4)` is called in the builder to override destructive fallback. |

### 3.4 Files to Delete
| File | Path | Responsibility |
| :--- | :--- | :--- |
| None | | |

## 4. Manual Steps for Owner
| Step | Action | Where | Status |
| :--- | :--- | :--- | :--- |
| 1 | Run `002` migration script in Supabase SQL Editor | Supabase Dashboard | [ ] |

## 5. Exit Criteria
- [x] `MessageEntity` compiles and matches the schema in `chatroom_database.md`.
- [x] `MessageDao` provides methods for `Flow<List<MessageEntity>>` by `conversationId`.
- [x] `AppDatabase` version incremented to `4`.
- [x] `MIGRATION_3_4` correctly creates the `messages` table and indices.
- [x] Domain models are decoupled from Room annotations.

## 6. Complexity
Small (Estimated Effort: 1 agent pass)

## 7. Risks
- 🚩 **Room Migration**: Failure to implement `MIGRATION_3_4` will cause data loss due to `fallbackToDestructiveMigration()` in `AppDatabase.kt`.
- 🚩 **JSON Serialization**: `reactions` field in `MessageEntity` needs to be correctly handled (likely via a TypeConverter if not using a String directly).

## 8. Dependencies
- Blocks: WP-2
- Blocked by: None

## 9. Notes
Supabase side (table creation, RLS, realtime) is ALREADY COMPLETE. This WP covers ONLY Kotlin/Room side.
The `reactions` in `MessageEntity` should be stored as a JSON String to keep the schema simple, following `chatroom_database.md`.
