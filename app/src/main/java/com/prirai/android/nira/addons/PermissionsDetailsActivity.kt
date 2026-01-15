package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.getParcelableExtraCompat
import com.prirai.android.nira.theme.applyCompleteTheme
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.applyPersistentInsets
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.AddonPermissionsAdapter
import mozilla.components.feature.addons.ui.translateName

private const val LEARN_MORE_URL =
    "https://smartcookieweb.com/help-biscuit/extensions/#permission-requests"


// An activity to show the permissions of an add-on.
class PermissionsDetailsActivity : AppCompatActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_add_on_permissions)
        
        applyCompleteTheme(this)
        
        // Enable edge-to-edge with standardized approach
        enableEdgeToEdgeMode()
        
        val addon = requireNotNull(intent.getParcelableExtraCompat<Addon>("add_on"))
        title = addon.translateName(this)

        // Apply insets to root view
        findViewById<View>(R.id.addon_permissions)?.applyPersistentInsets()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        supportActionBar?.elevation = 0f

        val recyclerView = findViewById<RecyclerView>(R.id.add_ons_permissions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val sortedPermissions = addon.translatePermissions(this).sorted()
        recyclerView.adapter = AddonPermissionsAdapter(sortedPermissions)

        findViewById<View>(R.id.learn_more_label).setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(this, false)
    }

    override fun onClick(v: View?) {
        val intent =
            Intent(Intent.ACTION_VIEW).setData(LEARN_MORE_URL.toUri())
        startActivity(intent)
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
