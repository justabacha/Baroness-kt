Read docs/ARCHITECTURE.md, docs/AGENT_RULES.md, and reports/[REPORT_NAME].md.

Fix ONLY the issues marked Critical and Medium from the report. Do NOT touch Low Priority issues unless explicitly asked.

Rules:
- Fix ONLY files listed in the report
- Do NOT refactor unrelated code
- Do NOT rename anything
- Do NOT change app behaviour — only fix stated problems
- Prefer StateFlow over LiveData
- Preserve offline-first behaviour
- Explain why each change is needed
- After each fix, confirm the file still compiles

If an issue is unclear, ask before proceeding. Do NOT guess.

Output: List which files you modified and what you changed in each.