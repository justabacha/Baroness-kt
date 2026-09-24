import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

async function signJwt(payload: object, secret: string): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const encodeBase64Url = (buf: Uint8Array) =>
    btoa(String.fromCharCode(...buf))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");

  const enc = new TextEncoder();
  const headerStr = encodeBase64Url(enc.encode(JSON.stringify(header)));
  const payloadStr = encodeBase64Url(enc.encode(JSON.stringify(payload)));
  const data = enc.encode(`${headerStr}.${payloadStr}`);

  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign("HMAC", key, data);
  const signatureStr = encodeBase64Url(new Uint8Array(signature));

  return `${headerStr}.${payloadStr}.${signatureStr}`;
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }

  try {
    const body = await req.json();
    const rawPersona = body.persona || body.personaId || "";
    const passkey = body.passkey || body.passKey || body.inputPass || "";

    if (!rawPersona || !passkey) {
      return new Response(
        JSON.stringify({ error: "Missing persona or passkey" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const personaBase = rawPersona.replace("_official", "").toLowerCase();
    const currentPersonaId = `${personaBase}_official`;

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    // 1. Validate passkey against access_keys (service role bypasses RLS)
    const { data: keyRecord, error: keyError } = await supabase
      .from("access_keys")
      .select("secret_key")
      .eq("id", personaBase)
      .maybeSingle();

    if (keyError || !keyRecord) {
      return new Response(
        JSON.stringify({ error: "Invalid persona or passkey" }),
        { status: 401, headers: { "Content-Type": "application/json" } }
      );
    }

    if (keyRecord.secret_key !== passkey) {
      return new Response(
        JSON.stringify({ error: "Invalid pass key, blud!" }),
        { status: 401, headers: { "Content-Type": "application/json" } }
      );
    }

    // 2. Fetch profile
    const { data: profile } = await supabase
      .from("profiles")
      .select("display_name, avatar_url, persona")
      .eq("id", currentPersonaId)
      .maybeSingle();

    // 3. Mint JWT token containing persona claim
    const jwtSecret = Deno.env.get("SUPABASE_JWT_SECRET") || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const now = Math.floor(Date.now() / 1000);
    const token = await signJwt(
      {
        role: "authenticated",
        persona: currentPersonaId,
        sub: currentPersonaId,
        iat: now,
        exp: now + 60 * 60 * 24 * 365, // 1 year
      },
      jwtSecret
    );

    return new Response(
      JSON.stringify({
        token,
        currentPersonaId,
        userProfile: {
          displayName: profile?.display_name || "",
          avatarUrl: profile?.avatar_url || null,
          persona: profile?.persona || "",
          personaId: currentPersonaId,
        },
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("verify-passkey error:", err);
    return new Response(
      JSON.stringify({ error: err.message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
