import assert from 'node:assert';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import postgres from 'postgres';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });

console.log("=== Running Memory Dedup & Conflict Resolution DB Tests ===");

async function testDedupAndConflict() {
  const testOwner = 'phesty_official';
  const category = 'preference';

  try {
    await sql`DELETE FROM friday_memories WHERE owner_id = ${testOwner} AND memory_text LIKE 'TEST_MEM_%'`;

    const initialText = 'TEST_MEM_User prefers drinking black coffee in the morning';
    const [mem1] = await sql`
      INSERT INTO friday_memories (owner_id, memory_text, category, importance, confidence, status)
      VALUES (${testOwner}, ${initialText}, ${category}, 3, 3, 'active')
      RETURNING id, confidence, status
    `;
    assert.strictEqual(mem1.confidence, 3);
    assert.strictEqual(mem1.status, 'active');
    console.log("✓ Inserted initial memory (confidence: 3, status: active)");

    const newConf = Math.min(5, mem1.confidence + 1);
    await sql`
      UPDATE friday_memories
      SET confidence = ${newConf}, last_reinforced_at = NOW()
      WHERE id = ${mem1.id}
    `;

    await sql`
      INSERT INTO friday_memory_history (memory_id, change_type, reason)
      VALUES (${mem1.id}, 'reinforced', 'restated in session')
    `;

    const [updated1] = await sql`SELECT confidence, last_reinforced_at FROM friday_memories WHERE id = ${mem1.id}`;
    assert.strictEqual(updated1.confidence, 4);
    assert.ok(updated1.last_reinforced_at !== null);

    const [history1] = await sql`
      SELECT change_type, reason FROM friday_memory_history
      WHERE memory_id = ${mem1.id} AND change_type = 'reinforced'
    `;
    assert.strictEqual(history1.change_type, 'reinforced');
    assert.strictEqual(history1.reason, 'restated in session');
    console.log("✓ Memory reinforcement verified (confidence bumped to 4, history row logged)");

    const newText = 'TEST_MEM_User switched from coffee to matcha green tea';
    const supersedesReason = 'switched from black coffee';

    await sql`UPDATE friday_memories SET status = 'stale' WHERE id = ${mem1.id}`;

    await sql`
      INSERT INTO friday_memory_history (memory_id, change_type, previous_text, reason)
      VALUES (${mem1.id}, 'superseded', ${initialText}, ${supersedesReason})
    `;

    const [mem2] = await sql`
      INSERT INTO friday_memories (owner_id, memory_text, category, importance, confidence, status)
      VALUES (${testOwner}, ${newText}, ${category}, 4, 4, 'active')
      RETURNING id, status
    `;
    assert.strictEqual(mem2.status, 'active');

    const [staleMem] = await sql`SELECT status FROM friday_memories WHERE id = ${mem1.id}`;
    assert.strictEqual(staleMem.status, 'stale');

    const [history2] = await sql`
      SELECT change_type, previous_text, reason FROM friday_memory_history
      WHERE memory_id = ${mem1.id} AND change_type = 'superseded'
    `;
    assert.strictEqual(history2.change_type, 'superseded');
    assert.strictEqual(history2.previous_text, initialText);
    assert.strictEqual(history2.reason, supersedesReason);
    console.log("✓ Memory conflict resolution verified (old marked stale, history superseded row logged, new memory active)");

    await sql`DELETE FROM friday_memories WHERE owner_id = ${testOwner} AND memory_text LIKE 'TEST_MEM_%'`;
    console.log("All Memory Dedup & Conflict Resolution tests passed! 🎉\n");

  } catch (err) {
    console.error("Dedup/conflict test error:", err);
    process.exit(1);
  } finally {
    await sql.end();
  }
}

testDedupAndConflict();
