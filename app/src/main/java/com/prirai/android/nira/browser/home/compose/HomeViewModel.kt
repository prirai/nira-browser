package com.prirai.android.nira.browser.home.compose

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
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.storage.VisitType

class HomeViewModel(
    private val bookmarkManager: BookmarkManager,
    private val shortcutDao: ShortcutDao,
    private val historyStorage: HistoryStorage,
) : ViewModel() {
    
    private val _shortcuts = MutableStateFlow<List<ShortcutItem>>(emptyList())
    val shortcuts: StateFlow<List<ShortcutItem>> = _shortcuts.asStateFlow()
    
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()
    
    private val _showAddShortcutDialog = MutableStateFlow(false)
    val showAddShortcutDialog: StateFlow<Boolean> = _showAddShortcutDialog.asStateFlow()
    
    private val _isBookmarkSectionExpanded = MutableStateFlow(true)
    val isBookmarkSectionExpanded: StateFlow<Boolean> = _isBookmarkSectionExpanded.asStateFlow()

    private val _jumpBackInHistory = MutableStateFlow<List<JumpBackInItem>>(emptyList())
    val jumpBackInHistory: StateFlow<List<JumpBackInItem>> = _jumpBackInHistory.asStateFlow()
    
    init {
        loadShortcuts()
        loadBookmarks()
        loadJumpBackInHistory()
    }

    fun loadJumpBackInHistory() {
        viewModelScope.launch {
            try {
                val visits = withContext(Dispatchers.IO) {
                    historyStorage.getVisitsPaginated(
                        offset = 0,
                        count = HISTORY_PAGE_SIZE,
                        excludeTypes = EXCLUDED_VISIT_TYPES,
                    )
                }
                val seen = linkedSetOf<String>()
                _jumpBackInHistory.value = visits.mapNotNull { visit ->
                    val url = visit.url
                    if (!url.startsWith("http") || !seen.add(url)) {
                        null
                    } else {
                        JumpBackInItem(
                            id = "history:$url",
                            url = url,
                            title = visit.title?.ifBlank { url } ?: url,
                        )
                    }
                }
            } catch (e: Exception) {
                _jumpBackInHistory.value = emptyList()
            }
        }
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
                                        url = item.url,
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
                
                // Get all first-level bookmarks (no limit)
                _bookmarks.value = firstLevelBookmarks
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
    private val bookmarkManager: BookmarkManager,
    private val shortcutDao: ShortcutDao,
    private val historyStorage: HistoryStorage,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(bookmarkManager, shortcutDao, historyStorage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private const val HISTORY_PAGE_SIZE = 40L

private val EXCLUDED_VISIT_TYPES = listOf(
    VisitType.DOWNLOAD,
    VisitType.REDIRECT_PERMANENT,
    VisitType.REDIRECT_TEMPORARY,
    VisitType.RELOAD,
    VisitType.EMBED,
    VisitType.FRAMED_LINK,
)
