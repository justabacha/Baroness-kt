-- Migration 011: Fix Friday Worker Cron Syntax
-- The previous version caused a "syntax error at or near PERFORM" because it wasn't wrapped in a DO block.

-- 1. Unschedule old job
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'friday-worker') THEN
        PERFORM cron.unschedule('friday-worker');
    END IF;
END $$;

-- 2. Register the CORRECTED schedule with proper PL/pgSQL wrapping
SELECT cron.schedule(
    'friday-worker',
    '*/10 * * * *',
    $$
    DO $block$
    BEGIN
        -- Secured Reflection Secretary
        PERFORM net.http_post(
            url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/friday-reflection',
            headers := '{"Content-Type": "application/json", "X-Internal-Secret": "mr.nice_guy"}'::jsonb,
            timeout_milliseconds := 60000
        );
        -- Secured Proactive Pulse
        PERFORM net.http_post(
            url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/friday-initiative',
            headers := '{"Content-Type": "application/json", "X-Internal-Secret": "mr.nice_guy"}'::jsonb,
            timeout_milliseconds := 60000
        );
    END;
    $block$;
    $$
);
