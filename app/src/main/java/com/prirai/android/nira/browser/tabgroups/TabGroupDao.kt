package com.prirai.android.nira.browser.tabgroups

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TabGroupDao {
    
    @Query("SELECT * FROM tab_groups WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllActiveGroups(): Flow<List<TabGroup>>
    
    @Query("SELECT * FROM tab_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): TabGroup?
    
    @Query("SELECT * FROM tab_groups WHERE name = :name AND isActive = 1 LIMIT 1")
    suspend fun getGroupByName(name: String): TabGroup?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: TabGroup)
    
    @Update
    suspend fun updateGroup(group: TabGroup)
    
    @Delete
    suspend fun deleteGroup(group: TabGroup)
    
    @Query("UPDATE tab_groups SET isActive = 0 WHERE id = :groupId")
    suspend fun markGroupAsInactive(groupId: String)
    
    // Tab group membership operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabGroupMember(member: TabGroupMember)
    
    @Delete
    suspend fun deleteTabGroupMember(member: TabGroupMember)
    
    @Query("DELETE FROM tab_group_members WHERE tabId = :tabId")
    suspend fun removeTabFromAllGroups(tabId: String)
    
    @Query("DELETE FROM tab_group_members WHERE tabId = :tabId AND groupId = :groupId")
    suspend fun removeTabFromGroup(tabId: String, groupId: String)
    
    @Query("DELETE FROM tab_group_members WHERE groupId = :groupId")
    suspend fun removeAllTabsFromGroup(groupId: String)
    
    @Query("SELECT groupId FROM tab_group_members WHERE tabId = :tabId")
    suspend fun getGroupIdForTab(tabId: String): String?
    
    @Query("SELECT tabId FROM tab_group_members WHERE groupId = :groupId ORDER BY position")
    suspend fun getTabIdsInGroup(groupId: String): List<String>
    
    @Query("""
        SELECT g.*, 
               (SELECT GROUP_CONCAT(tgm.tabId) FROM tab_group_members tgm WHERE tgm.groupId = g.id) as tabIds
        FROM tab_groups g 
        WHERE g.isActive = 1 
        ORDER BY g.createdAt DESC
    """)
    fun getAllGroupsWithTabs(): Flow<List<TabGroupWithTabIds>>
    
    @Query("SELECT COUNT(*) FROM tab_group_members WHERE groupId = :groupId")
    suspend fun getTabCountInGroup(groupId: String): Int
}

/**
 * Data class for Room query results
 */
data class TabGroupWithTabIds(
    val id: String,
    val name: String,
    val color: String,
    val createdAt: Long,
    val isActive: Boolean,
    val contextId: String?,
    val tabIds: String? // Comma-separated tab IDs
) {
    fun toTabGroupWithTabs(): TabGroupWithTabs {
        val group = TabGroup(id, name, color, createdAt, isActive, contextId)
        val tabIdList = tabIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return TabGroupWithTabs(group, tabIdList)
    }
}