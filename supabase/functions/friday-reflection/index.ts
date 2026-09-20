import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  // 0. Security Handshake
  const internalSecret = req.headers.get("X-Internal-Secret");
  const cronSecret = Deno.env.get("INTERNAL_CRON_SECRET");
  if (cronSecret && internalSecret !== cronSecret) {
    return new Response("Unauthorized", { status: 401 });
  }

  // Check if we are forcing a reflection (for testing)
  const url = new URL(req.url);
  const force = url.searchParams.get("force") === "true";

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
  );

  try {
    // 1. Fetch ALL unreflected messages (Full Sweep)
    let query = supabase
      .from("friday_messages")
      .select("*")
      .is("reflected_at", null);

    if (!force) {
      const idleLimit = new Date(Date.now() - 30 * 60 * 1000).toISOString();
      query = query.lt("created_at", idleLimit);
    }

    const { data: allUnreflected, error: fetchErr } = await query.order("created_at", { ascending: true });

    if (fetchErr || !allUnreflected || allUnreflected.length === 0) {
      return new Response("No pending messages to reflect on", { status: 200 });
    }

    // 2. Group by owner_id to process multiple users if needed
    const userGroups = allUnreflected.reduce((acc, msg) => {
      if (!acc[msg.owner_id]) acc[msg.owner_id] = [];
      acc[msg.owner_id].push(msg);
      return acc;
    }, {} as Record<string, any[]>);

    let totalExtracted = 0;
    const geminiKey = Deno.env.get("GEMINI_API_KEY")!;

    for (const [ownerId, messages] of Object.entries(userGroups)) {
      const transcript = messages.map(m => {
        const role = m.sender === "friday" ? "FRIDAY (AI)" : "USER";
        return `[${role}]: ${m.message}`;
      }).join("\n");

      console.log(`Performing Full Sweep reflection for user ${ownerId}...`);

      const systemPrompt = `You are Friday's Memory Secretary. Your job is to analyze a conversation between USER and FRIDAY (AI) and extract CORE FACTS about the USER to be stored in their personal memory bank.

CRITICAL RULES:
1. SENDER ROLES: 'USER' is the human user. 'FRIDAY (AI)' is you.
2. USER-ONLY DATA: ONLY extract facts, preferences, plans, and emotional states belonging to the USER. NEVER record facts about Friday herself.
3. ENTITY EXTRACTION: Identify specific people, places (like universities), or recurring items mentioned by the USER. These must go in the 'entities' array.
4. MEMORY EXTRACTION: Extract distilled factual statements about the USER. Things like 'Exams next week' or 'Attends University of Eldoret' are high priority.
5. STYLE: Format all memories in the THIRD PERSON factual style (e.g., 'User has an exam next week').
6. PRIVACY: Each memory must be specific to this user's context.

Output MUST strictly match the provided JSON schema.`;

      const jsonSchema = {
        type: "object",
        properties: {
          entities: {
            type: "array",
            items: {
              type: "object",
              properties: {
                name: { type: "string" },
                aliases: { type: "array", items: { type: "string" } },
                relationship: { type: "string" },
                last_known_fact: { type: "string" }
              },
              required: ["name", "last_known_fact"]
            }
          },
          memories: {
            type: "array",
            items: {
              type: "object",
              properties: {
                content: { type: "string" },
                category: { type: "string", enum: ["preference", "emotional_moment", "life_event", "recurring_pattern", "entity_link"] }
              },
              required: ["content", "category"]
            }
          }
        },
        required: ["entities", "memories"]
      };

      const response = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${geminiKey}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: systemPrompt }] },
            contents: [{ role: "user", parts: [{ text: `Transcript:\n${transcript}` }] }],
            generationConfig: {
              responseMimeType: "application/json",
              responseSchema: jsonSchema,
              temperature: 0.1
            }
          })
        }
      );

      if (response.ok) {
        const data = await response.json();
        const result = JSON.parse(data.candidates[0].content.parts[0].text);

        // 4. Save to DB (Entities & Memories)
        if (result.entities?.length > 0) {
          for (const entity of result.entities) {
            await supabase.from("friday_entities").upsert({
              owner_id: ownerId,
              name: entity.name,
              aliases: entity.aliases,
              relationship: entity.relationship,
              last_known_fact: entity.last_known_fact,
              updated_at: new Date().toISOString()
            }, { onConflict: "owner_id,name" });
          }
        }

        if (result.memories?.length > 0) {
          for (const mem of result.memories) {
            // 1. Generate embedding for the new candidate memory
            let embedding = null;
            const eResp = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=${geminiKey}`, {
              method: "POST", headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ model: "models/text-embedding-004", content: { parts: [{ text: mem.content }] } })
            });
            if (eResp.ok) embedding = (await eResp.json()).embedding.values;

            if (embedding) {
              // 2. Vector Similarity Deduplication: Check if a similar memory already exists (>0.85 similarity)
              const { data: similarMemories } = await supabase.rpc("match_friday_memories", {
                query_embedding: embedding,
                user_owner_id: ownerId,
                match_threshold: 0.85,
                match_count: 1
              });

              if (similarMemories && similarMemories.length > 0) {
                console.log(`Skipping duplicate memory for ${ownerId}: "${mem.content}" (Matches: "${similarMemories[0].memory_text}")`);
                continue;
              }

              // 3. Save new unique memory
              await supabase.from("friday_memories").insert({
                owner_id: ownerId,
                memory_text: mem.content,
                category: mem.category,
                embedding,
                follow_up_worthy: ["life_event", "emotional_moment"].includes(mem.category)
              });
              totalExtracted++;
            }
          }
        }
      }
    }

    // 6. Mark ALL processed messages as reflected
    const messageIds = allUnreflected.map(m => m.id);
    await supabase.from("friday_messages").update({ reflected_at: new Date().toISOString() }).in("id", messageIds);

    return new Response(JSON.stringify({ status: "success", processed_messages: messageIds.length, extracted_memories: totalExtracted }), { status: 200 });

  } catch (err) {
    console.error("Reflection failed:", err);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
});
