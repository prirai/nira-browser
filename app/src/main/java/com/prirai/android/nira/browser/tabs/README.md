# Tab Management Package

This package contains the tab management UI and logic for the Nira browser.

## Package Structure

```
tabs/
├── TabsBottomSheetFragment.kt    # Main tab switcher UI (Compose)
├── TabsTrayFragment.kt            # Alternative full-screen tab view
├── TabSearchFragment.kt           # Tab search dialog
├── TabConfiguration.kt            # Tab filtering configuration
├── TabItem.kt                     # Data classes for tab items
├── MergedProfileButton.kt         # Profile switcher button
│
├── compose/                       # Compose-based UI components (ACTIVE)
│   ├── TabViewModel.kt           # State management
│   ├── TabSheetListView.kt       # List view composable
│   ├── TabSheetGridView.kt       # Grid view composable
│   ├── TabCardComponents.kt      # Reusable tab card UI
│   ├── TabOrderManager.kt        # Tab ordering logic
│   ├── AdvancedDragDropSystem.kt # Drag-and-drop for list
│   ├── CustomDragDropSystem.kt   # Drag-and-drop for grid
│   └── ...                       # Other Compose utilities
│
└── [Legacy Files]                 # RecyclerView adapters (DEPRECATED)
    ├── TabsAdapter.kt            # Simple adapter (kept for compatibility)
    ├── TabListAdapter.kt         # Used by TabsTrayFragment
    ├── TabsWithGroupsAdapter.kt  # (unused, can be removed)
    ├── TabsGridAdapter.kt        # (unused, can be removed)
    └── dragdrop/                 # (unused, can be removed)
```

## Active System

The browser currently uses a **Compose-based tab management system**:

1. **UI**: `TabsBottomSheetFragment` displays tabs using Jetpack Compose
2. **Views**: `TabSheetListView` (list) and `TabSheetGridView` (grid)
3. **State**: `TabViewModel` manages tab state with StateFlow
4. **Groups**: `UnifiedTabGroupManager` handles tab grouping
5. **Storage**: Mozilla Components Store for tab data

## Key Features

- **List and Grid Views**: Switch between card list and compact grid
- **Tab Groups**: Color-coded groups with drag-to-add
- **Multi-Profile**: Separate tabs for work, personal, etc.
- **Private Mode**: Incognito browsing support
- **Drag and Drop**: Reorder tabs and create groups
- **Tab Search**: Fuzzy search across all tabs
- **Context Menus**: Long-press for tab operations

## Usage

### Opening Tab Switcher

```kotlin
val fragment = TabsBottomSheetFragment.newInstance()
fragment.show(fragmentManager, TabsBottomSheetFragment.TAG)
```

### Tab Operations

Tab operations are handled through Mozilla Components:

```kotlin
// Add tab
components.tabsUseCases.addTab("https://example.com")

// Close tab
components.tabsUseCases.removeTab(tabId)

// Select tab
components.tabsUseCases.selectTab(tabId)
```

### Group Operations

Groups are managed by `UnifiedTabGroupManager`:

```kotlin
val groupManager = UnifiedTabGroupManager.getInstance(context)

// Create group
groupManager.createGroup(tabIds, "Work", "blue")

// Add to group
groupManager.addTabToGroup(tabId, groupId)

// Remove from group
groupManager.removeTabFromGroup(tabId)
```

## Architecture

See `docs/TAB_ARCHITECTURE.md` for detailed architecture documentation.

## Development Notes

### Compose System (Current)

The system uses `useComposeTabSystem = true` by default. This flag can be found in `TabsBottomSheetFragment.kt` line ~77.

**Advantages:**
- Modern, declarative UI
- Better animations
- Simpler state management
- Less boilerplate code

### Legacy RecyclerView System (Deprecated)

The old RecyclerView-based adapters are still in the codebase for backwards compatibility but are not actively used. These can be safely removed in a future release.

**To use legacy system** (not recommended):
```kotlin
// In TabsBottomSheetFragment
private var useComposeTabSystem = false
```

### Testing Changes

When modifying tab code, test these scenarios:
1. Create/close tabs (normal and private)
2. Switch between tabs
3. Create and manage groups
4. Drag tabs to reorder
5. Drag tabs into groups
6. Switch profiles
7. Toggle list/grid view
8. Search tabs
9. Long-press menus
10. Empty state (no tabs)

### Performance

The Compose system uses:
- `LazyColumn`/`LazyVerticalGrid` for efficient rendering
- `remember` to cache expensive computations
- `produceState` for reactive state updates
- Stable keys to prevent unnecessary recompositions

## Common Tasks

### Adding a New Tab Operation

1. Add method to `TabViewModel`
2. Call from UI (list/grid composables)
3. Update `UnifiedTabGroupManager` if needed
4. Test thoroughly

### Customizing Tab Card UI

Edit `TabCardComponents.kt`:
- `TabCard` composable for list view
- Styles in `TabVisualStyles.kt`
- Colors in theme files

### Modifying Group Behavior

Edit `UnifiedTabGroupManager.kt` for business logic.
Edit `TabGroupBar.kt` for tab bar UI.

## Future Improvements

See `docs/TAB_SYSTEM_IMPROVEMENTS.md` for:
- Cleanup recommendations
- Performance optimizations
- UI/UX enhancements
- Migration guide for removing legacy code

## Questions?

For architecture questions, see:
- `docs/TAB_ARCHITECTURE.md` - System design
- `docs/TAB_SYSTEM_IMPROVEMENTS.md` - Improvement guide
- Code comments in `TabsBottomSheetFragment.kt`
