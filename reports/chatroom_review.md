# Tech Lead Review: ChatRoom Specification & Architecture

**Document Reviewed:** `docs/chatroom/*` (`chatroom_architecture.md`, `chatroom_database.md`, `chatroom_file_structure.md`, `chatroom_ui_ux.md`, `chatroom_implementation.md`, `WP_Design_Template.md`, `Execution_Report_Template.md`)  
**Reviewer:** Lead Technical Developer  
**Status:** ⚠️ **NEEDS REVISION (Conditional Pass)**

---

## Executive Summary

The overall design of the ChatRoom subsystem demonstrates strong architectural intent:
- Clear offline-first principle with local SQLite/Room as the single source of truth.
- Decoupled ViewModel abstraction separating AI interaction (`FridayChatViewModel`) from human-to-human sync (`HumanChatViewModel`).
- Thoughtful UI/UX specification adhering to the existing Baroness glassmorphic design language.

However, a pinpoint technical audit identified **6 major structural contradictions, edge-case gaps, and security risks** across the documentation. Implementing without resolving these issues will result in runtime crashes, data loss for existing users, authorization failures in Supabase, and broken sync queues.

---

## Detailed Findings, Severity Analysis & Fixes

---

### 1. 🔴 CRITICAL: Database Table Name Inconsistency (`sync_queue` vs `chat_sync_pipe`)

#### **Issue:**
In `chatroom_database.md`:
- Section 2 defines the table as **`sync_queue`** (e.g., *"Table: `sync_queue`"*, *"Inserts payload into Supabase `sync_queue` table"*).
- Section 4 (SQL DDL) creates the table as **`chat_sync_pipe`**:
  ```sql
  CREATE TABLE public.chat_sync_pipe ( ... );
  ```
- Section 5 & `chatroom_implementation.md` refer to `chat_sync_pipe`.

#### **Consequence:**
Developers or agents executing WP-1 and WP-2 will write queries against `sync_queue` in Kotlin while provisioning `chat_sync_pipe` in PostgreSQL (or vice versa), leading to SQL runtime errors (`42P01: relation does not exist`) in Supabase.

#### **Recommended Fix:**
Standardize on a single identifier across all documents: **`chat_sync_pipe`**.
- Update `chatroom_database.md` (Sections 2 and 3) to strictly use `chat_sync_pipe`.

---

### 2. 🔴 CRITICAL: Supabase RLS Policy Incompatibility with Persona/App ID Scheme

#### **Issue:**
The Supabase Row-Level Security policy in `chatroom_database.md` Section 4 is defined as:
```sql
CREATE POLICY "Users can only see and delete their own mail" ON public.chat_sync_pipe
    FOR ALL USING (recipient_id = auth.uid());
```
However, the architecture specifies sender/recipient IDs using custom persona strings (`"baroness"`, `"phesty"`, `"baroness_official"`, `"phesty_official"`), while `auth.uid()` in Supabase returns a standard Auth UUID (e.g. `123e4567-e89b-12d3-a456-426614174000`).

#### **Consequence:**
All `SELECT` and `DELETE` queries executed by the client app against `chat_sync_pipe` will return empty result sets or fail silently due to RLS mismatch (`"baroness_official" != auth.uid()`).

#### **Recommended Fix:**
Choose one of two consistent identity strategies:
1. **Option A (Recommended if using Supabase Auth):** Map `recipient_id` to `uuid` foreign-keying `auth.users.id`, and update the `Participant` model to hold both `persona_name` and `auth_id`.
2. **Option B (Custom Persona Claim):** If using custom JWT claims or public API keys with persona identifiers:
   ```sql
   CREATE POLICY "Users can only see and delete their own mail" ON public.chat_sync_pipe
       FOR ALL USING (recipient_id = (auth.jwt() ->> 'persona_id'));
   ```
   Or allow service-role/authenticated persona checks based on the Baroness profile mappings.

---

### 3. 🔴 CRITICAL: Destructive Database Migration Wiping Wishlist Production Data

#### **Issue:**
In `chatroom_database.md` Section 1 and `chatroom_architecture.md` Section 11:
> *"AppDatabase Update - Version: 4 (Destructive migration from 3) ... fallbackToDestructiveMigration() will WIPE ALL DATA, including existing Wishlist items."*

`AppDatabase` currently houses production entities: `WishEntity`, `ReactionEntity`, `RatingEntity`, and `SyncQueueItem`.

#### **Consequence:**
Pushing version 4 with `fallbackToDestructiveMigration()` permanently deletes all user wishlist items, ratings, and pending sync actions upon app update.

