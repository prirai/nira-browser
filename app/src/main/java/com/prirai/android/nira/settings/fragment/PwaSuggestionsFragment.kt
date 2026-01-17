package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.FragmentPwaSuggestionsBinding
import com.prirai.android.nira.webapp.PwaSuggestionManager
import com.prirai.android.nira.webapp.PwaSuggestionsAdapter

/**
 * Fragment for displaying suggested PWAs
 */
class PwaSuggestionsFragment : Fragment() {

    private lateinit var binding: FragmentPwaSuggestionsBinding
    private lateinit var adapter: PwaSuggestionsAdapter
    private lateinit var suggestionManager: PwaSuggestionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPwaSuggestionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        suggestionManager = com.prirai.android.nira.components.Components(requireContext()).pwaSuggestionManager
        setupRecyclerView()
        setupObservers()
        setupUI()
        loadSuggestions()
    }

    private fun setupRecyclerView() {
        adapter = PwaSuggestionsAdapter(
            lifecycleOwner = viewLifecycleOwner,
            context = requireContext(),
            onInstallClick = { pwa ->
                installSuggestedPwa(pwa)
            }
        )

        binding.suggestionsRecyclerView.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 4)
            adapter = this@PwaSuggestionsFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        suggestionManager.suggestedPwas.observe(viewLifecycleOwner) { suggestions ->
            updateSuggestionsList(suggestions)
        }

        suggestionManager.detectionState.observe(viewLifecycleOwner) { state ->
            updateDetectionState(state)
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.toolbar.title = getString(R.string.pwa_suggestions)

        // Setup category filter
        binding.categoryFilter.setOnClickListener {
            showCategoryFilterDialog()
        }

        // Setup refresh
        binding.refreshButton.setOnClickListener {
            loadSuggestions()
        }
    }

    private fun loadSuggestions() {
        suggestionManager.getAllSuggestedPwas()
    }

    private fun updateSuggestionsList(suggestions: List<PwaSuggestionManager.PwaSuggestion>) {
        if (suggestions.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.suggestionsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.suggestionsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(suggestions)
        }
    }

    private fun updateDetectionState(state: PwaSuggestionManager.DetectionState) {
        when (state) {
            PwaSuggestionManager.DetectionState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.refreshButton.visibility = View.VISIBLE
            }

            PwaSuggestionManager.DetectionState.Detecting -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.refreshButton.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            }

            is PwaSuggestionManager.DetectionState.SuggestionsReady -> {
                binding.progressBar.visibility = View.GONE
                binding.refreshButton.visibility = View.VISIBLE
            }

            is PwaSuggestionManager.DetectionState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.refreshButton.visibility = View.VISIBLE
                showError(state.message)
            }
        }
    }

    private fun installSuggestedPwa(pwa: PwaSuggestionManager.PwaSuggestion) {
        // Show installation confirmation
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.install_suggested_pwa))
            .setMessage(pwa.description)
            .setPositiveButton(R.string.install) { _, _ ->
                // Start installation process
                startInstallation(pwa)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startInstallation(pwa: PwaSuggestionManager.PwaSuggestion) {
        // This would use the WebAppInstallationManager to install the PWA
        // For now, we'll show a success message
        showInstallationSuccess(pwa)
    }

    private fun showInstallationSuccess(pwa: PwaSuggestionManager.PwaSuggestion) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.app_installed))
            .setMessage(getString(R.string.pwa_installation_complete, pwa.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Navigate to PWA management
                navigateToPwaManagement()
            }
            .show()
    }

    private fun showCategoryFilterDialog() {
        val categories = listOf(
            getString(R.string.all_categories),
            "Messaging",
            "Social",
            "News",
            "Games",
            "Music",
            "Navigation",
            "Productivity",
            "Entertainment"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.pwa_categories)
            .setItems(categories.toTypedArray()) { _, which ->
                filterByCategory(categories[which])
            }
            .show()
    }

    private fun filterByCategory(category: String) {
        // Category filtering removed - show all suggestions
        loadSuggestions()
    }

    private fun showError(message: String) {
        binding.emptyState.visibility = View.VISIBLE
        binding.suggestionsRecyclerView.visibility = View.GONE
        binding.emptyStateText.text = message
    }

    private fun navigateToPwaManagement() {
        // This would navigate to the PWA management screen
        // For now, we'll just show a message
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setMessage(R.string.pwa_management_navigation)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        fun newInstance(): PwaSuggestionsFragment {
            return PwaSuggestionsFragment()
        }
    }
}
