package com.prirai.android.nira.browser.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.databinding.FragmentTabSearchBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import androidx.navigation.fragment.NavHostFragment

class TabSearchFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTabSearchBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var searchAdapter: TabSearchAdapter
    private val searchResults = mutableListOf<SearchResultItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Use dynamic color context if available
        val userPreferences = com.prirai.android.nira.preferences.UserPreferences(requireContext())
        val contextForInflater = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && userPreferences.dynamicColors) {
            com.google.android.material.color.DynamicColors.wrapContextIfAvailable(requireContext())
        } else {
            requireContext()
        }
        
        val themedInflater = inflater.cloneInContext(contextForInflater)
        _binding = FragmentTabSearchBinding.inflate(themedInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSearchView()
        setupRecyclerView()
        
        // Auto-focus the search view
        binding.searchView.requestFocus()
        binding.searchView.isIconified = false
    }
    
    override fun onStart() {
        super.onStart()
        
        // Apply Material 3 dynamic theming
        dialog?.window?.let { window ->
            val userPreferences = com.prirai.android.nira.preferences.UserPreferences(requireContext())
            val isDarkTheme = com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())
            
            // Apply dynamic colors if enabled
            val bgColor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && userPreferences.dynamicColors) {
                val dynamicContext = com.google.android.material.color.DynamicColors.wrapContextIfAvailable(requireContext())
                val typedValue = android.util.TypedValue()
                dynamicContext.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
                if (userPreferences.amoledMode && isDarkTheme) {
                    android.graphics.Color.BLACK
                } else {
                    typedValue.data
                }
            } else {
                // Apply Material 3 background color with proper theming
                if (userPreferences.amoledMode && isDarkTheme) {
                    android.graphics.Color.BLACK
                } else {
                    val typedValue = android.util.TypedValue()
                    if (requireContext().theme.resolveAttribute(
                        com.google.android.material.R.attr.colorSurface, typedValue, true
                    )) {
                        typedValue.data
                    } else {
                        androidx.core.content.ContextCompat.getColor(
                            requireContext(), 
                            com.prirai.android.nira.R.color.m3_surface
                        )
                    }
                }
            }
            
            window.decorView.setBackgroundColor(bgColor)
            window.navigationBarColor = bgColor
            
            // Set status bar color for edge-to-edge
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        
        // Match the TabsBottomSheetFragment height behavior
        val bottomSheetDialog = dialog as com.google.android.material.bottomsheet.BottomSheetDialog
        val behavior = bottomSheetDialog.behavior
        
        val screenHeight = resources.displayMetrics.heightPixels
        val desiredHeight = (screenHeight * 0.85).toInt()
        
        behavior.isFitToContents = false
        behavior.peekHeight = desiredHeight
        behavior.expandedOffset = screenHeight - desiredHeight
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isHideable = true
        behavior.isDraggable = true
        
        bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
            val layoutParams = bottomSheet.layoutParams
            layoutParams.height = desiredHeight
            bottomSheet.layoutParams = layoutParams
            
            // Apply window insets to handle gesture navigation
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { v, insets ->
                insets
            }
        }
        
        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSearch(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    searchResults.clear()
                    searchAdapter.notifyDataSetChanged()
                    binding.emptyState.visibility = View.VISIBLE
                    binding.resultsRecyclerView.visibility = View.GONE
                } else {
                    performSearch(newText)
                }
                return true
            }
        })
    }

    private fun setupRecyclerView() {
        searchAdapter = TabSearchAdapter(
            items = searchResults,
            onItemClick = { item ->
                when (item) {
                    is SearchResultItem.HeaderResult -> {
                        // Headers are not clickable
                    }
                    is SearchResultItem.SubHeaderResult -> {
                        // Sub-headers are not clickable
                    }
                    is SearchResultItem.TabResult -> {
                        // Switch to the tab
                        requireContext().components.tabsUseCases.selectTab(item.tab.id)
                        
                        // Navigate to browser
                        try {
                            val navController = NavHostFragment.findNavController(this)
                            if (navController.currentDestination?.id == R.id.homeFragment) {
                                (requireActivity() as BrowserActivity).openToBrowser(
                                    from = com.prirai.android.nira.BrowserDirection.FromHome,
                                    customTabSessionId = item.tab.id
                                )
                            }
                        } catch (e: Exception) {
                            // Ignore navigation errors
                        }
                        dismiss()
                    }
                    is SearchResultItem.BookmarkResult -> {
                        // Open bookmark in new tab
                        val activity = requireActivity() as BrowserActivity
                        val isPrivate = activity.browsingModeManager.mode.isPrivate
                        val currentProfile = activity.browsingModeManager.currentProfile
                        val contextId = if (isPrivate) "private" else "profile_${currentProfile.id}"
                        
                        requireContext().components.tabsUseCases.addTab(
                            url = item.url,
                            private = isPrivate,
                            contextId = contextId,
                            selectTab = true
                        )
                        dismiss()
                    }
                    is SearchResultItem.HistoryResult -> {
                        // Open history in new tab
                        val activity = requireActivity() as BrowserActivity
                        val isPrivate = activity.browsingModeManager.mode.isPrivate
                        val currentProfile = activity.browsingModeManager.currentProfile
                        val contextId = if (isPrivate) "private" else "profile_${currentProfile.id}"
                        
                        requireContext().components.tabsUseCases.addTab(
                            url = item.url,
                            private = isPrivate,
                            contextId = contextId,
                            selectTab = true
                        )
                        dismiss()
                    }
                }
            }
        )
        
        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            searchResults.clear()
            
            // Search tabs and create grouped results
            val tabResults = searchTabs(query)
            if (tabResults.isNotEmpty()) {
                searchResults.add(SearchResultItem.HeaderResult("Tabs"))
                // Group by profile
                val groupedTabs = tabResults.groupBy { it.profileName }
                groupedTabs.forEach { (profileName, items) ->
                    searchResults.add(SearchResultItem.SubHeaderResult(profileName))
                    searchResults.addAll(items)
                }
            }
            
            // Search bookmarks and create grouped results
            val bookmarkResults = searchBookmarks(query)
            if (bookmarkResults.isNotEmpty()) {
                searchResults.add(SearchResultItem.HeaderResult("Bookmarks"))
                // Group by folder path
                val groupedBookmarks = bookmarkResults.groupBy { it.folderPath }
                groupedBookmarks.forEach { (path, items) ->
                    searchResults.add(SearchResultItem.SubHeaderResult(path))
                    searchResults.addAll(items)
                }
            }
            
            // Search history and create grouped results
            val historyResults = searchHistory(query)
            if (historyResults.isNotEmpty()) {
                searchResults.add(SearchResultItem.HeaderResult("History"))
                // Group by time period
                val groupedHistory = historyResults.groupBy { it.timeGroup }
                groupedHistory.forEach { (timeGroup, items) ->
                    searchResults.add(SearchResultItem.SubHeaderResult(timeGroup))
                    searchResults.addAll(items)
                }
            }
            
            // Update UI
            if (searchResults.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.resultsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.resultsRecyclerView.visibility = View.VISIBLE
            }
            
            searchAdapter.notifyDataSetChanged()
        }
    }

    private fun searchTabs(query: String): List<SearchResultItem.TabResult> {
        val store = requireContext().components.store.state
        val profileManager = ProfileManager.getInstance(requireContext())
        val allProfiles = profileManager.getAllProfiles()
        
        // Get tabs from all non-private profiles
        val results = mutableListOf<SearchResultItem.TabResult>()
        
        store.tabs.forEach { tab ->
            if (!tab.content.private) {
                val title = tab.content.title.ifEmpty { tab.content.url }
                val url = tab.content.url
                
                if (title.contains(query, ignoreCase = true) || url.contains(query, ignoreCase = true)) {
                    // Find profile for this tab
                    val profileId = tab.contextId?.removePrefix("profile_") ?: ""
                    val profile = allProfiles.find { it.id == profileId }
                    
                    // Find group for this tab
                    val unifiedGroupManager = com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager.getInstance(requireContext())
                    val group = unifiedGroupManager.getAllGroups().find { it.tabIds.contains(tab.id) }
                    
                    results.add(
                        SearchResultItem.TabResult(
                            tab = tab,
                            profileName = profile?.let { "${it.emoji} ${it.name}" } ?: "Unknown",
                            groupName = group?.name
                        )
                    )
                }
            }
        }
        
        return results.take(10) // Limit results
    }

    private fun searchBookmarks(query: String): List<SearchResultItem.BookmarkResult> {
        val bookmarkManager = com.prirai.android.nira.browser.bookmark.repository.BookmarkManager.getInstance(requireContext())
        val results = mutableListOf<SearchResultItem.BookmarkResult>()
        
        try {
            searchBookmarksRecursive(bookmarkManager.root, query, results, "Bookmarks")
        } catch (e: Exception) {
            // Handle error silently
        }
        
        return results.take(10) // Limit results
    }

    private fun searchBookmarksRecursive(
        folder: com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem,
        query: String,
        results: MutableList<SearchResultItem.BookmarkResult>,
        path: String
    ) {
        folder.list.forEach { item ->
            when (item) {
                is com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem -> {
                    val title = item.title ?: ""
                    if (title.contains(query, ignoreCase = true) || 
                        item.url.contains(query, ignoreCase = true)) {
                        
                        results.add(
                            SearchResultItem.BookmarkResult(
                                title = title,
                                url = item.url,
                                folderPath = path
                            )
                        )
                    }
                }
                is com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem -> {
                    val newPath = if (item.title?.isNotEmpty() == true) {
                        "$path > ${item.title}"
                    } else {
                        path
                    }
                    searchBookmarksRecursive(item, query, results, newPath)
                }
            }
        }
    }

    private suspend fun searchHistory(query: String): List<SearchResultItem.HistoryResult> {
        val results = mutableListOf<SearchResultItem.HistoryResult>()
        
        try {
            val historyStorage = requireContext().components.historyStorage
            val visits = historyStorage.getDetailedVisits(0, excludeTypes = emptyList())
            
            visits.forEach { visit ->
                if (visit.title?.contains(query, ignoreCase = true) == true || 
                    visit.url.contains(query, ignoreCase = true)) {
                    
                    val timeGroup = getTimeGroup(visit.visitTime)
                    
                    results.add(
                        SearchResultItem.HistoryResult(
                            title = visit.title ?: visit.url,
                            url = visit.url,
                            visitTime = visit.visitTime,
                            timeGroup = timeGroup
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
        
        return results.take(10) // Limit results
    }
    
    private fun getTimeGroup(visitTime: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - visitTime
        
        return when {
            diff < 3600000 -> "Last hour" // Less than 1 hour
            diff < 86400000 -> "Today" // Less than 24 hours
            diff < 172800000 -> "Yesterday" // Less than 48 hours
            diff < 604800000 -> "This week" // Less than 7 days
            diff < 2592000000 -> "This month" // Less than 30 days
            else -> "Older"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TabSearchFragment"
        fun newInstance() = TabSearchFragment()
    }
}

sealed class SearchResultItem {
    data class HeaderResult(val title: String) : SearchResultItem()
    data class SubHeaderResult(val title: String) : SearchResultItem()
    
    data class TabResult(
        val tab: TabSessionState,
        val profileName: String,
        val groupName: String?
    ) : SearchResultItem()
    
    data class BookmarkResult(
        val title: String,
        val url: String,
        val folderPath: String
    ) : SearchResultItem()
    
    data class HistoryResult(
        val title: String,
        val url: String,
        val visitTime: Long,
        val timeGroup: String
    ) : SearchResultItem()
}
