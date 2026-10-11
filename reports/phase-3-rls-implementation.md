# Phase 3 — Strict RLS Enforcement

## Final Status

**READY FOR SMOKE TEST.** The approved RLS migration applied successfully, all REST authorization checks passed, both trigger behaviors passed using authorized transaction-scoped synthetic rows, and the synthetic rows were rolled back. The debug APK built successfully. No credentials or JWTs are included in this report.

## Safety Model and Preconditions

- The operator waived the server-side backup requirement because the project has no physical backups and is on the Free plan.
- The operator waived the DDL schema dump. No `supabase db dump` or Docker attempt was made during this resume.
- The pre-migration rollback reference remains at [`phase-3-pre-migration-policies.txt`](phase-3-pre-migration-policies.txt): 30 policy rows, 2,672 bytes, captured at `2026-10-10 01:34:02.296194+00`.
- Fresh check: `SELECT count(*) FROM public.gallery_items` returned **0**. Search of `app/src/main` and `supabase/functions` returned **no references**.
- Fresh read-only check found **0 rows** in `public.messages`; the operator authorized transaction-scoped synthetic rows for trigger tests, with explicit rollback and a subsequent zero-row check.
- The six pre-migration avatar-bucket policies on `storage.objects` matched the migration DROP list.
- The two valid passkeys were read from `public.access_keys` only into process memory to obtain test JWTs. Passkeys and JWTs were not printed, saved, or included in logs or this report.

## Table Classification

- **Tightened:** `profiles`, `messages` (including `enforce_message_immutability`), `chat_sync_pipe`, `friday_messages`, `friday_memories`, `wishlist_items`, `wishlist_reactions`, `wishlist_ratings`, and avatar policies on `storage.objects`.
- **Dropped:** `public.gallery_items`, confirmed empty and unreferenced on 2026-10-10.
- **Left untouched:** `typing_status` and `backup_log` (existing permissive policies preserved); `friday_entities`, `friday_memory_history`, `friday_sessions`, `friday_action_requests`, `friday_api_usage`, `migration_history`, and `access_keys` (zero-policy state preserved); `phestydrop_releases` (existing public SELECT policy preserved).

The migration file header matches this classification. It contains no DROP or CREATE policy statements targeting leave-as-is tables. Post-migration policy inspection found the existing three policies on `backup_log`, `typing_status`, and `phestydrop_releases` unchanged; the other listed leave-as-is tables remain with zero policies.

## Android Avatar Path Change

- File: `app/src/main/java/com/baroness/app/modules/ProfileManager.kt`
- Method: `ProfileManager.saveSetup`
- Changed at lines 74-85; upload URL construction is in `uploadAvatar` at line 114.
- Old object path: `avatar_<timestamp>.jpg`
- New object path: `<currentPersonaId>/avatar_<timestamp>.jpg`
- Existing avatar deletion now retains the full object path, including any persona folder, instead of removing only the final filename.
- `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL**. APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk` (45,902,355 bytes). No device installation was performed.

## Migration Application

- Method: Supabase CLI remote SQL execution using `supabase db query --linked --file reports\phase-3-proposed-rls-migration.sql --output-format json`. The migration was invoked once; it was not retried.
- Applied at (UTC, timestamp captured immediately before the successful CLI invocation): `2026-10-10T02:17:07.559Z`.
- SHA-256 of `reports/phase-3-proposed-rls-migration.sql`: `C13A73A72DE911126D09328D686076FEAED241DB0F4B9421B455A1AC1EB66AA7`.
- Outcome: successful. `public.gallery_items` is absent; `enforce_message_immutability_trigger` and its function are present.
- No rollback was performed.

## REST Verification Results

Equivalent HTTP requests were issued with .NET `HttpClient` against the specified REST paths using the anon API key and, where required, the appropriate verify-passkey-issued bearer token. Responses below are verbatim; credentials are redacted and are not part of the response bodies.

