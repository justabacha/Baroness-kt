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



\## Before Any Edit

1\. Read `ARCHITECTURE.md`

2\. Read `AGENT\_RULES.md`

3\. Check if feature has a spec in `docs/specs/`

