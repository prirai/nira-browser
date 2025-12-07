package com.prirai.android.nira.settings

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemDecoration for Material 3 styled preference groups.
 * 
 * Creates grouped appearance with:
 * - Top item: top + left + right margins, top rounding
 * - Middle items: left + right margins only, no rounding
 * - Bottom item: bottom + left + right margins, bottom rounding
 */
class Material3PreferenceItemDecoration(
    private val horizontalMargin: Int,
    private val verticalMargin: Int,
    private val groupSpacing: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val adapter = parent.adapter ?: return
        val itemCount = adapter.itemCount

        // Determine if this is a category header
        val isCategory = view.tag == "preference_category"

        if (isCategory) {
            // Category headers: only top margin for spacing
            outRect.top = groupSpacing
            outRect.left = 0
            outRect.right = 0
            outRect.bottom = 0
            return
        }

        // Find group boundaries (preferences between categories)
        val isFirstInGroup = isFirstInGroup(position, adapter)
        val isLastInGroup = isLastInGroup(position, adapter, itemCount)

        // Apply margins based on position in group
        outRect.left = horizontalMargin
        outRect.right = horizontalMargin

        when {
            isFirstInGroup && isLastInGroup -> {
                // Single item in group
                outRect.top = verticalMargin
                outRect.bottom = verticalMargin
            }
            isFirstInGroup -> {
                // First in group
                outRect.top = verticalMargin
                outRect.bottom = 0
            }
            isLastInGroup -> {
                // Last in group
                outRect.top = 0
                outRect.bottom = verticalMargin
            }
            else -> {
                // Middle item
                outRect.top = 0
                outRect.bottom = 0
            }
        }
    }

    private fun isFirstInGroup(position: Int, adapter: RecyclerView.Adapter<*>): Boolean {
        if (position == 0) return true
        
        // Check if previous item is a category
        return try {
            val prevType = adapter.getItemViewType(position - 1)
            prevType == CATEGORY_TYPE
        } catch (e: Exception) {
            false
        }
    }

    private fun isLastInGroup(position: Int, adapter: RecyclerView.Adapter<*>, itemCount: Int): Boolean {
        if (position == itemCount - 1) return true
        
        // Check if next item is a category
        return try {
            val nextType = adapter.getItemViewType(position + 1)
            nextType == CATEGORY_TYPE
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        // PreferenceGroupAdapter uses these view types
        private const val CATEGORY_TYPE = 0 // TYPE_PREFERENCE_CATEGORY
    }
}
