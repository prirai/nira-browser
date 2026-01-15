package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.getParcelableExtraCompat
import com.prirai.android.nira.theme.applyCompleteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.setIcon
import mozilla.components.feature.addons.ui.showInformationDialog
import mozilla.components.feature.addons.ui.translateDescription
import mozilla.components.feature.addons.ui.translateName
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.applyPersistentInsets

// An activity to show the details of an add-on.
class AddonDetailsActivity : AppCompatActivity() {

    private val updateAttemptStorage: DefaultAddonUpdater.UpdateAttemptStorage by lazy {
        DefaultAddonUpdater.UpdateAttemptStorage(this)
    }
    
    private val webExtensionPromptFeature = ViewBoundFeatureWrapper<WebExtensionPromptFeature>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_on_details)
        
        applyCompleteTheme(this)
        
        // Enable edge-to-edge with standardized approach
        enableEdgeToEdgeMode()
        
        // Apply insets to root view
        findViewById<View>(R.id.addon_details)?.applyPersistentInsets()

        supportActionBar?.elevation = 0f

        val addon = requireNotNull(intent.getParcelableExtraCompat<Addon>("add_on"))
        
        val rootView = findViewById<View>(R.id.addon_details)
        webExtensionPromptFeature.set(
            feature = WebExtensionPromptFeature(
                store = components.store,
                provideAddons = { components.addonManager.getAddons() },
                context = this,
                view = rootView,
                fragmentManager = supportFragmentManager,
                onLinkClicked = { url, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                }
            ),
            owner = this,
            view = rootView
        )
        
        initViews(addon)
    }

    override fun onResume() {
        super.onResume()
        com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(this, false)
    }

    private fun initViews(addon: Addon) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = resources.getString(R.string.mozac_feature_addons_details)

        val iconView = findViewById<ImageView>(R.id.addon_icon)
        val titleView = findViewById<TextView>(R.id.addon_title)

        val detailsView = findViewById<TextView>(R.id.details)
        val detailsText = addon.translateDescription(this)

        val htmlText = detailsText.replace("\n", "<br>")
        val text = HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_COMPACT)

        iconView.setIcon(addon)

        titleView.text = addon.translateName(this)

        detailsView.text = text
        detailsView.movementMethod = LinkMovementMethod.getInstance()

        val authorsView = findViewById<TextView>(R.id.author_text)

        authorsView.text = addon.author?.name.orEmpty()

        val versionView = findViewById<TextView>(R.id.version_text)
        versionView.text = addon.installedState?.version?.ifEmpty { addon.version } ?: addon.version

        if (addon.isInstalled()) {
            versionView.setOnLongClickListener {
                showUpdaterDialog(addon)
                true
            }
        }

        val lastUpdatedView = findViewById<TextView>(R.id.last_updated_text)
        lastUpdatedView.text = formatDate(addon.updatedAt)

        findViewById<View>(R.id.home_page_text).setOnClickListener {
            val intent =
                Intent(Intent.ACTION_VIEW).setData(addon.homepageUrl.toUri())
            startActivity(intent)
        }

        addon.rating?.let {
            val ratingNum = findViewById<TextView>(R.id.users_count)
            val ratingView = findViewById<RatingBar>(R.id.rating_view)

            ratingNum.text = "(${getFormattedAmount(it.reviews)})"

            val ratingContentDescription =
                getString(R.string.mozac_feature_addons_rating_content_description_2)
            ratingView.contentDescription = String.format(ratingContentDescription, it.average)
            ratingView.rating = it.average
        }
        
        // Install button
        val installButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.install_button)
        if (addon.isInstalled()) {
            installButton.visibility = View.GONE
        } else {
            installButton.setOnClickListener {
                installButton.isEnabled = false
                installButton.text = "Installing..."
                
                applicationContext.components.addonManager.installAddon(
                    url = addon.downloadUrl,
                    installationMethod = mozilla.components.concept.engine.webextension.InstallationMethod.MANAGER,
                    onSuccess = { installedAddon ->
                        runOnUiThread {
                            installButton.isEnabled = true
                            installButton.text = "Install"
                            ExtensionsBottomSheetFragment.clearCache()
                            android.widget.Toast.makeText(
                                this,
                                "Successfully installed ${installedAddon.translateName(this)}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            installButton.isEnabled = true
                            installButton.text = "Install"
                            if (error !is java.util.concurrent.CancellationException) {
                                android.widget.Toast.makeText(
                                    this,
                                    "Failed to install ${addon.translateName(this)}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun showUpdaterDialog(addon: Addon) {
        val context = this@AddonDetailsActivity
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val updateAttempt = updateAttemptStorage.findUpdateAttemptBy(addon.id)
            updateAttempt?.let {
                withContext(Dispatchers.Main) {
                    it.showInformationDialog(context)
                }
            }
        }
    }

    private fun formatDate(text: String): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        return DateFormat.getDateInstance().format(formatter.parse(text)!!)
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
