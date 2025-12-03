package com.prirai.android.nira.browser.bookmark

import android.content.Context
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import mozilla.components.concept.storage.BookmarkInfo
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.BookmarksStorage
import java.util.UUID

class CustomBookmarksStorage(context: Context): BookmarksStorage {

    private val manager = BookmarkManager.getInstance(context)

    override suspend fun addFolder(parentGuid: String, title: String, position: UInt?): Result<String> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun addItem(
        parentGuid: String,
        url: String,
        title: String,
        position: UInt?
    ): Result<String> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun addSeparator(parentGuid: String, position: UInt?): Result<String> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override fun cleanup() {
        // No-op: Bookmark cleanup handled by BookmarkManager save/load cycle
    }

    override suspend fun countBookmarksInTrees(guids: List<String>): UInt {
        // Count all bookmarks recursively
        var count = 0u
        fun countInFolder(folder: com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem) {
            folder.list.forEach { item ->
                when (item) {
                    is BookmarkSiteItem -> count++
                    is com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem -> countInFolder(item)
                }
            }
        }
        countInFolder(manager.root)
        return count
    }

    override suspend fun deleteNode(guid: String): Result<Boolean> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun getBookmark(guid: String): Result<BookmarkNode?> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun getBookmarksWithUrl(url: String): Result<List<BookmarkNode>> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun getRecentBookmarks(
        limit: Int,
        maxAge: Long?,
        currentTime: Long
    ): Result<List<BookmarkNode>> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun getTree(guid: String, recursive: Boolean): Result<BookmarkNode?> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun runMaintenance(dbSizeLimit: UInt) {
        // Perform basic maintenance: save bookmarks to ensure data persistence
        manager.save()
    }

    override suspend fun searchBookmarks(query: String, limit: Int): Result<List<BookmarkNode>> {
        return try {
            val bookmarks: MutableList<BookmarkNode> = emptyList<BookmarkNode>().toMutableList()
            for(i in manager.root.itemList){
                if(i is BookmarkSiteItem){
                    bookmarks.add(BookmarkNode(BookmarkNodeType.ITEM, UUID.randomUUID().toString(), "",
                        0u, i.title, i.url, 0, 0, null))
                }
            }

            val filteredBookmarks = bookmarks.filter { s -> s.title?.contains(query) == true || s.url?.contains(query) == true }.take(limit)
            Result.success(filteredBookmarks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateNode(guid: String, info: BookmarkInfo): Result<Unit> {
        return Result.failure(NotImplementedError("Not yet implemented"))
    }

    override suspend fun warmUp() {
        // Pre-load bookmark data by ensuring manager is initialized
        manager.initialize()
    }
}