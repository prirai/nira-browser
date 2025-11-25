package com.prirai.android.nira.browser.bookmark.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.databinding.FragmentFolderSelectionBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FolderSelectionBottomSheetFragment : BottomSheetDialogFragment() {
    
    private var _binding: FragmentFolderSelectionBottomSheetBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var manager: BookmarkManager
    private lateinit var folderAdapter: FolderSelectionAdapter
    private lateinit var currentFolder: BookmarkFolderItem
    private var selectedFolder: BookmarkFolderItem? = null
    private var excludeItem: BookmarkItem? = null
    private var onFolderSelectedListener: ((BookmarkFolderItem) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderSelectionBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        loadFolders()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderSelectionAdapter { folder ->
            if (folder == null) {
                // Parent folder (".." item)
                currentFolder.parent?.let { parent ->
                    setCurrentFolder(parent)
                }
            } else {
                // Navigate into folder or select it
                setCurrentFolder(folder)
            }
        }
        
        binding.foldersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = folderAdapter
        }
    }

    private fun setupClickListeners() {
        binding.addFolderButton.setOnClickListener {
            showAddFolderDialog()
        }
        
        binding.backButton.setOnClickListener {
            currentFolder.parent?.let { parent ->
                setCurrentFolder(parent)
            }
        }
        
        binding.confirmButton.setOnClickListener {
            selectedFolder = currentFolder
            onFolderSelectedListener?.invoke(currentFolder)
            dismiss()
        }
    }

    private fun loadFolders() {
        setCurrentFolder(currentFolder)
    }

    private fun setCurrentFolder(folder: BookmarkFolderItem) {
        currentFolder = folder
        selectedFolder = folder
        
        // Update UI
        if (folder == manager.root) {
            binding.backButton.visibility = View.GONE
            binding.folderTitle.text = getString(R.string.select_folder)
        } else {
            binding.backButton.visibility = View.VISIBLE
            binding.folderTitle.text = folder.title ?: "Folder"
        }
        
        // Create folder list
        val folders = mutableListOf<BookmarkFolderItem?>()
        
        // Add parent folder option if not at root
        if (folder.parent != null) {
            folders.add(null) // null represents ".." (parent folder)
        }
        
        // Add child folders (excluding the item being moved and its descendants)
        folder.itemList.forEach { item ->
            if (item is BookmarkFolderItem && !shouldExcludeFolder(item)) {
                folders.add(item)
            }
        }
        
        folderAdapter.updateFolders(folders)
        
        // Update confirm button text
        binding.confirmButton.text = getString(R.string.select_folder_confirm, 
            if (folder == manager.root) "Root" else folder.title)
    }

    private fun showAddFolderDialog() {
        val dialog = AddBookmarkFolderDialog(
            context = requireContext(),
            mManager = manager,
            title = null,
            mParent = currentFolder,
            item = null
        )
        dialog.setOnClickListener { _, _ ->
            // Refresh folder list after adding new folder
            setCurrentFolder(currentFolder)
        }
        dialog.show()
    }

    fun setManager(manager: BookmarkManager): FolderSelectionBottomSheetFragment {
        this.manager = manager
        return this
    }

    fun setInitialFolder(folder: BookmarkFolderItem): FolderSelectionBottomSheetFragment {
        this.currentFolder = folder
        return this
    }

    fun setOnFolderSelectedListener(listener: (BookmarkFolderItem) -> Unit): FolderSelectionBottomSheetFragment {
        this.onFolderSelectedListener = listener
        return this
    }

    fun setExcludeItem(item: BookmarkItem?): FolderSelectionBottomSheetFragment {
        this.excludeItem = item
        return this
    }

    private fun shouldExcludeFolder(folder: BookmarkFolderItem): Boolean {
        if (excludeItem == null) return false
        
        // Exclude the item itself if it's a folder
        if (folder == excludeItem) return true
        
        // Exclude if this folder is a descendant of the excluded item (if it's a folder)
        if (excludeItem is BookmarkFolderItem) {
            return isDescendantOf(folder, excludeItem as BookmarkFolderItem)
        }
        
        return false
    }

    private fun isDescendantOf(folder: BookmarkFolderItem, ancestor: BookmarkFolderItem): Boolean {
        var current = folder.parent
        while (current != null) {
            if (current == ancestor) return true
            current = current.parent
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FolderSelectionBottomSheetFragment {
            return FolderSelectionBottomSheetFragment()
        }
    }
}