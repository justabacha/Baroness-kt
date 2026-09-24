import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { logEvent } from "./logger.ts";

export interface RateLimitResult {
  allowed: boolean;
  remaining: number;
}

export async function checkAndIncrement(
  supabase: SupabaseClient,
  ownerId: string,
  provider: "groq" | "gemini",
  dailyLimit?: number
): Promise<RateLimitResult> {
  const envLimitStr = provider === "gemini"
    ? Deno.env.get("GEMINI_DAILY_LIMIT")
    : Deno.env.get("GROQ_DAILY_LIMIT");

  const defaultLimit = provider === "gemini" ? 1000 : 5000;
  const limit = dailyLimit ?? (envLimitStr ? parseInt(envLimitStr, 10) : defaultLimit);
  const today = new Date().toISOString().split("T")[0];

  try {
    const { data: callCount, error } = await supabase.rpc("increment_api_usage", {
      p_owner_id: ownerId,
      p_provider: provider,
      p_day: today,
    });

    if (error) {
      console.error(`Rate limiter DB error (${provider}):`, error.message);
      return { allowed: true, remaining: limit };
    }

    const count = typeof callCount === "number" ? callCount : 1;
    const allowed = count <= limit;
    const remaining = Math.max(0, limit - count);

    if (!allowed) {
      logEvent("rate_limit.blocked", {
        owner_id: ownerId,
        provider,
        call_count: count,
        daily_limit: limit,
      });
    }

    return { allowed, remaining };
  } catch (err) {
    console.error(`Rate limiter exception (${provider}):`, err.message);
    return { allowed: true, remaining: limit };
  }
}
