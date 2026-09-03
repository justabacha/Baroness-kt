# ChatRoom Database Schema Document

## 1. Room Schema (Local - Single Source of Truth)

All chat data resides permanently in the local Room database. Supabase is used strictly as a real-time transport layer and a remote backup repository.

### MessageEntity
- **Table Name**: `messages`
- **Fields**:
    - `id`: `String` (UUID) - **Primary Key**
    - `conversationId`: `String` - Indexed (`friday`, `baroness`, `phesty`)
    - `senderId`: `String` (`user`, `friday`, `baroness_official`, `phesty_official`)
    - `content`: `String` - Message body
    - `timestamp`: `Long` - Local creation time (epoch ms) - Indexed
    - `serverTimestamp`: `Long?` - Populated from the sync pipe metadata upon delivery
    - `status`: `String` - (`PENDING`, `SENT`, `DELIVERED`, `READ`, `FAILED`)
    - `isDeleted`: `Boolean` - Soft delete flag
    - `reactions`: `String` - JSON-encoded map of `emoji -> List of UserIds`

- **Indices**:
    - `index_messages_conversationId` on `conversationId`
    - `index_messages_timestamp` on `timestamp`

### AppDatabase Update
- **Version**: `4` (Destructive migration from `3`)
- **Entities**: Add `MessageEntity::class`
- **DAO**: Add `MessageDao`

---

## 2. Supabase Schema (Remote - Sync Pipe & Backup Store)

Supabase does **NOT** persist messages in database tables. It facilitates delivery and stores historical backups.

### Real-Time Sync Pipe: `sync_queue` (Ephemeral)
For delivery to partners who are offline when a message is sent. 
- **Purpose**: Acts as a "mailbox" that is purged immediately after the recipient consumes the message.
- **Table**: `sync_queue`
- **Columns**:
    - `id`: `uuid` (PRIMARY KEY, DEFAULT gen_random_uuid())
    - `recipient_id`: `text` (NOT NULL) - Persona ID of the receiver
    - `payload`: `jsonb` (NOT NULL) - Full JSON representation of the `MessageEntity`
    - `created_at`: `timestamp with time zone` (DEFAULT now())

### Backup Store: Supabase Storage
- **Bucket**: `chat_backups`
- **Path**: `backups/{persona}_official_backup.json`
- **Format**: Flat JSON array of message objects.

### Backup Tracking: `backup_log`
- **Purpose**: Tracks when each persona last successfully uploaded their Room data to Storage.
- **Table**: `backup_log`
- **Columns**:
    - `persona_id`: `text` (PRIMARY KEY) - e.g., 'baroness_official'
    - `last_backup_at`: `timestamp with time zone` (DEFAULT now())
    - `message_count_at_backup`: `integer`

### Existing Tables (Keep)
- `typing_status`: Real-time broadcast for "is typing" state.
- `profiles`: Participant metadata (display names, avatars).

---

## 3. Data Flow & Sync Strategy

### Real-Time Delivery (Partner Online)
1. User writes `MessageEntity` to Room (status: `PENDING`).
2. App broadcasts the payload via Supabase Realtime Channel.
3. Partner receives broadcast -> Writes to their Room -> Sends "DELIVERED" acknowledgement.
4. Sender receives ack -> Updates Room status to `SENT`.

### Ephemeral Delivery (Partner Offline)
1. User writes `MessageEntity` to Room (status: `PENDING`).
2. App attempts broadcast (fails/no ack) -> Inserts payload into Supabase `sync_queue` table.
3. Partner comes online -> Polls/Subscribes to `sync_queue` -> Downloads payload -> Writes to Room.
4. Partner deletes consumed records from `sync_queue`.

### Backup Flow
1. App detects background state or trigger condition (>50 new messages).
2. App queries Room for all non-deleted messages.
3. App serializes to JSON and uploads to `chat_backups` Storage bucket (overwrite).
4. App updates `backup_log` with the current timestamp and count.

---

## 4. SQL / DDL for Supabase

```sql
-- EPHEMERAL SYNC PIPE
CREATE TABLE public.chat_sync_pipe (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    recipient_id text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT chat_sync_pipe_pkey PRIMARY KEY (id)
);

-- BACKUP LOG
CREATE TABLE public.backup_log (
    persona_id text NOT NULL,
    last_backup_at timestamp with time zone DEFAULT now(),
    message_count_at_backup integer DEFAULT 0,
    CONSTRAINT backup_log_pkey PRIMARY KEY (persona_id)
);

-- RLS for Sync Pipe
ALTER TABLE public.chat_sync_pipe ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can only see and delete their own mail" ON public.chat_sync_pipe
    FOR ALL USING (recipient_id = auth.uid());
```

---

## 5. Implementation Checklist

| Item | Status | Notes |
| :--- | :--- | :--- |
| `MessageEntity.kt` | Needs Building | Local Room storage |
| `MessageDao.kt` | Needs Building | Local lookups |
| `AppDatabase.kt` | Needs Update | Bump version and add entity |
| Supabase `chat_sync_pipe` | Needs Building | Ephemeral transport |
| Supabase `backup_log` | Needs Building | Tracking for Storage backups |
| Supabase Storage Bucket | Needs Creation | `chat_backups` |
