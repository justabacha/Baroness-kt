import { AssembledContext } from "./contextBuilder.ts";

export async function generateReply(
  context: AssembledContext,
  currentMessage: string
): Promise<{ text: string; providerUsed: "gemini" | "groq" }> {
  const groqKey = Deno.env.get("GROQ_API_KEY");
  const geminiKey = Deno.env.get("GEMINI_API_KEY");

  // 1. PRIMARY: Try Groq with your validated configuration settings
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
          model: "openai/gpt-oss-120b", // Switched back to your validated model
          messages,
          temperature: 0.6,
          max_tokens: 1024,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        const text = data.choices?.[0]?.message?.content;
        if (text) {
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

  // 2. FALLBACK: Try Gemini if Groq keys hit rate limits or quotas
  if (geminiKey) {
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
        if (text) {
          return { text: text.trim(), providerUsed: "gemini" };
        }
      }
    } catch (err) {
      console.error("Gemini fallback loop exception:", err.message);
    }
  }

  // 3. LLM_UNAVAILABLE Safe State Recovery (Spec 03 §5)
  return {
    text: "Hey, having a little trouble thinking straight right now — give me a second and let's try again?",
    providerUsed: "groq",
  };
}
