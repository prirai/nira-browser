package com.prirai.android.nira.search.awesomebar

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AbstractComposeView
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.concept.awesomebar.AwesomeBar.GroupedSuggestion
import mozilla.components.support.ktx.android.view.hideKeyboard

class AwesomeBarWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr), AwesomeBar {
    private val providers = mutableStateOf(emptyList<AwesomeBar.SuggestionProvider>())
    private val text = mutableStateOf("")
    private val hiddenSuggestions = mutableStateOf(emptySet<GroupedSuggestion>())
    private var onEditSuggestionListener: ((String) -> Unit)? = null
    private var onStopListener: (() -> Unit)? = null
    private var onRemoveSuggestionButtonClicked: ((GroupedSuggestion) -> Unit)? = null

    @Composable
    override fun Content() {
        if (providers.value.isEmpty()) {
            return
        }

        val prefs = UserPreferences(context)
        NiraTheme(
            amoledMode = prefs.amoledMode,
            dynamicColor = prefs.dynamicColors
        ) {
            NiraAwesomeBar(
                text = text.value,
                providers = providers.value,
                hiddenSuggestions = hiddenSuggestions.value,
                onSuggestionClicked = { suggestion ->
                    suggestion.onSuggestionClicked?.invoke()
                    onStopListener?.invoke()
                },
                onRemoveClicked = { grouped ->
                    hiddenSuggestions.value += grouped
                    onRemoveSuggestionButtonClicked?.invoke(grouped)
                },
                onScroll = { hideKeyboard() }
            )
        }
    }

    override fun addProviders(vararg providers: AwesomeBar.SuggestionProvider) {
        val newProviders = this.providers.value.toMutableList()
        newProviders.addAll(providers)
        this.providers.value = newProviders
    }

    override fun containsProvider(provider: AwesomeBar.SuggestionProvider): Boolean {
        return providers.value.any { current -> current.id == provider.id }
    }

    override fun onInputChanged(text: String) {
        hiddenSuggestions.value = emptySet()
        this.text.value = text
    }

    override fun removeAllProviders() {
        providers.value = emptyList()
    }

    override fun removeProviders(vararg providers: AwesomeBar.SuggestionProvider) {
        val newProviders = this.providers.value.toMutableList()
        newProviders.removeAll(providers.toSet())
        this.providers.value = newProviders
    }

    override fun setOnEditSuggestionListener(listener: (String) -> Unit) {
        onEditSuggestionListener = listener
    }

    override fun setOnStopListener(listener: () -> Unit) {
        onStopListener = listener
    }

    override fun updateHiddenSuggestions(hiddenSuggestions: Set<GroupedSuggestion>) {
        this.hiddenSuggestions.value = hiddenSuggestions
    }

    override fun setOnRemoveSuggestionButtonClicked(listener: (GroupedSuggestion) -> Unit) {
        onRemoveSuggestionButtonClicked = listener
    }
}
