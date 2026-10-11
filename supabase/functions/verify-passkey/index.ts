import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const personaAccounts: Record<string, { email: string; passwordEnv: string }> = {
  phesty: {
    email: "phesty@baroness.invalid",
    passwordEnv: "AUTH_PASSWORD_PHESTY",
  },
  baroness: {
    email: "baroness@baroness.invalid",
    passwordEnv: "AUTH_PASSWORD_BARONESS",
  },
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body = await req.json();
    const rawPersona = body.persona || body.personaId || "";
    const passkey = body.passkey || body.passKey || body.inputPass || "";

    if (typeof rawPersona !== "string" || typeof passkey !== "string" || !rawPersona || !passkey) {
      return new Response(
        JSON.stringify({ error: "Missing persona or passkey" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const personaBase = rawPersona.replace("_official", "").toLowerCase();
    const currentPersonaId = `${personaBase}_official`;
    const authAccount = personaAccounts[personaBase];
    if (!authAccount) {
      return new Response(
        JSON.stringify({ error: "Invalid persona or passkey" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceRoleKey) {
      console.error("verify-passkey server configuration is incomplete");
      return new Response(
        JSON.stringify({ error: "Authentication service configuration error" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const { data: keyRecord, error: keyError } = await supabase
      .from("access_keys")
      .select("secret_key")
      .eq("id", personaBase)
      .maybeSingle();

    if (keyError || !keyRecord) {
      return new Response(
        JSON.stringify({ error: "Invalid persona or passkey" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (keyRecord.secret_key !== passkey) {
      return new Response(
        JSON.stringify({ error: "Invalid pass key, blud!" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const password = Deno.env.get(authAccount.passwordEnv);
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!password || !anonKey) {
      console.error("verify-passkey Auth credentials are not configured");
      return new Response(
        JSON.stringify({ error: "Authentication service configuration error" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const authResponse = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: {
        apikey: anonKey,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email: authAccount.email, password }),
    });

    if (!authResponse.ok) {
      console.error("verify-passkey Auth password grant failed", { status: authResponse.status });
      return new Response(
        JSON.stringify({ error: "Could not create authentication session" }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const session = await authResponse.json();
    if (
      typeof session.access_token !== "string" ||
      typeof session.refresh_token !== "string" ||
      typeof session.expires_in !== "number"
    ) {
      console.error("verify-passkey Auth password grant returned an incomplete session");
      return new Response(
        JSON.stringify({ error: "Could not create authentication session" }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { data: profile } = await supabase
      .from("profiles")
      .select("display_name, avatar_url, persona")
      .eq("id", currentPersonaId)
      .maybeSingle();

    return new Response(
      JSON.stringify({
        token: session.access_token,
        refresh_token: session.refresh_token,
        expires_in: session.expires_in,
        currentPersonaId,
        userProfile: {
          displayName: profile?.display_name || "",
          avatarUrl: profile?.avatar_url || null,
          persona: profile?.persona || "",
          personaId: currentPersonaId,
        },
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("verify-passkey error:", err);
    return new Response(
      JSON.stringify({ error: "Authentication request failed" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
