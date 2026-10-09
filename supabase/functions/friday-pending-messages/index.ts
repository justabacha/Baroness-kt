// Gateway JWT verification is intentionally disabled in config.toml (verify_jwt = false); authentication is enforced inside this function via _shared/jwt.ts.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { authenticateRequest } from "../_shared/jwt.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  // 1. Authenticate and extract caller persona from verified JWT
  const authResult = await authenticateRequest(req);
  if (authResult instanceof Response) {
    return authResult;
  }

  const { callerPersona } = authResult;

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
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
  );

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
