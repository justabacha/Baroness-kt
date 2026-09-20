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
    const userMessageId = record.id;

    // 2. Setup Supabase Client
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    // 2.1 IMMEDIATE FEEDBACK: Tell the phone to show typing dots NOW
    // We do this at the absolute top of the processing to minimize perceived latency
    await supabase.from("chat_sync_pipe").insert({
      recipient_id: ownerId,
      payload: {
        type: "START_TYPING",
        conversationId: "friday",
      },
    });

    // 2.2 IDEMPOTENCY LOCK: Use the unique constraint on reply_to_id
    // We insert a placeholder message immediately to "claim" this request.
    const placeholderId = crypto.randomUUID();
    const { error: lockError } = await supabase
      .from("friday_messages")
      .insert({
        id: placeholderId,
        owner_id: ownerId,
        sender: "friday",
        message: "...", // Placeholder
        reply_to_id: userMessageId,
        status: "THINKING"
      });

    if (lockError) {
      console.log(`Could not acquire lock for message ${userMessageId}: ${lockError.message}`);
      return new Response("Duplicate or conflict ignored", { status: 200 });
    }

    // 2.3 Manage Session ID
    const { data: lastMsg } = await supabase
      .from("friday_messages")
      .select("session_id, created_at")
      .eq("owner_id", ownerId)
      .neq("id", placeholderId) // Don't count our own placeholder
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

    // 2.3 Update the current incoming message and our placeholder with the session_id
    await Promise.all([
      supabase.from("friday_messages").update({ session_id: sessionId }).eq("id", userMessageId),
      supabase.from("friday_messages").update({ session_id: sessionId }).eq("id", placeholderId)
    ]);

    // 3. Phase A: Intent Classification
    const classification = await classifyMessage(userMessage);

    if (classification.classification === "COMMAND") {
      // FAST PATH: Direct command execution
      const acknowledgment = "On it";

      // Update placeholder with acknowledgment
      await supabase.from("friday_messages")
        .update({
          message: acknowledgment,
          status: "SENT"
        })
        .eq("id", placeholderId);

      const commandPayload = {
        type: "COMMAND",
        messageId: placeholderId,
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

    // 4.4 Update placeholder with the final message
    await supabase.from("friday_messages")
      .update({
        message: text,
        status: "SENT"
      })
      .eq("id", placeholderId);

    // 4.5 Push to chat_sync_pipe for Android Realtime Sync
    const pipePayload = {
      type: "NEW_MESSAGE",
      messageId: placeholderId,
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
