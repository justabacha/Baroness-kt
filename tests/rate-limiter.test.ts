import assert from 'node:assert';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import postgres from 'postgres';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });

console.log("=== Running Rate Limiter DB Tests ===");

async function testRateLimiter() {
  const testOwner = 'phesty_official';
  const provider = 'gemini';
  const today = new Date().toISOString().split('T')[0];

  try {
    await sql`DELETE FROM friday_api_usage WHERE owner_id = ${testOwner} AND provider = ${provider} AND day = ${today}::date`;

    const [res1] = await sql`SELECT increment_api_usage(${testOwner}, ${provider}, ${today}::date) as call_count`;
    assert.strictEqual(res1.call_count, 1, 'First call count should be 1');
    console.log("✓ Initial RPC call incremented count to 1");

    const [res2] = await sql`SELECT increment_api_usage(${testOwner}, ${provider}, ${today}::date) as call_count`;
    assert.strictEqual(res2.call_count, 2, 'Second call count should be 2');
    console.log("✓ Second RPC call incremented count to 2");

    const limit = 2;
    const allowedAfter2 = res2.call_count <= limit;
    assert.strictEqual(allowedAfter2, true, 'Calls <= limit should be allowed');

    const [res3] = await sql`SELECT increment_api_usage(${testOwner}, ${provider}, ${today}::date) as call_count`;
    assert.strictEqual(res3.call_count, 3, 'Third call count should be 3');

    const allowedAfter3 = res3.call_count <= limit;
    assert.strictEqual(allowedAfter3, false, 'Calls > limit should be blocked');
    console.log("✓ Rate limit exceeded correctly detected as blocked");

    await sql`DELETE FROM friday_api_usage WHERE owner_id = ${testOwner} AND provider = ${provider} AND day = ${today}::date`;
    console.log("All Rate Limiter tests passed! 🎉\n");
  } catch (err) {
    console.error("Rate limiter test error:", err);
    process.exit(1);
  } finally {
    await sql.end();
  }
}

testRateLimiter();
