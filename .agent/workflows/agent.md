---
description: Nira Browser Development Orchestrator
---

# Nira Browser Development Orchestrator

## INSTRUCTIONS FOR LLM AGENTS

You are working on the **Nira Browser** - an Android browser based on Mozilla's GeckoView engine. This orchestrator determines which prompt template you should follow based on the type of task presented.

**CRITICAL**: Read this entire file first, then immediately proceed to the appropriate template file and follow it exactly.

## PROJECT OVERVIEW

### What is Nira Browser?
- **Type**: Android mobile browser application
- **Engine**: Mozilla GeckoView (Gecko rendering engine)
- **Language**: Kotlin
- **Architecture**: Mozilla Android Components + Custom UI
- **Key Features**: Tab groups, ad blocking, extensions support, custom tabs, modern Material 3 UI

### Upstream Dependencies
- **Primary Upstream**: `github.com/mozilla/firefox` (NOT mozilla-firefox/firefox)
  - Mobile Android: `mobile/android/`
  - Android Components: `mobile/android/android-components/`
  - GeckoView: `mobile/android/geckoview/`
  - Fenix (Firefox for Android): `mobile/android/fenix/`
- **Documentation**: Mozilla's Android Components docs
- **Release Channel**: GeckoView Beta/Release

### Critical Understanding
- This is NOT a fork of Firefox/Fenix
- This uses Mozilla's components as libraries
- Custom UI and features built on top of Mozilla's architecture
- Must stay compatible with Mozilla's component APIs

## TASK CLASSIFICATION - all files in .prompts/ directory

### 🔍 **INVESTIGATION TASKS**
**When to use `01_INVESTIGATION.md`**:
- Bug reports or issues that need analysis
- "Why is X not working?"
- "Figure out what's wrong with Y"
- User reports unexpected behavior
- Features not working as described
- Performance problems that need diagnosis
- Understanding existing code behavior
- Exploring upstream Mozilla implementations

**Examples**:
- "Custom tabs not opening from external apps"
- "Find in page not appearing above keyboard"
- "Tab groups not persisting after app restart"
- "Extensions not loading properly"
- "Memory leak in tab management"
- "How does Mozilla implement feature X?"

**Action**: Read and follow `01_INVESTIGATION.md` completely before making any code changes.

### 📋 **PLANNING TASKS**
**When to use `02_PLANNING.md`**:
- You have completed investigation and need to plan implementation
- Task explicitly asks for implementation planning
- Architecture design requests
- "How should we implement X?"
- Risk assessment requests
- Breaking down complex features into steps
- Evaluating upstream approaches

**Examples**:
- "Plan implementation of picture-in-picture mode"
- "Design architecture for improved tab persistence"
- "Break down PWA support implementation"
- "Plan migration to newer GeckoView version"
- "Design custom tabs integration strategy"

**Prerequisites**: Investigation must be completed first (either in current session or provided separately)
**Action**: Read and follow `02_PLANNING.md` to create detailed implementation plan.

### ⚡ **IMPLEMENTATION TASKS**
**When to use `03_IMPLEMENTATION.md`**:
- You have an approved implementation plan
- Task explicitly asks to implement specific changes
- "Fix the bug in file X"
- "Implement feature Y"
- Code changes are requested with clear requirements

**Examples**:
- "Implement the planned find-in-page keyboard adjustment"
- "Add Material 3 design to browser menu"
- "Fix tab group persistence logic"
- "Integrate custom FindInPageComponent"

**Prerequisites**: Implementation plan must exist (either in current session or provided)
**Action**: Read and follow `03_IMPLEMENTATION.md` for step-by-step execution.

### 📖 **DOCUMENTATION TASKS**
**When to use `04_DOCUMENTATION.md`**:
- Implementation is complete and needs documentation
- Task asks to update documentation
- "Document the changes made"
- "Update the changelog"
- README or docs updates requested
- Code comments need improvement

**Examples**:
- "Document the custom tabs implementation"
- "Update README with new features"
- "Add KDoc comments to FindInPageComponent"
- "Create architecture documentation for tab groups"

**Prerequisites**: Implementation must be completed and tested
**Action**: Read and follow `04_DOCUMENTATION.md` to update documentation.

### 🚨 **EMERGENCY SITUATIONS**
**When to use `99_EMERGENCY_PROCEDURES.md`**:
- Code won't compile
- Tests are failing unexpectedly
- App crashes on startup
- GeckoView runtime errors
- Performance severely degraded
- Data corruption suspected
- Need to rollback changes
- Any critical system failure

**Examples**:
- "The app won't compile after GeckoView update"
- "All tabs crash when opening"
- "Need to revert the last implementation"
- "Browser freezes after custom tabs changes"
- "Database migration failed"

**Action**: STOP current work immediately and read `99_EMERGENCY_PROCEDURES.md`.

## ROUTING LOGIC

### Step 1: Classify the Request
Read the user's request and determine which category it falls into:

1. **Investigation**: Problem analysis, bug investigation, understanding issues, exploring upstream
2. **Planning**: Architecture design, implementation planning, risk assessment, upstream evaluation
3. **Implementation**: Actual code changes, feature development, bug fixes
4. **Documentation**: Updating docs, changelogs, specifications, code comments
5. **Emergency**: System failures, critical issues, rollback needs

### Step 2: Check Prerequisites
- **Planning** requires completed investigation
- **Implementation** requires approved plan
- **Documentation** requires completed implementation
- **Emergency** overrides all other work

