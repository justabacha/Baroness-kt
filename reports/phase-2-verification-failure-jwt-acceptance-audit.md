# Phase 2 Verification Failure — Live JWT Acceptance Audit

**Audit date:** 2026-10-09  
**Project:** `wckluymkbqxdmipzaiff`  
**Scope:** Read-only live audit. No database rows, policies, functions, secrets, or configuration were changed.

## 1. Executive Summary

**Root cause confirmed: the JWT minted by `verify-passkey` is signed with the wrong key material and algorithm for the live PostgREST verifier.** The deployed function emits an `HS256` token using `SUPABASE_JWT_SECRET` if present, otherwise it falls back to `SUPABASE_SERVICE_ROLE_KEY`. The deployed secret-name inventory contains `SUPABASE_SERVICE_ROLE_KEY` but not `SUPABASE_JWT_SECRET`, so the fallback is selected. The live Auth JWKS endpoint advertises an `ES256` elliptic-curve signing key, and PostgREST rejects the minted HS256 token with `PGRST301` / “No suitable key or wrong key type.”

This is not an RLS failure. The public table returns HTTP 200 with both no Authorization header and the anon bearer token, then HTTP 401 only with the persona JWT. The JWT has a valid-looking `role` that exists in Postgres, matching `sub` and `persona`, a future `exp`, and no `aud` or `iss`; however, the rejection is at key decoding/signature verification, before those claims could explain the observed failure.

The literal deployed secret values were not retrieved or compared. That is unnecessary to establish the operative mismatch: the function’s deployed fallback is to the service-role key, the emitted algorithm is HS256, the live public signing-key set is ES256, and the PostgREST error explicitly reports no suitable key/wrong key type.

## 2. Reproduction Evidence

A fresh JWT was obtained from the deployed `verify-passkey` function using the disposable audit persona. The passkey and token are intentionally omitted from this report. REST requests used `select=id` and `limit=1` to minimize returned data. All requests included the anon key in `apikey`; “none” means no `Authorization` header.

Equivalent redacted curl commands:

```sh
BASE='https://wckluymkbqxdmipzaiff.supabase.co'
ANON_KEY='<redacted legacy anon API key>'
VERIFY_RESPONSE=$(curl -fsS -X POST "$BASE/functions/v1/verify-passkey" \
  -H "apikey: $ANON_KEY" \
  -H "Authorization: Bearer $ANON_KEY" \
  -H "Content-Type: application/json" \
  --data '{"persona":"<TEST_PERSONA>","passkey":"<REDACTED_TEST_PASSKEY>"}')
JWT=$(printf '%s' "$VERIFY_RESPONSE" | jq -r '.token')
unset VERIFY_RESPONSE
# Keep JWT in memory only; do not print it or persist it.

curl -i "$BASE/rest/v1/phestydrop_releases?limit=1&select=id" \
  -H "apikey: $ANON_KEY"
curl -i "$BASE/rest/v1/phestydrop_releases?limit=1&select=id" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $ANON_KEY"
curl -i "$BASE/rest/v1/phestydrop_releases?limit=1&select=id" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $JWT"

curl -i "$BASE/rest/v1/profiles?id=eq.<TEST_PERSONA>&select=id&limit=1" \
  -H "apikey: $ANON_KEY"
curl -i "$BASE/rest/v1/profiles?id=eq.<TEST_PERSONA>&select=id&limit=1" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $ANON_KEY"
curl -i "$BASE/rest/v1/profiles?id=eq.<TEST_PERSONA>&select=id&limit=1" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $JWT"

curl -i "$BASE/rest/v1/friday_messages?limit=1&select=id" \
  -H "apikey: $ANON_KEY"
curl -i "$BASE/rest/v1/friday_messages?limit=1&select=id" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $ANON_KEY"
curl -i "$BASE/rest/v1/friday_messages?limit=1&select=id" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $JWT"
```

Observed status and exact response body:

| Endpoint | Authorization mode | Status | Response body |
|---|---|---:|---|
| `phestydrop_releases` | none | 200 | `[{"id":"3356c39f-80b6-4737-bf0c-b728d02dff9d"}]` |
| `phestydrop_releases` | anon bearer | 200 | `[{"id":"3356c39f-80b6-4737-bf0c-b728d02dff9d"}]` |
| `phestydrop_releases` | persona JWT | 401 | `{"code":"PGRST301","details":"None of the keys was able to decode the JWT","hint":null,"message":"No suitable key or wrong key type"}` |
| `profiles` | none | 200 | `[]` |
| `profiles` | anon bearer | 200 | `[]` |
| `profiles` | persona JWT | 401 | `{"code":"PGRST301","details":"None of the keys was able to decode the JWT","hint":null,"message":"No suitable key or wrong key type"}` |
| `friday_messages` | none | 200 | `[{"id":"bdf6af31-b93d-42ff-92ac-716dd9b52b8a"}]` |
| `friday_messages` | anon bearer | 200 | `[{"id":"bdf6af31-b93d-42ff-92ac-716dd9b52b8a"}]` |
| `friday_messages` | persona JWT | 401 | `{"code":"PGRST301","details":"None of the keys was able to decode the JWT","hint":null,"message":"No suitable key or wrong key type"}` |

