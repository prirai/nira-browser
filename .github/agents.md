# LLM Agent Guide for Nira Browser

This document provides guidance for AI assistants and LLM models working on the Nira Browser codebase.

## Project Overview

**Nira Browser** is a privacy-focused Android browser built on Mozilla's GeckoView engine, featuring Material 3 design, multi-profile support, tab groups, PWA support, and advanced privacy features.

- **License**: Mozilla Public License 2.0 (MPL-2.0)
- **Language**: Kotlin (Android)
- **Minimum SDK**: 27 (Android 8.1)
- **Target SDK**: 37
- **Mozilla Components**: 156.0.1 (`components` version in `gradle/libs.versions.toml`)
- **Architecture**: MVVM with Android Components and Jetpack Compose

## Related Mozilla Projects

When working on this project, you may need to reference the following Mozilla repositories for GeckoView, Android Components, and browser architecture patterns:

### Primary References
- **Mozilla Reference Browser**: https://github.com/mozilla-mobile/reference-browser
  - Example implementation of GeckoView-based browser
  - Best practices for Mozilla Android Components usage
  - Reference for browser UI/UX patterns

- **Firefox Repository**: https://github.com/mozilla-firefox/firefox/
  - Complete Firefox source code including:
    - Android browser implementation (Fenix)
    - GeckoView engine source code
    - Android Components library source
    - Advanced features and integrations
  - **This is the primary reference for all Mozilla Android code**

### Additional Resources
- **GeckoView Documentation**: https://mozilla.github.io/geckoview/
  - Engine API documentation
  - Integration guides

## Project Structure

```
nira-browser/
├── app/src/main/java/com/prirai/android/nira/
│   ├── browser/              # Core browser functionality
│   │   ├── tabs/            # Tab management (list, compose, groups)
│   │   │   ├── compose/     # Modern Compose UI for tabs (PRIMARY)
│   │   │   ├── modern/      # Legacy tab management (DEPRECATED)
│   │   │   └── ...
│   │   ├── tabgroups/       # Tab grouping system
│   │   │   ├── UnifiedTabGroupManager.kt    # Group management
│   │   │   ├── TabGroup.kt                   # Database entities
│   │   │   └── TabGroupDatabase.kt           # Room database
│   │   ├── profile/         # Multi-profile system (ProfileManager, BrowserProfile)
│   │   └── ...
│   ├── search/              # Address-bar search dialog + AwesomeBar
│   ├── components/          # UI components
│   │   ├── toolbar/         # Browser toolbar (modern/classic)
│   │   │   └── modern/      # Modern Compose toolbar (PRIMARY)
│   │   ├── topsites/        # Homepage shortcuts
│   │   └── ...
│   ├── settings/            # Settings screens
│   ├── preferences/         # Preference management
│   ├── webapp/              # PWA support
│   ├── downloads/           # Download manager
│   ├── history/             # Browsing history
│   ├── addons/              # Extension support
│   ├── theme/               # Theming system
│   ├── ui/                  # General UI utilities
│   ├── utils/               # Utility functions
│   └── ext/                 # Kotlin extensions
└── app/src/main/res/        # Android resources
```

## Key Systems and Where to Find Them

### Tab Management
- **Primary Implementation**: `browser/tabs/compose/`
  - `TabViewModel.kt` - Tab state management (MVVM)
  - `TabOrderManager.kt` - Tab ordering and persistence (DataStore)
  - `TabBarCompose.kt` - Horizontal tab bar UI
  - `UnifiedTabOrder.kt` - Order data model
  - `UnifiedDragSystem.kt` - Drag-and-drop system

- **Tab Groups**:
  - `browser/tabgroups/UnifiedTabGroupManager.kt` - Group CRUD operations
  - `browser/tabgroups/TabGroup.kt` - Room database entities
  - `browser/tabgroups/TabGroupDatabase.kt` - Database instance

- **Legacy Code** (avoid modifying unless necessary):
  - `browser/tabs/modern/` - Old tab management system

### Multi-Profile System
- `browser/profile/ProfileManager.kt` - Profile CRUD
- `browser/profile/BrowserProfile.kt` - Profile model (`id = "default"` for the built-in profile)
- `components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt` - Profile UI

### Search (address bar vs unified search)
These are **two different UIs**. Do not merge them unless asked.

| UI | Entry | Role |
|----|--------|------|
| Address-bar search | `search/SearchDialogFragment.kt` | URL / search-engine suggestions while typing in the toolbar |
| Unified search | `browser/tabs/TabSearchFragment.kt` + `TabSearchAdapter.kt` | Grouped cards for tabs / bookmarks / history (profile + date chips) |

