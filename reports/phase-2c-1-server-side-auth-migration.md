# Phase 2C-1 — Server-Side Auth Migration

**Date:** 2026-10-09  
**Project:** `wckluymkbqxdmipzaiff`  
**Status:** Complete

## Executive Summary

Supabase Auth now issues the persona sessions used by the server-side flow. The hosted access-token lifetime was **604800 seconds (7 days)**. Auth signs the tested access token with **ES256** (`kid=35688530-4b80-4574-bf4e-6161e3f3bc79`). The registered Postgres Custom Access Token Hook preserved the Auth claims and added a top-level `persona` claim; a password-grant token with `persona=phesty_official` was accepted by PostgREST with HTTP 200.

Two confirmed Auth users were created with auto-confirmed email identities and server-managed random passwords stored only under the Supabase secrets `AUTH_PASSWORD_PHESTY` and `AUTH_PASSWORD_BARONESS`. `verify-passkey` now validates the existing access key and exchanges the corresponding server-managed Auth credentials for an Auth session. `friday-pending-messages` now verifies bearer tokens through Supabase Auth `getUser`, then scopes the service-role query by the verified user's `app_metadata.persona`.

Both coupled functions deployed successfully. Cross-persona denial, legitimate Friday retrieval, end-to-end passkey login, refresh rotation, PostgREST acceptance, and old HS256 rejection all produced the expected results. No Android/Kotlin code, RLS policy, table, or unrelated function source was changed.

## 1. Live Auth Configuration

- Hosted Auth access-token lifetime confirmed by the operator in Dashboard → Authentication → Sessions → Access Tokens: **604800 seconds**.
- Direct password-grant response returned `expires_in=604800`.
- Pre-existing Custom Access Token Hook registry state: **empty**.
- Registered hook: Postgres `public.custom_access_token_hook`, enabled and visible in Dashboard → Authentication → Auth Hooks.
- Current live JWKS key: `kid=35688530-4b80-4574-bf4e-6161e3f3bc79`, `alg=ES256`, `kty=EC`, `use=sig`.
- Auth users before provisioning: **0**; after provisioning: **2**.
- Public signup/Auth settings were not changed.

The checked-in `supabase/config.toml` still has `auth.jwt_expiry = 3600`; it was not pushed or changed. It is stale relative to the operator-confirmed hosted setting.

## 2. Auth Users and Password Secrets

| Persona | Auth email format | Email confirmed | Trusted app metadata |
|---|---|---:|---|
| `phesty_official` | `phesty@baroness.invalid` | Yes | `persona=phesty_official` |
| `baroness_official` | `baroness@baroness.invalid` | Yes | `persona=baroness_official` |

Each password was generated with a cryptographically secure random source. Password values were uploaded to Supabase secrets and never printed or committed:

- `AUTH_PASSWORD_PHESTY`
- `AUTH_PASSWORD_BARONESS`

The emails use the reserved `.invalid` domain, not a real mailbox. Users were created through the Auth Admin API with `email_confirm=true`; no invitations or confirmation emails were sent.

## 3. Custom Access Token Hook

The hook function was applied as migration `20261009205844_custom_access_token_persona_hook` and registered by the operator in Dashboard → Authentication → Auth Hooks with:

`pg-functions://postgres/public/custom_access_token_hook`

The live function is `SECURITY DEFINER` with `search_path = pg_catalog, public`. It reads the documented event claims path `claims.app_metadata.persona`, preserves the full original claims object, and sets the top-level persona:

```sql
create or replace function public.custom_access_token_hook(event jsonb)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  claims jsonb := event -> 'claims';
  persona text := event #>> '{claims,app_metadata,persona}';
begin
  if claims is null or jsonb_typeof(claims) <> 'object' then
    raise exception 'Custom access token hook received no claims object';
  end if;

  if persona is null or btrim(persona) = '' then
    raise exception 'Auth user is missing app_metadata.persona';
  end if;

  claims := jsonb_set(claims, '{persona}', to_jsonb(persona), true);
  return jsonb_build_object('claims', claims);
end;
$$;

revoke all on function public.custom_access_token_hook(jsonb)
  from public, anon, authenticated;
grant usage on schema public to supabase_auth_admin;
grant execute on function public.custom_access_token_hook(jsonb)
  to supabase_auth_admin;
```

