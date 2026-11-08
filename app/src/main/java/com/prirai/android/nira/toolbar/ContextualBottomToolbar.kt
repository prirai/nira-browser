@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.prirai.android.nira.toolbar

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.color.MaterialColors
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.ui.components.pressScale
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.ui.icons.R as MozacIcons

/**
 * Context-aware bottom toolbar that shows different quick actions based on browsing state
 *
 * The row itself renders in Compose (hosted in a [ComposeView]); the class stays a
 * [LinearLayout] so [com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar]
 * keeps mounting it through ModernToolbarSystem exactly as before, and the public
 * surface callers rely on ([listener], [updateForContext], View visibility/height) is
 * unchanged. The View API mutates Compose snapshot state, so context updates flow
 * into the row as recompositions.
 */
class ContextualBottomToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface ContextualToolbarListener {
        fun onBackClicked()
        fun onForwardClicked()
        fun onShareClicked()
        fun onSearchClicked()
        fun onNewTabClicked()
        fun onTabCountClicked()
        fun onMenuClicked()
        fun onBookmarksClicked()

        /**
         * Extensions button on the contextual toolbar - opens the
         * extensions (add-ons) bottom sheet.
         */
        fun onExtensionsClicked()

        /**
         * Long-press on the back button. Only called when the button is
         * actually showing the back glyph - on the homepage the back slot
         * is the bookmarks button and the long-press is swallowed.
         */
        fun onBackLongClicked() {}

