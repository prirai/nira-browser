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
    val isActive: Boolean = true,
    val contextId: String? = null, // Profile/browsing mode context (null = default, "private", "profile_xxx")
    val isCollapsed: Boolean = false // Whether the group is collapsed in UI
)

/**
 * Represents the association between a tab and a group.
 */
@Entity(tableName = "tab_group_members", primaryKeys = ["tabId", "groupId"])
data class TabGroupMember(
    val tabId: String,
    val groupId: String,
    val position: Int = 0,
)
