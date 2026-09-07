# REVIEW WORK PACKAGE: WP-{N}_{NAME}

> 📂 **WP Blueprint**: `docs/chatroom/work-packages/WP-{N}_{NAME}.md`
> 📂 **Execution Report**: `docs/chatroom/reports/WP-{N}_{NAME}_EXECUTION_REPORT.md`

## Your Task
Read the WP blueprint and execution report. Then inspect the ACTUAL codebase to verify the implementation is correct, complete, and follows specifications.

## Source Documents (Read These First)
1. docs/chatroom/chatroom_architecture.md — Core architecture decisions
2. docs/chatroom/chatroom_database.md — Database schema and sync strategy
3. docs/chatroom/chatroom_ui_ux.md — UI/UX specifications
4. docs/chatroom/chatroom_file_structure.md — File locations and naming
5. docs/chatroom/chatroom_implementation.md — Master checklist and dependencies
6. docs/chatroom/work-packages/WP-{N}_{NAME}.md — The WP blueprint
7. docs/chatroom/reports/WP-{N}_{NAME}_EXECUTION_REPORT.md — What the executor claimed to build

## What You Must Verify

### 1. Files Exist
- Every file listed in the execution report actually exists at the claimed path
- Every file claimed as "created" is present
- Every file claimed as "modified" shows actual changes

### 2. Code Quality
- Follows Kotlin coding standards (naming, structure, coroutines, StateFlow)
- No hardcoded secrets, API keys, or connection strings
- Reuses existing infrastructure where specified
- Single responsibility per file
- No unnecessary abstractions

### 3. Spec Compliance
- Implementation matches WP blueprint tasks
- Implementation matches architecture.md decisions
- Database operations match database.md schema
- UI components match ui_ux.md specifications
- File locations match file_structure.md

### 4. Compilation
- Project compiles successfully
- No new warnings introduced
- All existing code still compiles

### 5. Completeness
- All tasks in WP blueprint §3.1 are addressed
- All exit criteria in WP blueprint §5 are met
- Manual steps are documented and verifiable

## Output Rules
- Write review to docs/chatroom/reports/WP-{N}_{NAME}_EXECUTION_REVIEW_REPORT.md with:
    - Overall verdict: ✅ APPROVED / ⚠️ NEEDS_REVISION / ❌ REJECTED
    - File-by-file verification (exists, correct, issues)
    - Spec compliance checklist
    - Issues found (critical vs minor)
    - Recommendations for fixes
    - Whether next WP can proceed

## Before You Finish
- [ ] All files in execution report inspected
- [ ] All specs compared against implementation
- [ ] Review report written to disk
- [ ] Verdict clear and justified

## Final Output
List the review report path and verdict.

Do not proceed to next WP unless verdict is APPROVED.

🎯 CURRENT TASK
Review WP-2: Repository & Sync Layer
Read docs/chatroom/work-packages/WP-2_REPOSITORY_SYNC_LAYER.md and docs/chatroom/reports/WP-2_REPOSITORY_SYNC_LAYER_EXECUTION_REPORT.md. Inspect the actual codebase. Produce docs/chatroom/reports/WP-2_REPOSITORY_SYNC_LAYER_REVIEW_REPORT.md.