The three 401 bodies were identical. The public rows returned by the no-Authorization and anon cases establish that these are not policy-denied results; the persona token is rejected before RLS applies.

## 3. JWT Structure

Decoded locally in memory; neither the JWT nor its signature was printed or written to this report.

| Item | Observed |
|---|---|
| Header `alg` | `HS256` |
| Header `typ` | `JWT` |
| Payload keys | `exp`, `iat`, `persona`, `role`, `sub` |
| `aud` | Absent |
| `iss` | Absent |
| `role` | `authenticated`; this role exists in the live Postgres role catalog |
| `sub` and `persona` | Match |
| `iat` | Current at issuance (within one second of audit-time decoding) |
| `exp` | In the future by approximately 31,536,000 seconds (one year) |

There are no additional claims in the deployed function’s minted payload. The absent `aud` and `iss` could matter only if the live verifier requires them; they do not account for the exact observed error, which is key selection/decoding failure.

## 4. Signing Secret Consistency

The live deployed `verify-passkey` source reads:

1. `SUPABASE_JWT_SECRET`
2. If unavailable, `SUPABASE_SERVICE_ROLE_KEY`

The live Supabase CLI secret-name listing contained `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_JWKS`, and `SUPABASE_SECRET_KEYS`; it did **not** contain a secret named `SUPABASE_JWT_SECRET`. The function therefore falls back to `SUPABASE_SERVICE_ROLE_KEY` based on the deployed inventory. Secret values were neither requested nor exposed.

The project’s live public signing-key reference is the JWKS key with `kid` `35688530-4b80-4574-bf4e-6161e3f3bc79`; its public metadata is `kty=EC`, `alg=ES256`, `use=sig`. The `verify-passkey` function signs as HS256 using the fallback value rather than that asymmetric signing key. This explains the observed key-type rejection. A literal comparison between secret values was not performed; the secret itself is intentionally unavailable to this audit.

## 5. PostgREST / Kong JWT Configuration

- Live REST responses identify the rejecting component as PostgREST: `PGRST301`, “None of the keys was able to decode the JWT,” “No suitable key or wrong key type.”
- The public Auth JWKS endpoint returned one signing key, an EC key using ES256. No symmetric key material was exposed by the JWKS endpoint.
- `authenticated` exists in Postgres, so the token’s `role` value is not a nonexistent-role cause.
- The `aud` and `iss` claims are absent from the minted JWT. Available live interfaces did not expose PostgREST’s complete `jwt-aud`/issuer settings. The attempted Auth settings endpoint returned 401, and the database settings query exposed no PostgREST JWT settings. Therefore, any additional required audience or issuer is **unconfirmed**, not a demonstrated cause.
- The observed failure is at cryptographic key selection/verification, before role, audience, or issuer checks can be isolated.
- The deployed function inventory reports `verify-passkey` version 14 with gateway `verify_jwt=true` and `friday-pending-messages` version 23 with `verify_jwt=false`. The checked-in `config.toml` is not proof of current hosted gateway configuration.
- No live evidence was available for a recent PostgREST JWT-config change or a custom Auth access-token hook. The checked-in hook settings are commented/unset, but that local file does not establish hosted Auth configuration.

## 6. PostgREST Reload Event

The ±10-minute live `public.migration_history` query around `2026-10-09T18:44:13Z` returned **no applied migrations**. The most recent live migration-history entry was `025_phestydrop_manifest.sql`, applied `2026-09-28T20:50:12.548681Z`.

Live logs show PostgREST receiving several `pgrst` schema-cache reload notifications at `18:44:12Z`, followed by `Config reloaded` at `18:44:12.581915Z` and again at `18:44:13.873547Z`; schema cache reload completed around `18:44:14Z`. The verify-passkey Edge Function invocation appears at `18:44:25Z`, after those reload messages. Its deployed version 14 timestamp corresponds to `2026-09-29T09:14:11Z`, not the reload window.

No migration or Edge Function deployment correlates with the reload. Logs confirm PostgREST configuration/schema-cache reload and connection reinitialization, but do not identify the initiating actor. A platform/internal reload is plausible; attribution to the platform versus a direct dashboard/config action is **not confirmed** by the available records.

## 7. Live RLS Policy Snapshot

