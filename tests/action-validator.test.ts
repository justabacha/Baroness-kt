import assert from 'node:assert';
import { validateAction } from '../supabase/functions/_shared/action-validator.ts';

console.log("=== Running Action Validator Unit Tests ===");

// Test 1: Valid play_music command
const res1 = validateAction({
  type: 'COMMAND',
  intent: 'play_music',
  parameters: { query: 'Alie Gatie Can\'t Lie', app: 'youtube', genre: 'jazz', extra_junk: 'should_be_stripped' }
});
assert.strictEqual(res1.valid, true);
assert.strictEqual(res1.action?.intent, 'play_music');
assert.strictEqual(res1.action?.parameters.query, 'Alie Gatie Can\'t Lie');
assert.strictEqual(res1.action?.parameters.app, 'youtube');
assert.strictEqual(res1.action?.parameters.genre, 'jazz');
assert.strictEqual(res1.action?.parameters.extra_junk, undefined, 'Extra parameter should be stripped');
console.log("✓ Valid play_music with query, app, genre, and parameter stripping passed");

// Test 2: Valid navigate command
const res2 = validateAction({
  type: 'COMMAND',
  intent: 'navigate',
  parameters: { destination: 'Nairobi CBD' }
});
assert.strictEqual(res2.valid, true);
assert.strictEqual(res2.action?.parameters.destination, 'Nairobi CBD');
console.log("✓ Valid navigate passed");

// Test 3: Missing required parameter (navigate missing destination)
const res3 = validateAction({
  type: 'COMMAND',
  intent: 'navigate',
  parameters: {}
});
assert.strictEqual(res3.valid, false);
assert.strictEqual(res3.reason, 'missing destination');
console.log("✓ Missing required parameter rejected correctly");

// Test 4: Out of range timer minutes (< 1 or > 1440)
const res4a = validateAction({
  type: 'COMMAND',
  intent: 'set_timer',
  parameters: { minutes: 0 }
});
assert.strictEqual(res4a.valid, false);
assert.ok(res4a.reason?.includes('below minimum'));

const res4b = validateAction({
  type: 'COMMAND',
  intent: 'set_timer',
  parameters: { minutes: 2000 }
});
assert.strictEqual(res4b.valid, false);
assert.ok(res4b.reason?.includes('above maximum'));
console.log("✓ Out of range timer minutes rejected correctly");

// Test 5: Out of range alarm hour/minute
const res5a = validateAction({
  type: 'COMMAND',
  intent: 'set_alarm',
  parameters: { hour: 25, minute: 30 }
});
assert.strictEqual(res5a.valid, false);
assert.ok(res5a.reason?.includes('hour above maximum'));

const res5b = validateAction({
  type: 'COMMAND',
  intent: 'set_alarm',
  parameters: { hour: 7, minute: -5 }
});
assert.strictEqual(res5b.valid, false);
assert.ok(res5b.reason?.includes('minute below minimum'));
console.log("✓ Out of range alarm values rejected correctly");

// Test 6: Unknown action intent
const res6 = validateAction({
  type: 'COMMAND',
  intent: 'open_youtube',
  parameters: {}
});
assert.strictEqual(res6.valid, false);
assert.strictEqual(res6.reason, 'unknown action');
console.log("✓ Unknown intent rejected correctly");

// Test 7: Non-COMMAND type
const res7 = validateAction({
  type: 'CONVERSATION',
  intent: 'play_music',
  parameters: {}
});
assert.strictEqual(res7.valid, false);
assert.strictEqual(res7.reason, 'not a command');
console.log("✓ Non-COMMAND type rejected correctly");

// Test 8: Wrong parameter type
const res8 = validateAction({
  type: 'COMMAND',
  intent: 'set_timer',
  parameters: { minutes: "five" }
});
assert.strictEqual(res8.valid, false);
assert.strictEqual(res8.reason, 'minutes wrong type');
console.log("✓ Mismatched parameter type rejected correctly");

console.log("All Action Validator tests passed! 🎉\n");
