import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { logEvent } from "../_shared/logger.ts";

serve(async (req) => {
  // 0. Security Handshake
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
    let query = supabase
      .from("friday_messages")
      .select("id, owner_id, sender, message, created_at, session_id")
      .is("reflected_at", null);

    if (!force) {
      const idleLimit = new Date(Date.now() - 30 * 60 * 1000).toISOString();
      query = query.lt("created_at", idleLimit);
    }

    const { data: allUnreflected, error: fetchErr } = await query
      .order("created_at", { ascending: true })
      .limit(100);

    if (fetchErr || !allUnreflected || allUnreflected.length === 0) {
      return new Response("No pending messages to reflect on", { status: 200 });
    }

    const userGroups = allUnreflected.reduce((acc, msg) => {
      if (!acc[msg.owner_id]) acc[msg.owner_id] = [];
      acc[msg.owner_id].push(msg);
      return acc;
    }, {} as Record<string, any[]>);

    let candidatesFound = 0;
    let candidatesReinforced = 0;
    let candidatesSuperseded = 0;
    let candidatesNew = 0;
    let embeddingFailureCount = 0;
    let insertFailureCount = 0;
    const reflectedMessageIds: string[] = [];
    const geminiKey = Deno.env.get("GEMINI_API_KEY")!;

    for (const [ownerId, messages] of Object.entries(userGroups)) {
      const transcript = messages.map(m => {
        const role = m.sender === "friday" ? "FRIDAY (AI)" : "USER";
        return `[${role}]: ${m.message}`;
      }).join("\n");

      console.log(`Performing Reflection sweep for user ${ownerId}...`);

      const systemPrompt = `You are Friday's Memory Secretary. Analyze the conversation between USER and FRIDAY (AI) to extract core factual memories, entities, and emotional vibe.

CRITICAL RULES:
1. SENDER ROLES: 'USER' is the human user. 'FRIDAY (AI)' is the AI companion.
2. USER-ONLY DATA: ONLY extract facts belonging to the USER.
3. ENTITY EXTRACTION: Identify specific people, places, or things.
4. MEMORY EXTRACTION: Extract distilled factual statements in THIRD PERSON (e.g., 'User works as a designer').
5. SCORING & CONFLICTS:
   - 'importance' (1-5): 1=trivial, 5=major life fact.
   - 'confidence' (1-5): 1=speculative, 5=explicit user statement.
   - 'supersedes': string describing prior fact this contradicts (e.g., 'used Spotify'), or null if not contradicting anything.
6. VIBE EXTRACTION: Friday's vibe ('chilled', 'hyped', 'salty', 'concerned', 'playful').`;

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
                fact: { type: "string" }
              },
              required: ["name", "fact"]
            }
          },
          memories: {
            type: "array",
            items: {
              type: "object",
              properties: {
                category: {
                  type: "string",
                  enum: ["preference", "emotional_moment", "life_event", "recurring_pattern", "entity_link"]
                },
                content: { type: "string" },
                emotionTag: { type: "string" },
                importance: { type: "integer" },
                confidence: { type: "integer" },
                followUpWorthy: { type: "boolean" },
                supersedes: { type: "string" }
              },
              required: ["category", "content", "importance", "confidence", "followUpWorthy"]
            }
          },
          vibe: {
            type: "string",
            enum: ["chilled", "hyped", "salty", "concerned", "playful"]
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
        const usage = data.usageMetadata || {};
        logEvent("reflection.extract", {
          owner_id: ownerId,
          model: "gemini-3.5-flash",
          provider: "gemini",
          prompt_tokens: usage.promptTokenCount ?? 0,
          completion_tokens: usage.candidatesTokenCount ?? 0,
          total_tokens: usage.totalTokenCount ?? 0,
        });

        const result = JSON.parse(data.candidates[0].content.parts[0].text);

        // Save Entities
        if (result.entities?.length > 0) {
          for (const entity of result.entities) {
            await supabase.from("friday_entities").upsert({
              owner_id: ownerId,
              name: entity.name,
              aliases: entity.aliases || [],
              relationship: entity.relationship || null,
              last_known_fact: entity.fact || entity.last_known_fact,
              updated_at: new Date().toISOString()
            }, { onConflict: "owner_id,name" });
          }
        }

        // Save Memories with Dedup & Conflict Resolution (§7)
        if (result.memories?.length > 0) {
          candidatesFound += result.memories.length;

          for (const candidate of result.memories) {
            const importance = Math.min(5, Math.max(1, candidate.importance || 3));
            const confidence = Math.min(5, Math.max(1, candidate.confidence || 3));
            const category = candidate.category || "preference";

            const embedRes = await fetch(
              `https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=${geminiKey}`,
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  model: "models/gemini-embedding-001",
                  content: { parts: [{ text: candidate.content }] },
                  outputDimensionality: 768
                })
              }
            );

            if (!embedRes.ok) {
              embeddingFailureCount++;
              continue;
            }

            const embedData = await embedRes.json();
            const embedUsage = embedData?.usageMetadata || {};
            logEvent("reflection.embedding", {
              owner_id: ownerId,
              model: "gemini-embedding-001",
              provider: "gemini",
              prompt_tokens: embedUsage.promptTokenCount ?? 0,
              completion_tokens: 0,
              total_tokens: embedUsage.totalTokenCount ?? embedUsage.promptTokenCount ?? 0,
            });

            const embedding = embedData.embedding?.values;
            if (!Array.isArray(embedding) || embedding.length !== 768) {
              embeddingFailureCount++;
              continue;
            }

            const { data: rawNeighbors } = await supabase.rpc("match_friday_memories", {
              query_embedding: embedding,
              user_owner_id: ownerId,
              match_threshold: 0.0,
              match_count: 5
            });

            let nearest = null;
            let distance = Infinity;

            if (rawNeighbors && rawNeighbors.length > 0) {
              const neighborIds = rawNeighbors.map((n: any) => n.id);
              const { data: fullNeighbors } = await supabase
                .from("friday_memories")
                .select("id, memory_text, importance, confidence, status, category")
                .in("id", neighborIds)
                .eq("category", category)
                .eq("status", "active");

              if (fullNeighbors && fullNeighbors.length > 0) {
                const activeMap = new Map(fullNeighbors.map((fn: any) => [fn.id, fn]));
                for (const rawN of rawNeighbors) {
                  if (activeMap.has(rawN.id)) {
                    nearest = activeMap.get(rawN.id);
                    distance = 1 - rawN.similarity;
                    break;
                  }
                }
              }
            }

            const sessionId = messages[0]?.session_id || null;

            if (nearest && distance < 0.15) {
              const newConfidence = Math.min(5, (nearest.confidence || 3) + 1);
              await supabase
                .from("friday_memories")
                .update({
                  confidence: newConfidence,
                  last_reinforced_at: new Date().toISOString()
                })
                .eq("id", nearest.id);

              await supabase
                .from("friday_memory_history")
                .insert({
                  memory_id: nearest.id,
                  change_type: "reinforced",
                  reason: "restated in session"
                });

              candidatesReinforced++;

            } else if (nearest && candidate.supersedes && distance < 0.45) {
              await supabase
                .from("friday_memories")
                .update({ status: "stale" })
                .eq("id", nearest.id);

              await supabase
                .from("friday_memory_history")
                .insert({
                  memory_id: nearest.id,
                  change_type: "superseded",
                  previous_text: nearest.memory_text,
                  reason: candidate.supersedes
                });

              const { error: insErr } = await supabase
                .from("friday_memories")
                .insert({
                  owner_id: ownerId,
                  memory_text: candidate.content,
                  category,
                  emotion_tag: candidate.emotionTag || null,
                  embedding,
                  importance,
                  confidence,
                  status: "active",
                  follow_up_worthy: !!candidate.followUpWorthy,
                  session_id: sessionId
                });

              if (insErr) {
                insertFailureCount++;
              } else {
                candidatesSuperseded++;
              }

            } else {
              const { error: insErr } = await supabase
                .from("friday_memories")
                .insert({
                  owner_id: ownerId,
                  memory_text: candidate.content,
                  category,
                  emotion_tag: candidate.emotionTag || null,
                  embedding,
                  importance,
                  confidence,
                  status: "active",
                  follow_up_worthy: !!candidate.followUpWorthy,
                  session_id: sessionId
                });

              if (insErr) {
                insertFailureCount++;
              } else {
                candidatesNew++;
              }
            }
          }
        }

        if (result.vibe) {
          await supabase
            .from("profiles")
            .update({ friday_vibe: result.vibe })
            .eq("id", ownerId);
        }

        reflectedMessageIds.push(...messages.map(m => m.id));
      }
    }

    if (reflectedMessageIds.length > 0) {
      const { error: markErr } = await supabase
        .from("friday_messages")
        .update({ reflected_at: new Date().toISOString() })
        .in("id", reflectedMessageIds);
      if (markErr) throw markErr;
    }

    logEvent("reflection.run", {
      candidates_found: candidatesFound,
      candidates_reinforced: candidatesReinforced,
      candidates_superseded: candidatesSuperseded,
      candidates_new: candidatesNew,
      processed_messages: reflectedMessageIds.length,
    });

    return new Response(JSON.stringify({
      status: "success",
      processed_messages: reflectedMessageIds.length,
      candidates_found: candidatesFound,
      candidates_reinforced: candidatesReinforced,
      candidates_superseded: candidatesSuperseded,
      candidates_new: candidatesNew,
      embedding_failures: embeddingFailureCount,
      insert_failures: insertFailureCount
    }), { status: 200 });

  } catch (err) {
    console.error("Reflection failed:", err);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
});
