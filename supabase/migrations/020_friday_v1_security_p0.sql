-- FridayV1 Step 2: Revoke public select on access_keys
drop policy if exists "Allow public select" on access_keys;
-- Leave RLS enabled with no policy: only service-role key can read this table going forward.
