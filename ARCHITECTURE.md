# Nira Browser Architecture

This document describes the architectural design, patterns, and key components of Nira Browser.

## Table of Contents

- [Overview](#overview)
- [Architecture Patterns](#architecture-patterns)
- [Core Systems](#core-systems)
- [Data Layer](#data-layer)
- [UI Layer](#ui-layer)
- [Key Components](#key-components)
- [State Management](#state-management)
- [Threading Model](#threading-model)

## Overview

Nira Browser is built on Mozilla's GeckoView engine and Android Components, following modern Android development best practices with MVVM architecture, Jetpack Compose, and reactive programming patterns.

### Technology Stack

- **Engine**: GeckoView (Gecko rendering engine)
- **UI**: Jetpack Compose + Material 3
- **Language**: Kotlin 100%
- **Architecture**: MVVM (Model-View-ViewModel)
- **Persistence**: Room Database + DataStore
- **Reactive**: Kotlin Coroutines + Flow
- **DI**: Manual dependency injection (no framework)

### Architecture Principles

1. **Separation of Concerns**: Clear boundaries between UI, business logic, and data
2. **Single Source of Truth**: Each piece of data has one authoritative source
3. **Unidirectional Data Flow**: Data flows down, events flow up
4. **Reactive**: UI observes state changes via Flow/StateFlow
5. **Testability**: Components designed for unit testing

## Architecture Patterns

### MVVM (Model-View-ViewModel)

```
┌─────────────┐
│    View     │ (Composables)
│  (Compose)  │
└──────┬──────┘
       │ observes StateFlow
       │ calls functions
┌──────▼──────┐
│  ViewModel  │ (TabViewModel, etc.)
│   (State)   │
└──────┬──────┘
       │ calls suspend functions
       │ receives data
┌──────▼──────┐
│   Manager   │ (UnifiedTabGroupManager, etc.)
│ (Repository)│
└──────┬──────┘
       │ reads/writes
┌──────▼──────┐
│  Database   │ (Room, DataStore)
│   (Model)   │
└─────────────┘
```

### Repository Pattern

Managers act as repositories, providing a clean API for data operations:

```kotlin
// Manager maintains cache + database
class UnifiedTabGroupManager {
    private val cache: Map<String, TabGroupData>
    private val dao: TabGroupDao
    
    // Public API
    suspend fun createGroup(...): TabGroupData
    suspend fun getAllGroups(): List<TabGroupData>
    
    // Emits events for UI updates
    val groupEvents: SharedFlow<GroupEvent>
}
```

### Observer Pattern

UI components observe state changes via Kotlin Flow:

```kotlin
// ViewModel exposes StateFlow
class TabViewModel {
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()
}

// Composable observes
@Composable
fun TabList(viewModel: TabViewModel) {
    val tabs by viewModel.tabs.collectAsState()
    // UI updates automatically when tabs change
}
```

## Core Systems

### 1. Tab Management System

**Purpose**: Manage browser tabs, their order, and grouping.

**Components**:
- `TabViewModel` - State management for tabs
- `TabOrderManager` - Persistence of tab order
- `UnifiedTabGroupManager` - Tab group CRUD operations
- `TabBarCompose` - Tab bar UI

**Data Flow**:
```
User Action → ViewModel → Manager → Database
                ↓          ↓
              State     Events
                ↓          ↓
               UI      Updates
```

**Key Features**:
- Tab ordering with drag-and-drop
- Tab grouping with colors
- Collapse/expand state persistence
- Profile isolation

### 2. Multi-Profile System

**Purpose**: Isolate browsing data across different user profiles.

**Components**:
- `ProfileManager` - Profile lifecycle management
- `Profile` - Database entity
- Cookie jar management
- Profile-specific stores

**Architecture**:
```
┌─────────────────────────────────────┐
│         ProfileManager              │
├─────────────────────────────────────┤
│  • Creates/deletes profiles         │
│  • Switches active profile          │
│  • Manages cookie jars              │
│  • Isolates browsing data           │
└─────────────────────────────────────┘
            ↓
┌───────────┬───────────┬───────────┐
│  Profile  │  Profile  │  Profile  │
│  Default  │  Private  │  Work     │
├───────────┼───────────┼───────────┤
│ Tabs      │ Tabs      │ Tabs      │
│ Groups    │ Groups    │ Groups    │
│ PWAs      │ PWAs      │ PWAs      │
│ Cookies   │ Cookies   │ Cookies   │
│ History   │ History   │ History   │
└───────────┴───────────┴───────────┘
```

**ContextId System**:
- `null` or `"profile_default"` - Default profile
- `"private"` - Private browsing
- `"profile_{uuid}"` - Custom profiles

All data entities (tabs, groups, PWAs) are filtered by contextId.

### 3. Progressive Web App (PWA) System

**Purpose**: Install and manage web apps as standalone applications.

**Components**:
- `WebAppManager` - PWA lifecycle
- `InstalledWebApp` - Database entity
- Manifest parsing
- Profile binding

**Installation Flow**:
```
1. User requests PWA install
2. Fetch manifest.json
3. Parse app name, icons, theme
4. Create InstalledWebApp entity
5. Bind to current profile
6. Create launcher icon
7. Emit installation event
```

### 4. Theme System

**Purpose**: Material 3 theming with dynamic colors and dark mode.

**Components**:
- `ColorConstants` - Color palette definitions
- `Theme.kt` - Compose theme setup
- Dynamic color extraction
- AMOLED mode support

**Theme Types**:
- Light Mode
- Dark Mode
- AMOLED Dark Mode
- Dynamic Color (Android 12+)

## Data Layer

### Room Database

**Databases**:
1. `TabGroupDatabase` - Tab groups and members
2. `ProfileDatabase` - User profiles
3. `WebAppDatabase` - Installed PWAs
4. `HistoryDatabase` - Browsing history

**Schema Example** (TabGroup):
```kotlin
@Entity(tableName = "tab_groups")
data class TabGroup(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,          // Stored as string name
    val createdAt: Long,
    val isActive: Boolean,
    val contextId: String?      // Profile isolation
)
```

### DataStore

**Used For**: Key-value preferences and structured data

**Storage**:
- Tab order (JSON serialized)
- User preferences
- UI state

**Example**:
```kotlin
data class UnifiedTabOrder(
    val profileId: String,
    val primaryOrder: List<OrderItem>,
    val lastModified: Long
)

// Stored in DataStore as JSON
```

### In-Memory Cache

Managers maintain caches for performance:

```kotlin
class UnifiedTabGroupManager {
    private val groupsCache = mutableMapOf<String, TabGroupData>()
    private val tabToGroupMap = mutableMapOf<String, String>()
    
    // Cache synced with database
}
```

## UI Layer

### Jetpack Compose

All modern UI built with Compose:
- Tab bar
- Toolbar
- Settings screens
- Profile switcher
- Dialogs and sheets

### Composable Architecture

```kotlin
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // UI based on state
    when (state) {
        is Loading -> LoadingIndicator()
        is Success -> Content(state.data)
        is Error -> ErrorMessage(state.error)
    }
}
```

### Material 3 Design

- Material You dynamic colors
- Adaptive layouts
- Motion and animation
- Edge-to-edge display

## Key Components

### UnifiedTabGroupManager

**Responsibility**: Single source of truth for tab groups.

**Operations**:
- Create, read, update, delete groups
- Add/remove tabs from groups
- Change group colors and names
- Emit events for UI updates

**Thread Safety**:
- All database operations on IO dispatcher
- Cache access synchronized
- StateFlow for reactive updates

### TabOrderManager

**Responsibility**: Persist and manage tab order.

**Operations**:
- Load/save order from DataStore
- Rebuild order when groups change
- Handle drag-and-drop reordering
- Track expand/collapse state

**Serialization**: JSON via kotlinx.serialization

### TabViewModel

**Responsibility**: Manage tab UI state.

**State**:
- Current tabs
- Active groups
- Expanded group IDs
- Selected tab ID

**Operations**:
- Load tabs for profile
- Create/delete groups
- Expand/collapse groups
- Handle group events

### ProfileManager

**Responsibility**: Manage user profiles.

**Operations**:
- Create/delete profiles
- Switch active profile
- List all profiles
- Cookie jar management

**Profile Types**:
- Default profile (always exists)
- Private browsing (temporary)
- Custom profiles (user-created)

## State Management

### StateFlow Pattern

```kotlin
class FeatureViewModel {
    // Private mutable state
    private val _state = MutableStateFlow<State>(Initial)
    
    // Public immutable state
    val state: StateFlow<State> = _state.asStateFlow()
    
    // Update state
    fun updateState(newState: State) {
        _state.value = newState
    }
}
```

### Event Pattern

```kotlin
class Manager {
    // Events emitted for UI updates
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()
    
    suspend fun performAction() {
        // Do work
        _events.emit(ActionCompleted)
    }
}
```

### State Hoisting

State managed at appropriate levels:
- **ViewModel**: Screen-level state
- **Manager**: Domain-level state
- **Composable**: Local UI state

## Threading Model

### Coroutines and Dispatchers

```kotlin
// UI operations
viewModelScope.launch {
    // Runs on Main dispatcher
    updateUI()
}

// Database operations
withContext(Dispatchers.IO) {
    // Runs on IO dispatcher
    dao.insert(entity)
}

// CPU-intensive work
withContext(Dispatchers.Default) {
    // Runs on computation dispatcher
    processLargeDataset()
}
```

### Lifecycle Awareness

- ViewModels survive configuration changes
- Coroutines cancelled when ViewModel cleared
- Room DAOs accessed via suspend functions

## GeckoView Integration

### Engine Session Management

```
BrowserFragment
    ↓
EngineView (GeckoView)
    ↓
EngineSession (Gecko)
    ↓
Gecko Engine
```

### Mozilla Components

Key components used:
- `browser-engine-gecko` - GeckoView wrapper
- `browser-state` - Browser state management
- `browser-toolbar` - Toolbar components
- `concept-engine` - Engine abstractions
- `feature-tabs` - Tab features
- `feature-downloads` - Download handling

## Performance Considerations

### Caching Strategy

1. **In-Memory Cache**: Hot data (groups, profiles)
2. **Database**: Persistent storage
3. **DataStore**: Configuration and order

### Lazy Loading

- Tab list loaded on-demand
- History paginated
- Images loaded asynchronously

### State Optimization

- StateFlow prevents redundant updates
- Remember in Compose reduces recomposition
- Keys prevent unnecessary recomposition

## Security and Privacy

### Data Isolation

- Profiles have separate cookie jars
- Private mode uses temporary storage
- Context IDs isolate all data

### Permissions

- Runtime permission requests
- Scope permissions to features
- User control over data access

## Testing Strategy

### Unit Tests

- ViewModels (state management)
- Managers (business logic)
- Utilities (pure functions)

### Integration Tests

- Database DAOs
- Manager + Database interactions
- Flow transformations

### UI Tests

- Compose UI tests
- User interaction flows
- Navigation tests

## Migration and Compatibility

### Database Migrations

- Room migrations for schema changes
- Backward compatibility maintained
- Data migration scripts

### Legacy Code

- `browser/tabs/modern/` - Old tab system
- Gradual migration to Compose
- Deprecation warnings

## Related Projects

### Mozilla References

For implementation guidance, refer to:

- **Mozilla Reference Browser**: https://github.com/mozilla-mobile/reference-browser
  - Example GeckoView browser implementation
  - Simple patterns for common features
  - Good starting point for understanding Mozilla components

- **Firefox Repository**: https://github.com/mozilla-firefox/firefox/
  - **Primary reference for all Mozilla Android code**
  - Complete Firefox source including:
    - Fenix (Firefox for Android) implementation
    - GeckoView engine source code
    - Android Components library source
    - Advanced features and integrations
  - Production-quality browser implementation

## Future Architecture

### Planned Improvements

- [ ] Migrate remaining Views to Compose
- [ ] Implement proper DI framework (Hilt/Koin)
- [ ] Add comprehensive unit tests
- [ ] Refactor legacy code
- [ ] Sync system architecture
- [ ] Improve error handling
- [ ] Add analytics framework

---

**Last Updated**: March 2026

This architecture document should be updated as the project evolves. For questions or suggestions, open an issue on GitHub.
