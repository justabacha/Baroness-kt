Read docs/ARCHITECTURE.md, docs/AGENT_RULES.md, reports/[REPORT_NAME].md, and the modified files from the implementation.

Verify: Did the implementation actually solve the stated issues?

Check:
- Issue #[NUMBER]: Was it fixed correctly?
- Were any AGENT_RULES.md rules violated?
- Does offline-first behaviour remain intact?
- Are there unintended side effects?
- Did the agent touch any files NOT listed in the report?

Do NOT change any code. Only report your findings as PASS or FAIL for each issue, with explanation.

Output format:
- Issue #1: [PASS/FAIL] — [explanation]
- Issue #2: [PASS/FAIL] — [explanation]
- Rule violations: [list or "None"]
- Unintended changes: [list or "None"]
- Overall verdict: [READY TO COMMIT / NEEDS FIX]

Write your complete verification findings to reports/VERIFY_[YYYY-MM-DD]_[HHMMSS].md following the structure in REVIEW_REPORT.md.