-- Baroness-kt Supabase Schema (Generated via Node Bridge)


-- Table: access_keys
--   id: text (NOT NULL)
--   secret_key: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: backup_log
--   persona_id: text (NOT NULL)
--   last_backup_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   message_count_at_backup: integer (NULLABLE, DEFAULT 0)

-- Table: chat_sync_pipe
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   recipient_id: text (NOT NULL)
--   payload: jsonb (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: friday_action_requests
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   owner_id: text (NOT NULL)
--   session_id: uuid (NULLABLE)
--   action_name: text (NOT NULL)
--   parameters: jsonb (NOT NULL)
--   status: text (NOT NULL, DEFAULT 'dispatched'::text)
--   created_at: timestamp with time zone (NOT NULL, DEFAULT now())

-- Table: friday_api_usage
--   owner_id: text (NOT NULL)
--   provider: text (NOT NULL)
--   day: date (NOT NULL, DEFAULT CURRENT_DATE)
--   call_count: integer (NOT NULL, DEFAULT 0)

-- Table: friday_entities
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   owner_id: text (NOT NULL)
--   name: text (NOT NULL)
--   aliases: ARRAY (NULLABLE, DEFAULT '{}'::text[])
--   relationship: text (NULLABLE)
--   last_known_fact: text (NULLABLE)
--   updated_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: friday_memories
--   id: bigint (NOT NULL)
--   owner_id: text (NOT NULL)
--   memory_text: text (NOT NULL)
--   emotion_tag: text (NULLABLE, DEFAULT 'soft'::text)
--   is_pinned: boolean (NULLABLE, DEFAULT false)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   category: text (NULLABLE)
--   embedding: USER-DEFINED (NULLABLE)
--   session_id: uuid (NULLABLE)
--   follow_up_worthy: boolean (NULLABLE, DEFAULT false)
--   importance: smallint (NOT NULL, DEFAULT 3)
--   confidence: smallint (NOT NULL, DEFAULT 3)
--   status: text (NOT NULL, DEFAULT 'active'::text)
--   last_reinforced_at: timestamp with time zone (NULLABLE)

-- Table: friday_memory_history
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   memory_id: bigint (NOT NULL)
--   change_type: text (NOT NULL)
--   previous_text: text (NULLABLE)
--   reason: text (NULLABLE)
--   changed_at: timestamp with time zone (NOT NULL, DEFAULT now())

-- Table: friday_messages
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   owner_id: text (NOT NULL)
--   sender: text (NOT NULL)
--   message: text (NOT NULL)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   session_id: uuid (NULLABLE)
--   reflected_at: timestamp with time zone (NULLABLE)
--   is_pinned: boolean (NULLABLE, DEFAULT false)
--   is_deleted: boolean (NULLABLE, DEFAULT false)
--   reactions: jsonb (NULLABLE, DEFAULT '{}'::jsonb)
--   reply_to_id: uuid (NULLABLE)
--   status: text (NULLABLE, DEFAULT 'SENT'::text)
--   is_proactive: boolean (NULLABLE, DEFAULT false)
--   is_command: boolean (NULLABLE, DEFAULT false)
--   sentiment: text (NULLABLE)

-- Table: friday_sessions
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   owner_id: text (NOT NULL)
--   started_at: timestamp with time zone (NOT NULL, DEFAULT now())
--   ended_at: timestamp with time zone (NULLABLE)
--   summary: text (NULLABLE)
--   status: text (NOT NULL, DEFAULT 'active'::text)

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

-- Table: phestydrop_releases
--   id: uuid (NOT NULL, DEFAULT gen_random_uuid())
--   version_code: integer (NOT NULL)
--   version_name: text (NOT NULL)
--   download_url: text (NOT NULL)
--   changelog: text (NULLABLE)
--   is_mandatory: boolean (NULLABLE, DEFAULT false)
--   min_supported_version_code: integer (NULLABLE, DEFAULT 1)
--   created_at: timestamp with time zone (NULLABLE, DEFAULT now())

-- Table: profiles
--   id: text (NOT NULL)
--   display_name: text (NOT NULL)
--   persona: text (NULLABLE, DEFAULT 'Phesty'::text)
--   updated_at: timestamp with time zone (NULLABLE, DEFAULT now())
--   avatar_url: text (NULLABLE)
--   fcm_token: text (NULLABLE)
--   friday_vibe: text (NULLABLE, DEFAULT 'chilled'::text)

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
-- Policy: Allow all operations on typing_status on typing_status (ALL)
-- Policy: Allow public access to messages on messages (ALL)
-- Policy: Allow public access to backup log on backup_log (ALL)
-- Policy: Allow public select on access_keys on access_keys (SELECT)
-- Policy: Allow public access to profiles on profiles (ALL)
-- Policy: Allow public access to AI messages on friday_messages (ALL)
-- Policy: Allow public access to friday_memories on friday_memories (ALL)
-- Policy: Allow public access to sync pipe on chat_sync_pipe (ALL)
-- Policy: Allow public read access to phestydrop_releases on phestydrop_releases (SELECT)