#### **Recommended Fix:**
Provide an explicit, safe Room migration path from `3` to `4` in the documentation:
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `messages` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `conversationId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `serverTimestamp` INTEGER,
                `status` TEXT NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `reactions` TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")
    }
}
```
Update `AppDatabase.kt` to attach `.addMigrations(MIGRATION_3_4)`.

---

### 4. 🟡 MAJOR: Two-Way Message Editing Conflict

#### **Issue:**
In `chatroom_architecture.md` Section 7:
> *"Editing a message sets status back to PENDING, updates the timestamp, and triggers a re-sync."*

Updating `timestamp` on an existing message breaks the inverted chat ordering (`index_messages_timestamp` will re-sort the edited message as if it were brand new at the bottom of the feed). Furthermore, `chatroom_database.md` has no `editedAt` field or edit synchronization mechanism in the sync queue.

#### **Consequence:**
- Messages jump out of chronological conversation flow when edited.
- Partner clients receiving an updated message payload with a modified timestamp will either fail to identify it or duplicate it.

#### **Recommended Fix:**
1. Keep the original `timestamp` (creation time) immutable to preserve message ordering.
2. Add an optional `editedAt: Long? = null` field to `MessageEntity` and `Message.kt`.
3. In `ChatRepository`, handle edits by updating `content`, `editedAt = System.currentTimeMillis()`, and setting `status = PENDING`.

---

### 5. 🟡 MAJOR: Groq API Key Handling & Client Network Architecture

#### **Issue:**
In `chatroom_architecture.md` Section 6 & `chatroom_file_structure.md` Section 5:
- Spec states: *"API Key: Stored as a BuildConfig field for v1"* and lists `io.ktor:ktor-client-okhttp` for networking.
- No client service or repository abstraction is defined for Groq in `chatroom_file_structure.md` (it directly jumps from ViewModel to Groq API).

#### **Consequence:**
- Missing explicit model definitions for Groq's Chat Completion payload (`ChatCompletionRequest`, `MessageDto`, `Choice`).
- If network logic is embedded directly into `FridayChatViewModel`, it violates MVVM/Clean Architecture conventions established in the Baroness repository.

#### **Recommended Fix:**
Add a dedicated client in `app/src/main/java/com/baroness/app/data/remote/groq/`:
- `GroqApiService.kt` (Ktor/OkHttp client handling completions).
- `GroqModels.kt` (Request/Response data classes with `@Serializable`).
- Inject or pass `GroqApiService` into `FridayChatViewModel` via `ChatRoomViewModelFactory`.

---

### 6. 🟡 MODERATE: Friday AI Reactions Inconsistency

#### **Issue:**
In `chatroom_architecture.md` Section 6 vs `chatroom_ui_ux.md` Section 2:
- Architecture doc states: *"Reactions show locally but are NOT saved to Room or Supabase."*
- UI/UX doc states: *"Reactions: Purely visual (ephemeral) decoration."*
- However, if Room is the single source of truth driving the UI Flow (`Flow<List<MessageEntity>>`), a reaction that is not written to Room will disappear immediately upon recomposition or navigating away and back.

#### **Consequence:**
User taps a reaction on a Friday message -> ViewModel cannot persist it in the Room Flow -> Reaction blinks and vanishes.

#### **Recommended Fix:**
Reactions on Friday messages **should be persisted locally in Room** (`MessageEntity.reactions`), but **flagged to skip Supabase transport/sync**. This provides a consistent local experience without unnecessary network traffic.

---

## Action Plan for Document Revisions

| Document | Required Edits |
| :--- | :--- |
| **`chatroom_database.md`** | 1. Replace all occurrences of `sync_queue` with `chat_sync_pipe`.<br>2. Reconcile RLS policy with persona IDs.<br>3. Add `editedAt: Long?` to `MessageEntity`.<br>4. Provide `MIGRATION_3_4` instead of destructive migration. |
| **`chatroom_architecture.md`** | 1. Clarify message edit mechanism (immutable creation `timestamp` + mutable `editedAt`).<br>2. Update Friday reaction rule to allow local Room persistence without remote sync.<br>3. Add `GroqApiService` layer under Data/Remote. |
| **`chatroom_file_structure.md`** | 1. Add `com.baroness.app.data.remote.groq.GroqApiService.kt` and `GroqModels.kt`.<br>2. Mention `MIGRATION_3_4` in `AppDatabase.kt` notes. |
| **`chatroom_implementation.md`** | 1. Update WP-1 tasks to include safe database migration.<br>2. Update WP-7 to include `GroqApiService` setup. |

---

## Conclusion

The architecture is well-conceived and near implementation-ready. Once the above 6 points are reconciled in `docs/chatroom/`, work package execution (starting with WP-1) can proceed with zero risk of schema collisions or data destruction.
