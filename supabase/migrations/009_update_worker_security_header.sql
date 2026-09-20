-- Migration 009: Update Worker Security Header
-- Switching from 'Authorization' to 'X-Internal-Secret' to avoid JWT verification conflicts

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'friday-worker') THEN
        PERFORM cron.unschedule('friday-worker');
    END IF;
END $$;

SELECT cron.schedule(
    'friday-worker',
    '*/10 * * * *',
    $$
    BEGIN
        -- Reflection Secretary
        PERFORM net.http_post(
            url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/friday-reflection',
            headers := '{"Content-Type": "application/json", "X-Internal-Secret": "mr.nice_guy"}'::jsonb,
            timeout_milliseconds := 60000
        );
        -- Proactive Pulse
        PERFORM net.http_post(
            url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/friday-initiative',
            headers := '{"Content-Type": "application/json", "X-Internal-Secret": "mr.nice_guy"}'::jsonb,
            timeout_milliseconds := 60000
        );
    END;
    $$
);
