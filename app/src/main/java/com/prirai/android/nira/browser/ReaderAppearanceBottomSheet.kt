@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.readerview.view.ReaderViewControlsView

/**
 * Reader-mode appearance controls on the shared Material 3 Expressive
 * sheet stack: color scheme and font family as segmented buttons, font
 * size as a stepped slider with smaller/larger steppers.
 *
 * Every control forwards through the mozac [ReaderViewControlsView]
 * listener wired by [com.prirai.android.nira.integration.ReaderModeIntegration].
 * The listener is what applies a change and persists it to the
 * `mozac_feature_reader_view` SharedPreferences, which this sheet reads
 * back for its initial state - the sheet owns no reader state itself.
 */
class ReaderAppearanceBottomSheet(
    private val readerViewFeature: ReaderViewFeature,
    private val controlsView: ReaderViewControlsView
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors
            ) {
                ReaderAppearanceSheetContent(
                    controlsView = controlsView,
                    onClose = ::dismiss
                )
            }
        }
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

@Composable
private fun ReaderAppearanceSheetContent(
    controlsView: ReaderViewControlsView,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val readerPrefs = remember(context) {
        context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var colorScheme by remember {
        mutableStateOf(readerPrefs.getString(PREF_COLOR_SCHEME, COLOR_LIGHT) ?: COLOR_LIGHT)
    }
    var fontType by remember {
        mutableStateOf(readerPrefs.getString(PREF_FONT_TYPE, FONT_SANS_SERIF) ?: FONT_SANS_SERIF)
    }
    var fontSize by remember {
        mutableStateOf(readerPrefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE).toFloat())
    }

    fun syncFontSize(target: Int) {
        val listener = controlsView.listener ?: return
        // Each mozac +/- call applies one step and persists it, so the
        // pref read stays the live baseline while stepping to the target.
        var steps = target - readerPrefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE)
        while (steps > 0) {
            listener.onFontSizeIncreased()
            steps--
        }
        while (steps < 0) {
            listener.onFontSizeDecreased()
            steps++
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NiraSheetHeader(
            title = stringResource(R.string.reader_view_appearance),
            onClose = onClose,
            dividerAlpha = null,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
        ) {
            SectionLabel(
                text = stringResource(R.string.reader_appearance_color_scheme),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = colorScheme == COLOR_LIGHT,
                    onClick = {
                        colorScheme = COLOR_LIGHT
                        controlsView.listener?.onColorSchemeChanged(ReaderViewFeature.ColorScheme.LIGHT)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text(stringResource(R.string.reader_appearance_light))
                }
                SegmentedButton(
                    selected = colorScheme == COLOR_DARK,
                    onClick = {
                        colorScheme = COLOR_DARK
                        controlsView.listener?.onColorSchemeChanged(ReaderViewFeature.ColorScheme.DARK)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text(stringResource(R.string.reader_appearance_dark))
                }
                SegmentedButton(
                    selected = colorScheme == COLOR_SEPIA,
                    onClick = {
                        colorScheme = COLOR_SEPIA
                        controlsView.listener?.onColorSchemeChanged(ReaderViewFeature.ColorScheme.SEPIA)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text(stringResource(R.string.reader_appearance_sepia))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(
                text = stringResource(R.string.reader_appearance_font),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = fontType == FONT_SANS_SERIF,
                    onClick = {
                        fontType = FONT_SANS_SERIF
                        controlsView.listener?.onFontChanged(ReaderViewFeature.FontType.SANSSERIF)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.reader_appearance_sans_serif))
                }
                SegmentedButton(
                    selected = fontType == FONT_SERIF,
                    onClick = {
                        fontType = FONT_SERIF
                        controlsView.listener?.onFontChanged(ReaderViewFeature.FontType.SERIF)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.reader_appearance_serif))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(
                text = stringResource(R.string.font_size),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = {
                        val newSize = controlsView.listener?.onFontSizeDecreased()
                        if (newSize != null) fontSize = newSize.toFloat()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.reader_appearance_decrease_font_size)
                    )
                }
                val sliderLabel = stringResource(R.string.font_size)
                Slider(
                    value = fontSize,
                    onValueChange = { value ->
                        fontSize = value
                        syncFontSize(value.toInt())
                    },
                    valueRange = FONT_SIZE_MIN.toFloat()..FONT_SIZE_MAX.toFloat(),
                    steps = FONT_SIZE_MAX - FONT_SIZE_MIN - 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = sliderLabel }
                )
                FilledTonalIconButton(
                    onClick = {
                        val newSize = controlsView.listener?.onFontSizeIncreased()
                        if (newSize != null) fontSize = newSize.toFloat()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.reader_appearance_increase_font_size)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

private const val READER_PREFS_NAME = "mozac_feature_reader_view"
private const val PREF_COLOR_SCHEME = "mozac-readerview-colorscheme"
private const val PREF_FONT_TYPE = "mozac-readerview-fonttype"
private const val PREF_FONT_SIZE = "mozac-readerview-fontsize"
private const val COLOR_LIGHT = "LIGHT"
private const val COLOR_DARK = "DARK"
private const val COLOR_SEPIA = "SEPIA"
private const val FONT_SANS_SERIF = "SANSSERIF"
private const val FONT_SERIF = "SERIF"
private const val DEFAULT_FONT_SIZE = 3
private const val FONT_SIZE_MIN = 1
private const val FONT_SIZE_MAX = 9