Address-bar stack:
- `BrowserActivity.load()` / `openToBrowserAndLoad()` — URL vs search decision
- `search/SearchDialogController.kt` — commit / suggestion tap
- `search/SearchFragmentStore.kt` — selected engine for the dialog
- `search/awesomebar/AwesomeBarView.kt` — provider wiring
- `search/awesomebar/AwesomeBarWrapper.kt` — Compose host; **must invoke** click / remove callbacks
- `search/awesomebar/NiraAwesomeBar.kt` — grouped rounded-card list (title only, no edit arrow); provider queries are **debounced 120 ms** (each round cancels in-flight provider queries and favicon loads — removing the debounce reintroduces list flicker and held-backspace lag)
- `search/awesomebar/NiraHistorySuggestionProvider.kt` — history rows + favicons
- ~~`search/awesomebar/SearchForQueryProvider.kt`~~ — **removed** (it re-rendered a top-priority section on every keystroke, causing typing lag). The typed-query echo is suppressed via `SearchSuggestionProvider(filterExactMatch = true)`; Enter still submits the exact typed text through `BrowserActivity.load()`
- `browser/SearchEngineList.kt` — Nira's default engines and `{searchTerms}` templates

History page (separate from suggestions): `history/HistoryActivity.kt` + `HistoryItemRecyclerViewAdapter.kt`. Use `FaviconLoader.loadFavicon()`, not cache-only.

### Tab History Chart (long-press back/forward) & Back Navigation
- `browser/history/TabHistoryBottomSheet.kt` + `TabHistoryAdapter.kt`: Firefox-style **full chart** (sites before + current page + sites forward), rendered with `reverseLayout` (oldest at the bottom, current page centered with a filled dot). Corners/dividers must follow the reversed rendering (`isFirst` = visually bottom).
- **`about:homepage` cannot be an engine history entry** — GeckoView cannot load it; the homepage is the Compose overlay over a blank engine page. The new tab page is a *state position*, not a navigation: `browser/tabs/NewTabCreator.setTabToNewTabPage()` dispatches `ContentAction.UpdateHistoryStateAction(currentIndex = NEW_TAB_INDEX = -1)` + url/title + navigation flags (`canGoBack=false`, `canGoForward=true`), so the whole session history becomes forward of it.
- `tab.content.history` (`HistoryState`) can be **stale** — dispatch `EngineAction.FlushEngineSessionStateAction(tabId)` first and collect the state reactively (port of upstream Fenix Bug 1970055). Skip the flush while on the synthetic new tab page (`currentIndex == -1`).
- Back walking past the oldest entry lands the tab on its new tab page via that state transition (`BrowserFragment.onBackPressed`) — do **not** nav to `homeFragment` for normal content tabs (the fragment swap desynced the tab pill). Back on the new tab page **closes the tab** and selects the LRU last-used tab (AC `lastAccess`, filtered by privacy + profile partition).
- New-tab semantics: home search over a content tab starts a **fresh tab** (`NewTabCreator.createHomeTabAndLoad` — Chrome NTP parity); address-bar search on a content page reuses the tab (omnibox behaviour). Decision: `SearchDialogController.shouldCreateNewTabForSearch` + `openedFromHome` (previous nav destination == `homeFragment`).

### Toolbar Layout (since v0.5.16)
- Contextual toolbar (`toolbar/ContextualBottomToolbar.kt` + `contextual_bottom_toolbar.xml`): back-slot (bookmark glyph on homepage) / forward / share / search / new tab / **extensions** / menu. The extensions button opens `ExtensionsBottomSheetFragment`. There is **no tab count** on this toolbar anymore.
- The tab count is a badge on the **address bar**, inserted at index 0 of the browser-actions container (left of the chevron), built by `UnifiedToolbar.addTabCountBadgeToAddressBar()` — always added, while the menu button is contextual-toolbar-disabled-only. The badge counts guest/default-partition tabs (`contextId == null`) as well.
- `updateContextualToolbar(tab, canGoBack, canGoForward, isHomepage)` — no tabCount parameter. Long-press back/forward on the contextual toolbar and the overflow-menu toolbar row open the tab-history sheet.

### Progressive Web Apps (PWAs)
- `webapp/WebAppManager.kt` - PWA management
- `webapp/InstalledWebApp.kt` - Database entities
- Settings integration in `settings/webapps/`

### UI Components
- **Toolbar**: `components/toolbar/modern/` (Compose-based)
- **Topsites**: `components/topsites/` (Homepage shortcuts)
- **Theme**: `theme/ColorConstants.kt`, `theme/Theme.kt`

