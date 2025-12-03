package com.prirai.android.nira.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

/**
 * A SwitchPreference that properly binds to Material 3 MaterialSwitch widget.
 * This fixes the issue where MaterialSwitch doesn't update its visual state
 * when the preference value changes.
 */
class MaterialSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        // Find the switch widget and ensure it's properly synced
        val switchView = holder.findViewById(android.R.id.switch_widget)
        if (switchView is Checkable) {
            switchView.isChecked = isChecked
            
            // Disable clickable on the switch itself since the preference handles clicks
            switchView.isClickable = false
            switchView.isFocusable = false
        }
    }
}
