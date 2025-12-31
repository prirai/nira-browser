# Tab Groups Feature Analysis

## High-Level User Requirements
The user aims to implement a "best-in-class" tab grouping experience, surpassing modern mobile browsers. Key requested features included:

1.  **Tab Autogrouping (Group Island '+' Button)**
    *   **Goal:** When a user uses the '+' button on a Tab Group Island, the new tab must join that group.
    *   **Prior Behavior:** The tab would open as an ungrouped tab.
    *   **Fix:** Explicitly waiting for the new tab ID before adding to the group.

2.  **Enhanced Drag-and-Drop Grouping**
    *   **Goal:** Dragging a tab onto *any* member of a group should add it to that group.
    *   **Visual Feedback:** Highlight the group container when hovering over any member.

3.  **Architecture**
    *   Willingness to refactor for simplicity.

## Codebase Findings
*   **`TabOrderManager`**: Manages `UnifiedTabOrder` (Tabs & Groups).
*   **`AdvancedTabDragDropSystem`**: Handles `Reorder`, `GroupWith`, `MoveToGroup`.
*   **UI**: `TabBarCompose`, `TabSheetListCompose`, `TabSheetGridCompose` all support grouping visuals.

The system is complex due to multiple drag contexts and state management for nested items.
