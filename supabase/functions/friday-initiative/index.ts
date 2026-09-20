import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
  );

  try {
    // 1. Find users who haven't been messaged in 12-18 hours
    const minGap = new Date(Date.now() - 12 * 60 * 60 * 1000).toISOString();
    const maxGap = new Date(Date.now() - 18 * 60 * 60 * 1000).toISOString();

    // In a real multi-user system, we'd query for all distinct owner_ids.
    // For now, we'll fetch the last message for each owner and check the gap.
    const { data: recentMessages } = await supabase
      .from("friday_messages")
      .select("owner_id, created_at")
      .order("created_at", { ascending: false });

    // Deduplicate to get the latest per user
    const latestPerUser: Record<string, string> = {};
    recentMessages?.forEach(m => {
      if (!latestPerUser[m.owner_id]) latestPerUser[m.owner_id] = m.created_at;
    });

    for (const [ownerId, lastCreatedAt] of Object.entries(latestPerUser)) {
      const lastTime = new Date(lastCreatedAt).getTime();
      const now = Date.now();
      const gapHours = (now - lastTime) / (60 * 60 * 1000);

      if (gapHours >= 12 && gapHours <= 18) {
        // Check if we already sent an initiative message for this gap
        // We can check if the last message was a proactive one.
        const { data: check } = await supabase
          .from("friday_messages")
          .select("id")
          .eq("owner_id", ownerId)
          .eq("sender", "friday")
          .gt("created_at", lastCreatedAt)
          .limit(1);

        if (check && check.length > 0) continue; // Already sent something recently

        // 2. Find a follow-up worthy memory
        const { data: memories } = await supabase
          .from("friday_memories")
          .select("memory_text, category")
          .eq("owner_id", ownerId)
          .eq("follow_up_worthy", true)
          .order("created_at", { ascending: false })
          .limit(1);

        if (!memories || memories.length === 0) continue;

        const memory = memories[0].memory_text;

        // 3. Generate proactive message
        const geminiKey = Deno.env.get("GEMINI_API_KEY");
        if (!geminiKey) continue;

        const prompt = `You are FRIDAY reaching out to your friend after some time has passed.
You remember this about them: ${memory}

Write a short, natural text message (1-2 sentences max) that:
- References the memory naturally, not as "I remember when..."
- Feels like a friend checking in, not a notification
- Matches FRIDAY's vibe (warm, a little playful, but genuine)
- Does not ask more than one question
- Does not start with "Hey" or "Hi" — start mid-thought like a real text

Output only the message text, nothing else.`;

        const resp = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${geminiKey}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              contents: [{ parts: [{ text: prompt }] }],
              generationConfig: { temperature: 0.8, maxOutputTokens: 100 }
            })
          }
        );

        if (resp.ok) {
          const data = await resp.json();
          const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim();

          if (text) {
            // 4. Send Message
            const sessionId = crypto.randomUUID(); // Proactive messages start new sessions
            const msgId = crypto.randomUUID();

            await supabase.from("friday_messages").insert({
              id: msgId,
              owner_id: ownerId,
              sender: "friday",
              message: text,
              session_id: sessionId
            });

            await supabase.from("chat_sync_pipe").insert({
              recipient_id: ownerId,
              payload: {
                type: "NEW_MESSAGE",
                messageId: msgId,
                conversationId: "friday",
                senderId: "friday",
                content: text,
                timestamp: Date.now(),
                is_proactive: true
              }
            });

            console.log(`Sent initiative to ${ownerId}`);
          }
        }
      }
    }

    return new Response("Initiative check completed", { status: 200 });

  } catch (err) {
    console.error("Initiative failed:", err);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
});
