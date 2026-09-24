import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { logEvent } from "../_shared/logger.ts";

serve(async (req) => {
  const internalSecret = req.headers.get("X-Internal-Secret");
  const cronSecret = Deno.env.get("INTERNAL_CRON_SECRET");

  if (cronSecret && internalSecret !== cronSecret) {
    return new Response("Unauthorized", { status: 401 });
  }

  const url = new URL(req.url);
  const force = url.searchParams.get("force") === "true";

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
  );

  try {
    console.log(">>> [INITIATIVE] Starting check...");

    const { data: recentMessages } = await supabase
      .from("friday_messages")
      .select("owner_id, created_at")
      .order("created_at", { ascending: false });

    const latestPerUser: Record<string, string> = {};
    recentMessages?.forEach(m => {
      if (!latestPerUser[m.owner_id]) latestPerUser[m.owner_id] = m.created_at;
    });

    for (const [ownerId, lastCreatedAt] of Object.entries(latestPerUser)) {
      const lastTime = new Date(lastCreatedAt).getTime();
      const gapHours = (Date.now() - lastTime) / (60 * 60 * 1000);

      console.log(`>>> [INITIATIVE] User ${ownerId} was active ${gapHours.toFixed(2)}h ago.`);

      const isWindowOpen = force ? (gapHours > 0.001) : (gapHours >= 12 && gapHours <= 18);

      if (isWindowOpen) {
        let memQuery = supabase.from("friday_memories").select("*").eq("owner_id", ownerId);
        if (!force) memQuery = memQuery.eq("follow_up_worthy", true);

        const { data: memories } = await memQuery.order("created_at", { ascending: false }).limit(5);

        if (!memories || memories.length === 0) {
          console.log(`>>> [INITIATIVE] No memories found for ${ownerId}.`);
          continue;
        }

        const memoryList = memories.map(m => `- ${m.memory_text}`).join("\n");
        const groqKey = Deno.env.get("REFLECTION_GROQ_API_KEY") || Deno.env.get("GROQ_API_KEY");

        console.log(`>>> [INITIATIVE] Generating message for ${ownerId} using Groq...`);

        const prompt = `Act as FRIDAY, a witty, high-vibe best friend to Phestone.
You're checking in after a long time apart. Use these memories to pick ONE natural thing to bring up:
${memoryList}

TASK: Write a SHORT (1 sentence), ultra-casual text message.
VIBE RULES:
- Use lowercase, mate.
- Use slang: 'uni', 'mate', 'bangers', 'trenches'.
- ABSOLUTELY NO "Hi", "Hey", "Hope", or "I remember".
- Start mid-thought.

Output ONLY the raw text message.`;

        const resp = await fetch("https://api.groq.com/openai/v1/chat/completions", {
          method: "POST",
          headers: { "Authorization": `Bearer ${groqKey}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            model: "openai/gpt-oss-120b",
            messages: [{ role: "user", content: prompt }],
            temperature: 0.9
          })
        });

        if (resp.ok) {
          const data = await resp.json();
          const usage = data.usage || {};
          logEvent("initiative.outreach", {
            owner_id: ownerId,
            model: "openai/gpt-oss-120b",
            provider: "groq",
            prompt_tokens: usage.prompt_tokens ?? 0,
            completion_tokens: usage.completion_tokens ?? 0,
            total_tokens: usage.total_tokens ?? 0,
          });

          const text = data.choices[0].message.content.trim();
          console.log(`>>> [INITIATIVE] Generated: ${text}`);

          const msgId = crypto.randomUUID();
          await supabase.from("friday_messages").insert({
            id: msgId, owner_id: ownerId, sender: "friday",
            message: text, is_proactive: true
          });

          await supabase.from("chat_sync_pipe").insert({
            recipient_id: ownerId,
            payload: {
              type: "NEW_MESSAGE", messageId: msgId, conversationId: "friday",
              senderId: "friday", content: text, timestamp: Date.now(),
              is_proactive: true, typing_duration_ms: 2500
            }
          });
          console.log(`>>> [INITIATIVE] Successfully sent to ${ownerId}`);
        } else {
          console.error(`>>> [INITIATIVE] Groq Error: ${resp.status}`);
        }
      }
    }
    return new Response("OK", { status: 200 });
  } catch (err) {
    console.error(">>> [INITIATIVE] Critical failure:", err.message);
    return new Response(err.message, { status: 500 });
  }
});
