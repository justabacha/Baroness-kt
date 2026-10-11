# Phase 3 Open Questions & Operator Decisions Required

Before implementing the Phase 3 RLS migration in production, the operator must review and confirm the following decisions:

1. **Gallery Items (`gallery_items`) Semantics**:
   - *Context*: `gallery_items` is an unreferenced legacy table in the public schema with permissive policies.
   - *Decision Required*: Should `gallery_items` be retired and dropped, or should it be restricted to owner-only (`uploaded_by = auth.jwt() ->> 'persona'`) or shared collaboration? (Current draft keeps shared collaboration).

2. **Backup Log (`backup_log`) Semantics**:
   - *Context*: `backup_log` tracks chat backups per persona (`persona_id`).
   - *Decision Required*: Confirm that `persona_id = auth.jwt() ->> 'persona'` is the correct scoping rule for backup logs.

3. **Friday Action Requests & API Usage**:
   - *Context*: `friday_action_requests` and `friday_api_usage` use `owner_id`.
   - *Decision Required*: Confirm that client-side access (if any) or Edge Function access via user JWT correctly scopes to `owner_id = auth.jwt() ->> 'persona'`.

4. **Avatar Upload Path Client Prerequisite**:
   - *Context*: The storage RLS policy enforces `(storage.foldername(name))[1] = (auth.jwt() ->> 'persona')`, which requires avatar files to be uploaded to `<persona>/avatar_<timestamp>.jpg` instead of root `avatar_<timestamp>.jpg`.
   - *Decision Required*: Confirm that `ProfileManager.kt` will be updated during Phase 3 implementation to prefix avatar uploads with the active persona ID.

5. **Message Deletion Behavior (`messages`)**:
   - *Context*: The client implements "delete for me" / "delete for everyone" via soft deletes (`is_deleted = true` update) and hard deletes (`ChatRepository.deleteMessage`).
   - *Decision Required*: Confirm that DELETE on `messages` should remain restricted to `sender_id = auth.jwt() ->> 'persona'`, while recipients update metadata (`is_deleted`, `read_at`, `reactions`) protected by the immutability trigger.
