@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.prirai.android.nira.search

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.constraintlayout.widget.ConstraintProperties.BOTTOM
import androidx.constraintlayout.widget.ConstraintProperties.PARENT_ID
import androidx.constraintlayout.widget.ConstraintProperties.TOP
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.BrowserDirection
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.FragmentSearchDialogBinding
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.search.awesomebar.AwesomeBarView
import com.prirai.android.nira.search.toolbar.ToolbarView
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.lib.state.ext.consumeFlow
import mozilla.components.lib.state.ext.consumeFrom
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.ktx.android.content.getColorFromAttr
import mozilla.components.support.ktx.android.view.hideKeyboard
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText
import mozilla.components.ui.widgets.behavior.DependencyGravity
import com.prirai.android.nira.components.toolbar.ToolbarPosition as OldToolbarPosition

typealias SearchDialogFragmentStore = SearchFragmentStore

class SearchDialogFragment : AppCompatDialogFragment(), UserInteractionHandler {
    private var _binding: FragmentSearchDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var interactor: SearchDialogInteractor
    private lateinit var store: SearchDialogFragmentStore
    private lateinit var toolbarView: ToolbarView
    private lateinit var awesomeBarView: AwesomeBarView
    private var firstUpdate = true

    private var dialogHandledAction = false

    // State for the Compose-rendered accessories. The fragment keeps
    // computing these exactly as it drove the old plain Views; the
    // composables at the bottom of this file render them.
    private val clipboardUrl = mutableStateOf<String?>(null)
    private val clipboardDividerAbove = mutableStateOf(false)
    private val pillDividerVisible = mutableStateOf(true)
    private val shortcutsAvailable = mutableStateOf(false)
    private val showShortcuts = mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    override fun onStop() {
        super.onStop()
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.SearchDialogStyle)
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.let { win ->
            // Apply Material 3 dynamic theming
            val userPreferences = UserPreferences(requireContext())
            val isDarkTheme = com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())
            
            val bgColor: Int // Declare bgColor here
            
            // Apply dynamic colors if enabled (Material You on Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && userPreferences.dynamicColors) {
                // Dynamic colors will be applied via theme inheritance
                // Just ensure we're using the right context
                val dynamicContext = com.google.android.material.color.DynamicColors.wrapContextIfAvailable(requireContext())
                
