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

  const prompt = `You are a fast intent classifier for a personal companion app. Classify the user's
message as exactly one of: COMMAND or CONVERSATION.

COMMAND means the message is a direct, actionable request the app can execute right
now, such as: playing music, navigation/directions, setting a timer or alarm.

CONVERSATION means anything else, including greetings, questions, venting, planning,
or ambiguous phrasing that could be conversational.

If you are not highly confident it is a COMMAND, classify it as CONVERSATION.

Respond with ONLY a JSON object, no other text:
{"classification": "COMMAND" | "CONVERSATION", "intent": string | null, "parameters": object | null, "confidence": number}

Examples:
"play some jazz" -> {"classification":"COMMAND","intent":"play_music","parameters":{"genre":"jazz"},"confidence":0.95}
"navigate home" -> {"classification":"COMMAND","intent":"navigate","parameters":{"destination":"home"},"confidence":0.97}
"set a timer for 10 minutes" -> {"classification":"COMMAND","intent":"set_timer","parameters":{"minutes":10},"confidence":0.98}
"I think I'm going to quit my job" -> {"classification":"CONVERSATION","intent":null,"parameters":null,"confidence":0.99}

User message: "${message.replace(/"/g, '\\"')}"`;

  try {
    const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "openai/gpt-oss-20b", // Switched to your supported high-speed model
        messages: [{ role: "user", content: prompt }],
        temperature: 0.1,
        response_format: { type: "json_object" },
      }),
    });

    if (!response.ok) {
      throw new Error(`Groq API returned status ${response.status}`);
    }

    const data = await response.json();
    const resultText = data.choices?.[0]?.message?.content;
    if (!resultText) throw new Error("Empty response from Groq");

    const parsed: ClassificationResult = JSON.parse(resultText);

    // Hardcoded confidence threshold override to protect conversation intimacy
    if (parsed.classification === "COMMAND" && parsed.confidence < 0.7) {
      return { classification: "CONVERSATION", intent: null, parameters: null, confidence: 1.0 };
    }

    return parsed;
  } catch (err) {
    console.error("Classifier failed, defaulting to CONVERSATION:", err.message);
    return { classification: "CONVERSATION", intent: null, parameters: null, confidence: 1.0 };
  }
}
