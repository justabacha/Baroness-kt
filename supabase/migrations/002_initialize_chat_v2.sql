-- Migration 002: Initialize Chat (Clean Slate V2)
-- This migration sets up the new UUID-based schema as requested.

-- 1. Create Persistent Human Messages table
CREATE TABLE IF NOT EXISTS public.messages (
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

-- 2. Create Persistent AI Messages table
CREATE TABLE IF NOT EXISTS public.friday_messages (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    owner_id text NOT NULL,
    sender text NOT NULL,
    message text NOT NULL,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT friday_messages_pkey PRIMARY KEY (id)
);

-- 3. Create Ephemeral Sync Pipe (Mailbox)
CREATE TABLE IF NOT EXISTS public.chat_sync_pipe (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    recipient_id text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT chat_sync_pipe_pkey PRIMARY KEY (id)
);

-- 4. Create Backup Log
CREATE TABLE IF NOT EXISTS public.backup_log (
    persona_id text NOT NULL,
    last_backup_at timestamptz DEFAULT now(),
    message_count_at_backup integer DEFAULT 0,
    CONSTRAINT backup_log_pkey PRIMARY KEY (persona_id)
);

-- 5. RLS Policies
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

-- 6. Realtime Configuration
-- Ensure these tables are in the realtime publication
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'messages') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'chat_sync_pipe') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_sync_pipe;
  END IF;
END $$;

-- [OK] Initialization complete.
