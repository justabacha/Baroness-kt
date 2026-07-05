-- Baroness-kt Supabase Schema
-- Exported: 2026-07-05
-- Database: PostgreSQL via Supabase

-- Table: access_keys
-- id: text (NOT NULL)
-- secret_key: text (NOT NULL)
-- created_at: timestamp with time zone (DEFAULT now())

-- Table: friday_memories
-- id: bigint (NOT NULL)
-- owner_id: text (NOT NULL)
-- memory_text: text (NOT NULL)
-- emotion_tag: text (DEFAULT 'soft')
-- is_pinned: boolean (DEFAULT false)
-- created_at: timestamp with time zone (DEFAULT now())

-- Table: friday_messages
-- id: bigint (NOT NULL)
-- owner_id: text (NOT NULL)
-- sender: text (NOT NULL)
-- message: text (NOT NULL)
-- message_type: text (DEFAULT 'text')
-- created_at: timestamp with time zone (DEFAULT now())

-- Table: gallery_items
-- id: bigint (NOT NULL)
-- image_url: text (NOT NULL)
-- uploaded_by: text (NOT NULL)
-- linked_wish_id: bigint (NULLABLE)
-- created_at: timestamp with time zone (DEFAULT now())

-- Table: messages
-- id: integer (NOT NULL, SEQUENCE)
-- sender_id: text (NOT NULL)
-- receiver_id: text (NOT NULL)
-- message: text (NOT NULL)
-- read_at: timestamp without time zone (NULLABLE)
-- created_at: timestamp without time zone (DEFAULT now())
-- updated_at: timestamp without time zone (DEFAULT now())
-- attachment_url: text (NULLABLE)
-- attachment_type: text (NULLABLE)

-- Table: profiles
-- id: text (NOT NULL)
-- display_name: text (NOT NULL)
-- persona: text (DEFAULT 'Phesty')
-- updated_at: timestamp with time zone (DEFAULT now())
-- avatar_url: text (NULLABLE)

-- Table: reactions
-- id: integer (NOT NULL, SEQUENCE)
-- message_id: integer (NULLABLE)
-- user_id: text (NOT NULL)
-- reaction: text (NOT NULL)
-- created_at: timestamp without time zone (DEFAULT now())

-- Table: typing_status
-- user_id: text (NOT NULL)
-- chat_partner_id: text (NOT NULL)
-- is_typing: boolean (DEFAULT false)
-- updated_at: timestamp without time zone (DEFAULT now())

-- Table: wishlist_items
-- id: bigint (NOT NULL)
-- text: text (NOT NULL)
-- wish_date: date (NOT NULL)
-- status: text (DEFAULT 'planning')
-- creator_id: text (NOT NULL)
-- created_at: timestamp with time zone (DEFAULT now())
-- updated_at: timestamp with time zone (DEFAULT now())

-- Table: wishlist_ratings
-- id: bigint (NOT NULL)
-- wish_id: bigint (NOT NULL)
-- persona_id: text (NOT NULL)
-- rating: integer (NOT NULL)
-- created_at: timestamp with time zone (DEFAULT now())

-- Table: wishlist_reactions
-- id: bigint (NOT NULL)
-- wish_id: bigint (NOT NULL)
-- persona_id: text (NOT NULL)
-- emoji: text (NOT NULL)
-- created_at: timestamp with time zone (DEFAULT now())