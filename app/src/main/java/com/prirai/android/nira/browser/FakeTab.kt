package com.prirai.android.nira.browser

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.concept.base.images.ImageLoadRequest
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.databinding.TabPreviewBinding
import kotlin.math.max

class FakeTab @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val binding = TabPreviewBinding.inflate(LayoutInflater.from(context), this)
    private val thumbnailLoader = ThumbnailLoader(context.components.thumbnailStorage)
    private val preferences = UserPreferences(context)

    init {
        if (preferences.toolbarPosition != ToolbarPosition.BOTTOM.ordinal) {
            binding.fakeToolbar.updateLayoutParams<LayoutParams> {
                gravity = Gravity.TOP
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        binding.previewThumbnail.translationY = if (preferences.shouldUseBottomToolbar) {
            binding.fakeToolbar.height.toFloat()
        } else {
            0f
        }
    }

    fun loadPreviewThumbnail(thumbnailId: String) {
        doOnNextLayout {
            val thumbnailSize = max(binding.previewThumbnail.height, binding.previewThumbnail.width)
            thumbnailLoader.loadIntoView(
                binding.previewThumbnail,
                ImageLoadRequest(thumbnailId, thumbnailSize, false)
            )
        }
    }
}