Live privilege inspection confirmed `supabase_auth_admin` can execute it; `anon` and `authenticated` cannot. An actual Auth password-grant token confirmed the hook output is a top-level `persona` claim.

### Hook Registration

The live hook was initially registered by the operator in Dashboard → Authentication → Auth Hooks. This state is not dashboard-only: the Supabase CLI supports declaring `[auth.hook.custom_access_token]` in `supabase/config.toml` and applying it with `supabase config push`. The exact block is:

```toml
[auth.hook.custom_access_token]
enabled = true
uri = "pg-functions://postgres/public/custom_access_token_hook"
```

The block was not added or pushed during Phase 2C-1b because that phase was verification/documentation-only and a broad config push could also apply the stale local `auth.jwt_expiry = 3600` over the hosted 604800-second value. Fresh-project setup is documented in [`docs/auth-hook-registration.md`](../docs/auth-hook-registration.md); enable and push the hook block or register it in the Dashboard, and verify a top-level persona claim before relying on it.

### `sub` Semantics

No current persona authorization depends on `sub` equaling a persona ID:

- No deployed Edge Function extracts `payload.sub` or `user.sub` as a persona. The current Friday endpoint uses the verified user's `app_metadata.persona`; verify-passkey maps the requested persona to the fixed Auth account.
- No live RLS policy contains `auth.jwt() ->> 'sub'` or uses `sub` as the persona. Current persona-scoped policies use the top-level `persona` claim.
- The local function and migration search found no `payload.sub`, `user.sub`, or other `sub`-as-persona extraction. The live policy query for `sub` references returned no rows.

There are no identified Phase 3 dependencies on `sub == persona`. Auth `sub` remains the Auth user's UUID and should not be treated as the existing text persona ID.

## 4. Function Changes and Deployment

### `verify-passkey`

The deployed function retains passkey validation against `public.access_keys` using the service role, validates the supported persona, and then calls:

`POST /auth/v1/token?grant_type=password`

It uses the persona-specific synthetic email and password secret. It no longer contains custom HS256 signing or an HS256 fallback. Its response keeps the existing `token` property for Phase 2 and includes `refresh_token`, `expires_in`, `currentPersonaId`, and the existing `userProfile`. Passwords and service-role credentials are never returned.

Deployed version: **17**, `verify_jwt=true`.

### `friday-pending-messages`

The function now requires a bearer token, validates it with `supabase.auth.getUser(token)`, and derives identity from the verified `user.app_metadata.persona`. Missing/invalid sessions return 401; a requested different persona returns the existing 403 response. The database query remains scoped by `.eq("owner_id", callerPersona)`.

Deployed version: **26**, `verify_jwt=true`. The gateway verifies the Auth JWT, and the function also verifies the current Auth user through GoTrue.

The old `_shared/jwt.ts` HS256 verifier was removed because this was its only caller.

## 5. Verification Results

All checks were run against the live project. Tokens, passwords, API keys, and passkey values were kept in process memory and redacted from output/report.

### Direct password grant

Request shape (credentials redacted):

```sh
curl -i -X POST \
  'https://wckluymkbqxdmipzaiff.supabase.co/auth/v1/token?grant_type=password' \
  -H 'apikey: <publishable API key>' \
  -H 'Content-Type: application/json' \
  --data '{"email":"phesty@baroness.invalid","password":"<REDACTED>"}'
```

- **HTTP:** 200
- **Response body:** `{"access_token":"<REDACTED>","token_type":"bearer","expires_in":604800,"refresh_token":"<REDACTED>","user":<Auth user object>}`. The live response included a complete session; the token values and user identifiers are redacted.

### Token decoding and PostgREST acceptance

- **JWT header:** `alg=ES256`, `kid=35688530-4b80-4574-bf4e-6161e3f3bc79`.
- **Claim:** top-level `persona=phesty_official`.
- **Lifetime:** `expires_in=604800`.
- **PostgREST request:** `GET /rest/v1/phestydrop_releases?limit=1&select=id` with the Auth access token.
- **HTTP:** 200
- **Response body:** `[{"id":"3356c39f-80b6-4737-bf0c-b728d02dff9d"}]`

### Friday cross-persona denial

- **Request:** `GET /functions/v1/friday-pending-messages?user_id=baroness_official&since=2099-01-01T00%3A00%3A00Z` with the phesty Auth token.
- **HTTP:** 403
- **Response body:** `{"error":"Forbidden: Cannot access messages for another user"}`

