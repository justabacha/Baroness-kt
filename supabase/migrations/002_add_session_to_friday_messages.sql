-- Migration 002: Add session_id to friday_messages
-- This allows the Reflection engine to group conversations into discrete sessions.

ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS session_id uuid;
CREATE INDEX IF NOT EXISTS idx_friday_messages_session_id ON public.friday_messages(session_id);
