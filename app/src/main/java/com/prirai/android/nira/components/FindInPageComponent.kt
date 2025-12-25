package com.prirai.android.nira.components

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.prirai.android.nira.R
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession

/**
 * Custom Material 3 Find in Page component that works for both regular tabs and custom tabs.
 * Provides a clean, customizable search interface.
 */
class FindInPageComponent(
    private val context: Context,
    private val store: BrowserStore,
    private val sessionId: String,
    private val lifecycleOwner: LifecycleOwner,
    private val isCustomTab: Boolean = false
) {
    
    private var rootView: MaterialCardView? = null
    private var findQueryText: EditText? = null
    private var findResultText: MaterialTextView? = null
    private var isAttached = false
    private var parentLayout: ViewGroup? = null
    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    
    /**
     * Attach the Find in Page bar to a parent layout.
     */
    fun attach(parentLayout: ViewGroup) {
        if (isAttached) return
        
        this.parentLayout = parentLayout
        
        // Inflate the find in page layout
        val findLayout = LayoutInflater.from(context)
            .inflate(R.layout.custom_find_in_page, null) as MaterialCardView
        
        // Add to parent with proper layout params and margins
        val margin = context.resources.getDimensionPixelSize(R.dimen.find_in_page_margin)
        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            leftMargin = margin
            rightMargin = margin
            bottomMargin = margin
        }
        
        parentLayout.addView(findLayout, layoutParams)
        
        // Initialize views
        rootView = findLayout
        findQueryText = findLayout.findViewById(R.id.findQueryText)
        findResultText = findLayout.findViewById(R.id.findResultText)
        
        // Set up keyboard visibility listener to adjust position
        setupKeyboardListener(parentLayout)
        
        // Set up listeners
        setupListeners(findLayout)
        
        // Observe engine session for find results
        observeFindResults()
        
        isAttached = true
    }
    
    private fun setupKeyboardListener(parent: ViewGroup) {
        keyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            parent.getWindowVisibleDisplayFrame(rect)
            val screenHeight = parent.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            // If keyboard is visible (height > 15% of screen)
            if (keypadHeight > screenHeight * 0.15) {
                // Position find bar above keyboard
                adjustPositionForKeyboard(keypadHeight)
            } else {
                // Reset to bottom with margin
                resetPosition()
            }
        }
        parent.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener)
    }
    
    private fun adjustPositionForKeyboard(keyboardHeight: Int) {
        val margin = context.resources.getDimensionPixelSize(R.dimen.find_in_page_margin)
        rootView?.let { view ->
            val layoutParams = view.layoutParams as? FrameLayout.LayoutParams
            layoutParams?.apply {
                bottomMargin = keyboardHeight + margin
                view.layoutParams = this
            }
        }
    }
    
    private fun resetPosition() {
        val margin = context.resources.getDimensionPixelSize(R.dimen.find_in_page_margin)
        rootView?.let { view ->
            val layoutParams = view.layoutParams as? FrameLayout.LayoutParams
            layoutParams?.apply {
                bottomMargin = margin
                view.layoutParams = this
            }
        }
    }
    
    private fun setupListeners(findLayout: View) {
        // Text watcher for search
        findQueryText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performFind(s?.toString() ?: "")
            }
        })
        
        // Previous button
        findLayout.findViewById<View>(R.id.findPrevButton)?.setOnClickListener {
            getEngineSession()?.findNext(forward = false)
        }
        
        // Next button
        findLayout.findViewById<View>(R.id.findNextButton)?.setOnClickListener {
            getEngineSession()?.findNext(forward = true)
        }
        
        // Close button
        findLayout.findViewById<View>(R.id.findCloseButton)?.setOnClickListener {
            hide()
        }
        
        // IME action
        findQueryText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                getEngineSession()?.findNext(forward = true)
                true
            } else {
                false
            }
        }
    }
    
    private fun observeFindResults() {
        // For now, we'll skip automatic result count updates
        // The find functionality will still work, just without showing counts
        // This can be enhanced later by observing engine session callbacks
    }
    
    private fun updateResultCount(currentIndex: Int, totalMatches: Int) {
        val text = if (totalMatches > 0) {
            "${currentIndex + 1} of $totalMatches"
        } else {
            "No matches"
        }
        findResultText?.text = text
    }
    
    private fun performFind(query: String) {
        if (query.isEmpty()) {
            findResultText?.text = ""
            getEngineSession()?.clearFindMatches()
            return
        }
        
        getEngineSession()?.findAll(query)
    }
    
    private fun getEngineSession(): EngineSession? {
        val tab = if (isCustomTab) {
            store.state.findCustomTab(sessionId)
        } else {
            store.state.findTab(sessionId)
        }
        return tab?.engineState?.engineSession
    }
    
    /**
     * Show the Find in Page bar and focus the search input.
     */
    fun show() {
        rootView?.isVisible = true
        
        // Post to ensure view is laid out before focusing
        rootView?.post {
            findQueryText?.requestFocus()
            
            // Show keyboard with a slight delay to ensure it opens
            findQueryText?.postDelayed({
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(findQueryText, InputMethodManager.SHOW_FORCED)
            }, 100)
        }
    }
    
    /**
     * Hide the Find in Page bar and clear the search.
     */
    fun hide() {
        // Hide keyboard first
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(findQueryText?.windowToken, 0)
        
        // Clear find matches
        getEngineSession()?.clearFindMatches()
        
        // Clear UI
        findQueryText?.text?.clear()
        findResultText?.text = ""
        
        // Hide the bar
        rootView?.isVisible = false
        
        // Reset position
        resetPosition()
    }
    
    /**
     * Clean up resources when component is destroyed.
     */
    fun destroy() {
        keyboardListener?.let { listener ->
            parentLayout?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        }
        keyboardListener = null
        parentLayout = null
    }
    
    /**
     * Check if the Find in Page bar is currently visible.
     */
    fun isVisible(): Boolean = rootView?.isVisible == true
    
    /**
     * Handle back button press. Returns true if handled.
     */
    fun onBackPressed(): Boolean {
        return if (isVisible()) {
            hide()
            true
        } else {
            false
        }
    }
}
