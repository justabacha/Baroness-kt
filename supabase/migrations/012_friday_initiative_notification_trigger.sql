-- Migration 012: Notification Trigger for Friday Proactive Messages
-- This ensures that when Friday initiates contact via the background job,
-- a push notification is sent to the user's phone.

CREATE OR REPLACE FUNCTION public.trigger_friday_proactive_notification()
RETURNS TRIGGER AS $$
DECLARE
    user_fcm_token text;
    sender_name text := 'FRIDAY';
BEGIN
    -- Only fire for proactive messages sent by Friday
    IF NEW.sender = 'friday' AND NEW.is_proactive = true THEN

        -- 1. Get the recipient's FCM token from the profiles table
        SELECT fcm_token INTO user_fcm_token
        FROM public.profiles
        WHERE id = NEW.owner_id;

        -- 2. If token exists, send the notification via the existing notify-trigger function
        IF user_fcm_token IS NOT NULL THEN
             PERFORM net.http_post(
                url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/notify-trigger',
                body := json_build_object(
                    'event', 'INSERT',
                    'record', json_build_object(
                        'creator_id', 'friday_official', -- Mock sender ID for the trigger
                        'text', NEW.message,
                        'owner_id', NEW.owner_id
                    )
                )::jsonb,
                headers := json_build_object(
                    'Content-Type', 'application/json',
                    'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3NzgyMzc2MCwiZXhwIjoyMDkzMzk5NzYwfQ.uzC71abZdDC8u0qL9ibcsUJDUIqGTPHzjpSRO4ol4ec'
                )::jsonb,
                timeout_milliseconds := 30000
            );
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Re-apply trigger
DROP TRIGGER IF EXISTS on_friday_proactive_message ON public.friday_messages;
CREATE TRIGGER on_friday_proactive_message
AFTER INSERT ON public.friday_messages
FOR EACH ROW EXECUTE FUNCTION public.trigger_friday_proactive_notification();
