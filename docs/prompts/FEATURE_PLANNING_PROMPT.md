You are a senior product manager and Android architect. Based on everything we've discussed about the [FeatureName] feature for my Kotlin Android app (Baroness-kt), write a complete feature plan.

Your job: Format our discussion into a structured plan I can save as `docs/plans/FEATURE_[FeatureName].md` and hand to an AI agent for implementation.

## App Context
- Jetpack Compose UI
- MVVM architecture with StateFlow
- Room database for local caching
- Supabase for remote data and realtime
- Offline-first philosophy
- Navigation with Compose Navigation

## Output Format

Write the plan in this exact structure:

```markdown
# Feature: [FeatureName]

## Overview
[2-3 sentences: what this feature does and why]

## User Stories
- As a [type of user], I want to [action] so that [benefit]
- As a [type of user], I want to [action] so that [benefit]

## Screen Layout / Design
[Describe each section of the screen top to bottom]
- Top bar: [what it shows, any buttons]
- Main content: [what appears here]
- Bottom section: [buttons, inputs, etc.]
- Empty state: [what shows when no data]
- Loading state: [what shows while loading]
- Error state: [what shows on error]

## Interactions & Behavior
- Tap [element]: [what happens]
- Swipe [element]: [what happens]
- Pull-to-refresh: [yes/no, what it does]
- Back button: [what happens]

## Data Requirements
- What data to display: [list each piece of data]
- Data source: [Room local / Supabase remote / Both]
- Realtime updates needed: [yes/no]
- Offline support: [how it works without network]
- Sync strategy: [when and how data syncs]

## Files to Create
[List every file the agent should create]
- `screens/[Feature]Screen.kt` — [purpose]
- `viewmodels/[Feature]ViewModel.kt` — [purpose]
- `repository/[Feature]Repository.kt` — [purpose]
- `models/[Feature]Models.kt` — [data models]
- `components/[Feature]/[Component].kt` — [reusable UI pieces]
- `data/local/dao/[Feature]Dao.kt` — [Room database access]
- `data/local/database/[Feature]Entity.kt` — [Room entity]

## Architecture Constraints
- Use StateFlow (not LiveData)
- Collect with collectAsStateWithLifecycle()
- State hoisting to ViewModel
- No business logic in Composables
- Follow existing patterns from [reference feature if similar]
- Preserve offline-first behavior

## Do NOT Modify
[List files the agent must NOT touch]
- [Existing screen that shouldn't change]
- [Existing ViewModel that shouldn't change]
- [Theme files without permission]

## Open Questions
[Anything still being decided]