### Friday legitimate retrieval

- **Request:** `GET /functions/v1/friday-pending-messages?user_id=phesty_official&since=1970-01-01T00%3A00%3A00Z` with the phesty Auth token.
- **HTTP:** 200
- **Result:** 323 message records; every returned `owner_id` was `phesty_official` and every `sender` was `friday`.
- **Response body:** Message content is intentionally redacted from this report: `{"messages":[<323 phesty-owned Friday message records; content redacted>]}`. The response was checked in memory and message text was not emitted or persisted.

### verify-passkey end-to-end

- **Request:** `POST /functions/v1/verify-passkey` with persona `phesty_official` and its existing passkey; credential omitted.
- **HTTP:** 200
- **JWT:** `alg=ES256`, top-level `persona=phesty_official`.
- **Response body:** `{"token":"<REDACTED>","refresh_token":"<REDACTED>","expires_in":604800,"currentPersonaId":"phesty_official","userProfile":{"displayName":"Ryan","avatarUrl":"https://wckluymkbqxdmipzaiff.supabase.co/storage/v1/object/public/avatars/avatar_1790546062184.jpg","persona":"Phesty","personaId":"phesty_official"}}`

### Refresh flow

- **Request:** `POST /auth/v1/token?grant_type=refresh_token` with the verify-passkey-issued refresh token.
- **HTTP:** 200
- **Result:** New access token and rotated refresh token returned; the access token decoded to `alg=ES256`, top-level `persona=phesty_official`, `expires_in=604800`.
- **Response body:** `{"access_token":"<REDACTED>","token_type":"bearer","expires_in":604800,"refresh_token":"<REDACTED>",...}`. Secret token values and user identifiers are redacted.

### Old HS256 rejection

A legacy-format HS256 token was tested against both deployed paths.

| Endpoint | HTTP | Exact response body |
|---|---:|---|
| `GET /functions/v1/friday-pending-messages?...` | 401 | `{"code":"UNAUTHORIZED_LEGACY_JWT","message":"Invalid JWT"}` |
| `POST /functions/v1/verify-passkey` with the old token in Authorization | 401 | `{"code":"UNAUTHORIZED_LEGACY_JWT","message":"Invalid JWT"}` |

Both live responses were re-captured verbatim in Phase 2C-1b and match the Phase 2C-1 report exactly. This body is produced by the Supabase Edge gateway's native legacy-JWT rejection, not by custom code in either deployed function. The deployed function sources do not contain the `UNAUTHORIZED_LEGACY_JWT` code or the `"Invalid JWT"` response body. With gateway verification enabled, the invalid token is rejected before the function handler runs. Supabase documents this code as `UNAUTHORIZED_LEGACY_JWT` for invalid legacy HS256 tokens: [Edge Function error codes](https://supabase.com/docs/guides/functions/error-codes#unauthorized_legacy_jwt).

### Login Request Shape

The live gateway accepts the anon-key login request when both headers are present:

```sh
curl -i -X POST \
  'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/verify-passkey' \
  -H 'apikey: <ANON_KEY>' \
  -H 'Authorization: Bearer <ANON_KEY>' \
  -H 'Content-Type: application/json' \
  --data '{"persona":"phesty_official","passkey":"<REDACTED_VALID_PASSKEY>"}'
```

- **HTTP:** 200; the gateway accepted the request.
- **Response:** Auth session fields returned; token and refresh token redacted.
- **Token check:** returned access token decoded as `alg=ES256` with top-level `persona=phesty_official`.

The apikey-only variant, with no Authorization header, failed at the gateway:

```sh
curl -i -X POST \
  'https://wckluymkbqxdmipzaiff.supabase.co/functions/v1/verify-passkey' \
  -H 'apikey: <ANON_KEY>' \
  -H 'Content-Type: application/json' \
  --data '{"persona":"phesty_official","passkey":"<REDACTED_VALID_PASSKEY>"}'
```

- **HTTP:** 401
- **Exact response body:** `{"code":"UNAUTHORIZED_NO_AUTH_HEADER","message":"Missing authorization header"}`

Therefore, the Android login request must include both the anon `apikey` and `Authorization: Bearer <ANON_KEY>` headers. The valid passkey and resulting token are not printed here.

### Secret Storage Confirmation

