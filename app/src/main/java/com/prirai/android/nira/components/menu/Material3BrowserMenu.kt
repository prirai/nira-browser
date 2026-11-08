@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.components.menu

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.prirai.android.nira.R
import com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlin.math.roundToInt

/**
 * Custom Material 3 Expressive browser menu. The content is rendered with the
 * app's unified Compose stack ([NiraTheme]); it does not use the Mozilla
 * Components menu system.
 *
 * Hosted as an overlay attached to the activity's content view (the same
 * pattern as the settings/profile Compose dialogs), NOT a [PopupWindow]: a
 * popup's decor view has no ViewTree lifecycle owners, so a ComposeView
 * inside one crashes with "ViewTreeLifecycleOwner not found" when it tries
 * to create its window recomposer. Attached to the activity content, the
 * ComposeView inherits the full view-tree owners for free.
 *
 * The overlay keeps the anchoring, outside-dismiss (full-screen scrim) and
 * back-press behaviour (intercepted while shown) of the previous
 * View-based implementation: absolute screen coordinates computed from
 * the anchor, never scrollable, 280dp wide.
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
            val forwardEnabled: Boolean = true,
            /** Optional long-press affordance: opens the tab-history popup. */
            val onBackLongClick: (() -> Unit)? = null,
            val onForwardLongClick: (() -> Unit)? = null
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
    private var overlayViews: MutableList<View> = mutableListOf()
    private var backCallback: OnBackPressedCallback? = null

    fun show(anchor: View, preferBottom: Boolean = true) {
        val visibleItems = items.filter { item ->
            when (item) {
                is MenuItem.Action -> item.visible
                is MenuItem.Toggle -> item.visible
                is MenuItem.ToolbarRow -> true
                is MenuItem.PillRow -> true
                is MenuItem.QuadRow -> true
                is MenuItem.IconRow -> true
                is MenuItem.Divider -> true
            }
        }

        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val menuWidthPx = (MENU_WIDTH_DP * density).toInt()
        // Every row renders at a fixed height, so this dp sum is the natural
        // content height. It only positions the popup; the window itself wraps
        // the composed content, so the menu is never clipped or scrollable.
        val menuHeightPx = (visibleItems.menuHeightDp().value * density).roundToInt()

        val prefs = UserPreferences(context)
        val darkTheme = ThemeManager.isDarkMode(context)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                NiraTheme(
                    darkTheme = darkTheme,
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors,
                ) {
                    BrowserMenuContent(
                        items = visibleItems,
                        onDismiss = ::dismiss,
                    )
                }
            }
        }

        val screenHeight = displayMetrics.heightPixels
        val gap = (8 * density).toInt()
        val screenPadding = (16 * density).toInt()

        // Get anchor screen position
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorY = location[1]

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
            anchorY - menuHeightPx - gap
        } else {
            // Show below - position below anchor
            anchorY + anchor.height + gap
        }

        // Calculate X position - align right edge of menu with right edge of screen minus padding
        val xPos = displayMetrics.widthPixels - menuWidthPx - screenPadding

        // Ensure menu stays on screen
        val finalX = xPos.coerceIn(screenPadding, displayMetrics.widthPixels - menuWidthPx - screenPadding)
        val finalY = yPos.coerceIn(screenPadding, screenHeight - menuHeightPx - screenPadding)

        // Host in the activity's content view: the ComposeView inherits the
        // view-tree lifecycle owners (a PopupWindow's decor view has none,
        // which crashed composition). A full-screen scrim dismisses on
        // outside taps; an OnBackPressedCallback intercepts back while shown.
        val activity = anchor.context.let { ctx ->
            var current: android.content.Context? = ctx
            while (current is android.content.ContextWrapper) {
                if (current is androidx.fragment.app.FragmentActivity) return@let current
                current = current.baseContext
            }
            null
        } ?: run {
            // No activity host (unit-test context): fall back to a plain
            // popup so the API still behaves.
            popupWindow = PopupWindow(
                composeView,
                menuWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                showAtLocation(anchor, Gravity.NO_GRAVITY, finalX, finalY)
            }
            return
        }

        val content = activity.window.decorView
            .findViewById(android.R.id.content) as? ViewGroup ?: return

        // Screen coordinates -> content-view-local coordinates.
        val contentOrigin = IntArray(2)
        content.getLocationOnScreen(contentOrigin)
        val localX = finalX - contentOrigin[0]
        val localY = finalY - contentOrigin[1]

        val scrim = View(context).apply {
            setOnClickListener { dismiss() }
        }
        val scrimParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        content.addView(scrim, scrimParams)

        val menuParams = FrameLayout.LayoutParams(menuWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = localX
            topMargin = localY
        }
        content.addView(composeView, menuParams)

        overlayViews.addAll(listOf(scrim, composeView))

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss()
            }
        }.also { activity.onBackPressedDispatcher.addCallback(it) }
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
        backCallback?.remove()
        backCallback = null
        overlayViews.forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlayViews.clear()
    }
}

