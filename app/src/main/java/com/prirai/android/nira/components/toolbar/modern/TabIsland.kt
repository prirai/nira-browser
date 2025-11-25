package com.prirai.android.nira.components.toolbar.modern

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import mozilla.components.browser.state.state.SessionState

/**
 * Represents a Tab Island - a group of related tabs with a shared color and name.
 * Tab Islands help organize tabs visually and conceptually.
 */
@Parcelize
data class TabIsland(
    val id: String,
    val name: String,
    val color: Int,
    val tabIds: MutableList<String>,
    val isCollapsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
) : Parcelable {

    /**
     * Checks if this island contains the given tab ID
     */
    fun containsTab(tabId: String): Boolean = tabIds.contains(tabId)

    /**
     * Returns the number of tabs in this island
     */
    fun size(): Int = tabIds.size

    /**
     * Checks if this island is empty
     */
    fun isEmpty(): Boolean = tabIds.isEmpty()

    /**
     * Creates a copy with updated name
     */
    fun withName(newName: String): TabIsland = copy(
        name = newName,
        lastModifiedAt = System.currentTimeMillis()
    )

    /**
     * Creates a copy with updated color
     */
    fun withColor(newColor: Int): TabIsland = copy(
        color = newColor,
        lastModifiedAt = System.currentTimeMillis()
    )

    /**
     * Creates a copy with updated collapse state
     */
    fun withCollapsed(collapsed: Boolean): TabIsland = copy(
        isCollapsed = collapsed,
        lastModifiedAt = System.currentTimeMillis()
    )

    /**
     * Creates a copy with a tab added
     */
    fun withTabAdded(tabId: String): TabIsland {
        if (!tabIds.contains(tabId)) {
            tabIds.add(tabId)
        }
        return copy(
            tabIds = tabIds,
            lastModifiedAt = System.currentTimeMillis()
        )
    }

    /**
     * Creates a copy with a tab removed
     */
    fun withTabRemoved(tabId: String): TabIsland {
        tabIds.remove(tabId)
        return copy(
            tabIds = tabIds,
            lastModifiedAt = System.currentTimeMillis()
        )
    }

    /**
     * Creates a copy with tabs reordered
     */
    fun withTabsReordered(newTabIds: List<String>): TabIsland = copy(
        tabIds = newTabIds.toMutableList(),
        lastModifiedAt = System.currentTimeMillis()
    )

    companion object {
        /**
         * Default colors for tab islands
         */
        val DEFAULT_COLORS = listOf(
            0xFFE57373.toInt(), // Red
            0xFF81C784.toInt(), // Green
            0xFF64B5F6.toInt(), // Blue
            0xFFFFB74D.toInt(), // Orange
            0xFFAED581.toInt(), // Light Green
            0xFFFFD54F.toInt(), // Yellow
            0xFF90A4AE.toInt(), // Grey
            0xFFF06292.toInt(), // Pink
            0xFF9575CD.toInt(), // Purple
            0xFF4DD0E1.toInt(), // Cyan
            0xFFDCE775.toInt(), // Lime
            0xFFFFAB91.toInt()  // Deep Orange
        )

        /**
         * Generates a default name for an island based on its index.
         * Returns blank string - islands are identified by internal ID only.
         */
        fun generateDefaultName(index: Int): String = ""

        /**
         * Creates a new island with generated defaults
         */
        fun create(
            tabIds: List<String> = emptyList(),
            colorIndex: Int = 0,
            name: String? = null
        ): TabIsland {
            val color = DEFAULT_COLORS[colorIndex % DEFAULT_COLORS.size]
            val islandName = name ?: generateDefaultName(colorIndex)
            return TabIsland(
                id = java.util.UUID.randomUUID().toString(),
                name = islandName,
                color = color,
                tabIds = tabIds.toMutableList(),
                isCollapsed = false
            )
        }
    }
}

/**
 * Represents a display item in the tab pills view - can be either a tab or an island header
 */
sealed class TabPillItem {
    /**
     * A regular tab that may or may not belong to an island
     */
    data class Tab(
        val session: SessionState,
        val islandId: String? = null,
        val islandColor: Int? = null,
        val isFirst: Boolean = false,
        val isLast: Boolean = false
    ) : TabPillItem()

    /**
     * An island header that shows the island name and collapse button
     */
    data class IslandHeader(
        val island: TabIsland,
        val isCollapsed: Boolean
    ) : TabPillItem()

    /**
     * A collapsed island pill that shows tab count
     */
    data class CollapsedIsland(
        val island: TabIsland,
        val tabCount: Int
    ) : TabPillItem()

    /**
     * An expanded island group showing header and all child tabs as one unit
     */
    data class ExpandedIslandGroup(
        val island: TabIsland,
        val tabs: List<SessionState>
    ) : TabPillItem()
}
