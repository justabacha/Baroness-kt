import { AssembledContext } from "./contextBuilder.ts";
import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { checkAndIncrement } from "../_shared/rate-limiter.ts";
import { logEvent } from "../_shared/logger.ts";

export async function generateReply(
  context: AssembledContext,
  currentMessage: string,
  supabase?: SupabaseClient,
  ownerId?: string
): Promise<{ text: string; providerUsed: "gemini" | "groq" | "none" }> {
  const startTime = Date.now();
  const groqKey = Deno.env.get("GROQ_API_KEY");
  const geminiKey = Deno.env.get("GEMINI_API_KEY");

  // 1. PRIMARY: Try Groq with validated configuration settings
  if (groqKey) {
    try {
      const messages = [
        { role: "system", content: context.systemPrompt },
        ...context.chatHistory.map((h) => ({ role: h.role, content: h.content })),
        { role: "user", content: currentMessage },
      ];

      console.log("Routing live query to Groq Primary engine...");
      const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${groqKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: "openai/gpt-oss-120b",
          messages,
          temperature: 0.6,
          max_tokens: 1024,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        const text = data.choices?.[0]?.message?.content;
        const usage = data.usage || {};
        if (text) {
          const latency = Date.now() - startTime;
          logEvent("chat.reply", {
            owner_id: ownerId,
            model: "openai/gpt-oss-120b",
            provider: "groq",
            latency_ms: latency,
            success: true,
            prompt_tokens: usage.prompt_tokens ?? 0,
            completion_tokens: usage.completion_tokens ?? 0,
            total_tokens: usage.total_tokens ?? 0,
          });
          return { text: text.trim(), providerUsed: "groq" };
        }
      } else {
        const errText = await response.text();
        console.error(`Groq Primary returned error status ${response.status}: ${errText}`);
      }
    } catch (err) {
      console.error("Groq Primary exception encountered:", err.message);
    }
  }

  // 2. FALLBACK: Try Gemini if Groq failed, but check rate limits first
  if (geminiKey) {
    if (supabase && ownerId) {
      const rateLimit = await checkAndIncrement(supabase, ownerId, "gemini");
      if (!rateLimit.allowed) {
        console.warn(`Gemini rate limit exceeded for owner ${ownerId}. Skipping Gemini fallback.`);
        logEvent("rate_limit.blocked", { owner_id: ownerId, provider: "gemini" });
        return {
          text: "Hey, having a little trouble thinking straight right now — give me a second and let's try again?",
          providerUsed: "none",
        };
      }
    }

    try {
      console.log("Groq primary missed, initiating Gemini fallback loop...");
      const contents = context.chatHistory.map((h) => ({
        role: h.role === "assistant" ? "model" : "user",
        parts: [{ text: h.content }],
      }));

      contents.push({
        role: "user",
        parts: [{ text: currentMessage }],
      });

      const response = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${geminiKey}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            systemInstruction: {
              parts: [{ text: context.systemPrompt }],
            },
            contents,
            generationConfig: {
              maxOutputTokens: 150,
              temperature: 0.7,
            },
          }),
        }
      );

      if (response.ok) {
        const data = await response.json();
        const text = data.candidates?.[0]?.content?.parts?.[0]?.text;
        const usage = data.usageMetadata || {};
        if (text) {
          const latency = Date.now() - startTime;
          logEvent("chat.reply", {
            owner_id: ownerId,
            model: "gemini-1.5-flash",
            provider: "gemini",
            latency_ms: latency,
            success: true,
            prompt_tokens: usage.promptTokenCount ?? 0,
            completion_tokens: usage.candidatesTokenCount ?? 0,
            total_tokens: usage.totalTokenCount ?? 0,
          });
          return { text: text.trim(), providerUsed: "gemini" };
        }
      }
    } catch (err) {
      console.error("Gemini fallback loop exception:", err.message);
    }
  }

  // 3. Static fallback if both failed
  logEvent("chat.reply", {
    owner_id: ownerId,
    model: "none",
    provider: "none",
    latency_ms: Date.now() - startTime,
    success: false,
    prompt_tokens: 0,
    completion_tokens: 0,
    total_tokens: 0,
  });

  return {
    text: "Hey, having a little trouble thinking straight right now — give me a second and let's try again?",
    providerUsed: "none",
  };
}
