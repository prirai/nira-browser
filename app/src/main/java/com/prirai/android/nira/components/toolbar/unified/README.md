# Unified Toolbar System

## Overview

The Unified Toolbar System centralizes all toolbar components in the Nira Browser, providing a coherent and well-organized architecture with shared theming and consistent behavior.

## Components

### 1. UnifiedToolbar.kt

The main toolbar component that brings together:
- **Tab Group Bar**: Horizontal bar showing current tab groups
- **Address Bar**: URL display and navigation controls
- **Contextual Toolbar**: Context-aware action buttons

**Key Features:**
- Shared theming across all components (background colors, text colors, elevations)
- Support for top/bottom positioning
- Visibility controls for each component
- Proper scroll behavior integration
- Material 3 design language
- Private mode theming support

**Usage:**
```kotlin
val toolbar = UnifiedToolbar.create(
    context = context,
    parent = parentContainer,
    lifecycleOwner = viewLifecycleOwner,
    interactor = toolbarInteractor,
    customTabSession = null,
    tabGroupManager = tabGroupManager,
    tabGroupBarListener = tabBarListener
)

// Set engine view for scroll behavior
toolbar.setEngineView(engineView)

// Update contextual toolbar based on browsing state
toolbar.updateContextualToolbar(
    tab = currentTab,
    canGoBack = true,
    canGoForward = false,
    tabCount = 5,
    isHomepage = false
)

// Apply private mode theming
toolbar.applyPrivateMode(isPrivate = true)
```

### 2. UnifiedToolbarTheme.kt

Centralized theming for all toolbar components.

**Provides:**
- Background colors (normal and private mode)
- Text colors (primary, secondary, hint)
- Icon colors (primary, secondary, security indicators)
- Elevation and shadows
- Dimensions (heights, paddings, margins)
- Material 3 design tokens

**Usage:**
```kotlin
val theme = UnifiedToolbarTheme(context)

// Get toolbar background color with Material 3 tonal elevation
val backgroundColor = theme.toolbarBackgroundColor

// Get private mode background color
val privateBackground = theme.privateToolbarBackgroundColor

// Get icon tint based on mode
val iconTint = theme.getIconTint(isPrivateMode = false)

// Get text color based on mode
val textColor = theme.getTextColor(isPrivateMode = false)
```

### 3. UnifiedToolbarCompose.kt

Compose wrapper for the unified toolbar, enabling integration with Compose-based screens.

**Features:**
- Composable interface for Jetpack Compose
- Swipe gesture support for tab navigation
- Proper state observation from browser store
- Seamless integration with existing Compose UI

**Usage:**
```kotlin
@Composable
fun MyScreen() {
    Column {
        // Add unified toolbar at top
        UnifiedToolbarComposable(
            isPrivateMode = isPrivateMode,
            onTabSwipe = { isNext ->
                // Handle tab swipe
            },
            showTabBar = true,
            showAddressBar = true,
            showContextualToolbar = true
        )

        // Content
        Box(modifier = Modifier.weight(1f)) {
            // Your content here
        }
    }
}
```

**With Swipe Gesture Detector:**
```kotlin
TabSwipeGestureDetector(
    onTabSwipe = { isNext ->
        if (isNext) {
            // Navigate to next tab
        } else {
            // Navigate to previous tab
        }
    }
) {
    // Wrap your content
}
```

## Configuration

### Toolbar Position

The toolbar supports both top and bottom positioning through `UserPreferences`:

```kotlin
val prefs = UserPreferences(context)

// Get toolbar position
val position = prefs.toolbarPositionType // TOP or BOTTOM

// Set toolbar position
prefs.toolbarPosition = ToolbarPosition.BOTTOM.ordinal
```

### Component Visibility

Control visibility of individual toolbar components:

```kotlin
val prefs = UserPreferences(context)

// Show/hide tab group bar
prefs.showTabGroupBar = true

// Show/hide contextual toolbar
prefs.showContextualToolbar = true
```

### Scroll Behavior

The toolbar automatically hides on scroll when enabled:

```kotlin
val prefs = UserPreferences(context)

// Enable/disable scroll hiding
// Note: Always enabled for bottom toolbar to prevent black bar at top
prefs.hideBarWhileScrolling // Always returns true
```

