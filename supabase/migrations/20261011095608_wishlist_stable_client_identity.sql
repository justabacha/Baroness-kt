ALTER TABLE public.wishlist_items
  ADD COLUMN client_uuid uuid;

UPDATE public.wishlist_items
  SET client_uuid = gen_random_uuid()
  WHERE client_uuid IS NULL;

ALTER TABLE public.wishlist_items
  ALTER COLUMN client_uuid SET NOT NULL;

CREATE UNIQUE INDEX wishlist_items_client_uuid_key
  ON public.wishlist_items (client_uuid);

ALTER TABLE public.wishlist_reactions
  ADD COLUMN wish_client_uuid uuid;

ALTER TABLE public.wishlist_ratings
  ADD COLUMN wish_client_uuid uuid;

UPDATE public.wishlist_reactions r
  SET wish_client_uuid = w.client_uuid
  FROM public.wishlist_items w
  WHERE r.wish_id = w.id;

UPDATE public.wishlist_ratings r
  SET wish_client_uuid = w.client_uuid
  FROM public.wishlist_items w
  WHERE r.wish_id = w.id;

ALTER TABLE public.wishlist_reactions
  ALTER COLUMN wish_client_uuid SET NOT NULL;

ALTER TABLE public.wishlist_ratings
  ALTER COLUMN wish_client_uuid SET NOT NULL;