### Settings
- `settings/` - All settings screens
- `preferences/` - SharedPreferences management
- Uses Jetpack Compose for modern UI

### Data Persistence
- **Room Database**: Tab groups, profiles, PWAs, history
- **DataStore**: Tab order, user preferences
- **SharedPreferences**: Legacy settings (migrating to DataStore)

## Common Tasks and Where to Look

### Adding a New Feature
1. **UI Changes**: Start in `components/` or `browser/tabs/compose/`
2. **State Management**: Add ViewModels in relevant packages
3. **Persistence**: Extend Room entities or DataStore models
4. **Settings**: Add preferences in `settings/` and `preferences/`

### Fixing Tab-Related Issues
- Check `browser/tabs/compose/TabViewModel.kt` for state issues
- Check `browser/tabs/compose/TabOrderManager.kt` for persistence
- Check `browser/tabgroups/UnifiedTabGroupManager.kt` for groups
- Look for color/theme issues in `theme/ColorConstants.kt`

### Profile-Related Issues
- `browser/profile/ProfileManager.kt` - Profile management
- `components/toolbar/modern/` - Profile switching UI
- Check for contextId filtering in tab/group queries

### Search / "everything became a URL"
1. `BrowserActivity.load()`: if `engine == null`, input is treated as a URL (`toNormalizedUrl()`). Always resolve an engine first (`store.selectedOrDefaultSearchEngine` or `SearchEngineList.getSelectedEngine()`).
2. `String.isUrl()` is Mozilla's **lenient** `URLStringUtils.isURLLike` (no spaces + `.` / `:` / `://` counts as a URL). Do not replace it unless asked.
3. `SearchMiddleware` + `RegionMiddleware` load bundled engines **asynchronously**. `selectedOrDefaultSearchEngine` is often null on first search. Seed from `UserPreferences` / `SearchEngineList`.
4. `setupSearchEngines()` is deferred with `view.post` — do not assume engines are selected at Activity `onCreate`.
5. Engine `suggestUrl` must be a real OpenSearch template containing `{searchTerms}` (e.g. Google `complete/search?client=firefox&q={searchTerms}`). Homepage URLs produce zero suggestions.
6. `SearchSuggestionProvider` with `filterExactMatch = true` excludes the typed query from the engine suggestions. The standalone `Search for "query"` row (`SearchForQueryProvider`) was **removed** for typing perf — do not reintroduce it without debouncing.
7. Address-bar history/tab grouping by profile or date is **slow** (`getDetailedVisits`). Keep that only in unified search (`TabSearchFragment`).
8. History X-delete: `historyStorage.deleteVisitsFor(url)` using `suggestion.description` (URL). Hide the row via `hiddenSuggestions`.

### PWA Issues
- `webapp/WebAppManager.kt` - Installation, uninstallation
- Database schema in `webapp/InstalledWebApp.kt`
- Settings UI in `settings/webapps/`

### UI/Theme Issues
- `theme/` - Color constants, Material 3 theming
- `components/toolbar/` - Toolbar customization
- Check for Compose vs View-based UI conflicts

## Code Patterns and Best Practices

### Architecture
- **MVVM Pattern**: Use ViewModels for state management
- **Repository Pattern**: Managers (e.g., ProfileManager, WebAppManager)
- **Single Source of Truth**: Managers maintain in-memory cache synced with database
- **Reactive UI**: StateFlow/Flow for observing data changes

### State Management
```kotlin
// ViewModel pattern
private val _state = MutableStateFlow<State>(initialState)
val state: StateFlow<State> = _state.asStateFlow()

// Compose collection
val state by viewModel.state.collectAsState()
```

### Database Access
```kotlin
// Always use withContext(Dispatchers.IO) for database operations
suspend fun getData() = withContext(Dispatchers.IO) {
    dao.query()
}
```

### Color Handling
- Colors stored as strings in database (e.g., "blue", "light_red")
- Parsed to Int at runtime via `ColorConstants.TabGroups.parseColor()`
- Two palettes exist: standard colors and lighter variants
- Always use conversion functions in `theme/ColorConstants.kt`

### Tab Groups
- Groups stored in Room database with contextId for profile isolation
- Order and expand/collapse state stored separately in DataStore
- Use `UnifiedTabGroupManager` for all group operations
- Events emitted via SharedFlow for UI updates

### Profiles
- Each profile has unique contextId (e.g., "profile_default", "private", "profile_{id}")
- Tabs, groups, and PWAs filtered by contextId
- Cookie jars isolated per profile
- Default profile handles null contextId for backward compatibility

