-- Migration 003: Add reflected_at to friday_messages
-- Tracks which messages have already been processed by the Reflection engine.

ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS reflected_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_friday_messages_reflected_at ON public.friday_messages(reflected_at) WHERE reflected_at IS NULL;
