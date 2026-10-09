import assert from 'node:assert';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const supabaseUrl = "https://wckluymkbqxdmipzaiff.supabase.co";
const supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4MjM3NjAsImV4cCI6MjA5MzM5OTc2MH0.y3murBfcZtgluuPd_uFBut4Ky3Wl8WAHVCp-kA1u9sU";

console.log("=== Running Gate Screen / AuthManager Edge Function Integration Test ===");

async function testEdgeVerifyPasskey(persona: string, inputPass: string) {
  const verifyUrl = `${supabaseUrl}/functions/v1/verify-passkey`;
  const res = await fetch(verifyUrl, {
    method: 'POST',
    headers: {
      "Content-Type": "application/json",
      "apikey": supabaseKey,
      "Authorization": `Bearer ${supabaseKey}`
    },
    body: JSON.stringify({ persona, passkey: inputPass })
  });

  assert.strictEqual(res.status, 200, `verify-passkey HTTP status should be 200, got ${res.status}`);
  const data = await res.json();
  assert.ok(data.token, `Response should contain token for ${persona}`);
  assert.strictEqual(data.currentPersonaId, `${persona}_official`, `Persona ID should match ${persona}_official`);
  assert.ok(data.userProfile, `Response should contain userProfile object`);
  assert.ok(typeof data.userProfile.displayName === 'string', `userProfile should have displayName`);

  console.log(`✓ Gate Login via Edge Function SUCCESS for persona '${persona}' -> ${data.currentPersonaId} (Display Name: '${data.userProfile.displayName}')`);
}

async function testInvalidPasskey(persona: string, wrongPass: string) {
  const verifyUrl = `${supabaseUrl}/functions/v1/verify-passkey`;
  const res = await fetch(verifyUrl, {
    method: 'POST',
    headers: {
      "Content-Type": "application/json",
      "apikey": supabaseKey,
      "Authorization": `Bearer ${supabaseKey}`
    },
    body: JSON.stringify({ persona, passkey: wrongPass })
  });

  assert.strictEqual(res.status, 401, `Invalid passkey should return HTTP status 401, got ${res.status}`);
  const data = await res.json();
  assert.ok(data.error, `Invalid passkey response should contain error message`);
  console.log(`✓ Invalid passkey correctly rejected with status 401 (${data.error})`);
}

async function run() {
  try {
    await testEdgeVerifyPasskey('phesty', 'mr.nice_guy');
    await testEdgeVerifyPasskey('baroness', 'mrs.nice_guy');
    await testInvalidPasskey('phesty', 'wrong_passkey_123');
    console.log("All Gate Screen / AuthManager Edge Function tests passed! 🎉\n");
  } catch (err) {
    console.error("Gate Login test failed:", err);
    process.exit(1);
  }
}

run();
