package com.prirai.android.nira.browser.bookmark.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BookmarkSortType
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest
import java.util.Locale


open class BookmarkAdapter(
    protected val context: Context,
    list: MutableList<BookmarkItem>,
    private val bookmarkItemListener: OnBookmarkRecyclerListener
) : ArrayRecyclerAdapter<BookmarkItem, BookmarkAdapter.BookmarkItemHolder>(context, list, null) {

    var isMultiSelectMode = false
        private set
    private val multiSelectedItems = mutableSetOf<BookmarkItem>()

    init {
        setRecyclerListener(object : BookmarkRecyclerViewClickInterface {
            override fun onRecyclerItemClicked(v: View, position: Int) {
                if (isMultiSelectMode) {
                    toggleItemSelection(position)
                } else {
                    bookmarkItemListener.onRecyclerItemClicked(v, position)
                }
            }

            override fun onRecyclerItemLongClicked(v: View, position: Int): Boolean {
                if (!isMultiSelectMode) {
                    startMultiSelectMode()
                    toggleItemSelection(position)
                    return true
                }
                return bookmarkItemListener.onRecyclerItemLongClicked(v, position)
            }
        })
    }

    fun startMultiSelectMode() {
        if (!isMultiSelectMode) {
            isMultiSelectMode = true
            multiSelectedItems.clear()
            notifyDataSetChanged()
            bookmarkItemListener.onMultiSelectModeChanged(true, 0)
        }
    }

    fun exitMultiSelectMode() {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            multiSelectedItems.clear()
            notifyDataSetChanged()
            bookmarkItemListener.onMultiSelectModeChanged(false, 0)
        }
    }

    fun toggleItemSelection(position: Int) {
        val item = get(position)
        if (multiSelectedItems.contains(item)) {
            multiSelectedItems.remove(item)
        } else {
            multiSelectedItems.add(item)
        }
        notifyItemChanged(position)
        bookmarkItemListener.onMultiSelectModeChanged(true, multiSelectedItems.size)
    }

    fun getSelectedBookmarkItems(): List<BookmarkItem> = multiSelectedItems.toList()

    fun isItemSelected(item: BookmarkItem): Boolean = multiSelectedItems.contains(item)

    fun clearSelection() {
        multiSelectedItems.clear()
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: BookmarkItemHolder, item: BookmarkItem, position: Int) {
        super.onBindViewHolder(holder, item, position)

        if (item is BookmarkSiteItem && holder is BookmarkSiteHolder) {
            holder.url.text = item.url

            CoroutineScope(Dispatchers.Main).launch{
                val bitmap: Bitmap
                withContext(Dispatchers.IO) {
                    bitmap = context.components.icons.loadIcon(IconRequest(item.url)).await().bitmap
                }

                withContext(Dispatchers.Main){
                    holder.icon.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): BookmarkItemHolder {
        return when (viewType) {
            TYPE_SITE -> BookmarkSiteHolder(inflater.inflate(R.layout.bookmark_item_site, parent, false), this)
            TYPE_FOLDER -> BookmarkItemHolder(inflater.inflate(R.layout.bookmark_item_folder, parent, false), this)
            else -> throw IllegalStateException()
        }
    }

    protected fun onOverflowButtonClick(v: View, position: Int, item: BookmarkItem) {
        val calPosition = searchPosition(position, item)
        if (calPosition < 0){
            return
        }
        bookmarkItemListener.onShowMenu(v, calPosition)
    }

    override fun getItemViewType(position: Int): Int {
        return when (get(position)) {
            is BookmarkSiteItem -> TYPE_SITE
            is BookmarkFolderItem -> TYPE_FOLDER
            else -> throw IllegalStateException()
        }
    }

    class BookmarkSiteHolder(itemView: View, adapter: BookmarkAdapter) : BookmarkItemHolder(itemView, adapter) {
        // TODO: setting text size here, when customization settings are added
        val url: TextView = itemView.findViewById(R.id.urlTextView)
    }

    open class BookmarkItemHolder(itemView: View, private val bookmarkAdapter: BookmarkAdapter) : ArrayViewHolder<BookmarkItem>(itemView, bookmarkAdapter) {
        val title: TextView = itemView.findViewById(R.id.titleTextView)
        val icon: ImageButton = itemView.findViewById(R.id.imageButton)
        val more: ImageButton = itemView.findViewById(R.id.dropdownBookmark)
        val checkbox: android.widget.CheckBox = itemView.findViewById(R.id.selectionCheckbox)

        init {
            more.setOnClickListener {
                bookmarkAdapter.onOverflowButtonClick(more, adapterPosition, item)
            }
        }

        override fun setUp(item: BookmarkItem) {
            super.setUp(item)
            title.text = item.title
            
            // Update checkbox visibility and state based on multiselect mode
            if (bookmarkAdapter.isMultiSelectMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.isChecked = bookmarkAdapter.isItemSelected(item)
                more.visibility = View.GONE
            } else {
                checkbox.visibility = View.GONE
                more.visibility = View.VISIBLE
            }
        }
    }

    fun sortBookmarks(sortType: BookmarkSortType) {
        when (sortType) {
            BookmarkSortType.A_Z -> items.sortBy { it.title?.lowercase(Locale.getDefault()) }
            BookmarkSortType.Z_A -> items.sortByDescending { it.title?.lowercase(Locale.getDefault()) }
            BookmarkSortType.MANUAL -> {} // Do nothing, keep the manual order
        }
        notifyDataSetChanged()
    }

    interface OnBookmarkRecyclerListener : BookmarkRecyclerViewClickInterface {
        fun onIconClick(v: View, position: Int)

        fun onShowMenu(v: View, position: Int)

        fun onSelectionStateChange(items: Int)

        fun onMultiSelectModeChanged(isEnabled: Boolean, selectedCount: Int)
    }

    companion object {
        const val TYPE_SITE = 1
        const val TYPE_FOLDER = 2
    }
}