        /** Long-press on the forward button. */
        fun onForwardLongClicked() {}
    }

    /**
     * Immutable snapshot of the quick-action row. The extensions and menu slots are
     * visible in every context (they always were), so they are not part of the state.
     */
    private data class ToolbarState(
        @DrawableRes val backIconRes: Int = R.drawable.ic_ios_back,
        val backVisible: Boolean = true,
        val backEnabled: Boolean = true,
        val isBookmarkSlot: Boolean = false,
        val forwardVisible: Boolean = false,
        val forwardEnabled: Boolean = false,
        val shareVisible: Boolean = true,
        val searchVisible: Boolean = false,
        val newTabVisible: Boolean = false
    )

    private var listenerState by mutableStateOf<ContextualToolbarListener?>(null)

    var listener: ContextualToolbarListener?
        get() = listenerState
        set(value) {
            listenerState = value
        }

    private var state by mutableStateOf(ToolbarState())

    /** Toolbar icon-size preference, applied once at creation like the old button sizing. */
    private val iconScale =
        UserPreferences(context).toolbarIconSize.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }

    init {
        // Use unified ThemeManager helper (supports AMOLED + Material 3)
        setBackgroundColor(
            com.prirai.android.nira.theme.ThemeManager.getToolbarBackgroundColor(
                context,
                useElevation = true,
                elevationDp = 3f
            )
        )

        // Remove any elevation/shadow that creates gradient effect
        elevation = 0f
        outlineProvider = null

        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        composeView.setContent {
            NiraTheme(amoledMode = UserPreferences(context).amoledMode) {
                ContextualToolbarRow()
            }
        }
    }

    fun updateForContext(
        tab: TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isHomepage: Boolean
    ) {
        val showContextualToolbar =
            com.prirai.android.nira.preferences.UserPreferences(context).showContextualToolbar

        if (!showContextualToolbar) {
            this.visibility = GONE
            return
        }

        when {
            isHomepage -> showHomepageContext(canGoForward)
            canGoForward -> showFullNavigationContext()
            tab != null && !isHomepage -> showWebsiteContext(canGoBack)
            else -> showDefaultContext()
        }
    }

    private fun showHomepageContext(canGoForward: Boolean = false) {
        // Only the homepage context forces the toolbar visible; the other
        // contexts leave the current View visibility untouched (as before).
        this.visibility = VISIBLE

        state = state.copy(
            backIconRes = MozacIcons.drawable.mozac_ic_bookmark_24,
            backVisible = true,
            backEnabled = true,
            isBookmarkSlot = true,
            forwardVisible = true,
            forwardEnabled = canGoForward,
            shareVisible = false,
            searchVisible = true,
            newTabVisible = false
        )
    }

    private fun showWebsiteContext(canGoBack: Boolean) {
        state = state.copy(
            backIconRes = MozacIcons.drawable.mozac_ic_back_24,
            backVisible = true,
            backEnabled = canGoBack,
            isBookmarkSlot = false,
            forwardVisible = false,
            shareVisible = true,
            searchVisible = false,
            newTabVisible = true
        )
    }

    private fun showFullNavigationContext() {
        state = state.copy(
            backIconRes = MozacIcons.drawable.mozac_ic_back_24,
            backVisible = true,
            backEnabled = true,
            isBookmarkSlot = false,
            forwardVisible = true,
            forwardEnabled = true,
            shareVisible = false,
            searchVisible = false,
            newTabVisible = true
        )
    }

    private fun showDefaultContext() {
        state = state.copy(
            backIconRes = MozacIcons.drawable.mozac_ic_back_24,
            backVisible = true,
            backEnabled = true,
            isBookmarkSlot = false,
            forwardVisible = false,
            shareVisible = true,
            searchVisible = false,
            newTabVisible = true
        )
    }

    @Composable
    private fun ContextualToolbarRow() {
        val view = LocalView.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.backVisible) {
                ContextualToolbarButton(
                    iconRes = state.backIconRes,
                    contentDescription = stringResource(
                        if (state.isBookmarkSlot) {
                            R.string.contextual_toolbar_bookmarks
                        } else {
                            R.string.back
                        }
                    ),
                    enabled = state.backEnabled,
                    onClick = {
                        if (state.isBookmarkSlot) {
                            listener?.onBookmarksClicked()
                        } else {
                            listener?.onBackClicked()
                        }
                    },
                    onLongClick = {
                        // Long-press opens the tab-history sheet (Chrome/Fenix
                        // style). Swallowed when the back slot is showing the
                        // bookmark glyph (homepage context) - that button has
                        // no history to show.
                        if (!state.isBookmarkSlot) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            listener?.onBackLongClicked()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.forwardVisible) {
                ContextualToolbarButton(
                    iconRes = MozacIcons.drawable.mozac_ic_forward_24,
                    contentDescription = stringResource(R.string.forward),
                    enabled = state.forwardEnabled,
                    onClick = { listener?.onForwardClicked() },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        listener?.onForwardLongClicked()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.shareVisible) {
                ContextualToolbarButton(
                    iconRes = R.drawable.ios_share_24,
                    contentDescription = stringResource(R.string.share),
                    enabled = true,
                    onClick = { listener?.onShareClicked() },
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.searchVisible) {
                ContextualToolbarButton(
                    iconRes = R.drawable.ic_ios_search,
                    contentDescription = stringResource(R.string.search),
                    enabled = true,
                    onClick = { listener?.onSearchClicked() },
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.newTabVisible) {
                ContextualToolbarButton(
                    iconRes = R.drawable.ic_ios_add,
                    contentDescription = stringResource(R.string.new_tab),
                    enabled = true,
                    onClick = { listener?.onNewTabClicked() },
                    modifier = Modifier.weight(1f)
                )
            }

            ContextualToolbarButton(
                iconRes = R.drawable.mozac_ic_extension_24,
                contentDescription = stringResource(R.string.extensions),
                enabled = true,
                onClick = { listener?.onExtensionsClicked() },
                modifier = Modifier.weight(1f)
            )

            MenuButtonSlot(modifier = Modifier.weight(1f))
        }
    }

    /**
     * One quick-action slot. Built on combinedClickable rather than the M3 IconButton
     * widget because material3 1.5.0-alpha26 has no long-press overload, and the
     * back/forward slots need long-press for the tab-history sheet. Visuals match
     * IconButton: circular bounded ripple, onSurface token, and the disabled slot
     * dims to the same 40% alpha the old ImageButton row used.
     */
    @Composable
    private fun ContextualToolbarButton(
        @DrawableRes iconRes: Int,
        contentDescription: String,
        enabled: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val buttonHeight = 48.dp * iconScale
        // The old ImageButton drew the glyph fitCenter inside a 48dp box with a
        // 10dp inset, both scaled by the toolbar icon-size preference.
        val glyphSize = buttonHeight - (20.dp / iconScale)

        Box(
            modifier = modifier
                .height(buttonHeight)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .pressScale(interactionSource)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(glyphSize)
            )
        }
    }

    /**
     * The menu slot stays a real [ImageButton] with id `menu_button`:
     * BrowserFragment.showBrowserMenu() looks that id up (as an ImageButton) to
     * anchor the browser menu popup above the toolbar, so the button itself cannot
     * become a Compose node. It is hosted inside the Compose row through AndroidView
     * interop and styled to match its Compose siblings.
     */
    @Composable
    private fun MenuButtonSlot(modifier: Modifier = Modifier) {
        val buttonHeight = 48.dp * iconScale

        AndroidView(
            modifier = modifier.height(buttonHeight),
            factory = { viewContext ->
                ImageButton(viewContext).apply {
                    id = R.id.menu_button
                    contentDescription = context.getString(R.string.menu)

                    val outValue = TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless,
                        outValue,
                        true
                    )
                    setBackgroundResource(outValue.resourceId)

                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    setImageResource(R.drawable.ic_ios_menu)
                    imageTintList = ColorStateList.valueOf(
                        MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorOnSurface,
                            android.graphics.Color.BLACK
                        )
                    )

                    val inset = (10f / iconScale * resources.displayMetrics.density).toInt()
                    setPadding(inset, inset, inset, inset)

                    setOnClickListener { listener?.onMenuClicked() }
                }
            }
        )
    }

    companion object {
        private const val DISABLED_ALPHA = 0.4f
        private const val MIN_ICON_SCALE = 0.5f
        private const val MAX_ICON_SCALE = 2f
    }
}
