package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.graphics.Bitmap
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.net.toUri
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.FragmentWebappSettingsBinding
import com.prirai.android.nira.webapp.WebAppEntity
import com.prirai.android.nira.webapp.WebAppActivity
import com.prirai.android.nira.webapp.WebAppSettingsAdapter
import com.prirai.android.nira.components.Components
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for managing installed Progressive Web Apps
 */
class WebAppSettingsFragment : Fragment() {

    private lateinit var binding: FragmentWebappSettingsBinding
    private lateinit var adapter: WebAppSettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWebappSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupUI()
    }

    private fun setupRecyclerView() {
        adapter = WebAppSettingsAdapter(
            lifecycleOwner = viewLifecycleOwner,
            context = requireContext(),
            onWebAppClick = { webApp ->
                // Launch the PWA
                launchWebApp(webApp)
            },
            onUninstallClick = { webApp ->
                // Show confirmation and uninstall
                showUninstallConfirmation(webApp)
            },
            onEnableToggle = { webApp, enabled ->
                // Toggle PWA enabled state
                toggleWebAppEnabled(webApp, enabled)
            },
            onClearDataClick = { webApp ->
                // Show confirmation and clear data
                showClearDataConfirmation(webApp)
            },
            onUpdateCacheClick = { webApp ->
                // Update PWA cache for offline use
                updatePwaCache(webApp)
            },
            onAddShortcutClick = { webApp ->
                // Add shortcut to home screen
                addShortcut(webApp)
            }
        )

        binding.webAppsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@WebAppSettingsFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.getAllWebApps()
                .collectLatest { webApps ->
                    updateWebAppList(webApps)
                }
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.toolbar.title = getString(R.string.web_app_settings_title)
    }

    private fun updateWebAppList(webApps: List<WebAppEntity>) {
        if (webApps.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.webAppsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.webAppsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(webApps)
        }
    }

    private fun launchWebApp(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.updateWebAppUsage(webApp.id)
            // Launch the PWA using WebAppActivity
            val intent = Intent(requireContext(), WebAppActivity::class.java).apply {
                putExtra(WebAppActivity.EXTRA_WEB_APP_URL, webApp.url)
            }
            startActivity(intent)
        }
    }

    private fun showUninstallConfirmation(webApp: WebAppEntity) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.uninstall_web_app))
            .setMessage(getString(R.string.uninstall_web_app_confirmation, webApp.name))
            .setPositiveButton(R.string.uninstall) { _, _ ->
                uninstallWebApp(webApp)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun uninstallWebApp(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.uninstallWebApp(webApp.id)
            // Also remove the shortcut if it exists
            // TODO: Implement shortcut removal
        }
    }

    private fun toggleWebAppEnabled(webApp: WebAppEntity, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.setWebAppEnabled(webApp.id, enabled)
        }
    }

    private fun showClearDataConfirmation(webApp: WebAppEntity) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_web_app_data))
            .setMessage(getString(R.string.clear_web_app_data_confirmation, webApp.name))
            .setPositiveButton(R.string.clear_data) { _, _ ->
                clearWebAppData(webApp)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearWebAppData(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.clearWebAppData(webApp.id)
            // Show success message
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage(R.string.web_app_data_cleared)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun updatePwaCache(webApp: WebAppEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            Components(requireContext()).webAppManager.updatePwaCache(webApp.id)
            // Show success message
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

                // Create shortcut
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
                androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                // Shortcut will be added by system - no need to show success dialog
            } catch (e: Exception) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.shortcut_failed)
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    companion object {
        fun newInstance(): WebAppSettingsFragment {
            return WebAppSettingsFragment()
        }
    }
}
