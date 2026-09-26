import { validateAction } from "../_shared/action-validator.ts";
import { logEvent } from "../_shared/logger.ts";

export interface ClassificationAction {
  intent: string;
  parameters: Record<string, unknown>;
}

export interface ClassificationResult {
  classification: "COMMAND" | "CONVERSATION";
  actions: ClassificationAction[];
  intent: string | null;
  parameters: Record<string, unknown> | null;
  confidence: number;
}

export async function classifyMessage(message: string): Promise<ClassificationResult> {
  const apiKey = Deno.env.get("GROQ_API_KEY");
  if (!apiKey) {
    console.error("Missing GROQ_API_KEY environment variable.");
    return { classification: "CONVERSATION", actions: [], intent: null, parameters: null, confidence: 1.0 };
  }

  const prompt = `You are a fast, precise intent classifier for a personal AI companion app named Friday. Classify the user's message as exactly one of: COMMAND or CONVERSATION.

COMMAND means an EXPLICIT, IMMEDIATE imperative order for the app to control the phone right now.
Supported commands:
- "play_music": playing music or media ("play Alie Gatie Can't Lie", "play some jazz", "play Aslay")
  Parameters for play_music: "query" (song/artist/genre search term), "app" (target app: "youtube").
- "pause_media": pausing current music or video ("pause", "pause music", "stop music")
- "resume_media": resuming music or playback ("resume", "continue playing", "unpause")
- "next_track": skipping to next track ("skip", "next song", "next track")
- "previous_track": going back to previous track ("previous song", "go back")
- "set_volume": setting media volume level ("set volume to 80", "volume 50%")
  Parameters for set_volume: "level" (number 0..100).
- "volume_up": turning up volume ("volume up", "louder", "turn it up")
- "volume_down": turning down volume ("volume down", "quieter", "turn it down")
- "mute": muting audio ("mute", "silence")
- "unmute": unmuting audio ("unmute")
- "navigate": directions/navigation ("navigate home", "take me to the airport")
- "set_timer": setting a countdown timer ("set a timer for 10 minutes")
- "set_alarm": setting an alarm clock ("set an alarm for 7am")

MULTI-COMMAND CHAINING: If the user message contains multiple sequential commands (e.g. "resume the music and play the next song" or "set volume to 80 and play lofi"), include ALL corresponding command actions in sequence inside the "actions" array!

CONVERSATION means anything else, including:
- Mentions of sports or games ("I love playing football", "let's play a game")
- Questions and inquiries ("who are you?", "what is your name?", "can you play music?")
- Questions or general banter ("I want to listen to music later", "what time is it?")
If you are not 100% sure the user is giving a COMMAND, classify it as CONVERSATION.

Respond with ONLY a JSON object:
{"classification": "COMMAND" | "CONVERSATION", "actions": [{"intent": string, "parameters": object}], "confidence": number}

Examples:
"resume the music and play the next song" -> {"classification":"COMMAND","actions":[{"intent":"resume_media","parameters":{}},{"intent":"next_track","parameters":{}}],"confidence":0.98}
"set volume to 80 and play lofi" -> {"classification":"COMMAND","actions":[{"intent":"set_volume","parameters":{"level":80}},{"intent":"play_music","parameters":{"query":"lofi","app":"youtube"}}],"confidence":0.98}
"play Alie Gatie Can't Lie" -> {"classification":"COMMAND","actions":[{"intent":"play_music","parameters":{"query":"Alie Gatie Can't Lie","app":"youtube"}}],"confidence":0.98}
"pause the song" -> {"classification":"COMMAND","actions":[{"intent":"pause_media","parameters":{}}],"confidence":0.98}
"what is your name?" -> {"classification":"CONVERSATION","actions":[],"confidence":0.99}
"Polo G is fire, we should roll with his track" -> {"classification":"CONVERSATION","actions":[],"confidence":0.99}
"I play basketball and football" -> {"classification":"CONVERSATION","actions":[],"confidence":0.99}
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

    const parsed = JSON.parse(resultText);

    if (parsed.classification === "COMMAND" && parsed.confidence < 0.7) {
      return { classification: "CONVERSATION", actions: [], intent: null, parameters: null, confidence: 1.0 };
    }

    if (parsed.classification === "COMMAND") {
      const rawActions = Array.isArray(parsed.actions) ? parsed.actions : [];
      if (rawActions.length === 0 && parsed.intent) {
        rawActions.push({ intent: parsed.intent, parameters: parsed.parameters || {} });
      }

      const validActions: ClassificationAction[] = [];
      for (const act of rawActions) {
        const validation = validateAction({
          type: "COMMAND",
          intent: act.intent ?? undefined,
          parameters: act.parameters ?? undefined,
        });

        if (validation.valid && validation.action) {
          validActions.push({
            intent: validation.action.intent,
            parameters: validation.action.parameters,
          });
        }
      }

      if (validActions.length === 0) {
        return { classification: "CONVERSATION", actions: [], intent: null, parameters: null, confidence: 1.0 };
      }

      return {
        classification: "COMMAND",
        actions: validActions,
        intent: validActions[0].intent,
        parameters: validActions[0].parameters,
        confidence: parsed.confidence,
      };
    }

    return {
      classification: "CONVERSATION",
      actions: [],
      intent: null,
      parameters: null,
      confidence: parsed.confidence ?? 1.0,
    };
  } catch (err) {
    console.error("Classifier failed, defaulting to CONVERSATION:", err.message);
    return { classification: "CONVERSATION", actions: [], intent: null, parameters: null, confidence: 1.0 };
  }
}
