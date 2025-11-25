package com.prirai.android.nira.browser.bookmark.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem

class FolderSelectionAdapter(
    private val onFolderClickListener: (BookmarkFolderItem?) -> Unit
) : RecyclerView.Adapter<FolderSelectionAdapter.FolderViewHolder>() {

    private val folders = mutableListOf<BookmarkFolderItem?>()

    fun updateFolders(newFolders: List<BookmarkFolderItem?>) {
        folders.clear()
        folders.addAll(newFolders)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.folder_selection_item, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder, onFolderClickListener)
    }

    override fun getItemCount(): Int = folders.size

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderIcon: ImageView = itemView.findViewById(R.id.folderIcon)
        private val folderName: TextView = itemView.findViewById(R.id.folderName)

        fun bind(folder: BookmarkFolderItem?, onClickListener: (BookmarkFolderItem?) -> Unit) {
            if (folder == null) {
                // Parent folder (".." item)
                folderIcon.setImageResource(R.drawable.ic_ios_back)
                folderName.text = ".."
            } else {
                folderIcon.setImageResource(R.drawable.ic_baseline_folder)
                folderName.text = folder.title ?: "Unnamed Folder"
            }

            itemView.setOnClickListener {
                onClickListener(folder)
            }
        }
    }
}