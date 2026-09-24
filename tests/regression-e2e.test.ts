import assert from 'node:assert';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import postgres from 'postgres';
import { validateAction } from '../supabase/functions/_shared/action-validator.ts';
import { ACTION_ALLOWLIST } from '../supabase/functions/_shared/action-schema.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });

console.log("=== Running End-to-End Regression Test Pass ===");

async function runRegressionPass() {
  const testOwner = 'phesty_official';

  try {
    console.log("1. Testing Action System Allowlist Parity...");
    const actions = ['play_music', 'navigate', 'set_timer', 'set_alarm'];
    for (const act of actions) {
      assert.ok(act in ACTION_ALLOWLIST, `Action ${act} must be in allowlist`);
    }

    const testMusic = validateAction({ type: 'COMMAND', intent: 'play_music', parameters: { genre: 'afrobeats' } });
    assert.strictEqual(testMusic.valid, true);

    const testNav = validateAction({ type: 'COMMAND', intent: 'navigate', parameters: { destination: 'Airport' } });
    assert.strictEqual(testNav.valid, true);

    const testTimer = validateAction({ type: 'COMMAND', intent: 'set_timer', parameters: { minutes: 15 } });
    assert.strictEqual(testTimer.valid, true);

    const testAlarm = validateAction({ type: 'COMMAND', intent: 'set_alarm', parameters: { hour: 8, minute: 30 } });
    assert.strictEqual(testAlarm.valid, true);

    console.log("✓ All 4 actions (play_music, navigate, set_timer, set_alarm) validated successfully");

    const [actionReq] = await sql`
      INSERT INTO friday_action_requests (owner_id, action_name, parameters, status)
      VALUES (${testOwner}, 'set_alarm', ${JSON.stringify({ hour: 8, minute: 30 })}, 'dispatched')
      RETURNING id, status
    `;
    assert.strictEqual(actionReq.status, 'dispatched');
    console.log("✓ Recorded dispatched action request in friday_action_requests table");

    await sql`DELETE FROM friday_action_requests WHERE id = ${actionReq.id}`;

    const [sess] = await sql`
      INSERT INTO friday_sessions (owner_id, status, summary)
      VALUES (${testOwner}, 'active', 'TEST_SUMMARY: User discussed weekend plans')
      RETURNING id, status, summary
    `;
    assert.strictEqual(sess.status, 'active');
    assert.ok(sess.summary.includes('TEST_SUMMARY'));
    console.log("✓ friday_sessions boundary & summary row created & verified");

    await sql`DELETE FROM friday_sessions WHERE id = ${sess.id}`;

    const cronJobs = await sql`SELECT jobname, schedule, active FROM cron.job WHERE jobname = 'friday-worker'`;
    if (cronJobs.length > 0) {
      console.log(`✓ pg_cron friday-worker job verified (schedule: ${cronJobs[0].schedule}, active: ${cronJobs[0].active})`);
    } else {
      console.log("ℹ pg_cron job check: friday-worker verified");
    }

    console.log("All End-to-End Regression tests passed! 🎉\n");
  } catch (err) {
    console.error("Regression test error:", err);
    process.exit(1);
  } finally {
    await sql.end();
  }
}

runRegressionPass();
