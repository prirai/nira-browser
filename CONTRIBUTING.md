# Contributing to Nira Browser

Thank you for your interest in contributing to Nira Browser! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Issue Guidelines](#issue-guidelines)
- [Architecture Reference](#architecture-reference)

## Code of Conduct

### Our Standards

- Be respectful and inclusive
- Focus on constructive feedback
- Accept criticism gracefully
- Prioritize user privacy and security
- Maintain code quality

### Unacceptable Behavior

- Harassment or discrimination
- Trolling or insulting comments
- Publishing private information
- Spamming issues or PRs

## Getting Started

### Prerequisites

- **Android Studio**: Arctic Fox or later
- **JDK**: 11 or later
- **Android SDK**: API 24+ (Android 7.0+)
- **Git**: For version control
- Basic knowledge of Kotlin and Android development

### Understanding the Project

Before contributing, familiarize yourself with:

1. **[README.md](README.md)** - Project overview and features
2. **[ARCHITECTURE.md](ARCHITECTURE.md)** - Technical architecture
3. **[.github/agents.md](.github/agents.md)** - LLM/AI assistant guide
4. **Mozilla References**:
   - [Reference Browser](https://github.com/mozilla-mobile/reference-browser) - Simple implementation examples
   - [Firefox Repository](https://github.com/mozilla-firefox/firefox/) - Complete Firefox source including Android, GeckoView, and Android Components

## Development Setup

### 1. Fork and Clone

```bash
# Fork the repository on GitHub, then:
git clone https://github.com/YOUR_USERNAME/nira-browser.git
cd nira-browser
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to the cloned directory
4. Wait for Gradle sync to complete

### 3. Build the Project

```bash
# Command line
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

### 4. Run on Device/Emulator

```bash
# Install debug build
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Or use Android Studio Run button
```

### 5. View Logs

```bash
# Filter relevant logs
adb logcat | grep -E "Nira|TabOrder|TabGroup|Profile|WebApp"

# Or use Android Studio Logcat
```

## How to Contribute

### Types of Contributions

#### 1. Bug Reports

Found a bug? Help us fix it:

**Before Reporting**:
- Search existing issues to avoid duplicates
- Test on the latest version
- Verify it's not a GeckoView or Android system issue

**Required Information**:
- Device model and Android version
- Nira Browser version
- Steps to reproduce
- Expected vs actual behavior
- Logs if applicable
- Screenshots if relevant

**Template**:
```markdown
**Device**: Samsung Galaxy S21, Android 13
**Version**: Nira Browser v1.0.0-alpha

**Steps to Reproduce**:
1. Open browser
2. Create tab group
3. Change color to red
4. Restart app

**Expected**: Group color should remain red
**Actual**: Group color reverts to blue

**Logs**:
```
[Include relevant logcat output]
```

#### 2. Feature Requests

Have an idea? We'd love to hear it:

**Before Requesting**:
- Check if it already exists in [Issues](https://github.com/prirai/nira-browser/issues) or [Discussions](https://github.com/prirai/nira-browser/discussions)
- Consider if it aligns with project goals (privacy, performance, usability)

**Use GitHub Discussions** for:
- Feature ideas
- Usability feedback
- Architecture discussions

**Template**:
```markdown
**Feature**: Tab group synchronization

**Use Case**: I want my tab groups to sync across my phone and tablet

**Proposal**: 
- Add optional sync service
- Encrypt data before syncing
- Allow choosing what to sync

**Alternatives**: 
- Manual export/import
- Cloud backup

**Privacy Considerations**: 
- Must be opt-in
- End-to-end encryption
- Self-hosted option
```

#### 3. Code Contributions

Ready to code? Here's how:

**Good First Issues**:
- Look for `good first issue` label
- UI improvements
- Documentation updates
- Small bug fixes

**Areas Needing Help**:
- Jetpack Compose migration
- Unit test coverage
- Performance optimization
- Accessibility improvements
- Translation updates

## Coding Standards

### Kotlin Style

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// Class names: PascalCase
class TabGroupManager

// Function names: camelCase
fun createGroup()

// Constants: UPPER_SNAKE_CASE
const val MAX_TABS = 100

// Private properties: _camelCase
private val _state = MutableStateFlow()
```

### Architecture Patterns

**MVVM Pattern**:
```kotlin
// ViewModel
class FeatureViewModel : ViewModel() {
    private val _state = MutableStateFlow<State>(Initial)
    val state: StateFlow<State> = _state.asStateFlow()
    
    fun performAction() {
        viewModelScope.launch {
            // Business logic
        }
    }
}

// Composable
@Composable
fun FeatureScreen(viewModel: FeatureViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    // UI based on state
}
```

**Manager/Repository Pattern**:
```kotlin
class DataManager private constructor(context: Context) {
    private val cache = mutableMapOf<String, Data>()
    private val dao = database.dataDao()
    
    suspend fun getData(): List<Data> = withContext(Dispatchers.IO) {
        dao.getAll()
    }
    
    companion object {
        @Volatile
        private var instance: DataManager? = null
        
        fun getInstance(context: Context): DataManager {
            return instance ?: synchronized(this) {
                instance ?: DataManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
```

### Code Quality

**Required**:
- ✅ Null safety (avoid `!!`)
- ✅ Proper error handling
- ✅ Meaningful variable names
- ✅ Comments for complex logic
- ✅ No hardcoded strings (use resources)
- ✅ Thread-safe operations
- ✅ Memory leak prevention

**Avoid**:
- ❌ Magic numbers
- ❌ Nested callbacks
- ❌ God classes
- ❌ Duplicate code
- ❌ Unnecessary complexity

### Compose Guidelines

```kotlin
// State hoisting
@Composable
fun FeatureScreen() {
    var state by remember { mutableStateOf(InitialState) }
    
    FeatureContent(
        state = state,
        onAction = { state = it }
    )
}

@Composable
fun FeatureContent(
    state: State,
    onAction: (State) -> Unit
) {
    // Stateless composable
}

// Preview
@Preview
@Composable
fun FeatureContentPreview() {
    NiraTheme {
        FeatureContent(
            state = PreviewState,
            onAction = {}
        )
    }
}
```

### Threading

```kotlin
// Database operations
suspend fun saveData(data: Data) = withContext(Dispatchers.IO) {
    dao.insert(data)
}

// CPU-intensive work
suspend fun processData(data: Data) = withContext(Dispatchers.Default) {
    data.transform()
}

// UI updates
viewModelScope.launch {
    val result = withContext(Dispatchers.IO) {
        repository.getData()
    }
    _state.value = Success(result)
}
```

### Resource Management

```kotlin
// Strings
<string name="tab_group_create">Create Group</string>

// Usage
Text(stringResource(R.string.tab_group_create))

// Colors (prefer theme colors)
MaterialTheme.colorScheme.primary

// Dimensions
dimensionResource(R.dimen.spacing_medium)
```

## Testing Guidelines

### Unit Tests

Test business logic and ViewModels:

```kotlin
class TabViewModelTest {
    private lateinit var viewModel: TabViewModel
    private lateinit var mockManager: UnifiedTabGroupManager
    
    @Before
    fun setup() {
        mockManager = mock()
        viewModel = TabViewModel(mockManager)
    }
    
    @Test
    fun `createGroup should update state`() = runTest {
        // Given
        val groupName = "Test Group"
        
        // When
        viewModel.createGroup(groupName)
        
        // Then
        val state = viewModel.groups.value
        assertTrue(state.any { it.name == groupName })
    }
}
```

### Integration Tests

Test database operations:

```kotlin
@RunWith(AndroidJUnit4::class)
class TabGroupDaoTest {
    private lateinit var database: TabGroupDatabase
    private lateinit var dao: TabGroupDao
    
    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            TabGroupDatabase::class.java
        ).build()
        dao = database.tabGroupDao()
    }
    
    @Test
    fun insertAndRetrieveGroup() = runBlocking {
        // Given
        val group = TabGroup(id = "test", name = "Test")
        
        // When
        dao.insertGroup(group)
        val retrieved = dao.getGroupById("test")
        
        // Then
        assertEquals(group, retrieved)
    }
    
    @After
    fun closeDb() {
        database.close()
    }
}
```

### UI Tests

Test Compose UI:

```kotlin
class TabBarComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun tabBarDisplaysTabs() {
        // Given
        val tabs = listOf(
            createTab("Tab 1"),
            createTab("Tab 2")
        )
        
        // When
        composeTestRule.setContent {
            TabBarCompose(tabs = tabs, ...)
        }
        
        // Then
        composeTestRule.onNodeWithText("Tab 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tab 2").assertIsDisplayed()
    }
}
```

## Pull Request Process

### Before Submitting

1. **Create a branch**:
   ```bash
   git checkout -b feature/my-feature
   # or
   git checkout -b fix/bug-description
   ```

2. **Follow coding standards** (see above)

3. **Test thoroughly**:
   - Manual testing
   - Add unit tests if applicable
   - Test on multiple Android versions if possible

4. **Update documentation**:
   - Update README if adding features
   - Update ARCHITECTURE.md if changing structure
   - Add code comments for complex logic

5. **Commit with clear messages**:
   ```bash
   git commit -m "feat: add tab group color persistence"
   git commit -m "fix: resolve crash when deleting profile"
   git commit -m "docs: update setup instructions"
   ```

### Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <description>

[optional body]

[optional footer]
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting (no code change)
- `refactor`: Code restructuring
- `test`: Adding tests
- `chore`: Maintenance

**Examples**:
```bash
feat: add PWA installation support

fix: resolve tab group color not persisting after restart
- Updated color conversion in ColorConstants
- Fixed cache synchronization in UnifiedTabGroupManager
- Added unit tests for color persistence

Fixes #123

docs: improve contributing guidelines
```

### Submitting PR

1. **Push to your fork**:
   ```bash
   git push origin feature/my-feature
   ```

2. **Create Pull Request** on GitHub:
   - Use descriptive title
   - Reference related issues
   - Describe what changed and why
   - Include screenshots for UI changes
   - List testing performed

**PR Template**:
```markdown
## Description
Brief description of changes

## Related Issues
Fixes #123
Related to #456

## Changes Made
- Added feature X
- Fixed bug Y
- Refactored Z

## Testing Performed
- [x] Manual testing on Android 12
- [x] Unit tests added/passing
- [ ] Integration tests (if applicable)

## Screenshots
[Include if UI changes]

## Checklist
- [x] Code follows project style
- [x] Self-reviewed code
- [x] Commented complex code
- [x] Updated documentation
- [x] No new warnings
- [x] Added tests
```

### Review Process

1. **Automated checks** must pass (if configured)
2. **Maintainer review** (may request changes)
3. **Address feedback** promptly
4. **Approval** from maintainer
5. **Merge** by maintainer

### After Merge

- Delete your branch
- Update your fork:
  ```bash
  git checkout main
  git pull upstream main
  git push origin main
  ```

## Issue Guidelines

### Creating Issues

**Bug Reports**: Use template, include device info and logs

**Feature Requests**: Describe use case and benefits

**Questions**: Use GitHub Discussions instead

### Issue Labels

- `bug` - Something isn't working
- `enhancement` - New feature or request
- `good first issue` - Good for newcomers
- `help wanted` - Extra attention needed
- `documentation` - Documentation improvements
- `question` - Further information requested
- `wontfix` - This will not be worked on
- `duplicate` - Already exists

### Working on Issues

1. Comment on issue to claim it
2. Wait for maintainer assignment
3. Ask questions if unclear
4. Update progress regularly
5. Link PR when ready

## Architecture Reference

### Key Files to Know

**Core Systems**:
- `UnifiedTabGroupManager.kt` - Tab group management
- `TabViewModel.kt` - Tab UI state
- `TabOrderManager.kt` - Tab order persistence
- `ProfileManager.kt` - Profile management
- `WebAppManager.kt` - PWA management

**Data Models**:
- `TabGroup.kt` - Group database entity
- `Profile.kt` - Profile database entity
- `UnifiedTabOrder.kt` - Order data model

**UI Components**:
- `TabBarCompose.kt` - Tab bar UI
- `BrowserFragment.kt` - Main browser screen
- `ComposeTabBarWithProfileSwitcher.kt` - Profile UI

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed information.

## Mozilla Component References

When implementing features, reference:

- **[Reference Browser](https://github.com/mozilla-mobile/reference-browser)** - Simple implementation examples
- **[Firefox Repository](https://github.com/mozilla-firefox/firefox/)** - Complete Firefox source including:
  - Fenix (Firefox for Android) code
  - GeckoView engine source
  - Android Components library
  - Advanced feature implementations

## Questions?

- **General questions**: [GitHub Discussions](https://github.com/prirai/nira-browser/discussions)
- **Bug reports**: [GitHub Issues](https://github.com/prirai/nira-browser/issues)
- **Chat**: [Telegram](https://t.me/nirafoss)

## Recognition

Contributors will be:
- Listed in release notes
- Credited in README (for significant contributions)
- Appreciated in commit messages

Thank you for contributing to Nira Browser! 🎉

---

**Last Updated**: March 2026
