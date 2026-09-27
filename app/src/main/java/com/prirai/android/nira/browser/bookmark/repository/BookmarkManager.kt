package com.prirai.android.nira.browser.bookmark.repository

import android.content.Context
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import mozilla.components.support.base.log.logger.Logger
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.io.Serializable

class BookmarkManager private constructor(context: Context) : Serializable {
    val file = File(context.getDir("bookmarks", Context.MODE_PRIVATE), "bookmarks.dat")
    val root = BookmarkFolderItem(null, null, -1)
    private val siteIndex = ArrayList<BookmarkSiteItem>()
    private val siteComparator: Comparator<BookmarkSiteItem> = Comparator { s1, s2 -> s1.url.hashCode().compareTo(s2.url.hashCode()) }
    private val logger = Logger("BookmarkManager")

    init {
        initialize()
    }

    companion object {
        @Volatile
        private var instance: BookmarkManager? = null

        /**
         * Threadsafe singleton accessor. Uses double-checked locking with a
         * `@Volatile` field so that concurrent callers on different threads
         * cannot each construct their own `BookmarkManager` (the old
         * implementation had a plain `if (instance == null) instance = ...`
         * race that could produce two managers pointing at the same
         * `bookmarks.dat` file, with the corresponding possibility of
         * concurrent-writer data corruption).
         */
        fun getInstance(context: Context): BookmarkManager {
            return instance ?: synchronized(this) {
                instance ?: BookmarkManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun initialize(): Boolean {
        root.clear()

        if (!file.exists() || file.isDirectory){
            return true
        }

        try {
            JsonReader.of(file.source().buffer()).use {
                root.readForRoot(it)
                createIndex()

                return true
            }
        } catch (e: IOException) {
            // Bookmarks file is corrupt or unreadable. Return false so callers
            // can react (currently none do - but at least the exception is
            // no longer silently swallowed).
            logger.error("Failed to read bookmarks from ${file.path}", e)
        }
        return false
    }

    fun save(): Boolean {
        file.parentFile?.apply {
            if (!exists()) mkdirs()
        }

        try {
            JsonWriter.of(file.sink().buffer()).use {
                root.writeForRoot(it)
                return true
            }
        } catch (e: IOException) {
            // Bookmarks write failed - disk full, permissions, etc. Callers
            // observe the returned `false`; see BrowserFragment "Add Bookmark"
            // menu action which surfaces this to the user as a Toast.
            logger.error("Failed to save bookmarks to ${file.path}", e)
        }

        return false
    }

    fun addFirst(folder: BookmarkFolderItem, item: BookmarkItem) {
        folder.list.add(0, item)
        if (item is BookmarkSiteItem) {
            addToIndex(item)
        }
    }

    fun add(folder: BookmarkFolderItem, item: BookmarkItem) {
        folder.add(item)
        if (item is BookmarkSiteItem) {
            addToIndex(item)
        }
    }

    fun moveToFirst(folder: BookmarkFolderItem, item: BookmarkItem) {
        folder.list.remove(item)
        folder.list.add(0, item)
    }

    fun remove(folder: BookmarkFolderItem, index: Int) {
        val item = folder.list.removeAt(index)

        if (item is BookmarkSiteItem) {
            removeSiteFromIndex(item)
        }
    }

    fun removeAll(url: String) {
        val it = siteIndex.iterator()
        while (it.hasNext()) {
            val site = it.next()
            if (site.url == url) {
                it.remove()
                recursiveRemove(root, site)
            }
        }
    }

    fun flattenFolder(parentFolder: BookmarkFolderItem, folderToFlatten: BookmarkFolderItem) {
        // Get all items from the folder to be flattened
        val itemsToMove = ArrayList(folderToFlatten.list)
        
        // Move all items to parent folder
        itemsToMove.forEach { item ->
            folderToFlatten.list.remove(item)
            parentFolder.add(item)
            
            // Update parent reference for folders
            if (item is BookmarkFolderItem) {
                item.parent = parentFolder
            }
        }
        
        // Remove the now-empty folder from parent
        parentFolder.list.remove(folderToFlatten)
    }

    private fun recursiveRemove(folder: BookmarkFolderItem, item: BookmarkItem): Boolean {
        val it = folder.list.iterator()
        while (it.hasNext()) {
            val child = it.next()
            if (child is BookmarkFolderItem) {
                if (recursiveRemove(child, item))
                    return true
            } else if (child is BookmarkSiteItem) {
                if (child == item) {
                    it.remove()
                    return true
                }
            }
        }
        return false
    }

    fun moveTo(from: BookmarkFolderItem, to: BookmarkFolderItem, siteIndex: Int) {
        val item = from.list.removeAt(siteIndex)
        to.list.add(item)
        if (item is BookmarkFolderItem) {
            item.parent = to
        }
    }

    fun isBookmarked(url: String?): Boolean {
        if (url == null){
            return false
        }

        var low = 0
        var high = siteIndex.size - 1
        val hash = url.hashCode()

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val itemHash = siteIndex[mid].url.hashCode()
            when {
                itemHash < hash -> low = mid + 1
                itemHash > hash -> high = mid - 1
                else -> {
                    if (url == siteIndex[mid].url) {
                        return true
                    }
                    for (i in mid - 1 downTo 0) {
                        val nowHash = siteIndex[i].hashCode()
                        if (hash != nowHash) {
                            break
                        }
                        if (siteIndex[i].url == url) {
                            return true
                        }
                    }
                    for (i in mid + 1 until siteIndex.size) {
                        val nowHash = siteIndex[i].hashCode()
                        if (hash != nowHash) {
                            break
                        }
                        if (siteIndex[i].url == url) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    operator fun get(id: Long): BookmarkItem? {
        return if (id < 0){
            null
        } else{
            get(id, root)
        }
    }

    private fun get(id: Long, root: BookmarkFolderItem): BookmarkItem? {
        for (item in root.list) {
            if (item.id == id) {
                return item
            } else if (item is BookmarkFolderItem) {
                val inner = get(id, item)
                if (inner != null) {
                    return inner
                }
            }
        }
        return null
    }

    private fun createIndex() {
        siteIndex.clear()
        addToIndexFromFolder(root)
    }

    private fun addToIndexFromFolder(folder: BookmarkFolderItem) {
        folder.list.forEach {
            if (it is BookmarkFolderItem) {
                addToIndexFromFolder(it)
            }
            if (it is BookmarkSiteItem) {
                addToIndex(it)
            }
        }
    }

    private fun addToIndex(site: BookmarkSiteItem) {
        val hash = site.url.hashCode()
        val index = siteIndex.binarySearch(site, siteComparator)
        if (index < 0) {
            siteIndex.add(index.inv(), site)
        } else {
            if (siteIndex[index] != site) {
                for (i in index - 1 downTo 0) {
                    val itemHash = siteIndex[i].url.hashCode()
                    if (hash != itemHash) {
                        break
                    }
                    if (siteIndex[i] == site) {
                        return
                    }
                }

                for (i in index + 1 until siteIndex.size) {
                    val itemHash = siteIndex[i].url.hashCode()
                    if (hash != itemHash) {
                        break
                    }
                    if (siteIndex[i] == site) {
                        return
                    }
                }
                siteIndex.add(index, site)
            }
        }
    }

    private fun removeSiteFromIndex(site: BookmarkSiteItem) {
        val hash = site.url.hashCode()
        val index = siteIndex.binarySearch(site, siteComparator)
        if (index >= 0) {
            if (siteIndex[index] == site) {
                siteIndex.removeAt(index)
                return
            }
            for (i in index - 1 downTo 0) {
                val itemHash = siteIndex[i].url.hashCode()
                if (hash != itemHash) {
                    break
                }
                if (siteIndex[i] == site) {
                    siteIndex.removeAt(index)
                    return
                }
            }
            for (i in index + 1 until siteIndex.size) {
                val itemHash = siteIndex[i].url.hashCode()
                if (hash != itemHash) {
                    break
                }
                if (siteIndex[i] == site) {
                    siteIndex.removeAt(index)
                    return
                }
            }
        }
    }
}