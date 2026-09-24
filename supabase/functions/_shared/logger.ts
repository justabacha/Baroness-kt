export interface LogMeta {
  owner_id?: string;
  session_id?: string;
  model?: string;
  latency_ms?: number;
  action_name?: string;
  memory_category?: string;
  success?: boolean;
  error_code?: string;
  provider?: string;
  candidates_found?: number;
  candidates_reinforced?: number;
  candidates_superseded?: number;
  candidates_new?: number;
  prompt_tokens?: number;
  completion_tokens?: number;
  total_tokens?: number;
  [key: string]: unknown;
}

const DISALLOWED_KEYS = new Set([
  'message',
  'messages',
  'content',
  'memory_text',
  'raw_output',
  'prompt',
  'parameters',
  'params',
  'secret',
  'key'
]);

export function logEvent(event: string, meta: LogMeta = {}): void {
  const safeMeta: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(meta)) {
    if (DISALLOWED_KEYS.has(key.toLowerCase())) {
      continue;
    }
    safeMeta[key] = value;
  }

  const logEntry = {
    event,
    ...safeMeta,
    ts: new Date().toISOString()
  };

  console.log(JSON.stringify(logEntry));
}
