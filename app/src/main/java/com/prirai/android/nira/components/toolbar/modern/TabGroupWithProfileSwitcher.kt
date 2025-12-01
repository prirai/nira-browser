package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import mozilla.components.browser.state.state.SessionState

/**
 * Container that wraps EnhancedTabGroupView and adds a profile switcher pill at the end
 */
class TabGroupWithProfileSwitcher @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val tabGroupView: EnhancedTabGroupView
    private val profilePillCard: CardView
    private val profileEmojiText: TextView
    private val profileNameText: TextView
    
    private var onProfileSelected: ((BrowserProfile) -> Unit)? = null

    init {
        clipToPadding = false
        clipChildren = false

        // Create tab group view (below)
        tabGroupView = EnhancedTabGroupView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            // Add padding at the end to make room for profile switcher
            // Profile pill: 40dp (no margin since it's flush to edge)
            val endPadding = (40 * resources.displayMetrics.density).toInt()
            setPadding(paddingLeft, paddingTop, endPadding, paddingBottom)
        }
        addView(tabGroupView)

        // Create profile switcher pill (above tabs) - emoji only
        profilePillCard = CardView(context).apply {
            layoutParams = LayoutParams(
                (40 * resources.displayMetrics.density).toInt(), // 40dp width
                (40 * resources.displayMetrics.density).toInt() // 40dp height
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = 0 // No margin - flush to the right edge
            }
            
            radius = 0f // No radius - will apply custom shape
            cardElevation = 8f // Higher elevation to appear above tabs
            
            // Apply custom background with rounded left corners only
            post {
                val bg = GradientDrawable().apply {
                    val cornerRadius = 20f * resources.displayMetrics.density
                    cornerRadii = floatArrayOf(
                        cornerRadius, cornerRadius, // top-left
                        0f, 0f,                     // top-right (square)
                        0f, 0f,                     // bottom-right (square)
                        cornerRadius, cornerRadius  // bottom-left
                    )
                    setColor(ContextCompat.getColor(context, android.R.color.white))
                    
                    // Add elevation shadow effect
                    val isDark = context.resources.configuration.uiMode and 
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    if (isDark) {
                        setColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    }
                }
                background = bg
            }
            
            // Inner container for pill content
            val pillContent = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER // Center the emoji
                val padding = (8 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                
                // Profile emoji only (no name)
                profileEmojiText = TextView(context).apply {
                    text = "👤"
                    textSize = 22f // Larger emoji
                    gravity = Gravity.CENTER
                    includeFontPadding = false // Remove extra padding
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                addView(profileEmojiText)
                
                // Hidden profile name (kept for updateProfileIcon compatibility)
                profileNameText = TextView(context).apply {
                    text = ""
                    visibility = GONE
                }
                
                // No dropdown icon needed since it's just the emoji
            }
            
            addView(pillContent)
            
            // Add ripple effect
            foreground = ContextCompat.getDrawable(
                context,
                android.R.drawable.list_selector_background
            )
            
            isClickable = true
            isFocusable = true
            
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                showProfileSwitcherMenu()
            }
        }
        addView(profilePillCard)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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
        
        // Initialize with current profile
        val profileManager = ProfileManager.getInstance(context)
        val currentProfile = profileManager.getActiveProfile()
        updateProfileIcon(currentProfile)
    }

    fun updateTabs(tabs: List<SessionState>, selectedId: String?) {
        tabGroupView.updateTabs(tabs, selectedId)
    }

    fun updateProfileIcon(profile: BrowserProfile) {
        profileEmojiText.text = profile.emoji
        profileNameText.text = profile.name
        
        // Update pill background color with profile color (subtle)
        val isDark = isDarkMode()
        val backgroundColor = if (isDark) {
            0xFF1E1E1E.toInt() // Dark surface color
        } else {
            0xFFF5F5F5.toInt() // Light surface color
        }
        
        // Add subtle tint of profile color
        val tintedColor = blendColors(backgroundColor, profile.color, 0.15f)
        profilePillCard.setCardBackgroundColor(tintedColor)
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = android.graphics.Color.red(color1) * inverseRatio + android.graphics.Color.red(color2) * ratio
        val g = android.graphics.Color.green(color1) * inverseRatio + android.graphics.Color.green(color2) * ratio
        val b = android.graphics.Color.blue(color1) * inverseRatio + android.graphics.Color.blue(color2) * ratio
        return android.graphics.Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    private fun showProfileSwitcherMenu() {
        val profileManager = ProfileManager.getInstance(context)
        val profiles = profileManager.getAllProfiles()
        val currentProfile = profileManager.getActiveProfile()
        val isPrivate = profileManager.isPrivateMode()

        // Create popup menu with Gravity.TOP to appear above the pill
        val popup = PopupMenu(context, profilePillCard, Gravity.TOP or Gravity.END)
        
        // Add profile options (only regular profiles, NOT private mode)
        profiles.forEachIndexed { index, profile ->
            val item = popup.menu.add(0, index, index, "${profile.emoji} ${profile.name}")
            
            // Mark current profile
            if (profile.id == currentProfile.id && !isPrivate) {
                item.isChecked = true
            }
        }

        // Enable icons in menu
        popup.menu.setGroupCheckable(0, true, true)

        popup.setOnMenuItemClickListener { item ->
            val itemId = item.itemId
            
            if (itemId < profiles.size) {
                // Profile selected
                val profile = profiles[itemId]
                profileManager.setActiveProfile(profile)
                profileManager.setPrivateMode(false)
                
                // Update the emoji to show the new profile
                updateProfileIcon(profile)
                
                // Notify callback
                onProfileSelected?.invoke(profile)
                true
            } else {
                false
            }
        }

        popup.show()
    }
}
