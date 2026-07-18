package com.prirai.android.nira.browser

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.ReaderAppearanceBottomSheetBinding
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.readerview.view.ReaderViewControlsView

class ReaderAppearanceBottomSheet(
    private val readerViewFeature: ReaderViewFeature,
    private val controlsView: ReaderViewControlsView
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

        loadCurrentSettings()
        setupColorScheme()
        setupFont()
        setupFontSize()
    }

    private fun loadCurrentSettings() {
        val prefs = requireContext().getSharedPreferences(
            "mozac_feature_reader_view", Context.MODE_PRIVATE
        )

        val colorScheme = prefs.getString("mozac-readerview-colorscheme", "LIGHT") ?: "LIGHT"
        when (colorScheme) {
            "LIGHT" -> binding.colorSchemeGroup.check(R.id.colorLight)
            "DARK" -> binding.colorSchemeGroup.check(R.id.colorDark)
            "SEPIA" -> binding.colorSchemeGroup.check(R.id.colorSepia)
        }

        val fontType = prefs.getString("mozac-readerview-fonttype", "SANSSERIF") ?: "SANSSERIF"
        when (fontType) {
            "SANSSERIF" -> binding.fontGroup.check(R.id.fontSansSerif)
            "SERIF" -> binding.fontGroup.check(R.id.fontSerif)
        }

        val fontSize = prefs.getInt("mozac-readerview-fontsize", 3)
        binding.fontSizeSlider.value = fontSize.toFloat()
    }

    private fun setupColorScheme() {
        binding.colorSchemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val scheme = when (checkedId) {
                R.id.colorDark -> ReaderViewFeature.ColorScheme.DARK
                R.id.colorSepia -> ReaderViewFeature.ColorScheme.SEPIA
                else -> ReaderViewFeature.ColorScheme.LIGHT
            }
            controlsView.listener?.onColorSchemeChanged(scheme)
        }
    }

    private fun setupFont() {
        binding.fontGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val font = when (checkedId) {
                R.id.fontSerif -> ReaderViewFeature.FontType.SERIF
                else -> ReaderViewFeature.FontType.SANSSERIF
            }
            controlsView.listener?.onFontChanged(font)
        }
    }

    private fun setupFontSize() {
        binding.fontSizeDecrease.setOnClickListener {
            binding.fontSizeSlider.value =
                controlsView.listener?.onFontSizeDecreased()?.toFloat() ?: binding.fontSizeSlider.value
        }

        binding.fontSizeIncrease.setOnClickListener {
            binding.fontSizeSlider.value =
                controlsView.listener?.onFontSizeIncreased()?.toFloat() ?: binding.fontSizeSlider.value
        }

        binding.fontSizeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val currentSize = binding.fontSizeSlider.value.toInt()
                controlsView.listener?.let { listener ->
                    // Increment/decrement to the target value using the +/- API
                    val prefsSize = requireContext().getSharedPreferences(
                        "mozac_feature_reader_view", Context.MODE_PRIVATE
                    ).getInt("mozac-readerview-fontsize", 3)
                    var steps = currentSize - prefsSize
                    while (steps > 0) { listener.onFontSizeIncreased(); steps-- }
                    while (steps < 0) { listener.onFontSizeDecreased(); steps++ }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReaderAppearanceBottomSheet"

        fun newInstance(
            feature: ReaderViewFeature,
            controlsView: ReaderViewControlsView
        ): ReaderAppearanceBottomSheet {
            return ReaderAppearanceBottomSheet(feature, controlsView)
        }
    }
}
