package com.prirai.android.nira.browser.tabgroups

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Tab group data model based on Mozilla's architecture.
 * Represents a collection of related tabs.
 */
@Entity(tableName = "tab_groups")
data class TabGroup(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val color: String = "blue", // Color identifier for visual distinction
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Represents the association between a tab and a group.
 */
@Entity(tableName = "tab_group_members", primaryKeys = ["tabId", "groupId"])
data class TabGroupMember(
    val tabId: String,
    val groupId: String,
    val position: Int = 0
)

/**
 * Domain model representing a tab group with its members.
 */
data class TabGroupWithTabs(
    val group: TabGroup,
    val tabIds: List<String> = emptyList()
) {
    val tabCount: Int get() = tabIds.size
    val isEmpty: Boolean get() = tabIds.isEmpty()
    val isNotEmpty: Boolean get() = tabIds.isNotEmpty()
}

/**
 * Tab group name generator that returns blank names.
 * Groups are identified internally by their UUID, but display no name by default.
 */
class TabGroupNameGenerator {
    /**
     * Returns an empty string as the default group name.
     * Groups have internal IDs (UUIDs) for identification.
     */
    fun generateName(): String {
        return ""
    }

    fun releaseName(name: String) {
        // No-op since we don't track blank names
    }
}
