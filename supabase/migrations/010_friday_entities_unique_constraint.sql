-- Migration 010: Ensure unique entities per user for clean upserts
-- This allows the AI to update an existing "Identity Card" rather than creating duplicates.

ALTER TABLE public.friday_entities ADD CONSTRAINT friday_entities_owner_id_name_key UNIQUE (owner_id, name);

-- Refresh schema cache
NOTIFY pgrst, 'reload schema';
