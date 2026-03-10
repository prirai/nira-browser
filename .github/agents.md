# LLM Agent Guide for Nira Browser

This document provides guidance for AI assistants and LLM models working on the Nira Browser codebase.

## Project Overview

**Nira Browser** is a privacy-focused Android browser built on Mozilla's GeckoView engine, featuring Material 3 design, multi-profile support, tab groups, PWA support, and advanced privacy features.

- **License**: Mozilla Public License 2.0 (MPL-2.0)
- **Language**: Kotlin (Android)
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
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
│   │   ├── profiles/        # Multi-profile system
│   │   └── ...
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
- `browser/profiles/ProfileManager.kt` - Profile CRUD
- `browser/profiles/Profile.kt` - Database entities
- `components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt` - Profile UI

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
- `browser/profiles/ProfileManager.kt` - Profile management
- `components/toolbar/modern/` - Profile switching UI
- Check for contextId filtering in tab/group queries

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
- `ProfileManager.kt` - Profile lifecycle management
- `WebAppManager.kt` - PWA lifecycle management
- `ColorConstants.kt` - Color definitions and conversions

### Data Models
- `TabGroup.kt` - Room entity for groups
- `Profile.kt` - Room entity for profiles
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

**Last Updated**: March 2026

For questions or updates to this guide, please open an issue or discussion on GitHub.
