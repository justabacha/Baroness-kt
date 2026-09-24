-- Migration 022: Ensure friday_entities_owner_id_name_key constraint exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'friday_entities_owner_id_name_key'
    ) THEN
        ALTER TABLE public.friday_entities ADD CONSTRAINT friday_entities_owner_id_name_key UNIQUE (owner_id, name);
    END IF;
END $$;

NOTIFY pgrst, 'reload schema';
