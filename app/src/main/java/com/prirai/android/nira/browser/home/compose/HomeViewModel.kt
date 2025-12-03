package com.prirai.android.nira.browser.home.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.browser.shortcuts.ShortcutDao
import com.prirai.android.nira.browser.shortcuts.ShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val context: Context,
    private val bookmarkManager: BookmarkManager,
    private val shortcutDao: ShortcutDao
) : ViewModel() {
    
    private val _shortcuts = MutableStateFlow<List<ShortcutItem>>(emptyList())
    val shortcuts: StateFlow<List<ShortcutItem>> = _shortcuts.asStateFlow()
    
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()
    
    private val _showAddShortcutDialog = MutableStateFlow(false)
    val showAddShortcutDialog: StateFlow<Boolean> = _showAddShortcutDialog.asStateFlow()
    
    private val _isBookmarkSectionExpanded = MutableStateFlow(true)
    val isBookmarkSectionExpanded: StateFlow<Boolean> = _isBookmarkSectionExpanded.asStateFlow()
    
    init {
        loadShortcuts()
        loadBookmarks()
    }
    
    fun loadShortcuts() {
        viewModelScope.launch {
            try {
                val entities = withContext(Dispatchers.IO) {
                    shortcutDao.getAll()
                }
                _shortcuts.value = entities.map { entity ->
                    ShortcutItem(
                        id = entity.uid,
                        title = entity.title ?: "",
                        url = entity.url ?: "",
                        icon = null
                    )
                }.take(12)
            } catch (e: Exception) {
                _shortcuts.value = emptyList()
            }
        }
    }
    
    fun loadBookmarks() {
        viewModelScope.launch {
            try {
                val firstLevelBookmarks = mutableListOf<BookmarkItem>()
                
                // Get first level items from root folder
                withContext(Dispatchers.IO) {
                    bookmarkManager.root.list.forEach { item ->
                        when (item) {
                            is BookmarkSiteItem -> {
                                firstLevelBookmarks.add(
                                    BookmarkItem(
                                        id = item.id.toString(),
                                        title = item.title ?: "",
                                        url = item.url ?: "",
                                        icon = null,
                                        isFolder = false
                                    )
                                )
                            }
                            is BookmarkFolderItem -> {
                                firstLevelBookmarks.add(
                                    BookmarkItem(
                                        id = item.id.toString(),
                                        title = item.title ?: "Folder",
                                        url = "", // Folders don't have URLs
                                        icon = null,
                                        isFolder = true
                                    )
                                )
                            }
                        }
                    }
                }
                
                _bookmarks.value = firstLevelBookmarks.take(20)
            } catch (e: Exception) {
                _bookmarks.value = emptyList()
            }
        }
    }
    
    fun addShortcut(url: String, title: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val entity = ShortcutEntity(url = url, title = title)
                    shortcutDao.insertAll(entity)
                }
                loadShortcuts()
                _showAddShortcutDialog.value = false
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun deleteShortcut(shortcut: ShortcutItem) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val entity = shortcutDao.loadAllByIds(intArrayOf(shortcut.id)).firstOrNull()
                    if (entity != null) {
                        shortcutDao.delete(entity)
                    }
                }
                loadShortcuts()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun showAddShortcutDialog() {
        _showAddShortcutDialog.value = true
    }
    
    fun hideAddShortcutDialog() {
        _showAddShortcutDialog.value = false
    }
    
    fun toggleBookmarkSection() {
        _isBookmarkSectionExpanded.value = !_isBookmarkSectionExpanded.value
    }
}

class HomeViewModelFactory(
    private val context: Context,
    private val bookmarkManager: BookmarkManager,
    private val shortcutDao: ShortcutDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(context, bookmarkManager, shortcutDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
