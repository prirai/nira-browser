package com.prirai.android.nira.integration

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.R
import com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.support.ktx.android.content.getColorFromAttr

/**
 * Toolbar browser-action button that toggles [UnifiedToolbar]'s minimal
 * state. Added to the right of the URL pill via
 * [BrowserToolbar.addBrowserAction].
 *
 * Visual contract from the design brief:
 *  - Non-minimal state  -> chevron pointing DOWN  (tap to collapse)
 *  - Minimal state      -> chevron pointing UP    (tap to expand)
 *
 * Icons come from AC's `ui-icons` module (`mozac_ic_chevron_down_24` /
 * `mozac_ic_chevron_up_24`), tinted with the themed primary text color
 * so the glyph matches the other address-bar icons.
 *
 * Pattern mirrors [ReloadStopButtonIntegration]: a
 * [BrowserToolbar.TwoStateButton] whose `isInPrimaryState` lambda reads
 * live state (here [UnifiedToolbar.isMinimal]) and that calls
 * [BrowserToolbar.invalidateActions] whenever that state flips, so the
 * glyph swap is immediate.
 *
 * Lifecycle: implements [DefaultLifecycleObserver] so the caller can
 * do `viewLifecycleOwner.lifecycle.addObserver(this)` and the
 * minimal-state listener is torn down on `onDestroy`. The browser
 * action itself is added once on construction and lives for as long
 * as the toolbar does.
 */
class MinimalStateToggleIntegration(
    private val context: Context,
    private val toolbar: BrowserToolbar,
    private val unifiedToolbar: UnifiedToolbar,
) : DefaultLifecycleObserver {

    private val toggleButton = BrowserToolbar.TwoStateButton(
        primaryImage = ContextCompat.getDrawable(
            context,
            mozilla.components.ui.icons.R.drawable.mozac_ic_chevron_down_24,
        )!!.mutate().apply {
            setTint(context.getColorFromAttr(android.R.attr.textColorPrimary))
        },
        primaryContentDescription = context.getString(R.string.minimal_state_collapse),
        secondaryImage = ContextCompat.getDrawable(
            context,
            mozilla.components.ui.icons.R.drawable.mozac_ic_chevron_up_24,
        )!!.mutate().apply {
            setTint(context.getColorFromAttr(android.R.attr.textColorPrimary))
        },
        secondaryContentDescription = context.getString(R.string.minimal_state_expand),
        // Primary state = non-minimal (down chevron shown).
        isInPrimaryState = { !unifiedToolbar.isMinimal() },
        disableInSecondaryState = false,
    ) {
        if (unifiedToolbar.isMinimal()) {
            unifiedToolbar.exitMinimalState()
        } else {
            unifiedToolbar.enterMinimalState()
        }
        // invalidateActions re-queries isInPrimaryState and swaps the
        // glyph; without this the icon would stay stale until the next
        // scroll / toolbar event forced a redraw.
        toolbar.invalidateActions()
    }

    init {
        toolbar.addBrowserAction(toggleButton)
    }

    /**
     * Call from the fragment's own `setOnMinimalStateChangedListener`
     * dispatch so the toggle glyph swaps when minimal state changes
     * from a source OTHER than this button (page scroll, programmatic
     * calls, etc.). The listener on [UnifiedToolbar] is a single-slot
     * setter, so composing it at the fragment side avoids clobbering
     * the fragment's existing listener that updates web content
     * positioning.
     */
    fun onMinimalStateChanged() {
        toolbar.invalidateActions()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // The toolbar's browser-action list is cleared automatically
        // when the view is destroyed, so no explicit `removeBrowserAction`
        // is needed.
    }
}
