import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { classifyMessage } from "./classifier.ts";
import { buildContext } from "./contextBuilder.ts";
import { generateReply } from "./llmRouter.ts";

serve(async (req) => {
  // Handle preflight requests
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }

  try {
    const { record, event } = await req.json();

    // 1. Filter out Friday's own messages and non-insert events
    if (event !== "INSERT" || record.sender === "friday") {
      return new Response("Event ignored", { status: 200 });
    }

    const ownerId = record.owner_id;
    const userMessage = record.message;

    // 2. Setup Supabase Client
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    // 2.1 IMMEDIATE FEEDBACK: Tell the phone to show typing dots NOW
    await supabase.from("chat_sync_pipe").insert({
      recipient_id: ownerId,
      payload: {
        type: "START_TYPING",
        conversationId: "friday",
      },
    });

    // Small sleep to ensure the Realtime pipe processes START_TYPING before we get busy with AI
    await new Promise(resolve => setTimeout(resolve, 500));

    // 2.2 Manage Session ID
    const { data: lastMsg } = await supabase
      .from("friday_messages")
      .select("session_id, created_at")
      .eq("owner_id", ownerId)
      .order("created_at", { ascending: false })
      .limit(1)
      .single();

    let sessionId = lastMsg?.session_id;
    const now = Date.now();
    const lastTime = lastMsg ? new Date(lastMsg.created_at).getTime() : 0;
    const idleLimit = 30 * 60 * 1000; // 30 mins

    if (!sessionId || (now - lastTime) > idleLimit) {
      sessionId = crypto.randomUUID();
    }

    // 2.2 Update the current incoming message with the session_id
    await supabase
      .from("friday_messages")
      .update({ session_id: sessionId })
      .eq("id", record.id);

    // 3. Phase A: Intent Classification
    const classification = await classifyMessage(userMessage);

    if (classification.classification === "COMMAND") {
      // FAST PATH: Direct command execution
      const acknowledgment = "On it";
      const fridayMsgId = crypto.randomUUID();

      // Save acknowledgment to history
      await supabase.from("friday_messages").insert({
        id: fridayMsgId,
        owner_id: ownerId,
        sender: "friday",
        message: acknowledgment,
        session_id: sessionId,
      });

      const commandPayload = {
        type: "COMMAND",
        messageId: fridayMsgId,
        intent: classification.intent,
        parameters: classification.parameters,
        content: acknowledgment,
        typing_duration_ms: 300,
      };

      await supabase.from("chat_sync_pipe").insert({
        recipient_id: ownerId,
        payload: commandPayload,
      });

      return new Response(JSON.stringify({ status: "command_sent" }), { status: 200 });
    }

    // 4. Phase B: Deep Path (Conversation)
    // 4.1 Assemble Context (History + Entities + Vector Memories)
    const context = await buildContext(supabase, ownerId, userMessage);

    // 4.2 Generate LLM Reply
    const { text } = await generateReply(context, userMessage);

    // 4.3 Compute Typing Delay (Characters per MS)
    const typingDurationMs = Math.round(Math.min(text.length / 0.06, 6000) + 400);

    // 4.4 Save to friday_messages table
    const fridayMsgId = crypto.randomUUID();
    await supabase.from("friday_messages").insert({
      id: fridayMsgId,
      owner_id: ownerId,
      sender: "friday",
      message: text,
      session_id: sessionId,
    });

    // 4.5 Push to chat_sync_pipe for Android Realtime Sync
    const pipePayload = {
      type: "NEW_MESSAGE",
      messageId: fridayMsgId,
      conversationId: "friday",
      senderId: "friday",
      content: text,
      timestamp: Date.now(),
      typing_duration_ms: typingDurationMs,
    };

    await supabase.from("chat_sync_pipe").insert({
      recipient_id: ownerId,
      payload: pipePayload,
    });

    return new Response(JSON.stringify({ status: "reply_sent", text }), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });

  } catch (error) {
    console.error("Orchestrator critical error:", error);
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { "Content-Type": "application/json" },
      status: 500,
    });
  }
});
