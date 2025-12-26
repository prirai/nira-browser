package com.prirai.android.nira.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.ReaderAppearanceBottomSheetBinding
import mozilla.components.feature.readerview.ReaderViewFeature

class ReaderAppearanceBottomSheet(
    private val readerViewFeature: ReaderViewFeature
) : BottomSheetDialogFragment() {

    private var _binding: ReaderAppearanceBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ReaderAppearanceBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupColorScheme()
        setupFont()
        setupFontSize()
    }

    private fun setupColorScheme() {
        // Default to light
        binding.colorSchemeGroup.check(R.id.colorLight)

        binding.colorSchemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            
            when (checkedId) {
                R.id.colorLight -> {
                    readerViewFeature.showControls()
                    readerViewFeature.hideControls()
                }
                R.id.colorDark -> {
                    readerViewFeature.showControls()
                    readerViewFeature.hideControls()
                }
                R.id.colorSepia -> {
                    readerViewFeature.showControls()
                    readerViewFeature.hideControls()
                }
            }
        }
    }

    private fun setupFont() {
        // Default to sans-serif
        binding.fontGroup.check(R.id.fontSansSerif)

        binding.fontGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            
            when (checkedId) {
                R.id.fontSansSerif -> {
                    readerViewFeature.showControls()
                    readerViewFeature.hideControls()
                }
                R.id.fontSerif -> {
                    readerViewFeature.showControls()
                    readerViewFeature.hideControls()
                }
            }
        }
    }

    private fun setupFontSize() {
        // Default to 5 (middle)
        binding.fontSizeSlider.value = 5f

        binding.fontSizeDecrease.setOnClickListener {
            val currentValue = binding.fontSizeSlider.value
            if (currentValue > 1f) {
                binding.fontSizeSlider.value = currentValue - 1f
            }
        }

        binding.fontSizeIncrease.setOnClickListener {
            val currentValue = binding.fontSizeSlider.value
            if (currentValue < 9f) {
                binding.fontSizeSlider.value = currentValue + 1f
            }
        }

        binding.fontSizeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                // Trigger update by toggling controls
                readerViewFeature.showControls()
                readerViewFeature.hideControls()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReaderAppearanceBottomSheet"
        
        fun newInstance(feature: ReaderViewFeature): ReaderAppearanceBottomSheet {
            return ReaderAppearanceBottomSheet(feature)
        }
    }
}
