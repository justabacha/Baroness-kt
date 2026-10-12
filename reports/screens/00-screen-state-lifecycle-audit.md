# Screen State Lifecycle Audit — Instant-Render Readiness Report

## 1. Executive Summary

This audit evaluates screen state initialization, ViewModel scoping, and data hydration timing across non-settings screens in the Baroness application to determine readiness for instant rendering (zero flicker, no placeholder delay).

- **Total Screens Evaluated**: 6 primary functional screens (excluding Settings Center & sub-pages, Chat Room, and transient overlays).
- **Classification Counts**:
  - **CLEAN**: 2 (`WishlistScreen`, `GateScreen`)
  - **PLACEHOLDER_FLICKER**: 4 (`DashboardScreen`, `ChatListScreen`, `PhotosScreen`, `ProfileSetupScreen`)
  - **RELOAD_ON_RETURN**: 0
  - **UNKNOWN**: 0
- **Primary Finding**: While Room database-backed and Room/DataStore-backed screens (`WishlistScreen`) render instantly via upstream `StateFlow` initial values, network-bound or async profile/photo repositories (`DashboardScreen`, `ChatListScreen`, `PhotosScreen`, `ProfileSetupScreen`) initialize loading flags to `true` or empty states, causing temporary spinners or placeholder flicker on first frame.

---

## 2. Screen Inventory Table (Section 1)

| Screen Composable | File | Route (if any) | Top-Level State Source | Classification |
| :--- | :--- | :--- | :--- | :--- |
| `DashboardScreen` | `screens/DashboardScreen.kt` | `"dashboard"` | `DashboardViewModel` (AndroidViewModel) | PLACEHOLDER_FLICKER |
| `ChatListScreen` | `screens/ChatListScreen.kt` | `"chat_list"` | `ChatListViewModel` (ViewModel) | PLACEHOLDER_FLICKER |
| `WishlistScreen` | `screens/WishlistScreen.kt` | `"wishlist"` | `WishlistViewModel` (ViewModel) + `WishlistRepository` | **CLEAN** |
| `PhotosScreen` | `screens/PhotosScreen.kt` | `"photos"` | `PhotosViewModel` (ViewModel) + `PhotoRepository` | PLACEHOLDER_FLICKER |
| `ProfileSetupScreen` | `screens/ProfileSetupScreen.kt` | `"profile_setup/{personaId}"` | `ProfileSetupViewModel` (ViewModel) | PLACEHOLDER_FLICKER |
| `GateScreen` | `screens/GateScreen.kt` | `"gate"` | `GateViewModel` (ViewModel) | **CLEAN** |
| *Settings Sub-Pages (12 pages)* | `screens/settings/*.kt` | `"settings_*"` | `SettingsViewModel` + `SettingsRepository` | **CLEAN (Verified)** |

---

## 3. Per-Screen State Analysis (Section 2)

### 3.1 `DashboardScreen` (`screens/DashboardScreen.kt`)
- **ViewModel Scope**: Created via `viewModel(factory = DashboardViewModelFactory(...))` inside the composable (screen-scoped).
- **Initial State**: `_isInitialLoading = MutableStateFlow(true)`, `_userProfile = MutableStateFlow(null)`.
- **Data Hydration Timing**: `init` block launches `loadInitialData()` which asynchronously reads DataStore (`userProfile`) and resolves local avatar cache via `AvatarRepository`. Also triggers network/weather updates.
- **Re-fetch on Navigation**: `LaunchedEffect(currentEntry)` re-triggers `viewModel.reloadProfile()` every time back stack entry returns to `"dashboard"`.
- **Flicker Classification**: **PLACEHOLDER_FLICKER** (initial loading state / null profile before DataStore read completes).

### 3.2 `ChatListScreen` (`screens/ChatListScreen.kt`)
- **ViewModel Scope**: Created via `viewModel(factory = ChatListViewModelFactory(...))` inside the composable (screen-scoped).
- **Initial State**: `_isInitialLoading = MutableStateFlow(true)`, `_conversations = MutableStateFlow(emptyList())`.
- **Data Hydration Timing**: `init` loads cached profile from `StorageManager` synchronously in coroutine and collects Room DAO flow (`messageDao.getLastMessageForConversation`). Shows `CircularProgressIndicator` if `isInitialLoading && conversations.isEmpty()`.
- **Re-fetch on Navigation**: ViewModel is screen-scoped; re-initializes on return, collecting Room flow.
- **Flicker Classification**: **PLACEHOLDER_FLICKER** (renders `CircularProgressIndicator` spinner on first frame before DataStore cache read completes).

### 3.3 `WishlistScreen` (`screens/WishlistScreen.kt`)
- **ViewModel Scope**: Created via `viewModel(factory = WishlistViewModelFactory(...))` (screen-scoped).
- **Initial State**: `wishes` and `stats` use `SharingStarted.WhileSubscribed(5000)` with `initialValue = emptyList()` and `WishStats(0, 0)`.
- **Data Hydration Timing**: Room database provides cached data immediately on startup via Flow. Background sync triggered via `LaunchedEffect(Unit)`.
- **Re-fetch on Navigation**: Upstream Room Flow maintains state without blocking initial render.
- **Flicker Classification**: **CLEAN** (renders instantly from Room cache without blocking on network).

