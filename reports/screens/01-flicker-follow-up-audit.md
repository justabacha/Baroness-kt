# Screen Flicker — Follow-Up Audit Report (Wishlist, ChatRoom, Dashboard, ChatList)

## 1. Executive Summary

This follow-up audit investigates the root causes of screen flicker and empty-first-frame rendering across four core functional screens: **Wishlist**, **ChatRoom**, **Dashboard**, and **ChatList**. 

- **Key Finding**: While Room database and DataStore caching mechanisms exist underneath, all four screens suffer from initial state placeholders (`emptyList()`, `ChatRoomUiState.Loading`, `isInitialLoading = true`, `null` profile) combined with screen-scoped ViewModels that reset state upon navigation return. This produces the operator-observed "empty container then items fill in" flicker pattern.

---

## 2. Wishlist — Honest Re-Examination (Section 1)

### 2.1 What Renders on the First Frame
- The `wishes` `StateFlow` in `WishlistViewModel` is created via `repository.getAllWishes().stateIn(..., initialValue = emptyList())`.
- **First Frame Behavior**: The initial emission is `emptyList()`. Consequently, the first frame renders an empty container or zero wish items before Room asynchronous database queries emit cached rows.

### 2.2 Why the Empty-First-Frame Happens
- **Line Reference**: `WishlistViewModel.kt`:
  ```kotlin
  val wishes: StateFlow<List<Wish>> = repository.getAllWishes()
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = emptyList()
      )
  ```
- Room DAO queries wrapped in Kotlin Flows emit asynchronously upon subscription; `stateIn` requires an explicit `initialValue`, which defaults to empty. There is no synchronous in-memory cache pre-population before subscription.

### 2.3 User-Visible Behavior
- When opening Wishlist with existing cached wishes, an empty state / empty list container flashes briefly (~100–300ms) before Room delivers the cached items, matching the operator's description.

### 2.4 Nav-Return Behavior
- `WishlistScreen` instantiates its ViewModel via screen-scoped `viewModel()`. Navigating away destroys the ViewModel; returning recreates it, re-triggering `initialValue = emptyList()` and causing a repeated empty flash.

### 2.5 Fix Shape
- **Smallest Fix**: Seed `WishlistRepository` with an in-memory cached snapshot or use `SharingStarted.Eagerly` with a blocking initial Room query (or pre-fetched initial list) so that `initialValue` contains cached items immediately on subscription.

---

## 3. ChatRoom — Analysis (Section 2)

### 3.1 First Frame
- `HumanChatViewModel` (and `FridayChatViewModel`) defines `uiState` via `stateIn(..., initialValue = ChatRoomUiState.Loading)`.
- **First Frame Behavior**: Renders `ChatRoomUiState.Loading` (spinner or empty message list state) before Room message queries emit.

### 3.2 Data Source Timing
- Messages are read from Room (`repository.getMessages(conversationId)`), while a network catch-up (`fetchMessagesFromServer`) is launched asynchronously in `init`. Even though Room has cached messages, `initialValue = ChatRoomUiState.Loading` forces the loading/empty frame.

### 3.3 Nav-Return
- The ViewModel is screen-scoped (`viewModel(factory = ChatRoomViewModelFactory(...))`). Navigating away and back destroys and recreates the ViewModel, resetting state to `ChatRoomUiState.Loading` and causing re-flicker.

### 3.4 Fix Shape
- **Smallest Fix**: Change `initialValue` of `uiState` in `HumanChatViewModel` to attempt a synchronous read from Room or cache if available, or omit the `Loading` state if cached messages exist.

---

## 4. Dashboard — Confirm the Fix Shape (Section 3)

### 4.1 First Frame
- `_userProfile` starts as `null`, and `_isInitialLoading` starts as `true`. The greeting and welcome card render placeholder text ("Hi User, Welcome back") and a blank avatar slot until `loadInitialData()` finishes reading DataStore.

### 4.2 Reload on Nav Return
- `DashboardScreen.kt` contains:
  ```kotlin
  val currentEntry by navController.currentBackStackEntryAsState()
  LaunchedEffect(currentEntry) {
      if (currentEntry?.destination?.route == "dashboard") {
          viewModel.reloadProfile()
      }
  }
  ```
- This explicitly triggers a profile reload every single time the user navigates back to the dashboard.

### 4.3 Fix Shape
- **(a) Synchronous Initial Seed**: Seed `_userProfile` from `StorageManager` (or cached SharedPreferences mirror) during `DashboardViewModel` instantiation rather than asynchronously in `init`.
- **(b) Gate Nav-Return Reload**: Remove or conditionalize the `LaunchedEffect` reload-on-return so it only reloads when stale or explicitly requested via pull-to-refresh.

---

## 5. ChatList — Analysis (Section 4)

### 5.1 First Frame
- `_isInitialLoading` starts as `true`, and `_conversations` starts as `emptyList()`.
- **First Frame Behavior**: Renders a `CircularProgressIndicator` centered on screen until `loadData()` reads cached profile and sets `_isInitialLoading.value = false`.

### 5.2 Nav-Return
- The ViewModel is screen-scoped (`viewModel()`) and recreated on navigation return, triggering `loadData()` and flashing the loading indicator again.

### 5.3 Fix Shape
- **Smallest Fix**: Pre-seed `otherProfile` and initial conversation state synchronously in the `ChatListViewModel` constructor from storage cache, and initialize `_isInitialLoading` to `false` if cached profile data is present.

---

## 6. Shared Findings (Section 5)

- **`StorageManager` Synchronous Helpers**: `StorageManager` uses Jetpack DataStore (`context.dataStore.data.map { ... }.first()`), which is strictly asynchronous and suspend-based. There is **no non-suspend `getStringSync` helper** in `StorageManager`.
- **ViewModel Initialization Patterns**: ViewModels do not currently standardize a `hasLoaded` flag or activity-scoped caching layer.
- **Nav-Graph Scoping**: Not used across main functional screens; most rely on screen-scoped `viewModel()`.

---

## 7. Cross-Screen Recommendation Table (Section 6)

| Screen | Flicker on first open? | Flicker on nav return? | Root cause | Smallest fix |
| :--- | :--- | :--- | :--- | :--- |
| **Wishlist** | Yes (Empty list flash) | Yes (ViewModel recreated) | `stateIn` initialValue set to `emptyList()` before Room emits | Seed repository/ViewModel with cached initial snapshot |
| **ChatRoom** | Yes (Loading state / empty list) | Yes (ViewModel recreated) | `uiState` initialValue set to `ChatRoomUiState.Loading` | Initialize `uiState` with cached Room messages |
| **Dashboard** | Yes (Null profile / loading) | Yes (Reloads on every backstack entry) | `_userProfile` starts null + `LaunchedEffect` forces reload on navigation return | Synchronously seed profile from cache + remove forced `LaunchedEffect` reload |
| **ChatList** | Yes (Loading spinner) | Yes (ViewModel recreated) | `_isInitialLoading` starts true + screen-scoped ViewModel recreation | Pre-seed cache state in ViewModel constructor and default `isInitialLoading` to false |