                // Get colors from dynamic context
                val typedValue = android.util.TypedValue()
                dynamicContext.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
                bgColor = if (userPreferences.amoledMode && isDarkTheme) {
                    Color.BLACK
                } else {
                    typedValue.data
                }
            } else {
                // Apply proper background color with AMOLED support
                bgColor = if (userPreferences.amoledMode && isDarkTheme) {
                    Color.BLACK
                } else {
                    requireContext().getColorFromAttr(com.google.android.material.R.attr.colorSurface)
                }
            }
            
            win.decorView.setBackgroundColor(bgColor)
            win.navigationBarColor = bgColor
            win.statusBarColor = bgColor
            
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Apply dynamic colors to dialog context if enabled
        val userPreferences = UserPreferences(requireContext())
        val dialogContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && userPreferences.dynamicColors) {
            com.google.android.material.color.DynamicColors.wrapContextIfAvailable(requireContext())
        } else {
            requireContext()
        }
        
        return object : Dialog(dialogContext, this.theme) {
            @Deprecated("Deprecated in Java")
            override fun onBackPressed() {
                // Call the fragment's UserInteractionHandler.onBackPressed() instead
                if (!this@SearchDialogFragment.onBackPressed()) {
                    // If fragment doesn't handle it, use default behavior
                    @Suppress("DEPRECATION")
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args by navArgs<SearchDialogFragmentArgs>()
        
        // Use dynamic color context if available
        val userPreferences = UserPreferences(requireContext())
        val contextForInflater = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && userPreferences.dynamicColors) {
            com.google.android.material.color.DynamicColors.wrapContextIfAvailable(requireContext())
        } else {
            requireContext()
        }
        
        val themedInflater = inflater.cloneInContext(contextForInflater)
        _binding = FragmentSearchDialogBinding.inflate(themedInflater, container, false)
        val view = binding.root
        val activity = requireActivity() as BrowserActivity
        val isPrivate = activity.browsingModeManager.mode.isPrivate

        store = SearchDialogFragmentStore(
            createInitialSearchFragmentState(
                components,
                tabId = args.sessionId,
                pastedText = args.pastedText,
                context = requireContext()
            )
        )

        interactor = SearchDialogInteractor(
            SearchDialogController(
                activity = activity,
                store = components.store,
                tabsUseCases = components.tabsUseCases,
                fragmentStore = store,
                navController = findNavController(),
                openedFromHome =
                    findNavController().previousBackStackEntry?.destination?.id == R.id.homeFragment,
                dismissDialog = {
                    dialogHandledAction = true
                    dismissAllowingStateLoss()
                },
                clearToolbarFocus = {
                    dialogHandledAction = true
                    toolbarView.view.hideKeyboard()
                    toolbarView.view.clearFocus()
                },
                focusToolbar = { toolbarView.view.edit.focus() }
            )
        )

        toolbarView = ToolbarView(
            requireContext(),
            interactor,
            historyStorageProvider(),
            isPrivate,
            binding.toolbar,
            components.engine
        )

        val awesomeBar = binding.awesomeBar
        //awesomeBar.customizeForBottomToolbar = UserPreferences(requireContext()).toolbarPosition == ToolbarPosition.BOTTOM.ordinal

        awesomeBarView = AwesomeBarView(
            activity,
            interactor,
            awesomeBar
        )

        binding.awesomeBar.setOnTouchListener { v, _ ->
            v.performClick()
            view.hideKeyboard()
            false
        }

        awesomeBarView.view.setOnEditSuggestionListener(toolbarView.view::setSearchTerms)
        awesomeBarView.view.setOnStopListener {
            dialogHandledAction = true
            dismissAllowingStateLoss()
        }
        awesomeBarView.view.setOnRemoveSuggestionButtonClicked { grouped ->
            val suggestion = grouped.suggestion as? AwesomeBar.Suggestion ?: return@setOnRemoveSuggestionButtonClicked
            val url = suggestion.description ?: suggestion.editSuggestion ?: return@setOnRemoveSuggestionButtonClicked
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                components.historyStorage.deleteVisitsFor(url)
            }
        }

        val urlView = toolbarView.view
            .findViewById<InlineAutocompleteEditText>(R.id.mozac_browser_toolbar_edit_url_view)
        urlView?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        components.engine.speculativeCreateSession(isPrivate)

        if (findNavController().previousBackStackEntry?.destination?.id == R.id.homeFragment) {
            // When displayed above home, dispatches the touch events to scrim area to the HomeFragment
            binding.searchWrapper.background = Color.TRANSPARENT.toDrawable()
            dialog?.window?.decorView?.setOnTouchListener { v, event ->
                v.performClick()
                requireActivity().dispatchTouchEvent(event)
                false
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAccessoryComposeContent()

        consumeFlow(components.store) { flow ->
            flow.map { state -> state.search }
                .distinctUntilChanged()
                .collect { search ->
                    store.dispatch(SearchFragmentAction.UpdateSearchState(search))
                }
        }

        setupConstraints(view)

        // When displayed above browser, dismisses dialog on clicking scrim area
        if (findNavController().previousBackStackEntry?.destination?.id == R.id.browserFragment) {
            binding.searchWrapper.setOnClickListener {
                it.hideKeyboard()
                dismissAllowingStateLoss()
            }
        }

        consumeFrom(store) {
            /*
            * firstUpdate is used to make sure we keep the awesomebar hidden on the first run
            *  of the searchFragmentDialog. We only turn it false after the user has changed the
            *  query as consumeFrom may run several times on fragment start due to state updates.
            * */
            if (it.url != it.query) firstUpdate = false
            binding.awesomeBar.visibility = if (shouldShowAwesomebar(it)) View.VISIBLE else View.INVISIBLE
            // The clipboard check is a binder call; pass it lazily so it
            // only runs when the result can matter (empty query, clipboard
            // suggestions enabled) instead of on every keystroke.
            updateClipboardSuggestion(it) {
                requireContext().components.clipboardHandler.containsURL()
            }
            updateToolbarContentDescription(it)
            updateSearchShortcuts(it)
            toolbarView.update(it)
            awesomeBarView.update(requireContext(), it)
        }
    }

    /**
     * Hosts the two Compose accessory sections (clipboard card + search
     * actions row) in their ComposeViews. The AC toolbar and the awesome
     * bar are NOT part of this - they keep their own View/Compose hosts.
     */
    private fun setupAccessoryComposeContent() {
        val context = requireContext()
        val prefs = UserPreferences(context)
        val darkTheme = ThemeManager.isDarkMode(context)

        binding.fillLinkFromClipboard.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NiraTheme(
                    darkTheme = darkTheme,
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    SearchClipboardCard(
                        url = clipboardUrl.value,
                        dividerAbove = clipboardDividerAbove.value,
                        onFillLink = ::onFillLinkFromClipboardClicked,
                    )
                }
            }
        }

        binding.pillWrapper.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NiraTheme(
                    darkTheme = darkTheme,
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    SearchActionsRow(
                        showDivider = pillDividerVisible.value,
                        shortcutsAvailable = shortcutsAvailable.value,
                        showShortcuts = showShortcuts.value,
                        onShortcutsClick = { interactor.onSearchShortcutsButtonClicked() },
                        onQrScan = ::onQrScanClicked,
                    )
                }
            }
        }
    }

    private fun onQrScanClicked() {
        val qrScanner = com.prirai.android.nira.integration.QrScannerIntegration(
            context = requireContext(),
            fragment = this,
            onScanResult = { scannedText ->
                // Process the scanned QR code and open it as a URL
                val url = scannedText.trim()

                // Hide keyboard and dismiss search dialog
                view?.hideKeyboard()

                // Use the same navigation method as other search actions
                (activity as? BrowserActivity)?.openToBrowserAndLoad(
                    searchTermOrURL = url,
                    newTab = store.state.tabId == null,
                    from = BrowserDirection.FromSearchDialog,
                    engine = store.state.searchEngineSource.searchEngine
                        ?: store.state.defaultEngine
                )

                dismiss()
            },
            onNeedToRequestPermissions = { permissions ->
                requestPermissions(permissions, REQUEST_CODE_QR_SCAN)
            }
        )
        qrScanner.scan()
    }

    private fun onFillLinkFromClipboardClicked() {
        val clipboardUrl = requireContext().components.clipboardHandler.extractURL() ?: ""
        val urlView = toolbarView.view
            .findViewById<InlineAutocompleteEditText>(R.id.mozac_browser_toolbar_edit_url_view)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            toolbarView.view.edit.updateUrl(clipboardUrl)
            hideClipboardSection()
            urlView.setSelection(clipboardUrl.length)
        } else {
            requireView().hideKeyboard()
            toolbarView.view.clearFocus()
            (activity as BrowserActivity)
                .openToBrowserAndLoad(
                    searchTermOrURL = clipboardUrl,
                    newTab = store.state.tabId == null,
                    from = BrowserDirection.FromSearchDialog,
                )
        }
        requireContext().components.clipboardHandler.text = null
    }

    private fun shouldShowAwesomebar(searchFragmentState: SearchFragmentState) =
        !firstUpdate && searchFragmentState.query.isNotBlank() || searchFragmentState.showSearchShortcuts


    override fun onPause() {
        super.onPause()
        view?.hideKeyboard()
    }

    private fun hideClipboardSection() {
        // The divider, title, url and icon all render inside the
        // clipboard ComposeView, so hiding the host hides the section.
        binding.fillLinkFromClipboard.isVisible = false
    }

    /*
     * This way of dismissing the keyboard is needed to smoothly dismiss the keyboard while the dialog
     * is also dismissing. For example, when clicking a top site on home while this dialog is showing.
     */
    private fun hideDeviceKeyboard() {
        // If the interactor/controller has handled a search event itself, it will hide the keyboard.
        if (!dialogHandledAction) {
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        hideDeviceKeyboard()
    }

    override fun onBackPressed(): Boolean {
        view?.hideKeyboard()
        dismissAllowingStateLoss()
        return true
    }

    private fun historyStorageProvider(): HistoryStorage {
        return components.historyStorage
    }

    private fun setupConstraints(view: View) {
        if (UserPreferences(view.context).toolbarPosition == OldToolbarPosition.BOTTOM.ordinal) {
            // Bottom toolbar flips the accessory stack: the clipboard
            // card's divider renders above the card instead of below.
            clipboardDividerAbove.value = true
            ConstraintSet().apply {
                clone(binding.searchWrapper)

                clear(binding.toolbar.id, TOP)
                connect(binding.toolbar.id, BOTTOM, PARENT_ID, BOTTOM)

                clear(binding.pillWrapper.id, BOTTOM)
                connect(binding.pillWrapper.id, BOTTOM, binding.toolbar.id, TOP)

                clear(binding.fillLinkFromClipboard.id, TOP)
                connect(binding.fillLinkFromClipboard.id, BOTTOM, binding.pillWrapper.id, TOP)

                applyTo(binding.searchWrapper)
            }
        }
    }

    private fun updateClipboardSuggestion(
        searchState: SearchFragmentState,
        containsUrl: () -> Boolean
    ) {
        // Lazy evaluation order matters: the binder read is skipped unless
        // the query is empty and clipboard suggestions can show at all.
        val shouldShowView = searchState.showClipboardSuggestions &&
                searchState.query.isEmpty() &&
                !containsUrl()

        binding.fillLinkFromClipboard.isVisible = shouldShowView
        pillDividerVisible.value =
            !(shouldShowView && UserPreferences(requireContext()).toolbarPosition == OldToolbarPosition.BOTTOM.ordinal)

        if (shouldShowView) {
            // On S+ the clipboard URL is not exposed to the app, so the
            // card shows its title only - matching the old View row,
            // which never populated the url TextView on S+.
            clipboardUrl.value =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    null
                } else {
                    val clipboardUrl = context?.components?.clipboardHandler?.extractURL()

                    if (clipboardUrl != null && !((activity as BrowserActivity).browsingModeManager.mode.isPrivate)) {
                        components.engine.speculativeConnect(clipboardUrl)
                    }
                    clipboardUrl
                }
        }
    }

    private fun updateToolbarContentDescription(searchState: SearchFragmentState) {
        val urlView = toolbarView.view
            .findViewById<InlineAutocompleteEditText>(R.id.mozac_browser_toolbar_edit_url_view)

        searchState.searchEngineSource.searchEngine?.let { engine ->
            toolbarView.view.contentDescription = engine.name + ", " + urlView.hint
        }

        urlView?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun updateSearchShortcuts(searchState: SearchFragmentState) {
        shortcutsAvailable.value = searchState.areShortcutsAvailable
        showShortcuts.value = searchState.showSearchShortcuts
    }

    companion object {
        private const val REQUEST_CODE_QR_SCAN = 103
    }
}