## Known Issues and Gotchas

### Tab Groups
- ⚠️ Two different color palettes exist (`AVAILABLE_COLORS` vs `ColorConstants`)
- ✅ Color conversion functions updated to handle both palettes
- Groups must be filtered by contextId when querying
- Collapse/expand state separate from group entity

### Profiles
- Default profile accepts both `null` and `"profile_default"` contextId
- Profile deletion must clean up associated tabs, groups, PWAs
- Cookie jar switching requires browser restart in some cases

### PWAs
- Installation checks for existing PWAs with same URL
- Profile binding affects which tabs can see the PWA
- Manifest parsing can fail for malformed JSON

### UI/Compose
- Mix of Compose and View-based UI exists
- Prefer Compose for new features
- `modern/` packages indicate Compose implementations
- Legacy code often in root package or `modern/` may indicate old implementation
- **Never toggle `Modifier.animateItem()` after first composition** (e.g. after a delay). That crashes with `ArrayIndexOutOfBoundsException` in `LazyLayoutItemAnimator`. Keep the modifier stable or omit it.
- Lazy list keys must be unique across types: prefix `"tab-"` / `"group-"` / section id. Duplicate `item.id` values crash the same animator.
- `NiraTheme` must **not** cast `view.context as Activity`. Search dialogs wrap context in `ContextThemeWrapper`. Unwrap with a `ContextWrapper` loop (`findActivity()`).
- `AwesomeBarWrapper` used to pass empty `{ }` click lambdas — Compose AwesomeBar does not call `Suggestion.onSuggestionClicked` unless the wrapper does.

### GeckoView / Engine Gotchas
- ⚠️ `privacy.resistFingerprinting` (a hardening default in `components/UserJsPreferences.kt`) **forces content `prefers-color-scheme: light`** by design (bug 1535189). The "Web theme" setting drives the engine's `preferredColorScheme` correctly, but pages cannot react while RFP is enabled — an accepted trade-off, documented inline in `UserJsPreferences.privacyPrefs`. Investigated end-to-end in v0.5.16; do not re-investigate without changing the RFP default.
- AC `BrowserStore.dispatch` reduces state **synchronously** on the calling thread — reading `store.state` right after dispatch is safe (e.g. selectedTabId after `addTab(selectTab = true)`); engine side-effects are serialized in dispatch order.
- The engine reports `canGoBack`/`canGoForward` reliably on every navigation; `HistoryState` indexes do **not** stay as current (see the flush note under Tab History). Use flags as truthiness signals, not history indexes.
- `GeckoRuntimeSettings.writeToParcel` does **not** serialize `mPreferredColorScheme` to child processes — live `setPreferredColorScheme` changes reach content only via the LookAndFeel refresh push (`onDeviceChanged` → `NotifyChangedAllWindows`), not via the settings parcel.
- Long-press on the Android **back gesture is impossible** (system-owned; no duration is exposed to apps). The tab-history sheet is reachable only from visible buttons (contextual toolbar, overflow menu) — do not attempt to wire gesture holds.

### Favicons
- Suggestions / history page: `FaviconLoader.loadFavicon(context, url)` (memory → disk → `BrowserIcons`).
- `FaviconCache.loadFavicon()` is cache-only and will miss most history icons.

## Testing

### Manual Testing Checklist
- Test across multiple profiles (default, private, custom)
- Verify data isolation between profiles
- Test tab grouping: create, rename, recolor, collapse, delete
- Test app restart persistence for colors and collapse state
- Test PWA installation and profile binding
- Test theme switching (light, dark, AMOLED, dynamic color)

### Areas Requiring Extra Testing
- Tab group color persistence
- Profile switching with active groups
- PWA profile binding
- Cookie isolation between profiles
- Theme changes with active tabs
- Tab-history chart: contents from both entry points (contextual toolbar + overflow menu), corner/divider orientation in the reversed layout, New Tab row → hold forward flow, back-gesture → new tab page → pill sync → close + LRU switch

### On-Device Debugging Techniques (adb)
- **uiautomator cannot read GeckoView web content** — DOM text/fields inside pages are invisible to `uiautomator dump`. To probe page-visible state (e.g. `prefers-color-scheme`), load a data URL whose JS sets `document.title` from the value you need and read the title from the native tab pill.
- Debug builds use the `.debug` application-id suffix and are `run-as`-able:
  - `adb shell run-as com.prirai.android.nira.debug cat shared_prefs/scw_preferences.xml` — app prefs (`web_theme_choice`, etc.)
  - Gecko profile prefs live under `files/mozilla/<profile>/prefs.js`
