package com.prirai.android.nira.request

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import androidx.navigation.NavController
import com.prirai.android.nira.addons.AddonsActivity
// import com.prirai.android.nira.browser.home.HomeFragmentDirections // Removed - using BrowserFragment for homepage
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.browser.errorpages.ErrorType
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.concept.engine.request.RequestInterceptor.InterceptionResponse
import java.lang.ref.WeakReference


class AppRequestInterceptor(val context: Context) : RequestInterceptor {

    private var navController: WeakReference<NavController>? = null

    fun setNavController(navController: NavController) {
        this.navController = WeakReference(navController)
    }

    override fun onLoadRequest(
        engineSession: EngineSession,
        uri: String,
        lastUri: String?,
        hasUserGesture: Boolean,
        isSameDomain: Boolean,
        isRedirect: Boolean,
        isDirectNavigation: Boolean,
        isSubframeRequest: Boolean
    ): InterceptionResponse? {
        
        // Handle custom nira:// scheme for homepage interactions
        if (uri.startsWith("nira://")) {
            handleCustomScheme(uri)
            return InterceptionResponse.Deny
        }
        
        interceptXpiUrl(uri, hasUserGesture)?.let { response ->
            return response
        }

       var response = context.components.appLinksInterceptor.onLoadRequest(
           engineSession, uri, lastUri, hasUserGesture, isSameDomain, isRedirect,
           isDirectNavigation, isSubframeRequest
       )

        if (response == null && !isDirectNavigation) {
            response = context.components.webAppInterceptor.onLoadRequest(
                engineSession, uri, lastUri, hasUserGesture, isSameDomain, isRedirect,
                isDirectNavigation, isSubframeRequest
            )
        }

        return response
    }
    
