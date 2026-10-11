import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authorization = req.headers.get("Authorization");
  if (!authorization?.toLowerCase().startsWith("bearer ")) {
    return new Response(
      JSON.stringify({ error: "Unauthorized: Missing or invalid Authorization header" }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const token = authorization.slice(7).trim();
  if (!token) {
    return new Response(
      JSON.stringify({ error: "Unauthorized: Empty Bearer token" }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    console.error("friday-pending-messages Auth configuration is incomplete");
    return new Response(
      JSON.stringify({ error: "Server authentication configuration error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey);
  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) {
    return new Response(
      JSON.stringify({ error: "Unauthorized: Invalid Auth session" }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const persona = user.app_metadata?.persona;
  if (typeof persona !== "string" || !persona) {
    return new Response(
      JSON.stringify({ error: "Unauthorized: Token missing persona claim" }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
  const callerPersona = persona;

  // 2. Validate query parameters
  const url = new URL(req.url);
  const requestedUserId = url.searchParams.get("user_id");
  const since = url.searchParams.get("since");

  if (!since) {
    return new Response(
      JSON.stringify({ error: "Missing required parameter: since" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  // 3. Prevent cross-persona message access if user_id is explicitly supplied
  if (requestedUserId && requestedUserId !== callerPersona) {
    return new Response(
      JSON.stringify({ error: "Forbidden: Cannot access messages for another user" }),
      { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  // 4. Query Friday pending messages scoped strictly to verified caller persona
  try {
    const { data: messages, error } = await supabase
      .from("friday_messages")
      .select("*")
      .eq("owner_id", callerPersona)
      .eq("sender", "friday")
      .gt("created_at", since)
      .order("created_at", { ascending: true });

    if (error) {
      console.error("Database query error in friday-pending-messages:", error.message);
      return new Response(
        JSON.stringify({ error: "Database query failed" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(JSON.stringify({ messages }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });
  } catch (err) {
    console.error("Unhandled error in friday-pending-messages:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
