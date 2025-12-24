package com.prirai.android.nira.browser.bookmark.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BookmarkSortType
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.databinding.FragmentBookmarksBottomSheetBinding
import com.prirai.android.nira.ext.components
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import mozilla.components.browser.state.selector.selectedTab

class BookmarksBottomSheetFragment : BottomSheetDialogFragment(), BookmarkAdapter.OnBookmarkRecyclerListener {
    
    private var _binding: FragmentBookmarksBottomSheetBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var bookmarkAdapter: BookmarkAdapter
    private lateinit var manager: BookmarkManager
    private lateinit var currentFolder: BookmarkFolderItem
    private var isMultiSelectMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarksBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set bottom sheet to 60% of screen height
        setupBottomSheetHeight()
        
        setupBookmarkManager()
        setupRecyclerView()
        setupClickListeners()
        loadBookmarks()
    }

    private fun setupBottomSheetHeight() {
        // Get screen height
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        
        // Set bottom sheet to 60% of screen height
        val sheetHeight = (screenHeight * 0.6).toInt()
        
        // Calculate RecyclerView max height (subtract header/padding)
        val headerHeight = 200 // Approximate header + margins
        val recyclerMaxHeight = sheetHeight - headerHeight
        
        // Set RecyclerView max height
        val recyclerLayoutParams = binding.bookmarksRecyclerView.layoutParams
        recyclerLayoutParams.height = recyclerMaxHeight
        binding.bookmarksRecyclerView.layoutParams = recyclerLayoutParams
        
        // Apply to bottom sheet behavior
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as? com.google.android.material.bottomsheet.BottomSheetDialog)
                ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.peekHeight = sheetHeight
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                behavior.isDraggable = false // Prevent dragging
            }
        }
    }

    private fun setupBookmarkManager() {
        manager = BookmarkManager.getInstance(requireActivity())
        
        // Get folderId from arguments if passed
        val folderId = arguments?.getLong(ARG_FOLDER_ID, -1L) ?: -1L
        
        // Find the folder if folderId is valid
        currentFolder = if (folderId != -1L) {
            findFolderById(manager.root, folderId) ?: manager.root
        } else {
            manager.root
        }
    }
    
    private fun findFolderById(folder: BookmarkFolderItem, targetId: Long): BookmarkFolderItem? {
        if (folder.id == targetId) return folder
        
        for (item in folder.list) {
            if (item is BookmarkFolderItem) {
                val found = findFolderById(item, targetId)
                if (found != null) return found
            }
        }
        return null
    }

    private fun setupRecyclerView() {
        binding.bookmarksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun loadBookmarks() {
        setBookmarkList(currentFolder)
    }

    private fun setBookmarkList(folder: BookmarkFolderItem) {
        currentFolder = folder
        
        // Create adapter with current folder's items
        bookmarkAdapter = BookmarkAdapter(requireContext(), folder.itemList, this)
        binding.bookmarksRecyclerView.adapter = bookmarkAdapter
        
        if (folder.itemList.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
        }
        
        // Update navigation UI based on current folder
        if (folder == manager.root) {
            // At root level
            binding.backButton.visibility = View.GONE
            binding.bookmarksTitle.text = getString(R.string.action_bookmarks)
            binding.pathView.visibility = View.GONE
        } else {
            // In a subfolder
            binding.backButton.visibility = View.VISIBLE
            binding.bookmarksTitle.text = folder.title
            binding.pathView.visibility = View.VISIBLE
            // TODO: Update path breadcrumbs
        }
    }

    private fun setupClickListeners() {
        binding.addBookmarkButton.setOnClickListener {
            // Show add bookmark dialog (similar to original implementation)
            showAddBookmarkDialog()
        }
        
        binding.addFolderButton.setOnClickListener {
            // Show add folder dialog
            showAddFolderDialog()
        }
        
        binding.sortBookmarksButton.setOnClickListener {
            // Show sort options dialog
            showSortDialog()
        }
        
        binding.multiselectButton.setOnClickListener {
            // Toggle multiselect mode
            bookmarkAdapter.startMultiSelectMode()
        }
        
        binding.backButton.setOnClickListener {
            // Navigate back to parent folder
            currentFolder.parent?.let { parentFolder ->
                setBookmarkList(parentFolder)
            } ?: run {
                // Already at root, go back to root
                setBookmarkList(manager.root)
            }
        }
        
        // Multiselect toolbar actions - set up after view is inflated
        view?.post {
            view?.findViewById<android.widget.ImageButton>(R.id.closeMultiSelectButton)?.setOnClickListener {
                exitMultiSelectMode()
            }
            
            view?.findViewById<android.widget.LinearLayout>(R.id.openInBackgroundButton)?.setOnClickListener {
                openSelectedInBackground()
            }
            
            view?.findViewById<android.widget.LinearLayout>(R.id.moveSelectedButton)?.setOnClickListener {
                moveSelectedItems()
            }
            
            view?.findViewById<android.widget.LinearLayout>(R.id.deleteSelectedButton)?.setOnClickListener {
                deleteSelectedItems()
            }
        }
    }

    private fun showAddBookmarkDialog() {
        // Show add bookmark dialog for current page
        val store = requireContext().components.store
        val selectedTab = store.state.selectedTab
        val title = selectedTab?.content?.title ?: ""
        val url = selectedTab?.content?.url ?: ""
        
        val dialog = AddBookmarkSiteDialog(requireActivity(), title, url)
        dialog.setOnClickListener { _, _ ->
            // Save changes to persistent storage
            manager.save()
            // Refresh the bookmark list after adding
            setBookmarkList(currentFolder)
        }
        dialog.show()
    }

    private fun showAddFolderDialog() {
        // Show add folder dialog - create new folder in current folder
        val dialog = AddBookmarkFolderDialog(
            context = requireContext(),
            mManager = manager,
            title = null, // Empty title for new folder
            mParent = currentFolder, // Create in current folder
            item = null // null = create new folder (not edit existing)
        )
        dialog.setOnClickListener { _, _ ->
            // Refresh the bookmark list after adding folder (manager.save() is called inside dialog)
            setBookmarkList(currentFolder)
        }
        dialog.show()
    }

    private fun showSortDialog() {
        val items = arrayOf(
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_url),
            getString(R.string.sort_by_date_added)
        )
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sort))
            .setItems(items) { _, which ->
                val sortType = when (which) {
                    0 -> BookmarkSortType.A_Z
                    1 -> BookmarkSortType.A_Z // URL sorting not implemented in original
                    2 -> BookmarkSortType.A_Z // Date sorting not implemented in original
                    else -> BookmarkSortType.A_Z
                }
                bookmarkAdapter.sortBookmarks(sortType)
            }
            .create()
        dialog.show()
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.bookmarksRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.bookmarksRecyclerView.visibility = View.VISIBLE
    }

    // BookmarkAdapter.OnBookmarkRecyclerListener implementation - required methods
    override fun onIconClick(v: View, position: Int) {
        val item = bookmarkAdapter[position]
        if (item is BookmarkSiteItem) {
            onShowMenu(v, position)
        }
    }

    override fun onShowMenu(v: View, position: Int) {
        val item = currentFolder[position]
        
        // Create context menu with bookmark actions
        try {
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), v)
            
            val menuRes = when (item) {
                is BookmarkSiteItem -> {
                    R.menu.bookmark_site_menu
                }
                is BookmarkFolderItem -> {
                    R.menu.bookmark_folder_menu
                }
                else -> {
                    return
                }
            }
            
            popup.menuInflater.inflate(menuRes, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.editBookmark -> {
                    editBookmark(item, position)
                    true
                }
                R.id.deleteBookmark -> {
                    deleteBookmark(item, position)
                    true
                }
                R.id.flattenFolder -> {
                    if (item is BookmarkFolderItem) {
                        flattenFolder(item)
                    }
                    true
                }
                R.id.open -> {
                    if (item is BookmarkSiteItem) {
                        requireContext().components.tabsUseCases.addTab.invoke(
                            url = item.url,
                            selectTab = true
                        )
                        dismiss()
                    }
                    true
                }
                R.id.openNew -> {
                    if (item is BookmarkSiteItem) {
                        requireContext().components.tabsUseCases.addTab.invoke(
                            url = item.url,
                            selectTab = false
                        )
                    }
                    true
                }
                R.id.openBg -> {
                    if (item is BookmarkSiteItem) {
                        requireContext().components.tabsUseCases.addTab.invoke(
                            url = item.url,
                            selectTab = false
                        )
                    }
                    true
                }
                R.id.share -> {
                    if (item is BookmarkSiteItem) {
                        // Share the bookmark URL
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, item.url)
                            type = "text/plain"
                        }
                        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share)))
                    }
                    true
                }
                R.id.moveBookmark -> {
                    // Show folder selection dialog to move bookmark
                    showMoveFolderDialog(item, position)
                    true
                }
                R.id.addShortcut -> {
                    // TODO: Implement add shortcut functionality  
                    true
                }
                else -> false
            }
            }
            
            popup.show()
        } catch (e: Exception) {
        }
    }

    override fun onSelectionStateChange(items: Int) {
        // Handle selection state changes if needed
    }

    override fun onRecyclerItemClicked(v: View, position: Int) {
        when (val item = currentFolder[position]) {
            is BookmarkSiteItem -> {
                // Open bookmark URL and navigate to browser
                requireContext().components.tabsUseCases.addTab.invoke(
                    url = item.url,
                    selectTab = true
                )
                
                // Navigate to browser if on home
                try {
                    val navController = androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    if (navController.currentDestination?.id == R.id.homeFragment) {
                        val directions = com.prirai.android.nira.NavGraphDirections.actionGlobalBrowser(null)
                        navController.navigate(directions)
                    }
                } catch (e: Exception) {
                    // Ignore navigation errors
                }
                
                dismiss()
            }
            is BookmarkFolderItem -> {
                // Navigate into folder
                setBookmarkList(item)
            }
        }
    }

    override fun onRecyclerItemLongClicked(v: View, position: Int): Boolean {
        // Handle long click - could show context menu
        onShowMenu(binding.bookmarksRecyclerView, position)
        return true
    }

    private fun editBookmark(item: BookmarkItem, position: Int) {
        when (item) {
            is BookmarkSiteItem -> {
                // Show edit bookmark dialog
                val dialog = AddBookmarkSiteDialog(requireActivity(), item.title ?: "", item.url)
                dialog.setOnClickListener { _, _ ->
                    // Save changes to persistent storage
                    manager.save()
                    // Refresh the bookmark list after editing
                    setBookmarkList(currentFolder)
                }
                dialog.show()
            }
            is BookmarkFolderItem -> {
                // Show edit folder dialog
                val dialog = AddBookmarkFolderDialog(requireActivity(), manager, item)
                dialog.setOnClickListener { _, _ ->
                    // Save changes to persistent storage
                    manager.save()
                    // Refresh the bookmark list after editing
                    setBookmarkList(currentFolder)
                }
                dialog.show()
            }
        }
    }

    private fun deleteBookmark(item: BookmarkItem, position: Int) {
        // Show confirmation dialog
        val message = when (item) {
            is BookmarkSiteItem -> getString(R.string.delete_bookmark_confirm, item.title ?: "")
            is BookmarkFolderItem -> getString(R.string.delete_folder_confirm, item.title ?: "")
            else -> getString(R.string.delete_confirm)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Delete the bookmark
                try {
                    currentFolder.itemList.remove(item)
                    // Save changes to persistent storage
                    manager.save()
                    // Refresh the bookmark list
                    setBookmarkList(currentFolder)
                } catch (e: Exception) {
                    // Handle deletion error - show user feedback
                    android.widget.Toast.makeText(
                        requireContext(), 
                        "Error deleting bookmark", 
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMoveFolderDialog(item: BookmarkItem, position: Int) {
        // Show modern folder selection bottom sheet for moving
        val folderSelectionSheet = FolderSelectionBottomSheetFragment.newInstance()
            .setManager(manager)
            .setInitialFolder(manager.root)
            .setExcludeItem(item) // Prevent moving folder into itself or its descendants
            .setOnFolderSelectedListener { selectedFolder ->
                moveItemToFolder(item, selectedFolder)
            }
        
        folderSelectionSheet.show(parentFragmentManager, "MoveFolderSelectionBottomSheet")
    }
    
    
    private fun moveItemToFolder(item: BookmarkItem, targetFolder: BookmarkFolderItem) {
        try {
            // Remove from current parent
            currentFolder.itemList.remove(item)
            
            // Add to new parent
            targetFolder.add(item)
            
            // Save changes to persistent storage
            manager.save()
            
            // Refresh the current view
            setBookmarkList(currentFolder)
            
            // Show success message
            android.widget.Toast.makeText(
                requireContext(), 
                "Moved \"${item.title}\" to \"${if (targetFolder == manager.root) "Root" else targetFolder.title}\"", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                requireContext(), 
                "Error moving bookmark", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onMultiSelectModeChanged(isEnabled: Boolean, selectedCount: Int) {
        isMultiSelectMode = isEnabled
        
        val multiselectToolbar = binding.root.findViewById<View>(R.id.multiselectToolbar)
        
        if (isEnabled) {
            // Show multiselect toolbar, hide normal header
            multiselectToolbar?.visibility = View.VISIBLE
            binding.headerLayout.visibility = View.GONE
            
            // Update selected count text
            val countText = getString(R.string.multiselect_mode, selectedCount)
            binding.root.findViewById<android.widget.TextView>(R.id.selectedCountText)?.text = countText
        } else {
            // Hide multiselect toolbar, show normal header
            multiselectToolbar?.visibility = View.GONE
            binding.headerLayout.visibility = View.VISIBLE
        }
    }

    private fun exitMultiSelectMode() {
        bookmarkAdapter.exitMultiSelectMode()
    }

    private fun openSelectedInBackground() {
        val selectedItems = bookmarkAdapter.getSelectedBookmarkItems()
        val siteItems = selectedItems.filterIsInstance<BookmarkSiteItem>()
        
        if (siteItems.isEmpty()) {
            android.widget.Toast.makeText(
                requireContext(),
                "No bookmark sites selected",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Open all selected bookmarks in background tabs
        siteItems.forEach { site ->
            requireContext().components.tabsUseCases.addTab.invoke(
                url = site.url,
                selectTab = false
            )
        }
        
        android.widget.Toast.makeText(
            requireContext(),
            "Opened ${siteItems.size} tabs in background",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        exitMultiSelectMode()
    }

    private fun moveSelectedItems() {
        val selectedItems = bookmarkAdapter.getSelectedBookmarkItems()
        
        if (selectedItems.isEmpty()) {
            return
        }
        
        // Show folder selection dialog for moving multiple items
        val folderSelectionSheet = FolderSelectionBottomSheetFragment.newInstance()
            .setManager(manager)
            .setInitialFolder(manager.root)
            .setExcludeItems(selectedItems) // Prevent moving folders into themselves
            .setOnFolderSelectedListener { targetFolder ->
                moveItemsToFolder(selectedItems, targetFolder)
            }
        
        folderSelectionSheet.show(parentFragmentManager, "MoveMultipleFolderSelectionBottomSheet")
    }

    private fun moveItemsToFolder(items: List<BookmarkItem>, targetFolder: BookmarkFolderItem) {
        try {
            items.forEach { item ->
                // Remove from current parent
                currentFolder.itemList.remove(item)
                
                // Add to new parent
                targetFolder.add(item)
                
                // Update parent reference for folders
                if (item is BookmarkFolderItem) {
                    item.parent = targetFolder
                }
            }
            
            // Save changes to persistent storage
            manager.save()
            
            // Refresh the current view
            setBookmarkList(currentFolder)
            
            // Show success message
            android.widget.Toast.makeText(
                requireContext(),
                "Moved ${items.size} items to \"${if (targetFolder == manager.root) "Root" else targetFolder.title}\"",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            exitMultiSelectMode()
            
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                requireContext(),
                "Error moving items",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteSelectedItems() {
        val selectedItems = bookmarkAdapter.getSelectedBookmarkItems()
        
        if (selectedItems.isEmpty()) {
            return
        }
        
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage("Delete ${selectedItems.size} items?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                try {
                    selectedItems.forEach { item ->
                        currentFolder.itemList.remove(item)
                    }
                    
                    // Save changes to persistent storage
                    manager.save()
                    
                    // Refresh the bookmark list
                    setBookmarkList(currentFolder)
                    
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Deleted ${selectedItems.size} items",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    exitMultiSelectMode()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error deleting items",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun flattenFolder(folder: BookmarkFolderItem) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.flatten_folder)
            .setMessage(getString(R.string.flatten_folder_confirm, folder.title ?: ""))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                try {
                    // Flatten the folder
                    manager.flattenFolder(currentFolder, folder)
                    
                    // Save changes to persistent storage
                    manager.save()
                    
                    // Refresh the bookmark list
                    setBookmarkList(currentFolder)
                    
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Flattened \"${folder.title}\"",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error flattening folder",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FOLDER_ID = "folder_id"
        
        fun newInstance(folderId: Long = -1L): BookmarksBottomSheetFragment {
            return BookmarksBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_FOLDER_ID, folderId)
                }
            }
        }
    }
}