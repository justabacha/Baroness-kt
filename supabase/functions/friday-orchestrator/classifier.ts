import { validateAction } from "../_shared/action-validator.ts";
import { logEvent } from "../_shared/logger.ts";

export interface ClassificationResult {
  classification: "COMMAND" | "CONVERSATION";
  intent: string | null;
  parameters: Record<string, unknown> | null;
  confidence: number;
}

export async function classifyMessage(message: string): Promise<ClassificationResult> {
  const apiKey = Deno.env.get("GROQ_API_KEY");
  if (!apiKey) {
    console.error("Missing GROQ_API_KEY environment variable.");
    return { classification: "CONVERSATION", intent: null, parameters: null, confidence: 1.0 };
  }

  const prompt = `You are a fast, precise intent classifier for a personal AI companion app. Classify the user's message as exactly one of: COMMAND or CONVERSATION.

COMMAND means an EXPLICIT, IMMEDIATE imperative order for the app to control the phone right now.
Supported commands:
- "play_music": playing music or media ("play Alie Gatie Can't Lie", "play some jazz", "play Aslay")
  Parameters for play_music: "query" (song/artist/genre search term), "app" (target app: "youtube").
- "navigate": directions/navigation ("navigate home", "take me to the airport")
- "set_timer": setting a countdown timer ("set a timer for 10 minutes")
- "set_alarm": setting an alarm clock ("set an alarm for 7am")

CONVERSATION means anything else, including:
- Mentions of sports or games ("I love playing football", "let's play a game")
- Narrative or past/future statements ("I played music yesterday", "did you play that song?")
- Questions or inquiries ("can you play music?", "how do I set an alarm?")
- Incomplete requests or general banter ("I want to listen to music later", "what time is it?")

If you are not 100% sure it is an immediate phone control command, classify as CONVERSATION.

Respond with ONLY a JSON object:
{"classification": "COMMAND" | "CONVERSATION", "intent": string | null, "parameters": object | null, "confidence": number}

Examples:
"play Alie Gatie Can't Lie" -> {"classification":"COMMAND","intent":"play_music","parameters":{"query":"Alie Gatie Can't Lie","app":"youtube"},"confidence":0.98}
"play Aslay on Spotify" -> {"classification":"COMMAND","intent":"play_music","parameters":{"query":"Aslay","app":"youtube"},"confidence":0.98}
"play some jazz" -> {"classification":"COMMAND","intent":"play_music","parameters":{"query":"jazz","app":"youtube"},"confidence":0.98}
"I played basketball with my friends" -> {"classification":"CONVERSATION","intent":null,"parameters":null,"confidence":0.99}
"Can you set an alarm for 8am?" -> {"classification":"COMMAND","intent":"set_alarm","parameters":{"hour":8,"minute":0},"confidence":0.95}
"I love playing chess" -> {"classification":"CONVERSATION","intent":null,"parameters":null,"confidence":0.99}

User message: "${message.replace(/"/g, '\\"')}"`;

  try {
    const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "openai/gpt-oss-20b",
        messages: [{ role: "user", content: prompt }],
        temperature: 0.1,
        response_format: { type: "json_object" },
      }),
    });

    if (!response.ok) {
      throw new Error(`Groq API returned status ${response.status}`);
    }

    const data = await response.json();
    const usage = data.usage || {};
    logEvent("classifier.intent", {
      model: "openai/gpt-oss-20b",
      provider: "groq",
      prompt_tokens: usage.prompt_tokens ?? 0,
      completion_tokens: usage.completion_tokens ?? 0,
      total_tokens: usage.total_tokens ?? 0,
    });

    const resultText = data.choices?.[0]?.message?.content;
    if (!resultText) throw new Error("Empty response from Groq");

    const parsed: ClassificationResult = JSON.parse(resultText);

    if (parsed.classification === "COMMAND" && parsed.confidence < 0.7) {
      return { classification: "CONVERSATION", intent: null, parameters: null, confidence: 1.0 };
    }

    if (parsed.classification === "COMMAND") {
      const validation = validateAction({
        type: "COMMAND",
        intent: parsed.intent ?? undefined,
        parameters: parsed.parameters ?? undefined,
      });

      if (!validation.valid) {
        logEvent("chat.action_validation_failed", {
          reason: validation.reason,
          action_name: parsed.intent ?? undefined,
        });
        return { classification: "CONVERSATION", intent: null, parameters: null, confidence: 1.0 };
      }

      return {
        classification: "COMMAND",
        intent: validation.action!.intent,
        parameters: validation.action!.parameters,
        confidence: parsed.confidence,
      };
    }

    return parsed;
  } catch (err) {
    console.error("Classifier failed, defaulting to CONVERSATION:", err.message);
    return { classification: "CONVERSATION", intent: null, parameters: null, confidence: 1.0 };
  }
}