/**
 * The "fill link from clipboard" accessory: the clipboard URL as a
 * grouped-card [NiraCardRow] stack, with the section divider above or
 * below the card depending on where the toolbar mounts (top toolbar:
 * divider below, bottom toolbar: divider above).
 *
 * Pre-S the clipboard URL is readable and renders as a second row of
 * the same card; on S+ the binder never exposes it, so the card stays a
 * single title row - the same split the old plain-View row had.
 */
@Composable
private fun SearchClipboardCard(
    url: String?,
    dividerAbove: Boolean,
    onFillLink: () -> Unit,
) {
    val title = stringResource(R.string.fill_from_clipboard)
    val showUrl = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val description =
        if (showUrl) "$title, ${url.orEmpty()}." else "$title."

    Column {
        if (dividerAbove) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        NiraCardRow(
            title = title,
            isTopRow = true,
            isBottomRow = !showUrl,
            showDivider = false,
            onClick = onFillLink,
            modifier = Modifier.semantics { contentDescription = description },
            leading = {
                Icon(
                    painter = painterResource(R.drawable.mozac_ic_link_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        if (showUrl) {
            NiraCardRow(
                title = url.orEmpty(),
                isTopRow = false,
                isBottomRow = true,
                showDivider = false,
                onClick = onFillLink,
                minRowHeight = 32.dp,
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                leading = { Spacer(modifier = Modifier.size(24.dp)) },
            )
        }

        if (!dividerAbove) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * The search-actions accessory: the divider above the row (hidden while
 * the bottom-mounted clipboard card shows, as before) plus the engine
 * shortcut chip and the QR scan icon button.
 */
@Composable
private fun SearchActionsRow(
    showDivider: Boolean,
    shortcutsAvailable: Boolean,
    showShortcuts: Boolean,
    onShortcutsClick: () -> Unit,
    onQrScan: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (showDivider) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (shortcutsAvailable) {
                    FilterChip(
                        selected = showShortcuts,
                        onClick = onShortcutsClick,
                        label = { Text(stringResource(R.string.change_search_engine)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_ios_search),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = onQrScan) {
                    Icon(
                        painter = painterResource(R.drawable.ic_qr_code_scanner),
                        contentDescription = stringResource(R.string.scan_qr_code),
                    )
                }
            }
        }
    }
}
