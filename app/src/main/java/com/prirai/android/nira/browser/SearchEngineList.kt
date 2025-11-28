package com.prirai.android.nira.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import mozilla.components.browser.state.search.SearchEngine

class SearchEngineList(private val context: Context) {

    private fun getIconBitmap(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
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

    fun getEngines(): List<SearchEngine> {
        return listOf(
            SearchEngine(
                id = "google-b-m",
                name = "Google",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.google),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.google.com/?q={searchTerms}"),
                suggestUrl = "https://www.google.com/"
            ),
            SearchEngine(
                id = "ddg",
                name = "DuckDuckGo",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.duckduckgo),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.duckduckgo.com/?q={searchTerms}"),
                suggestUrl = "https://www.duckduckgo.com/"
            ),
            SearchEngine(
                id = "bing",
                name = "Bing",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.microsoft_bing),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.bing.com/?q={searchTerms}"),
                suggestUrl = "https://www.bing.com/"
            ),
            SearchEngine(
                id = "baidu",
                name = "Baidu",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.baidu),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://www.baidu.com/s?wd={searchTerms}"),
                suggestUrl = "https://www.baidu.com/"
            ),
            SearchEngine(
                id = "yandex",
                name = "Yandex",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.yandex),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://yandex.com/search/?text={searchTerms}"),
                suggestUrl = "https://www.yandex.com/"
            ),
            SearchEngine(
                id = "naver",
                name = "Naver",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.naver),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://m.search.naver.com/search.naver?query={searchTerms}"),
                suggestUrl = "https://www.naver.com/"
            ),
            SearchEngine(
                id = "qwant",
                name = "Qwant",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.qwant),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://qwant.com/?q={searchTerms}")
            ),
            SearchEngine(
                id = "startpage",
                name = "StartPage",
                icon = getIconBitmap(com.prirai.android.nira.R.drawable.startpage),
                type = SearchEngine.Type.CUSTOM,
                resultUrls = listOf("https://startpage.com/sp/search?query={searchTerms}")
            )
        )
    }
}
