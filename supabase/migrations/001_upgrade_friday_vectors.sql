-- New Baseline Migration 001: Upgrade Friday Memory Vectors and Entities
-- This script safely updates existing Friday tables and builds the vector search architecture.

-- 1. Enable Vector Extension securely
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Safely Upgrade Existing public.friday_memories Table
-- Adds the necessary vector embedding and tracking dimensions without dropping your table data.
ALTER TABLE public.friday_memories ADD COLUMN IF NOT EXISTS category text CHECK (category IN ('preference', 'emotional_moment', 'life_event', 'recurring_pattern', 'entity_link'));
ALTER TABLE public.friday_memories ADD COLUMN IF NOT EXISTS embedding vector(1536); -- Optimized for text-embedding-3-small or equivalent
ALTER TABLE public.friday_memories ADD COLUMN IF NOT EXISTS session_id uuid;
ALTER TABLE public.friday_memories ADD COLUMN IF NOT EXISTS follow_up_worthy boolean DEFAULT false;

-- 3. Create Entity Tracker Table ("Who's Who")
CREATE TABLE IF NOT EXISTS public.friday_entities (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    owner_id text NOT NULL, -- Ties to currentPersonaId
    name text NOT NULL,
    aliases text[] DEFAULT '{}'::text[],
    relationship text,
    last_known_fact text,
    updated_at timestamptz DEFAULT now(),
    created_at timestamptz DEFAULT now(),
    CONSTRAINT friday_entities_pkey PRIMARY KEY (id)
);

-- Indexing for high-speed identity verification matches
CREATE INDEX IF NOT EXISTS idx_friday_entities_owner_name ON public.friday_entities(owner_id, name);

-- 4. Create Vector Similarity Matching Engine (RPC)
-- This function is executed by your context builder to handle long-term memory lookups.
CREATE OR REPLACE FUNCTION public.match_friday_memories(
    query_embedding vector(1536),
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
