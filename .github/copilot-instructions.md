# Copilot Instructions for Nira Browser

Nira Browser is a privacy-focused Android browser built on Mozilla's GeckoView engine, using Jetpack Compose, Material 3, MVVM architecture, and Kotlin Coroutines.

## Build Commands

```bash
# Debug build (Windows)
gradlew.bat assembleDebug

# Clean build
gradlew.bat clean assembleDebug

# Install to connected device (ARM64)
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Other APK variants (architecture-split builds)
# app-armeabi-v7a-debug.apk  (ARMv7)
# app-x86_64-debug.apk       (emulator)
```

No automated tests exist yet. Manual testing covers tabs, profiles, groups, and themes.

## Architecture

**Stack**: MVVM · Jetpack Compose · GeckoView · Room + DataStore · Kotlin Coroutines/Flow · Manual DI

```
View (Composables)
  └─ observes StateFlow / calls functions
ViewModel (TabViewModel, etc.)
  └─ calls suspend functions
Manager (UnifiedTabGroupManager, ProfileManager, etc.)  ← acts as Repository
  └─ reads/writes
Database (Room) + DataStore
```

### Critical files to understand before modifying

| File | Role |
|------|------|
| `UnifiedTabGroupManager.kt` | Single source of truth for all tab group operations |
| `TabViewModel.kt` | Tab UI state — observes groups and tab lists |
| `TabOrderManager.kt` | Persists tab/group order via DataStore (JSON) |
| `ProfileManager.kt` | Profile lifecycle — create, delete, switch |
| `WebAppManager.kt` | PWA lifecycle — install, uninstall, profile binding |
| `Components.kt` | Manual DI container — initializes all managers |
| `ColorConstants.kt` | Color palette definitions and string→Int conversion |
| `BrowserFragment.kt` | Main browser screen entry point |
| `BrowserActivity.load()` | URL vs search. `engine == null` must **not** fall through to `toNormalizedUrl()` |
| `SearchEngineList.kt` | Nira default engines; `suggestUrl` must include `{searchTerms}` |
| `AwesomeBarWrapper.kt` / `NiraAwesomeBar.kt` | Address-bar suggestion UI; wrapper must invoke click/remove callbacks |

### Package layout

```
app/src/main/java/com/prirai/android/nira/
├── browser/
│   ├── tabs/compose/      ← PRIMARY tab UI (Compose)
│   ├── tabs/modern/       ← LEGACY tab system (avoid modifying)
│   ├── tabgroups/         ← Group CRUD + Room DB
│   └── profile/           ← Multi-profile (ProfileManager, BrowserProfile)
├── search/                ← Address-bar search dialog + AwesomeBar
├── components/
│   └── toolbar/modern/    ← PRIMARY toolbar (Compose)
├── settings/              ← Settings screens
├── webapp/                ← PWA support
├── history/               ← Full history page (not the AwesomeBar)
├── theme/                 ← Material 3 + color constants (`ui/theme/Theme.kt` = NiraTheme)
├── addons/                ← WebExtension support
└── ext/                   ← Kotlin extension functions
```

## Key Conventions

### Dependency Injection
No DI framework. Use double-checked locking singleton pattern:
```kotlin
companion object {
    @Volatile private var instance: MyManager? = null

    fun getInstance(context: Context): MyManager =
        instance ?: synchronized(this) {
            instance ?: MyManager(context.applicationContext).also { instance = it }
        }
}
```

### State Management
```kotlin
// ViewModels expose immutable StateFlow
private val _state = MutableStateFlow<State>(Initial)
val state: StateFlow<State> = _state.asStateFlow()

// Managers emit events via SharedFlow
private val _events = MutableSharedFlow<Event>()
val events: SharedFlow<Event> = _events.asSharedFlow()

// Composables collect state
val state by viewModel.state.collectAsState()
```

### Database / IO operations
Always wrap Room and DataStore calls in `withContext(Dispatchers.IO)`:
```kotlin
suspend fun getData() = withContext(Dispatchers.IO) { dao.query() }
```

### Color handling
Tab group colors are stored as strings in the database (`"blue"`, `"light_red"`, etc.) and converted to `Int` at runtime. Never hardcode color ints — always use:
```kotlin
ColorConstants.TabGroups.parseColor(colorString)
```
Two palettes exist: standard and lighter variants. Both are handled by the conversion functions.

### Profile / contextId system
Every data entity (tab, group, PWA) is scoped to a `contextId`:
- `null` or `"profile_default"` → default profile
- `"private"` → private browsing
- `"profile_{uuid}"` → user-created profiles

Always filter queries by `contextId`. The default profile must accept both `null` and `"profile_default"` for backward compatibility.

### Search vs URL
- Address-bar search (`SearchDialogFragment`) ≠ unified search (`TabSearchFragment`). Different UIs.
- `String.isUrl()` is Mozilla's **lenient** `isURLLike` (spaces absent + `.` / `:` / `://` ⇒ URL).
- `SearchMiddleware` loads engines asynchronously. `selectedOrDefaultSearchEngine` is often null at first type. Fall back to `SearchEngineList.getSelectedEngine(UserPreferences)`.
- `setupSearchEngines()` is deferred (`view.post`). Do not assume a selected engine in `onCreate`.
- `SearchForQueryProvider` was **removed** for typing perf — do not reintroduce without debouncing. `SearchSuggestionProvider(filterExactMatch = true)` suppresses the typed-query echo; Enter submits the exact text via `BrowserActivity.load()`.
- `NiraAwesomeBar` debounces provider queries by **120 ms** — every input round cancels in-flight provider queries and favicon loads; removing it reintroduces list flicker + held-backspace lag.
- Do **not** group address-bar history/tabs by profile or date — `getDetailedVisits` is too slow. That grouping lives only in unified search.
- History delete from suggestions: `historyStorage.deleteVisitsFor(url)` using `suggestion.description`.

