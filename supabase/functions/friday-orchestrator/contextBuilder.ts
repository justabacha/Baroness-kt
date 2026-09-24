import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { getPersonalityBlock } from "./personality.ts";
import { logEvent } from "../_shared/logger.ts";

export interface AssembledContext {
  systemPrompt: string;
  chatHistory: { role: "user" | "assistant"; content: string }[];
}

export async function buildContext(
  supabase: SupabaseClient,
  ownerId: string,
  messageText: string,
  currentMessageId?: string,
  meta?: { timeGapMinutes: number; currentTime: string; actionNote?: string }
): Promise<AssembledContext> {
  // 1. Fetch Working Memory (Last 15 raw messages from friday_messages)
  let historyQuery = supabase
    .from("friday_messages")
    .select("sender, message")
    .eq("owner_id", ownerId)
    .order("created_at", { ascending: false })
    .limit(15);
  if (currentMessageId) {
    historyQuery = historyQuery.neq("id", currentMessageId);
  }
  const { data: messages } = await historyQuery;

  const chatHistory = (messages ?? [])
    .reverse()
    .map((m) => ({
      role: m.sender === "friday" ? ("assistant" as const) : ("user" as const),
      content: m.message,
    }));

  // 1.1 Session Summary Context & Trigger
  let sessionSummaryBlock = "";
  const { data: activeSession } = await supabase
    .from("friday_sessions")
    .select("id, summary")
    .eq("owner_id", ownerId)
    .eq("status", "active")
    .order("started_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (activeSession?.summary) {
    sessionSummaryBlock = `\n[Session Summary]: ${activeSession.summary}\n`;
  }

  if (activeSession?.id) {
    const { count } = await supabase
      .from("friday_messages")
      .select("id", { count: "exact", head: true })
      .eq("owner_id", ownerId)
      .eq("session_id", activeSession.id);

    if (count && count > 20) {
      const { data: olderMsgs } = await supabase
        .from("friday_messages")
        .select("sender, message, created_at")
        .eq("owner_id", ownerId)
        .eq("session_id", activeSession.id)
        .order("created_at", { ascending: true });

      if (olderMsgs && olderMsgs.length > 15) {
        const msgsToSummarize = olderMsgs.slice(0, olderMsgs.length - 15);
        const transcript = msgsToSummarize.map((m) => `${m.sender}: ${m.message}`).join("\n");

        const groqKey = Deno.env.get("GROQ_API_KEY");
        if (groqKey) {
          try {
            const existingSummary = activeSession.summary ? `Existing summary: ${activeSession.summary}\n\n` : "";
            const summaryPrompt = `Summarize the following chat transcript into 2-4 concise sentences, updating or extending any existing session summary. Focus on key context, decisions, and topics.

${existingSummary}New messages to incorporate:
${transcript}`;

            const res = await fetch("https://api.groq.com/openai/v1/chat/completions", {
              method: "POST",
              headers: {
                "Authorization": `Bearer ${groqKey}`,
                "Content-Type": "application/json",
              },
              body: JSON.stringify({
                model: "openai/gpt-oss-20b",
                messages: [{ role: "user", content: summaryPrompt }],
                temperature: 0.3,
                max_tokens: 250,
              }),
            });

            if (res.ok) {
              const data = await res.json();
              const usage = data.usage || {};
              logEvent("session.summary", {
                owner_id: ownerId,
                model: "openai/gpt-oss-20b",
                provider: "groq",
                prompt_tokens: usage.prompt_tokens ?? 0,
                completion_tokens: usage.completion_tokens ?? 0,
                total_tokens: usage.total_tokens ?? 0,
              });

              const newSummary = data.choices?.[0]?.message?.content?.trim();
              if (newSummary) {
                await supabase
                  .from("friday_sessions")
                  .update({ summary: newSummary })
                  .eq("id", activeSession.id);
                sessionSummaryBlock = `\n[Session Summary]: ${newSummary}\n`;
              }
            }
          } catch (e) {
            console.error("Failed to generate session summary:", e.message);
          }
        }
      }
    }
  }

  // 2. Entity Lookup ("Who's Who")
  const { data: entities } = await supabase
    .from("friday_entities")
    .select("name, aliases, relationship, last_known_fact")
    .eq("owner_id", ownerId)
    .limit(100);

  let identityContextBlock = "";
  let entityContextBlock = "";
  let userPreferredName: string | null = null;

  if (entities && entities.length > 0) {
    const selfEntities = entities.filter((e) => e.relationship === "Self");
    const otherEntities = entities.filter((e) => e.relationship !== "Self");

    if (selfEntities.length > 0) {
      userPreferredName = selfEntities[0].name;
      identityContextBlock = "\nAbout the person you're talking to (Core Identity):\n" +
        selfEntities.map((e) => `- ${e.name}: ${e.last_known_fact ?? ""}`).join("\n") + "\n";
    }

    const lowerMsg = messageText.toLowerCase();
    const matchedExternal = otherEntities.filter((entity) => {
      const nameMatch = entity.name && lowerMsg.includes(entity.name.toLowerCase());
      const aliasMatch = Array.isArray(entity.aliases) && entity.aliases.some((alias: string) =>
        lowerMsg.includes(alias.toLowerCase())
      );
      return nameMatch || aliasMatch;
    });

    // If explicit substring match found, use those. Otherwise include top active entities as baseline background.
    const activeEntitiesToInclude = matchedExternal.length > 0 ? matchedExternal : otherEntities.slice(0, 5);

    if (activeEntitiesToInclude.length > 0) {
      entityContextBlock = "\nPeople & Things in their life (Entities):\n" +
        activeEntitiesToInclude.map((e) => `- ${e.name} (${e.relationship ?? "No relation specified"}): ${e.last_known_fact ?? ""}`).join("\n") + "\n";
    }
  }

  // 3. Deep Memory Retrieval & Re-ranking (§8)
  let deepMemoryBlock = "";
  const geminiKey = Deno.env.get("GEMINI_API_KEY");

  if (geminiKey) {
    try {
      const embedResponse = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=${geminiKey}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            model: "models/gemini-embedding-001",
            content: { parts: [{ text: messageText }] },
            outputDimensionality: 768,
          }),
        }
      );

      if (embedResponse.ok) {
        const embedData = await embedResponse.json();
        const usage = embedData?.usageMetadata || {};
        logEvent("embedding.generate", {
          owner_id: ownerId,
          model: "gemini-embedding-001",
          provider: "gemini",
          prompt_tokens: usage.promptTokenCount ?? 0,
          completion_tokens: 0,
          total_tokens: usage.totalTokenCount ?? usage.promptTokenCount ?? 0,
        });

        const vector = embedData?.embedding?.values;

        if (vector && Array.isArray(vector) && vector.length === 768) {
          const { data: memories } = await supabase.rpc("match_friday_memories", {
            query_embedding: vector,
            user_owner_id: ownerId,
            match_threshold: 0.0,
            match_count: 15,
          });

          if (memories && memories.length > 0) {
            const memIds = memories.map((m: any) => m.id);
            const { data: fullMemories } = await supabase
              .from("friday_memories")
              .select("id, importance, created_at, last_reinforced_at, status")
              .in("id", memIds)
              .eq("status", "active");

            const activeMap = new Map((fullMemories || []).map((fm: any) => [fm.id, fm]));

            const scoredCandidates = memories
              .filter((m: any) => activeMap.has(m.id))
              .map((m: any) => {
                const fm = activeMap.get(m.id);
                const lastRef = fm.last_reinforced_at || fm.created_at || new Date().toISOString();
                const recencyDays = (Date.now() - new Date(lastRef).getTime()) / (1000 * 60 * 60 * 24);
                const recencyScore = Math.exp(-Math.max(0, recencyDays) / 30);
                const importanceScore = (fm.importance || 3) / 5;
                const vectorScore = m.similarity;
                const finalScore = 0.6 * vectorScore + 0.25 * importanceScore + 0.15 * recencyScore;
                return {
                  memory_text: m.memory_text,
                  finalScore,
                };
              });

            scoredCandidates.sort((a: any, b: any) => b.finalScore - a.finalScore);
            const top6 = scoredCandidates.slice(0, 6);

            if (top6.length > 0) {
              deepMemoryBlock = "\nRelevant Memory Context:\n" +
                top6.map((m: any) => `- ${m.memory_text}`).join("\n") + "\n";
            }
          }
        }
      }

      // Fallback: If vector block is still empty, pull top 5 active memories as baseline
      if (!deepMemoryBlock) {
        const { data: baselineMemories } = await supabase
          .from("friday_memories")
          .select("memory_text")
          .eq("owner_id", ownerId)
          .eq("status", "active")
          .order("importance", { ascending: false })
          .order("created_at", { ascending: false })
          .limit(5);

        if (baselineMemories && baselineMemories.length > 0) {
          deepMemoryBlock = "\nKey Facts You Remember About Them:\n" +
            baselineMemories.map((m: any) => `- ${m.memory_text}`).join("\n") + "\n";
        }
      }
    } catch (err) {
      console.error("Deep memory retrieval degraded safely:", err.message);
    }
  }

  // 4. Temporal Context Injection
  let temporalBlock = "";
  if (meta) {
    const isReentry = (meta.timeGapMinutes ?? 0) > 15;
    const flowStatus = isReentry ? "New Session / Re-entry" : "Ongoing Conversation Flow";
    temporalBlock = `\n[Temporal Context]:
- Current Time: ${meta.currentTime}
- Silence Duration: ${meta.timeGapMinutes} minutes
- Conversation State: ${flowStatus}\n`;
  }

  // 5. Emotional Vibe Injection
  const { data: profile } = await supabase
    .from("profiles")
    .select("friday_vibe")
    .eq("id", ownerId)
    .single();
  const currentVibe = profile?.friday_vibe || "chilled";
  const vibeBlock = `\n[Current Emotional State]: You are currently feeling ${currentVibe}. Let this color your tone.\n`;

  // 6. Action Execution Note Injection (if any)
  const actionBlock = meta?.actionNote ? `\n${meta.actionNote}\n` : "";

  // 7. Final Assembly
  const basePersonality = getPersonalityBlock(userPreferredName);
  const systemPrompt = `${basePersonality}${temporalBlock}${vibeBlock}${sessionSummaryBlock}${identityContextBlock}${entityContextBlock}${deepMemoryBlock}${actionBlock}`;

  return {
    systemPrompt,
    chatHistory,
  };
}
