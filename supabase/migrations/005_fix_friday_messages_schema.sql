-- Migration 005: Fix Friday Messages Schema
-- 1. Add missing is_pinned column to friday_messages
-- 2. Add missing is_deleted column to friday_messages (to support the delete logic)

ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS is_pinned boolean DEFAULT false;
ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS is_deleted boolean DEFAULT false;
ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS reactions jsonb DEFAULT '{}'::jsonb;

-- Refresh schema cache
NOTIFY pgrst, 'reload schema';
