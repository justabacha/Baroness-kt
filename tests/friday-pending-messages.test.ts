import assert from 'node:assert';
import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import { verifyJwt, authenticateRequest } from '../supabase/functions/_shared/jwt.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const supabaseUrl = "https://wckluymkbqxdmipzaiff.supabase.co";
const anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4MjM3NjAsImV4cCI6MjA5MzM5OTc2MH0.y3murBfcZtgluuPd_uFBut4Ky3Wl8WAHVCp-kA1u9sU";

// Use the runtime secret used by verify-passkey and friday-pending-messages in Supabase Cloud
const edgeSecret = process.env.SUPABASE_SERVICE_ROLE_KEY || "dummy_secret_for_tests";
process.env.SUPABASE_SERVICE_ROLE_KEY = edgeSecret;

function signJwt(payload: object, secret: string, alg = 'HS256'): string {
  const header = { alg, typ: 'JWT' };
  const encodeBase64Url = (buf: Buffer) => buf.toString('base64url');

  const headerStr = encodeBase64Url(Buffer.from(JSON.stringify(header)));
  const payloadStr = encodeBase64Url(Buffer.from(JSON.stringify(payload)));
  const data = `${headerStr}.${payloadStr}`;

  const hmac = crypto.createHmac('sha256', secret);
  hmac.update(data);
  const signatureStr = hmac.digest('base64url');

  return `${data}.${signatureStr}`;
}

console.log("=== Running Friday Pending-Messages Security Unit & Integration Tests ===");

async function getLiveToken(persona: string, passkey: string): Promise<string> {
  const res = await fetch(`${supabaseUrl}/functions/v1/verify-passkey`, {
    method: 'POST',
    headers: {
      "Content-Type": "application/json",
      "apikey": anonKey,
      "Authorization": `Bearer ${anonKey}`
    },
    body: JSON.stringify({ persona, passkey })
  });
  if (!res.ok) {
    throw new Error(`verify-passkey failed with status ${res.status}: ${await res.text()}`);
  }
  const data = await res.json();
  return data.token;
}

