package com.prirai.android.nira.browser.tabs.dragdrop

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView

/**
 * Renders visual feedback for drag operations.
 * Separated from logic for testability and reusability.
 */
class DragVisualFeedback {
    
    private val groupHighlightPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4CAF50")
        alpha = 80
    }
    
    private val groupStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#4CAF50")
        alpha = 220
    }
    
    private val tabMergeHighlightPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2196F3")
        alpha = 100
    }
    
    private val tabMergeStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#2196F3")
        alpha = 240
    }
    
    private val reorderLinePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF9800")
    }
    
    private val ungroupOverlayPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF5722")
        alpha = 120
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }
    
    /**
     * Draw green highlight when hovering over a group header
     */
    fun drawGroupHighlight(canvas: Canvas, bounds: RectF) {
        val inset = 4f
        val rect = RectF(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        
        canvas.drawRoundRect(rect, 16f, 16f, groupHighlightPaint)
        canvas.drawRoundRect(rect, 16f, 16f, groupStrokePaint)
    }
    
    /**
     * Draw blue highlight when hovering over a tab to merge
     */
    fun drawTabMergeHighlight(canvas: Canvas, bounds: RectF) {
        val inset = 4f
        val rect = RectF(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        
        canvas.drawRoundRect(rect, 12f, 12f, tabMergeHighlightPaint)
        canvas.drawRoundRect(rect, 12f, 12f, tabMergeStrokePaint)
    }
    
    /**
     * Draw vertical reorder line (list view)
     */
    fun drawReorderLineVertical(canvas: Canvas, recyclerView: RecyclerView, yPosition: Float) {
        val indicatorHeight = 4f
        val inset = 16f
        
        val rect = RectF(
            inset,
            yPosition - indicatorHeight / 2,
            recyclerView.width.toFloat() - inset,
            yPosition + indicatorHeight / 2
        )
        
        canvas.drawRoundRect(rect, indicatorHeight / 2, indicatorHeight / 2, reorderLinePaint)
        
        // Draw end caps
        val capRadius = 8f
        canvas.drawCircle(inset, yPosition, capRadius, reorderLinePaint)
        canvas.drawCircle(recyclerView.width.toFloat() - inset, yPosition, capRadius, reorderLinePaint)
    }
    
    /**
     * Draw horizontal reorder line (grid view)
     */
    fun drawReorderLineHorizontal(canvas: Canvas, recyclerView: RecyclerView, xPosition: Float) {
        val indicatorWidth = 4f
        val inset = 16f
        
        val rect = RectF(
            xPosition - indicatorWidth / 2,
            inset,
            xPosition + indicatorWidth / 2,
            recyclerView.height.toFloat() - inset
        )
        
        canvas.drawRoundRect(rect, indicatorWidth / 2, indicatorWidth / 2, reorderLinePaint)
        
        // Draw end caps
        val capRadius = 8f
        canvas.drawCircle(xPosition, inset, capRadius, reorderLinePaint)
        canvas.drawCircle(xPosition, recyclerView.height.toFloat() - inset, capRadius, reorderLinePaint)
    }
    
    /**
     * Draw red ungroup zone overlay
     */
    fun drawUngroupZone(canvas: Canvas, recyclerView: RecyclerView) {
        val rect = RectF(
            0f,
            0f,
            recyclerView.width.toFloat(),
            recyclerView.height.toFloat()
        )
        
        canvas.drawRect(rect, ungroupOverlayPaint)
        canvas.drawText(
            "Release to Ungroup",
            rect.centerX(),
            rect.centerY(),
            textPaint
        )
    }
    
    /**
     * Render the appropriate visual feedback based on drop target
     */
    fun render(
        canvas: Canvas,
        recyclerView: RecyclerView,
        dropTarget: DropTarget,
        viewHolderInfos: List<ViewHolderInfo>
    ) {
        when (dropTarget) {
            is DropTarget.OnGroup -> {
                val info = viewHolderInfos.find { it.adapterPosition == dropTarget.position }
                info?.let { drawGroupHighlight(canvas, it.bounds) }
            }
            
            is DropTarget.OnTab -> {
                val info = viewHolderInfos.find { it.adapterPosition == dropTarget.position }
                info?.let { drawTabMergeHighlight(canvas, it.bounds) }
            }
            
            is DropTarget.Between -> {
                when {
                    dropTarget.yPosition != null -> {
                        drawReorderLineVertical(canvas, recyclerView, dropTarget.yPosition)
                    }
                    dropTarget.xPosition != null -> {
                        drawReorderLineHorizontal(canvas, recyclerView, dropTarget.xPosition)
                    }
                }
            }
            
            is DropTarget.UngroupZone -> {
                // REMOVED: No longer used, but kept for exhaustive when
            }
            
            is DropTarget.None -> {
                // No visual feedback
            }
        }
    }
}
