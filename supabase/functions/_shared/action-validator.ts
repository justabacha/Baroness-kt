import type { ClassifierResult, ValidationResult } from './types.ts';
import { ACTION_ALLOWLIST } from './action-schema.ts';

export function validateAction(raw: ClassifierResult): ValidationResult {
  if (!raw || raw.type !== 'COMMAND') {
    return { valid: false, reason: 'not a command' };
  }

  if (!raw.intent || !(raw.intent in ACTION_ALLOWLIST)) {
    return { valid: false, reason: 'unknown action' };
  }

  const definition = ACTION_ALLOWLIST[raw.intent];
  const params = raw.parameters || {};
  const cleaned: Record<string, unknown> = {};

  for (const [key, schema] of Object.entries(definition.parameters)) {
    let value = params[key];

    // Alias mapping (e.g. length -> minutes)
    if ((value === undefined || value === null) && key === 'minutes' && params['length'] !== undefined) {
      value = params['length'];
    }

    if (value === undefined || value === null) {
      if (schema.required) {
        return { valid: false, reason: `missing ${key}` };
      }
      continue;
    }

    // Auto-coerce string numbers to number type (e.g. "1" -> 1)
    if (schema.type === 'number' && typeof value === 'string' && !isNaN(Number(value))) {
      value = Number(value);
    }

    if (typeof value !== schema.type) {
      return { valid: false, reason: `${key} wrong type` };
    }

    if (schema.type === 'number') {
      const numVal = value as number;
      if (schema.min !== undefined && numVal < schema.min) {
        return { valid: false, reason: `${key} below minimum ${schema.min}` };
      }
      if (schema.max !== undefined && numVal > schema.max) {
        return { valid: false, reason: `${key} above maximum ${schema.max}` };
      }
    }

    cleaned[key] = value;
  }

  return {
    valid: true,
    action: {
      intent: raw.intent,
      parameters: cleaned
    }
  };
}
