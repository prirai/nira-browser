package com.prirai.android.nira.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.prirai.android.nira.R
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.preferences.UserPreferences

class OnboardingCustomizationFragment : Fragment() {
    
    private lateinit var userPreferences: UserPreferences
    private val previewIcons = mutableListOf<ImageView>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_customization, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userPreferences = UserPreferences(requireContext())
        
        val topToolbarCard: MaterialCardView = view.findViewById(R.id.topToolbarCard)
        val bottomToolbarCard: MaterialCardView = view.findViewById(R.id.bottomToolbarCard)
        val iconSizeSlider: Slider = view.findViewById(R.id.iconSizeSlider)
        
        // Collect preview icons
        previewIcons.add(view.findViewById(R.id.previewIcon1))
        previewIcons.add(view.findViewById(R.id.previewIcon2))
        previewIcons.add(view.findViewById(R.id.previewIcon3))
        previewIcons.add(view.findViewById(R.id.previewIcon4))
        previewIcons.add(view.findViewById(R.id.previewIcon5))
        
        updateToolbarSelection()
        
        topToolbarCard.setOnClickListener {
            userPreferences.toolbarPosition = ToolbarPosition.TOP.ordinal
            updateToolbarSelection()
        }
        
        bottomToolbarCard.setOnClickListener {
            userPreferences.toolbarPosition = ToolbarPosition.BOTTOM.ordinal
            updateToolbarSelection()
        }
        
        iconSizeSlider.value = userPreferences.toolbarIconSize
        updateIconPreview(userPreferences.toolbarIconSize)
        
        iconSizeSlider.addOnChangeListener { _, value, _ ->
            userPreferences.toolbarIconSize = value
            updateIconPreview(value)
        }
    }
    
    private fun updateToolbarSelection() {
        val topToolbarCard: MaterialCardView = requireView().findViewById(R.id.topToolbarCard)
        val bottomToolbarCard: MaterialCardView = requireView().findViewById(R.id.bottomToolbarCard)
        
        topToolbarCard.isChecked = userPreferences.toolbarPosition == ToolbarPosition.TOP.ordinal
        bottomToolbarCard.isChecked = userPreferences.toolbarPosition == ToolbarPosition.BOTTOM.ordinal
    }
    
    private fun updateIconPreview(scale: Float) {
        val baseSize = (24 * resources.displayMetrics.density).toInt()
        val scaledSize = (baseSize * scale).toInt()
        
        previewIcons.forEach { icon ->
            val layoutParams = icon.layoutParams
            layoutParams.width = scaledSize
            layoutParams.height = scaledSize
            icon.layoutParams = layoutParams
        }
    }
}
