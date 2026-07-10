-- Baroness-kt Supabase RLS Policies
-- Exported: 2026-07-10

-- Table: profiles
-- ALL: "Allow public access to profiles" (PERMISSIVE, public, qual: true, with_check: true)
-- ALL: "Enable all for current_user" (PERMISSIVE, public, qual: id = 'current_user'::text, with_check: id = 'current_user'::text)
-- SELECT: "Public profiles are viewable by everyone" (PERMISSIVE, public, qual: true)
-- ALL: "Users can insert or update their own profile" (PERMISSIVE, public, qual: true, with_check: true)

-- Table: access_keys
-- SELECT: "Allow public to read access keys" (PERMISSIVE, public, qual: true)
-- MISSING: INSERT policy
-- MISSING: UPDATE policy
-- MISSING: DELETE policy

-- Table: friday_messages
-- DELETE: "Allow delete messages" (PERMISSIVE, public, qual: true)
-- INSERT: "Allow insert messages" (PERMISSIVE, public, with_check: true)
-- SELECT: "Allow read messages" (PERMISSIVE, public, qual: true)
-- UPDATE: "Allow update messages" (PERMISSIVE, public, qual: true)

-- Table: friday_memories
-- DELETE: "Allow delete memories" (PERMISSIVE, public, qual: true)
-- INSERT: "Allow insert memories" (PERMISSIVE, public, with_check: true)
-- SELECT: "Allow read memories" (PERMISSIVE, public, qual: true)
-- UPDATE: "Allow update memories" (PERMISSIVE, public, qual: true)

-- Table: wishlist_items
-- DELETE: "wishlist_items_delete" (PERMISSIVE, public, qual: true)
-- INSERT: "wishlist_items_insert" (PERMISSIVE, public, with_check: true)
-- SELECT: "wishlist_items_select" (PERMISSIVE, public, qual: true)
-- UPDATE: "wishlist_items_update" (PERMISSIVE, public, qual: true)

-- Table: wishlist_reactions
-- DELETE: "wishlist_reactions_delete" (PERMISSIVE, public, qual: true)
-- INSERT: "wishlist_reactions_insert" (PERMISSIVE, public, with_check: true)
-- SELECT: "wishlist_reactions_select" (PERMISSIVE, public, qual: true)
-- UPDATE: "wishlist_reactions_update" (PERMISSIVE, public, qual: true)

-- Table: wishlist_ratings
-- DELETE: "wishlist_ratings_delete" (PERMISSIVE, public, qual: true)
-- INSERT: "wishlist_ratings_insert" (PERMISSIVE, public, with_check: true)
-- SELECT: "wishlist_ratings_select" (PERMISSIVE, public, qual: true)
-- UPDATE: "wishlist_ratings_update" (PERMISSIVE, public, qual: true)

-- Table: gallery_items
-- DELETE: "gallery_items_delete" (PERMISSIVE, public, qual: true)
-- INSERT: "gallery_items_insert" (PERMISSIVE, public, with_check: true)
-- SELECT: "gallery_items_select" (PERMISSIVE, public, qual: true)
-- UPDATE: "gallery_items_update" (PERMISSIVE, public, qual: true)

-- Table: messages
-- DELETE: "Users can delete their own messages" (PERMISSIVE, public, qual: sender_id = ANY (ARRAY['phesty_official'::text, 'baroness_official'::text]))
-- UPDATE: "Users can mark messages as read" (PERMISSIVE, public, qual: receiver_id = ANY (ARRAY['phesty_official'::text, 'baroness_official'::text]), with_check: receiver_id = ANY (ARRAY['phesty_official'::text, 'baroness_official'::text]))
-- SELECT: "Users can see their own conversations" (PERMISSIVE, public, qual: (sender_id = ANY (ARRAY['phesty_official'::text, 'baroness_official'::text])) OR (receiver_id = ANY (ARRAY['phesty_official'::text, 'baroness_official'::text])))
-- INSERT: "Users can send messages as themselves" (PERMISSIVE, public, with_check: (auth.role() = 'authenticated'::text) AND (sender_id = ANY (ARRAY['phesty_official'::text, 'baroness_official'::text])))

-- Table: typing_status
-- ALL: "Allow all operations on typing_status" (PERMISSIVE, public, qual: true)

-- Table: reactions
-- ALL: "Enable all for authenticated users" (PERMISSIVE, public, qual: true)