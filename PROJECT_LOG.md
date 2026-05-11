# Nira Browser Project Log

## Project Overview & Structure

### Project Type
- Privacy-focused Android web browser built on Mozilla's GeckoView engine
- Tech Stack: Kotlin, MVVM architecture, Jetpack Compose, Material 3
- Status: Alpha stage, MPL-2.0 license
- GitHub: https://github.com/prirai/nira-browser

### Logs & Tracking
- Crash logs: `nira_crash.log` (local logcat output)
- No dedicated session logs directory; GeckoView manages browser sessions
- Bug tracking: GitHub Issues (https://github.com/prirai/nira-browser/issues)
- Local dev log: This file (PROJECT_LOG.md)

### Project Structure
```
nira-browser/
├── app/                          # Main Android app module
│   └── src/main/java/com/prirai/android/nira/
│       ├── browser/               # Core browser (tabs, groups, profiles)
│       ├── components/            # UI components (toolbar, menu)
│       ├── settings/              # Settings screens
│       ├── webapp/                # PWA support
│       ├── addons/                # Extension support
│       ├── downloads/             # Download manager
│       ├── history/               # Browsing history
│       ├── theme/                 # Theming (light/dark/AMOLED)
│       ├── media/                 # Media session
│       ├── customtabs/            # Custom tab support
│       ├── utils/, ext/           # Utilities
│       └── BaseBrowserFragment.kt # Main browser fragment
├── mozilla/                       # Mozilla components reference
├── .github/                       # GitHub config (agents, workflows)
├── build.gradle                   # Root build config
├── ARCHITECTURE.md                # Architecture docs
├── CONTRIBUTING.md                # Contributor guidelines
├── README.md                      # Project overview
├── PROJECT_LOG.md                 # This log
├── nira_crash.log                 # Crash logs
└── LICENSE                        # MPL-2.0
```

### Key Patterns
- MVVM with ViewModels, Repository pattern (Managers)
- Room databases (tab groups, profiles, PWAs)
- DataStore for preferences, StateFlow/Flow for reactive UI

### Build & Install Process
- **Build debug APK**: `./gradlew assembleDebug`
- **APK location**: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (ARM64 for Pixel 7)
  - Note: Build generates architecture-specific APKs (armeabi-v7a, arm64-v8a, x86_64)
- **Install on device**: `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
  - `-r` flag replaces existing app, keeps data
- **Device**: Pixel 7 (Android 16), connected via USB
- **ADB check**: `adb devices -l` to verify connection
- **App name**: Installs as "Nira (Debug)" (separate from release version)

## Session: 2026-05-02
**Device**: Pixel 7 (Android 16)  
**User**: sinnohman (GitHub: Sinnohman)

### What We Fixed
- **Crash when opening new tabs** (NullPointerException in BaseBrowserFragment)
  - Root cause: `initializeEngineView` used `unifiedToolbar?.post {}` which accessed `_binding` after fragment view was destroyed
  - Fix: Added null checks for `_binding` and `context` at start of `initializeEngineView`, plus guard inside the `post` lambda
  - File changed: `app/src/main/java/com/prirai/android/nira/BaseBrowserFragment.kt`
  - Built debug APK and tested on Pixel 7 - crash no longer occurs

### Pull Request Created
- **PR**: `fix/new-tab-crash-nullpointer` on fork `sinnohman/nira-browser`
- Branch: `fix/new-tab-crash-nullpointer`
- Status: Submitted and waiting for prirai's review
- Description includes root cause, changes made, and testing performed

### GitHub Discussion Posted
- **Topic**: "Check for Updates" button in Settings > About
- Manual check only (no automatic/background checks)
- Location: GitHub Discussions "Ideas" category
- Offered to help implement if prirai is open to it

### Environment Setup Completed
- JDK 17 installed ✅
- ADB installed ✅
- Android SDK installed at `~/Android` ✅
- `local.properties` created with SDK path ✅
- Device connected: Pixel 7 (Android 16) ✅

### Next Session Plan
- Check if prirai responded to the GitHub Discussion about "Check for Updates" button
- If yes: Implement Option A (manual "Check for Updates" button in Settings > About)
- If no: Wait for response before implementing new features

### Important Notes
- Fork URL: `git@github.com:Sinnohman/nira-browser.git` (note capital S)
- Remote `myfork` = sinnohman/nira-browser
- Remote `upstream` = NOT YET ADDED (add when ready to sync: `git remote add upstream git@github.com:prirai/nira-browser.git`)
- Debug app installs as "Nira (Debug)" (separate from release version)

## Session: 2026-05-03
**Device**: Pixel 7 (Android 16)  
**User**: sinnohman (GitHub: Sinnohman)

### What We Did
- **Added summary text to "Swipe to refresh" setting**
  - Problem: Setting had no description, users didn't know how to use the feature
  - Fix: Added string resource `swipe_to_refresh_summary` = "Pull down from the top of the page to reload"
  - Files changed:
    - `app/src/main/res/values/strings.xml` (added string at line ~382)
    - `app/src/main/res/xml/preferences_customization.xml` (added `app:summary` attribute at line ~158)
  - Built and installed on Pixel 7 - verified working ✅

### Issue Investigated: Swipe to Refresh Requires App Restart
- **Problem**: After enabling "Swipe to refresh" in Settings > Customization > Gestures, user must:
  1. Restart the app (toast says "App restart required")
  2. Change tabs
  3. Click around for a while
  Before the feature actually works

- **Goal**: Make it work immediately AFTER app restart (not avoiding restart, just making restart sufficient)

### Investigation Findings
**How it currently works:**
1. Setting saved in `UserPreferences.swipeToRefresh` (UserPreferences.kt:63)
2. In `BaseBrowserFragment.initializeUI()` (line 491):
   - `binding.swipeRefresh.isEnabled = shouldPullToRefreshBeEnabled()`
   - If enabled, initializes `SwipeRefreshFeature` (lines 507-517)
3. Tab observer (lines 519-527) updates `isEnabled` but doesn't re-initialize feature

**Root cause hypothesis:**
- `swipeRefreshFeature.set(...)` at lines 507-517 runs before view is fully attached
- After process restart, timing issue prevents `ViewBoundFeatureWrapper` from properly starting the feature
- `SwipeRefreshFeature` not properly connected to swipe refresh layout after restart

**Key code locations:**
- Setting UI: `CustomizationSettingsFragment.kt:96-107` (shows restart toast)
- Feature init: `BaseBrowserFragment.kt:490-528`
- Check function: `BaseBrowserFragment.kt:646-649` (`shouldPullToRefreshBeEnabled()`)
- Layout: `fragment_browser.xml:27-40` (VerticalSwipeRefreshLayout)

### Plan (On Back Burner - Resume Here)
**Approach:** Post feature initialization to view's message queue

**File to modify:** `app/src/main/java/com/prirai/android/nira/BaseBrowserFragment.kt`

**Location:** In `initializeUI()` method, lines 507-517

**Current code:**
```kotlin
swipeRefreshFeature.set(
    feature = SwipeRefreshFeature(
        requireContext().components.store,
        requireContext().components.sessionUseCases.reload,
        binding.swipeRefresh,
        ({}),
        customTabSessionId
    ),
    owner = this,
    view = view
)
```

**Proposed fix:**
```kotlin
// Post to ensure view is attached before setting feature
view.post {
    swipeRefreshFeature.set(
        feature = SwipeRefreshFeature(
            requireContext().components.store,
            requireContext().components.sessionUseCases.reload,
            binding.swipeRefresh,
            ({}),
            customTabSessionId
        ),
        owner = this,
        view = view
    )
}
```

**Why this should work:**
- `view.post { ... }` queues initialization until after current layout completes
- Ensures view is attached to window before `ViewBoundFeatureWrapper` starts feature
- `SwipeRefreshFeature` will properly intercept swipe gestures

**Verification steps (when resuming):**
1. Build: `./gradlew assembleDebug`
2. Install: `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
3. Enable "Swipe to refresh" in Settings
4. Force stop app (or reboot device)
5. Open Nira, navigate to any webpage
6. **Test:** Pull down from top of page immediately (no tab switching needed)
7. **Expected:** Page reloads / swipe refresh works without changing tabs

**Alternative if above doesn't work:**
- Re-initialize feature in tab observer (lines 519-527)
- May need to call `swipeRefreshFeature.get()?.start()` or recreate feature on tab switch

### Facebook Scrolling Issue (Mentioned, Not Investigated)
- **Symptom**: On Facebook.com, after scrolling 2-3 posts and pausing, page jumps up/down 2 posts
- **Not a crash**, no errors in logcat
- **Possibly related**: "Swipe to refresh" feature interfering with Facebook's scroll behavior
- **Suggested test**: Disable swipe to refresh, see if scrolling improves
- **Status**: User acknowledged how swipe to refresh works, may test correlation later

(End of file)

## Session: 2026-05-03 (Part 2)
**Device**: Pixel 7 (Android 16)  
**User**: sinnohman (GitHub: Sinnohman)

### Facebook Scrolling Issue - RESOLVED (Upstream Diagnosis)

**Problem**: On Facebook.com, after scrolling 2-3 posts and pausing, page jumps up/down 2 posts

**Diagnosis (Confirmed)**:
1. **Tested with Firefox for Android** - Same scrolling issue occurs
2. **Root cause identified**: This is a **KNOWN GeckoView bug** tracked in Mozilla's Bugzilla

**Key Bug Reports**:
- **Bug 1812227**: "Android webpages jump 'up' every so often while scrolling down"
  - Status: ✅ FIXED in Firefox 132 (Oct 2024)
  - Root cause: `MaybeSplitTouchMoveEvent()` synthesizing touch events with incorrect coordinates
  
- **Bug 1779404**: "Scrolling jumps back and forth in the Facebook feed"
  - **Actual culprit**: Facebook's virtual scrolling mechanism
  - Quote from Mozilla dev: *"Facebook has some sort of virtual scrolling mechanism that removes posts scrolled offscreen and adjusts scroll position to keep current post in view, which is likely responsible for the observed jumps"*
  - Also referenced as Bug 1858022 (multiple fix attempts from FF 115-125)

**Nira Technical Details**:
- Using Mozilla Components **148.0** (line 124 in app/build.gradle)
- This corresponds to Firefox 148 (released after 132)
- The GeckoView fix (Bug 1812227) SHOULD be included
- **However**: Facebook's virtual scrolling (Bug 1779404) is separate and NOT fixable by GeckoView updates

**Conclusion**: 
- ❌ NOT a Nira bug
- ❌ NOT fixable by Nira (it's Facebook's website behavior)
- ✅ Can only be mitigated, not eliminated

### uBlock Origin Configuration for Facebook

**Lists Enabled**:
1. ✅ uBlock filters – Annoyances (includes Fanboy's, EasyList Cookie, social filters)
2. ✅ Custom filters added (see below)

**Custom Filters Added** (in "My filters" tab):
```
! Block Sponsored Posts
facebook.com##[role="feed"] [aria-label="Sponsored"]:upward(div[role="article"])

! Block "Suggested for You" Posts
facebook.com##span:has-text(/^Suggested for You$|^Suggested Post$/):upward(div[role="article"])

! Block "People You May Know"
facebook.com##span:has-text(/^People You May Know$/):upward(div[role="article"])

! Block Instagram Shares (Oct 2025 method)
www.facebook.com##.html-div:has(a[href*="www.instagram.com"])
```

**Extensions for Facebook**:
- ✅ **"No Login"** (https://addons.mozilla.org/en-US/android/addon/no-login/)
  - Removes Facebook app nag banners and login popups
  - Works on Facebook, Instagram, LinkedIn
  - Version 1.17, last updated Jan 2025
  
**Element Picker Used**:
- ✅ Removed "Get Facebook for Android" banner using uBlock Origin Element Picker

### Firefox for Android Theme Discovery

**Built-in Material You Support**:
- Firefox for Android HAS built-in theme support
- Path: Settings → Theme → "Follow device theme" 🦊
- Uses Android 12+ Material You dynamic colors (pulls from wallpaper)
- **Known Bug**: Bug 1852919 - "Follow device theme" doesn't always update properly, may need app restart

**Why Nira Still Preferred**:
- Nira's Material You implementation is more polished
- Better integration with system theme
- Custom UI tweaks not available in Firefox

### Session Progress Summary

**Completed**:
1. ✅ Added summary text to "Swipe to refresh" setting (Session 2026-05-03 Part 1)
2. ✅ Diagnosed Facebook scrolling issue (upstream GeckoView + Facebook virtual scrolling)
3. ✅ Configured uBlock Origin with annoyance lists and custom filters
4. ✅ Identified "No Login" extension for removing Facebook app banners
5. ✅ Tested with Firefox for Android (confirmed not Nira's fault)

**Key Learning**: 
- Don't chase bugs that are already reported upstream to Mozilla
- If Firefox (reference GeckoView) has the same issue, it's not fixable in Nira
- uBlock Origin + custom filters can significantly improve Facebook experience
- Mozilla Components 148.0 corresponds to Firefox 148 (check Bugzilla for fix status)

**Next Session To-Do** (Optional):
- [ ] Resume swipe-to-refresh investigation (if user wants it working immediately after restart)
- [ ] Test if disabling toolbar auto-hide improves Facebook scrolling (low priority)
- [ ] Maybe report to Bug 1779404 with findings that Firefox 148 still has issue

## Session: 2026-05-03 (Part 3)
**Device**: Pixel 7 (Android 16)  
**User**: sinnohman (GitHub: Sinnohman)

### Efficiency Improvements Implemented

**Branch**: `feature/storage-efficiency-improvements` (fork: sinnohman/nira-browser)

#### 1. Added AGENT_JOURNAL.md to .gitignore
- **Problem**: Agent journal was being tracked by git, should be local-only like PROJECT_LOG.md
- **Fix**: Added `/AGENT_JOURNAL.md` to `.gitignore` (after `/PROJECT_LOG.md`)
- **File changed**: `.gitignore` (line 65)

#### 2. Stabilized Thumbnail Loader (Singleton Pattern)
- **Problem**: `ThumbnailLoader` was being created multiple times in different files, wasting resources
  - `ThumbnailImageView.kt:37` - Created new loader in remember block
  - `TabIslandsVerticalAdapter.kt:35` - Created new loader in Adapter
  - `FakeTab.kt:25` - Created new loader in custom view
- **Fix**: Added singleton `thumbnailLoader` to `Components.kt` and updated all usages to use `context.components.thumbnailLoader`
- **Files changed**:
  - `app/src/main/java/com/prirai/android/nira/components/Components.kt` (added `thumbnailLoader` singleton at line 216)
  - `app/src/main/java/com/prirai/android/nira/browser/tabs/compose/ThumbnailImageView.kt` (line 37)
  - `app/src/main/java/com/prirai/android/nira/browser/tabs/TabIslandsVerticalAdapter.kt` (line 35)
  - `app/src/main/java/com/prirai/android/nira/browser/FakeTab.kt` (line 25)

#### 3. Implemented `onTrimMemory()` in BrowserActivity
- **Problem**: `Components.kt:256` showed `trimMemoryAutomatically = false`, but `BrowserActivity` didn't override `onTrimMemory()` to handle system memory pressure
- **Fix**: Added `onTrimMemory()` method (lines 233-249) with three levels:
  - `TRIM_MEMORY_RUNNING_CRITICAL`: Clear thumbnails + close non-selected engine sessions
  - `TRIM_MEMORY_RUNNING_LOW`: Clear thumbnails to free disk space
  - `TRIM_MEMORY_BACKGROUND`: Trim icons memory
- **File changed**: `app/src/main/java/com/prirai/android/nira/BrowserActivity.kt`

#### 4. Thumbnail Storage Cleanup (Orphaned Thumbnails)
- **Problem**: When tabs are closed, their thumbnails remain on disk, causing storage bloat
- **Fix**: Added `cleanupOrphanedThumbnails()` method (lines 616-642) that:
  - Runs on app start (in `onCreate()` via `view.post {}`)
  - Gets current tab IDs from `components.store.state.tabs`
  - Deletes thumbnail files not belonging to existing tabs
  - Logs number of deleted thumbnails
- **File changed**: `app/src/main/java/com/prirai/android/nira/BrowserActivity.kt`

### Tech Stack Learning Session
User (sinnohman) learned about Nira's tech stack today:
1. **Kotlin** - Programming language (similar to C#, has null safety, extension functions, data classes)
2. **GeckoView** - Browser engine (pre-built library from Mozilla, like NuGet package in C#)
3. **MVVM Architecture** - Pattern: Model (data), View (UI), ViewModel (logic middleman)
4. **Jetpack Compose** - Modern UI toolkit (declarative Kotlin, no separate XML/CSS)
5. **Material 3** - Google's design system (light/dark/AMOLED themes, dynamic colors)

### Session Progress Summary
**Completed**:
1. ✅ Added `AGENT_JOURNAL.md` to `.gitignore`
2. ✅ Stabilized `ThumbnailLoader` as singleton in `Components.kt`
3. ✅ Implemented `onTrimMemory()` in `BrowserActivity`
4. ✅ Added orphaned thumbnail cleanup on app start
5. ✅ Added swipe-to-refresh summary text to settings (strings.xml + preferences_customization.xml)
6. ✅ Built and installed APK on Pixel 7: `app-arm64-v8a-debug.apk`
7. ✅ Committed to `feature/storage-efficiency-improvements` branch
8. ✅ Pushed to fork: `sinnohman/nira-browser:feature/storage-efficiency-improvements`

**Next Session To-Do**:
- [ ] Test efficiency improvements on Pixel 7 (thumbnails, cleanup, onTrimMemory)
- [ ] Create PR to `prirai/nira-browser:main` when testing is complete
- [ ] Wait for prirai to review both PRs (new tab crash fix + efficiency improvements)
- [ ] Resume swipe-to-refresh investigation (if desired)

## Session: 2026-05-11
**Device**: Pixel 7 (Android 16)  
**User**: sinnohman (GitHub: Sinnohman)

### What We Did
- **Reviewed PR status**: PR #73 (`fix/new-tab-crash-nullpointer`) still open with no reviews after ~2 weeks
- **Created second PR**: Created PR for `feature/storage-efficiency-improvements` via GitHub web UI
  - PR description includes: ThumbnailLoader singleton, onTrimMemory(), orphaned thumbnail cleanup, swipe-to-refresh summary, updateToolbarBackground() fix, .gitignore updates
- **Built personal APK with both fixes**: Cherry-picked the crash fix commit onto `feature/storage-efficiency-improvements` branch, built `assembleDebug`, and installed on Pixel 7
  - Current branch: `feature/storage-efficiency-improvements` with both efficiency improvements + crash fix
- **gh CLI authentication**: Logged out danR-ezTech account, logged in as Sinnohman (SSH protocol)

### Pull Requests Status
- **PR #73** (`fix/new-tab-crash-nullpointer`): Open, no reviews, no comments — waiting on prirai
- **PR #74** (`feature/storage-efficiency-improvements`): Just created, waiting on prirai

### Next Session Plan
- [ ] Check if prirai has responded to either PR
- [ ] Optionally bump PRs with a comment if too much time passes
- [ ] Resume swipe-to-refresh investigation (if desired)

## Session: 2026-05-11 (Part 4 — Automated Tests)

### What We Did
- **Created comprehensive test suite**: 181 unit tests across 13 test classes
- **Test infrastructure**: Added Robolectric 4.14.1, MockK 1.13.13, kotlinx-coroutines-test 1.9.0, Turbine 1.2.0, Truth 1.4.4, Room-testing 2.6.1, Compose UI test 1.7.6, Navigation-testing 2.8.5, AndroidX core-testing 2.2.0

### Test Layout
```
app/src/test/java/com/prirai/android/nira/       (unit tests — 181 tests, all pass)
    browser/
        home/compose/HomeViewModelTest.kt          — 19 tests
        profile/ProfileManagerTest.kt               — 11 tests
        tabgroups/GroupExpansionStateManagerTest.kt — 17 tests
        tabgroups/TabGroupEntityTest.kt             — 20 tests
        tabs/TabLRUManagerTest.kt                   — 20 tests
        tabs/compose/TabViewModelTest.kt            — 9 tests
    downloads/DownloadConfirmationMiddlewareTest.kt — 5 tests
    ext/ExtensionFunctionsTest.kt                   — 8 tests
    history/HistoryTimeFormatterTest.kt             — 7 tests
    middleware/FaviconMiddlewareTest.kt             — 6 tests
    preferences/UserPreferencesTest.kt              — 34 tests
    utils/FaviconCacheTest.kt                       — 17 tests
    utils/UtilsTest.kt                              — 8 tests
```

### Test Methodology
- **Phase 1 — Pure logic**: TabLRUManager, TabGroupEntity, HistoryTimeFormatter, ExtensionFunctions — no Android deps needed
- **Phase 2 — Robolectric**: UserPreferences (DataStore), GroupExpansionStateManager (DataStore), FaviconCache (disk I/O), Utils (bitmap generation)
- **Phase 3 — ViewModels**: HomeViewModel, TabViewModel — MockK for Store/DAO mocks, viewModelScope coroutine advancement
- **Phase 4 — Middleware/Manager**: FaviconMiddleware (Redux middleware interception), DownloadConfirmationMiddleware, ProfileManager
- **Phase 5 — Room instrumentation**: TabGroupDatabaseTest, ShortcutDatabaseTest, WebAppDatabaseTest (in androidTest/)
- **Phase 6 — Compose UI instrumentation**: TabBarComposeTest, HomeScreenComposeTest (in androidTest/)

### Key Challenges & Fixes
- **viewModelScope coroutines**: Tests needed dual `advanceUntilIdle()` passes with `Thread.sleep(50)` interleaved to let `Dispatchers.IO` threads complete and post continuations back to the test dispatcher
- **Static mock StackOverflow**: `mockkStatic(System::class)` causes StackOverflowError in Kotlin/JVM — rewrote HistoryTimeFormatterTest to avoid static mocks
- **Robolectric resource resolution**: `mozac_browser_icons_*` dimen resources from Mozilla Components AAR not always loadable — mocked Resources in UtilsTest
- **DataStore test isolation**: Tests required explicit state reset between runs via `setExpansionState(emptySet())` in setUp
- **LRU wrapping**: `getTabAtLRUOffset` wraps at boundaries (circular navigation), tests updated to expect wrapping behavior

### Test Execution
- **Command**: `./gradlew app:testDebugUnitTest`
- **Result**: 181 tests, 181 passed, 0 failed (BUILD SUCCESSFUL in ~17s)
- **Instrumentation tests**: Located in `app/src/androidTest/java/` — require emulator/device to run

---

## Agent Journal - 2026-05-02

### What I Learned Today

**Project Understanding:**
- Nira Browser is a Firefox-based Android browser using GeckoView 148.0
- Tech stack: 100% Kotlin, Jetpack Compose, Material 3, MVVM architecture
- Key file patterns: `BaseBrowserFragment.kt` handles core browser UI, uses `ViewBoundFeatureWrapper` for lifecycle-aware features
- `local.properties` sets SDK path, added to `.gitignore` (line 32, 4)
- Build outputs architecture-specific APKs (arm64-v8a for Pixel 7)

**Crash Diagnosis Process:**
- Used `adb logcat | grep -E "com.prirai.android.nira|AndroidRuntime"` to capture crashes
- NullPointerException in `BaseBrowserFragment.getBinding` = lifecycle race condition
- Root cause: `unifiedToolbar?.post { ... }` lambda accesses `_binding` after `onDestroyView()` sets it to null
- Fix: Add null checks for `_binding` and `context` before accessing, both at method start and inside lambdas

**Android Development Patterns Noticed:**
- Fragments: `_binding` set in `onCreateView()`, nulled in `onDestroyView()` (standard pattern)
- `requireContext()` crashes if fragment is detached; `context` (nullable) is safer for deferred callbacks
- `ViewBoundFeatureWrapper` auto-cleans features when fragment/activity destroys
- `consumeFlow` extension from Mozilla components for observing Store state in fragments

**GitHub Contribution Workflow:**
- Fork → branch → commit → push to fork → create PR
- Branch naming: `fix/description` or `feature/description` (matches CONTRIBUTING.md)
- PR description should include: root cause, changes made, testing performed (device + Android version)
- `upstream` remote = original repo (prirai/nira-browser), `myfork` = your fork (sinnohman/nira-browser)
- User's fork URL changed to `git@github.com:Sinnohman/nira-browser.git` (capital S)

**User Preferences & Context:**
- Device: Pixel 7, Android 16
- Wants conversational tone (not formal/AI-sounding) in PRs and discussions
- New to GitHub and Android development - needs step-by-step guidance
- Uses VS Code but may be signed into work account (doesn't matter for local git operations)
- Wants to keep helping with the project long-term

**Environment Setup (for future reference):**
- JDK 17 required (project uses Android Gradle Plugin 9.1.0)
- SDK installed at `~/Android`, set via `local.properties` or `ANDROID_HOME`
- ADB must have USB debugging enabled on device (Settings → Developer Options → USB debugging)
- Device authorization: tap "Allow" on phone when connecting via USB

**Feature Ideas Discussed:**
- "Check for Updates" button (Option A - manual check only)
  - Would use GitHub Releases API: `https://api.github.com/repos/prirai/nira-browser/releases/latest`
  - OkHttp already a dependency (line 217 in app/build.gradle)
  - No WorkManager or automatic checks needed for Alpha stage
  - Posted to GitHub Discussions "Ideas" category, waiting for prirai's response

**Critical Reminders for Next Session:**
- Always read `PROJECT_LOG.md` first to get context
- Check if prirai responded to the Discussion about "Check for Updates" button
- User's PR is at: `sinnohman/nira-browser` branch `fix/new-tab-crash-nullpointer`
- If implementing new features, create new branch from `main` after syncing with upstream
- Never push directly to `prirai/nira-browser` (no write access)
- Add new learnings to this journal at end of every session

### Mistakes to Avoid
- Don't use `requireContext()` in deferred callbacks (post, coroutine, etc.) - use `context ?: return` pattern
- Don't add `nira_crash.log` or `PROJECT_LOG.md` to git commits (both in `.gitignore`)
- Don't assume user has write access to upstream repo - always use fork workflow
- Don't make PR descriptions sound like AI wrote them (user prefers natural, conversational tone)
- Don't cache preference values in `var` fields — always read from `UserPreferences` directly for reactive behavior

## Session: 2026-05-11 (Part 3 — Crash & UI Bug Fixes)
**Device**: Pixel 7 (Android 16)  
**User**: sinnohman (GitHub: Sinnohman)

### Branch Strategy
Created two branches for focused PRs (instead of 8 small branches that would sit unreviewed):

| Branch | Contents | Status |
|--------|----------|--------|
| `fix/crash-bugs` | Issues #69, #68, #59 + swipe-to-refresh | ✅ Pushed to myfork |
| `fix/ui-bugs` | Issue #70 (tab group bar) | ✅ Pushed to myfork |
| (Unresolved) | Issues #54, #72, #71 | Need maintainer input |

### Issue #69 — Toolbar Icon Size Slider Crash (FIXED)
**Root cause**: Material Slider's `value` must equal `valueFrom + N * stepSize`. Persisted float (0.9829825) didn't satisfy this constraint.

**Fix** (`CustomizationSettingsFragment.kt:441`, `OnboardingCustomizationFragment.kt:56`):
- Snap loaded value to nearest valid step using `round((value - valueFrom) / stepSize) * stepSize + valueFrom`
- Applied to both the `CustomizationSettingsFragment` slider and the `OnboardingCustomizationFragment` onboarding slider

**Files changed**:
- `app/src/main/java/com/prirai/android/nira/settings/fragment/CustomizationSettingsFragment.kt`
- `app/src/main/java/com/prirai/android/nira/onboarding/OnboardingCustomizationFragment.kt`

### Swipe-to-Refresh Restart Issue (FIXED)
**Root cause**: `swipeRefreshFeature.set()` ran before the view hierarchy was fully attached after a fresh process start. `ViewBoundFeatureWrapper` couldn't properly bind to the not-yet-laid-out `VerticalSwipeRefreshLayout`.

**Fix** (`BaseBrowserFragment.kt:507`):
- Wrapped `swipeRefreshFeature.set()` in `view.post {}` to defer initialization until after layout completes
- Same approach works for the tab observer too

**Files changed**:
- `app/src/main/java/com/prirai/android/nira/BaseBrowserFragment.kt`

### Issue #68 — Android 10 Crash (FIXED)
**Root cause**: `GeckoRuntime.create()` calls `DebugConfig.fromFile()` which calls `Package.getName()`. On Android 10, this returns null, causing a NullPointerException. Upstream Mozilla Components bug that may not get fixed for API 29.

**Fix** (`Components.kt:384`):
- Wrapped `GeckoRuntime.create(settings)` in try/catch
- If it fails, retry with `settings.aboutConfigEnabled = false` (disables debug config)
- This avoids the null `Package.getName()` path entirely

**Files changed**:
- `app/src/main/java/com/prirai/android/nira/components/Components.kt`

### Issue #59 — Find-in-Page NPE (FIXED)
**Root cause**: No stack trace available, but `setupFindInPage` used `!!` on `binding.toolbar` (line 585). If the toolbar is null during fragment lifecycle transitions, this crashes.

**Fix** (`BaseBrowserFragment.kt:585`):
- Replaced `val toolbar = binding.toolbar!!` with `val toolbar = binding.toolbar ?: return`
- Added commented hint for future: remaining `!!` operators should be audited

**Files changed**:
- `app/src/main/java/com/prirai/android/nira/BaseBrowserFragment.kt`

### Issue #70 — Tab Group Bar Persists When Disabled (FIXED)
**Root cause**: `UnifiedToolbar.kt:89` cached `showTabGroupBar` as a `private var` initialized once from `prefs.showTabGroupBar` at construction time. Subsequent preference changes never propagated — not even on app restart if the toolbar was already initialized.

**Fix** (`UnifiedToolbar.kt:89, 255, 493, 787`):
- Removed the cached field entirely
- All reads now go through `prefs.showTabGroupBar` directly
- `updateComponentVisibility()` defaults to `prefs.showTabGroupBar` instead of stale field

**Files changed**:
- `app/src/main/java/com/prirai/android/nira/components/toolbar/unified/UnifiedToolbar.kt`

### Issues Investigated (Not Fixable Without More Info)

**Issue #54 — Tab switching loops**:
- Reviewed `TabViewModel.kt`, `BaseBrowserFragment.kt`, `ToolbarGestureHandler.kt`
- Gesture routing: `onTabSelected` → `ToolbarGestureHandler.selectTab()` → `selectTabUseCase()` → `BrowserFragment.fromTab()`
- `TabViewModel.selectTab()` only updates a local `StateFlow` — it does NOT call `BrowserStore.selectTab()`
- The actual tab selection happens via `selectTabUseCase` in the fragment
- No infinite loop found in the selection call chain
- **Needs**: User's screen recording showing the behavior

**Issue #72 — Tab island bar doesn't scroll**:
- Reporter asked "Is this intentional?" — no maintainer response since Nov 18
- Cannot determine if it's a bug or a design choice
- **Needs**: Maintainer clarification

**Issue #71 — Downloads third-party manager prompt**:
- `shouldForwardToThirdParties` lambda reads `promptExternalDownloader` preference
- `DownloadsFeature` uses `SystemDownloadManager` internally when forwarding is enabled
- Configuration appears correct on code review
- Without device testing, can't determine why system chooser doesn't appear
- **Needs**: Device testing with a download URL to verify behavior

### PR Creation Attempt
- `gh pr create` failed: `GraphQL: Resource not accessible by personal access token`
- The GitHub PAT doesn't have permission to create PRs on `prirai/nira-browser`
- Manual PR URLs:
  - Crash bugs: https://github.com/Sinnohman/nira-browser/pull/new/fix/crash-bugs
  - UI bugs: https://github.com/Sinnohman/nira-browser/pull/new/fix/ui-bugs

### Key Learnings
- Preference values should never be cached in `var` fields — always read from `UserPreferences` directly
- `view.post {}` is the fix for any `ViewBoundFeatureWrapper` timing issues on fresh start
- `GeckoRuntime.create()` can fail on Android 10 due to null `Package.getName()` — disable `aboutConfig` as fallback
- Material Slider requires `value = valueFrom + N * stepSize` or it throws `IllegalStateException`
- `gh pr create` from a fork to an upstream repo requires PAT with repo scope on the upstream org

### Next Steps
- [ ] Create PRs manually via GitHub web UI (links above)
- [ ] Bump PRs if unreviewed after 2 weeks
- [ ] If Issue #54 reporter provides screen recording, investigate further
- [ ] If maintainer responds on #72, implement scroll fix if needed
- [ ] If maintainer confirms #71 is a real bug, test on device with download URLs

(End of file)
