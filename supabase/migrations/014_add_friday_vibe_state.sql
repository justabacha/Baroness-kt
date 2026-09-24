-- Migration 014: Friday Vibe State
-- Adds emotional persistence to Friday's personality

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS friday_vibe text DEFAULT 'chilled';
ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS sentiment text;

-- Add comment for context
COMMENT ON COLUMN public.profiles.friday_vibe IS 'Stores Fridays current emotional state regarding this user (e.g., hyped, salty, chilled, concerned)';