- The contextual toolbar buttons: disabled buttons never receive long-press; the homepage back slot shows the bookmark glyph and its long-press is swallowed by design.
- `adb shell input swipe X Y X Y <ms>` synthesizes a long-press for visible buttons. The Android back **gesture** cannot be held — only triggered (`input swipe` from the screen edge).

## Development Workflow

### Building
```bash
# Debug build
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

### Common Commands
```bash
# Clean build
./gradlew clean assembleDebug

# Install to device
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# View logs
adb logcat | grep "Nira\|TabOrder\|TabGroup\|Profile"
```

### Important Log Tags
- `UnifiedTabGroupManager` - Group operations
- `TabOrderManager` - Order persistence
- `TabViewModel` - Tab state changes
- `ProfileManager` - Profile operations
- `WebAppManager` - PWA operations

### Releases (automated)
Releases are **tag-driven and fully automated** by `.github/workflows/apk-release.yml`. Do not build/upload release APKs manually.

To cut a release (see `v0.5.15`/`v0.5.16` for the exact shape):
1. Bump `versionCode` and `versionName` in `app/build.gradle` (base bump by 1; ABI APKs get `base*1000 + versionCode` — arm64-v8a=1, armeabi-v7a=2, x86_64=0).
2. Commit: subject `Release vX.Y.Z`, body `versionCode A -> B, versionName x -> y.`.
3. Create a **lightweight** tag on that commit: `git tag vX.Y.Z`.
4. `git push origin main vX.Y.Z` — the workflow's `release` job checks out the **tag**, builds signed APKs (keystore from repo secrets) and publishes the GitHub release ("Release {tag}", "Automated release build") with the three ABI APK assets.

The in-app updater (`update/UpdateChecker.kt`) reads GitHub releases and matches ABI + versionCode — a release is only discoverable once CI has attached the APKs. Dependent PRs must target their base branch (e.g. PR #121 targeted `feat/auto-update`) and be retargeted to `main` after the base merges.

## When to Ask for Clarification

Ask the user for guidance when:
1. Changing database schema (requires migration)
2. Modifying core architecture (Managers, ViewModels)
3. Deprecating or removing features
4. Adding new dependencies
5. Changing color palette or theming system
6. Modifying GeckoView integration
7. Unsure about profile/contextId handling
8. Need to understand business logic for a feature

## Quick Reference: File Purposes

### Critical Files (Understand Before Modifying)
- `UnifiedTabGroupManager.kt` - Single source of truth for groups
- `TabViewModel.kt` - Tab UI state management
- `TabOrderManager.kt` - Tab/group ordering persistence
- `ProfileManager.kt` - Profile lifecycle management (`browser/profile/`)
- `WebAppManager.kt` - PWA lifecycle management
- `ColorConstants.kt` - Color definitions and conversions
- `BrowserActivity.load()` - URL vs search; never treat missing engine as URL
- `SearchEngineList.kt` - Default engines + suggest URL templates
- `AwesomeBarWrapper.kt` / `NiraAwesomeBar.kt` - Address-bar suggestion UI

### Data Models
- `TabGroup.kt` - Room entity for groups
- `BrowserProfile.kt` - Profile model (`browser/profile/`)
- `InstalledWebApp.kt` - Room entity for PWAs
- `UnifiedTabOrder.kt` - DataStore model for tab order

### UI Entry Points
- `BrowserFragment.kt` - Main browser screen
- `BrowserActivity.kt` - Activity host
- `TabBarCompose.kt` - Horizontal tab bar
- `ComposeTabBarWithProfileSwitcher.kt` - Profile switcher

## Mozilla Components Integration

When working with Mozilla Components, consult:
- **Firefox Repository** (https://github.com/mozilla-firefox/firefox/) for:
  - GeckoView engine source code
  - Android Components implementation
  - Fenix (Firefox for Android) source code
  - API reference and examples
- **Reference Browser** (https://github.com/mozilla-mobile/reference-browser) for simple implementation examples
- **GeckoView Documentation** (https://mozilla.github.io/geckoview/) for engine integration

Common Mozilla components used:
- `browser-engine-gecko` - GeckoView integration
- `browser-state` - Browser state management
- `browser-toolbar` - Toolbar components
- `concept-engine` - Engine abstractions
- `feature-tabs` - Tab management
- `feature-downloads` - Download handling
- `feature-addons` - Extension support

---

**Last Updated**: October 2026 (v0.5.16: tab history chart, back-UX rework, toolbar rework, address-bar perf, automated release process)

For questions or updates to this guide, please open an issue or discussion on GitHub.
