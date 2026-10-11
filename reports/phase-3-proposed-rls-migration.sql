-- ============================================================================
-- Phase 3: Strict Persona-Scoped RLS Policies
-- Target Expression for Persona: (auth.jwt() ->> 'persona')
--
-- Tightened: profiles, messages, chat_sync_pipe, friday_messages,
-- friday_memories, wishlist_items, wishlist_reactions, wishlist_ratings,
-- and storage.objects avatar policies.
-- Dropped: public.gallery_items (confirmed empty and unreferenced on 2026-10-10).
-- Left untouched:
--   typing_status, backup_log: preserve existing permissive policies.
--   friday_entities, friday_memory_history, friday_sessions,
--   friday_action_requests, friday_api_usage: preserve current zero-policy
--   deny-all state; client behavior is not verified.
--   migration_history, access_keys: preserve current zero-policy state.
--   phestydrop_releases: preserve existing public SELECT policy.
-- ============================================================================

-- ============================================================================
-- 1. DROP EXISTING PERMISSIVE POLICIES (Using Exact Policy Names from pg_policies)
-- ============================================================================
DROP POLICY IF EXISTS "Allow public access to sync pipe" ON public.chat_sync_pipe;
DROP POLICY IF EXISTS "Allow public access to friday_memories" ON public.friday_memories;
DROP POLICY IF EXISTS "Allow public access to AI messages" ON public.friday_messages;
DROP POLICY IF EXISTS "Allow public access to messages" ON public.messages;
DROP POLICY IF EXISTS "Allow public access to profiles" ON public.profiles;

DROP POLICY IF EXISTS "wishlist_items_select" ON public.wishlist_items;
DROP POLICY IF EXISTS "wishlist_items_insert" ON public.wishlist_items;
DROP POLICY IF EXISTS "wishlist_items_update" ON public.wishlist_items;
DROP POLICY IF EXISTS "wishlist_items_delete" ON public.wishlist_items;

DROP POLICY IF EXISTS "wishlist_reactions_select" ON public.wishlist_reactions;
DROP POLICY IF EXISTS "wishlist_reactions_insert" ON public.wishlist_reactions;
DROP POLICY IF EXISTS "wishlist_reactions_update" ON public.wishlist_reactions;
DROP POLICY IF EXISTS "wishlist_reactions_delete" ON public.wishlist_reactions;

DROP POLICY IF EXISTS "wishlist_ratings_select" ON public.wishlist_ratings;
DROP POLICY IF EXISTS "wishlist_ratings_insert" ON public.wishlist_ratings;
DROP POLICY IF EXISTS "wishlist_ratings_update" ON public.wishlist_ratings;
DROP POLICY IF EXISTS "wishlist_ratings_delete" ON public.wishlist_ratings;

-- Bucket-scoped storage policies for 'avatars' bucket only
DROP POLICY IF EXISTS "Avatar images are publicly accessible" ON storage.objects;
DROP POLICY IF EXISTS "Anyone can upload an avatar" ON storage.objects;
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
DROP POLICY IF EXISTS "Allow Upload" ON storage.objects;
DROP POLICY IF EXISTS "Allow Update" ON storage.objects;
DROP POLICY IF EXISTS "Allow Delete" ON storage.objects;


-- ============================================================================
-- 2. CREATE STRICT RLS POLICIES BY TABLE
-- ============================================================================

-- ----------------------------------------------------------------------------
-- TABLE: profiles
-- Business Rule: Both personas can read both profiles. Each persona can only update their own profile row.
-- ----------------------------------------------------------------------------
CREATE POLICY "profiles_select" ON public.profiles
    FOR SELECT TO authenticated
    USING ((auth.jwt() ->> 'persona') IS NOT NULL);

CREATE POLICY "profiles_update" ON public.profiles
    FOR UPDATE TO authenticated
    USING (id = (auth.jwt() ->> 'persona'))
    WITH CHECK (id = (auth.jwt() ->> 'persona'));


