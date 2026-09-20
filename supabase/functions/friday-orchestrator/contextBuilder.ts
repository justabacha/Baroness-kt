import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { getPersonalityBlock } from "./personality.ts";

export interface AssembledContext {
  systemPrompt: string;
  chatHistory: { role: "user" | "assistant"; content: string }[];
}

export async function buildContext(
  supabase: SupabaseClient,
  ownerId: string,
  messageText: string
): Promise<AssembledContext> {
  // 1. Fetch Working Memory (Last 20 messages from friday_messages)
  const { data: messages } = await supabase
    .from("friday_messages")
    .select("sender, message")
    .eq("owner_id", ownerId)
    .order("created_at", { ascending: false })
    .limit(20);

  const chatHistory = (messages ?? [])
    .reverse()
    .map((m) => ({
      role: m.sender === "friday" ? ("assistant" as const) : ("user" as const),
      content: m.message,
    }));

  // 2. Entity Lookup ("Who's Who")
  const { data: entities } = await supabase
    .from("friday_entities")
    .select("name, aliases, relationship, last_known_fact")
    .eq("owner_id", ownerId);

  let entityContextBlock = "";
  if (entities && entities.length > 0) {
    const matchedEntities = entities.filter((entity) => {
      const lowerMsg = messageText.toLowerCase();
      const nameMatch = entity.name && lowerMsg.includes(entity.name.toLowerCase());
      const aliasMatch = Array.isArray(entity.aliases) && entity.aliases.some((alias: string) =>
        lowerMsg.includes(alias.toLowerCase())
      );
      return nameMatch || aliasMatch;
    });

    if (matchedEntities.length > 0) {
      entityContextBlock = "\nKnown people/things in the user's life relevant to this conversation:\n" +
        matchedEntities.map((e) => `- ${e.name} (${e.relationship ?? "No relation specified"}): ${e.last_known_fact ?? ""}`).join("\n") + "\n";
    }
  }

  // 3. Deep Memory Retrieval & Tea Test
  let deepMemoryBlock = "";
  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  if (geminiKey) {
    try {
      // Fetch text embedding via Gemini API
      const embedResponse = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=${geminiKey}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            model: "models/text-embedding-004",
            content: { parts: [{ text: messageText }] },
          }),
        }
      );

      if (embedResponse.ok) {
        const embedData = await embedResponse.json();
        const vector = embedData?.embedding?.values;

        if (vector && Array.isArray(vector) && vector.length === 768) {
          // Query pgvector RPC matching function
          const { data: memories } = await supabase.rpc("match_friday_memories", {
            query_embedding: vector,
            user_owner_id: ownerId,
            match_threshold: 0.75,
            match_count: 5,
          });

          if (memories && memories.length > 0) {
            // Apply Tea Test Step 3: keyword relevance filtering
            const filteredMemories = memories.filter((m: any) => {
              const category = m.category;
              if (category === "preference" || category === "recurring_pattern") {
                return true; // Keep preferences and standing patterns liberally
              }
              // For situational emotional/life events, enforce light keyword overlap
              const lowerText = m.memory_text.toLowerCase();
              const words = messageText.toLowerCase().split(/\s+/);
              return words.some((word) => word.length > 3 && lowerText.includes(word));
            });

            if (filteredMemories.length > 0) {
              deepMemoryBlock = "\nRelevant things you remember:\n" +
                filteredMemories.map((m: any) => `- ${m.memory_text}`).join("\n") + "\n";
            }
          }
        }
      }
    } catch (err) {
      console.error("Deep memory retrieval degraded safely:", err.message);
    }
  }

  // 4. Assemble Final Combined Prompt
  const basePersonality = getPersonalityBlock();
  const systemPrompt = `${basePersonality}${entityContextBlock}${deepMemoryBlock}`;

  return {
    systemPrompt,
    chatHistory,
  };
}
