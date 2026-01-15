package com.prirai.android.nira.history

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.VisitInfo

import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.prirai.android.nira.ext.isAppInDarkTheme
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.applyPersistentInsets
import androidx.lifecycle.lifecycleScope


class HistoryActivity : AppCompatActivity(), SearchView.OnQueryTextListener {

    var history: List<VisitInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true); supportActionBar!!.elevation = 0f

        // Enable edge-to-edge with standardized approach
        enableEdgeToEdgeMode()
        
        // Apply insets to root view
        findViewById<View>(R.id.history)?.applyPersistentInsets()

        val recyclerView = findViewById<RecyclerView>(R.id.list)
        val emptyView = findViewById<TextView>(R.id.emptyView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            history = components.historyStorage.getDetailedVisits(0).reversed()
            if (history!!.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = HistoryItemRecyclerViewAdapter(
                    history!!
                )
            }
        }

        recyclerView.addOnItemTouchListener(
            HistoryRecyclerViewItemTouchListener(
                this,
                recyclerView,
                object : HistoryRecyclerViewItemTouchListener.OnItemClickListener {
                    override fun onItemClick(view: View?, position: Int) {
                        onBackPressedDispatcher.onBackPressed()
                        components.sessionUseCases.loadUrl(
                            (recyclerView.adapter as HistoryItemRecyclerViewAdapter).getItem(
                                position
                            ).url
                        )
                    }

                    override fun onLongItemClick(view: View?, position: Int) {
                        val items = arrayOf(
                            resources.getString(R.string.open_new),
                            resources.getString(R.string.open_new_private),
                            resources.getString(R.string.mozac_selection_context_menu_share),
                            resources.getString(R.string.copy),
                            resources.getString(R.string.remove_history_item)
                        )

                        MaterialAlertDialogBuilder(this@HistoryActivity)
                            .setTitle(resources.getString(R.string.action_history))
                            .setItems(items) { dialog, which ->
                                when (which) {
                                    0 -> {
                                        onBackPressedDispatcher.onBackPressed()
                                        components.tabsUseCases.addTab.invoke(
                                            (recyclerView.adapter as HistoryItemRecyclerViewAdapter).getItem(
                                                position
                                            ).url,
                                            selectTab = true
                                        )
                                    }
                                    1 -> {
                                        onBackPressedDispatcher.onBackPressed()
                                        components.tabsUseCases.addTab.invoke(
                                            (recyclerView.adapter as HistoryItemRecyclerViewAdapter).getItem(
                                                position
                                            ).url,
                                            selectTab = true,
                                            private = true
                                        )
                                    }
                                    2 -> {
                                        val shareIntent = Intent(Intent.ACTION_SEND)
                                        shareIntent.type = "text/plain"
                                        shareIntent.putExtra(
                                            Intent.EXTRA_TEXT,
                                            (recyclerView.adapter as HistoryItemRecyclerViewAdapter).getItem(
                                                position
                                            ).url
                                        )
                                        ContextCompat.startActivity(
                                            this@HistoryActivity,
                                            Intent.createChooser(
                                                shareIntent,
                                                resources.getString(R.string.mozac_selection_context_menu_share)
                                            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            null
                                        )
                                    }
                                    3 -> {
                                        val clipboard = getSystemService(
                                            this@HistoryActivity,
                                            ClipboardManager::class.java
                                        )
                                        val clip = ClipData.newPlainText(
                                            "URL",
                                            (recyclerView.adapter as HistoryItemRecyclerViewAdapter).getItem(
                                                position
                                            ).url
                                        )
                                        clipboard?.setPrimaryClip(clip)
                                    }
                                    4 -> {
                                        lifecycleScope.launch {
                                            components.historyStorage.deleteVisit(
                                                (recyclerView.adapter as HistoryItemRecyclerViewAdapter).getItem(
                                                    position
                                                ).url,
                                                (recyclerView.adapter as HistoryItemRecyclerViewAdapter).getItem(
                                                    position
                                                ).visitTime
                                            )
                                            history = components.historyStorage.getDetailedVisits(0)
                                                .reversed()
                                            if (history!!.isEmpty()) {
                                                emptyView.visibility = View.VISIBLE
                                                recyclerView.visibility = View.GONE
                                            } else {
                                                recyclerView.adapter =
                                                    HistoryItemRecyclerViewAdapter(
                                                        history!!
                                                    )
                                                (recyclerView.adapter as HistoryItemRecyclerViewAdapter).notifyItemRemoved(
                                                    position
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            .show()
                    }
                }
            )
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search)
        val searchView: SearchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onQueryTextChange(query: String?): Boolean {
        (findViewById<RecyclerView>(R.id.list).adapter as HistoryItemRecyclerViewAdapter).getFilter()
            ?.filter(query)
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}