### Step 3: Route to Template
Once classified, immediately proceed to the appropriate template file:
- Investigation → `01_INVESTIGATION.md`
- Planning → `02_PLANNING.md`
- Implementation → `03_IMPLEMENTATION.md`
- Documentation → `04_DOCUMENTATION.md`
- Emergency → `99_EMERGENCY_PROCEDURES.md`

### Step 4: Follow Template Exactly
- Read the entire template file
- Follow every step in the specified order
- Complete all requirements before proceeding
- Do not skip steps or take shortcuts
- Do not assume success without explicit user confirmation

## SPECIAL CASES

### Multi-Phase Requests
If request spans multiple phases (e.g., "investigate and fix custom tabs bug"):
1. Start with investigation phase (`01_INVESTIGATION.md`)
2. Complete investigation fully - get user confirmation
3. Move to planning phase (`02_PLANNING.md`)
4. Complete planning fully - get user approval
5. Move to implementation phase (`03_IMPLEMENTATION.md`)
6. Complete implementation fully - get user confirmation of success
7. Move to documentation phase (`04_DOCUMENTATION.md`) if needed

**CRITICAL**: Complete each phase 100% and get explicit user confirmation before moving to next.

### Unclear Requests
If the request type is unclear:
1. Default to Investigation phase (`01_INVESTIGATION.md`)
2. The investigation will clarify what type of work is actually needed
3. Route appropriately after investigation is complete
4. Ask user for clarification if still uncertain

### Context from Previous Sessions
If user provides context from previous work:
1. Determine which phase that work represents
2. Route to the next appropriate phase
3. If uncertain, start with investigation to verify current state
4. Never assume previous work was successful without verification

## CODING STANDARDS

**CRITICAL**: Before starting ANY work, read `CODING_STANDARDS.md` to understand:
- Clean code practices and separation of concerns
- Material 3 theming (ALWAYS use defined colors from XML)
- Code reusability patterns
- Kotlin coding standards
- Fragment lifecycle safety
- Error handling patterns
- Testing requirements

**Key Standards**:
- Use Material 3 colors from `res/values/colors.xml` (NEVER hardcode colors)
- Follow separation of concerns (Business logic in Managers, UI logic in Fragments)
- Reuse existing code and components
- Handle lifecycles properly (fragments, views, flows)
- Write defensive code with null checks and error handling
- Test in both light and dark modes

## ABSOLUTE RULES

### 🚫 **NEVER DO THESE**:
- Skip phases or jump ahead without completing current phase
- Make assumptions about which template to use
- Combine multiple templates in one session without clear phase boundaries
- Deviate from template structure
- Start implementation without investigation and planning
- **Assume a problem is fixed without explicit user confirmation**
- **Run `adb` commands that install, uninstall, or modify device state without asking permission first**
- Make breaking changes to public APIs without documenting
- Commit changes that don't compile
- Skip testing steps defined in templates
- Ignore upstream Mozilla patterns and conventions
- **Hardcode colors instead of using Material 3 color definitions**
- **Put business logic in UI components (Fragments/Activities)**
- **Duplicate code instead of extracting reusable functions**

### **ALWAYS DO THESE**:
- **Read `CODING_STANDARDS.md` before starting any work**
- Read this orchestrator first
- Choose exactly one template to follow
- Complete the chosen template 100%
- Follow the template's specific rules and procedures
- Document your work according to template requirements
- **Get explicit user confirmation before proceeding to next phase**
- **Ask permission before running device-modifying `adb` commands**
- Use `gradle` commands for building and verification
- Use `adb logcat` and `dumpsys` for debugging and diagnostics only
- Search upstream Mozilla code when uncertain about patterns
- Maintain code style consistency with existing codebase
- **Use Material 3 colors defined in `res/values/colors.xml` and `res/values-night/colors.xml`**
- **Test in both light and dark modes**
- **Follow separation of concerns (Manager classes for business logic)**
- **Reuse existing components and patterns**
- Test changes thoroughly before marking complete
- Clean up temporary files (prefix: `tmp_rovodev_*`)

## DEVELOPMENT BEST PRACTICES

### Code Quality
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and small
- Avoid hardcoding values
- Use dependency injection where appropriate
- Prefer composition over inheritance

### Testing Strategy
- Test only relevant parts of codebase
- Don't fix unrelated issues unless explicitly asked
- Verify changes don't break existing functionality
- Test on actual device when UI changes are involved
- Use `adb logcat` to verify runtime behavior

### Version Control
- Make atomic, focused commits
- Write clear commit messages
- Don't mix refactoring with feature work
- Keep changes reviewable and understandable

### Upstream Integration
- Check Mozilla's implementation before reinventing
- Reuse Mozilla components when possible
- Stay compatible with Mozilla's APIs
- Document deviations from upstream patterns

## TOOL USAGE GUIDELINES

### Building & Compilation
```bash
# Always allowed - use these freely
./gradlew assembleDebug
./gradlew clean
./gradlew build
```

### Debugging & Diagnostics (Read-only)
```bash
# Always allowed - these only read state
adb logcat -d
adb shell dumpsys activity
adb shell dumpsys window
adb shell screencap -p > screenshot.png
```

### Device Modification (ASK FIRST)
```bash
# ALWAYS ASK PERMISSION before running these
adb install -r app.apk
adb uninstall com.prirai.android.nira
adb shell am start
adb shell am force-stop
adb shell pm clear
```

### GitHub Integration
- Use GitHub MCP tool to search upstream Mozilla code
- Search for patterns: `repo:mozilla/firefox path:mobile/android/`
- Look for implementation examples in Fenix
- Check Android Components documentation
- Reference GeckoView API docs