-- ----------------------------------------------------------------------------
-- TABLE: messages (HumanChat) & Message Immutability Trigger
-- Business Rule:
-- - SELECT/DELETE: Restricted to sender or receiver.
-- - INSERT: Sender must be the authenticated caller.
-- - UPDATE: Enforced by trigger (sender may edit content/metadata; receiver may update metadata only: read_at, is_deleted, reactions).
-- ----------------------------------------------------------------------------
CREATE POLICY "messages_select" ON public.messages
    FOR SELECT TO authenticated
    USING (
        sender_id = (auth.jwt() ->> 'persona') OR
        receiver_id = (auth.jwt() ->> 'persona')
    );

CREATE POLICY "messages_insert" ON public.messages
    FOR INSERT TO authenticated
    WITH CHECK (sender_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "messages_update" ON public.messages
    FOR UPDATE TO authenticated
    USING (
        sender_id = (auth.jwt() ->> 'persona') OR
        receiver_id = (auth.jwt() ->> 'persona')
    )
    WITH CHECK (
        sender_id = (auth.jwt() ->> 'persona') OR
        receiver_id = (auth.jwt() ->> 'persona')
    );

CREATE POLICY "messages_delete" ON public.messages
    FOR DELETE TO authenticated
    USING (sender_id = (auth.jwt() ->> 'persona'));

-- Message Immutability Trigger Function (SECURITY INVOKER, uses IS NOT DISTINCT FROM to safely handle NULL created_at)
CREATE OR REPLACE FUNCTION public.enforce_message_immutability()
RETURNS TRIGGER AS $$
DECLARE
    v_persona text;
BEGIN
    v_persona := auth.jwt() ->> 'persona';

    -- Senders own their messages and may edit content or metadata
    IF v_persona = OLD.sender_id THEN
        RETURN NEW;
    END IF;

    -- Recipients may update metadata (read_at, is_deleted, reactions) but NOT immutable identifiers or content
    IF v_persona = OLD.receiver_id THEN
        IF NEW.id IS NOT DISTINCT FROM OLD.id AND
           NEW.conversation_id IS NOT DISTINCT FROM OLD.conversation_id AND
           NEW.sender_id IS NOT DISTINCT FROM OLD.sender_id AND
           NEW.receiver_id IS NOT DISTINCT FROM OLD.receiver_id AND
           NEW.content IS NOT DISTINCT FROM OLD.content AND
           NEW.created_at IS NOT DISTINCT FROM OLD.created_at THEN
            RETURN NEW;
        ELSE
            RAISE EXCEPTION 'Not authorized to update immutable message content or identifiers';
        END IF;
    END IF;

    RAISE EXCEPTION 'Not authorized to update this message';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS enforce_message_immutability_trigger ON public.messages;
CREATE TRIGGER enforce_message_immutability_trigger
    BEFORE UPDATE ON public.messages
    FOR EACH ROW
    EXECUTE FUNCTION public.enforce_message_immutability();


-- ----------------------------------------------------------------------------
-- TABLE: chat_sync_pipe
-- Business Rule: Recipients can read and delete their sync pipe items. Any authenticated persona can push an event.
-- ----------------------------------------------------------------------------
CREATE POLICY "chat_sync_pipe_select" ON public.chat_sync_pipe
    FOR SELECT TO authenticated
    USING (recipient_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "chat_sync_pipe_insert" ON public.chat_sync_pipe
    FOR INSERT TO authenticated
    WITH CHECK ((auth.jwt() ->> 'persona') IS NOT NULL);

CREATE POLICY "chat_sync_pipe_delete" ON public.chat_sync_pipe
    FOR DELETE TO authenticated
    USING (recipient_id = (auth.jwt() ->> 'persona'));


-- ----------------------------------------------------------------------------
-- TABLES: friday_messages, friday_memories
-- Business Rule: Friday AI data is strictly private to the owning persona (owner_id).
-- ----------------------------------------------------------------------------
CREATE POLICY "friday_messages_all" ON public.friday_messages
    FOR ALL TO authenticated
    USING (owner_id = (auth.jwt() ->> 'persona'))
    WITH CHECK (owner_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "friday_memories_all" ON public.friday_memories
    FOR ALL TO authenticated
    USING (owner_id = (auth.jwt() ->> 'persona'))
    WITH CHECK (owner_id = (auth.jwt() ->> 'persona'));

-- ----------------------------------------------------------------------------
-- TABLES: wishlist_items, wishlist_reactions, wishlist_ratings
-- Business Rule: Shared collaboration between paired personas.
-- - Wishlist Items: SELECT/UPDATE/DELETE shared; INSERT binds creator_id to caller.
-- - Reactions / Ratings: SELECT/UPDATE/DELETE shared; INSERT binds persona_id to caller.
-- ----------------------------------------------------------------------------
CREATE POLICY "wishlist_items_select" ON public.wishlist_items
    FOR SELECT TO authenticated
    USING ((auth.jwt() ->> 'persona') IS NOT NULL);

CREATE POLICY "wishlist_items_insert" ON public.wishlist_items
    FOR INSERT TO authenticated
    WITH CHECK (creator_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "wishlist_items_update" ON public.wishlist_items
    FOR UPDATE TO authenticated
    USING ((auth.jwt() ->> 'persona') IS NOT NULL)
    With CHECK ((auth.jwt() ->> 'persona') IS NOT NULL);

CREATE POLICY "wishlist_items_delete" ON public.wishlist_items
    FOR DELETE TO authenticated
    USING ((auth.jwt() ->> 'persona') IS NOT NULL);


CREATE POLICY "wishlist_reactions_select" ON public.wishlist_reactions
    FOR SELECT TO authenticated
    USING ((auth.jwt() ->> 'persona') IS NOT NULL);

CREATE POLICY "wishlist_reactions_insert" ON public.wishlist_reactions
    FOR INSERT TO authenticated
    WITH CHECK (persona_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "wishlist_reactions_update" ON public.wishlist_reactions
    FOR UPDATE TO authenticated
    USING (persona_id = (auth.jwt() ->> 'persona'))
    With CHECK (persona_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "wishlist_reactions_delete" ON public.wishlist_reactions
    FOR DELETE TO authenticated
    USING (persona_id = (auth.jwt() ->> 'persona'));


CREATE POLICY "wishlist_ratings_select" ON public.wishlist_ratings
    FOR SELECT TO authenticated
    USING ((auth.jwt() ->> 'persona') IS NOT NULL);

CREATE POLICY "wishlist_ratings_insert" ON public.wishlist_ratings
    FOR INSERT TO authenticated
    WITH CHECK (persona_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "wishlist_ratings_update" ON public.wishlist_ratings
    FOR UPDATE TO authenticated
    USING (persona_id = (auth.jwt() ->> 'persona'))
    With CHECK (persona_id = (auth.jwt() ->> 'persona'));

CREATE POLICY "wishlist_ratings_delete" ON public.wishlist_ratings
    FOR DELETE TO authenticated
    USING (persona_id = (auth.jwt() ->> 'persona'));


-- ----------------------------------------------------------------------------
-- TABLE: storage.objects (Bucket: avatars)
-- Business Rule: Public read; authenticated personas can upload/update/delete their own avatar files.
-- PREREQUISITE: Client upload path must be updated to use '<persona>/avatar_<timestamp>.jpg'.
-- ----------------------------------------------------------------------------
CREATE POLICY "avatars_select" ON storage.objects
    FOR SELECT TO public
    USING (bucket_id = 'avatars');

CREATE POLICY "avatars_insert" ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'avatars' AND
        (storage.foldername(name))[1] = (auth.jwt() ->> 'persona')
    );

CREATE POLICY "avatars_update" ON storage.objects
    FOR UPDATE TO authenticated
    USING (
        bucket_id = 'avatars' AND
        (storage.foldername(name))[1] = (auth.jwt() ->> 'persona')
    )
    WITH CHECK (
        bucket_id = 'avatars' AND
        (storage.foldername(name))[1] = (auth.jwt() ->> 'persona')
    );

CREATE POLICY "avatars_delete" ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id = 'avatars' AND
        (storage.foldername(name))[1] = (auth.jwt() ->> 'persona')
    );

-- Drop the table confirmed empty and unreferenced on 2026-10-10.
DROP TABLE IF EXISTS public.gallery_items;
