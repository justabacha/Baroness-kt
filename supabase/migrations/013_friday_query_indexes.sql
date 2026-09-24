-- Friday query indexes for bounded history, reflection, initiative, and pipe recovery.
CREATE INDEX IF NOT EXISTS idx_friday_messages_owner_created_at
    ON public.friday_messages(owner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_friday_messages_owner_reflected_created_at
    ON public.friday_messages(owner_id, reflected_at, created_at ASC)
    WHERE reflected_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_sync_pipe_recipient_created_at
    ON public.chat_sync_pipe(recipient_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_friday_memories_owner_created_at
    ON public.friday_memories(owner_id, created_at DESC);
