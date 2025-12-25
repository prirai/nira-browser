package com.prirai.android.nira.settings.fragment

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R

abstract class BaseSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the correct SharedPreferences file name to match UserPreferences
        preferenceManager.sharedPreferencesName = "scw_preferences"
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Apply iOS-style grouped styling with custom dividers
        listView.apply {
            // Disable default divider
            setDivider(null)
            setDividerHeight(0)
            
            // Add custom item decoration for grouped styling
            addItemDecoration(GroupedPreferenceItemDecoration())
        }
    }
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Override in subclasses
    }

    /**
     * Custom ItemDecoration for iOS-style grouped preferences with:
     * - Top item: top margin + top rounding
     * - Middle items: dividers limited to content area
     * - Bottom item: bottom margin + bottom rounding
     */
    private inner class GroupedPreferenceItemDecoration : RecyclerView.ItemDecoration() {
        
        private val dividerPaint = Paint().apply {
            color = ContextCompat.getColor(requireContext(), R.color.preference_divider)
            strokeWidth = 1f
        }
        
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            
            val adapter = parent.adapter ?: return
            
            // Check if this is a category
            val isCategory = adapter.getItemViewType(position) == 0 // PreferenceCategory type
            
            if (isCategory) {
                // Categories get spacing above them, but not for the first category
                outRect.top = if (position > 0) (16 * resources.displayMetrics.density).toInt() else 0
                return
            }
            
            // Determine position in group
            val isFirstInGroup = position == 0 || adapter.getItemViewType(position - 1) == 0
            val isLastInGroup = position == adapter.itemCount - 1 || 
                                (position < adapter.itemCount - 1 && adapter.getItemViewType(position + 1) == 0)
            
            // Horizontal margins for all items
            val horizontalMargin = (16 * resources.displayMetrics.density).toInt()
            outRect.left = horizontalMargin
            outRect.right = horizontalMargin
            
            // Vertical margins only for first and last in group
            // But for the very first item, don't add top margin
            val verticalMargin = (8 * resources.displayMetrics.density).toInt()
            outRect.top = if (isFirstInGroup && position > 0) verticalMargin else 0
            outRect.bottom = if (isLastInGroup) verticalMargin else 0
            
            // Apply background based on position
            applyBackground(view, isFirstInGroup, isLastInGroup)
        }
        
        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            // Does nothing
        }

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val adapter = parent.adapter ?: return
            
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                
                if (position == RecyclerView.NO_POSITION) continue

                // Do not draw a divider for the last item in the adapter
                if (position == adapter.itemCount - 1) continue
                
                val isCurrentCategory = adapter.getItemViewType(position) == 0
                val isNextCategory = adapter.getItemViewType(position + 1) == 0

                // Do not draw a divider for categories, or if the next item is a category
                if (isCurrentCategory || isNextCategory) continue
                
                val dividerLeft = child.left.toFloat()
                val dividerRight = child.right.toFloat()
                val dividerY = child.bottom.toFloat()
                
                canvas.drawLine(dividerLeft, dividerY, dividerRight, dividerY, dividerPaint)
            }
        }
        
        private fun applyBackground(view: View, isFirst: Boolean, isLast: Boolean) {
            val backgroundRes = when {
                isFirst && isLast -> R.drawable.preference_background_single
                isFirst -> R.drawable.preference_background_top
                isLast -> R.drawable.preference_background_bottom
                else -> R.drawable.preference_background_middle
            }
            view.setBackgroundResource(backgroundRes)
        }
    }

    protected fun switchPreference(
        preference: String,
        isChecked: Boolean,
        isEnabled: Boolean = true,
        summary: String? = null,
        onCheckChange: (Boolean) -> Unit
    ) = findPreference<com.prirai.android.nira.settings.MaterialSwitchPreference>(preference)?.apply {
        // Force the preference to match the actual stored value from UserPreferences
        // This handles cases where the preference might not have been saved yet
        // or where there's computed/inverted logic
        val prefs = preferenceManager.sharedPreferences
        if (prefs != null) {
            val currentValue = prefs.getBoolean(preference, !isChecked) // Use opposite as default to detect mismatch
            if (currentValue != isChecked) {
                // Value doesn't match, update SharedPreferences
                prefs.edit().putBoolean(preference, isChecked).apply()
            }
        }
        
        // The preference will now read the correct value from SharedPreferences
        // Force a refresh by re-reading from persistent storage
        this.isChecked = isChecked
        
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        onPreferenceChangeListener = OnPreferenceChangeListener { _, any: Any ->
            onCheckChange(any as Boolean)
            true
        }
    }

    protected fun clickablePreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: () -> Unit
    ) = clickableDynamicPreference(
        preference = preference,
        isEnabled = isEnabled,
        summary = summary,
        onClick = { onClick() }
    )

    protected fun clickableDynamicPreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: (SummaryUpdater) -> Unit
    ) = findPreference<Preference>(preference)?.apply {
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        val summaryUpdate = SummaryUpdater(this)
        onPreferenceClickListener = OnPreferenceClickListener {
            onClick(summaryUpdate)
            true
        }
    }

    protected fun seekbarPreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onStateChanged: (Int) -> Unit
    ) = findPreference<androidx.preference.SeekBarPreference>(preference)?.apply {
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        SummaryUpdater(this)
        onPreferenceChangeListener = OnPreferenceChangeListener { preference: Preference, newValue: Any ->
            onStateChanged(newValue as Int)
            true
        }
    }

}