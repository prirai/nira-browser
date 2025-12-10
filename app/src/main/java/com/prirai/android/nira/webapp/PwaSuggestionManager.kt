package com.prirai.android.nira.webapp

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.prirai.android.nira.R
import com.prirai.android.nira.components.Components
import com.prirai.android.nira.utils.FaviconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manager for PWA suggestions
 * Detects high-quality PWAs and recommends them to users
 */
class PwaSuggestionManager(private val context: Context) {
    private val _suggestedPwas = MutableLiveData<List<PwaSuggestion>>()
    val suggestedPwas: LiveData<List<PwaSuggestion>> = _suggestedPwas

    private val _detectionState = MutableLiveData<DetectionState>()
    val detectionState: LiveData<DetectionState> = _detectionState

    sealed class DetectionState {
        object Idle : DetectionState()
        object Detecting : DetectionState()
        data class SuggestionsReady(val count: Int) : DetectionState()
        data class Error(val message: String) : DetectionState()
    }

    data class PwaSuggestion(
        val url: String,
        val name: String,
        val description: String
    )

    // Predefined list of popular web apps
    private val highQualityPwas = listOf(
        PwaSuggestion(
            url = "https://web.telegram.org/",
            name = "Telegram",
            description = "Fast and secure messaging"
        ),
        PwaSuggestion(
            url = "https://discord.com/app",
            name = "Discord",
            description = "Chat, voice, and video"
        ),
        PwaSuggestion(
            url = "https://x.com/",
            name = "X - Formerly Twitter",
            description = "Social networking"
        ),
        PwaSuggestion(
            url = "https://www.reddit.com/",
            name = "Reddit",
            description = "Social news and discussion"
        ),
        PwaSuggestion(
            url = "https://www.instagram.com/",
            name = "Instagram",
            description = "Photo and video sharing"
        ),
        PwaSuggestion(
            url = "https://music.youtube.com/",
            name = "YouTube Music",
            description = "Music streaming service"
        ),
        PwaSuggestion(
            url = "https://open.spotify.com/",
            name = "Spotify",
            description = "Music and podcasts"
        ),
        PwaSuggestion(
            url = "https://soundcloud.com/",
            name = "SoundCloud",
            description = "Music and audio"
        ),
        PwaSuggestion(
            url = "https://www.twitch.tv/",
            name = "Twitch",
            description = "Live streaming platform"
        ),
        PwaSuggestion(
            url = "https://www.netflix.com/",
            name = "Netflix",
            description = "Movies and TV shows"
        ),
        PwaSuggestion(
            url = "https://www.figma.com/",
            name = "Figma",
            description = "Design and prototyping"
        ),
        PwaSuggestion(
            url = "https://www.canva.com/",
            name = "Canva",
            description = "Graphic design platform"
        ),
        PwaSuggestion(
            url = "https://excalidraw.com/",
            name = "Excalidraw",
            description = "Virtual whiteboard"
        ),
        PwaSuggestion(
            url = "https://outlook.live.com/",
            name = "Outlook",
            description = "Email and calendar"
        ),
        PwaSuggestion(
            url = "https://www.notion.so/",
            name = "Notion",
            description = "Notes and collaboration"
        ),
        PwaSuggestion(
            url = "https://trello.com/",
            name = "Trello",
            description = "Project management"
        ),
        PwaSuggestion(
            url = "https://asana.com/",
            name = "Asana",
            description = "Work management"
        ),
        PwaSuggestion(
            url = "https://github.com/",
            name = "GitHub",
            description = "Code hosting platform"
        ),
        PwaSuggestion(
            url = "https://gitlab.com/",
            name = "GitLab",
            description = "DevOps platform"
        ),
        PwaSuggestion(
            url = "https://stackoverflow.com/",
            name = "Stack Overflow",
            description = "Developer Q&A"
        ),
        PwaSuggestion(
            url = "https://news.ycombinator.com/",
            name = "Hacker News",
            description = "Tech news"
        ),
        PwaSuggestion(
            url = "https://medium.com/",
            name = "Medium",
            description = "Reading and writing"
        ),
        PwaSuggestion(
            url = "https://dev.to/",
            name = "DEV Community",
            description = "Developer community"
        ),
        PwaSuggestion(
            url = "https://www.linkedin.com/",
            name = "LinkedIn",
            description = "Professional networking"
        ),
        PwaSuggestion(
            url = "https://www.dropbox.com/",
            name = "Dropbox",
            description = "File storage and sharing"
        ),
        PwaSuggestion(
            url = "https://www.office.com/",
            name = "Microsoft Office",
            description = "Office suite online"
        ),
        PwaSuggestion(
            url = "https://calendar.google.com/",
            name = "Google Calendar",
            description = "Calendar and scheduling"
        ),
        PwaSuggestion(
            url = "https://maps.google.com/",
            name = "Google Maps",
            description = "Maps and navigation"
        ),
        PwaSuggestion(
            url = "https://translate.google.com/",
            name = "Google Translate",
            description = "Language translation"
        ),
        PwaSuggestion(
            url = "https://chat.openai.com/",
            name = "ChatGPT",
            description = "AI assistant"
        ),
        PwaSuggestion(
            url = "https://Gemini.google.com/",
            name = "Google Gemini",
            description = "AI chatbot"
        ),
        PwaSuggestion(
            url = "https://www.photopea.com/",
            name = "Photopea",
            description = "Online photo editor"
        ),
        PwaSuggestion(
            url = "https://squoosh.app/",
            name = "Squoosh",
            description = "Image compression"
        ),
        PwaSuggestion(
            url = "https://app.diagrams.net/",
            name = "Draw.io",
            description = "Diagram creation"
        ),
        PwaSuggestion(
            url = "https://www.ecosia.org/",
            name = "Ecosia",
            description = "Tree-planting search engine"
        ),
        PwaSuggestion(
            url = "https://www.wikipedia.org/",
            name = "Wikipedia",
            description = "Free encyclopedia"
        ),
        PwaSuggestion(
            url = "https://www.duolingo.com/",
            name = "Duolingo",
            description = "Language learning"
        ),
        PwaSuggestion(
            url = "https://www.chess.com/",
            name = "Chess.com",
            description = "Online chess platform"
        ),
        PwaSuggestion(
            url = "https://www.lichess.org/",
            name = "Lichess",
            description = "Free online chess"
        ),
        PwaSuggestion(
            url = "https://www.geoguessr.com/",
            name = "GeoGuessr",
            description = "Geography game"
        ),
        PwaSuggestion(
            url = "https://play.typeracer.com/",
            name = "TypeRacer",
            description = "Typing game"
        ),
        PwaSuggestion(
            url = "https://monkeytype.com/",
            name = "Monkeytype",
            description = "Typing test"
        )
    )

