package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private var onPrivateModeSelected: (() -> Unit)? = null

    init {
        clipToPadding = false
        clipChildren = false

        // Set container background using Material 3 surface color with tonal elevation overlay (3dp)
        val elevationDp = 3f * resources.displayMetrics.density
        val elevatedColor = com.google.android.material.elevation.ElevationOverlayProvider(context)
            .compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
        setBackgroundColor(elevatedColor)
        elevation = 2f

        // Create tab group view (below)
        tabGroupView = EnhancedTabGroupView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            // Add padding at the end to make room for profile switcher
            // Profile pill: ~100dp estimated (emoji + name + padding)
            val endPadding = (100 * resources.displayMetrics.density).toInt()
            setPadding(paddingLeft, paddingTop, endPadding, paddingBottom)
        }
        addView(tabGroupView)

        // Create profile switcher pill (above tabs) - show both emoji and name
        profilePillCard = CardView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, // Auto width to fit content
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
                    // Use Material 3 surface color
                    setColor(ContextCompat.getColor(context, R.color.m3_surface_container_background))
                }
                background = bg
            }
            
            // Inner container for pill content
            val pillContent = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val paddingHorizontal = (12 * resources.displayMetrics.density).toInt()
                val paddingVertical = (8 * resources.displayMetrics.density).toInt()
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                
                // Profile emoji
                profileEmojiText = TextView(context).apply {
                    text = "👤"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = (4 * resources.displayMetrics.density).toInt()
                    }
                }
                addView(profileEmojiText)
                
                // Profile name - always visible
                profileNameText = TextView(context).apply {
                    text = "Default"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.m3_on_surface))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                addView(profileNameText)
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
        onProfileSelected: (BrowserProfile) -> Unit,
        onPrivateModeSelected: (() -> Unit)? = null,
        onTabDuplicated: ((String) -> Unit)? = null
    ) {
        tabGroupView.setup(onTabSelected, onTabClosed, onIslandRenamed, onNewTabInIsland, onTabDuplicated)
        this.onProfileSelected = onProfileSelected
        this.onPrivateModeSelected = onPrivateModeSelected
        
        // Initialize with current mode
        val profileManager = ProfileManager.getInstance(context)
        val isPrivate = profileManager.isPrivateMode()
        if (isPrivate) {
            updateToPrivateMode()
        } else {
            val currentProfile = profileManager.getActiveProfile()
            updateProfileIcon(currentProfile)
        }
        
        // Profile switcher permanently hidden
        setProfileSwitcherVisible(false)
    }
    
    fun setProfileSwitcherVisible(visible: Boolean) {
        profilePillCard.visibility = if (visible) VISIBLE else GONE
        
        // Adjust tab group padding based on visibility
        val endPadding = if (visible) {
            (100 * resources.displayMetrics.density).toInt()
        } else {
            0
        }
        tabGroupView.setPadding(tabGroupView.paddingLeft, tabGroupView.paddingTop, endPadding, tabGroupView.paddingBottom)
    }

    fun updateTabs(tabs: List<SessionState>, selectedId: String?) {
        tabGroupView.updateTabs(tabs, selectedId)
    }

    fun updateProfileIcon(profile: BrowserProfile) {
        profileEmojiText.text = profile.emoji
        profileNameText.text = profile.name
        
        // Update pill background color with Material 3 theme colors
        val typedValue = android.util.TypedValue()
        val theme = context.theme
        
        val backgroundColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
            typedValue.data
        } else {
            // Fallback
            if (isDarkMode()) 0xFF1E1E1E.toInt() else 0xFFF5F5F5.toInt()
        }
        
        // Add subtle tint of profile color
        val tintedColor = blendColors(backgroundColor, profile.color, 0.15f)
        profilePillCard.setCardBackgroundColor(tintedColor)
    }
    
    fun updateToPrivateMode() {
        profileEmojiText.text = "🕵️"
        profileNameText.text = "Private"
        
        // Use Material 3 purple tint for private mode
        android.util.TypedValue()
        context.theme
        
        val backgroundColor = com.prirai.android.nira.theme.ColorConstants.getColorFromAttr(
            context,
            com.google.android.material.R.attr.colorSurfaceVariant,
            if (isDarkMode()) 0xFF1E1E1E.toInt() else 0xFFF5F5F5.toInt()
        )
        val purpleColor = com.prirai.android.nira.theme.ColorConstants.PrivateMode.PURPLE
        val tintedColor = blendColors(backgroundColor, purpleColor, 0.2f)
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
        // Profile switcher permanently disabled - function not used
        return
        
        /* Commented out unused code
        val profileManager = ProfileManager.getInstance(context)
        val profiles = profileManager.getAllProfiles()
        val currentProfile = profileManager.getActiveProfile()
        val isPrivate = profileManager.isPrivateMode()

        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_profile_switcher, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.profileRecyclerView)
        
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        
        val items = mutableListOf<ProfileSwitcherItem>()
        
        // Add private mode
        items.add(ProfileSwitcherItem(
            id = "private",
            emoji = "🕵️",
            name = "Private",
            description = null,
            isSelected = isPrivate,
            isPrivate = true
        ))
        
        // Add all profiles
        profiles.forEach { profile ->
            items.add(ProfileSwitcherItem(
                id = profile.id,
                emoji = profile.emoji,
                name = profile.name,
                description = null,
                isSelected = !isPrivate && profile.id == currentProfile.id,
                isPrivate = false
            ))
        }
        
        val adapter = ProfileSwitcherAdapter(items) { selectedItem ->
            if (selectedItem.isPrivate) {
                // Private mode selected
                profileManager.setPrivateMode(true)
                updateToPrivateMode()
                onPrivateModeSelected?.invoke()
            } else {
                // Profile selected
                val profile = profiles.find { it.id == selectedItem.id }
                if (profile != null) {
                    profileManager.setActiveProfile(profile)
                    profileManager.setPrivateMode(false)
                    updateProfileIcon(profile)
                    onProfileSelected?.invoke(profile)
                }
            }
        }
        
        recyclerView.adapter = adapter
        
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()
            
        dialog.show()
        */
    }
    
    private data class ProfileSwitcherItem(
        val id: String,
        val emoji: String,
        val name: String,
        val description: String?,
        val isSelected: Boolean,
        val isPrivate: Boolean
    )
    
    private inner class ProfileSwitcherAdapter(
        private val items: List<ProfileSwitcherItem>,
        private val onItemClick: (ProfileSwitcherItem) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_profile_selector, parent, false)
            return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
        }
        
        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val card = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.profileCard)
            val emoji = holder.itemView.findViewById<TextView>(R.id.profileEmoji)
            val name = holder.itemView.findViewById<TextView>(R.id.profileName)
            val description = holder.itemView.findViewById<TextView>(R.id.profileDescription)
            
            emoji.text = item.emoji
            name.text = item.name
            
            if (item.description != null) {
                description.text = item.description
                description.visibility = VISIBLE
            } else {
                description.visibility = GONE
            }
            
            card.isChecked = item.isSelected
            
            card.setOnClickListener {
                onItemClick(item)
                // Find the dialog and dismiss it
                (holder.itemView.parent.parent.parent as? android.app.Dialog)?.dismiss()
            }
        }
        
        override fun getItemCount() = items.size
    }
}
