-- Baroness-kt Supabase Schema (Generated via Node Bridge)


-- Table: access_keys
--   id: text (NOT NULL)
--   secret_key: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: agent_test_table
--   id: integer (NOT NULL, DEFAULT nextval('agent_test_table_id_seq'::regclass))
--   test_value: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: friday_memories
--   id: bigint (NOT NULL)
--   owner_id: text (NOT NULL)
--   memory_text: text (NOT NULL)
--   emotion_tag: text (NULLABLE, DEFAULT 'soft'::text)
--   is_pinned: boolean (NULLABLE, DEFAULT false)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: friday_messages
--   id: bigint (NOT NULL)
--   owner_id: text (NOT NULL)
--   sender: text (NOT NULL)
--   message: text (NOT NULL)
--   message_type: text (NULLABLE, DEFAULT 'text'::text)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: gallery_items
--   id: bigint (NOT NULL)
--   image_url: text (NOT NULL)
--   uploaded_by: text (NOT NULL)
--   linked_wish_id: bigint (NULLABLE)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: messages
--   id: integer (NOT NULL, DEFAULT nextval('messages_id_seq'::regclass))
--   sender_id: text (NOT NULL)
--   receiver_id: text (NOT NULL)
--   message: text (NOT NULL)
--   read_at: timestamp without time zone (NULLABLE)
--   created_at: timestamp without time zone (NULLABLE, DEFAULT now())
--   updated_at: timestamp without time zone (NULLABLE, DEFAULT now())
--   attachment_url: text (NULLABLE)
--   attachment_type: text (NULLABLE)

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

-- Table: reactions
--   id: integer (NOT NULL, DEFAULT nextval('reactions_id_seq'::regclass))
--   message_id: integer (NULLABLE)
--   user_id: text (NOT NULL)
--   reaction: text (NOT NULL)
--   created_at: timestamp without time zone (NULLABLE, DEFAULT now())

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
-- Policy: Allow public to read access keys on access_keys (SELECT)
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
-- Policy: Allow read messages on friday_messages (SELECT)
-- Policy: Allow insert messages on friday_messages (INSERT)
-- Policy: Allow update messages on friday_messages (UPDATE)
-- Policy: Allow delete messages on friday_messages (DELETE)
-- Policy: Allow read memories on friday_memories (SELECT)
-- Policy: Allow insert memories on friday_memories (INSERT)
-- Policy: Allow update memories on friday_memories (UPDATE)
-- Policy: Allow delete memories on friday_memories (DELETE)
-- Policy: Users can send messages as themselves on messages (INSERT)
-- Policy: Users can see their own conversations on messages (SELECT)
-- Policy: Users can mark messages as read on messages (UPDATE)
-- Policy: Users can delete their own messages on messages (DELETE)
-- Policy: Allow all operations on typing_status on typing_status (ALL)
-- Policy: Enable all for authenticated users on reactions (ALL)