    /**
     * Check if current URL is a suggested PWA
     */
    fun checkCurrentUrl(url: String): PwaSuggestion? {
        return highQualityPwas.find { pwa ->
            url.contains(pwa.url.replace("https://", "").replace("http://", "").replace("www.", ""))
        }
    }

    /**
     * Get all suggested PWAs
     */
    fun getAllSuggestedPwas() {
        _detectionState.value = DetectionState.Detecting

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    _suggestedPwas.value = highQualityPwas
                    _detectionState.value = DetectionState.SuggestionsReady(highQualityPwas.size)
                }

                // Preload favicons in background
                preloadFavicons()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _detectionState.value = DetectionState.Error("Failed to load suggestions: ${e.message}")
                }
            }
        }
    }

    /**
     * Preload favicons for all suggestions
     */
    private suspend fun preloadFavicons() {
        highQualityPwas.forEach { pwa ->
            try {
                // Check if favicon is already in cache
                val cached = FaviconCache.getInstance(context).loadFavicon(pwa.url)
                if (cached == null) {
                    // Extract domain
                    val domain = try {
                        java.net.URL(pwa.url).host
                    } catch (e: Exception) {
                        pwa.url
                    }

                    // Try Google's favicon service first (fastest and most reliable)
                    try {
                        val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                        val connection = java.net.URL(faviconUrl).openConnection()
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        val inputStream = connection.getInputStream()
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream.close()

                        if (bitmap != null) {
                            FaviconCache.getInstance(context).saveFavicon(pwa.url, bitmap)
                        }
                    } catch (e: Exception) {
                        // Fallback to browser icons
                        val iconRequest = mozilla.components.browser.icons.IconRequest(url = pwa.url)
                        val icon = Components(context).icons.loadIcon(iconRequest).await()
                        icon.bitmap?.let {
                            FaviconCache.getInstance(context).saveFavicon(pwa.url, it)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore errors for individual favicons
            }
        }
    }

    /**
     * Reset suggestions
     */
    fun reset() {
        _detectionState.value = DetectionState.Idle
        _suggestedPwas.value = emptyList()
    }
}
