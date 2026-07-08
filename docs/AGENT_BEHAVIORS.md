# Agent Behavior Reference

---

## Aider (Gemini 2.5 Flash)

### Strengths

- Unlimited usage (free Gemini API key)
- Fast, reads entire codebase context
- Excellent for multi-file refactors
- Good at generating structured reports

### Verification Stage

- NEVER use Aider for verification — it will edit despite instructions
- ALWAYS use Windsurf for verification — respects read-only boundaries
- Verification checks:
    - Issue status
    - Rule violations
    - Unintended changes

### Weaknesses

- Auto-commits by default (disable with `--no-auto-commits`)
- Ignores "do not edit" if it believes a fix is obvious
- Unicode emoji in prompts may cause encoding issues
- Can create new files without explicit approval

### Best Practices

- Always check `git log` after every session
- Prefer `--no-auto-commits`
- Avoid emoji in prompts
- Explicitly list protected files
- Revert immediately if scope is exceeded

### Use For

- Heavy analysis
- Large multi-file refactors
- Structured report generation
- Backup implementation agent

---

## Windsurf / Cascade (Codeium)

### Strengths

- Excellent implementation agent
- Respects read-only boundaries
- Writes directly into project
- Strong understanding of project context
- Generous daily/weekly quota
- Full IDE experience

### Weaknesses

- Quota based
- Requires IDE

### Best Practices

- Use for implementation after planning
- Use for verification
- Save quota for medium and large features

### Use For

- Daily development
- Feature implementation
- Verification
- UI work
- Controlled refactoring

---

## GitHub Copilot

### Strengths

- Excellent Kotlin + Jetpack Compose generation
- Reads existing project structure well
- Produces modular code
- Fixes its own compile errors
- Uses Gradle to verify builds
- Clearly explains root cause of compiler errors
- Usually stays within requested scope

### Weaknesses

- Can generate incorrect imports/packages on first attempt
- Consumes monthly usage credits
- May simplify UI details if prompts lack context

### Best Practices

- Give complete feature context
- Require a build after implementation
- Ask it to explain compiler errors before fixing
- Keep implementation scope narrow
- Review git diff before committing

### Use For

- Compose screen creation
- Kotlin implementation
- UI components
- Small-to-medium features
- Compile-error fixing
- Gradle verification

---

## Gemini Code Assist (Google)

### Strengths

- Excellent understanding of Android architecture
- Strong Compose and MVVM knowledge
- Reviews business logic well
- Considers Offline-First architecture
- Good at identifying validation gaps
- Explains reasoning clearly

### Weaknesses

- Tends to over-architect solutions
- May recommend unnecessary file creation
- Sometimes proposes navigation or naming changes outside scope
- Authentication may require correct Google account permissions

### Best Practices

- Use for reviews before implementation
- Tell it explicitly:
    - Do not edit files
    - Do not refactor
    - Recommend only
- Ask for implementation plans before writing code
- Reject unnecessary architecture changes

### Use For

- Code reviews
- Business rule validation
- MVVM reviews
- Compose reviews
- Offline-first reviews
- Performance analysis
- Android best practices

---

## Antigravity

### Strengths

- Respects boundaries
- Full IDE experience
- Access to multiple models

### Weaknesses

- Weekly lockout after heavy usage
- Free tier behaves like a trial

### Best Practices

- Emergency backup only
- Save quota for important work

### Use For

- Emergency implementation
- Quick fixes

---

## Codex (OpenAI)

### Strengths

- Deep architectural reasoning
- Excellent dependency tracing
- Produces comprehensive review reports
- Finds hidden design issues
- Excellent at project-wide analysis

### Weaknesses

- Tight monthly usage limits
- Web-first workflow

### Best Practices

- Reserve for architecture work
- Don't spend credits on simple fixes
- Export reports for implementation agents

### Use For

- Architecture audits
- Project health reviews
- Technical debt analysis
- Refactoring strategy

---

# General Rules for Every Agent

1. Git checkpoint before every session.
2. Define explicit scope.
3. Explicitly state what MUST NOT change.
4. Never assume the agent has read AGENT_RULES.md.
5. Review `git diff` before committing.
6. Rotate agents to preserve quotas.
7. Avoid emoji in prompts where compatibility is uncertain.
8. Require implementation agents to build the project before completion.
9. Require review agents to stop after recommendations.
10. Large features should follow this workflow:

Planning (ChatGPT)
↓
Architecture Review (Codex)
↓
Business Logic Review (Gemini)
↓
Implementation (Copilot or Windsurf)
↓
Verification Build (Copilot or Windsurf)
↓
Git Commit