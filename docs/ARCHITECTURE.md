\# Baroness-kt Architecture



\## App Layers

\- UI Layer: Jetpack Compose screens in `screens/`

\- ViewModel Layer: State holders in `viewmodels/`

\- Repository Layer: Data access in `repository/` and `data/`

\- Local Data: Room database in `data/local/`

\- Remote Data: Supabase API in `api/`

- Modules Layer: High-level application business logic in `modules/` (e.g., `AuthManager.kt`, `ProfileManager.kt`)
- Utility Layer: Reusable helper classes and cross-cutting concerns in `utils/` (e.g., `DateUtils.kt`, `StorageManager.kt`, `VibeManager.kt`, `VoiceManager.kt`)

\## Naming Conventions

\- Screens: `\[Feature]Screen.kt` (e.g., `PhotosScreen.kt`)

\- ViewModels: `\[Feature]ViewModel.kt`

\- Repositories: `\[Feature]Repository.kt`

\- Composables: Descriptive names, PascalCase



\## State Management

\- Use `StateFlow` for UI state

\- Use `MutableStateFlow` internally, expose as `StateFlow`

\- Collect in UI with `collectAsStateWithLifecycle()`



\## Offline-First Philosophy

\- All data must be cached locally via Room

\- API calls only sync local changes

\- App must function fully without network

\- Background sync is currently handled by calling SyncManager directly from repository methods

\- WorkManager integration for scheduled/reliable background sync is **planned but not yet implemented**



\## Realtime Philosophy

\- Supabase realtime subscriptions where needed

\- Local cache is source of truth

\- Realtime updates merge into local state



\## Compose Guidelines

\- State hoisting to ViewModels

\- No business logic in Composables

\- Use `remember` and `derivedStateOf` appropriately