`supabase secrets list` confirmed the names `AUTH_PASSWORD_PHESTY` and `AUTH_PASSWORD_BARONESS` exist as Edge Function secrets; values were not displayed. The public `/auth/v1/settings` endpoint returned HTTP 200 and only the public Auth settings keys (`external`, `disable_signup`, `mailer_autoconfirm`, `phone_autoconfirm`, `sms_provider`, `saml_enabled`, `saml_private_key_next_configured`, `passkeys_enabled`); it exposed neither password secret name nor value. The live verify-passkey end-to-end login returned HTTP 200 with a complete Auth session, confirming the function can read and use its selected server-side password secret.

## 6. Deployment and Rollback

- `friday-pending-messages` deployed first: **success**, version 26, gateway JWT verification enabled.
- After its cross-persona and legitimate-access checks passed, `verify-passkey` deployed: **success**, version 17, gateway JWT verification remains enabled.
- All required verifications passed; **no rollback was needed**.
- Both deployed functions are active. No other Edge Function was included in the deploy commands.

## 7. Scope Confirmation and Follow-Up

- Android/Kotlin code: **not modified**. Phase 2C-2 refresh persistence/rotation remains outstanding.
- RLS policies and tables: **not modified**.
- Other Edge Function source: **not modified**.
- Public signup and unrelated Auth settings: **not changed**.
- `verify-passkey` hosted gateway setting remained `true`; local `supabase/config.toml` was set to `true`.
- `friday-pending-messages` local and deployed gateway verification are `true`.
- Local `auth.jwt_expiry = 3600` remains stale relative to the hosted 604800-second setting; it was not pushed to Auth. Reconcile this declarative-config drift separately before any broad `supabase config push`.

## Discovered — Out of Scope

The Android client still stores only the access token. Auth sessions now return a refresh token, but Android refresh-token persistence and rotation are Phase 2C-2 work. The current seven-day access token lifetime means a future client update is needed for sessions to continue transparently beyond expiry.

## Follow-Up Items for Phase 2C-2 or Phase 3

- Phase 2C-2 must store and rotate the Auth refresh token, update the access-token cache atomically, and handle refresh failures by requiring passkey reauthentication.
- Before a future whole-project `supabase config push`, reconcile the local `auth.jwt_expiry = 3600` with the hosted 604800-second setting to avoid unintentionally changing the live token lifetime.
- Continue to use the top-level persona claim in Phase 3 RLS policies; there is no current dependency on `sub == persona`.

### Exact `supabase/config.toml` diff from Phase 2C-1

The previous report's wording implied only the verify-passkey setting changed. That was inaccurate: both affected function settings changed, and the old explanatory Friday comment was removed. The exact tracked diff is:

```diff
 [functions.friday-pending-messages]
-# Gateway JWT verification is intentionally disabled (verify_jwt = false) because custom persona JWTs are verified inside the function code via _shared/jwt.ts.
-verify_jwt = false
+verify_jwt = true
 
 [functions.verify-passkey]
-verify_jwt = false
-
+verify_jwt = true
```

No other tracked lines in `supabase/config.toml` differ from the repository baseline. In particular, `auth.jwt_expiry = 3600` was unchanged.

## Files Changed

### Phase 2C-1 implementation

- `supabase/config.toml` — set `verify_jwt = true` for `friday-pending-messages` and `verify-passkey`; removed the old Friday comment describing custom HS256 verification.
- `supabase/functions/verify-passkey/index.ts` — replaced custom HS256 minting with Auth password-grant session issuance.
- `supabase/functions/friday-pending-messages/index.ts` — replaced custom HS256 verification with Supabase Auth `getUser`.
- `supabase/functions/_shared/jwt.ts` — removed the obsolete single-purpose HS256 verifier.
- `supabase/migrations/027_custom_access_token_persona_hook.sql` — added the persona claim hook and restricted grants.
- `reports/phase-2c-1-server-side-auth-migration.md` — recorded the migration results.

### Phase 2C-1b verification-only changes

- `docs/auth-hook-registration.md` — created to document the supported config and Dashboard registration paths.
- `reports/phase-2c-1-server-side-auth-migration.md` — updated with literal gateway responses, login header results, `sub` search findings, secret visibility, hook registration, and config diff correction.

No function, migration, config, RLS policy, Auth user, secret, or Android/Kotlin code was modified during Phase 2C-1b.
