\# Agent Behavior Reference



\## Aider (Gemini 2.5 Flash)



\### Strengths

\- Unlimited usage (free Gemini API key)

\- Fast, reads entire codebase context

\- Excellent for multi-file refactors

\- Good at generating structured reports

## Verification Stage
- NEVER use Aider for verification — it will edit despite instructions
- ALWAYS use Windsurf for verification — respects read-only boundaries
- Verification checks: issue status, rule violations, unintended changes


### Weaknesses
- AUTO-COMMITS by default — but CAN be disabled with `--no-auto-commits` flag
- Ignores "do not edit" when it sees "obvious fixes"
- Unicode emoji in prompts causes encoding crashes
- Will create new files without explicit permission


\### Best Practices

\- ALWAYS check `git log` after Aider runs

\- Use `--no-auto-commit` flag if available

\- Avoid emoji in prompts (use text: "Critical" instead of 🔴)

\- Explicitly list files it should NOT touch

\- Revert immediately if it goes off-script



\### Use For

\- Heavy analysis and report generation

\- Multi-file implementation (with close supervision)

\- Backup when other agents hit limits



\---



\## Windsurf / Cascade (Codeium)



\### Strengths

\- Respects "do not modify" instructions

\- Writes files to disk correctly

\- Generous daily/weekly quota (resets daily)

\- Full IDE experience



\### Weaknesses

\- Quota-based (not unlimited)

\- Cannot run without IDE open



\### Best Practices

\- Use for controlled report generation

\- Use for daily coding and UI work

\- Save daily quota for important tasks



\### Use For

\- Primary IDE for daily development

\- Report generation (reliable, controlled)

\- Implementation when precision matters



\---



\## Antigravity (Google)



\### Strengths

\- Respects boundaries well

\- Full IDE experience

\- Access to multiple models (Gemini, Claude)



\### Weaknesses

\- Weekly lockout after \~20-30 minutes of heavy use

\- Free tier is basically a trial



\### Best Practices

\- Use for emergency backup only

\- Save weekly quota for critical fixes

\- Don't rely on it as primary tool



\### Use For

\- Emergency implementation when others are capped

\- Quick fixes that don't need deep context



\---



\## Codex (OpenAI)



\### Strengths

\- Deep analysis capabilities

\- Excellent at architecture reviews

\- Good at tracing dependencies



\### Weaknesses

\- Tight usage limits (depreciating)

\- Web-based only (no local file access directly)



\### Best Practices

\- Save for heavy analysis only

\- Don't waste credits on simple tasks

\- Use when other agents can't handle complexity



\### Use For

\- Initial project audits

\- Complex architecture reviews

\- When other agents miss deep issues



\---



\## General Rules for All Agents



1\. \*\*Git checkpoint before every agent session\*\*

2\. \*\*Explicit boundaries in every prompt\*\* — list what NOT to do

3\. \*\*Never assume agent read AGENT\_RULES.md\*\* — include key rules in prompt

4\. \*\*Verify output before trusting\*\* — check `git diff` after every run

5\. \*\*Rotate agents to avoid limit exhaustion\*\*

6\. \*\*Emoji causes crashes in some agents\*\* — use text labels instead

