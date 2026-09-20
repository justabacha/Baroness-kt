import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
  );

  try {
    // 1. Find the oldest session that is "closed" (no messages in last 30 mins)
    // and has unreflected messages.
    const idleLimit = new Date(Date.now() - 30 * 60 * 1000).toISOString();

    const { data: unreflected } = await supabase
      .from("friday_messages")
      .select("session_id, owner_id")
      .is("reflected_at", null)
      .lt("created_at", idleLimit)
      .order("created_at", { ascending: true })
      .limit(1);

    if (!unreflected || unreflected.length === 0) {
      return new Response("No sessions ready for reflection", { status: 200 });
    }

    const { session_id, owner_id } = unreflected[0];

    // 2. Fetch the full transcript for this session
    const { data: messages } = await supabase
      .from("friday_messages")
      .select("sender, message, created_at")
      .eq("session_id", session_id)
      .order("created_at", { ascending: true });

    if (!messages || messages.length === 0) {
      return new Response("Empty session", { status: 200 });
    }

    const transcript = messages.map(m => `${m.sender}: ${m.message}`).join("\n");

    // 3. Invoke DeepSeek R1 for Extraction
    const openRouterKey = Deno.env.get("OPENROUTER_API_KEY");
    if (!openRouterKey) throw new Error("Missing OPENROUTER_API_KEY");

    console.log(`Reflecting on session ${session_id}...`);

    const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${openRouterKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "deepseek/deepseek-r1",
        messages: [
          {
            role: "system",
            content: `Review this conversation session and extract what a close friend would actually
remember afterward. Return ONLY valid JSON in this exact shape:

{
  "entities": [{"name": string, "aliases": string[], "relationship": string, "last_known_fact": string}],
  "memories": [{"content": string, "category": "preference"|"emotional_moment"|"life_event"|"recurring_pattern"|"entity_link", "entity_ref": string | null}]
}

Rules:
- Write memory content in third person, factual, distilled.
- Only extract things worth remembering weeks later. Skip small talk.
- If nothing is worth remembering, return {"entities": [], "memories": []}.`
          },
          { role: "user", content: `Conversation:\n${transcript}` }
        ],
        response_format: { type: "json_object" }
      })
    });

    if (!response.ok) throw new Error(`OpenRouter returned status ${response.status}`);

    const data = await response.json();
    const result = JSON.parse(data.choices[0].message.content);

    // 4. Upsert Entities
    if (result.entities && result.entities.length > 0) {
      for (const entity of result.entities) {
        await supabase.from("friday_entities").upsert({
          owner_id,
          name: entity.name,
          aliases: entity.aliases,
          relationship: entity.relationship,
          last_known_fact: entity.last_known_fact,
          updated_at: new Date().toISOString()
        }, { onConflict: "owner_id,name" });
      }
    }

    // 5. Store Memories with Embeddings
    if (result.memories && result.memories.length > 0) {
      const geminiKey = Deno.env.get("GEMINI_API_KEY");
      for (const mem of result.memories) {
        let embedding = null;
        if (geminiKey) {
          try {
            const embedResp = await fetch(
              `https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=${geminiKey}`,
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  model: "models/text-embedding-004",
                  content: { parts: [{ text: mem.content }] },
                }),
              }
            );
            if (embedResp.ok) {
              const eData = await embedResp.json();
              embedding = eData.embedding.values;
            }
          } catch (e) {
            console.error("Embedding failed for memory:", e);
          }
        }

        await supabase.from("friday_memories").insert({
          owner_id,
          memory_text: mem.content,
          category: mem.category,
          embedding,
          session_id,
          follow_up_worthy: mem.category === "life_event" || mem.category === "emotional_moment"
        });
      }
    }

    // 6. Mark messages as reflected
    await supabase
      .from("friday_messages")
      .update({ reflected_at: new Date().toISOString() })
      .eq("session_id", session_id);

    return new Response(JSON.stringify({ status: "success", session_id }), { status: 200 });

  } catch (err) {
    console.error("Reflection failed:", err);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
});