async function runTests() {
  const now = Math.floor(Date.now() / 1000);

  // Fixture Tokens
  const localPhestyToken = signJwt({
    role: 'authenticated',
    persona: 'phesty_official',
    sub: 'phesty_official',
    iat: now,
    exp: now + 3600
  }, edgeSecret);

  const expiredToken = signJwt({
    role: 'authenticated',
    persona: 'phesty_official',
    sub: 'phesty_official',
    iat: now - 7200,
    exp: now - 60
  }, edgeSecret);

  const noPersonaToken = signJwt({
    role: 'authenticated',
    iat: now,
    exp: now + 3600
  }, edgeSecret);

  const badSignatureToken = signJwt({
    role: 'authenticated',
    persona: 'phesty_official',
    sub: 'phesty_official',
    iat: now,
    exp: now + 3600
  }, 'wrong-secret');

  console.log("\n--- PART 1: Unit & Logic Verification ---");

  // Test 1: Verify JWT Signature & Claims Parsing
  console.log("1. Testing verifyJwt helper...");
  const parsed = await verifyJwt(localPhestyToken, edgeSecret);
  assert.strictEqual(parsed.persona, 'phesty_official');
  console.log("  ✓ Valid JWT signature and claims successfully verified");

  // Test 2: Expired Token Rejection
  console.log("2. Testing expired token rejection...");
  await assert.rejects(
    async () => await verifyJwt(expiredToken, edgeSecret),
    (err: Error) => err.message.includes("JWT has expired")
  );
  console.log("  ✓ Expired JWT correctly rejected");

  // Test 3: Bad Signature Rejection
  console.log("3. Testing invalid signature rejection...");
  await assert.rejects(
    async () => await verifyJwt(badSignatureToken, edgeSecret),
    (err: Error) => err.message.includes("Invalid JWT signature")
  );
  console.log("  ✓ Bad signature correctly rejected");

  // Test 4: Missing Authorization Header
  console.log("4. Testing Request without Authorization header...");
  const reqNoAuth = new Request(`${supabaseUrl}/functions/v1/friday-pending-messages?since=1970-01-01T00:00:00Z`);
  const resNoAuth = await authenticateRequest(reqNoAuth);
  assert.ok(resNoAuth instanceof Response);
  assert.strictEqual(resNoAuth.status, 401);
  const jsonNoAuth = await resNoAuth.json();
  assert.ok(jsonNoAuth.error.includes("Missing or invalid Authorization Bearer header"));
  console.log("  ✓ Missing Authorization header rejected with status 401");

  // Test 5: Malformed Bearer Header
  console.log("5. Testing Request with malformed Bearer header...");
  const reqBadBearer = new Request(`${supabaseUrl}/functions/v1/friday-pending-messages?since=1970-01-01T00:00:00Z`, {
    headers: { "Authorization": "Basic dXNlcjpwYXNz" }
  });
  const resBadBearer = await authenticateRequest(reqBadBearer);
  assert.ok(resBadBearer instanceof Response);
  assert.strictEqual(resBadBearer.status, 401);
  console.log("  ✓ Malformed Bearer header rejected with status 401");

  // Test 6: Missing Persona Claim
  console.log("6. Testing Request with token missing persona claim...");
  const reqNoPersona = new Request(`${supabaseUrl}/functions/v1/friday-pending-messages?since=1970-01-01T00:00:00Z`, {
    headers: { "Authorization": `Bearer ${noPersonaToken}` }
  });
  const resNoPersona = await authenticateRequest(reqNoPersona);
  assert.ok(resNoPersona instanceof Response);
  assert.strictEqual(resNoPersona.status, 401);
  const jsonNoPersona = await resNoPersona.json();
  assert.ok(jsonNoPersona.error.includes("missing persona claim"));
  console.log("  ✓ Token missing persona claim rejected with status 401");

  // Test 7: Valid Phesty Authentication
  console.log("7. Testing valid Phesty request authentication...");
  const reqPhesty = new Request(`${supabaseUrl}/functions/v1/friday-pending-messages?user_id=phesty_official&since=1970-01-01T00:00:00Z`, {
    headers: { "Authorization": `Bearer ${localPhestyToken}` }
  });
  const authPhesty = await authenticateRequest(reqPhesty);
  assert.ok(!(authPhesty instanceof Response));
  assert.strictEqual(authPhesty.callerPersona, 'phesty_official');
  console.log("  ✓ Valid Phesty authentication succeeded with callerPersona = phesty_official");

  console.log("\n--- PART 2: Deployed Remote Endpoint Containment Tests ---");
  const endpointUrl = `${supabaseUrl}/functions/v1/friday-pending-messages`;

  // Fetch real tokens issued by verify-passkey on the remote server
  console.log("Fetching live tokens from verify-passkey endpoint...");
  const livePhestyToken = await getLiveToken('phesty', 'mr.nice_guy');
  const liveBaronessToken = await getLiveToken('baroness', 'mrs.nice_guy');
  console.log("  ✓ Successfully minted live tokens for Phesty and Baroness");

  // Remote Test 1: Unauthenticated Request (Anon Key, No Persona JWT)
  console.log("10. Remote: Testing Unauthenticated Request (Anon key only, no persona JWT)...");
  const remoteRes1 = await fetch(`${endpointUrl}?user_id=phesty_official&since=1970-01-01T00:00:00Z`, {
    headers: { "apikey": anonKey }
  });
  assert.strictEqual(remoteRes1.status, 401, `Unauthenticated remote request should return 401, got ${remoteRes1.status}`);
  const remoteBody1 = await remoteRes1.json();
  assert.strictEqual(remoteBody1.error, "Unauthorized: Missing or invalid Authorization Bearer header");
  console.log(`  ✓ Remote endpoint rejected unauthenticated request with status 401 (${remoteBody1.error})`);

  // Remote Test 2: Expired Token to Deployed Endpoint
  console.log("11. Remote: Testing Expired JWT (signed with correct secret, past exp)...");
  const remoteRes2 = await fetch(`${endpointUrl}?user_id=phesty_official&since=1970-01-01T00:00:00Z`, {
    headers: {
      "apikey": anonKey,
      "Authorization": `Bearer ${expiredToken}`
    }
  });
  assert.strictEqual(remoteRes2.status, 401, `Expired token should return 401, got ${remoteRes2.status}`);
  const remoteBody2 = await remoteRes2.json();
  assert.strictEqual(remoteBody2.error, "Unauthorized: JWT has expired");
  console.log(`  ✓ Remote endpoint rejected expired token with status 401 VERBATIM: "${remoteBody2.error}"`);

  // Remote Test 3: Cross-Persona Access Attempt (Phesty JWT -> user_id=baroness_official)
  console.log("12. Remote: Testing Cross-Persona Access Attempt (Phesty JWT -> baroness_official)...");
  const remoteRes3 = await fetch(`${endpointUrl}?user_id=baroness_official&since=1970-01-01T00:00:00Z`, {
    headers: {
      "apikey": anonKey,
      "Authorization": `Bearer ${livePhestyToken}`
    }
  });
  assert.strictEqual(remoteRes3.status, 403, `Cross-persona request should return 403 Forbidden, got ${remoteRes3.status}`);
  const remoteBody3 = await remoteRes3.json();
  assert.strictEqual(remoteBody3.error, "Forbidden: Cannot access messages for another user");
  console.log(`  ✓ Remote endpoint rejected cross-persona access with status 403 (${remoteBody3.error})`);

  // Remote Test 4: Valid Phesty Token to Deployed Endpoint
  console.log("13. Remote: Testing Valid Phesty Token...");
  const remoteRes4 = await fetch(`${endpointUrl}?user_id=phesty_official&since=1970-01-01T00:00:00Z`, {
    headers: {
      "apikey": anonKey,
      "Authorization": `Bearer ${livePhestyToken}`
    }
  });
  assert.strictEqual(remoteRes4.status, 200, `Valid Phesty request should return 200, got ${remoteRes4.status}`);
  const remoteBody4 = await remoteRes4.json();
  assert.ok(Array.isArray(remoteBody4.messages), "Response should contain messages array");
  console.log(`  ✓ Remote endpoint returned status 200 OK with ${remoteBody4.messages.length} pending messages for Phesty`);

  // Remote Test 5: Valid Baroness Token to Deployed Endpoint
  console.log("14. Remote: Testing Valid Baroness Token...");
  const remoteRes5 = await fetch(`${endpointUrl}?user_id=baroness_official&since=1970-01-01T00:00:00Z`, {
    headers: {
      "apikey": anonKey,
      "Authorization": `Bearer ${liveBaronessToken}`
    }
  });
  assert.strictEqual(remoteRes5.status, 200, `Valid Baroness request should return 200, got ${remoteRes5.status}`);
  const remoteBody5 = await remoteRes5.json();
  assert.ok(Array.isArray(remoteBody5.messages), "Response should contain messages array");
  console.log(`  ✓ Remote endpoint returned status 200 OK with ${remoteBody5.messages.length} pending messages for Baroness`);

  console.log("\nAll Friday Pending-Messages security unit & remote integration tests passed! 🎉\n");
}

runTests().catch((err) => {
  console.error("Test execution failed:", err);
  process.exit(1);
});
