package com.prirai.android.nira.components.toolbar

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.R as MaterialR
import mozilla.components.browser.menu.item.BrowserMenuImageText

/**
 * Themed version of BrowserMenuImageText that uses Material 3 colors.
 * 
 * This wrapper applies colorOnSurface from Material 3 theme to menu icons and text,
 * ensuring proper theming in light/dark modes.
 */
class ThemedBrowserMenuImageText(
    label: String,
    @DrawableRes imageResource: Int,
    listener: () -> Unit = {}
) : BrowserMenuImageText(
    label,
    imageResource,
    iconTintColorResource = android.R.color.transparent, // We'll apply color programmatically
    listener = listener
) {
    
    override fun bind(menu: mozilla.components.browser.menu.BrowserMenu, view: View) {
        super.bind(menu, view)
        
        // Apply Material 3 colorOnSurface to icon and text
        val context = view.context
        val m3Color = resolveM3Color(context)
        
        // Find and tint the icon
        val iconView = view.findViewById<ImageView>(
            mozilla.components.browser.menu.R.id.image
        )
        iconView?.setColorFilter(m3Color)
        
        // Find and color the text
        val textView = view.findViewById<TextView>(
            mozilla.components.browser.menu.R.id.text
        )
        textView?.setTextColor(m3Color)
    }
    
    private fun resolveM3Color(context: Context): Int {
        val typedValue = android.util.TypedValue()
        val resolved = context.theme.resolveAttribute(
            MaterialR.attr.colorOnSurface,
            typedValue,
            true
        )
        
        return if (resolved && typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else if (resolved) {
            typedValue.data
        } else {
            // Fallback to black
            ContextCompat.getColor(context, android.R.color.black)
        }
    }
}
