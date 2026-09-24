-- Migration 024: App Compatibility RLS Policies
-- Ensures public/anon access for Android app PostgREST requests (Gate, Profiles, Chat, Sync Pipe, Memories)

-- 1. access_keys
DROP POLICY IF EXISTS "Allow public select" ON public.access_keys;
DROP POLICY IF EXISTS "Allow public to read access keys" ON public.access_keys;
DROP POLICY IF EXISTS "Allow public select on access_keys" ON public.access_keys;
CREATE POLICY "Allow public select on access_keys" ON public.access_keys FOR SELECT USING (true);
ALTER TABLE public.access_keys ENABLE ROW LEVEL SECURITY;

-- 2. profiles
DROP POLICY IF EXISTS "Allow public access to profiles" ON public.profiles;
DROP POLICY IF EXISTS "Enable all for current_user" ON public.profiles;
DROP POLICY IF EXISTS "Public profiles are viewable by everyone" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert or update their own profile" ON public.profiles;
DROP POLICY IF EXISTS "Persona authenticated access to profiles" ON public.profiles;

CREATE POLICY "Allow public access to profiles" ON public.profiles FOR ALL USING (true) WITH CHECK (true);

-- 3. friday_messages
DROP POLICY IF EXISTS "Allow public access to AI messages" ON public.friday_messages;
DROP POLICY IF EXISTS "Persona authenticated access to friday_messages" ON public.friday_messages;

CREATE POLICY "Allow public access to AI messages" ON public.friday_messages FOR ALL USING (true) WITH CHECK (true);

-- 4. friday_memories
DROP POLICY IF EXISTS "Allow read memories" ON public.friday_memories;
DROP POLICY IF EXISTS "Allow insert memories" ON public.friday_memories;
DROP POLICY IF EXISTS "Allow update memories" ON public.friday_memories;
DROP POLICY IF EXISTS "Allow delete memories" ON public.friday_memories;
DROP POLICY IF EXISTS "Persona authenticated access to friday_memories" ON public.friday_memories;

CREATE POLICY "Allow public access to friday_memories" ON public.friday_memories FOR ALL USING (true) WITH CHECK (true);

-- 5. chat_sync_pipe
DROP POLICY IF EXISTS "Allow public access to sync pipe" ON public.chat_sync_pipe;
DROP POLICY IF EXISTS "Persona authenticated access to chat_sync_pipe" ON public.chat_sync_pipe;

CREATE POLICY "Allow public access to sync pipe" ON public.chat_sync_pipe FOR ALL USING (true) WITH CHECK (true);

-- Refresh schema cache
NOTIFY pgrst, 'reload schema';
