# Phase 2B — Supabase Auth Alignment Feasibility Audit

**Audit date:** 2026-10-09  
**Project:** `wckluymkbqxdmipzaiff`  
**Scope:** Read-only feasibility review. No Auth users, sessions, hooks, settings, database rows, policies, secrets, Edge Functions, or Android code were changed.

## 1. Executive Summary

**Path A is viable and is the recommended direction.** The live project has GoTrue/Auth enabled (`/auth/v1/health` returned GoTrue `v2.197.0`) and publishes an ES256 EC signing key at its JWKS endpoint. Supabase Auth is designed to issue access JWTs that its Data API/PostgREST and Realtime services can validate using the project signing keys.

For the existing persona-scoped policy shape `auth.jwt() ->> 'persona'`, the exact suitable injection mechanism is a **Custom Access Token Hook** that copies a trusted persona value to the JWT's top-level `persona` claim. It can be a Postgres function receiving one `jsonb` event and returning a `jsonb` object containing updated `claims`. Alternatively, `app_metadata.persona` is a trusted place to store the persona, but by itself it appears nested at `auth.jwt() -> 'app_metadata' ->> 'persona'`; it does **not** satisfy a top-level `auth.jwt() ->> 'persona'` policy without a hook or policy-expression change.

Custom Access Token Hooks are supported on Free and Pro plans, but this audit could not read the hosted Auth Hooks dashboard registration. No hook-like function was found in the live `public` or `auth` function catalog, and the checked-in `config.toml` has the hook configuration commented out. Thus, no currently active persona-injection mechanism is evidenced; hosted hook registration remains unconfirmed.

The principal product trade-off is session lifetime: access tokens should be short-lived and the project’s current hosted expiry value was not exposed by the public Auth settings endpoint. Supabase documentation caps configurable JWT expiry at seven days; one-year access JWTs are not supported. Refresh-token handling is therefore needed for a robust long-running session. The Phase 2 token interceptor can continue sending any JWT string, but Phase 2's current access-token-only storage/login flow will need a refresh-session addition.

## 2. Auth Signing Model

| Check | Live finding |
|---|---|
| Auth enabled | Yes. `GET /auth/v1/health` returned HTTP 200 and identified GoTrue `v2.197.0`. |
| Public JWKS | `GET /auth/v1/.well-known/jwks.json` returned one key: `kid=35688530-4b80-4574-bf4e-6161e3f3bc79`, `alg=ES256`, `kty=EC`, `use=sig`. |
| Active algorithm | ES256 is the sole published verification key and is the expected current signing algorithm. The key is treated as current based on the live JWKS and prior live REST rejection evidence; this audit did not mint an Auth token because no Auth user was to be created and no test-user credentials were available. |
| Auth issuance | Supabase Auth issues user-session access tokens with the project’s configured signing keys; the Auth JWT docs describe this model. Direct observation of an Auth-issued token was not possible within the no-user-creation constraint. |
| PostgREST alignment | Supabase’s JWT documentation states Supabase products verify JWTs against signing keys, and the project exposes its Auth JWKS. PostgREST’s live PGRST301 rejection of the custom HS256 token confirms it rejects that token’s key type. No valid Auth-issued token was available to perform a direct REST acceptance test, so acceptance of a token signed by this exact JWKS key is strongly supported by the platform’s documented integration, not independently exercised here. |