## Theming

### Colors

All toolbar colors are defined in `res/values/colors.xml`:

```xml
<!-- Unified Toolbar colors -->
<color name="primary_text_color">#212121</color>
<color name="secondary_text_color">#757575</color>
<color name="separator_color">#E0E0E0</color>
<color name="tab_count_badge_background">#5e5e5e</color>
<color name="tab_count_badge_text">#FFFFFF</color>
<color name="progress_bar_color">@color/colorAccent</color>
<color name="secure_icon_color">#4CAF50</color>
<color name="insecure_icon_color">#F44336</color>
```

### Dimensions

All toolbar dimensions are defined in `res/values/dimens.xml`:

```xml
<!-- Unified Toolbar dimensions -->
<dimen name="browser_toolbar_height">60dp</dimen>
<dimen name="contextual_toolbar_height">56dp</dimen>
<dimen name="tab_group_bar_height">48dp</dimen>
<dimen name="toolbar_corner_radius">0dp</dimen>
```

### Material 3 Elevation

The toolbar uses Material 3 tonal elevation (3dp) for a cohesive visual hierarchy:

```kotlin
val elevationDp = 3f * context.resources.displayMetrics.density
val elevatedColor = ElevationOverlayProvider(context)
    .compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
```

## Integration Examples

### ComposeHomeFragment

The home screen uses the unified toolbar with swipe gestures:

```kotlin
NiraTheme(isPrivateMode = isPrivateMode) {
    TabSwipeGestureDetector(
        onTabSwipe = { isNext ->
            handleTabSwipe(isNext, isPrivateMode)
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Add toolbar at top/bottom based on position
            if (toolbarPosition == ToolbarPosition.TOP) {
                UnifiedToolbarComposable(
                    isPrivateMode = isPrivateMode,
                    showTabBar = true,
                    showAddressBar = true,
                    showContextualToolbar = true
                )
            }

            // Content
            Box(modifier = Modifier.weight(1f)) {
                HomeScreen(/* ... */)
            }

            if (toolbarPosition == ToolbarPosition.BOTTOM) {
                UnifiedToolbarComposable(
                    isPrivateMode = isPrivateMode,
                    showTabBar = true,
                    showAddressBar = true,
                    showContextualToolbar = true
                )
            }
        }
    }
}
```

### BrowserFragment (View-based)

For traditional View-based fragments, use UnifiedToolbar directly:

```kotlin
val toolbar = UnifiedToolbar.create(
    context = requireContext(),
    parent = binding.container,
    lifecycleOwner = viewLifecycleOwner,
    interactor = toolbarInteractor,
    tabGroupManager = tabGroupManager,
    tabGroupBarListener = tabBarListener
)

toolbar.setEngineView(binding.engineView)
toolbar.setContextualToolbarListener(contextualToolbarListener)
```

## Swipe Gestures

### Tab Navigation

The unified toolbar supports swipe gestures for navigating between tabs:

**Gesture Detection:**
- Horizontal swipes on the toolbar area
- Left swipe → Next tab (LTR) / Previous tab (RTL)
- Right swipe → Previous tab (LTR) / Next tab (RTL)
- Minimum distance: 25% of screen width
- Velocity-based fling detection

**Implementation:**
```kotlin
private fun handleTabSwipe(isNext: Boolean, isPrivateMode: Boolean) {
    val currentTab = store.state.selectedTab ?: return
    val tabs = store.state.getNormalOrPrivateTabs(isPrivateMode)
    val currentIndex = tabs.indexOfFirst { it.id == currentTab.id }

    if (currentIndex == -1) return

    val newIndex = if (isNext) currentIndex + 1 else currentIndex - 1

    if (newIndex in tabs.indices) {
        val newTab = tabs[newIndex]
        tabsUseCases.selectTab(newTab.id)

        // Navigate to browser if needed
        if (newTab.content.url != "about:homepage") {
            navigateToBrowser()
        }
    }
}
```

## Architecture

### Component Hierarchy