| Test | Request | HTTP | Response body |
|---|---|---:|---|
| Anonymous Friday messages | `GET /rest/v1/friday_messages?limit=1` | 200 | `[]` |
| Anonymous messages | `GET /rest/v1/messages?limit=1` | 200 | `[]` |
| Anonymous sync pipe | `GET /rest/v1/chat_sync_pipe?limit=1` | 200 | `[]` |
| Anonymous public releases | `GET /rest/v1/phestydrop_releases?limit=1` | 200 | `[{"id":"3356c39f-80b6-4737-bf0c-b728d02dff9d","version_code":2,"version_name":"1.0.1","download_url":"https://github.com/justabacha/Baroness-kt/releases/download/v1.0.1/baroness-v1.0.1.apk","changelog":"• PhestyDrop Live Test Successful!\r\n• New Verification Badge Screen Unlocked ⚡","is_mandatory":false,"min_supported_version_code":1,"created_at":"2026-09-28T21:00:11.571748+00:00"}]` |
| Phesty reads own Friday data | `GET /rest/v1/friday_messages?owner_id=eq.phesty_official&limit=1` | 200 | `[{"id":"7e865ee2-5173-4847-8c12-430f83bb5d06","owner_id":"phesty_official","sender":"phesty_official","message":"so what's new","created_at":"2026-10-09T23:59:58.542+00:00","session_id":"b74c762d-6afb-49e1-a459-d77c5bc43dbf","reflected_at":"2026-10-10T00:30:05.541+00:00","is_pinned":false,"is_deleted":false,"reactions":{},"reply_to_id":null,"status":"SENT","is_proactive":false,"is_command":false,"sentiment":null}]` |
| Phesty reads own sync pipe | `GET /rest/v1/chat_sync_pipe?recipient_id=eq.phesty_official&limit=1` | 200 | `[{"id":"48938948-7f73-4a9b-b22a-45b0895f2709","recipient_id":"phesty_official","payload":{"type": "START_TYPING", "conversationId": "friday"},"created_at":"2026-10-09T23:59:59.766215+00:00"}]` |
| Phesty cross-persona Friday denial | `GET /rest/v1/friday_messages?owner_id=eq.baroness_official&limit=1` | 200 | `[]` |
| Phesty cross-persona sync denial | `GET /rest/v1/chat_sync_pipe?recipient_id=eq.baroness_official&limit=1` | 200 | `[]` |
| Phesty updates Baroness profile | `PATCH /rest/v1/profiles?id=eq.baroness_official`, JSON `{"display_name":"hacked"}`, `Prefer: return=representation` | 200 | `[]` |
| Phesty reads shared wishlist | `GET /rest/v1/wishlist_items?limit=5` | 200 | `[{"id":146,"text":"DYE DATE 🎨🖌️","wish_date":"2026-12-24","status":"planning","creator_id":"phesty_official","created_at":"2026-08-26T05:30:48.596+00:00","updated_at":"2026-08-26T05:30:49.394733+00:00"}]` |
| Baroness reads Phesty's shared wishlist item | `GET /rest/v1/wishlist_items?limit=5` | 200 | `[{"id":146,"text":"DYE DATE 🎨🖌️","wish_date":"2026-12-24","status":"planning","creator_id":"phesty_official","created_at":"2026-08-26T05:30:48.596+00:00","updated_at":"2026-08-26T05:30:49.394733+00:00"}]` |

The cross-persona profile PATCH returned an empty representation, so no profile row was updated. The live wishlist currently contains one Phesty-created item and no Baroness-created items; Baroness could read the Phesty item, confirming cross-persona shared visibility with the available data.

Both `verify-passkey` calls returned HTTP 200. The access tokens were confirmed in memory to contain the expected top-level persona claims; they were not printed.

## Message Trigger Tests

Because the live `messages` table was empty, and per the operator's authorization, the tests used a synthetic message inserted and tested inside a transaction that was explicitly rolled back. No HTTP message PATCH could target a real row.

- **Forbidden content edit:** as `baroness_official` (the receiver), changing the Phesty message's `content` raised the trigger's expected exception, `Not authorized to update immutable message content or identifiers`. **PASS.**
- **Allowed metadata edit:** as `baroness_official`, changing only `read_at` succeeded. **PASS.**
- Follow-up query found **0** rows with the synthetic test conversation ID. The synthetic rows were not persisted.

## Post-Migration State and Handoff

- Tightened policies and trigger are present; `gallery_items` is dropped.
- Leave-as-is policy state is unchanged.
- No verification failure occurred; rollback was not exercised.
- **READY FOR SMOKE TEST.** Install the debug APK and run the operator's manual chat, edit, Friday, wishlist, avatar upload, and relogin checks.

## Discovered — Out of Scope

The UI gates message editing to the sender, but `ChatRepository.editMessage` itself does not perform an ownership check. The database trigger now enforces sender-only content edits; no unrelated Android changes were made.
