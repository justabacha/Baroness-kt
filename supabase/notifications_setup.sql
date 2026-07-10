-- 1. Enable pg_net extension if not enabled
CREATE EXTENSION IF NOT EXISTS pg_net;

-- 2. Create the notification function
CREATE OR REPLACE FUNCTION public.handle_new_wish_notification()
RETURNS TRIGGER AS $$
BEGIN
  -- Call the Supabase Edge Function
  -- We pass the event type and the record data
  -- Replace [REFERENCE_ID] with your actual Supabase project reference if needed
  -- or use the internal URL if within the same project
  PERFORM
    net.http_post(
      url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/notify-trigger',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'Authorization', 'Bearer ' || current_setting('request.jwt.claims', true)::jsonb->>'api_key' -- Note: In background triggers, you might need a fixed key or use service_role
      ),
      body := jsonb_build_object(
        'event', 'INSERT',
        'record', row_to_json(NEW)
      )
    );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Create the trigger on wishlist_items
DROP TRIGGER IF EXISTS on_wish_inserted ON public.wishlist_items;
CREATE TRIGGER on_wish_inserted
  AFTER INSERT ON public.wishlist_items
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_wish_notification();

-- NOTE: If the above authorization fails in the background (as there is no request context),
-- use the service_role key directly in the header or use a dedicated secret.
-- Alternatively, if you have supabase_functions enabled, you can use:
-- SELECT extensions.http_post(...)