```
UnifiedToolbar (LinearLayout)
├── TabGroupBar (when showTabGroupBar = true)
│   └── RecyclerView (horizontal)
│       └── TabGroupAdapter
├── AddressBar (BrowserToolbar)
│   ├── URL Display
│   ├── Security Indicator
│   ├── Progress Bar
│   └── Menu Actions
└── ContextualToolbar (when showContextualToolbar = true)
    ├── Back Button
    ├── Forward Button (context-aware)
    ├── Share Button (context-aware)
    ├── Search Button (homepage only)
    ├── New Tab Button (context-aware)
    ├── Refresh Button (optional)
    ├── Tab Count Badge
    └── Menu Button
```

### Positioning

**Top Position:**
- Order: Tab Bar → Address Bar → Contextual Toolbar
- Scroll direction: Hides upward
- Dynamic toolbar height for GeckoView

**Bottom Position:**
- Order: Contextual Toolbar → Address Bar → Tab Bar
- Scroll direction: Hides downward
- No dynamic toolbar height (prevents black bar at top)

## Benefits

1. **Centralized Management**: All toolbar components in one place
2. **Shared Theming**: Consistent colors, elevations, and styling
3. **Easier Maintenance**: Single source of truth for toolbar behavior
4. **Better UX**: Smooth animations, proper scroll behavior, intuitive swipe gestures
5. **Material 3**: Modern design language with proper elevation
6. **Flexibility**: Easy to show/hide components based on context
7. **Private Mode**: Automatic theming for private browsing
8. **Edge-to-Edge**: Proper window insets handling

## Migration Guide

### From Old Toolbar Components

**Before:**
```kotlin
// Separate components scattered across different files
val tabGroupBar = TabGroupBar(context)
val browserToolbar = BrowserToolbar(context)
val contextualToolbar = ContextualBottomToolbar(context)

// Manual theming for each component
tabGroupBar.setBackgroundColor(color1)
browserToolbar.setBackgroundColor(color2)
contextualToolbar.setBackgroundColor(color3)
```

**After:**
```kotlin
// Single unified component
val toolbar = UnifiedToolbar.create(
    context = context,
    parent = container,
    lifecycleOwner = lifecycleOwner,
    interactor = interactor,
    tabGroupManager = tabGroupManager,
    tabGroupBarListener = listener
)

// Automatic shared theming
// All components use the same background color from theme
```

## Future Enhancements

Potential improvements for the unified toolbar:

1. **Custom Tab Groups UI**: Enhanced visual design for tab groups
2. **Voice Search Integration**: Voice input button in address bar
3. **Reading Mode Indicator**: Visual indicator for reader-friendly pages
4. **Download Progress**: Inline download progress in toolbar
5. **Extension Actions**: Quick access to extension actions
6. **Customizable Button Order**: User-configurable button layout
7. **Haptic Feedback**: Enhanced tactile feedback for interactions
8. **Gesture Customization**: User-configurable swipe gestures

## Troubleshooting

### Black Bar at Top with Bottom Toolbar

**Issue**: Black bar appears at top when using bottom toolbar.

**Solution**: The unified toolbar automatically sets dynamic toolbar height to 0 for bottom toolbar. Ensure you're using `UnifiedToolbar` instead of custom implementations.

```kotlin
if (toolbarPosition == ToolbarPosition.BOTTOM) {
    engineView.setDynamicToolbarMaxHeight(0)
    engineView.setVerticalClipping(0)
}
```

### Components Not Showing

**Issue**: Tab bar or contextual toolbar not visible.

**Solution**: Check visibility settings in UserPreferences:

```kotlin
val prefs = UserPreferences(context)
toolbar.updateComponentVisibility(
    showTabBar = prefs.showTabGroupBar,
    showContextual = prefs.showContextualToolbar
)
```

### Theming Issues

**Issue**: Toolbar colors don't match the app theme.

**Solution**: Ensure all required color resources are defined in `colors.xml` and the theme is properly applied:

```kotlin
val theme = UnifiedToolbarTheme(context)
toolbar.applyPrivateMode(isPrivateMode)
```

## Contributing

When modifying the unified toolbar:

1. Update `UnifiedToolbar.kt` for structural changes
2. Update `UnifiedToolbarTheme.kt` for theming changes
3. Update `UnifiedToolbarCompose.kt` for Compose integration
4. Test both top and bottom positions
5. Test private and normal modes
6. Verify scroll behavior
7. Test swipe gestures
8. Update this documentation

## License

Part of the Nira Browser project.
