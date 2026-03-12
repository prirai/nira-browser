package com.prirai.android.nira.components.menu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.prirai.android.nira.R

/**
 * Custom Material 3 browser menu using native Android components.
 * Does not use Mozilla Components menu system.
 */
class Material3BrowserMenu(
    private val context: Context,
    private val items: List<MenuItem>
) {
    
    sealed class MenuItem {
        data class Action(
            val id: String,
            val title: String,
            val iconRes: Int,
            val enabled: Boolean = true,
            val visible: Boolean = true,
            val onClick: () -> Unit
        ) : MenuItem()
        
        data class Toggle(
            val id: String,
            val title: String,
            val iconRes: Int,
            val isChecked: Boolean,
            val enabled: Boolean = true,
            val visible: Boolean = true,
            val onToggle: (Boolean) -> Unit
        ) : MenuItem()
        
        data class ToolbarRow(
            val onBackClick: () -> Unit,
            val onForwardClick: () -> Unit,
            val onReloadClick: () -> Unit,
            val onShareClick: () -> Unit,
            val backEnabled: Boolean = true,
            val forwardEnabled: Boolean = true
        ) : MenuItem()
        
        data class PillRow(
            val title1: String,
            val icon1: Int,
            val onClick1: () -> Unit,
            val title2: String,
            val icon2: Int,
            val onClick2: () -> Unit
        ) : MenuItem()

        data class QuadRow(
            val title1: String, val icon1: Int, val onClick1: () -> Unit,
            val title2: String, val icon2: Int, val onClick2: () -> Unit,
            val title3: String, val icon3: Int, val onClick3: () -> Unit,
            val title4: String, val icon4: Int, val onClick4: () -> Unit
        ) : MenuItem()

        data class IconRow(
            val items: List<IconRowItem>
        ) : MenuItem()

        object Divider : MenuItem()
    }

    data class IconRowItem(
        val title: String,
        val iconRes: Int,
        val onClick: () -> Unit
    )
    
    private var popupWindow: PopupWindow? = null
    
    fun show(anchor: View, preferBottom: Boolean = true) {
        val inflater = LayoutInflater.from(context)
        val menuView = inflater.inflate(R.layout.material3_browser_menu, null)
        
        val recyclerView = menuView.findViewById<RecyclerView>(R.id.menu_recycler)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        recyclerView.adapter = MenuAdapter(items.filter {
            when (it) {
                is MenuItem.Action -> it.visible
                is MenuItem.Toggle -> it.visible
                is MenuItem.ToolbarRow -> true
                is MenuItem.PillRow -> true
                is MenuItem.QuadRow -> true
                is MenuItem.IconRow -> true
                is MenuItem.Divider -> true
            }
        }) {
            popupWindow?.dismiss()
        }

        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val menuWidthPx = (280 * displayMetrics.density).toInt()

        // Measure without height constraint so the menu is never scrollable
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val menuHeight = recyclerView.measuredHeight
        
        popupWindow = PopupWindow(
            menuView,
            menuWidthPx,
            menuHeight,
            true
        ).apply {
            elevation = 0f
            // Apply menu background color programmatically (supports AMOLED)
            val menuBgColor = com.prirai.android.nira.theme.ThemeManager.getMenuBackgroundColor(context)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(menuBgColor).apply {
                // Add rounded corners
                val cornerRadius = (12 * displayMetrics.density)
                val shape = android.graphics.drawable.GradientDrawable()
                shape.cornerRadius = cornerRadius
                shape.setColor(menuBgColor)
                this@apply.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            })
            // Alternative: use a shape drawable with rounded corners
            val shape = android.graphics.drawable.GradientDrawable()
            shape.cornerRadius = (12 * displayMetrics.density)
            shape.setColor(menuBgColor)
            setBackgroundDrawable(shape)
            
            // Get anchor screen position
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            location[0]
            val anchorY = location[1]
            
            val menuWidth = menuWidthPx
            val gap = (8 * displayMetrics.density).toInt()
            
            // Check available space below and above
            screenHeight - (anchorY + anchor.height)

            // Determine if anchor is in the bottom half of the screen (contextual toolbar case)
            val anchorInBottomHalf = anchorY > screenHeight / 2
            
            // Decide whether to show above or below
            val showAbove = if (preferBottom) {
                // Prefer bottom positioning: if anchor is in bottom half, show above it to keep menu at bottom
                // if anchor is in top half, show below it to position menu at bottom
                anchorInBottomHalf
            } else {
                // Prefer top positioning: if anchor is in top half, show below it to keep menu at top
                // if anchor is in bottom half, show above it to position menu at top
                !anchorInBottomHalf
            }
            
            // Calculate Y position
            val yPos = if (showAbove) {
                // Show above - align bottom of menu with top of anchor
                anchorY - menuHeight - gap
            } else {
                // Show below - position below anchor
                anchorY + anchor.height + gap
            }
            
            // Calculate X position - align right edge of menu with right edge of screen minus padding
            val screenRightPadding = (16 * displayMetrics.density).toInt()
            val xPos = displayMetrics.widthPixels - menuWidth - screenRightPadding
            
            // Ensure menu stays on screen
            val finalX = xPos.coerceIn(screenRightPadding, displayMetrics.widthPixels - menuWidth - screenRightPadding)
            val finalY = yPos.coerceIn(screenRightPadding, screenHeight - menuHeight - screenRightPadding)
            
            showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, finalX, finalY)
        }
    }
    
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    private class MenuAdapter(
        private val items: List<MenuItem>,
        private val onDismiss: () -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        companion object {
            const val TYPE_ACTION = 0
            const val TYPE_TOGGLE = 1
            const val TYPE_DIVIDER = 2
            const val TYPE_TOOLBAR = 3
            const val TYPE_PILL = 4
            const val TYPE_QUAD = 5
            const val TYPE_ICON_ROW = 6
        }
        
        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is MenuItem.Action -> TYPE_ACTION
                is MenuItem.Toggle -> TYPE_TOGGLE
                is MenuItem.ToolbarRow -> TYPE_TOOLBAR
                is MenuItem.PillRow -> TYPE_PILL
                is MenuItem.QuadRow -> TYPE_QUAD
                is MenuItem.IconRow -> TYPE_ICON_ROW
                is MenuItem.Divider -> TYPE_DIVIDER
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_ACTION -> ActionViewHolder(
                    inflater.inflate(R.layout.menu_item_action, parent, false)
                )
                TYPE_TOGGLE -> ToggleViewHolder(
                    inflater.inflate(R.layout.menu_item_toggle, parent, false)
                )
                TYPE_TOOLBAR -> ToolbarViewHolder(
                    inflater.inflate(R.layout.menu_toolbar_row, parent, false)
                )
                TYPE_PILL -> PillViewHolder(
                    inflater.inflate(R.layout.menu_item_pill_row, parent, false)
                )
                TYPE_QUAD -> QuadViewHolder(
                    inflater.inflate(R.layout.menu_item_quad_row, parent, false)
                )
                TYPE_ICON_ROW -> IconRowViewHolder(
                    inflater.inflate(R.layout.menu_item_icon_row, parent, false)
                )
                else -> DividerViewHolder(
                    inflater.inflate(R.layout.menu_item_divider, parent, false)
                )
            }
        }
        
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is MenuItem.Action -> (holder as ActionViewHolder).bind(item, onDismiss)
                is MenuItem.Toggle -> (holder as ToggleViewHolder).bind(item, onDismiss)
                is MenuItem.ToolbarRow -> (holder as ToolbarViewHolder).bind(item, onDismiss)
                is MenuItem.PillRow -> (holder as PillViewHolder).bind(item, onDismiss)
                is MenuItem.QuadRow -> (holder as QuadViewHolder).bind(item, onDismiss)
                is MenuItem.IconRow -> (holder as IconRowViewHolder).bind(item, onDismiss)
                is MenuItem.Divider -> {}
            }
        }
        
        override fun getItemCount() = items.size
        
        private class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: android.widget.ImageView = view.findViewById(R.id.menu_item_icon)
            private val title: MaterialTextView = view.findViewById(R.id.menu_item_title)
            
            fun bind(item: MenuItem.Action, onDismiss: () -> Unit) {
                icon.setImageResource(item.iconRes)
                title.text = item.title
                itemView.isEnabled = item.enabled
                itemView.alpha = if (item.enabled) 1.0f else 0.5f
                
                itemView.setOnClickListener {
                    if (item.enabled) {
                        item.onClick()
                        onDismiss()
                    }
                }
            }
        }
        
        private class ToggleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: android.widget.ImageView = view.findViewById(R.id.menu_item_icon)
            private val title: MaterialTextView = view.findViewById(R.id.menu_item_title)
            private val toggle: com.google.android.material.switchmaterial.SwitchMaterial = 
                view.findViewById(R.id.menu_item_toggle)
            
            fun bind(item: MenuItem.Toggle, onDismiss: () -> Unit) {
                icon.setImageResource(item.iconRes)
                title.text = item.title
                toggle.isChecked = item.isChecked
                itemView.isEnabled = item.enabled
                itemView.alpha = if (item.enabled) 1.0f else 0.5f
                
                itemView.setOnClickListener {
                    if (item.enabled) {
                        val newState = !toggle.isChecked
                        toggle.isChecked = newState
                        item.onToggle(newState)
                        onDismiss()
                    }
                }
                
                toggle.setOnCheckedChangeListener { _, isChecked ->
                    if (item.enabled) {
                        item.onToggle(isChecked)
                    }
                }
            }
        }
        
        private class ToolbarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val backButton: android.widget.ImageButton = view.findViewById(R.id.toolbar_back)
            private val forwardButton: android.widget.ImageButton = view.findViewById(R.id.toolbar_forward)
            private val reloadButton: android.widget.ImageButton = view.findViewById(R.id.toolbar_reload)
            private val shareButton: android.widget.ImageButton = view.findViewById(R.id.toolbar_share)
            
            fun bind(item: MenuItem.ToolbarRow, onDismiss: () -> Unit) {
                backButton.isEnabled = item.backEnabled
                backButton.alpha = if (item.backEnabled) 1.0f else 0.5f
                backButton.setOnClickListener {
                    if (item.backEnabled) {
                        item.onBackClick()
                        onDismiss()
                    }
                }
                
                forwardButton.isEnabled = item.forwardEnabled
                forwardButton.alpha = if (item.forwardEnabled) 1.0f else 0.5f
                forwardButton.setOnClickListener {
                    if (item.forwardEnabled) {
                        item.onForwardClick()
                        onDismiss()
                    }
                }
                
                reloadButton.setOnClickListener {
                    item.onReloadClick()
                    onDismiss()
                }
                
                shareButton.setOnClickListener {
                    item.onShareClick()
                    onDismiss()
                }
            }
        }
        
        private class PillViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val action1: View = view.findViewById(R.id.pill_action_1)
            private val icon1: android.widget.ImageView = view.findViewById(R.id.pill_icon_1)
            private val title1: MaterialTextView = view.findViewById(R.id.pill_title_1)
            private val action2: View = view.findViewById(R.id.pill_action_2)
            private val icon2: android.widget.ImageView = view.findViewById(R.id.pill_icon_2)
            private val title2: MaterialTextView = view.findViewById(R.id.pill_title_2)
            
            fun bind(item: MenuItem.PillRow, onDismiss: () -> Unit) {
                icon1.setImageResource(item.icon1)
                title1.text = item.title1
                action1.setOnClickListener {
                    item.onClick1()
                    onDismiss()
                }
                
                icon2.setImageResource(item.icon2)
                title2.text = item.title2
                action2.setOnClickListener {
                    item.onClick2()
                    onDismiss()
                }
            }
        }
        
        private class QuadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val action1: View = view.findViewById(R.id.quad_action_1)
            private val icon1: android.widget.ImageView = view.findViewById(R.id.quad_icon_1)
            private val title1: MaterialTextView = view.findViewById(R.id.quad_title_1)
            private val action2: View = view.findViewById(R.id.quad_action_2)
            private val icon2: android.widget.ImageView = view.findViewById(R.id.quad_icon_2)
            private val title2: MaterialTextView = view.findViewById(R.id.quad_title_2)
            private val action3: View = view.findViewById(R.id.quad_action_3)
            private val icon3: android.widget.ImageView = view.findViewById(R.id.quad_icon_3)
            private val title3: MaterialTextView = view.findViewById(R.id.quad_title_3)
            private val action4: View = view.findViewById(R.id.quad_action_4)
            private val icon4: android.widget.ImageView = view.findViewById(R.id.quad_icon_4)
            private val title4: MaterialTextView = view.findViewById(R.id.quad_title_4)

            fun bind(item: MenuItem.QuadRow, onDismiss: () -> Unit) {
                icon1.setImageResource(item.icon1); title1.text = item.title1
                action1.setOnClickListener { item.onClick1(); onDismiss() }
                icon2.setImageResource(item.icon2); title2.text = item.title2
                action2.setOnClickListener { item.onClick2(); onDismiss() }
                icon3.setImageResource(item.icon3); title3.text = item.title3
                action3.setOnClickListener { item.onClick3(); onDismiss() }
                icon4.setImageResource(item.icon4); title4.text = item.title4
                action4.setOnClickListener { item.onClick4(); onDismiss() }
            }
        }

        private class IconRowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val item1: View = view.findViewById(R.id.icon_row_item_1)
            private val icon1: android.widget.ImageView = view.findViewById(R.id.icon_row_icon_1)
            private val title1: MaterialTextView = view.findViewById(R.id.icon_row_title_1)
            private val item2: View = view.findViewById(R.id.icon_row_item_2)
            private val icon2: android.widget.ImageView = view.findViewById(R.id.icon_row_icon_2)
            private val title2: MaterialTextView = view.findViewById(R.id.icon_row_title_2)
            private val item3: View = view.findViewById(R.id.icon_row_item_3)
            private val icon3: android.widget.ImageView = view.findViewById(R.id.icon_row_icon_3)
            private val title3: MaterialTextView = view.findViewById(R.id.icon_row_title_3)

            fun bind(row: MenuItem.IconRow, onDismiss: () -> Unit) {
                val slots = listOf(
                    Triple(item1, icon1, title1),
                    Triple(item2, icon2, title2),
                    Triple(item3, icon3, title3)
                )
                slots.forEachIndexed { index, (container, iconView, titleView) ->
                    val rowItem = row.items.getOrNull(index)
                    if (rowItem != null) {
                        container.visibility = View.VISIBLE
                        iconView.setImageResource(rowItem.iconRes)
                        titleView.text = rowItem.title
                        container.setOnClickListener { rowItem.onClick(); onDismiss() }
                    } else {
                        container.visibility = View.INVISIBLE
                    }
                }
            }
        }

        private class DividerViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
