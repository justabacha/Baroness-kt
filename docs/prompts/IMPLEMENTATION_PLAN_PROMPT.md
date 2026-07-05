Read docs/ARCHITECTURE.md, docs/AGENT_RULES.md, reports/[REPORT_NAME].md, and all files mentioned in the report.

For each Critical and Medium issue, propose a fix plan:

## Issue #[NUMBER]: [Title]

### Current State
[What the code does now]

### Proposed Change
[Exact file(s) to modify and what to change]

### Why This Approach
[Why this fix is correct]

### Risks
[What could go wrong]

### Files to Touch
- `path/to/file.kt` — [specific change]
- `path/to/file.kt` — [specific change]

### Files to NOT Touch
- `path/to/file.kt` — [why it's unrelated]

---

After all issues, provide:
- Total files modified: [count]
- Estimated complexity: [Low/Medium/High]
- Confidence level: [1-10]

Do NOT implement any changes. Wait for human approval before proceeding.

Write your complete implementation plan to reports/PLAN_[YYYY-MM-DD]_[HHMMSS].md following the structure in REVIEW_REPORT.md.