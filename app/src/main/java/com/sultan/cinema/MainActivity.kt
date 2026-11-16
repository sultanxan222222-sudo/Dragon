package com.sultan.cinema

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout
    private var startupErrorView: View? = null
    private var pageLoaded = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cinemaUrl = "https://appassets.androidplatform.net/assets/cinema.html"
    private val startupTimeout = Runnable {
        if (!pageLoaded) showStartupError(
            "Экран не загрузился",
            "Локальная страница не ответила. Проверь целостность установки и попробуй перезапустить загрузку."
        )
    }
    private val io = Executors.newFixedThreadPool(2)
    private val adultProviderUrls: Map<String, String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            val root = JSONObject(assets.open("adult_sources.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            val rows = root.optJSONArray("sources") ?: JSONArray()
            val result = mutableMapOf<String, String>()
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                if (!item.optBoolean("enabled", false)) continue
                val id = item.optString("id")
                val raw = item.optString("url")
                if (!id.matches(Regex("[a-z0-9_-]{1,60}"))) continue
                val uri = Uri.parse(raw)
                if (uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                    result[id] = uri.toString()
                }
            }
            result
        } catch (error: Exception) {
            Log.e("SultanCinema", "Could not load adult provider directory", error)
            emptyMap()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(8, 9, 11))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportZoom(false)
            addJavascriptInterface(CinemaBridge(), "AndroidCinema")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest) =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    if (url == cinemaUrl) {
                        pageLoaded = false
                        clearStartupError()
                        mainHandler.removeCallbacks(startupTimeout)
                        mainHandler.postDelayed(startupTimeout, 15000)
                    }
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    if (url == cinemaUrl) {
                        pageLoaded = true
                        mainHandler.removeCallbacks(startupTimeout)
                        webView.visibility = View.VISIBLE
                        clearStartupError()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url == cinemaUrl) {
                        pageLoaded = true
                        mainHandler.removeCallbacks(startupTimeout)
                        webView.visibility = View.VISIBLE
                        clearStartupError()
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        Log.e("SultanCinema", "WebView main-frame error: ${error.description}")
                        runOnUiThread {
                            showStartupError(
                                "Не удалось открыть экран",
                                "Ошибка WebView: ${error.description}"
                            )
                        }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    Log.w("SultanCinemaJS", "${message.message()} at ${message.sourceId()}:${message.lineNumber()}")
                    return true
                }
            }
        }
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(8, 9, 11))
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(rootLayout)
        mainHandler.postDelayed(startupTimeout, 15000)
        webView.loadUrl(cinemaUrl)
    }

    private fun clearStartupError() {
        startupErrorView?.let { rootLayout.removeView(it) }
        startupErrorView = null
    }

    private fun showStartupError(title: String, details: String) {
        if (!::rootLayout.isInitialized) return
        pageLoaded = false
        mainHandler.removeCallbacks(startupTimeout)
        webView.visibility = View.GONE
        clearStartupError()

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(android.graphics.Color.rgb(8, 9, 11))
        }
        panel.addView(TextView(this).apply {
            text = "КИНОТЕАТР СУЛТАНА"
            textSize = 22f
            setTextColor(android.graphics.Color.rgb(243, 217, 154))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(TextView(this).apply {
            text = details
            textSize = 14f
            setTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(Button(this).apply {
            text = "ПОВТОРИТЬ ЗАГРУЗКУ"
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                clearStartupError()
                webView.visibility = View.VISIBLE
                pageLoaded = false
                mainHandler.removeCallbacks(startupTimeout)
                mainHandler.postDelayed(startupTimeout, 15000)
                webView.reload()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
            topMargin = dp(24)
        })
        startupErrorView = panel
        rootLayout.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private inner class CinemaBridge {
        @JavascriptInterface
        fun searchArchive(searchText: String) {
            io.execute {
                try {
                    val encodedQuery = URLEncoder.encode(buildArchiveQuery(searchText), "UTF-8")
                    val fields = listOf("identifier", "title", "year", "licenseurl", "description", "creator", "runtime")
                        .joinToString("") { "&fl%5B%5D=" + URLEncoder.encode(it, "UTF-8") }
                    val address = "https://archive.org/advancedsearch.php?q=$encodedQuery$fields&rows=100&page=1&output=json&sort%5B%5D=downloads+desc"
                    val root = JSONObject(readUrl(address))
                    val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
                    val approved = JSONArray()
                    for (i in 0 until docs.length()) {
                        val item = docs.optJSONObject(i) ?: continue
                        val license = item.optString("licenseurl")
                        if (!isApprovedLicense(license)) continue
                        approved.put(item)
                    }
                    postResults(approved.toString())
                } catch (error: Exception) {
                    Log.e("SultanCinema", "Archive search failed", error)
                    postResults("[]", "Каталог временно недоступен. Проверь подключение к интернету.")
                }
            }
        }

        @JavascriptInterface
        fun openArchiveItem(identifier: String, title: String) {
            if (!identifier.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Некорректный идентификатор фильма", Toast.LENGTH_SHORT).show() }
                return
            }
            io.execute {
                try {
                    val metadata = JSONObject(readUrl("https://archive.org/metadata/${identifier}"))
                    val meta = metadata.optJSONObject("metadata") ?: JSONObject()
                    val license = meta.optString("licenseurl")
                    if (!isApprovedLicense(license)) {
                        throw IllegalStateException("У записи нет подтверждённой свободной лицензии")
                    }
                    val files = metadata.optJSONArray("files") ?: JSONArray()
                    val candidates = ArrayList<Pair<Int, String>>()
                    for (i in 0 until files.length()) {
                        val file = files.optJSONObject(i) ?: continue
                        val name = file.optString("name")
                        if (!name.lowercase().endsWith(".mp4")) continue
                        if (file.optString("private").equals("true", true)) continue
                        if (name.lowercase().contains("sample") || name.lowercase().contains("thumbnail")) continue
                        val width = file.optString("width").toIntOrNull() ?: 0
                        val height = file.optString("height").toIntOrNull() ?: 0
                        val rank = width * height + (file.optString("size").toLongOrNull()?.coerceAtMost(2_000_000_000L)?.div(10_000_000L)?.toInt() ?: 0)
                        candidates.add(rank to name)
                    }
                    val selected = candidates.maxByOrNull { it.first }?.second
                        ?: throw IllegalStateException("Для этой записи не найден доступный MP4-файл")
                    val videoUrl = "https://archive.org/download/${identifier}/${Uri.encode(selected)}"
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_URL, videoUrl)
                        putExtra(PlayerActivity.EXTRA_TITLE, title)
                        putExtra(PlayerActivity.EXTRA_SOURCE, "Internet Archive")
                        putExtra(PlayerActivity.EXTRA_LICENSE, license)
                    }
                    runOnUiThread { startActivity(intent) }
                } catch (error: Exception) {
                    Log.e("SultanCinema", "Unable to open archive item", error)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, error.message ?: "Не удалось открыть видео", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        @JavascriptInterface
        fun openExternalSource(url: String) {
            if (!url.startsWith("https://archive.org/")) return
            runOnUiThread {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: Exception) { Toast.makeText(this@MainActivity, "Не удалось открыть источник", Toast.LENGTH_SHORT).show() }
            }
        }

        @JavascriptInterface
        fun openAdultProvider(providerId: String) {
            if (!providerId.matches(Regex("[a-z0-9_-]{1,60}"))) return
            val address = adultProviderUrls[providerId]
            if (address == null) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Этот источник отключён: адрес или права не подтверждены", Toast.LENGTH_LONG).show()
                }
                return
            }
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "На устройстве не найден браузер для открытия сайта", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildArchiveQuery(searchText: String): String {
        val publicDomain = "(licenseurl:\"http://creativecommons.org/publicdomain/mark/1.0/\" OR licenseurl:\"https://creativecommons.org/publicdomain/mark/1.0/\")"
        val cc0 = "(licenseurl:\"http://creativecommons.org/publicdomain/zero/1.0/\" OR licenseurl:\"https://creativecommons.org/publicdomain/zero/1.0/\")"
        val base = "mediatype:movies AND ${ArchiveLicensePolicy.searchClause()}"
        val clean = searchText.trim().take(80)
        if (clean.isEmpty()) return base
        val safe = clean.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' }
        if (safe.isBlank()) return base
        return "$base AND title:(\"$safe\")"
    }

    private fun isApprovedLicense(value: String): Boolean =
        ArchiveLicensePolicy.isApproved(value)

    private fun readUrl(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("User-Agent", "SultanCinema/0.1 (Android TV)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Источник вернул HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun postResults(json: String, error: String? = null) {
        val safeError = JSONObject.quote(error ?: "")
        webView.post {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    "window.onArchiveResults && window.onArchiveResults($json, $safeError)",
                    null
                )
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(startupTimeout)
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidCinema")
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        io.shutdownNow()
        super.onDestroy()
    }
}