### Tab history & back navigation (v0.5.16)
- Long-press back/forward (contextual toolbar + overflow-menu toolbar row) opens the **full session chart** (`browser/history/TabHistoryBottomSheet.kt`) — reverseLayout, current page centered, dot row disabled.
- `about:homepage` **cannot be an engine history entry** (GeckoView can't load it — homepage is a Compose overlay). The new tab page is a *state position*: `NewTabCreator.setTabToNewTabPage()` dispatches `HistoryState(currentIndex = NEW_TAB_INDEX = -1)` + url/title/navigation flags. Forward works from it; back on it closes the tab + LRU-switches (`BrowserFragment.onBackPressed`).
- `tab.content.history` can be stale — dispatch `FlushEngineSessionStateAction` first, then collect reactively (Fenix Bug 1970055).
- Home search over a content tab starts a fresh tab (`NewTabCreator`); omnibox on a content page reuses it (`SearchDialogController.shouldCreateNewTabForSearch` + `openedFromHome`).

### UI: Compose vs Views
- `modern/` subdirectories → Compose implementations (prefer these)
- Legacy XML Views exist in some places — migrate to Compose for new work
- `TabSheetStateManager` is a singleton `object` that signals tab sheet dismissal events to the tab bar via a `StateFlow<Long>` timestamp; call `notifyTabSheetDismissed()` after closing the sheet
- Never toggle `Modifier.animateItem()` after first composition. Causes `ArrayIndexOutOfBoundsException` in `LazyLayoutItemAnimator`.
- Lazy keys must be unique across item types (`"tab-$id"` vs `"group-$id"`).
- `NiraTheme` must unwrap `ContextThemeWrapper` to find the Activity. `view.context as Activity` crashes in the search dialog.
- `AwesomeBarWrapper` must call `suggestion.onSuggestionClicked` / remove listeners itself.

### Favicons
- Use `FaviconLoader.loadFavicon(context, url)` (cache + BrowserIcons). `FaviconCache.loadFavicon()` is cache-only and misses most history icons.

### Toolbar layout (since v0.5.16)
- Contextual toolbar has **no tab count** — the slot is an **extensions button** (opens `ExtensionsBottomSheetFragment`).
- The tab count is a badge on the address bar's browser-actions (index 0, left of the chevron), built by `UnifiedToolbar.addTabCountBadgeToAddressBar()`; its filter must include `contextId == null` (default profile) tabs.

### GeckoView / engine caveats
- `privacy.resistFingerprinting` (hardening default in `components/UserJsPreferences.kt`) **forces content `prefers-color-scheme: light`** — the "Web theme" setting cannot reach pages while it is on. Accepted trade-off; documented inline. Do not re-investigate.
- AC `BrowserStore.dispatch` reduces state **synchronously** — reading `store.state` right after dispatch is safe.
- Trust `canGoBack`/`canGoForward` (engine-maintained) over `HistoryState` indexes (stale until flushed).
- Long-press on the Android back **gesture** is impossible (system-owned) — the tab-history sheet is only reachable from visible buttons.

### Releases (automated by `.github/workflows/apk-release.yml`)
- Bump `versionCode`/`versionName` in `app/build.gradle` → commit `Release vX.Y.Z` → **lightweight** tag → `git push origin main vX.Y.Z`. CI builds signed APKs from the tag and publishes the GitHub release with ABI assets (arm64=1xxx, armeabi-v7a=2xxx, x86_64=base). The in-app updater reads GitHub releases — no manual APK uploads.

### Database schema changes
Room migrations are required for any schema change. Ask before modifying entity classes.

### Managers maintain an in-memory cache
`UnifiedTabGroupManager`, `ProfileManager`, etc. keep an in-memory cache synced with the database. Emit `SharedFlow` events so the UI can react without re-querying.

## Log Tags (for `adb logcat`)

```
UnifiedTabGroupManager   TabOrderManager   TabViewModel
ProfileManager           WebAppManager
```

**On-device debugging**: uiautomator cannot read GeckoView web content — probe page-visible state via a data URL whose JS sets `document.title`, read from the native tab pill. Debug builds are `run-as`-able (`com.prirai.android.nira.debug`): app prefs in `shared_prefs/scw_preferences.xml`, Gecko prefs in `files/mozilla/<profile>/prefs.js`.

## Mozilla Components Reference

For GeckoView and Mozilla Android Components questions, refer to:
- [Firefox Repository](https://github.com/mozilla-firefox/firefox/) — Fenix, GeckoView source, Android Components
- [Reference Browser](https://github.com/mozilla-mobile/reference-browser) — simpler implementation examples
- [GeckoView Docs](https://mozilla.github.io/geckoview/)

Current Mozilla Components version: **156.0.1** (`components` version in `gradle/libs.versions.toml`). After AC bumps, re-check `SearchEngine` constructors, `SearchSuggestionProvider` headers, and `compose-awesomebar` click APIs — they change between major AC versions.