// ============================================================================
// Menu content (Compose)
// ============================================================================

/**
 * The menu card: one [Surface] in the theme's surface-container-high role, the
 * same container idiom as [com.prirai.android.nira.ui.components.NiraCardRow].
 * One composable per legacy view-holder type, each at a fixed height so the
 * popup position math in [Material3BrowserMenu.show] stays exact.
 */
@Composable
private fun BrowserMenuContent(items: List<MenuItem>, onDismiss: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.width(MENU_WIDTH_DP.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .padding(top = MENU_TOP_PADDING),
        ) {
            items.forEach { item ->
                when (item) {
                    is MenuItem.Action -> MenuActionRow(item, onDismiss)
                    is MenuItem.Toggle -> MenuToggleRow(item, onDismiss)
                    is MenuItem.ToolbarRow -> MenuToolbarRow(item, onDismiss)
                    is MenuItem.PillRow -> MenuPillRow(item, onDismiss)
                    is MenuItem.QuadRow -> MenuQuadRow(item, onDismiss)
                    is MenuItem.IconRow -> MenuIconRow(item, onDismiss)
                    is MenuItem.Divider -> HorizontalDivider(
                        thickness = DIVIDER_THICKNESS,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(item: MenuItem.Action, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_ROW_HEIGHT)
            .alpha(if (item.enabled) 1f else DISABLED_ALPHA)
            .then(
                if (item.enabled) {
                    Modifier.clickable {
                        item.onClick()
                        onDismiss()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MenuToggleRow(item: MenuItem.Toggle, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_ROW_HEIGHT)
            .alpha(if (item.enabled) 1f else DISABLED_ALPHA)
            .toggleable(
                value = item.isChecked,
                role = Role.Switch,
                enabled = item.enabled,
                onValueChange = { checked ->
                    item.onToggle(checked)
                    onDismiss()
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Display-only: the row's toggleable owns the interaction, so the
        // whole row (switch included) toggles and dismisses together.
        Switch(checked = item.isChecked, onCheckedChange = null)
    }
}

@Composable
private fun MenuToolbarRow(item: MenuItem.ToolbarRow, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOOLBAR_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuToolbarButton(
            iconRes = R.drawable.ic_ios_back,
            contentDescription = stringResource(R.string.mozac_feature_toolbar_back_button),
            enabled = item.backEnabled,
            onClick = {
                item.onBackClick()
                onDismiss()
            },
            onLongClick = item.onBackLongClick?.let { longClick -> { longClick(); onDismiss() } },
            modifier = Modifier.weight(1f),
        )
        MenuToolbarButton(
            iconRes = R.drawable.ic_ios_forward,
            contentDescription = stringResource(R.string.mozac_feature_toolbar_forward_button),
            enabled = item.forwardEnabled,
            onClick = {
                item.onForwardClick()
                onDismiss()
            },
            onLongClick = item.onForwardLongClick?.let { longClick -> { longClick(); onDismiss() } },
            modifier = Modifier.weight(1f),
        )
        MenuToolbarButton(
            iconRes = R.drawable.ic_refresh,
            contentDescription = stringResource(R.string.refresh),
            enabled = true,
            onClick = {
                item.onReloadClick()
                onDismiss()
            },
            modifier = Modifier.weight(1f),
        )
        MenuToolbarButton(
            iconRes = R.drawable.ios_share_24,
            contentDescription = stringResource(R.string.share),
            enabled = true,
            onClick = {
                item.onShareClick()
                onDismiss()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MenuToolbarButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(TOOLBAR_BUTTON_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(
                if (enabled) {
                    // combinedClickable performs the long-press haptic itself.
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun MenuPillRow(item: MenuItem.PillRow, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = PILL_ROW_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuPillChip(
            title = item.title1,
            iconRes = item.icon1,
            onClick = {
                item.onClick1()
                onDismiss()
            },
            modifier = Modifier.weight(1f),
        )
        MenuPillChip(
            title = item.title2,
            iconRes = item.icon2,
            onClick = {
                item.onClick2()
                onDismiss()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MenuPillChip(title: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        modifier = modifier.height(PILL_CHIP_HEIGHT),
    )
}

@Composable
private fun MenuQuadRow(item: MenuItem.QuadRow, onDismiss: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = QUAD_ROW_VERTICAL_PADDING),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.clip(shape)) {
            Row(modifier = Modifier.height(QUAD_CELL_HEIGHT)) {
                MenuQuadCell(
                    title = item.title1,
                    iconRes = item.icon1,
                    onClick = { item.onClick1(); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
                MenuQuadCellDivider()
                MenuQuadCell(
                    title = item.title2,
                    iconRes = item.icon2,
                    onClick = { item.onClick2(); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                thickness = DIVIDER_THICKNESS,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(modifier = Modifier.height(QUAD_CELL_HEIGHT)) {
                MenuQuadCell(
                    title = item.title3,
                    iconRes = item.icon3,
                    onClick = { item.onClick3(); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
                MenuQuadCellDivider()
                MenuQuadCell(
                    title = item.title4,
                    iconRes = item.icon4,
                    onClick = { item.onClick4(); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MenuQuadCellDivider() {
    VerticalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = DIVIDER_THICKNESS,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun MenuQuadCell(title: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun MenuIconRow(row: MenuItem.IconRow, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = ICON_ROW_VERTICAL_PADDING),
    ) {
        repeat(ICON_ROW_SLOT_COUNT) { slot ->
            val rowItem = row.items.getOrNull(slot)
            if (rowItem == null) {
                // The legacy layout kept unused slots invisible but laid out,
                // preserving the equal-width grid.
                Spacer(modifier = Modifier.weight(1f).height(ICON_CELL_HEIGHT))
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(ICON_CELL_HEIGHT)
                        .clickable {
                            rowItem.onClick()
                            onDismiss()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(rowItem.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = rowItem.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

// ============================================================================
// Height accounting
// ============================================================================

/**
 * The natural height of the composed menu, mirrored from the fixed row heights
 * above so [Material3BrowserMenu.show] can position the popup before the
 * content is composed. Keep in sync with the row composables.
 */
private fun List<MenuItem>.menuHeightDp(): Dp =
    MENU_TOP_PADDING + fold(0.dp) { acc, item ->
        acc + when (item) {
            is MenuItem.Action, is MenuItem.Toggle -> ACTION_ROW_HEIGHT
            is MenuItem.ToolbarRow -> TOOLBAR_ROW_HEIGHT
            is MenuItem.PillRow -> PILL_ROW_VERTICAL_PADDING * 2 + PILL_CHIP_HEIGHT
            is MenuItem.QuadRow -> QUAD_ROW_VERTICAL_PADDING * 2 + QUAD_CELL_HEIGHT * 2 + DIVIDER_THICKNESS
            is MenuItem.IconRow -> ICON_ROW_VERTICAL_PADDING * 2 + ICON_CELL_HEIGHT
            is MenuItem.Divider -> DIVIDER_THICKNESS
        }
    }

private const val MENU_WIDTH_DP = 280
private const val ICON_ROW_SLOT_COUNT = 3
private const val DISABLED_ALPHA = 0.5f

private val MENU_TOP_PADDING = 8.dp
private val ACTION_ROW_HEIGHT = 48.dp
private val TOOLBAR_ROW_HEIGHT = 56.dp
private val TOOLBAR_BUTTON_HEIGHT = 48.dp
private val PILL_ROW_VERTICAL_PADDING = 4.dp
private val PILL_CHIP_HEIGHT = 48.dp
private val QUAD_ROW_VERTICAL_PADDING = 4.dp
private val QUAD_CELL_HEIGHT = 56.dp
private val ICON_ROW_VERTICAL_PADDING = 4.dp
private val ICON_CELL_HEIGHT = 52.dp
private val DIVIDER_THICKNESS = 1.dp
