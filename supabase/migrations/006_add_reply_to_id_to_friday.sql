-- Migration 006: Add reply_to_id to friday_messages
-- This helps prevent double responses by tracking which user message a reply belongs to.

ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS reply_to_id uuid;
CREATE UNIQUE INDEX IF NOT EXISTS idx_friday_messages_reply_to_id ON public.friday_messages(reply_to_id) WHERE reply_to_id IS NOT NULL;

-- Refresh schema cache
NOTIFY pgrst, 'reload schema';
