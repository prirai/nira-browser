package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.switchmaterial.SwitchMaterial
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.getParcelableExtraCompat
import com.prirai.android.nira.theme.applyCompleteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.translateName

/**
 * An activity to show the details of a installed add-on.
 */
class InstalledAddonDetailsActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_installed_add_on_details)
        
        applyCompleteTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val addon = requireNotNull(intent.getParcelableExtraCompat<Addon>("add_on"))

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addon_details)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            val insetsController = WindowCompat.getInsetsController(window, v)
            val isDark = com.prirai.android.nira.theme.ThemeManager.isDarkMode(this)
            insetsController.isAppearanceLightStatusBars = !isDark
            WindowInsetsCompat.CONSUMED
        }

        supportActionBar?.elevation = 0f

        bindAddon(addon)
    }

    override fun onResume() {
        super.onResume()
        com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(this, false)
    }

    private fun bindAddon(addon: Addon) {
        scope.launch {
            try {
                val context = baseContext
                val addons = context.components.addonManager.getAddons()
                scope.launch(Dispatchers.Main) {
                    addons.find { addon.id == it.id }.let {
                        if (it == null) {
                            throw AddonManagerException(Exception("Addon ${addon.id} not found"))
                        } else {
                            bindUI(it)
                        }
                    }
                }
            } catch (e: AddonManagerException) {
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(
                            baseContext,
                            R.string.mozac_feature_addons_failed_to_query_extensions,
                            Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun bindUI(addon: Addon) {
        title = addon.translateName(this)

        // Hide irrelevant items when add-on has been sideloaded
        if(!addon.isSupported()){
            findViewById<SwitchMaterial>(R.id.enable_switch).visibility = View.GONE
            findViewById<SwitchCompat>(R.id.allow_in_private_browsing_switch).visibility = View.GONE
            findViewById<View>(R.id.details).visibility = View.GONE
            findViewById<View>(R.id.permissions).visibility = View.GONE
        }

        bindEnableSwitch(addon)

        bindSettings(addon)

        bindDetails(addon)

        bindPermissions(addon)

        bindAllowInPrivateBrowsingSwitch(addon)

        bindRemoveButton(addon)
    }

    private fun bindEnableSwitch(addon: Addon) {
        val switch = findViewById<SwitchMaterial>(R.id.enable_switch)
        switch.setState(addon.isEnabled())
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                this.components.addonManager.enableAddon(
                        addon,
                        onSuccess = {
                            switch.setState(true)
                            Toast.makeText(
                                    this,
                                    getString(R.string.mozac_feature_addons_successfully_enabled, addon.translateName(this)),
                                    Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = {
                            Toast.makeText(
                                    this,
                                    getString(R.string.mozac_feature_addons_failed_to_enable, addon.translateName(this)),
                                    Toast.LENGTH_SHORT
                            ).show()
                        }
                )
            } else {
                this.components.addonManager.disableAddon(
                        addon,
                        onSuccess = {
                            switch.setState(false)
                            Toast.makeText(
                                    this,
                                    getString(R.string.mozac_feature_addons_successfully_disabled, addon.translateName(this)),
                                    Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = {
                            Toast.makeText(
                                    this,
                                    getString(R.string.mozac_feature_addons_failed_to_disable, addon.translateName(this)),
                                    Toast.LENGTH_SHORT
                            ).show()
                        }
                )
            }
        }
    }

    private fun bindSettings(addon: Addon) {
        val view = findViewById<View>(R.id.settings)
        val optionsPageUrl = addon.installedState?.optionsPageUrl
        view.isEnabled = optionsPageUrl != null
        view.setOnClickListener {
            if (addon.installedState?.openOptionsPageInTab == true) {
                components.tabsUseCases.addTab(optionsPageUrl as String)
                val intent = Intent(this, BrowserActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                this.startActivity(intent)
            } else {
                val intent = Intent(this, AddonSettingsActivity::class.java)
                intent.putExtra("add_on", addon)
                this.startActivity(intent)
            }
        }
    }

    private fun bindDetails(addon: Addon) {
        findViewById<View>(R.id.details).setOnClickListener {
            val intent = Intent(this, AddonDetailsActivity::class.java)
            intent.putExtra("add_on", addon)
            this.startActivity(intent)
        }
    }

    private fun bindPermissions(addon: Addon) {
        findViewById<View>(R.id.permissions).setOnClickListener {
            val intent = Intent(this, PermissionsDetailsActivity::class.java)
            intent.putExtra("add_on", addon)
            this.startActivity(intent)
        }
    }

    private fun bindAllowInPrivateBrowsingSwitch(addon: Addon) {
        val switch = findViewById<SwitchCompat>(R.id.allow_in_private_browsing_switch)
        switch.isChecked = addon.isAllowedInPrivateBrowsing()
        switch.setOnCheckedChangeListener { _, isChecked ->
            this.components.addonManager.setAddonAllowedInPrivateBrowsing(
                    addon,
                    isChecked,
                    onSuccess = {
                        switch.isChecked = isChecked
                    }
            )
        }
    }

    private fun bindRemoveButton(addon: Addon) {
        findViewById<View>(R.id.remove_add_on).setOnClickListener {
            this.components.addonManager.uninstallAddon(
                    addon,
                    onSuccess = {
                        // Clear the cache so the bottom sheet shows updated data
                        ExtensionsBottomSheetFragment.clearCache()
                        Toast.makeText(
                                this,
                                getString(R.string.mozac_feature_addons_successfully_uninstalled, addon.translateName(this)),
                                Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onError = { _, _ ->
                        Toast.makeText(
                                this,
                                getString(R.string.mozac_feature_addons_failed_to_uninstall, addon.translateName(this)),
                                Toast.LENGTH_SHORT
                        ).show()
                    }
            )
        }
    }

    private fun SwitchCompat.setState(checked: Boolean) {
        isChecked = checked
    }
}
