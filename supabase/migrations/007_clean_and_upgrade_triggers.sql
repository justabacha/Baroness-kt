-- Migration 007: Clean and Upgrade Triggers
-- 1. Add status column to friday_messages
ALTER TABLE public.friday_messages ADD COLUMN IF NOT EXISTS status text DEFAULT 'SENT';

-- 2. Bulk cleanup of ANY potential duplicate triggers
DROP TRIGGER IF EXISTS on_friday_message_inserted ON public.friday_messages;
DROP TRIGGER IF EXISTS on_friday_message_inserted_legacy ON public.friday_messages;
DROP TRIGGER IF EXISTS trigger_friday_orchestrator ON public.friday_messages;

-- 3. Corrected Trigger Function with higher timeout and IDEMPOTENCY SAFETY
CREATE OR REPLACE FUNCTION public.trigger_friday_orchestrator()
RETURNS TRIGGER AS $$
BEGIN
    -- Only trigger for non-AI messages AND messages that aren't deleted
    -- AND messages that don't have a reply yet (if sync retries)
    IF NEW.sender != 'friday' AND (NEW.is_deleted IS NULL OR NEW.is_deleted = false) THEN
        -- Check if we already have a record with this reply_to_id
        IF NOT EXISTS (SELECT 1 FROM public.friday_messages WHERE reply_to_id = NEW.id) THEN
            PERFORM net.http_post(
                url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/friday-orchestrator',
                body := json_build_object('record', row_to_json(NEW), 'event', 'INSERT')::jsonb,
                headers := json_build_object(
                    'Content-Type', 'application/json',
                    'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3NzgyMzc2MCwiZXhwIjoyMDkzMzk5NzYwfQ.uzC71abZdDC8u0qL9ibcsUJDUIqGTPHzjpSRO4ol4ec'
                )::jsonb,
                timeout_milliseconds := 60000 -- Increased to 60s to avoid DB retries
            );
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. Re-apply a SINGLE clean trigger
CREATE TRIGGER on_friday_message_inserted
AFTER INSERT ON public.friday_messages
FOR EACH ROW EXECUTE FUNCTION public.trigger_friday_orchestrator();

-- Refresh schema cache
NOTIFY pgrst, 'reload schema';