### 3.4 `PhotosScreen` (`screens/PhotosScreen.kt`)
- **ViewModel Scope**: Created via `viewModel()` (screen-scoped).
- **Initial State**: `_uiState = MutableStateFlow<PhotosUiState>(PhotosUiState.Loading)`.
- **Data Hydration Timing**: `init` calls `loadPhotos()`, setting state to `Loading` while fetching from `PhotoRepository`.
- **Re-fetch on Navigation**: ViewModel is recreated on navigation return, triggering `loadPhotos()` from scratch.
- **Flicker Classification**: **PLACEHOLDER_FLICKER** (shows loading state/spinner on first frame).

### 3.5 `ProfileSetupScreen` (`screens/ProfileSetupScreen.kt`)
- **ViewModel Scope**: Created via `viewModel(factory = ProfileSetupViewModelFactory(...))` (screen-scoped).
- **Initial State**: `isLoadingProfile = mutableStateOf(true)`.
- **Data Hydration Timing**: `init` calls `loadExistingProfile()`, reading DataStore synchronously-ish in coroutine, setting `isLoadingProfile.value = false` upon cache hit before fetching remote profile.
- **Re-fetch on Navigation**: Screen-scoped ViewModel re-runs `init` on re-entry.
- **Flicker Classification**: **PLACEHOLDER_FLICKER** (shows loading/placeholder state until profile cache resolves).

### 3.6 `GateScreen` (`screens/GateScreen.kt`)
- **ViewModel Scope**: Created via `viewModel(factory = GateViewModelFactory(...))` (screen-scoped).
- **Initial State**: Empty password string, loading false, error null.
- **Data Hydration Timing**: No asynchronous data loading required for initial render.
- **Re-fetch on Navigation**: Local form state.
- **Flicker Classification**: **CLEAN** (renders instantly with no async data dependency).

---

## 4. Excluded Screens (Section 3)

- **Settings Center & Sub-Pages (`screens/settings/*`)**: Explicitly excluded per prompt instructions as they were already verified clean and refactored with synchronous DataStore seeding.
- **Chat Room Screen (`ChatRoomScreen.kt`)**: Excluded per prompt instructions because it manages active chat session message streams and real-time socket/database state, not a static render case.
- **Transient Modals / Sheets / Overlays**: Excluded as they are contextual components rendered on top of host screens rather than top-level navigation destinations.

---

## 5. Shared State Sources (Section 4)

- **`SettingsViewModel` / `SettingsRepository`**: Holds active theme, font, wallpaper, sound, and notification settings. Observed across multiple screens via `collectAsStateWithLifecycle()`. Uses DataStore with immediate reactive StateFlows.
- **`StorageManager`**: Low-level wrapper over SharedPreferences / DataStore. Used by ViewModels for synchronous/asynchronous local string retrieval (`userProfile`, `currentPersonaId`, etc.).
- **`WishlistRepository`**: Room database singleton providing reactive `Flow<List<Wish>>` and `Flow<WishStats>` to `WishlistViewModel`. Cached locally, enabling instant render.

---

## 6. Fix Recommendations Per Affected Screen (Section 5)

1. **`DashboardScreen` (`DashboardViewModel`)**:
   - Seed `userProfile` synchronously from `StorageManager` during ViewModel initialization (`init` block or property initializer) rather than waiting for `viewModelScope.launch` to assign `_userProfile.value`, eliminating profile header flicker.
2. **`ChatListScreen` (`ChatListViewModel`)**:
   - Seed `otherProfile` and initial conversation state synchronously in ViewModel constructor from `StorageManager` cache instead of defaulting `isInitialLoading` to `true`.
3. **`PhotosScreen` (`PhotosViewModel`)**:
   - Pre-seed or cache last known photo list in local storage / Room so `PhotosUiState` starts in `Success` with cached items rather than `PhotosUiState.Loading`.
4. **`ProfileSetupScreen` (`ProfileSetupViewModel`)**:
   - Initialize `isLoadingProfile` to `false` when cached DataStore `userProfile` exists, preventing initial loading spinner delay.

---

## 7. Existing Patterns to Reuse (Section 6)

- **Settings Center Seeding Pattern**: The settings sub-pages successfully eliminate flicker by initializing StateFlows with cached DataStore values or leveraging `SettingsRepository` with immediate cached defaults.
- **Room Repository Flow Pattern**: `WishlistRepository` successfully uses Room flows with `SharingStarted.WhileSubscribed(5000)` and `initialValue`, providing instant-render capability without loading flags. Reusing Room or synchronous DataStore initializers across `DashboardViewModel`, `ChatListViewModel`, and `PhotosViewModel` will bring them to parity.
