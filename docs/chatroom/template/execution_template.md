# EXECUTE WORK PACKAGE: WP-{N}_{NAME}

> 📂 **WP Blueprints**: `docs/chatroom/work-packages/WP-{N}_{NAME}.md`
> 📂 **Execution Reports**: `docs/chatroom/reports/WP-{N}_{NAME}_EXECUTION_REPORT.md`

## Your Task
Read `docs/chatroom/work-packages/WP-{N}_{NAME}.md` and execute it completely.

## Source Documents (Read These First)
1. docs/chatroom/chatroom_architecture.md — Core architecture decisions
2. docs/chatroom/chatroom_database.md — Database schema and sync strategy
3. docs/chatroom/chatroom_ui_ux.md — UI/UX specifications
4. docs/chatroom/chatroom_file_structure.md — File locations and naming
5. docs/chatroom/chatroom_implementation.md — Master checklist and dependencies
6. Any spec docs referenced in WP-{N} §Entry Criteria

## Prerequisites Verified
- List completed WPs that must be done before this one
- Example: WP-01 ✅, WP-02 ✅

## What You Must Do
[Specific tasks from the WP blueprint — copy from WP-{N} §Tasks]

## Rules
- Do NOT modify files from previous WPs unless explicitly required.
- Do NOT hardcode API keys, secrets, or connection strings. Use BuildConfig fields or local.properties.
- Follow existing Kotlin coding standards (naming, structure, coroutines, StateFlow).
- Reuse existing infrastructure (SettingsViewModel, ChatTypography, PhestyText, etc.).
- All new composables must be preview-able.
- All existing code must still compile.

## Output Rules
- Write ALL deliverables to files. Do NOT dump code in chat.
- Create docs/chatroom/reports/WP-{N}_{NAME}_EXECUTION_REPORT.md with:
    - Complete file listing (path, purpose, status)
    - Manual steps for owner (Phesty)
    - Verification steps (build commands, preview checks)
    - Assumptions made
    - Compilation status
- Update local.properties.example if new variables introduced.
- Update any relevant READMEs.

## Before You Finish
- [ ] All files written to disk, not chat
- [ ] Execution report created at docs/chatroom/reports/WP-{N}_{NAME}_EXECUTION_REPORT.md
- [ ] Project compiles successfully
- [ ] All existing code still compiles
- [ ] No secrets in any committed file
- [ ] local.properties.example updated with new variables
- [ ] Manual steps documented for owner

## Final Output
List the execution report path and any files the owner must review.

Do not proceed to next WP. Wait for owner verification.

---

## 🎯 CURRENT TASK

**WP-2: Repository & Sync Layer**

Read `docs/chatroom/work-packages/WP-2_REPOSITORY_SYNC_LAYER.md` and execute completely.