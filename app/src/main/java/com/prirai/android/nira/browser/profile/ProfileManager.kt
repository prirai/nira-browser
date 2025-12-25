package com.prirai.android.nira.browser.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.prirai.android.nira.ext.components
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Manages browser profiles - creation, deletion, and persistence
 * Profiles are stored in SharedPreferences as JSON
 */
class ProfileManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val profileListType = Types.newParameterizedType(List::class.java, BrowserProfile::class.java)
    private val profileListAdapter = moshi.adapter<List<BrowserProfile>>(profileListType)
    
    companion object {
        private const val PREFS_NAME = "profile_manager"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_LAST_PRIVATE_PROFILE = "last_private_profile"
        
        @Volatile
        private var instance: ProfileManager? = null
        
        fun getInstance(context: Context): ProfileManager {
            return instance ?: synchronized(this) {
                instance ?: ProfileManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Get all profiles (excluding private mode)
     * Always includes the default profile
     */
    fun getAllProfiles(): List<BrowserProfile> {
        val json = prefs.getString(KEY_PROFILES, null)
        val profiles = if (json != null) {
            profileListAdapter.fromJson(json) ?: emptyList()
        } else {
            emptyList()
        }
        
        // Always ensure default profile exists
        val defaultProfile = BrowserProfile.getDefaultProfile()
        return if (profiles.none { it.id == defaultProfile.id }) {
            listOf(defaultProfile) + profiles
        } else {
            profiles
        }
    }
    
    /**
     * Get the currently active profile
     */
    fun getActiveProfile(): BrowserProfile {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default")
        return getAllProfiles().find { it.id == activeId } ?: BrowserProfile.getDefaultProfile()
    }
    
    /**
     * Set the active profile
     */
    fun setActiveProfile(profile: BrowserProfile) {
        prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, profile.id)}
    }
    
    /**
     * Create a new profile
     */
    fun createProfile(name: String, color: Int, emoji: String): BrowserProfile {
        val newProfile = BrowserProfile(
            name = name,
            color = color,
            emoji = emoji,
            isDefault = false
        )
        
        val profiles = getAllProfiles().toMutableList()
        profiles.add(newProfile)
        saveProfiles(profiles)
        
        return newProfile
    }
    
    /**
     * Update an existing profile
     */
    fun updateProfile(profile: BrowserProfile) {
        if (profile.isDefault) {
            // Can't modify default profile name/icon, only in-memory representation
            return
        }
        
        val profiles = getAllProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles[index] = profile
            saveProfiles(profiles)
        }
    }
    
    /**
     * Delete a profile (cannot delete default)
     */
    fun deleteProfile(profileId: String) {
        if (profileId == "default") {
            throw IllegalArgumentException("Cannot delete default profile")
        }
        
        val profiles = getAllProfiles().toMutableList()
        profiles.removeAll { it.id == profileId }
        saveProfiles(profiles)
        
        // If deleted profile was active, switch to default
        if (getActiveProfile().id == profileId) {
            setActiveProfile(BrowserProfile.getDefaultProfile())
        }
        
        // Clean up profile-specific storage
        cleanupProfileStorage(profileId)
    }
    
    /**
     * Check if we're in private browsing mode
     */
    fun isPrivateMode(): Boolean {
        return prefs.getBoolean(KEY_LAST_PRIVATE_PROFILE, false)
    }
    
    /**
     * Set private browsing mode
     */
    fun setPrivateMode(isPrivate: Boolean) {
        prefs.edit { putBoolean(KEY_LAST_PRIVATE_PROFILE, isPrivate) }
    }
    
    private fun saveProfiles(profiles: List<BrowserProfile>) {
        // Don't save default profile to prefs, it's generated
        val toSave = profiles.filter { !it.isDefault }
        val json = profileListAdapter.toJson(toSave)
        prefs.edit { putString(KEY_PROFILES, json) }
    }
    
    private fun cleanupProfileStorage(profileId: String) {
        // Delete profile-specific session storage
        val profileDir = context.getDir("profile_$profileId", Context.MODE_PRIVATE)
        profileDir.deleteRecursively()
    }

    /**
     * Migrate a tab to another profile by updating its contextId
     * @param tabId The ID of the tab to migrate
     * @param targetProfileId The ID of the target profile ("private" for private mode)
     * @return true if migration was successful
     */
    fun migrateTabToProfile(tabId: String, targetProfileId: String): Boolean {
        val store = context.components.store
        val tab = store.state.tabs.find { it.id == tabId } ?: return false
        
        // Don't migrate if already in the target profile
        val currentContextId = tab.contextId
        val targetContextId = if (targetProfileId == "private") "private" else "profile_$targetProfileId"
        if (currentContextId == targetContextId) return false
        
        // Check if migrating between private and normal
        tab.content.private
        val isTargetPrivate = targetProfileId == "private"
        
        // Always recreate the tab with the new context and privacy mode
        val url = tab.content.url
        val title = tab.content.title
        val isSelected = store.state.selectedTabId == tabId
        
        // Remove old tab
        context.components.tabsUseCases.removeTab(tabId)
        
        // Create new tab with correct context
        context.components.tabsUseCases.addTab(
            url = url,
            private = isTargetPrivate,
            contextId = targetContextId,
            selectTab = isSelected,
            title = title
        )
        
        return true
    }
    
    /**
     * Migrate multiple tabs to another profile
     * @param tabIds List of tab IDs to migrate
     * @param targetProfileId The ID of the target profile ("private" for private mode)
     * @return Number of tabs successfully migrated
     */
    fun migrateTabsToProfile(tabIds: List<String>, targetProfileId: String): Int {
        var successCount = 0
        tabIds.forEach { tabId ->
            if (migrateTabToProfile(tabId, targetProfileId)) {
                successCount++
            }
        }
        return successCount
    }
}
