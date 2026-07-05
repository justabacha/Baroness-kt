-- Baroness-kt Supabase RLS Policies
-- Exported: 2026-07-05

-- Table: profiles
-- SELECT: "Public profiles are viewable by everyone" (PERMISSIVE, public, qual: true)
-- ALL: "Allow public access to profiles" (PERMISSIVE, public, qual: true, with_check: true)
-- ALL: "Enable all for current_user" (PERMISSIVE, public, qual: id = 'current_user')
-- ALL: "Users can insert or update their own profile" (PERMISSIVE, public, qual: true, with_check: true)

-- Table: access_keys
-- SELECT: "Allow public to read access keys" (PERMISSIVE, public, qual: true)
-- MISSING: INSERT policy
-- MISSING: UPDATE policy
-- MISSING: DELETE policy

-- Table: friday_messages
-- SELECT: "Allow read messages" (PERMISSIVE, public, qual: true)
-- INSERT: "Allow insert messages" (PERMISSIVE, public, with_check: true)
-- UPDATE: "Allow update messages" (PERMISSIVE, public, qual: true)
-- DELETE: "Allow delete messages" (PERMISSIVE, public, qual: true)

-- Table: friday_memories
-- SELECT: "Allow read memories" (PERMISSIVE, public, qual: true)
-- INSERT: "Allow insert memories" (PERMISSIVE, public, with_check: true)
-- UPDATE: "Allow update memories" (PERMISSIVE, public, qual: true)
-- DELETE: "Allow delete memories" (PERMISSIVE, public, qual: true)

-- Table: wishlist_items
-- SELECT: "wishlist_items_select" (PERMISSIVE, public, qual: true)
-- INSERT: "wishlist_items_insert" (PERMISSIVE, public, with_check: true)
-- UPDATE: "wishlist_items_update" (PERMISSIVE, public, qual: true)
-- DELETE: "wishlist_items_delete" (PERMISSIVE, public, qual: true)

-- Table: wishlist_reactions
-- SELECT: "wishlist_reactions_select" (PERMISSIVE, public, qual: true)
-- INSERT: "wishlist_reactions_insert" (PERMISSIVE, public, with_check: true)
-- UPDATE: "wishlist_reactions_update" (PERMISSIVE, public, qual: true)
-- DELETE: "wishlist_reactions_delete" (PERMISSIVE, public, qual: true)

-- Table: wishlist_ratings
-- SELECT: "wishlist_ratings_select" (PERMISSIVE, public, qual: true)
-- INSERT: "wishlist_ratings_insert" (PERMISSIVE, public, with_check: true)
-- UPDATE: "wishlist_ratings_update" (PERMISSIVE, public, qual: true)
-- DELETE: "wishlist_ratings_delete" (PERMISSIVE, public, qual: true)

-- Table: gallery_items
-- SELECT: "gallery_items_select" (PERMISSIVE, public, qual: true)
-- INSERT: "gallery_items_insert" (PERMISSIVE, public, with_check: true)
-- UPDATE: "gallery_items_update" (PERMISSIVE, public, qual: true)
-- DELETE: "gallery_items_delete" (PERMISSIVE, public, qual: true)

-- Table: messages
-- SELECT: "Users can see their own conversations" (restricted to phesty_official, baroness_official)
-- INSERT: "Users can send messages as themselves" (restricted to authenticated users)
-- UPDATE: "Users can mark messages as read" (restricted to phesty_official, baroness_official)
-- DELETE: "Users can delete their own messages" (restricted to phesty_official, baroness_official)

-- Table: typing_status
-- ALL: "Allow all operations on typing_status" (PERMISSIVE, public, qual: true)

-- Table: reactions
-- ALL: "Enable all for authenticated users" (PERMISSIVE, public, qual: true)