package com.prirai.android.nira.browser.tabs

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.isVisible
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.tabstray.TabViewHolder
import mozilla.components.browser.tabstray.TabsTrayStyling
import mozilla.components.support.ktx.android.content.getColorFromAttr

class TabListViewHolder(
    itemView: View
) : TabViewHolder(itemView) {
    @VisibleForTesting
    internal val iconView: ImageView? = itemView.findViewById(R.id.mozac_browser_tabstray_icon)

    @VisibleForTesting
    internal val titleView: TextView = itemView.findViewById(R.id.mozac_browser_tabstray_title)

    @VisibleForTesting
    internal val closeView: AppCompatImageButton = itemView.findViewById(R.id.mozac_browser_tabstray_close)

    @VisibleForTesting
    internal val groupIndicator: TextView? = itemView.findViewById(R.id.tabGroupIndicator)

    override var tab: TabSessionState? = null

    @VisibleForTesting
    internal var styling: TabsTrayStyling? = null

    fun bind(
        tab: TabSessionState,
        isSelected: Boolean,
        styling: TabsTrayStyling,
        delegate: mozilla.components.browser.tabstray.TabsTray.Delegate,
        isFirst: Boolean = false,
        isLast: Boolean = false,
        isSingle: Boolean = false
    ) {
        this.tab = tab
        this.styling = styling

        // Show title with fallback to URL
        val title = when {
            tab.content.title.isNotEmpty() -> tab.content.title
            tab.content.url.startsWith("about:homepage") || tab.content.url.startsWith("about:privatebrowsing") -> "New Tab"
            else -> tab.content.url.ifEmpty { "" }
        }

        titleView.text = title

        // Update group indicator
        updateGroupIndicator(tab.id)

        itemView.setOnClickListener {
            delegate.onTabSelected(tab)
        }

        closeView.setOnClickListener {
            delegate.onTabClosed(tab)
        }

        updateSelectedTabIndicator(isSelected, isFirst, isLast, isSingle)

        // Set favicon - check cache if tab icon is null
        if (tab.content.icon != null) {
            iconView?.setImageBitmap(tab.content.icon)
        } else {
            // Check favicon cache for this URL
            iconView?.setImageBitmap(null) // Clear first
            CoroutineScope(Dispatchers.Main).launch {
                val cachedIcon = itemView.context.components.faviconCache.loadFavicon(tab.content.url)
                if (cachedIcon != null) {
                    iconView?.setImageBitmap(cachedIcon)
                }
            }
        }
    }

    override fun bind(
        tab: TabSessionState,
        isSelected: Boolean,
        styling: TabsTrayStyling,
        delegate: mozilla.components.browser.tabstray.TabsTray.Delegate
    ) {
        bind(tab, isSelected, styling, delegate, false, false, false)
    }

    override fun updateSelectedTabIndicator(showAsSelected: Boolean) {
        updateSelectedTabIndicator(showAsSelected, false, false, false)
    }

    private fun updateSelectedTabIndicator(
        showAsSelected: Boolean,
        isFirst: Boolean,
        isLast: Boolean,
        isSingle: Boolean
    ) {
        if (showAsSelected) {
            showItemAsSelected(isFirst, isLast, isSingle)
        } else {
            showItemAsNotSelected(isFirst, isLast, isSingle)
        }
    }

    @VisibleForTesting
    internal fun showItemAsSelected(isFirst: Boolean = false, isLast: Boolean = false, isSingle: Boolean = false) {
        titleView.setTextColor(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))
        closeView.imageTintList =
            ColorStateList.valueOf(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))

        val backgroundResource = when {
            isSingle -> R.drawable.tab_list_single_selected_background
            isFirst -> R.drawable.tab_list_first_selected_background
            isLast -> R.drawable.tab_list_last_selected_background
            else -> R.drawable.tab_list_selected_background
        }
        itemView.setBackgroundResource(backgroundResource)
    }

    @VisibleForTesting
    internal fun showItemAsNotSelected(isFirst: Boolean = false, isLast: Boolean = false, isSingle: Boolean = false) {
        titleView.setTextColor(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))
        closeView.imageTintList =
            ColorStateList.valueOf(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))

        val backgroundResource = when {
            isSingle -> R.drawable.tab_list_single_unselected_background
            isFirst -> R.drawable.tab_list_first_unselected_with_top_background
            isLast -> R.drawable.tab_list_last_unselected_background
            else -> R.drawable.tab_list_unselected_background
        }
        itemView.setBackgroundResource(backgroundResource)
    }

    private fun updateGroupIndicator(tabId: String) {
        groupIndicator?.let { indicator ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val tabGroupManager = itemView.context.components.tabGroupManager
                    val groupData = tabGroupManager.getGroupForTab(tabId)
                    val groupName = groupData?.name

                    // Only show indicator if group name is not null and not blank
                    if (groupName != null && groupName.isNotBlank()) {
                        indicator.text = groupName
                        indicator.isVisible = true
                    } else {
                        indicator.isVisible = false
                    }
                } catch (e: Exception) {
                    // Fallback if tab groups not initialized
                    indicator.isVisible = false
                }
            }
        }
    }
}
