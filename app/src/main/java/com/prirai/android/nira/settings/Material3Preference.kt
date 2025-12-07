package com.prirai.android.nira.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.prirai.android.nira.R

/**
 * Material 3 styled preference that dynamically applies rounded corners based on position.
 * 
 * Background is determined by PreferenceGroupPosition tag set by the adapter.
 */
open class Material3Preference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    init {
        layoutResource = R.layout.preference_material3_grouped
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        // Apply background based on position in group
        val position = holder.itemView.tag as? PreferenceGroupPosition
        val backgroundRes = when (position) {
            PreferenceGroupPosition.TOP -> R.drawable.preference_background_top
            PreferenceGroupPosition.MIDDLE -> R.drawable.preference_background_middle
            PreferenceGroupPosition.BOTTOM -> R.drawable.preference_background_bottom
            PreferenceGroupPosition.SINGLE -> R.drawable.preference_background_single
            else -> R.drawable.preference_background_single
        }
        
        holder.itemView.background = ContextCompat.getDrawable(context, backgroundRes)
    }
}

/**
 * Position of a preference within a group
 */
enum class PreferenceGroupPosition {
    TOP,      // First item in group
    MIDDLE,   // Middle item in group
    BOTTOM,   // Last item in group
    SINGLE    // Only item in group
}
