-- Baroness-kt Supabase Schema (Generated via Node Bridge)


-- Table: backup_log
--   persona_id: text (NOT NULL)
--   last_backup_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   message_count_at_backup: integer (NULLABLE, DEFAULT 0)

-- Table: chat_sync_pipe
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   recipient_id: text (NOT NULL)
--   payload: jsonb (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: friday_memories
--   id: bigint (NOT NULL)
--   owner_id: text (NOT NULL)
--   memory_text: text (NOT NULL)
--   emotion_tag: text (NULLABLE, DEFAULT 'soft'::text)
--   is_pinned: boolean (NULLABLE, DEFAULT false)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: friday_messages
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   owner_id: text (NOT NULL)
--   sender: text (NOT NULL)
--   message: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: gallery_items
--   id: bigint (NOT NULL)
--   image_url: text (NOT NULL)
--   uploaded_by: text (NOT NULL)
--   linked_wish_id: bigint (NULLABLE)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: messages
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   conversation_id: text (NOT NULL)
--   sender_id: text (NOT NULL)
--   receiver_id: text (NOT NULL)
--   content: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   read_at: timestamp with time zone (NULLABLE)
--   is_deleted: boolean (NULLABLE, DEFAULT false)
--   reactions: jsonb (NULLABLE, DEFAULT '{}'::jsonb)

-- Table: migration_history
--   id: integer (NOT NULL, DEFAULT nextval('migration_history_id_seq'::regclass))
--   name: text (NOT NULL)
--   applied_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: profiles
--   id: text (NOT NULL)
--   display_name: text (NOT NULL)
--   persona: text (NULLABLE, DEFAULT 'Phesty'::text)
--   updated_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   avatar_url: text (NULLABLE)
--   fcm_token: text (NULLABLE)

-- Table: typing_status
--   user_id: text (NOT NULL)
--   chat_partner_id: text (NOT NULL)
--   is_typing: boolean (NULLABLE, DEFAULT false)
--   updated_at: timestamp without time zone (NULLABLE, DEFAULT now())

-- Table: wishlist_items
--   id: bigint (NOT NULL)
--   text: text (NOT NULL)
--   wish_date: date (NOT NULL)
--   status: text (NOT NULL, DEFAULT 'planning'::text)
--   creator_id: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   updated_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: wishlist_ratings
--   id: bigint (NOT NULL)
--   wish_id: bigint (NOT NULL)
--   persona_id: text (NOT NULL)
--   rating: integer (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: wishlist_reactions
--   id: bigint (NOT NULL)
--   wish_id: bigint (NOT NULL)
--   persona_id: text (NOT NULL)
--   emoji: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())


-- RLS Policies
-- Policy: Allow public access to profiles on profiles (ALL)
-- Policy: Enable all for current_user on profiles (ALL)
-- Policy: Public profiles are viewable by everyone on profiles (SELECT)
-- Policy: Users can insert or update their own profile on profiles (ALL)
-- Policy: wishlist_items_select on wishlist_items (SELECT)
-- Policy: wishlist_items_insert on wishlist_items (INSERT)
-- Policy: wishlist_items_update on wishlist_items (UPDATE)
-- Policy: wishlist_items_delete on wishlist_items (DELETE)
-- Policy: wishlist_reactions_select on wishlist_reactions (SELECT)
-- Policy: wishlist_reactions_insert on wishlist_reactions (INSERT)
-- Policy: wishlist_reactions_update on wishlist_reactions (UPDATE)
-- Policy: wishlist_reactions_delete on wishlist_reactions (DELETE)
-- Policy: wishlist_ratings_select on wishlist_ratings (SELECT)
-- Policy: wishlist_ratings_insert on wishlist_ratings (INSERT)
-- Policy: wishlist_ratings_update on wishlist_ratings (UPDATE)
-- Policy: wishlist_ratings_delete on wishlist_ratings (DELETE)
-- Policy: gallery_items_select on gallery_items (SELECT)
-- Policy: gallery_items_insert on gallery_items (INSERT)
-- Policy: gallery_items_update on gallery_items (UPDATE)
-- Policy: gallery_items_delete on gallery_items (DELETE)
-- Policy: Allow read memories on friday_memories (SELECT)
-- Policy: Allow insert memories on friday_memories (INSERT)
-- Policy: Allow update memories on friday_memories (UPDATE)
-- Policy: Allow delete memories on friday_memories (DELETE)
-- Policy: Allow all operations on typing_status on typing_status (ALL)
-- Policy: Users can access their own messages on messages (ALL)
-- Policy: Users can access their AI messages on friday_messages (ALL)
-- Policy: Users can only see their own mailbox on chat_sync_pipe (ALL)
-- Policy: Users can manage their own backup log on backup_log (ALL)