    private fun handleCustomScheme(uri: String) {
        when {
            uri.startsWith("nira://search?q=") -> {
                val query = uri.substringAfter("q=")
                val decodedQuery = java.net.URLDecoder.decode(query, "UTF-8")
                // Trigger search via components
                context.components.tabsUseCases.addTab(decodedQuery, selectTab = true)
            }
            uri == "nira://focus-search" -> {
                // Navigation to search dialog will be handled by navController
                navController?.get()?.let { nav ->
                    try {
                        // Import necessary navigation directions if available
                        // This would need to be adjusted based on your navigation setup
                    } catch (e: Exception) {
                        // Fallback: do nothing
                    }
                }
            }
            uri.startsWith("nira://delete-shortcut?") -> {
                // Parse URL parameter
                val params = Uri.parse(uri)
                val shortcutId = params.getQueryParameter("id")?.toIntOrNull()
                
                if (shortcutId != null) {
                    // Delete shortcut from database
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val database = androidx.room.Room.databaseBuilder(
                                context,
                                com.prirai.android.nira.browser.shortcuts.ShortcutDatabase::class.java,
                                "shortcut-database"
                            ).build()
                            
                            val shortcutDao = database.shortcutDao()
                            val shortcut = shortcutDao.loadAllByIds(intArrayOf(shortcutId)).firstOrNull()
                            if (shortcut != null) {
                                shortcutDao.delete(shortcut)
                            }
                            database.close()
                            
                            // Reload the page to reflect changes
                            CoroutineScope(Dispatchers.Main).launch {
                                context.components.sessionUseCases.reload()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            uri.startsWith("nira://add-shortcut?") -> {
                // Parse URL parameters
                val params = Uri.parse(uri)
                val url = params.getQueryParameter("url")
                val title = params.getQueryParameter("title")
                
                if (url != null && title != null) {
                    // Save shortcut to database
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val database = androidx.room.Room.databaseBuilder(
                                context,
                                com.prirai.android.nira.browser.shortcuts.ShortcutDatabase::class.java,
                                "shortcut-database"
                            ).build()
                            
                            val entity = com.prirai.android.nira.browser.shortcuts.ShortcutEntity(
                                url = url,
                                title = title
                            )
                            database.shortcutDao().insertAll(entity)
                            database.close()
                        } catch (e: Exception) {
                            // Ignore errors
                        }
                    }
                }
            }
        }
    }

    override fun onErrorRequest(
        session: EngineSession,
        errorType: ErrorType,
        uri: String?
    ): RequestInterceptor.ErrorResponse {
        val riskLevel = getErrorCategory(errorType)

        if (uri == "about:homepage") {
            /* Load HTML-based homepage with injected data
            * This provides consistent toolbar behavior across all pages
            */
            return RequestInterceptor.ErrorResponse(generateHomepageWithData())
        }

        val errorPageUri = ErrorPages.createUrlEncodedErrorPage(
            context = context,
            errorType = errorType,
            uri = uri,
            htmlResource = riskLevel.htmlRes
        )

        return RequestInterceptor.ErrorResponse(errorPageUri)
    }
    
    private fun generateHomepageWithData(): String {
        val shortcutsJson = getShortcutsJson()
        val bookmarksJson = getBookmarksJson()
        
        // Detect private browsing mode
        val isPrivateMode = try {
            val components = context.components
            val selectedTab = components.store.state.selectedTab
            selectedTab?.content?.private ?: false
        } catch (e: Exception) {
            false
        }
        
        // Read the homepage HTML template
        val htmlTemplate = try {
            context.assets.open("homepage.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return "resource://android/assets/homepage.html"
        }
        
        // Inject data by replacing the script section
        // Use JSON.parse to avoid escaping issues with quotes
        val injectedScript = """
            <script>
                // Injected data from Android
                window.NiraShortcuts = {
                    getShortcuts: function() {
                        return JSON.stringify($shortcutsJson);
                    }
                };
                
                window.NiraBookmarks = {
                    getBookmarks: function() {
                        return JSON.stringify($bookmarksJson);
                    }
                };
                
                // Private browsing mode flag
                window.NiraPrivateMode = $isPrivateMode;
                
                // Apply private mode styling if needed
                if (window.NiraPrivateMode) {
                    document.documentElement.classList.add('private-mode');
                }
            </script>
        """.trimIndent()
        
        // Insert the injected script before the existing script tag
        val modifiedHtml = htmlTemplate.replace("<script>", "$injectedScript\n    <script>")
        
        return "data:text/html;charset=utf-8;base64," + android.util.Base64.encodeToString(
            modifiedHtml.toByteArray(),
            android.util.Base64.NO_WRAP
        )
    }
    
    private fun getShortcutsJson(): String {
        return try {
            val interface_ = com.prirai.android.nira.browser.home.HomepageJavaScriptInterface(context)
            interface_.getShortcuts()
        } catch (e: Exception) {
            "[]"
        }
    }
    
    private fun getBookmarksJson(): String {
        return try {
            val interface_ = com.prirai.android.nira.browser.home.HomepageJavaScriptInterface(context)
            interface_.getBookmarks()
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun interceptXpiUrl(
        uri: String,
        hasUserGesture: Boolean
    ): InterceptionResponse? {
        if (hasUserGesture && uri.startsWith("https://addons.mozilla.org") && !UserPreferences(
                context
            ).customAddonCollection) {

            val matchResult = "https://addons.mozilla.org/firefox/downloads/file/([^\\s]+)/([^\\s]+\\.xpi)".toRegex().matchEntire(uri)
            if (matchResult != null) {

                matchResult.groupValues.getOrNull(1)?.let { addonId ->
                    val intent = Intent(context, AddonsActivity::class.java)
                    intent.putExtra("ADDON_ID", addonId)
                    intent.putExtra("ADDON_URL", uri)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(context, intent, null)

                    return InterceptionResponse.Deny
                }
            }
        }

        // In all other case we let the original request proceed.
        return null
    }

    private fun getErrorCategory(errorType: ErrorType): ErrorCategory = when (errorType) {
        ErrorType.UNKNOWN,
        ErrorType.ERROR_CORRUPTED_CONTENT,
        ErrorType.ERROR_CONTENT_CRASHED,
        ErrorType.ERROR_CONNECTION_REFUSED,
        ErrorType.ERROR_NO_INTERNET,
        ErrorType.ERROR_NET_INTERRUPT,
        ErrorType.ERROR_NET_TIMEOUT,
        ErrorType.ERROR_NET_RESET,
        ErrorType.ERROR_UNSAFE_CONTENT_TYPE,
        ErrorType.ERROR_REDIRECT_LOOP,
        ErrorType.ERROR_INVALID_CONTENT_ENCODING,
        ErrorType.ERROR_MALFORMED_URI,
        ErrorType.ERROR_FILE_NOT_FOUND,
        ErrorType.ERROR_FILE_ACCESS_DENIED,
        ErrorType.ERROR_PROXY_CONNECTION_REFUSED,
        ErrorType.ERROR_OFFLINE,
        ErrorType.ERROR_UNKNOWN_HOST,
        ErrorType.ERROR_UNKNOWN_SOCKET_TYPE,
        ErrorType.ERROR_UNKNOWN_PROXY_HOST,
        ErrorType.ERROR_HTTPS_ONLY,
        ErrorType.ERROR_UNKNOWN_PROTOCOL -> ErrorCategory.Network

        ErrorType.ERROR_SECURITY_BAD_CERT,
        ErrorType.ERROR_SECURITY_SSL,
        ErrorType.ERROR_BAD_HSTS_CERT,
        ErrorType.ERROR_PORT_BLOCKED -> ErrorCategory.SSL

        ErrorType.ERROR_SAFEBROWSING_HARMFUL_URI,
        ErrorType.ERROR_SAFEBROWSING_PHISHING_URI,
        ErrorType.ERROR_SAFEBROWSING_MALWARE_URI,
        ErrorType.ERROR_SAFEBROWSING_UNWANTED_URI -> ErrorCategory.Malware
    }

    internal enum class ErrorCategory(val htmlRes: String) {
        Network(NETWORK_ERROR_PAGE),
        SSL(SSL_ERROR_PAGE),
        Malware(MALWARE_ERROR_PAGE),
    }


    // REMOVED: generateHomepageHtml() - Replaced by homepage.html in assets
    // This function is no longer used - about:homepage now loads from assets/homepage.html

    

    /**
     * Handles cross-domain link clicks by creating new tabs instead of navigating current tab.
     * Same-domain links navigate in current tab, cross-domain links create new grouped tabs.
     */
    private fun handleCrossDomainLinkInterception(
        engineSession: EngineSession,
        newUri: String,
        lastUri: String?
    ): Boolean {
        // Skip internal URLs
        if (newUri.startsWith("about:") || 
            newUri.startsWith("chrome:") || 
            newUri.startsWith("file:") ||
            newUri.isBlank() ||
            lastUri.isNullOrBlank()) {
            return false
        }
        
        try {
            val store = context.components.store
            val currentTab = store.state.tabs.find { tab ->
                tab.engineState.engineSession == engineSession
            }
            
            if (currentTab != null) {
                val currentDomain = extractDomain(lastUri)
                val targetDomain = extractDomain(newUri)
                
                
                // If domains are different, open in new tab and group
                if (currentDomain != targetDomain && 
                    currentDomain != "unknown" && 
                    targetDomain != "unknown") {
                    
                    
                    // Create new tab for cross-domain link
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val tabsUseCases = context.components.tabsUseCases
                            val tabGroupManager = context.components.tabGroupManager
                            
                            val newTabId = tabsUseCases.addTab.invoke(
                                url = newUri,
                                selectTab = true,
                                source = mozilla.components.browser.state.state.SessionState.Source.Internal.UserEntered
                            )
                            
                            // Group with source tab
                            tabGroupManager.handleNewTabFromLink(
                                newTabId = newTabId,
                                newTabUrl = newUri,
                                sourceTabId = currentTab.id,
                                sourceTabUrl = lastUri
                            )
                            
                        } catch (e: Exception) {
                        }
                    }
                    
                    // Return true to deny the original navigation (prevent same-tab navigation)
                    return true
                } else {
                    // Same domain - allow normal navigation in current tab
                    return false
                }
            }
        } catch (e: Exception) {
        }
        
        return false
    }

    /**
     * Extract domain from URL for comparison.
     */
    private fun extractDomain(url: String): String {
        return try {
            Uri.parse(url).host?.replace("www.", "") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    companion object {
        internal const val NETWORK_ERROR_PAGE = "network_error_page.html"
        internal const val SSL_ERROR_PAGE = "ssl_error_page.html"
        internal const val MALWARE_ERROR_PAGE = "malware_error_page.html"
    }
}
