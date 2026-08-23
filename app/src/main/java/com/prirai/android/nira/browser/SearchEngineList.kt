package com.prirai.android.nira.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.state.search.SearchEngine

class SearchEngineList(private val context: Context) {

    private fun getIconBitmap(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return createBitmap(48, 48)
        val bitmap = Bitmap.createBitmap(
            if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48,
            if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun getSelectedEngine(preferences: UserPreferences): SearchEngine {
        if (preferences.customSearchEngine && preferences.customSearchEngineURL.isNotBlank()) {
            return SearchEngine(
                id = "custom",
                name = "Custom Search",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.ic_search),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf(preferences.customSearchEngineURL)
            )
        }
        val engines = getEngines()
        val index = preferences.searchEngineChoice.coerceIn(engines.indices)
        return engines[index]
    }

    fun getEngines(): List<SearchEngine> {
        return listOf(
            SearchEngine(
                id = "google-b-m",
                name = "Google",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.google),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.google.com/search?q={searchTerms}"),
                suggestUrl = "https://www.google.com/complete/search?client=firefox&q={searchTerms}"
            ),
            SearchEngine(
                id = "ddg",
                name = "DuckDuckGo",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.duckduckgo),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://duckduckgo.com/?q={searchTerms}"),
                suggestUrl = "https://ac.duckduckgo.com/ac/?q={searchTerms}&type=list"
            ),
            SearchEngine(
                id = "bing",
                name = "Bing",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.microsoft_bing),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.bing.com/search?q={searchTerms}"),
                suggestUrl = "https://www.bing.com/osjson.aspx?query={searchTerms}"
            ),
            SearchEngine(
                id = "baidu",
                name = "Baidu",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.baidu),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://www.baidu.com/s?wd={searchTerms}"),
                suggestUrl = "https://suggestion.baidu.com/su?wd={searchTerms}&action=opensearch"
            ),
            SearchEngine(
                id = "yandex",
                name = "Yandex",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.yandex),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://yandex.com/search/?text={searchTerms}"),
                suggestUrl = "https://suggest.yandex.com/suggest-ff.cgi?part={searchTerms}"
            ),
            SearchEngine(
                id = "naver",
                name = "Naver",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.naver),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://m.search.naver.com/search.naver?query={searchTerms}"),
                suggestUrl = "https://ac.search.naver.com/nx/ac?q={searchTerms}&con=0&frm=nx&ans=2&r_format=json&r_enc=UTF-8&q_enc=UTF-8&st=100"
            ),
            SearchEngine(
                id = "qwant",
                name = "Qwant",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.qwant),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://www.qwant.com/?q={searchTerms}"),
                suggestUrl = "https://api.qwant.com/v3/suggest?q={searchTerms}"
            ),
            SearchEngine(
                id = "startpage",
                name = "StartPage",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.startpage),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://www.startpage.com/sp/search?query={searchTerms}"),
                suggestUrl = "https://www.startpage.com/suggestions?q={searchTerms}&format=opensearch"
            )
        )
    }
}
