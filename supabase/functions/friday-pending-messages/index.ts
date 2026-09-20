import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  const url = new URL(req.url);
  const userId = url.searchParams.get("user_id");
  const since = url.searchParams.get("since");

  if (!userId || !since) {
    return new Response("Missing parameters", { status: 400 });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
  );

  try {
    const { data: messages, error } = await supabase
      .from("friday_messages")
      .select("*")
      .eq("owner_id", userId)
      .eq("sender", "friday")
      .gt("created_at", since)
      .order("created_at", { ascending: true });

    if (error) throw error;

    return new Response(JSON.stringify({ messages }), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
});
