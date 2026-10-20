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

    @Query("DELETE FROM tab_group_members WHERE tabId = :tabId AND groupId = :groupId")
    suspend fun removeTabFromGroup(tabId: String, groupId: String)

    @Query("DELETE FROM tab_group_members WHERE groupId = :groupId")
    suspend fun removeAllTabsFromGroup(groupId: String)

    @Query("SELECT tabId FROM tab_group_members WHERE groupId = :groupId ORDER BY position")
    suspend fun getTabIdsInGroup(groupId: String): List<String>

    /**
     * One-time migration helper: rewrite all rows whose `contextId == :from` to
     * `:to`. Used by
     * `com.prirai.android.nira.browser.profile.DefaultProfileTabMigration` to
     * flip legacy `"profile_default"` rows to `null`. Pass `to = null` to clear.
     * Returns the number of rows updated.
     */
    @Query("UPDATE tab_groups SET contextId = :to WHERE contextId = :from")
    suspend fun reassignContextIdGroups(from: String, to: String?): Int
}
