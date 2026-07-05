\# Agent Rules — Non-Negotiable



\## Never Do

\- Rename public APIs

\- Change package names

\- Refactor unrelated code

\- Remove comments unless explicitly asked

\- Modify theme files (`ui/theme/`) without permission

\- Replace Room with another database

\- Remove offline-first behavior



\## Always Do

\- Preserve existing behavior

\- Prefer `StateFlow` over `LiveData`

\- Explain why a change is needed

\- Follow `ARCHITECTURE.md` conventions

\- Run a mental check: "Does this break offline mode?"


\## Database Rules
- ALWAYS check `supabase/schema.sql` before changing model fields or types
- ALWAYS check `supabase/policies.sql` before adding CRUD operations
- Treat `supabase/schema.sql` as the source of truth for database structure
- Treat `supabase/policies.sql` as the source of truth for access control
- If Kotlin code and schema disagree, the schema is correct — update Kotlin, not the schema
- If a required RLS policy is missing, flag it as an issue — do NOT implement workarounds in code
- Not every table needs all CRUD operations — check policies.sql to see what's actually enabled

\## Before Any Edit

1\. Read `ARCHITECTURE.md`

2\. Read `AGENT\_RULES.md`

3\. Check if feature has a spec in `docs/specs/`

