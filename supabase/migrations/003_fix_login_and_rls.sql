-- Migration 003: Fix Login and RLS Policies
-- This migration restores the access_keys table and relaxes RLS policies to work with the current 'anon' key setup.

-- 1. Restore access_keys table
CREATE TABLE IF NOT EXISTS public.access_keys (
    id text PRIMARY KEY,
    secret_key text NOT NULL,
    created_at timestamptz DEFAULT now()
);

-- 2. Insert default keys if they don't exist (Adjust these as needed)
-- Note: Gate requires at least 6 characters
INSERT INTO public.access_keys (id, secret_key)
VALUES
    ('phesty', 'phesty123'),
    ('baroness', 'baroness123')
ON CONFLICT (id) DO NOTHING;

-- 3. Relax RLS policies for Chat tables
-- Since the app uses the 'anon' key and manual persona management instead of Supabase Auth,
-- the previous policies using (auth.jwt() ->> 'persona_id') were blocking all requests.

-- Table: messages
DROP POLICY IF EXISTS "Users can access their own messages" ON public.messages;
CREATE POLICY "Allow public access to messages" ON public.messages
    FOR ALL USING (true) WITH CHECK (true);

-- Table: friday_messages
DROP POLICY IF EXISTS "Users can access their AI messages" ON public.friday_messages;
CREATE POLICY "Allow public access to AI messages" ON public.friday_messages
    FOR ALL USING (true) WITH CHECK (true);

-- Table: chat_sync_pipe
DROP POLICY IF EXISTS "Users can only see their own mailbox" ON public.chat_sync_pipe;
CREATE POLICY "Allow public access to sync pipe" ON public.chat_sync_pipe
    FOR ALL USING (true) WITH CHECK (true);

-- Table: backup_log
DROP POLICY IF EXISTS "Users can manage their own backup log" ON public.backup_log;
CREATE POLICY "Allow public access to backup log" ON public.backup_log
    FOR ALL USING (true) WITH CHECK (true);

-- 4. Ensure access_keys is readable by anon
ALTER TABLE public.access_keys ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Allow public to read access keys" ON public.access_keys;
CREATE POLICY "Allow public to read access keys" ON public.access_keys
    FOR SELECT USING (true);

-- [OK] Login restored and RLS relaxed.
