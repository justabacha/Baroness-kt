# Custom Access Token Hook Registration

The persona JWT claim is added by the Postgres function
`public.custom_access_token_hook(jsonb)`, defined in
`supabase/migrations/027_custom_access_token_persona_hook.sql`.

## Declare the hook in `supabase/config.toml`

The Supabase CLI supports declaring the Auth hook in project config. Add or
uncomment:

```toml
[auth.hook.custom_access_token]
enabled = true
uri = "pg-functions://postgres/public/custom_access_token_hook"
```

Then apply the declared configuration to the intended hosted project with
`supabase config push --project-ref <project-ref>`. Review the entire config
diff first: `config push` can update other declared hosted settings, not just
the hook. This repository's hosted Auth access-token expiry is currently
604800 seconds, while the local `auth.jwt_expiry` is 3600 seconds; do not push
the stale expiry unintentionally.

The same hook can be registered in Supabase Dashboard at
**Authentication → Auth Hooks → Customize Access Token (JWT) Claims**, using
the Postgres function target above.

## Fresh project setup

A fresh hosted project does not inherit the existing project's Dashboard
hook registration. Ensure this config block is enabled and pushed, or register
the hook in the Dashboard, before issuing persona Auth sessions. Verify the
hook with a password-grant token and confirm the JWT has a top-level `persona`
claim before relying on persona-based RLS.

The hook expects `app_metadata.persona`, preserves all existing Auth claims,
and raises an error if the persona value is missing. Its execute privilege is
granted to `supabase_auth_admin`, not to `anon` or `authenticated`.
