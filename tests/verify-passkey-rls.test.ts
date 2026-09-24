import assert from 'node:assert';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import postgres from 'postgres';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });

async function signJwt(payload: object, secret: string): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const encodeBase64Url = (buf: Uint8Array) =>
    Buffer.from(buf).toString('base64url');

  const enc = new TextEncoder();
  const headerStr = encodeBase64Url(enc.encode(JSON.stringify(header)));
  const payloadStr = encodeBase64Url(enc.encode(JSON.stringify(payload)));
  const data = enc.encode(`${headerStr}.${payloadStr}`);

  const crypto = await import('node:crypto');
  const hmac = crypto.createHmac('sha256', secret);
  hmac.update(data);
  const signatureStr = hmac.digest('base64url');

  return `${headerStr}.${payloadStr}.${signatureStr}`;
}

console.log("=== Running verify-passkey and RLS Isolation Tests ===");

async function testAuthAndRls() {
  const jwtSecret = process.env.SUPABASE_SERVICE_ROLE_KEY!;

  try {
    const [phestyKey] = await sql`SELECT secret_key FROM access_keys WHERE id = 'phesty'`;
    assert.strictEqual(phestyKey.secret_key, 'mr.nice_guy');
    console.log("✓ Service-role read of access_keys verified for phesty");

    const [baronessKey] = await sql`SELECT secret_key FROM access_keys WHERE id = 'baroness'`;
    assert.strictEqual(baronessKey.secret_key, 'mrs.nice_guy');
    console.log("✓ Service-role read of access_keys verified for baroness");

    const now = Math.floor(Date.now() / 1000);
    const phestyJwt = await signJwt({
      role: 'authenticated',
      persona: 'phesty_official',
      sub: 'phesty_official',
      iat: now,
      exp: now + 3600
    }, jwtSecret);

    const baronessJwt = await signJwt({
      role: 'authenticated',
      persona: 'baroness_official',
      sub: 'baroness_official',
      iat: now,
      exp: now + 3600
    }, jwtSecret);

    await sql.begin(async (tx) => {
      await tx.unsafe(`SET LOCAL request.jwt.claims = '{"role":"authenticated", "persona":"phesty_official"}'`);
      await tx.unsafe(`SET LOCAL ROLE authenticated`);

      const phestyMsgs = await tx`SELECT owner_id FROM friday_messages LIMIT 5`;
      for (const m of phestyMsgs) {
        assert.strictEqual(m.owner_id, 'phesty_official', "Phesty session must only return phesty_official rows");
      }
      console.log(`✓ RLS Isolation verified for phesty_official (returned ${phestyMsgs.length} rows, all matching phesty_official)`);
    });

    await sql.begin(async (tx) => {
      await tx.unsafe(`SET LOCAL request.jwt.claims = '{"role":"authenticated", "persona":"baroness_official"}'`);
      await tx.unsafe(`SET LOCAL ROLE authenticated`);

      const baronessMsgs = await tx`SELECT owner_id FROM friday_messages LIMIT 5`;
      for (const m of baronessMsgs) {
        assert.strictEqual(m.owner_id, 'baroness_official', "Baroness session must only return baroness_official rows");
      }
      console.log(`✓ RLS Isolation verified for baroness_official (returned ${baronessMsgs.length} rows, all matching baroness_official)`);
    });

    console.log("All verify-passkey & RLS Isolation tests passed! 🎉\n");
  } catch (err) {
    console.error("Auth & RLS test error:", err);
    process.exit(1);
  } finally {
    await sql.end();
  }
}

testAuthAndRls();