Every listed table currently has RLS enabled. `PUBLIC` is shown as the policy role where Postgres returned `{public}`. All policies below are permissive.

| Table | Policy | Command | Roles | USING | WITH CHECK |
|---|---|---|---|---|---|
| `public.profiles` | `Allow public access to profiles` | ALL | PUBLIC | `true` | `true` |
| `public.messages` | `Allow public access to messages` | ALL | PUBLIC | `true` | `true` |
| `public.chat_sync_pipe` | `Allow public access to sync pipe` | ALL | PUBLIC | `true` | `true` |
| `public.friday_messages` | `Allow public access to AI messages` | ALL | PUBLIC | `true` | `true` |
| `public.friday_memories` | `Allow public access to friday_memories` | ALL | PUBLIC | `true` | `true` |
| `public.wishlist_items` | `wishlist_items_delete` | DELETE | PUBLIC | `true` | — |
| `public.wishlist_items` | `wishlist_items_insert` | INSERT | PUBLIC | — | `true` |
| `public.wishlist_items` | `wishlist_items_select` | SELECT | PUBLIC | `true` | — |
| `public.wishlist_items` | `wishlist_items_update` | UPDATE | PUBLIC | `true` | — |
| `public.wishlist_reactions` | `wishlist_reactions_delete` | DELETE | PUBLIC | `true` | — |
| `public.wishlist_reactions` | `wishlist_reactions_insert` | INSERT | PUBLIC | — | `true` |
| `public.wishlist_reactions` | `wishlist_reactions_select` | SELECT | PUBLIC | `true` | — |
| `public.wishlist_reactions` | `wishlist_reactions_update` | UPDATE | PUBLIC | `true` | — |
| `public.wishlist_ratings` | `wishlist_ratings_delete` | DELETE | PUBLIC | `true` | — |
| `public.wishlist_ratings` | `wishlist_ratings_insert` | INSERT | PUBLIC | — | `true` |
| `public.wishlist_ratings` | `wishlist_ratings_select` | SELECT | PUBLIC | `true` | — |
| `public.wishlist_ratings` | `wishlist_ratings_update` | UPDATE | PUBLIC | `true` | — |
| `public.phestydrop_releases` | `Allow public read access to phestydrop_releases` | SELECT | `{anon,authenticated}` | `true` | — |
| `public.access_keys` | **No policies found** | — | — | — | — |

For INSERT, Postgres returned no `USING` expression; for other commands where no check expression applies, it returned no `WITH CHECK`.

## 8. Cross-Reference with the Phase 2 Report

The Phase 2 report’s Friday probe was a **false positive for PostgREST acceptance**. The deployed `friday-pending-messages` function disables gateway JWT verification and performs its own HS256 signature check using the same `SUPABASE_JWT_SECRET`-then-`SUPABASE_SERVICE_ROLE_KEY` fallback. Its checker validates the signature and expiration, then extracts `persona` or `sub`; it does not enforce PostgREST’s key set or test PostgREST JWT acceptance. Its database query uses the service-role client, so a 200 from that Edge Function does not demonstrate that PostgREST accepts the client’s Authorization bearer.

The live REST reproduction now demonstrates the distinction directly: the same freshly minted token shape gets 401 from PostgREST on all three tables, while missing Authorization and anon bearer requests get 200 on the public SELECT tables. Phase 2 verified token propagation and the custom Friday verifier path, not the downstream PostgREST verifier.

## 9. Top Remediation Candidates for the Next Phase

1. **Use a signing key supported by the project’s active PostgREST/Auth verifier.** If retaining custom persona JWTs, align signing with the project’s configured JWT signing mechanism and key type; do not use the service-role API key as an HMAC secret. This preserves the custom persona model, but depends on safely provisioning/rotating a compatible signing key and confirming the hosted verifier’s accepted algorithms and claim requirements.
2. **Use Supabase Auth-issued access tokens and map the app persona into supported claims.** This aligns token issuance, signing-key rotation, and PostgREST validation with the platform. It likely requires broader identity/session integration and may change the current passkey-to-persona flow.

These are candidates for the next implementation phase, not changes made by this audit.

## 10. Remaining Unknowns

- Hosted PostgREST’s exact configured audience and issuer constraints were not exposed through the available read-only interfaces; the signature/key-type error occurs before those constraints can be evaluated.
- The direct secret values were intentionally not retrieved, so raw-value equality was not tested. The deployed function’s fallback and the live verifier’s accepted key metadata establish the operative incompatibility without that comparison.
- The reload logs do not disclose who initiated the reload. No migration or function deployment occurred in the window, but platform-versus-dashboard attribution remains unproven.
- Hosted Auth custom-hook configuration could not be confirmed; local config is not authoritative for the deployed project.
