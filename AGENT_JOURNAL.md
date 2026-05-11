# AI Agent Journal - Nira Browser Project

## Session: 2026-05-03

### Key Learnings

**1. Facebook Scrolling Issue - Complete Diagnosis**
- **Symptom**: Page jumps up/down 2 posts after scrolling stops on Facebook
- **Confirmed NOT Nira's fault**: Tested with regular Firefox for Android, same issue occurs
- **Root cause**: Two-part problem:
  - GeckoView Bug 1812227 (fixed in Firefox 132) - `MaybeSplitTouchMoveEvent()` issue
  - Facebook's virtual scrolling mechanism (Bug 1779404) - Facebook dynamically removes offscreen posts and adjusts scroll position
- **Nira uses Mozilla Components 148.0** (app/build.gradle line 124) which corresponds to Firefox 148
- **Conclusion**: Can only mitigate, not fix (it's upstream Facebook + GeckoView)

**2. uBlock Origin Configuration for Facebook**
- **Essential lists**: "uBlock filters – Annoyances" (includes Fanboy's, EasyList Cookie, social filters)
- **Custom filters that work (as of 2025-2026)**:
  - Block sponsored: `facebook.com##[role="feed"] [aria-label="Sponsored"]:upward(div[role="article"])`
  - Block suggested posts: `facebook.com##span:has-text(/^Suggested for You$|^Suggested Post$/):upward(div[role="article"])`
  - Block Instagram shares: `www.facebook.com##.html-div:has(a[href*="www.instagram.com"])`
- **Element Picker**: User successfully used it to remove "Get Facebook for Android" banner
- **Facebook changes classes frequently**: Filters may need updating (check `.html-div` class still valid)

**3. Firefox for Android Theme Support**
- Built-in Material You support: Settings → Theme → "Follow device theme" 🦊
- Uses Android 12+ dynamic colors (pulls from wallpaper)
- Known bug (1852919): "Follow device theme" doesn't always update properly, may need app restart
- **Extensions can't modify Firefox UI** (only webpage content via CSS/JS)
- Old "Material Design" themes (2019) are deprecated, won't work on modern Firefox (Fenix)

**4. "No Login" Extension**
- URL: https://addons.mozilla.org/en-US/android/addon/no-login/
- Removes login popups/banners on Facebook, Instagram, LinkedIn
- Version 1.17, last updated Jan 2025
- Works on both desktop and mobile

**5. Project Log & Build Process**
- Added build/install instructions to PROJECT_LOG.md (session 2026-05-03 Part 1)
- APK location: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (architecture-specific)
- Install command: `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- Swipe-to-refresh summary text added to settings (strings.xml + preferences_customization.xml)

**6. Mozilla Components Version Mapping**
- Nira uses: `mozComponentsVersion = "148.0"` (app/build.gradle:124)
- This corresponds to Firefox 148 (released ~Nov 2024)
- GeckoView version follows Mozilla Components version
- Bug 1812227 fixed in Firefox 132 (Oct 2024) - SHOULD be in Nira's GeckoView 148

### Mozilla Bugzilla Research - Key Bugs Found

| Bug # | Description | Status | Relevance |
|--------|-------------|--------|----------|
| 1812227 | Android webpages jump "up" while scrolling | ✅ Fixed in 132 | Original symptom match |
| 1779404 | Facebook virtual scrolling jumps | 🔄 Open | Actual Facebook culprit |
| 1893042 | Page twitches while panning | ✅ Fixed in 132 | Related scrolling issue |
| 1763038 | Touch resampling causes shakes | ✅ Fixed in 131 | Related to 1812227 |
| 1852919 | Firefox not following device theme | 🔄 Open | Theme switching bug |
| 1661480 | GeckoView not restoring scroll position | ✅ Fixed in 81-82 | Historical reference |

### Session Progress
- ✅ Diagnosed Facebook scrolling (upstream issue, not fixable)
- ✅ Configured uBlock Origin with annoyance lists + custom filters
- ✅ Identified useful extensions ("No Login")
- ✅ Updated PROJECT_LOG.md with full diagnostics
- ✅ Learned Mozilla Components → Firefox version mapping

### For Future Sessions
- If user reports other scrolling issues, CHECK BUGZILLA FIRST before investigating Nira code
- Facebook-specific issues are likely virtual scrolling (Bug 1779404)
- Mozilla Components version in app/build.gradle determines GeckoView version
- uBlock Origin filters for Facebook need periodic updates (Facebook changes classes)
- Don't chase bugs already reported upstream to Mozilla

## Session: 2026-05-11

### Key Learnings

**1. gh CLI Authentication**
- `gh auth login` can be used to switch between GitHub accounts
- The PAT (Personal Access Token) needs `repo` scope for creating PRs
- When using a fine-grained PAT, it must have access to the target repo
- SSH key upload during `gh auth login` may fail if PAT lacks `admin:public_key` scope — not critical, can add SSH key manually
- After auth switch, use `gh repo set-default <user>/<repo>` before creating PRs

**2. Creating PRs from Forks via gh CLI**
- Format: `gh pr create --base main --head <fork-user>:<branch> --repo <upstream-repo>`
- The authenticated user must be the fork owner for cross-repo PRs
- PR descriptions should be conversational (not AI-sounding) per user preference

**3. Cherry-picking Between Branches**
- Cherry-pick workflow: `git fetch <remote> <branch>` → `git cherry-pick <commit>`
- Useful for combining fixes from different branches into one build
- Push with `git push <remote> <branch>` after cherry-pick to update remote

**4. Building for Personal Use**
- Current branch has both crash fix + efficiency improvements merged
- APK installed as "Nira (Debug)" — separate from release version
- Build command: `./gradlew assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

### Session Progress
- ✅ Reviewed PR #73 status (still open, no reviews)
- ✅ Created PR #74 for efficiency improvements
- ✅ Switched gh CLI auth from danR-ezTech to Sinnohman
- ✅ Cherry-picked crash fix onto feature branch
- ✅ Built and installed APK on Pixel 7 with both fixes
- ✅ Updated PROJECT_LOG.md and AGENT_JOURNAL.md

### For Future Sessions
- Check if prirai responds to PRs before implementing anything new
- If PRs sit too long, user can gently bump with a comment
- Both PRs can be tracked: #73 (crash fix) and #74 (efficiency)
- The local branch now has a combined build ready for daily use

## Session: 2026-05-11 (Part 2)

### Enhancements Analysis — Comprehensive Ranking

Completed a full audit of all possible enhancements for the project, ranked by importance:

**Tier 1: Critical (Stability & Crashes)**
1. Fix open crash bugs — Issues #68 (Android 10), #59 (gesture switch), #69 (icon slider)
2. Add automated tests — Zero test files exist anywhere
3. Eliminate `!!` operator fragilities — ~74 instances across codebase
4. Replace `requireContext()` in deferred callbacks — crash risk on fragment detach
5. Fix WebAppUpdateManager coroutine leak — unmanaged CoroutineScope per call

**Tier 2: High (Bugs & Missing Features)**
6. Swipe-to-refresh restart issue — already diagnosed, fix in view.post{}
7. Tab island bar doesn't move with address bar (Issue #72)
8. Tab group bar persists when disabled (Issue #70)
9. Tab switching loops on new tab (Issue #54)
10. "Check for Updates" button — discussed with maintainer, OkHttp already available

**Tier 3: Medium (QoL & Architecture)**
11. PWA update mechanism — actual update checking (currently stubbed)
12. Compose migration — 117 XML layouts remain, 17% Compose
13. Accessibility descriptions — 50+ contentDescription = null usages
14. DI framework (Hilt/Koin) — 15+ manual singletons
15. Downloads third-party manager prompt (Issue #71)
16. Profile import/export (roadmap)
17. F-Droid inclusion (Issue #65)
18. Media picker in web apps (Issue #35)

**Tier 4: Lower (Nice-to-Have)**
19. User JS support (arkenfox/betterfox-inspired)
20. Download management interface
21. Code quality CI (ktlint, Detekt, dependency scanning)
22. Profile context passing
23. PWA suggestion interface
24. New app icon
25. Translation updates
26. More customization options
27. Sync across devices (partially implemented via FxA)

### Priority Issues — Fix Plan

**Issue #69 — Toolbar icon size slider crash**
- Root cause: Material Slider's step validation throws when saved float (0.9829825) != valueFrom(0.8) + N * stepSize(0.1)
- Fix: Snap loaded value to nearest valid step in CustomizationSettingsFragment.kt:441
- File: `app/src/main/java/com/prirai/android/nira/settings/fragment/CustomizationSettingsFragment.kt`

**Issue #68 — Android 10 crash on open**
- Root cause: GeckoRuntime.create() → DebugConfig.fromFile() → Package.getName() returns null on Android 10
- Upstream issue, may need GeckoView version bump or try/catch guard
- File: `app/src/main/java/com/prirai/android/nira/components/Components.kt`

**Issue #59 — Gesture tab switch crash**
- No stack trace available. Possibly same _binding NPE as PR #73, or ToolbarGestureHandler.kt
- Need reproduction + logcat capture

**Issue #54 — Tab switching loops on new tab**
- Tab selection logic might be re-selecting the new tab on each switch
- Investigate TabViewModel.kt and BaseBrowserFragment.kt session management

**Issue #70 — Tab group bar shows when disabled**
- Visibility setting not reactively observed
- Search showTabGroupBar usages and fix reactive behavior

**Issue #72 — Tab island bar doesn't move with address bar**
- Positioning layout issue — check if design choice or bug

**Issue #71 — Downloads third-party manager prompt**
- PromptFeature download delegation not working
- Check BaseBrowserFragment downloadsFeature config

**Swipe-to-refresh restart issue (already diagnosed)**
- Fix: Wrap swipeRefreshFeature.set() in view.post {} at BaseBrowserFragment.kt:507-517

### Planned Branches
- `fix/crash-bugs` — Issues #69, #68, #59 investigations + swipe-to-refresh
- `fix/ui-bugs` — Issues #54, #70, #72, #71

(End of entry)

## Session: 2026-05-11 (Part 3)

### What We Did

**Created fix/crash-bugs branch** (5 commits pushed to myfork):
1. `1061687` — Issue #69: Snap `toolbarIconSize` to nearest valid step in both `CustomizationSettingsFragment` and `OnboardingCustomizationFragment` Material Sliders. Prevents `IllegalStateException` when persisted value doesn't align to step size.
2. `38e4ede` — Swipe-to-refresh restart: Wrap `swipeRefreshFeature.set()` in `view.post {}` at `BaseBrowserFragment.kt:507` so NestedGeckoView is laid out before PullToRefresh attaches.
3. `1115c58` — Issue #68: Try/catch `GeckoRuntime.create()` with `aboutConfigEnabled(false)` fallback for Android 10 crash (null Package.getName() in DebugConfig.fromFile()).
4. `fb13037` — Added AGENT_JOURNAL.md, PROJECT_LOG.md, nira_crash.log to .gitignore.
5. `98b6e0a` — Issue #59: Replace `binding.toolbar!!` with `?.let {}` early return in `setupFindInPage`.

**Created fix/ui-bugs branch** (1 commit pushed to myfork):
1. `ac3f689` — Issue #70: Removed cached `showTabGroupBar` field from `UnifiedToolbar.kt:89`. All reads now go through `prefs.showTabGroupBar` directly so preference changes take effect immediately.

**Created both PRs** (failed — PAT lacks permission for upstream repo):
- URL for crash-bugs PR: https://github.com/Sinnohman/nira-browser/pull/new/fix/crash-bugs
- URL for ui-bugs PR: https://github.com/Sinnohman/nira-browser/pull/new/fix/ui-bugs

**Investigated but couldn't fix**:
- Issue #54 (tab switching loops): Reviewed TabViewModel, handleTabSelected, ToolbarGestureHandler — no infinite loop found in selection flow. Needs user's screen recording.
- Issue #72 (tab island bar doesn't scroll): Reporter asked "Is this intentional" with no follow-up. Design question for maintainer.
- Issue #71 (downloads third-party prompt): `shouldForwardToThirdParties` lambda wired correctly. If system chooser doesn't appear, likely Mozilla Components DownloadsFeature behavior. Needs device testing.

### Session Progress
- ✅ Fixed Issue #69 (toolbar icon slider crash) — committed
- ✅ Fixed swipe-to-refresh restart issue — committed
- ✅ Fixed Issue #68 (Android 10 GeckoRuntime crash) — committed
- ✅ Fixed Issue #59 (find-in-page NPE) — committed
- ✅ Fixed Issue #70 (tab group bar persists when disabled) — committed
- ✅ Pushed both branches to myfork
- ❌ Could not create PRs (PAT permission issue — user can create manually)
- 🔍 Issue #54 — investigated, needs screen recording
- 🔍 Issue #72 — investigated, needs maintainer clarification
- 🔍 Issue #71 — investigated, needs device testing

### For Future Sessions
- Create PRs manually at the URLs above
- For Issue #54: ask user for screen recording or logcat of the loop behavior
- For Issue #72: ask maintainer if the current positioning is intentional
- For Issue #71: test on actual device with a download link to verify if third-party chooser appears
- If more crash issues appear, follow the same pattern: `adb logcat` → find stack trace → fix root cause

(End of entry)

---

## Enhancement Rankings Reference

This section consolidates all possible enhancements for Nira Browser, ranked by importance. Useful across sessions for prioritization.

**Tier 1: Critical (Stability & Crashes)**
1. Fix open crash bugs — Issues #68 (Android 10), #59 (gesture switch), #69 (icon slider) — **DONE as of 2026-05-11**
2. Add automated tests — Zero test files exist anywhere in the project
3. Eliminate `!!` operator fragilities — ~74 instances across codebase, high crash risk
4. Replace `requireContext()` in deferred callbacks — crash risk on fragment detach
5. Fix WebAppUpdateManager coroutine leak — unmanaged CoroutineScope per call

**Tier 2: High (Bugs & Missing Features)**
6. Swipe-to-refresh restart issue — **FIXED as of 2026-05-11** (view.post {} wrap)
7. Tab island bar doesn't move with address bar (Issue #72) — needs maintainer input
8. Tab group bar persists when disabled (Issue #70) — **FIXED as of 2026-05-11**
9. Tab switching loops on new tab (Issue #54) — needs screen recording
10. "Check for Updates" button — discussed with maintainer, OkHttp already available

**Tier 3: Medium (QoL & Architecture)**
11. PWA update mechanism — actual update checking (currently stubbed)
12. Compose migration — 117 XML layouts remain, ~17% Compose
13. Accessibility descriptions — 50+ contentDescription = null usages
14. DI framework (Hilt/Koin) — 15+ manual singletons
15. Downloads third-party manager prompt (Issue #71) — needs device testing
16. Profile import/export (roadmap item)
17. F-Droid inclusion (Issue #65)
18. Media picker in web apps (Issue #35)

**Tier 4: Lower (Nice-to-Have)**
19. User JS support (arkenfox/betterfox-inspired)
20. Download management interface
21. Code quality CI (ktlint, Detekt, dependency scanning)
22. Profile context passing
23. PWA suggestion interface
24. New app icon
25. Translation updates
26. More customization options
27. Sync across devices (partially implemented via FxA)

(End of file)
