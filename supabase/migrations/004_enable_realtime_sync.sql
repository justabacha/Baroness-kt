-- Migration 004: Enable Realtime Broadcast for AI Sync
-- Standard Postgres compatible syntax for Publication management

-- 1. Refresh publication by dropping and recreating the relevant table links
-- Note: 'IF EXISTS' is not supported inside ALTER PUBLICATION DROP in some PG versions,
-- so we use a safe DO block.

DO $$
BEGIN
    -- Check and add chat_sync_pipe
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'chat_sync_pipe') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_sync_pipe;
    END IF;

    -- Check and add friday_messages
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'friday_messages') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.friday_messages;
    END IF;

    -- Check and add messages
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'messages') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;
END $$;

-- 2. Force schema cache refresh
NOTIFY pgrst, 'reload schema';