Supabase documentation: [JWTs](https://supabase.com/docs/guides/auth/jwts), [JWT Signing Keys](https://supabase.com/docs/guides/auth/signing-keys).

## 3. Auth User Creation Path

The server-side Admin API supports `createUser` with an email, a server-managed password, and `email_confirm: true`. Supabase documents `email_confirm` specifically as a way to auto-confirm the user. Admin user creation is intended for server-side use and must not expose a service-role credential to the app.

Two users—one per persona—are a supported standard pattern. Use two unique email identifiers; Auth requires identities to be unique within the project. They do not need to be end-user inboxes for the no-email-flow design: create them administratively with email confirmation already marked complete, and do not send confirmation/invite messages. Use a controlled, syntactically valid internal email namespace and maintain unique addresses. A password must be generated and managed server-side because the password grant requires it; it must never be returned to or embedded in the Android app.

The project’s public Auth settings endpoint returned `disable_signup=false`, but that only indicates signup is enabled and is not necessary for server-admin user provisioning. Email/pass sign-in is a supported GoTrue method.

Reference: [Admin create user](https://supabase.com/docs/reference/javascript/auth-admin-createuser).

## 4. Server-Side Session Creation

### Supported flow: password grant

The supported option is signing in the existing Auth user with the server-held email and password. GoTrue implements this standard password sign-in and returns an Auth session containing an access token and refresh token. It creates/refreshes session state, so it was not invoked during this read-only audit.

Request shape:

```http
POST https://wckluymkbqxdmipzaiff.supabase.co/auth/v1/token?grant_type=password
apikey: <project publishable or legacy anon API key>
Content-Type: application/json

{
  "email": "<persona-specific Auth email>",
  "password": "<server-held Auth password>"
}
```

The successful response includes `access_token`, `token_type`, `expires_in`, `refresh_token`, and `user`. The deployed verify-passkey function can make this Auth request after validating the persona passkey, using the project's publishable/anon API key as the API key; the service-role credential is not required to sign in. The service role is used only for privileged admin user provisioning. This audit did not change or call that function.

### Admin session creation alternative

The documented Auth Admin API supports user management, including creating users and generating links, but does not document an admin endpoint that creates a normal user session without that user's password (for example, an admin `createSession` operation). Do not plan on an undocumented endpoint. Use password grant with a server-managed password for this design.

References: [Password sign-in](https://supabase.com/docs/reference/javascript/auth-signinwithpassword), [User sessions](https://supabase.com/docs/guides/auth/sessions).

## 5. Persona Claim Injection

### Custom Access Token Hook

Custom Access Token Hooks are a supported Supabase Auth feature on Free and Pro plans. Hook registration is done in the Supabase Dashboard under **Authentication → Hooks**, or using supported Auth configuration. A Postgres-function hook receives one `jsonb` event and returns a `jsonb` object. Its event contains `user_id`, the current `claims`, and `authentication_method`; its successful output contains updated `claims`.

The hook can add an arbitrary custom claim such as top-level `"persona": "baroness_official"` while preserving required claims. It can either derive persona from trusted `app_metadata` already in the event, or query a mapping table keyed by Auth `user_id`. If it queries a public table, the Auth execution role must be granted appropriate access and the table's RLS must permit it (or the function must be designed with an appropriately restricted `SECURITY DEFINER` owner). The hook runs before token issuance, so the claim is available to Postgres RLS for the newly issued token.

### `app_metadata` alternative

Admin-managed `app_metadata` can hold a trusted persona mapping and is included in Auth JWT claims. It should not be confused with user-editable `user_metadata`; clients must not be able to set their own authorization persona. Direct app-metadata use yields a nested claim:

```sql
auth.jwt() -> 'app_metadata' ->> 'persona'
```

To preserve the proposed top-level expression `auth.jwt() ->> 'persona'`, a Custom Access Token Hook must copy the trusted `app_metadata.persona` value to `claims.persona`. Alternatively, RLS policy expressions would need to read the nested app-metadata claim.

### Current project configuration

The live function catalog search found no custom-access-token-hook candidate in `public` or `auth`. The checked-in `[auth.hook.custom_access_token]` section is commented out. The public Auth settings endpoint does not expose hook registration, and the Dashboard hook registry was not readable via the available tools. **Conclusion: there is no evidence of a currently configured persona hook or established app-metadata mechanism; hosted hook registration status is unconfirmed.**

References: [Custom Access Token Hook](https://supabase.com/docs/guides/auth/auth-hooks/custom-access-token-hook), [Auth Hooks](https://supabase.com/docs/guides/auth/auth-hooks).

## 6. Token Lifetime and Refresh

- **Current hosted access-token lifetime:** Not exposed by the live public `/auth/v1/settings` response. That response omitted `jwt_exp`. The checked-in `supabase/config.toml` specifies `auth.jwt_expiry = 3600`, but local config is not authoritative for the hosted project, so 3600 seconds is not asserted as the live value.
- **Maximum configurable access-token lifetime:** Supabase Auth configuration documentation caps JWT expiry at 604,800 seconds (7 days). One-year access tokens are not a supported setting.
- **Refresh-token lifetime:** The public settings response did not expose configured session timebox or inactivity limits. Supabase refresh tokens do not have a fixed expiry by default, but rotate on use and are subject to session termination/settings; the default short reuse interval handles retry/race cases and is not equivalent to a one-year access token.
- **Refresh path:** Supported via `POST /auth/v1/token?grant_type=refresh_token` with a refresh token. It returns a new access token and rotated refresh token/session data.
- **Design consequence:** Keep access JWTs short-lived and implement refresh handling. A refresh failure or revoked/expired session must fall back to passkey reauthentication.

References: [User sessions](https://supabase.com/docs/guides/auth/sessions), [Auth configuration reference](https://supabase.com/docs/guides/local-development/cli/config#auth).

## 7. Existing Tables, RLS, and Client Impact

- Existing persona identifiers are text: live columns include `profiles.id`, `messages.sender_id` / `receiver_id`, `friday_messages.owner_id`, `friday_memories.owner_id`, `chat_sync_pipe.recipient_id`, wishlist creator/persona IDs. No table foreign key to `auth.users` is required solely to issue Auth tokens.
- Auth-issued tokens can carry the same text persona value in a custom claim. No schema change is required for existing text persona identifiers. The RLS expression `auth.jwt() ->> 'persona'` remains valid if the hook emits that top-level claim.
- During the current permissive-policy migration window, valid Auth JWTs map to the existing `authenticated` Postgres role; permissive policies continue to allow their existing operations. The Auth token does not itself tighten or relax policies. This is not a recommendation to keep permissive policies long term.
- `AuthTokenInterceptor` replaces the Authorization header with the cached token string, and `supabase-kt` receives that string through its `accessToken` callback. Neither transport cares whether the string was custom HS256 or Auth-issued ES256, so the propagation mechanism can remain.
- The current `AuthManager` expects a `token` field, stores only `auth_token`, and updates the access-token cache. It does not store a `refresh_token` or refresh `expires_in`; therefore, the JWT transport/interceptor needs no algorithm-specific change, but the login/session lifecycle needs work to persist and rotate refresh tokens safely.
- Supabase Realtime uses the Auth JWT supplied by the client to establish user claims and authorize channel actions. The current client already supplies the token callback and pushes token updates with `setAuth`; a valid Auth-issued ES256 token is the native token model. Realtime's actual acceptance by this project was not independently exercised with a valid Auth user token under the no-user-creation restriction.

The live RLS snapshot and live column types were read only; no policies or schema objects were altered.

## 8. Friday Endpoint Compatibility

The deployed `friday-pending-messages` function’s custom verifier explicitly accepts only HS256 and verifies using its own `SUPABASE_JWT_SECRET` / service-role fallback. It will **not** accept Auth-issued ES256 tokens using that implementation. When Path A is implemented, this endpoint and any other custom HS256 verifiers must be realigned to the Auth-issued token/JWKS model (or use a supported Auth verification facility). This is a required later-phase change; it was not performed in this audit.

## 9. Migration Risk Assessment

| Risk | Assessment |
|---|---|
| Synthetic email identifiers | Admin creation with `email_confirm: true` can avoid email confirmation flows. Use unique, syntactically valid, controlled identifiers; avoid real user mailboxes if the app has no email identity. Confirm hosted email/provider settings before any future create-user operation. |
| Password storage | The password must be generated and retained server-side for password-grant session creation. A leaked or shared password could bypass the passkey gate; never put it in the app or logs, and restrict access to the server secret store. |
| Auth user IDs vs persona IDs | Auth `sub` will be a UUID, while existing rows use persona text IDs. This is fine if all relevant policies/functions rely on the custom persona claim; any existing code assuming `sub == persona` needs review. |
| Unique email and two users | Two distinct Auth users with unique email identities is a standard supported arrangement. Email uniqueness makes alias collisions a deployment concern; two fixed accounts are unlikely to stress Auth rate limits, but repeated password-grant requests should not be used as the app's per-request authentication mechanism. |
| Access-token expiry | A one-year Auth access token is unavailable; expiry requires refresh flow support in app and backend. |
| Refresh rotation | Refresh tokens rotate. Concurrent workers/devices refreshing one stored token can race; serialize refresh operations and atomically persist the replacement token pair. Phase 2 WorkManager identity-binding concerns make this especially important. |
| Signing-key rotation | With asymmetric signing keys and JWKS verification, documented rotation supports overlapping keys and does not normally force sign-out. Clients must not pin a specific `kid`, and API services must use the current JWKS. Revoking a key or changing session/password state can invalidate access/refresh behavior. |
| Logout/revocation | Clearing local tokens stops that client from sending them but does not automatically revoke the Auth session server-side. Server-side sign-out/revocation requires an Auth sign-out call with the session token and handling its failure. Offline logout should still clear local credentials; the server session may remain until expiry/revocation. |
| Custom hook availability/permissions | Hook is documented on Free/Pro, but its registration and runtime grants are not verified on this project. Hook failures can block token issuance; preserve all required claims and test issuance/refresh behavior. |
| Stale claim | Persona in an issued JWT is a snapshot until refresh. Changing a user's persona metadata does not alter already issued JWTs; revoke sessions or wait for token expiry to apply a remapping. |
| Friday custom verifier | The existing HS256 verifier becomes incompatible with Auth ES256 tokens and needs a separate, coordinated update before using Auth tokens on that path. |

## 10. Path B — `SUPABASE_SECRET_KEYS` (Informational)

The prior audit observed the deployed environment variable name `SUPABASE_SECRET_KEYS`. **This audit did not read or inspect its value, parse it, print it, or use it.** The variable's name alone does not establish whether it contains private signing material, key IDs, a JSON key set, or something else; its structure and contents therefore remain unconfirmed.

Supabase's signing-key system is designed for Auth to manage private signing material and publish the corresponding verification keys. Supabase supports externally minted JWTs when a compatible signing key has been deliberately imported, but placing a private signing key into an Edge Function secret and minting tokens there would duplicate issuer responsibilities, increase exposure and rotation burden, and risk divergence from Auth session/refresh semantics. Path A remains preferable because Auth issues the complete session, rotates keys, manages refresh tokens, and provides the standard user/JWT lifecycle. No attempt was made to read `SUPABASE_SECRET_KEYS`.

Reference: [JWT Signing Keys](https://supabase.com/docs/guides/auth/signing-keys).

## 11. Recommended Path A Sequence (No Implementation Performed)

1. Confirm the desired persona-to-Auth-user mapping and choose unique controlled email identifiers; decide how server-managed passwords will be created, stored, rotated, and protected.
2. Confirm the hosted Auth Hooks dashboard state, access-token expiry, email confirmation settings, and actual JWKS signing-key status with an authorized project administrator.
3. Define the trusted claim source (`app_metadata.persona`) and select a Custom Access Token Hook that emits the top-level `persona` claim while retaining all required standard claims. Verify hook grants/RLS access before enabling it.
4. In a later implementation phase, provision the two Auth users administratively with confirmed email and server-managed passwords; do not involve public signup or client-held service credentials.
5. Update passkey verification to obtain an Auth session via password grant only after successful passkey validation. Return the complete session information securely; never include Auth passwords or service secrets in the response.
6. Add Android refresh-token persistence/rotation and serialized refresh handling while keeping the existing access-token interceptor/callback.
7. Align `friday-pending-messages` and other custom token verifiers with Auth-issued JWTs, then validate PostgREST, Realtime, Edge Functions, refresh, logout, and transition behavior before tightening RLS.

## 12. Outstanding Unknowns

- The hosted Auth Hooks registry was not exposed by the available tools; whether a hook is registered in Dashboard remains unconfirmed.
- The actual hosted `jwt_exp` and refresh/session settings were not included in the public Auth settings response. Local `config.toml` is not authoritative.
- Auth token issuance against the currently advertised ES256 key, direct PostgREST acceptance of that token, and Realtime acceptance were not end-to-end tested because the audit was not permitted to create users or establish sessions.
- The `SUPABASE_SECRET_KEYS` environment variable's format and contents remain unknown by design; they were not accessed.
- No admin “create session” endpoint was identified in the documented Auth API. Password grant is the supported plan for a session tied to these persona users.
- It remains to be decided whether the hook reads persona from trusted `app_metadata` or from a separate user-ID mapping table; either can produce the required top-level claim.
