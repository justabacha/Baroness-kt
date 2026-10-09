const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export interface JwtPayload {
  role?: string;
  persona?: string;
  sub?: string;
  iat?: number;
  exp?: number;
  [key: string]: unknown;
}

export function decodeBase64Url(str: string): Uint8Array {
  let base64 = str.replace(/-/g, "+").replace(/_/g, "/");
  while (base64.length % 4 !== 0) {
    base64 += "=";
  }
  const binaryStr = atob(base64);
  const bytes = new Uint8Array(binaryStr.length);
  for (let i = 0; i < binaryStr.length; i++) {
    bytes[i] = binaryStr.charCodeAt(i);
  }
  return bytes;
}

export async function verifyJwt(token: string, secret: string): Promise<JwtPayload> {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid JWT format");
  }

  const [headerB64, payloadB64, sigB64] = parts;

  let header: { alg?: string; typ?: string };
  let payload: JwtPayload;

  try {
    const headerBytes = decodeBase64Url(headerB64);
    const payloadBytes = decodeBase64Url(payloadB64);
    header = JSON.parse(new TextDecoder().decode(headerBytes));
    payload = JSON.parse(new TextDecoder().decode(payloadBytes));
  } catch (_e) {
    throw new Error("Invalid JWT encoding or JSON");
  }

  if (header.alg !== "HS256") {
    throw new Error(`Unsupported JWT algorithm: ${header.alg}`);
  }

  const enc = new TextEncoder();
  const data = enc.encode(`${headerB64}.${payloadB64}`);
  const sigBytes = decodeBase64Url(sigB64);

  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"]
  );

  const isValid = await crypto.subtle.verify(
    "HMAC",
    key,
    sigBytes,
    data
  );

  if (!isValid) {
    throw new Error("Invalid JWT signature");
  }

  const now = Math.floor(Date.now() / 1000);
  if (payload.exp && typeof payload.exp === "number" && payload.exp < now) {
    throw new Error("JWT has expired");
  }

  return payload;
}

export async function authenticateRequest(req: Request): Promise<{ callerPersona: string; payload: JwtPayload } | Response> {
  const authHeader = req.headers.get("Authorization") || req.headers.get("authorization");
  if (!authHeader || !authHeader.toLowerCase().startsWith("bearer ")) {
    return new Response(
      JSON.stringify({ error: "Unauthorized: Missing or invalid Authorization Bearer header" }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const token = authHeader.substring(7).trim();
  if (!token) {
    return new Response(
      JSON.stringify({ error: "Unauthorized: Empty Bearer token" }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const getEnv = (key: string) => {
    if (typeof Deno !== "undefined" && Deno.env) {
      return Deno.env.get(key);
    }
    return process.env[key];
  };

  const secret = getEnv("SUPABASE_JWT_SECRET") || getEnv("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!secret) {
    console.error("No JWT secret or service role key available for verification");
    return new Response(
      JSON.stringify({ error: "Server authentication configuration error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  let payload: JwtPayload | null = null;
  try {
    payload = await verifyJwt(token, secret);
  } catch (err) {
    const errorMsg = (err as Error)?.message || "Invalid token";
    return new Response(
      JSON.stringify({ error: `Unauthorized: ${errorMsg}` }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const callerPersona = (payload.persona || payload.sub) as string | undefined;
  if (!callerPersona || typeof callerPersona !== "string") {
    return new Response(
      JSON.stringify({ error: "Unauthorized: Token missing persona claim" }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  return { callerPersona, payload };
}
