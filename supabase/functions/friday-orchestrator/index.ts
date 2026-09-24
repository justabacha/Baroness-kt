import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { classifyMessage } from "./classifier.ts";
import { buildContext } from "./contextBuilder.ts";
import { generateReply } from "./llmRouter.ts";
import { computeDelay, isEmotionallyWeighted } from "./delay.ts";
import { validateAction } from "../_shared/action-validator.ts";
import { logEvent } from "../_shared/logger.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }

  let supabase: ReturnType<typeof createClient> | null = null;
  let placeholderId: string | null = null;

  try {
    const { record, event } = await req.json();

    if (event !== "INSERT" || record.sender === "friday") {
      return new Response("Event ignored", { status: 200 });
    }

    const ownerId = record.owner_id;
    const userMessage = record.message;
    const userMessageId = record.id;

    supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    // 2.1 Typing indicator
    const { error: typingError } = await supabase.from("chat_sync_pipe").insert({
      recipient_id: ownerId,
      payload: {
        type: "START_TYPING",
        conversationId: "friday",
      },
    });
    if (typingError) throw typingError;

    // 2.2 Idempotency lock
    placeholderId = crypto.randomUUID();
    const { error: lockError } = await supabase
      .from("friday_messages")
      .insert({
        id: placeholderId,
        owner_id: ownerId,
        sender: "friday",
        message: "...",
        reply_to_id: userMessageId,
        status: "THINKING"
      });

    if (lockError) {
      console.log(`Could not acquire lock for message ${userMessageId}: ${lockError.message}`);
      return new Response("Duplicate or conflict ignored", { status: 200 });
    }

    // 2.3 Manage Session ID & Session Boundary (§5)
    const { data: lastSession } = await supabase
      .from("friday_sessions")
      .select("id, started_at")
      .eq("owner_id", ownerId)
      .eq("status", "active")
      .order("started_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    const { data: lastMsg } = await supabase
      .from("friday_messages")
      .select("created_at")
      .eq("owner_id", ownerId)
      .neq("id", placeholderId)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    const now = Date.now();
    const lastTime = lastMsg ? new Date(lastMsg.created_at).getTime() : 0;
    const idleLimit = 30 * 60 * 1000; // 30 mins
    const timeGapMinutes = lastMsg ? Math.round((now - lastTime) / 60000) : 0;

    let sessionId: string;

    if (!lastSession || (lastMsg && (now - lastTime) > idleLimit)) {
      if (lastSession) {
        await supabase
          .from("friday_sessions")
          .update({ status: "ended", ended_at: new Date().toISOString() })
          .eq("id", lastSession.id);
      }
      const { data: newSession, error: newSessErr } = await supabase
        .from("friday_sessions")
        .insert({ owner_id: ownerId, status: "active" })
        .select("id")
        .single();

      if (newSessErr || !newSession) throw newSessErr || new Error("Failed to create session");
      sessionId = newSession.id;
    } else {
      sessionId = lastSession.id;
    }

    const sessionUpdates = await Promise.all([
      supabase.from("friday_messages").update({ session_id: sessionId }).eq("id", userMessageId),
      supabase.from("friday_messages").update({ session_id: sessionId }).eq("id", placeholderId)
    ]);
    const sessionError = sessionUpdates.find((result) => result.error)?.error;
    if (sessionError) throw sessionError;

    // 3. Intent Classification
    const classification = await classifyMessage(userMessage);
    let actionNote: string | undefined = undefined;

    if (classification.classification === "COMMAND") {
      const { error: actionReqError } = await supabase
        .from("friday_action_requests")
        .insert({
          owner_id: ownerId,
          session_id: sessionId,
          action_name: classification.intent!,
          parameters: classification.parameters ?? {},
          status: "dispatched",
        });
      if (actionReqError) {
        console.error("Failed to record action request:", actionReqError.message);
      }

      logEvent("chat.action_dispatched", {
        owner_id: ownerId,
        session_id: sessionId,
        action_name: classification.intent!,
      });

      const commandPayload = {
        type: "COMMAND",
        messageId: placeholderId,
        intent: classification.intent,
        parameters: classification.parameters,
      };

      const { error: commandPipeError } = await supabase.from("chat_sync_pipe").insert({
        recipient_id: ownerId,
        payload: commandPayload,
      });
      if (commandPipeError) throw commandPipeError;

      const actName = classification.intent;
      const params = classification.parameters || {};

      if (actName === "play_music") {
        const queryStr = params.query ? ` (${params.query})` : "";
        actionNote = `[System Note]: You just triggered the phone to play music${queryStr}. Vibe back casually as a friend (e.g. "got u mate, lemme play u some bangers", "putting on some tunes for u"). Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "pause_media" || actName === "stop_media") {
        actionNote = `[System Note]: You just paused the music/media. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "resume_media") {
        actionNote = `[System Note]: You resumed playback. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "next_track") {
        actionNote = `[System Note]: You skipped to the next song. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "previous_track") {
        actionNote = `[System Note]: You went back to the previous track. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "set_volume") {
        actionNote = `[System Note]: You set the volume level to ${params.level || 50}%. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "volume_up") {
        actionNote = `[System Note]: You turned the volume up. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "volume_down") {
        actionNote = `[System Note]: You turned the volume down. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "mute") {
        actionNote = `[System Note]: You muted the audio. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "unmute") {
        actionNote = `[System Note]: You unmuted the audio. Confirm casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "navigate") {
        const dest = params.destination || "their destination";
        actionNote = `[System Note]: You just opened navigation to ${dest} on their phone. Confirm it casually as a friend (e.g. "got u, opening maps to ${dest} now"). Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "set_timer") {
        const mins = params.minutes || "";
        actionNote = `[System Note]: You just set a timer for ${mins} minutes on their phone. Confirm it casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else if (actName === "set_alarm") {
        const h = params.hour !== undefined ? params.hour : "";
        const m = params.minute !== undefined ? String(params.minute).padStart(2, "0") : "00";
        actionNote = `[System Note]: You just set an alarm for ${h}:${m} on their phone. Confirm it casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      } else {
        actionNote = `[System Note]: Action '${actName}' was triggered on their phone. Confirm it casually as a friend. Speak in plain casual text. Do NOT output any JSON or code.`;
      }
    }

    // 4. Conversation & Response Path
    const context = await buildContext(supabase, ownerId, userMessage, userMessageId, {
      timeGapMinutes,
      currentTime: new Date().toLocaleString('en-GB', {
        weekday: 'long',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
      }),
      actionNote
    });

    let { text } = await generateReply(context, userMessage, supabase, ownerId);

    // 4.1 Parse and dispatch conversational Action Tags if LLM included an [[ACTION: ...]] tag
    const actionTagMatch = text.match(/\[\[ACTION:\s*(\{[\s\S]*?\})\s*\]\]/i);
    if (actionTagMatch && classification.classification !== "COMMAND") {
      try {
        const parsedAction = JSON.parse(actionTagMatch[1]);
        const validation = validateAction({
          type: "COMMAND",
          intent: parsedAction.intent,
          parameters: parsedAction.parameters,
        });

        if (validation.valid && validation.action) {
          const commandPayload = {
            type: "COMMAND",
            messageId: placeholderId,
            intent: validation.action.intent,
            parameters: validation.action.parameters,
          };

          await supabase.from("chat_sync_pipe").insert({
            recipient_id: ownerId,
            payload: commandPayload,
          });

          await supabase.from("friday_action_requests").insert({
            owner_id: ownerId,
            session_id: sessionId,
            action_name: validation.action.intent,
            parameters: validation.action.parameters ?? {},
            status: "dispatched",
          });

          logEvent("chat.llm_action_dispatched", {
            owner_id: ownerId,
            session_id: sessionId,
            action_name: validation.action.intent,
          });
        }
      } catch (err) {
        console.error("Failed to parse LLM action tag:", err.message);
      }
    }

    // Clean text by stripping action tag and any stray raw JSON
    text = text.replace(/\[\[ACTION:[\s\S]*?\]\]/gi, "").trim();
    text = text.replace(/```(?:json)?[\s\S]*?```/gi, "").replace(/\{[\s\S]*?\}/g, "").trim();
    if (!text) {
      text = "got u mate, taken care of.";
    }

    const typingDurationMs = computeDelay({
      replyText: text,
      isCommand: classification.classification === "COMMAND",
      isEmotionallyWeighted: isEmotionallyWeighted(userMessage)
    });

    const { error: replyUpdateError } = await supabase.from("friday_messages")
      .update({
        message: text,
        status: "SENT",
        is_command: classification.classification === "COMMAND"
      })
      .eq("id", placeholderId);
    if (replyUpdateError) throw replyUpdateError;

    const pipePayload = {
      type: "NEW_MESSAGE",
      messageId: placeholderId,
      conversationId: "friday",
      senderId: "friday",
      content: text,
      timestamp: Date.now(),
      typing_duration_ms: typingDurationMs,
    };

    const { error: replyPipeError } = await supabase.from("chat_sync_pipe").insert({
      recipient_id: ownerId,
      payload: pipePayload,
    });
    if (replyPipeError) throw replyPipeError;

    return new Response(JSON.stringify({ status: "reply_sent", text }), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });

  } catch (error) {
    console.error("Orchestrator critical error:", error);
    if (supabase && placeholderId) {
      const { error: failureUpdateError } = await supabase
        .from("friday_messages")
        .update({
          message: "I hit a snag while processing that. Please try again.",
          status: "FAILED"
        })
        .eq("id", placeholderId);
      if (failureUpdateError) {
        console.error("Failed to persist orchestrator failure state:", failureUpdateError);
      }
    }
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { "Content-Type": "application/json" },
      status: 500,
    });
  }
});
