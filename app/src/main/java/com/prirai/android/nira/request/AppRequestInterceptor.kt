package com.prirai.android.nira.request

// import com.prirai.android.nira.browser.home.HomeFragmentDirections // Removed - using BrowserFragment for homepage
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.prirai.android.nira.addons.AddonsActivity
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
    private val webappSessions = mutableMapOf<EngineSession, Pair<String, String>>()

    var fxaInterceptor: RequestInterceptor? = null

    fun setNavController(navController: NavController) {
        this.navController = WeakReference(navController)
    }

    fun registerWebAppSession(session: EngineSession, domain: String, profileId: String) {
        webappSessions[session] = domain to profileId
    }

    fun unregisterWebAppSession(session: EngineSession) {
        webappSessions.remove(session)
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
        if (uri.startsWith("https://accounts.firefox.com")) {
            Log.d("FxaAuth", "onLoadRequest: url=$uri fxaInterceptorNull=${fxaInterceptor == null}")
        }
        // Delegate FxA OAuth handling to FirefoxAccountsAuthFeature.interceptor
        fxaInterceptor?.onLoadRequest(
            engineSession, uri, lastUri, hasUserGesture, isSameDomain,
            isRedirect, isDirectNavigation, isSubframeRequest
        )?.let { return it }

        // Handle homepage type preference
        if (uri == "about:homepage" || uri == "about:blank") {
            val prefs = UserPreferences(context)
            return when (prefs.homepageType) {
                com.prirai.android.nira.settings.HomepageChoice.BLANK_PAGE.ordinal -> {
                    if (uri != "about:blank") {
                        InterceptionResponse.Url("about:blank")
                    } else {
                        null
                    }
                }
                else -> {
                    if (uri == "about:blank") {
                        InterceptionResponse.Url("about:homepage")
                    } else {
                        null
                    }
                }
            }
        }

        // Check if this is a webapp session
        webappSessions[engineSession]?.let { (webappDomain, profileId) ->
            if (!isSubframeRequest) {
                val newDomain = try {
                    java.net.URL(uri).host
                } catch (e: Exception) {
                    null
                }
                if (newDomain != null && newDomain != webappDomain) {
                    openInBrowser(uri, profileId)
                    return InterceptionResponse.Deny
                }
            }
        }

        // Handle custom nira:// scheme
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
                false, isSubframeRequest
            )
        }

        return response
    }

    private fun handleCustomScheme(uri: String) {
        when {
            uri.startsWith("nira://search?q=") -> {
                val query = uri.substringAfter("q=")
                val decodedQuery = java.net.URLDecoder.decode(query, "UTF-8")
                context.components.tabsUseCases.addTab(decodedQuery, selectTab = true)
            }
            uri == "nira://focus-search" -> {
                navController?.get()?.let { nav ->
                    try {
                        // Placeholder for search dialog navigation
                    } catch (e: Exception) { }
                }
            }
            uri.startsWith("nira://delete-shortcut?") -> {
                val params = uri.toUri()
                val shortcutId = params.getQueryParameter("id")?.toIntOrNull()
                if (shortcutId != null) {
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
                val params = uri.toUri()
                val url = params.getQueryParameter("url")
                val title = params.getQueryParameter("title")
                if (url != null && title != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val database = androidx.room.Room.databaseBuilder(
                                context,
                                com.prirai.android.nira.browser.shortcuts.ShortcutDatabase::class.java,
                                "shortcut-database"
                            ).build()
                            val entity = com.prirai.android.nira.browser.shortcuts.ShortcutEntity(
                                url = url, title = title
                            )
                            database.shortcutDao().insertAll(entity)
                            database.close()
                        } catch (e: Exception) { }
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
            return RequestInterceptor.ErrorResponse(uri)
        }
        val errorPageUri = ErrorPages.createUrlEncodedErrorPage(
            context = context,
            errorType = errorType,
            uri = uri,
            htmlResource = riskLevel.htmlRes
        )
        return RequestInterceptor.ErrorResponse(errorPageUri)
    }

    private fun openInBrowser(url: String, profileId: String) {
        try {
            CoroutineScope(Dispatchers.Main).launch {
                context.components.tabsUseCases.addTab(url, selectTab = true, contextId = "profile_$profileId")
                val intent = Intent(context, com.prirai.android.nira.BrowserActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun interceptXpiUrl(
        uri: String,
        hasUserGesture: Boolean
    ): InterceptionResponse? {
        if (hasUserGesture && uri.startsWith("https://addons.mozilla.org") && !UserPreferences(context).customAddonCollection) {
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
        return null
    }

    private fun getErrorCategory(errorType: ErrorType): ErrorCategory = when (errorType) {
        ErrorType.UNKNOWN, ErrorType.ERROR_CORRUPTED_CONTENT, ErrorType.ERROR_CONTENT_CRASHED,
        ErrorType.ERROR_CONNECTION_REFUSED, ErrorType.ERROR_NO_INTERNET, ErrorType.ERROR_NET_INTERRUPT,
        ErrorType.ERROR_NET_TIMEOUT, ErrorType.ERROR_NET_RESET, ErrorType.ERROR_UNSAFE_CONTENT_TYPE,
        ErrorType.ERROR_REDIRECT_LOOP, ErrorType.ERROR_INVALID_CONTENT_ENCODING,
        ErrorType.ERROR_MALFORMED_URI, ErrorType.ERROR_FILE_NOT_FOUND, ErrorType.ERROR_FILE_ACCESS_DENIED,
        ErrorType.ERROR_PROXY_CONNECTION_REFUSED, ErrorType.ERROR_OFFLINE, ErrorType.ERROR_UNKNOWN_HOST,
        ErrorType.ERROR_UNKNOWN_SOCKET_TYPE, ErrorType.ERROR_UNKNOWN_PROXY_HOST,
        ErrorType.ERROR_HTTPS_ONLY, ErrorType.ERROR_UNKNOWN_PROTOCOL -> ErrorCategory.Network

        ErrorType.ERROR_SECURITY_BAD_CERT, ErrorType.ERROR_SECURITY_SSL,
        ErrorType.ERROR_BAD_HSTS_CERT, ErrorType.ERROR_PORT_BLOCKED -> ErrorCategory.SSL

        ErrorType.ERROR_SAFEBROWSING_HARMFUL_URI, ErrorType.ERROR_SAFEBROWSING_PHISHING_URI,
        ErrorType.ERROR_SAFEBROWSING_MALWARE_URI, ErrorType.ERROR_SAFEBROWSING_UNWANTED_URI,
        ErrorType.ERROR_HARMFULADDON_URI -> ErrorCategory.Malware
    }

    internal enum class ErrorCategory(val htmlRes: String) {
        Network(NETWORK_ERROR_PAGE),
        SSL(SSL_ERROR_PAGE),
        Malware(MALWARE_ERROR_PAGE),
    }

    companion object {
        internal const val NETWORK_ERROR_PAGE = "network_error_page.html"
        internal const val SSL_ERROR_PAGE = "ssl_error_page.html"
        internal const val MALWARE_ERROR_PAGE = "malware_error_page.html"
    }
}
