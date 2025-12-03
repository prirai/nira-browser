package com.prirai.android.nira.browser.home

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Display.FLAG_SECURE
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.TOP
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.NavGraphDirections
import com.prirai.android.nira.R
import com.prirai.android.nira.addons.AddonsActivity
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.settings.HomepageBackgroundChoice
import com.prirai.android.nira.browser.toolbar.ToolbarGestureHandler
import com.prirai.android.nira.browser.shortcuts.ShortcutDatabase
import com.prirai.android.nira.browser.shortcuts.ShortcutEntity
import com.prirai.android.nira.browser.shortcuts.ShortcutGridAdapter
import com.prirai.android.nira.databinding.FragmentHomeBinding
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.nav
import com.prirai.android.nira.toolbar.ContextualBottomToolbar
import com.prirai.android.nira.history.HistoryActivity
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageChoice
import com.prirai.android.nira.settings.activity.SettingsActivity
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import mozilla.components.browser.menu.view.MenuButton
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.fetch.Request
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import mozilla.components.lib.state.ext.consumeFlow
import mozilla.components.lib.state.ext.consumeFrom
import org.mozilla.gecko.util.ThreadUtils
import java.lang.ref.WeakReference
import mozilla.components.ui.widgets.behavior.ViewPosition as OldToolbarPosition


@ExperimentalCoroutinesApi
class HomeFragment : Fragment() {
    private var database: ShortcutDatabase? = null

    // private val args by navArgs<HomeFragmentArgs>() // Removed - HomeFragment no longer used
    // private lateinit var bundleArgs: Bundle // Removed - HomeFragment no longer used

    private val browsingModeManager get() = (activity as BrowserActivity).browsingModeManager

    private val store: BrowserStore
        get() = components.store

    private var appBarLayout: AppBarLayout? = null

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @VisibleForTesting
    internal var getMenuButton: () -> MenuButton? = { binding.menuButton }

    @Suppress("LongMethod")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root

        activity as BrowserActivity
        val components = requireContext().components

        updateLayout(view)

        if (!UserPreferences(requireContext()).showShortcuts) {
            binding.shortcutName.visibility = View.GONE
            binding.shortcutGrid.visibility = View.GONE
        }

