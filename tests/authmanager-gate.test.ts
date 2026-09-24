import assert from 'node:assert';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const supabaseUrl = "https://wckluymkbqxdmipzaiff.supabase.co";
const supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4MjM3NjAsImV4cCI6MjA5MzM5OTc2MH0.y3murBfcZtgluuPd_uFBut4Ky3Wl8WAHVCp-kA1u9sU";

console.log("=== Running Gate Screen / AuthManager Integration Test ===");

async function testGateLogin(persona: string, inputPass: string) {
  const accessUrl = `${supabaseUrl}/rest/v1/access_keys?id=eq.${persona}&select=secret_key`;
  const accessRes = await fetch(accessUrl, {
    headers: {
      "apikey": supabaseKey,
      "Authorization": `Bearer ${supabaseKey}`
    }
  });

  assert.strictEqual(accessRes.status, 200, `access_keys HTTP status should be 200, got ${accessRes.status}`);
  const accessData = await accessRes.json();
  assert.ok(Array.isArray(accessData) && accessData.length > 0, `access_keys should return data for persona ${persona}`);
  const storedSecretKey = accessData[0].secret_key;
  assert.strictEqual(storedSecretKey, inputPass, `Passkey for ${persona} should match`);

  const currentPersonaId = `${persona}_official`;

  const profileUrl = `${supabaseUrl}/rest/v1/profiles?id=eq.${currentPersonaId}&select=display_name,avatar_url,persona`;
  const profileRes = await fetch(profileUrl, {
    headers: {
      "apikey": supabaseKey,
      "Authorization": `Bearer ${supabaseKey}`
    }
  });

  assert.strictEqual(profileRes.status, 200, `profiles HTTP status should be 200, got ${profileRes.status}`);
  const profileData = await profileRes.json();
  assert.ok(Array.isArray(profileData) && profileData.length > 0, `profiles should return profile for ${currentPersonaId}`);

  console.log(`✓ Gate Login SUCCESS for persona '${persona}' -> ${currentPersonaId} (Display Name: '${profileData[0].display_name}')`);
}

async function run() {
  try {
    await testGateLogin('phesty', 'mr.nice_guy');
    await testGateLogin('baroness', 'mrs.nice_guy');
    console.log("All Gate Screen / AuthManager login tests passed! 🎉\n");
  } catch (err) {
    console.error("Gate Login test failed:", err);
    process.exit(1);
  }
}

run();
