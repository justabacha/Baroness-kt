-- Migration 001: Initial Cleanup
-- Dropping abandoned legacy tables to start fresh

-- 1. Drop old message tables (abandoned Serial IDs)
DROP TABLE IF EXISTS public.messages CASCADE;
DROP TABLE IF EXISTS public.friday_messages CASCADE;
DROP TABLE IF EXISTS public.reactions CASCADE;

-- 2. Drop unused/test tables
-- DROP TABLE IF EXISTS public.access_keys CASCADE; -- RESTORED: This is used by AuthManager
DROP TABLE IF EXISTS public.agent_test_table CASCADE;

-- 3. Reset migration history to ensure a clean start for the new architecture
-- We delete the history so we can restart from 002 with the real tables.
TRUNCATE TABLE public.migration_history;

-- [OK] Database is now clean.
