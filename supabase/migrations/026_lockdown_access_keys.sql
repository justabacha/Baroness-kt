-- Migration 026: Lock down access_keys table by revoking public select policy
-- AuthManager now authenticates passkeys server-side via the verify-passkey Edge Function.

DROP POLICY IF EXISTS "Allow public select" ON public.access_keys;
DROP POLICY IF EXISTS "Allow public to read access keys" ON public.access_keys;
DROP POLICY IF EXISTS "Allow public select on access_keys" ON public.access_keys;

-- Ensure RLS is enabled and no public SELECT policy exists on access_keys
ALTER TABLE public.access_keys ENABLE ROW LEVEL SECURITY;

-- Refresh PostgREST schema cache
NOTIFY pgrst, 'reload schema';
