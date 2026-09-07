# ChatRoom Database Schema Document

## 1. Room Schema (Local - Single Source of Truth)

All chat data resides permanently in the local Room database. Supabase is used as a synchronization store and transport layer.

### MessageEntity
- **Table Name**: `messages`
- **Fields**:
    - `id`: `String` (UUID) - **Primary Key**
    - `conversationId`: `String` - Indexed (`friday`, `baroness`, `phesty`)
    - `senderId`: `String` (`user`, `friday`, `baroness_official`, `phesty_official`)
    - `content`: `String` - Message body
    - `timestamp`: `Long` - Local creation time (epoch ms) - Indexed. **Immutable** to preserve order.
    - `editedAt`: `Long?` - Nullable, last modified timestamp.
    - `serverTimestamp`: `Long?` - Populated from the Supabase `created_at` upon successful sync.
    - `status`: `String` - (`PENDING`, `SENT`, `DELIVERED`, `READ`, `FAILED`)
    - `isDeleted`: `Boolean` - Soft delete flag
    - `reactions`: `String` - JSON-encoded map of `emoji -> List of UserIds`

- **Indices**:
    - `index_messages_conversationId` on `conversationId`
    - `index_messages_timestamp` on `timestamp`

### AppDatabase Update
- **Version**: `4`
- **Migration Strategy**: Use `MIGRATION_3_4` to prevent data loss for existing production tables (Wishlist, etc.).
- **Entities**: Add `MessageEntity::class`
- **DAO**: Add `MessageDao`

#### Room Migration SQL (3 -> 4)
```sql
CREATE TABLE IF NOT EXISTS `messages` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `conversationId` TEXT NOT NULL,
    `senderId` TEXT NOT NULL,
    `content` TEXT NOT NULL,
    `timestamp` INTEGER NOT NULL,
    `editedAt` INTEGER,
    `serverTimestamp` INTEGER,
    `status` TEXT NOT NULL,
    `isDeleted` INTEGER NOT NULL,
    `reactions` TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`);
CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`);
```

---

## 2. Supabase Schema (Remote - Sync & Backup Store)

We are starting fresh by dropping all abandoned legacy tables and creating a new UUID-based structure.

### Table: `messages` (Human Chat)
Persistent store for human-to-human conversations.
- **Columns**:
    - `id`: `uuid` PRIMARY KEY DEFAULT gen_random_uuid()
    - `conversation_id`: `text` NOT NULL
    - `sender_id`: `text` NOT NULL
    - `receiver_id`: `text` NOT NULL
    - `content`: `text` NOT NULL
    - `created_at`: `timestamptz` DEFAULT now()
    - `read_at`: `timestamptz`
    - `is_deleted`: `boolean` DEFAULT false
    - `reactions`: `jsonb` DEFAULT '{}'::jsonb

### Table: `friday_messages` (AI Chat)
Persistent store for AI conversations.
- **Columns**:
    - `id`: `uuid` PRIMARY KEY DEFAULT gen_random_uuid()
    - `owner_id`: `text` NOT NULL
    - `sender`: `text` NOT NULL
    - `message`: `text` NOT NULL
    - `created_at`: `timestamptz` DEFAULT now()

### Table: `chat_sync_pipe` (Ephemeral Mailbox)
Used for real-time delivery to partners who are offline.
- **Purpose**: Acts as a "mailbox" that is purged immediately after the recipient consumes the message.
- **Columns**:
    - `id`: `uuid` PRIMARY KEY DEFAULT gen_random_uuid()
    - `recipient_id`: `text` NOT NULL
    - `payload`: `jsonb` NOT NULL
    - `created_at`: `timestamptz` DEFAULT now()

### Table: `backup_log`
Tracks the last successful backup per persona.
- **Columns**:
    - `persona_id`: `text` PRIMARY KEY
    - `last_backup_at`: `timestamptz` DEFAULT now()
    - `message_count_at_backup`: `integer` DEFAULT 0

