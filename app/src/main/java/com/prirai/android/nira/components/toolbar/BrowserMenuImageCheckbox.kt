package com.prirai.android.nira.components.toolbar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import mozilla.components.browser.menu.BrowserMenu
import mozilla.components.browser.menu.BrowserMenuItem
import com.prirai.android.nira.R

class BrowserMenuImageCheckbox(
    private val imageResource: Int,
    private val label: String,
    private val initialState: () -> Boolean = { false },
    private val listener: (Boolean) -> Unit
) : BrowserMenuItem {

    override var visible: () -> Boolean = { true }

    override fun getLayoutResource() = R.layout.browser_menu_item_checkbox

    override fun bind(menu: BrowserMenu, view: View) {
        val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
        val textView = view.findViewById<TextView>(R.id.label)
        val imageView = view.findViewById<ImageView>(R.id.icon)

        textView.text = label
        imageView.setImageResource(imageResource)
        
        val isChecked = initialState()
        checkbox.isChecked = isChecked

        view.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
            listener(checkbox.isChecked)
            menu.dismiss()
        }
        
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            listener(isChecked)
            menu.dismiss()
        }
    }
}
