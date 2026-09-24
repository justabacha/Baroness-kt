import assert from 'node:assert';
import { validateAction } from '../supabase/functions/_shared/action-validator.ts';

console.log("=== Running Classifier Ambiguity Tests ===");

const ambiguousInputs = [
  { type: 'CONVERSATION', intent: null, parameters: null },
  { type: 'COMMAND', intent: 'unknown_intent', parameters: { foo: 'bar' } },
  { type: 'COMMAND', intent: 'navigate', parameters: {} }, // Missing required destination
  { type: 'COMMAND', intent: 'set_timer', parameters: { minutes: -10 } }, // Negative minutes
  { type: 'COMMAND', intent: 'set_alarm', parameters: { hour: 28, minute: 0 } }, // Invalid hour
];

for (const input of ambiguousInputs) {
  const result = validateAction(input as any);
  assert.strictEqual(result.valid, false, `Input should be invalid: ${JSON.stringify(input)}`);
  console.log(`✓ Correctly rejected ambiguous/invalid candidate: ${input.intent || 'CONVERSATION'} (${result.reason})`);
}

console.log("All Classifier Ambiguity tests passed! 🎉\n");
