package com.prirai.android.nira.browser.shortcuts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.prirai.android.nira.browser.home.compose.AddShortcutDialog
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddShortcutDialogFragment : DialogFragment() {
    
    private var initialUrl: String = ""
    private var initialTitle: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUrl = arguments?.getString(ARG_URL) ?: ""
        initialTitle = arguments?.getString(ARG_TITLE) ?: ""
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Material_Dialog)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NiraTheme {
                    AddShortcutDialog(
                        onDismiss = { dismiss() },
                        onSave = { url, title ->
                            lifecycleScope.launch {
                                addShortcut(url, title)
                                dismiss()
                            }
                        },
                        initialUrl = initialUrl,
                        initialTitle = initialTitle
                    )
                }
            }
        }
    }
    
    private suspend fun addShortcut(url: String, title: String) {
        val context = requireContext().applicationContext
        try {
            // Use migrations to match ComposeHomeFragment database setup
            val MIGRATION_1_2: androidx.room.migration.Migration = object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE shortcutentity ADD COLUMN title TEXT")
                }
            }

            val MIGRATION_2_3: androidx.room.migration.Migration = object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE shortcutentity_new (uid INTEGER NOT NULL, url TEXT, title TEXT, PRIMARY KEY(uid))")
                    db.execSQL("INSERT INTO shortcutentity_new (uid, url, title) SELECT uid, url, title FROM shortcutentity")
                    db.execSQL("DROP TABLE shortcutentity")
                    db.execSQL("ALTER TABLE shortcutentity_new RENAME TO shortcutentity")
                }
            }
            
            val database = Room.databaseBuilder(
                context,
                ShortcutDatabase::class.java,
                "shortcut-database"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
            
            withContext(Dispatchers.IO) {
                val entity = ShortcutEntity(url = url, title = title)
                database.shortcutDao().insertAll(entity)
            }
            
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "Added to favorites",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "Failed to add favorite: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TITLE = "title"
        const val TAG = "AddShortcutDialog"
        
        fun newInstance(url: String, title: String): AddShortcutDialogFragment {
            return AddShortcutDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putString(ARG_TITLE, title)
                }
            }
        }
    }
}
