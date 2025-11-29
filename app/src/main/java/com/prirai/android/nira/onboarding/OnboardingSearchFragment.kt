package com.prirai.android.nira.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.SearchEngineList
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.state.search.SearchEngine

class OnboardingSearchFragment : Fragment() {
    
    private lateinit var userPreferences: UserPreferences
    private lateinit var searchEngines: List<SearchEngine>
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_search, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userPreferences = UserPreferences(requireContext())
        searchEngines = SearchEngineList(requireContext()).getEngines()
        
        // Set DuckDuckGo as default (index 1) if Google is selected
        // This ensures DuckDuckGo is the default for new users
        val prefs = requireContext().getSharedPreferences("scw_preferences", android.content.Context.MODE_PRIVATE)
        if (!prefs.contains("search_engine_choice")) {
            userPreferences.searchEngineChoice = 1 // DuckDuckGo
        }
        
        val searchEngineContainer: GridLayout = view.findViewById(R.id.searchEngineContainer)
        
        // Dynamically create cards for each search engine
        searchEngines.forEachIndexed { index, engine ->
            val card = layoutInflater.inflate(R.layout.item_search_engine_card, searchEngineContainer, false) as MaterialCardView
            
            val icon = card.findViewById<ImageView>(R.id.searchEngineIcon)
            val name = card.findViewById<TextView>(R.id.searchEngineName)
            
            icon.setImageBitmap(engine.icon)
            name.text = engine.name
            
            // Set layout params for GridLayout
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(8, 8, 8, 8)
            card.layoutParams = params
            
            card.setOnClickListener {
                userPreferences.searchEngineChoice = index
                updateSelection()
            }
            
            searchEngineContainer.addView(card)
        }
        
        updateSelection()
    }
    
    private fun updateSelection() {
        val searchEngineContainer: GridLayout = requireView().findViewById(R.id.searchEngineContainer)
        
        for (i in 0 until searchEngineContainer.childCount) {
            val card = searchEngineContainer.getChildAt(i) as MaterialCardView
            card.isChecked = (i == userPreferences.searchEngineChoice)
        }
    }
}
