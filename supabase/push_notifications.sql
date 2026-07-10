-- PUSH NOTIFICATION TRIGGER SETUP
-- This SQL script sets up the database trigger to invoke the 'notify-trigger' Edge Function
-- whenever a new wish is added to the 'wishlist_items' table.

-- 1. Enable the pg_net extension (for asynchronous HTTP requests)
CREATE EXTENSION IF NOT EXISTS pg_net;

-- 2. Create the trigger function
CREATE OR REPLACE FUNCTION public.handle_new_wish_push_notification()
RETURNS TRIGGER AS $$
BEGIN
  -- We use pg_net.http_post to call our Edge Function asynchronously.
  -- Replace [PROJECT_REF] with your actual Supabase project reference if not already set.
  -- We pass the new record data to the function.
  PERFORM
    net.http_post(
      url := 'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/notify-trigger',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        -- Use your SERVICE_ROLE_KEY here for secure internal calls
        'Authorization', 'Bearer ' || 'YOUR_SERVICE_ROLE_KEY'
      ),
      body := jsonb_build_object(
        'event', 'INSERT',
        'record', row_to_json(NEW)
      )
    );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Attach the trigger to the wishlist_items table
DROP TRIGGER IF EXISTS on_wish_inserted_push ON public.wishlist_items;
CREATE TRIGGER on_wish_inserted_push
  AFTER INSERT ON public.wishlist_items
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_wish_push_notification();

-- NOTE:
-- 1. Replace 'YOUR_SERVICE_ROLE_KEY' with your actual service_role key from Supabase Dashboard.
-- 2. Make sure the 'notify-trigger' Edge Function is deployed.
-- 3. Ensure FCM secrets (FCM_PROJECT_ID, FCM_CLIENT_EMAIL, FCM_PRIVATE_KEY) are set in Supabase.
