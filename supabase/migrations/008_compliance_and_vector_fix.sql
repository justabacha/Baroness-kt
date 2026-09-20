-- Migration 008: Compliance Columns and Vector Dimension Fix
-- 1. Add missing compliance columns to friday_messages
ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS is_proactive boolean DEFAULT false;
ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS is_command boolean DEFAULT false;

-- 2. Fix vector dimension for text-embedding-004 (Spec 02 §2.3 specifies 768)
-- We need to drop the old column or alter it. Safer to alter if no data, but let's be thorough.
ALTER TABLE public.friday_memories ALTER COLUMN embedding TYPE vector(768);

-- 3. Update Similarity Matching Function to match new dimension
CREATE OR REPLACE FUNCTION public.match_friday_memories(
    query_embedding vector(768),
    user_owner_id text,
    match_threshold double precision,
    match_count integer
)
RETURNS TABLE (
    id bigint,
    memory_text text,
    category text,
    similarity double precision
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        m.id,
        m.memory_text,
        m.category,
        1 - (m.embedding <=> query_embedding) AS similarity
    FROM public.friday_memories m
    WHERE m.owner_id = user_owner_id
      AND m.embedding IS NOT NULL
      AND 1 - (m.embedding <=> query_embedding) > match_threshold
    ORDER BY m.embedding <=> query_embedding
    LIMIT match_count;
END;
$$;

-- Refresh schema cache
NOTIFY pgrst, 'reload schema';