        if (!UserPreferences(requireContext()).shortcutDrawerOpen) {
            binding.shortcutName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_baseline_shortcuts,
                0,
                R.drawable.ic_baseline_chevron_up,
                0
            )
            binding.shortcutGrid.visibility = View.GONE
        }

        when (UserPreferences(requireContext()).homepageBackgroundChoice) {
            HomepageBackgroundChoice.URL.ordinal -> {
                val url = UserPreferences(requireContext()).homepageBackgroundUrl
                if (url != "") {
                    val fullUrl = if (url.startsWith("http")) url else "https://$url"
                    val request = Request(fullUrl)
                    val client = HttpURLConnectionClient()

                    viewLifecycleOwner.lifecycleScope.launch {
                        val response = withContext(Dispatchers.IO) {
                            client.fetch(request)
                        }
                        response.use {
                            val bitmap = it.body.useStream { stream -> BitmapFactory.decodeStream(stream) }
                            val customBackground = object : BitmapDrawable(resources, bitmap) {
                                override fun draw(canvas: Canvas) {
                                    val width = bounds.width()
                                    val height = bounds.height()
                                    val bitmapWidth = bitmap.width
                                    val bitmapHeight = bitmap.height

                                    val scale = maxOf(
                                        width.toFloat() / bitmapWidth.toFloat(),
                                        height.toFloat() / bitmapHeight.toFloat()
                                    )

                                    val scaledWidth = (bitmapWidth * scale).toInt()
                                    val scaledHeight = (bitmapHeight * scale).toInt()

                                    val left = (width - scaledWidth) / 2
                                    val top = (height - scaledHeight) / 2

                                    val src = Rect(0, 0, bitmapWidth, bitmapHeight)
                                    val dst = Rect(left, top, left + scaledWidth, top + scaledHeight)

                                    canvas.drawBitmap(bitmap, src, dst, paint)
                                }
                            }

                            customBackground.gravity = Gravity.CENTER

                            binding.homeLayout.background = customBackground
                        }
                    }
                }
            }

            HomepageBackgroundChoice.GALLERY.ordinal -> {
                val uri = UserPreferences(requireContext()).homepageBackgroundUrl
                if (uri != "") {
                    if (activity != null) {
                        val contentResolver = requireContext().contentResolver
                        val bitmap = try {
                            MediaStore.Images.Media.getBitmap(contentResolver, uri.toUri())
                        } catch (e: Exception) {
                            null
                        }

                        val customBackground = if (bitmap != null) {
                            object : BitmapDrawable(resources, bitmap) {
                                override fun draw(canvas: Canvas) {
                                    val width = bounds.width()
                                    val height = bounds.height()
                                    val bitmapWidth = bitmap.width
                                    val bitmapHeight = bitmap.height

                                    val scale = maxOf(
                                        width.toFloat() / bitmapWidth.toFloat(),
                                        height.toFloat() / bitmapHeight.toFloat()
                                    )

                                    val scaledWidth = (bitmapWidth * scale).toInt()
                                    val scaledHeight = (bitmapHeight * scale).toInt()

                                    val left = (width - scaledWidth) / 2
                                    val top = (height - scaledHeight) / 2

                                    val src = Rect(0, 0, bitmapWidth, bitmapHeight)
                                    val dst =
                                        Rect(left, top, left + scaledWidth, top + scaledHeight)

                                    canvas.drawBitmap(bitmap, src, dst, paint)
                                }
                            }
                        } else null

                        customBackground?.gravity = Gravity.CENTER

                        binding.homeLayout.background = customBackground
                    }
                }
            }
        }

        binding.shortcutName.setOnClickListener {
            if (UserPreferences(requireContext()).shortcutDrawerOpen) {
                UserPreferences(requireContext()).shortcutDrawerOpen = false
                binding.shortcutName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_baseline_shortcuts,
                    0,
                    R.drawable.ic_baseline_chevron_up,
                    0
                )
                binding.shortcutGrid.visibility = View.GONE
            } else {
                UserPreferences(requireContext()).shortcutDrawerOpen = true
                binding.shortcutName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_baseline_shortcuts,
                    0,
                    R.drawable.ic_baseline_chevron_down,
                    0
                )
                binding.shortcutGrid.visibility = View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Update shortcut database to hold name
            val MIGRATION_1_2: Migration = object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE shortcutentity ADD COLUMN title TEXT")
                }
            }

            // Remove unused 'add' column from database
            val MIGRATION_2_3: Migration = object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Create new table without the 'add' column
                    db.execSQL("CREATE TABLE shortcutentity_new (uid INTEGER NOT NULL, url TEXT, title TEXT, PRIMARY KEY(uid))")
                    // Copy data from old table to new table
                    db.execSQL("INSERT INTO shortcutentity_new (uid, url, title) SELECT uid, url, title FROM shortcutentity")
                    // Drop old table
                    db.execSQL("DROP TABLE shortcutentity")
                    // Rename new table to original name
                    db.execSQL("ALTER TABLE shortcutentity_new RENAME TO shortcutentity")
                }
            }

            database = withContext(Dispatchers.IO) {
                Room.databaseBuilder(
                    requireContext(),
                    ShortcutDatabase::class.java, "shortcut-database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
            }

            val shortcutDao = database?.shortcutDao()
            val shortcuts: MutableList<ShortcutEntity> = withContext(Dispatchers.IO) {
                shortcutDao?.getAll() as? MutableList ?: mutableListOf()
            }

            val adapter = ShortcutGridAdapter(requireContext(), shortcuts)

            binding.shortcutGrid.adapter = adapter
        }

        binding.shortcutGrid.setOnItemClickListener { _, _, position, _ ->
            findNavController().navigate(
                R.id.browserFragment
            )

            components.sessionUseCases.loadUrl(
                (binding.shortcutGrid.adapter.getItem(position) as ShortcutEntity).url!!
            )
        }

        binding.shortcutGrid.setOnItemLongClickListener { _, _, position, _ ->
            val items =
                arrayOf(resources.getString(R.string.edit_shortcut), resources.getString(R.string.delete_shortcut))

            AlertDialog.Builder(requireContext())
                .setTitle(resources.getString(R.string.edit_shortcut))
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showEditShortcutDialog(position, binding.shortcutGrid.adapter as ShortcutGridAdapter)
                        1 -> deleteShortcut(
                            binding.shortcutGrid.adapter.getItem(position) as ShortcutEntity,
                            binding.shortcutGrid.adapter as ShortcutGridAdapter
                        )
                    }
                }
                .show()

            return@setOnItemLongClickListener true
        }

        binding.addShortcut.setOnClickListener {
            showCreateShortcutDialog(binding.shortcutGrid.adapter as ShortcutGridAdapter)
        }

        // Apply private browsing theme
        if (browsingModeManager.mode.isPrivate) {
            setupPrivateBrowsingTheme()
        } else {
            setupNormalBrowsingTheme()
        }

        appBarLayout = binding.homeAppBar

        return view
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        getMenuButton()?.dismissMenu()
    }

    private fun showEditShortcutDialog(position: Int, adapter: ShortcutGridAdapter) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle(resources.getString(R.string.edit_shortcut))
        val viewInflated: View =
            LayoutInflater.from(context).inflate(R.layout.add_shortcut_dialog, view as ViewGroup?, false)
        val url = viewInflated.findViewById<View>(R.id.urlEditText) as EditText
        url.setText(adapter.list[position].url)
        val name = viewInflated.findViewById<View>(R.id.nameEditText) as EditText
        name.setText(adapter.list[position].title)
        builder.setView(viewInflated)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val item = adapter.list[position]
            item.url = url.text.toString()
            item.title = name.text.toString()
            adapter.notifyDataSetChanged()

            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    database?.shortcutDao()?.update(item)
                }
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun showCreateShortcutDialog(adapter: ShortcutGridAdapter) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle(resources.getString(R.string.add_shortcut))
        val viewInflated: View =
            LayoutInflater.from(context).inflate(R.layout.add_shortcut_dialog, view as ViewGroup?, false)
        val url = viewInflated.findViewById<View>(R.id.urlEditText) as EditText
        val name = viewInflated.findViewById<View>(R.id.nameEditText) as EditText
        builder.setView(viewInflated)

        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dialog.dismiss()
            val list = adapter.list
            list.add(ShortcutEntity(url = url.text.toString(), title = name.text.toString()))
            adapter.list = list
            adapter.notifyDataSetChanged()

            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    database?.shortcutDao()
                        ?.insertAll(ShortcutEntity(url = url.text.toString(), title = name.text.toString()))
                }
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun deleteShortcut(shortcutEntity: ShortcutEntity, adapter: ShortcutGridAdapter) {
        val list = adapter.list
        list.remove(shortcutEntity)
        adapter.list = list
        adapter.notifyDataSetChanged()

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                database?.shortcutDao()?.delete(shortcutEntity)
            }
        }
    }

    private fun updateLayout(view: View) {
        when (UserPreferences(view.context).toolbarPosition) {
            OldToolbarPosition.TOP.ordinal -> {
                binding.toolbarLayout.layoutParams = CoordinatorLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP
                }

                ConstraintSet().apply {
                    clone(binding.toolbarLayout)
                    clear(binding.bottomBar.id, BOTTOM)
                    clear(binding.bottomBarShadow.id, BOTTOM)
                    connect(binding.bottomBar.id, TOP, PARENT_ID, TOP)
                    connect(binding.bottomBarShadow.id, TOP, binding.bottomBar.id, BOTTOM)
                    connect(binding.bottomBarShadow.id, BOTTOM, PARENT_ID, BOTTOM)
                    applyTo(binding.toolbarLayout)
                }

                binding.homeAppBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin =
                        resources.getDimensionPixelSize(R.dimen.home_fragment_top_toolbar_header_margin)
                }
            }

            OldToolbarPosition.BOTTOM.ordinal -> {}
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeSearchEngineChanges()
        createHomeMenu(requireContext(), WeakReference(binding.menuButton))

        // Setup contextual bottom toolbar
        setupContextualBottomToolbar()

        binding.gestureLayout.addGestureListener(
            ToolbarGestureHandler(
                activity = requireActivity(),
                contentLayout = binding.homeLayout,
                tabPreview = binding.tabPreview,
                toolbarLayout = binding.toolbarLayout,
                store = components.store,
                selectTabUseCase = components.tabsUseCases.selectTab
            )
        )

        binding.menuButton.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                R.color.main_icon
            )
        )

        binding.toolbarWrapper.setOnClickListener {
            navigateToSearch()
        }

        binding.tabButton.setOnClickListener {
            openTabDrawer()
        }

        if (browsingModeManager.mode.isPrivate) {
            requireActivity().window.addFlags(FLAG_SECURE)
        } else {
            requireActivity().window.clearFlags(FLAG_SECURE)
        }

        consumeFrom(components.store) {
            updateTabCounter(it)
        }

        updateTabCounter(components.store.state)

        // HomeFragment no longer used - commenting out bundleArgs
        // if (bundleArgs.getBoolean(FOCUS_ON_ADDRESS_BAR)) {
        //     navigateToSearch()
        // }
    }

    private fun observeSearchEngineChanges() {
        consumeFlow(store) { flow ->
            flow.map { state -> state.search.selectedOrDefaultSearchEngine }
                .distinctUntilChanged()
                .collect { searchEngine ->
                    if (searchEngine != null) {
                        val iconSize =
                            requireContext().resources.getDimensionPixelSize(R.dimen.icon_width)
                        val searchIcon = searchEngine.icon.toDrawable(requireContext().resources)
                        searchIcon.setBounds(0, 0, iconSize, iconSize)
                        binding.searchEngineIcon.setImageDrawable(searchIcon)
                    } else {
                        binding.searchEngineIcon.setImageDrawable(null)
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        appBarLayout = null
        // bundleArgs.clear() // HomeFragment no longer used
        requireActivity().window.clearFlags(FLAG_SECURE)
    }

    private fun navigateToSearch() {
        // HomeFragment no longer used - using about:homepage instead
        // Navigation now handled through BrowserFragment
        val directions = NavGraphDirections.actionGlobalSearchDialog(
            sessionId = null
        )
        nav(R.id.browserFragment, directions, null)
    }

    @SuppressWarnings("ComplexMethod", "LongMethod")
    private fun createHomeMenu(context: Context, menuButtonView: WeakReference<MenuButton>) =
        HomeMenu(
            this.viewLifecycleOwner,
            context,
            onItemTapped = {
                when (it) {
                    HomeMenu.Item.NewTab -> {
                        browsingModeManager.mode = BrowsingMode.Normal
                        when (UserPreferences(requireContext()).homepageType) {
                            HomepageChoice.VIEW.ordinal -> {
                                components.tabsUseCases.addTab.invoke(
                                    "about:homepage",
                                    selectTab = true
                                )
                            }

                            HomepageChoice.BLANK_PAGE.ordinal -> {
                                components.tabsUseCases.addTab.invoke(
                                    "about:blank",
                                    selectTab = true
                                )
                            }

                            HomepageChoice.CUSTOM_PAGE.ordinal -> {
                                components.tabsUseCases.addTab.invoke(
                                    UserPreferences(requireContext()).customHomepageUrl,
                                    selectTab = true
                                )
                            }
                        }
                    }

                    HomeMenu.Item.NewPrivateTab -> {
                        browsingModeManager.mode = BrowsingMode.Private
                        when (UserPreferences(requireContext()).homepageType) {
                            HomepageChoice.VIEW.ordinal -> {
                                components.tabsUseCases.addTab.invoke(
                                    "about:homepage",
                                    selectTab = true,
                                    private = true
                                )
                            }

                            HomepageChoice.BLANK_PAGE.ordinal -> {
                                components.tabsUseCases.addTab.invoke(
                                    "about:blank",
                                    selectTab = true,
                                    private = true
                                )
                            }

                            HomepageChoice.CUSTOM_PAGE.ordinal -> {
                                components.tabsUseCases.addTab.invoke(
                                    UserPreferences(requireContext()).customHomepageUrl,
                                    selectTab = true,
                                    private = true
                                )
                            }
                        }
                    }

                    HomeMenu.Item.Settings -> {
                        val settings = Intent(activity, SettingsActivity::class.java)
                        settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        requireActivity().startActivity(settings)
                    }

                    HomeMenu.Item.Bookmarks -> {
                        val bookmarksBottomSheet =
                            com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment.newInstance()
                        bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                    }

                    HomeMenu.Item.History -> {
                        val settings = Intent(activity, HistoryActivity::class.java)
                        settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        activity?.startActivity(settings)
                    }

                    HomeMenu.Item.AddonsManager -> {
                        val settings = Intent(activity, AddonsActivity::class.java)
                        settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        activity?.startActivity(settings)
                    }

                    else -> {}
                }
            },
            onHighlightPresent = { menuButtonView.get()?.setHighlight(it) },
            onMenuBuilderChanged = { menuButtonView.get()?.menuBuilder = it }
        )

    private fun openTabDrawer() {
        // Show the new bottom sheet tabs dialog
        val tabsBottomSheet = com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
        tabsBottomSheet.show(parentFragmentManager, com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG)
    }

    private fun updateTabCounter(browserState: BrowserState) {
        val tabCount = if (browsingModeManager.mode.isPrivate) {
            browserState.privateTabs.size
        } else {
            browserState.normalTabs.size
        }

        binding.tabButton.setCountWithAnimation(tabCount)

        // Also update the contextual bottom toolbar if it's visible
        updateContextualToolbarForHomepage()
    }

    private fun setupContextualBottomToolbar() {
        val toolbar = binding.contextualBottomToolbar

        // Force bottom toolbar to show for testing iOS icons
        val showBottomToolbar = true // UserPreferences(requireContext()).shouldUseBottomToolbar
        if (showBottomToolbar) {
            toolbar.visibility = View.VISIBLE
            // Show address bar but hide redundant elements when bottom toolbar is enabled
            binding.toolbarLayout.visibility = View.VISIBLE
            binding.tabButton.visibility = View.GONE // Hide tab counter (we have it in bottom toolbar)
            binding.menuButton.visibility = View.GONE // Hide old menu button (we have it in bottom toolbar)
        } else {
            toolbar.visibility = View.GONE
            // Show original layout when bottom toolbar is disabled
            binding.toolbarLayout.visibility = View.VISIBLE
            binding.tabButton.visibility = View.VISIBLE
            binding.menuButton.visibility = View.VISIBLE
        }

        if (showBottomToolbar) {
            // Set up bottom toolbar listener only when bottom toolbar is enabled
            toolbar.listener = object : ContextualBottomToolbar.ContextualToolbarListener {
                override fun onBackClicked() {
                    // On homepage, back button should be disabled, but kept for consistency
                }

                override fun onForwardClicked() {
                    // Forward not applicable on homepage
                }

                override fun onShareClicked() {
                    // Share not applicable on homepage, kept for consistency
                }

                override fun onSearchClicked() {
                    // Focus on search - same as clicking the search bar
                    navigateToSearch()
                }

                override fun onBookmarksClicked() {
                    // Open bookmarks modal bottom sheet
                    val bookmarksBottomSheet =
                        com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment.newInstance()
                    bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                }

                override fun onNewTabClicked() {
                    // Add new tab
                    browsingModeManager.mode = BrowsingMode.Normal
                    when (UserPreferences(requireContext()).homepageType) {
                        HomepageChoice.VIEW.ordinal -> {
                            components.tabsUseCases.addTab.invoke("about:homepage", selectTab = true)
                        }

                        HomepageChoice.BLANK_PAGE.ordinal -> {
                            components.tabsUseCases.addTab.invoke("about:blank", selectTab = true)
                        }

                        HomepageChoice.CUSTOM_PAGE.ordinal -> {
                            components.tabsUseCases.addTab.invoke(
                                UserPreferences(requireContext()).customHomepageUrl,
                                selectTab = true
                            )
                        }
                    }
                }

                override fun onTabCountClicked() {
                    // Open tabs bottom sheet
                    openTabDrawer()
                }

                override fun onMenuClicked() {
                    // Create and show the home menu anchored to the bottom toolbar button
                    val context = requireContext()
                    val bottomMenuButton = binding.contextualBottomToolbar.findViewById<View>(R.id.menu_button)

                    // Create home menu directly and show it
                    HomeMenu(
                        lifecycleOwner = viewLifecycleOwner,
                        context = context,
                        onItemTapped = { item ->
                            when (item) {
                                HomeMenu.Item.NewTab -> {
                                    browsingModeManager.mode = BrowsingMode.Normal
                                    when (UserPreferences(requireContext()).homepageType) {
                                        HomepageChoice.VIEW.ordinal -> {
                                            components.tabsUseCases.addTab.invoke("about:homepage", selectTab = true)
                                        }

                                        HomepageChoice.BLANK_PAGE.ordinal -> {
                                            components.tabsUseCases.addTab.invoke("about:blank", selectTab = true)
                                        }

                                        HomepageChoice.CUSTOM_PAGE.ordinal -> {
                                            components.tabsUseCases.addTab.invoke(
                                                UserPreferences(requireContext()).customHomepageUrl,
                                                selectTab = true
                                            )
                                        }
                                    }
                                }

                                HomeMenu.Item.NewPrivateTab -> {
                                    browsingModeManager.mode = BrowsingMode.Private
                                    when (UserPreferences(requireContext()).homepageType) {
                                        HomepageChoice.VIEW.ordinal -> {
                                            components.tabsUseCases.addTab.invoke(
                                                "about:homepage",
                                                selectTab = true,
                                                private = true
                                            )
                                        }

                                        HomepageChoice.BLANK_PAGE.ordinal -> {
                                            components.tabsUseCases.addTab.invoke(
                                                "about:blank",
                                                selectTab = true,
                                                private = true
                                            )
                                        }

                                        HomepageChoice.CUSTOM_PAGE.ordinal -> {
                                            components.tabsUseCases.addTab.invoke(
                                                UserPreferences(requireContext()).customHomepageUrl,
                                                selectTab = true, private = true
                                            )
                                        }
                                    }
                                }

                                HomeMenu.Item.Settings -> {
                                    val settings = Intent(activity, SettingsActivity::class.java)
                                    settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    requireActivity().startActivity(settings)
                                }

                                HomeMenu.Item.Bookmarks -> {
                                    val bookmarksBottomSheet =
                                        com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment.newInstance()
                                    bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                                }

                                HomeMenu.Item.History -> {
                                    val settings = Intent(activity, HistoryActivity::class.java)
                                    settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    activity?.startActivity(settings)
                                }

                                HomeMenu.Item.AddonsManager -> {
                                    val settings = Intent(activity, AddonsActivity::class.java)
                                    settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    activity?.startActivity(settings)
                                }

                                else -> {}
                            }
                        },
                        onMenuBuilderChanged = { menuBuilder ->
                            val menu = menuBuilder.build(context)

                            // Create a temporary view above the button for better positioning
                            val tempView = View(context)
                            tempView.layoutParams = ViewGroup.LayoutParams(1, 1)

                            // Add the temp view to the parent layout
                            val parent = binding.contextualBottomToolbar
                            parent.addView(tempView)

                            // Position the temp view above the menu button
                            tempView.x = bottomMenuButton.x
                            tempView.y = bottomMenuButton.y - 60 // 60px above the button

                            // Show menu anchored to temp view
                            menu.show(anchor = tempView)

                            // Clean up temp view after menu interaction
                            tempView.postDelayed({
                                try {
                                    parent.removeView(tempView)
                                } catch (e: Exception) {
                                    // Ignore if view was already removed
                                }
                            }, 12000) // 12 seconds cleanup delay
                        }
                    )
                }
            }

            // Update toolbar context for homepage
            updateContextualToolbarForHomepage()
        }
    }

    private fun updateContextualToolbarForHomepage() {
        val toolbar = binding.contextualBottomToolbar
        if (toolbar.isVisible) {
            val store = requireContext().components.store.state
            val tabCount = if (browsingModeManager.mode.isPrivate) {
                store.privateTabs.size
            } else {
                store.normalTabs.size
            }

            toolbar.updateForContext(
                tab = null, // No specific tab on homepage
                canGoBack = false, // Can't go back from homepage
                canGoForward = false, // Can't go forward from homepage
                tabCount = tabCount,
                isHomepage = true // This is the homepage
            )
        }
    }

    private fun setupPrivateBrowsingTheme() {
        // Purple/dark theme for private browsing (like Firefox)
        binding.toolbarWrapper.background = context?.let {
            ContextCompat.getDrawable(it, R.drawable.toolbar_background_private)
        }

        // Change background to darker shade
        binding.homeLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.photonViolet80))

        // Update app bar with private browsing colors
        binding.homeAppBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.photonViolet80))

        // Change app icon tint for private mode
        binding.appIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.photonWhite))

        // Update app name color
        binding.appName.setTextColor(ContextCompat.getColor(requireContext(), R.color.photonWhite))

        // Update shortcuts header color
        binding.shortcutName.setTextColor(ContextCompat.getColor(requireContext(), R.color.photonWhite))

    }

    private fun setupNormalBrowsingTheme() {
        // Don't override background colors - let XML handle theme-aware colors
        binding.toolbarWrapper.background = context?.let {
            ContextCompat.getDrawable(it, R.drawable.toolbar_background)
        }

        // Remove background color overrides to respect XML theme attributes
        // binding.homeLayout already uses ?attr/colorSurface in XML
        // binding.homeAppBar already uses ?attr/colorSurface in XML

        // Reset app icon tint to allow original colors
        binding.appIcon.clearColorFilter()

        // Remove text color overrides to respect XML/theme defaults
        // Let the XML handle theme-aware text colors

    }

    companion object {
        private const val FOCUS_ON_ADDRESS_BAR = "focusOnAddressBar"
    }
}
