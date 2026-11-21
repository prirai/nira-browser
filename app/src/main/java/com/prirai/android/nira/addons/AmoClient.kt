package com.prirai.android.nira.addons

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.feature.addons.Addon
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal client for the addons.mozilla.org search API. The Mozilla components
 * provider only reads a fixed collection, so featured lists and full-site
 * search go through the search endpoint directly. Parsing mirrors the
 * component's own (internal) mapping so results behave like collection add-ons.
 */
object AmoClient {
    private const val SEARCH_URL = "https://addons.mozilla.org/api/v4/addons/search/"
    private const val PAGE_SIZE = 50

    /** Mozilla's "Recommended" extensions that are compatible with Firefox for Android. */
    suspend fun featured(): List<Addon> = query(promotedRecommended = true, sort = "users")

    /** Searches every Android-compatible extension on AMO. */
    suspend fun search(text: String): List<Addon> = query(text = text, sort = "relevance")

    private suspend fun query(
        text: String? = null,
        promotedRecommended: Boolean = false,
        sort: String,
    ): List<Addon> = withContext(Dispatchers.IO) {
        val language = Locale.getDefault().toLanguageTag()
        val params = buildList {
            add("app" to "android")
            add("type" to "extension")
            add("sort" to sort)
            add("page_size" to PAGE_SIZE.toString())
            add("lang" to language)
            if (promotedRecommended) add("promoted" to "recommended")
            if (!text.isNullOrBlank()) add("q" to text)
        }.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

        val connection = URL("$SEARCH_URL?$params").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).getJSONArray("results")
            (0 until results.length()).mapNotNull { runCatching { results.getJSONObject(it).toAddon(language) }.getOrNull() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toAddon(language: String): Addon {
        val lang = language.lowercase(Locale.ROOT)
        val summary = translations("summary", lang)
        val file = getJSONObject("current_version").optJSONArray("files")?.optJSONObject(0)
        return Addon(
            id = safeString("guid"),
            author = optJSONArray("authors")?.optJSONObject(0)?.let {
                Addon.Author(name = it.safeString("name"), url = it.safeString("url"))
            },
            createdAt = safeString("created"),
            updatedAt = file?.safeString("created").orEmpty(),
            downloadUrl = file?.safeString("url").orEmpty(),
            version = getJSONObject("current_version").safeString("version"),
            permissions = file?.optJSONArray("permissions").toStrings(),
            translatableName = translations("name", lang),
            translatableDescription = translations("description", lang),
            translatableSummary = summary,
            iconUrl = safeString("icon_url"),
            homepageUrl = safeString("url"),
            rating = optJSONObject("ratings")?.let {
                Addon.Rating(average = it.optDouble("average").toFloat(), reviews = it.optInt("text_count"))
            },
            ratingUrl = safeString("ratings_url"),
            detailUrl = safeString("url"),
            defaultLocale = if (summary.containsKey(lang)) lang else {
                safeString("default_locale").ifEmpty { Addon.DEFAULT_LOCALE }.lowercase(Locale.ROOT)
            },
        )
    }

    private fun JSONObject.safeString(key: String) = if (isNull(key)) "" else getString(key)

    private fun JSONArray?.toStrings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONObject.translations(key: String, lang: String): Map<String, String> {
        if (isNull(key)) return emptyMap()
        val value = get(key)
        if (value is String) return mapOf(lang to value)
        val obj = value as? JSONObject ?: return emptyMap()
        return obj.keys().asSequence().associate { it.lowercase(Locale.ROOT) to obj.safeString(it) }
    }
}
