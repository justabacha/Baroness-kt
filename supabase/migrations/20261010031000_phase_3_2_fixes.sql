CREATE POLICY "profiles_insert" ON public.profiles
    FOR INSERT TO authenticated
    WITH CHECK (id = (auth.jwt() ->> 'persona'));

DROP POLICY IF EXISTS "avatars_delete" ON storage.objects;
DROP POLICY IF EXISTS "avatars_update" ON storage.objects;

CREATE POLICY "avatars_delete" ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id = 'avatars' AND (
            (storage.foldername(name))[1] = (auth.jwt() ->> 'persona')
            OR array_length(storage.foldername(name), 1) IS NULL
        )
    );

CREATE POLICY "avatars_update" ON storage.objects
    FOR UPDATE TO authenticated
    USING (
        bucket_id = 'avatars' AND (
            (storage.foldername(name))[1] = (auth.jwt() ->> 'persona')
            OR array_length(storage.foldername(name), 1) IS NULL
        )
    )
    WITH CHECK (
        bucket_id = 'avatars' AND
        (storage.foldername(name))[1] = (auth.jwt() ->> 'persona')
    );

ALTER TABLE public.wishlist_items REPLICA IDENTITY FULL;
ALTER TABLE public.wishlist_reactions REPLICA IDENTITY FULL;
ALTER TABLE public.wishlist_ratings REPLICA IDENTITY FULL;