---

## 3. Data Flow & Timestamp Mapping

| Room Field (`MessageEntity`) | Supabase Column (`messages`) | Supabase Column (`friday_messages`) |
| :--- | :--- | :--- |
| `id` | `id` (UUID) | `id` (UUID) |
| `conversationId` | `conversation_id` | (Implicit: 'friday') |
| `senderId` | `sender_id` | `sender` |
| `content` | `content` | `message` |
| **`serverTimestamp`** | **`created_at`** | **`created_at`** |
| `status` | (Computed from `read_at`) | (Always `SENT`) |
| `reactions` | `reactions` (JSONB) | (Local-only persistence; skip sync) |

---

## 4. SQL / DDL for Supabase (Clean Slate)

> ⚠️ **DESTRUCTIVE MIGRATION**: This script drops existing chat data.

```sql
-- 1. DROP ABANDONED TABLES
DROP TABLE IF EXISTS public.messages CASCADE;
DROP TABLE IF EXISTS public.friday_messages CASCADE;
DROP TABLE IF EXISTS public.reactions CASCADE;
DROP TABLE IF EXISTS public.access_keys CASCADE;

-- 2. CREATE NEW TABLES
CREATE TABLE public.messages (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    conversation_id text NOT NULL,
    sender_id text NOT NULL,
    receiver_id text NOT NULL,
    content text NOT NULL,
    created_at timestamptz DEFAULT now(),
    read_at timestamptz,
    is_deleted boolean DEFAULT false,
    reactions jsonb DEFAULT '{}'::jsonb,
    CONSTRAINT messages_pkey PRIMARY KEY (id)
);

CREATE TABLE public.friday_messages (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    owner_id text NOT NULL,
    sender text NOT NULL,
    message text NOT NULL,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT friday_messages_pkey PRIMARY KEY (id)
);

CREATE TABLE public.chat_sync_pipe (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    recipient_id text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT chat_sync_pipe_pkey PRIMARY KEY (id)
);

CREATE TABLE public.backup_log (
    persona_id text NOT NULL,
    last_backup_at timestamptz DEFAULT now(),
    message_count_at_backup integer DEFAULT 0,
    CONSTRAINT backup_log_pkey PRIMARY KEY (persona_id)
);

-- 3. RLS POLICIES (Persona/JWT Claim Based)
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can access their own messages" ON public.messages
    FOR ALL USING (sender_id = (auth.jwt() ->> 'persona_id') OR receiver_id = (auth.jwt() ->> 'persona_id'));

ALTER TABLE public.friday_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can access their AI messages" ON public.friday_messages
    FOR ALL USING (owner_id = (auth.jwt() ->> 'persona_id'));

ALTER TABLE public.chat_sync_pipe ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can only see their own mailbox" ON public.chat_sync_pipe
    FOR ALL USING (recipient_id = (auth.jwt() ->> 'persona_id'));

ALTER TABLE public.backup_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own backup log" ON public.backup_log
    FOR ALL USING (persona_id = (auth.jwt() ->> 'persona_id'));

-- 4. ENABLE REALTIME
ALTER PUBLICATION supabase_realtime ADD TABLE messages;
ALTER PUBLICATION supabase_realtime ADD TABLE chat_sync_pipe;
```

---

## 5. Implementation Checklist

| Item | Status | Notes |
| :--- | :--- | :--- |
| `MessageEntity.kt` | Needs Building | Local Room storage with `editedAt` |
| `MessageDao.kt` | Needs Building | Local lookups |
| `AppDatabase.kt` | Needs Update | Add `MIGRATION_3_4` |
| Supabase Setup | **DONE (CLEANED)** | Legacy tables dropped. |
| Supabase `messages` | Needs Create | Run 002 migration |
| Supabase `chat_sync_pipe` | Needs Create | Run 002 migration |
