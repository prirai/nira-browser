package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import mozilla.components.browser.state.state.SessionState

/**
 * Container that wraps EnhancedTabGroupView and adds a profile switcher button at the end
 */
class TabGroupWithProfileSwitcher @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val tabGroupView: EnhancedTabGroupView
    private val profileSwitcherButton: ImageView
    
    private var onProfileSelected: ((BrowserProfile) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipToPadding = false
        clipChildren = false

        // Create tab group view
        tabGroupView = EnhancedTabGroupView(context).apply {
            layoutParams = LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f // Take all available space
            )
        }
        addView(tabGroupView)

        // Create profile switcher button
        profileSwitcherButton = ImageView(context).apply {
            layoutParams = LayoutParams(
                resources.getDimensionPixelSize(R.dimen.profile_switcher_size),
                resources.getDimensionPixelSize(R.dimen.profile_switcher_size)
            ).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.profile_switcher_margin)
                marginEnd = resources.getDimensionPixelSize(R.dimen.profile_switcher_margin)
            }
            setImageResource(android.R.drawable.ic_menu_preferences)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Switch profile"
            
            // Style the button
            val padding = resources.getDimensionPixelSize(R.dimen.profile_switcher_padding)
            setPadding(padding, padding, padding, padding)
            
            // Add ripple effect
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue,
                true
            )
            setBackgroundResource(outValue.resourceId)
            
            setOnClickListener {
                showProfileSwitcherMenu()
            }
        }
        addView(profileSwitcherButton)

        // Set background
        val backgroundColor = if (isDarkMode()) {
            ContextCompat.getColor(context, android.R.color.background_dark)
        } else {
            ContextCompat.getColor(context, android.R.color.background_light)
        }
        setBackgroundColor(backgroundColor)
        elevation = 2f
    }

    private fun isDarkMode(): Boolean {
        return when (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    fun setup(
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit,
        onIslandRenamed: ((String, String) -> Unit)? = null,
        onNewTabInIsland: ((String) -> Unit)? = null,
        onProfileSelected: (BrowserProfile) -> Unit
    ) {
        tabGroupView.setup(onTabSelected, onTabClosed, onIslandRenamed, onNewTabInIsland)
        this.onProfileSelected = onProfileSelected
    }

    fun updateTabs(tabs: List<SessionState>, selectedId: String?) {
        tabGroupView.updateTabs(tabs, selectedId)
    }

    fun updateProfileIcon(profile: BrowserProfile) {
        // Update button to show current profile emoji as text
        // For now, just use an icon - we'll show emoji in the popup
        profileSwitcherButton.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        profileSwitcherButton.setColorFilter(profile.color)
    }

    private fun showProfileSwitcherMenu() {
        val profileManager = ProfileManager.getInstance(context)
        val profiles = profileManager.getAllProfiles()
        val currentProfile = profileManager.getActiveProfile()
        val isPrivate = profileManager.isPrivateMode()

        val popup = PopupMenu(context, profileSwitcherButton, Gravity.END)
        
        android.util.Log.d("TabGroupWithProfileSwitcher", "showProfileSwitcherMenu: profiles=${profiles.size}, currentProfile=${currentProfile.name}, isPrivate=$isPrivate")
        
        // Add profile options (only regular profiles, NOT private mode)
        profiles.forEachIndexed { index, profile ->
            val item = popup.menu.add(0, index, index, "${profile.emoji} ${profile.name}")
            
            // Mark current profile
            if (profile.id == currentProfile.id && !isPrivate) {
                item.isChecked = true
                android.util.Log.d("TabGroupWithProfileSwitcher", "Marked profile ${profile.name} as checked")
            }
        }

        // Enable icons in menu
        popup.menu.setGroupCheckable(0, true, true)

        popup.setOnMenuItemClickListener { item ->
            val itemId = item.itemId
            android.util.Log.d("TabGroupWithProfileSwitcher", "Menu item clicked: itemId=$itemId, profilesSize=${profiles.size}")
            
            if (itemId < profiles.size) {
                // Profile selected
                val profile = profiles[itemId]
                android.util.Log.d("TabGroupWithProfileSwitcher", "Profile selected: ${profile.name}")
                onProfileSelected?.invoke(profile)
                profileManager.setActiveProfile(profile)
                profileManager.setPrivateMode(false)
                true
            } else {
                false
            }
        }

        popup.show()
    }
}
