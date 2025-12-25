package com.prirai.android.nira.onboarding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var pageCount = 0
    private var currentPage = 0
    
    private val dotRadius = 4f.dpToPx()
    private val activeDotWidth = 24f.dpToPx()
    private val activeDotHeight = 8f.dpToPx()
    private val dotSpacing = 8f.dpToPx()
    
    init {
        inactivePaint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
        inactivePaint.style = Paint.Style.FILL
        
        activePaint.color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        activePaint.style = Paint.Style.FILL
    }
    
    fun setPageCount(count: Int) {
        pageCount = count
        requestLayout()
        invalidate()
    }
    
    fun setCurrentPage(page: Int) {
        if (currentPage != page) {
            currentPage = page
            invalidate()
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = ((pageCount - 1) * (dotRadius * 2 + dotSpacing) + activeDotWidth).toInt()
        val height = activeDotHeight.toInt()
        
        setMeasuredDimension(totalWidth, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (pageCount == 0) return
        
        val centerY = height / 2f
        var currentX = 0f
        
        for (i in 0 until pageCount) {
            if (i == currentPage) {
                // Draw active indicator (elongated pill)
                val left = currentX
                val top = centerY - activeDotHeight / 2
                val right = currentX + activeDotWidth
                val bottom = centerY + activeDotHeight / 2
                canvas.drawRoundRect(left, top, right, bottom, activeDotHeight / 2, activeDotHeight / 2, activePaint)
                currentX += activeDotWidth + dotSpacing
            } else {
                // Draw inactive indicator (circle)
                canvas.drawCircle(currentX + dotRadius, centerY, dotRadius, inactivePaint)
                currentX += dotRadius * 2 + dotSpacing
            }
        }
    }
    
    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }
}
