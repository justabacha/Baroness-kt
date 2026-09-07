# REVIEW WP BLUEPRINT: WP-{N}_{NAME}

> 📂 **WP Blueprint**: `docs/chatroom/work-packages/WP-{N}_{NAME}.md`
> 📂 **Review Report**: `docs/chatroom/reports/WP-{N}_{NAME}_BLUEPRINT_REVIEW.md`

## Your Task
Read the WP blueprint and compare it against ALL specification documents. Verify the blueprint is complete, correct, and executable. Flag any gaps, contradictions, or missing details BEFORE execution begins.

## Source Documents (Read These First)
1. docs/chatroom/chatroom_architecture.md — Core architecture decisions
2. docs/chatroom/chatroom_database.md — Database schema and sync strategy
3. docs/chatroom/chatroom_ui_ux.md — UI/UX specifications
4. docs/chatroom/chatroom_file_structure.md — File locations and naming
5. docs/chatroom/chatroom_implementation.md — Master checklist and dependencies
6. docs/chatroom/work-packages/WP-{N}_{NAME}.md — The blueprint to review
7. Any previous WP execution reports (if building on prior work)

## What You Must Verify

### 1. Completeness
- All tasks from chatroom_implementation.md §Master Checklist are covered
- No missing files (create/modify/delete)
- No missing manual steps for owner
- Exit criteria are measurable and clear

### 2. Spec Compliance
- Tasks match architecture.md decisions (offline-first, sync strategy, etc.)
- Database operations match database.md schema
- UI components match ui_ux.md specifications
- File paths match file_structure.md conventions
- Naming follows existing project patterns

### 3. Dependencies
- Previous WPs listed in prerequisites are actually complete
- Blocks/blocked-by relationships are correct
- No circular dependencies

### 4. Feasibility
- Complexity estimate is realistic
- Estimated effort is reasonable
- Risks are identified with mitigation
- Manual steps are actionable and clear

### 5. Consistency
- No contradictions between this WP and previous WPs
- No contradictions between this WP and spec docs
- Terminology matches across all documents

## Output Rules
- Write review to docs/chatroom/reports/WP-{N}_{NAME}_BLUEPRINT_REVIEW.md with:
    - Overall verdict: ✅ APPROVED / ⚠️ NEEDS_REVISION / ❌ REJECTED
    - Section-by-section verification
    - Gaps found (critical vs minor)
    - Contradictions with specs
    - Missing tasks or files
    - Recommendations for fixes
    - Whether execution can proceed

## Before You Finish
- [ ] All spec docs compared against blueprint
- [ ] All previous WP reports checked (if applicable)
- [ ] Review report written to disk
- [ ] Verdict clear and justified

## Final Output
List the review report path and verdict.

Do not proceed to execution unless verdict is APPROVED.