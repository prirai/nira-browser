package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.FragmentUnifiedWebappBinding
import com.prirai.android.nira.webapp.*
import com.prirai.android.nira.components.Components
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.net.toUri

/**
 * Unified fragment for managing installed PWAs and discovering new ones
 */
class UnifiedWebAppFragment : Fragment() {

    private lateinit var binding: FragmentUnifiedWebappBinding
    private lateinit var installedAdapter: WebAppSettingsMenuAdapter
    private lateinit var suggestionsAdapter: PwaSuggestionsAdapter
    private lateinit var suggestionManager: PwaSuggestionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUnifiedWebappBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        suggestionManager = Components(requireContext()).pwaSuggestionManager
        setupUI()
        setupTabs()
        setupRecyclerViews()
        setupObservers()
    }

    private fun setupUI() {
        // Title is handled by parent SettingsActivity
        // No toolbar setup needed
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.installed_apps))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.discover_apps))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showInstalledApps()
                    1 -> showSuggestedApps()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Select first tab (Installed) by default to trigger initial load
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
    }

    private fun setupRecyclerViews() {
        // Setup installed apps adapter (Linear layout) with three-dot menu
        installedAdapter = WebAppSettingsMenuAdapter(
            lifecycleOwner = viewLifecycleOwner,
            context = requireContext(),
            onWebAppClick = { webApp -> launchWebApp(webApp) },
            onEnableToggle = { webApp, enabled -> toggleWebAppEnabled(webApp, enabled) },
            onAddShortcut = { webApp -> addShortcut(webApp) },
            onAssociateProfile = { webApp -> showAssociateProfileDialog(webApp) },
            onClone = { webApp -> showCloneDialog(webApp) },
            onClearData = { webApp -> showClearDataConfirmation(webApp) },
            onUninstall = { webApp -> showUninstallConfirmation(webApp) }
        )

        // Setup suggestions adapter (Grid layout 4 columns)
        suggestionsAdapter = PwaSuggestionsAdapter(
            lifecycleOwner = viewLifecycleOwner,
            context = requireContext(),
            onInstallClick = { pwa -> installSuggestedPwa(pwa) },
            onLearnMoreClick = { pwa -> showPwaDetails(pwa) }
        )

        // Set initial layout manager for installed apps
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.setHasFixedSize(true)

        // Set initial adapter for installed apps
        binding.recyclerView.adapter = installedAdapter
    }

    private fun setupObservers() {
        // Observe installed apps - always collecting
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.getAllWebApps()
                .collectLatest { webApps ->
                    // Update list regardless of tab position
                    // but only show if on installed tab
                    installedAdapter.submitList(webApps)
                    if (binding.tabLayout.selectedTabPosition == 0) {
                        updateInstalledAppsVisibility(webApps)
                    }
                }
        }

        // Observe suggested apps
        suggestionManager.suggestedPwas.observe(viewLifecycleOwner) { suggestions ->
            suggestionsAdapter.submitList(suggestions)
            if (binding.tabLayout.selectedTabPosition == 1) {
                updateSuggestionsVisibility(suggestions)
            }
        }

        // Load suggestions initially
        suggestionManager.getAllSuggestedPwas()
    }

    private fun updateInstalledAppsVisibility(webApps: List<WebAppEntity>) {
        if (webApps.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateSuggestionsVisibility(suggestions: List<PwaSuggestionManager.PwaSuggestion>) {
        if (suggestions.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showInstalledApps() {
        // Switch to linear layout
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = installedAdapter
        binding.emptyStateText.text = getString(R.string.no_installed_webapps)
        // Data is already loaded via observer, just update visibility
        updateInstalledAppsVisibility(installedAdapter.currentList)
    }

    private fun showSuggestedApps() {
        // Switch to grid layout (4 columns)
        binding.recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 4)
        binding.recyclerView.adapter = suggestionsAdapter
        binding.emptyStateText.text = getString(R.string.no_suggested_webapps)
        // Data is already loaded via observer, just update visibility
        updateSuggestionsVisibility(suggestionsAdapter.currentList)
    }

    private fun updateInstalledAppsList(webApps: List<WebAppEntity>) {
        installedAdapter.submitList(webApps)
        updateInstalledAppsVisibility(webApps)
    }

    private fun updateSuggestionsList(suggestions: List<PwaSuggestionManager.PwaSuggestion>) {
        suggestionsAdapter.submitList(suggestions)
        updateSuggestionsVisibility(suggestions)
    }

    // Installed Apps Actions
    private fun launchWebApp(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.updateWebAppUsage(webApp.id)
            val intent = Intent(requireContext(), WebAppActivity::class.java).apply {
                putExtra(WebAppActivity.EXTRA_WEB_APP_URL, webApp.url)
            }
            startActivity(intent)
        }
    }

    private fun showUninstallConfirmation(webApp: WebAppEntity) {
        WebAppUninstallDialog(
            context = requireContext(),
            webApp = webApp,
            onConfirm = { uninstallWebApp(webApp) }
        ).show()
    }

    private fun uninstallWebApp(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.uninstallWebApp(webApp.id)
        }
    }

    private fun toggleWebAppEnabled(webApp: WebAppEntity, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.setWebAppEnabled(webApp.id, enabled)
        }
    }

    private fun showClearDataConfirmation(webApp: WebAppEntity) {
        WebAppClearDataDialog(
            context = requireContext(),
            webApp = webApp,
            onConfirm = { clearWebAppData(webApp) }
        ).show()
    }

    private fun clearWebAppData(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Clear web app data - this would involve clearing service worker caches, localStorage, etc.
            // For now, just show confirmation
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.success)
                .setMessage(R.string.web_app_data_cleared)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun updatePwaCache(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.updatePwaCache(webApp.id)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.pwa_cache_updated, webApp.name))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun addShortcut(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Load icon if available
                val icon = Components(requireContext()).webAppManager.loadIconFromFile(webApp.iconUrl)

                // Create shortcut using WebAppInstaller
                val context = requireContext()
                val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, "webapp_${webApp.id}")
                    .setShortLabel(webApp.name)
                    .setLongLabel(webApp.name)
                    .setIntent(Intent(context, WebAppActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = webApp.url.toUri()
                        putExtra(WebAppActivity.EXTRA_WEB_APP_URL, webApp.url)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    })
                    .apply {
                        if (icon != null) {
                            setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(icon))
                        } else {
                            setIcon(
                                androidx.core.graphics.drawable.IconCompat.createWithResource(
                                    context,
                                    R.drawable.ic_language
                                )
                            )
                        }
                    }
                    .build()

                // Request to pin the shortcut
                val success = androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)

                if (success) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.shortcut_added)
                        .setMessage(getString(R.string.shortcut_added_message, webApp.name))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.shortcut_failed)
                        .setMessage(R.string.shortcut_failed_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } catch (e: Exception) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.shortcut_failed)
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    // Suggested Apps Actions
    private fun installSuggestedPwa(pwa: PwaSuggestionManager.PwaSuggestion) {
        // Get current profile as default
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val currentProfile = profileManager.getActiveProfile()

        // Show Material 3 dialog with profile selection (icon loads inside dialog)
        val dialog = WebAppInstallDialog(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            appName = pwa.name,
            appUrl = pwa.url,
            appDescription = pwa.description,
            defaultProfileId = currentProfile.id
        ) { selectedProfileId ->
            startInstallation(pwa, selectedProfileId)
        }

        dialog.show()
    }

    private fun startInstallation(pwa: PwaSuggestionManager.PwaSuggestion, profileId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val webAppManager = Components(requireContext()).webAppManager

                // Check if already installed with same profile
                if (webAppManager.webAppExists(pwa.url, profileId)) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.already_installed)
                        .setMessage(getString(R.string.web_app_already_installed_profile))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@launch
                }

                // Load icon for installation using Mozilla Components BrowserIcons
                val icon: Bitmap? = withContext(Dispatchers.IO) {
                    try {
                        // Try BrowserIcons first
                        val iconRequest = mozilla.components.browser.icons.IconRequest(
                            url = pwa.url,
                            size = mozilla.components.browser.icons.IconRequest.Size.DEFAULT,
                            resources = listOf(
                                mozilla.components.browser.icons.IconRequest.Resource(
                                    url = pwa.url,
                                    type = mozilla.components.browser.icons.IconRequest.Resource.Type.FAVICON
                                )
                            )
                        )
                        val iconResult = Components(requireContext()).icons.loadIcon(iconRequest).await()
                        if (iconResult.bitmap != null) {
                            // Save to cache for future use
                            com.prirai.android.nira.utils.FaviconCache.getInstance(requireContext())
                                .saveFavicon(pwa.url, iconResult.bitmap)
                            iconResult.bitmap
                        } else {
                            // Fallback to cache
                            com.prirai.android.nira.utils.FaviconCache.getInstance(requireContext())
                                .loadFavicon(pwa.url)
                        }
                    } catch (e: Exception) {
                        // Fallback to cache on error
                        com.prirai.android.nira.utils.FaviconCache.getInstance(requireContext()).loadFavicon(pwa.url)
                    }
                }

                // Install using WebAppManager with profile
                webAppManager.installWebApp(
                    url = pwa.url,
                    name = pwa.name,
                    manifestUrl = null,
                    icon = icon,
                    themeColor = null,
                    backgroundColor = null,
                    profileId = profileId
                )

                // Create shortcut with the same icon
                val intent = Intent(requireContext(), WebAppActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = pwa.url.toUri()
                    putExtra(WebAppActivity.EXTRA_WEB_APP_URL, pwa.url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                }

                val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(requireContext(), pwa.url)
                    .setShortLabel(pwa.name)
                    .setLongLabel(pwa.name)
                    .setIntent(intent)
                    .apply {
                        if (icon != null) {
                            setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(icon))
                        } else {
                            setIcon(
                                androidx.core.graphics.drawable.IconCompat.createWithResource(
                                    requireContext(),
                                    R.drawable.ic_language
                                )
                            )
                        }
                    }
                    .build()

                androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(requireContext(), shortcut, null)

                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.app_installed))
                    .setMessage(getString(R.string.pwa_installation_complete, pwa.name))
                    .setPositiveButton(R.string.launch) { _, _ ->
                        launchSuggestedPwa(pwa)
                    }
                    .setNegativeButton(android.R.string.ok, null)
                    .show()

                // Switch to installed apps tab
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))

            } catch (e: Exception) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.installation_failed)
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun launchSuggestedPwa(pwa: PwaSuggestionManager.PwaSuggestion) {
        val intent = Intent(requireContext(), WebAppActivity::class.java).apply {
            putExtra(WebAppActivity.EXTRA_WEB_APP_URL, pwa.url)
        }
        startActivity(intent)
    }

    private fun showPwaDetails(pwa: PwaSuggestionManager.PwaSuggestion) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(pwa.name)
            .setMessage(pwa.description)
            .setPositiveButton(R.string.install) { _, _ ->
                installSuggestedPwa(pwa)
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    // New methods for profile association and cloning
    private fun showAssociateProfileDialog(webApp: WebAppEntity) {
        WebAppProfileDialog(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            webApp = webApp,
            onAssociate = { profileId ->
                viewLifecycleOwner.lifecycleScope.launch {
                    Components(requireContext()).webAppManager.updateWebApp(
                        webApp.copy(profileId = profileId)
                    )
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.success)
                        .setMessage(R.string.profile_associated)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        ).show()
    }

    private fun showCloneDialog(webApp: WebAppEntity) {
        WebAppCloneDialog(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            webApp = webApp,
            onClone = { name, profileId ->
                cloneWebApp(webApp, name, profileId)
            }
        ).show()
    }

    private fun cloneWebApp(webApp: WebAppEntity, newName: String, newProfileId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val webAppManager = Components(requireContext()).webAppManager
                webAppManager.installWebApp(
                    url = webApp.url,
                    name = newName,
                    manifestUrl = webApp.manifestUrl,
                    icon = webAppManager.loadIconFromFile(webApp.iconUrl),
                    themeColor = webApp.themeColor,
                    backgroundColor = webApp.backgroundColor,
                    profileId = newProfileId
                )

                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.success)
                    .setMessage(getString(R.string.web_app_cloned_success, newName))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (e: Exception) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.error)
                    .setMessage(e.message ?: getString(R.string.unknown_error))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    companion object {
        fun newInstance(): UnifiedWebAppFragment {
            return UnifiedWebAppFragment()
        }
    }
}
