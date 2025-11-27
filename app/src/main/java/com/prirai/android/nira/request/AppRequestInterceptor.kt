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
                val params = android.net.Uri.parse(uri)
                val shortcutId = params.getQueryParameter("id")?.toIntOrNull()
                
                if (shortcutId != null) {
                    // Delete shortcut from database
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
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
                val params = android.net.Uri.parse(uri)
                val url = params.getQueryParameter("url")
                val title = params.getQueryParameter("title")
                
                if (url != null && title != null) {
                    // Save shortcut to database
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
    private fun generateHomepageHtml_DEPRECATED(): String {
        // Load shortcuts from database
        val shortcutsJson = try {
            val interface_ = com.prirai.android.nira.browser.home.HomepageJavaScriptInterface(context)
            interface_.getShortcuts()
        } catch (e: Exception) {
            "[]"
        }
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Homepage</title>
                <link rel="stylesheet" href="file:///android_asset/base.css">
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background-color: var(--surface-color, #ffffff);
                        color: var(--text-primary-color, #000000);
                        overflow-x: hidden;
                    }
                    
                    .gesture-layout {
                        width: 100%;
                        min-height: 100vh;
                        background-color: var(--surface-color, #ffffff);
                    }
                    
                    .home-layout {
                        width: 100%;
                        min-height: 100vh;
                        position: relative;
                        background-color: var(--surface-color, #ffffff);
                    }
                    
                    .app-bar {
                        width: 100%;
                        background: transparent;
                        padding-top: 64px;
                        text-align: center;
                    }
                    
                    .app-header {
                        width: 156px;
                        height: 156px;
                        margin: 0 auto;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                    }
                    
                    .app-icon {
                        width: 80px;
                        height: 80px;
                        margin-bottom: 16px;
                        background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 108 108"><circle cx="54" cy="54" r="50" fill="%23007AFF"/><text x="54" y="62" text-anchor="middle" fill="white" font-size="36" font-family="system-ui">🍪</text></svg>') center/contain no-repeat;
                    }
                    
                    .app-name {
                        font-size: 35px;
                        font-weight: normal;
                        color: var(--text-primary-color, #000000);
                        text-align: center;
                        margin: 0;
                        max-width: 200px;
                    }
                    
                    .content-section {
                        margin: 32px 16px 120px 16px;
                    }
                    
                    .shortcuts-header {
                        display: flex;
                        align-items: center;
                        margin-bottom: 16px;
                    }
                    
                    .shortcuts-title {
                        flex: 1;
                        font-size: 18px;
                        display: flex;
                        align-items: center;
                        gap: 4px;
                    }
                    
                    .shortcuts-icon {
                        width: 20px;
                        height: 20px;
                        margin-right: 4px;
                    }
                    
                    .chevron-icon {
                        width: 20px;
                        height: 20px;
                        margin-left: 4px;
                    }
                    
                    .add-shortcut-btn {
                        width: 64px;
                        height: 48px;
                        background: transparent;
                        border: none;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    
                    .shortcuts-grid {
                        display: grid;
                        grid-template-columns: repeat(5, 1fr);
                        gap: 8px;
                        margin-top: 16px;
                    }
                    
                    .shortcut-item {
                        width: 80px;
                        height: 90px;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        cursor: pointer;
                        border-radius: 12px;
                        transition: background 0.2s;
                        position: relative;
                    }
                    
                    .shortcut-item:hover {
                        background: var(--surface-hover-color, #f0f0f0);
                    }
                    
                    .bottom-toolbar {
                        position: fixed;
                        bottom: 56px;
                        left: 0;
                        right: 0;
                        height: 56px;
                        background-color: var(--surface-color, #ffffff);
                        border-top: 1px solid rgba(0,0,0,0.1);
                        display: flex;
                        align-items: center;
                        padding: 0 8px;
                        z-index: 1000;
                    }
                    
                    .tab-counter {
                        width: 48px;
                        height: 48px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        background: transparent;
                        border: 1px solid var(--text-secondary-color, #666);
                        border-radius: 4px;
                        cursor: pointer;
                        margin-right: 8px;
                    }
                    
                    .toolbar-wrapper {
                        flex: 1;
                        height: 40px;
                        background: var(--toolbar-background, #f0f0f0);
                        border-radius: 20px;
                        display: flex;
                        align-items: center;
                        cursor: pointer;
                        margin-right: 8px;
                        padding: 0 8px;
                    }
                    
                    .search-engine-icon {
                        width: 24px;
                        height: 24px;
                        margin-right: 12px;
                        background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" fill="%23666"/></svg>') center/contain no-repeat;
                    }
                    
                    .toolbar-text {
                        color: var(--text-secondary-color, #666);
                        font-size: 15px;
                    }
                    
                    .menu-button {
                        width: 36px;
                        height: 48px;
                        background: transparent;
                        border: none;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                </style>
            </head>
            <body>
                <div class="gesture-layout">
                    <div class="home-layout">
                        <div class="app-bar">
                            <div class="app-header">
                                <div class="app-icon"></div>
                                <h1 class="app-name">SmartCookieWeb</h1>
                            </div>
                        </div>
                        
                        <div class="content-section">
                            <div class="shortcuts-header">
                                <div class="shortcuts-title">
                                    <div class="shortcuts-icon">📍</div>
                                    Favorites
                                    <div class="chevron-icon">⬇</div>
                                </div>
                                <button class="add-shortcut-btn" onclick="addShortcut()">
                                    ➕
                                </button>
                            </div>
                            
                            <div class="shortcuts-grid">
                                <!-- Placeholder - will be replaced by loadShortcuts() JavaScript -->
                                <div class="shortcut-item" onclick="addShortcut()">
                                    <div style="width: 60px; height: 60px; background: #f5f5f5; border: 2px dashed #999; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #999;">+</div>
                                    <div style="font-size: 11px; text-align: center; margin-top: 6px; color: #666;">Add</div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="bottom-toolbar">
                            <div class="tab-counter" onclick="openTabs()">1</div>
                            <div class="toolbar-wrapper" onclick="openSearch()">
                                <div class="search-engine-icon"></div>
                                <div class="toolbar-text">Search</div>
                            </div>
                            <button class="menu-button" onclick="openMenu()">⋯</button>
                        </div>
                    </div>
                </div>
                
                <script>
                    function openSearch() {
                        // Trigger search functionality
                        window.location.href = 'javascript:void(0)';
                    }
                    
                    function addShortcut() {
                        window.location.href = 'https://www.google.com';
                    }
                    
                    function deleteShortcut(id) {
                        if (confirm('Delete this shortcut?')) {
                            window.location.href = 'nira://delete-shortcut?id=' + id;
                        }
                    }
                    
                    function openTabs() {
                        // This would open tabs tray
                    }
                    
                    function openMenu() {
                        // This would open menu
                    }
                    
                    // Shortcuts data injected from Android
                    const shortcutsData = $shortcutsJson;
                    
                    // Load shortcuts on page load
                    window.addEventListener('DOMContentLoaded', function() {
                        loadShortcuts();
                    });
                    
                    function loadShortcuts() {
                        try {
                            // Use injected shortcuts data
                            const shortcuts = shortcutsData;
                            const grid = document.querySelector('.shortcuts-grid');
                            
                            // Clear existing shortcuts except the add button
                            grid.innerHTML = '';
                            
                            // Add each shortcut
                            shortcuts.forEach(function(shortcut) {
                                const item = document.createElement('div');
                                item.className = 'shortcut-item';
                                item.style.position = 'relative';
                                
                                // Create delete button (X)
                                const deleteBtn = document.createElement('button');
                                deleteBtn.innerHTML = '✕';
                                deleteBtn.className = 'delete-shortcut-btn';
                                deleteBtn.style.cssText = 'position: absolute; top: 2px; right: 2px; width: 24px; height: 24px; border-radius: 50%; background: #ff4444; color: white; border: none; font-size: 16px; font-weight: bold; cursor: pointer; display: none; z-index: 10; line-height: 24px; padding: 0; box-shadow: 0 2px 4px rgba(0,0,0,0.2);';
                                deleteBtn.onclick = function(e) {
                                    e.stopPropagation();
                                    deleteShortcut(shortcut.uid);
                                };
                                
                                // Show delete button on hover
                                item.onmouseenter = function() {
                                    deleteBtn.style.display = 'block';
                                };
                                item.onmouseleave = function() {
                                    deleteBtn.style.display = 'none';
                                };
                                
                                // Create icon
                                const icon = document.createElement('div');
                                icon.style.cssText = 'width: 60px; height: 60px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: white; font-weight: bold; box-shadow: 0 2px 8px rgba(0,0,0,0.1);';
                                icon.textContent = (shortcut.title || shortcut.url || '?').charAt(0).toUpperCase();
                                
                                // Create title
                                const title = document.createElement('div');
                                title.style.cssText = 'font-size: 11px; text-align: center; margin-top: 6px; color: #333; max-width: 80px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;';
                                title.textContent = shortcut.title || shortcut.url || 'Shortcut';
                                
                                // Click handler for navigation
                                item.onclick = function() {
                                    window.location.href = shortcut.url;
                                };
                                
                                item.appendChild(deleteBtn);
                                item.appendChild(icon);
                                item.appendChild(title);
                                grid.appendChild(item);
                            });
                            
                            // Add the "Add" button at the end
                            const addItem = document.createElement('div');
                            addItem.className = 'shortcut-item';
                            addItem.onclick = addShortcut;
                            addItem.innerHTML = '<div style="width: 60px; height: 60px; background: #f5f5f5; border: 2px dashed #999; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #999;">+</div><div style="font-size: 11px; text-align: center; margin-top: 6px; color: #666;">Add</div>';
                            grid.appendChild(addItem);
                            
                        } catch (e) {
                            console.error('Error loading shortcuts:', e);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    

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
