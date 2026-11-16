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
import java.util.concurrent.atomic.AtomicInteger

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
    private val io = Executors.newFixedThreadPool(3)
    private val catalogSearchGeneration = AtomicInteger(0)
    private val catalogPreferences by lazy {
        getSharedPreferences("sultan-cinema-catalog", MODE_PRIVATE)
    }
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
            val cleanSearch = searchText.trim().take(80)
            val generation = catalogSearchGeneration.incrementAndGet()
            io.execute {
                // First paint the last successful catalogue immediately. A slow or unavailable
                // upstream search must never make the home screen look permanently empty.
                if (cleanSearch.isEmpty()) {
                    val cached = readCachedCatalog()
                    if (cached.length() > 0 && generation == catalogSearchGeneration.get()) {
                        postResults(cached.toString(), "Показываю сохранённый каталог и обновляю список…")
                    }
                }

                try {
                    val approved = searchArchiveCatalog(cleanSearch)
                    if (generation != catalogSearchGeneration.get()) return@execute

                    if (approved.length() > 0) {
                        if (cleanSearch.isEmpty()) saveCachedCatalog(approved)
                        postResults(approved.toString())
                    } else {
                        val cached = if (cleanSearch.isEmpty()) readCachedCatalog() else JSONArray()
                        if (cached.length() > 0) {
                            postResults(cached.toString(), "Новый список пока недоступен. Показываю сохранённый каталог.")
                        } else {
                            postResults("[]", "Источник пока не вернул фильмы с подтверждённой открытой лицензией. Нажми «Повторить поиск».")
                        }
                    }
                } catch (error: Exception) {
                    Log.e("SultanCinema", "All Archive catalog endpoints failed", error)
                    if (generation != catalogSearchGeneration.get()) return@execute
                    val cached = if (cleanSearch.isEmpty()) readCachedCatalog() else JSONArray()
                    if (cached.length() > 0) {
                        postResults(cached.toString(), "Не удалось обновить каталог. Показана последняя сохранённая подборка.")
                    } else {
                        postResults("[]", "Не удалось подключиться к каталогу. Проверь интернет и нажми «Повторить поиск».")
                    }
                }
            }
        }

        private fun searchArchiveCatalog(searchText: String): JSONArray {
            val specificQuery = buildArchiveQuery(searchText)
            val broadQuery = buildMovieOnlyQuery(searchText)
            var lastError: Exception? = null

            // One small, bounded request per endpoint. This mirrors the lightweight
            // Internet Archive provider approach used by CloudStream and avoids long
            // multi-page scans that made the catalogue appear frozen.
            try {
                val result = searchWithScrapeApi(specificQuery)
                if (result.length() > 0) return result
            } catch (error: Exception) {
                lastError = error
                Log.w("SultanCinema", "Archive scrape request failed", error)
            }

            try {
                val result = searchWithAdvancedApi(specificQuery)
                if (result.length() > 0) return result
            } catch (error: Exception) {
                lastError = error
                Log.w("SultanCinema", "Archive advanced search failed", error)
            }

            // Some Archive index partitions do not understand license wildcards.
            // Discover a small movie page, then keep only records whose license is
            // independently verified by ArchiveLicensePolicy.
            if (broadQuery != specificQuery) {
                try {
                    val result = searchWithScrapeApi(broadQuery)
                    if (result.length() > 0) return result
                } catch (error: Exception) {
                    lastError = error
                    Log.w("SultanCinema", "Broad Archive scrape request failed", error)
                }
                try {
                    val result = searchWithAdvancedApi(broadQuery)
                    if (result.length() > 0) return result
                } catch (error: Exception) {
                    lastError = error
                    Log.w("SultanCinema", "Broad Archive advanced search failed", error)
                }
            }

            if (lastError != null) {
                throw IllegalStateException("Каталог Internet Archive временно недоступен", lastError)
            }
            return JSONArray()
        }

        private fun searchWithScrapeApi(query: String): JSONArray {
            val found = LinkedHashMap<String, JSONObject>()
            val fields = "identifier,title,year,licenseurl,description,creator,runtime"
            val address = buildString {
                append("https://archive.org/services/search/v1/scrape?fields=")
                append(URLEncoder.encode(fields, "UTF-8"))
                append("&q=")
                append(URLEncoder.encode(query, "UTF-8"))
                append("&size=60")
            }
            val root = JSONObject(readUrl(address))
            val items = root.optJSONArray("items")
                ?: throw IllegalStateException(root.optString("error", "Archive Search API вернул неожиданный ответ"))
            addApprovedItems(items, found)
            return JSONArray().apply { found.values.forEach { put(it) } }
        }

        private fun searchWithAdvancedApi(query: String): JSONArray {
            val fields = listOf("identifier", "title", "year", "licenseurl", "description", "creator", "runtime")
                .joinToString("") { "&fl%5B%5D=" + URLEncoder.encode(it, "UTF-8") }
            val address = "https://archive.org/advancedsearch.php?q=" +
                URLEncoder.encode(query, "UTF-8") +
                fields + "&rows=60&page=1&output=json&sort%5B%5D=downloads+desc"
            val root = JSONObject(readUrl(address))
            val response = root.optJSONObject("response")
                ?: throw IllegalStateException(root.optString("error", "Archive Advanced Search вернул неожиданный ответ"))
            val docs = response.optJSONArray("docs") ?: JSONArray()
            val found = LinkedHashMap<String, JSONObject>()
            addApprovedItems(docs, found)
            return JSONArray().apply { found.values.forEach { put(it) } }
        }

        private fun addApprovedItems(items: JSONArray, target: LinkedHashMap<String, JSONObject>) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val identifier = item.optString("identifier")
                if (!ArchiveLicensePolicy.isSafeIdentifier(identifier)) continue
                if (!itemHasApprovedLicense(item)) continue
                if (target.containsKey(identifier)) continue

                // Keep the WebView payload deliberately small; Archive descriptions
                // can be very large and are not needed for the card grid.
                val rawLicense = item.opt("licenseurl")
                val approvedLicense = when (rawLicense) {
                    is JSONArray -> (0 until rawLicense.length())
                        .map { rawLicense.optString(it) }
                        .firstOrNull { isApprovedLicense(it) }
                    else -> rawLicense?.toString()?.takeIf { isApprovedLicense(it) }
                } ?: continue

                val clean = JSONObject()
                    .put("identifier", identifier)
                    .put("title", item.optString("title").take(180))
                    .put("year", item.optString("year").take(12))
                    .put("licenseurl", approvedLicense)
                    .put("description", item.optString("description").take(700))
                    .put("creator", item.optString("creator").take(160))
                    .put("runtime", item.optString("runtime").take(32))
                target[identifier] = clean
                if (target.size >= 60) return
            }
        }

        private fun itemHasApprovedLicense(item: JSONObject): Boolean {
            val value = item.opt("licenseurl")
            return when (value) {
                is JSONArray -> (0 until value.length()).any { isApprovedLicense(value.optString(it)) }
                else -> isApprovedLicense(value?.toString().orEmpty())
            }
        }

        private fun readCachedCatalog(): JSONArray {
            return try {
                val raw = catalogPreferences.getString("approved_items", null) ?: return JSONArray()
                val stored = JSONArray(raw)
                val valid = JSONArray()
                for (i in 0 until stored.length()) {
                    val item = stored.optJSONObject(i) ?: continue
                    if (ArchiveLicensePolicy.isSafeIdentifier(item.optString("identifier")) && itemHasApprovedLicense(item)) {
                        valid.put(item)
                    }
                }
                valid
            } catch (error: Exception) {
                Log.w("SultanCinema", "Could not read cached catalog", error)
                JSONArray()
            }
        }

        private fun saveCachedCatalog(items: JSONArray) {
            try {
                catalogPreferences.edit().putString("approved_items", items.toString()).apply()
            } catch (error: Exception) {
                Log.w("SultanCinema", "Could not save catalog cache", error)
            }
        }

        @JavascriptInterface
        fun openArchiveItem(identifier: String, title: String) {
            if (!ArchiveLicensePolicy.isSafeIdentifier(identifier)) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Некорректный идентификатор фильма", Toast.LENGTH_SHORT).show()
                }
                return
            }
            io.execute {
                try {
                    val record = JSONObject(readUrl("https://archive.org/metadata/$identifier"))
                    val meta = record.optJSONObject("metadata") ?: JSONObject()
                    val licenseValue = meta.opt("licenseurl")
                    val license = when (licenseValue) {
                        is JSONArray -> (0 until licenseValue.length())
                            .map { licenseValue.optString(it) }
                            .firstOrNull { isApprovedLicense(it) }.orEmpty()
                        else -> licenseValue?.toString().orEmpty()
                    }
                    if (!isApprovedLicense(license)) {
                        throw IllegalStateException("У этой записи не подтверждена свободная лицензия")
                    }

                    val files = record.optJSONArray("files") ?: JSONArray()
                    val playableExtensions = setOf("mp4", "m4v", "mov", "mkv", "webm", "ogv", "ogg", "avi")
                    val candidates = ArrayList<Triple<Long, String, String>>()
                    for (i in 0 until files.length()) {
                        val file = files.optJSONObject(i) ?: continue
                        val name = file.optString("name")
                        if (name.isBlank() || name.startsWith("__ia_thumb")) continue
                        if (file.optString("private").equals("true", true)) continue
                        val extension = name.substringAfterLast('.', "").lowercase()
                        val format = file.optString("format")
                        val supportedFormat = format.contains("MPEG", true) ||
                            format.contains("H.264", true) ||
                            format.contains("Matroska", true) ||
                            format.contains("WebM", true) ||
                            format.contains("Ogg Video", true) ||
                            format.contains("DivX", true) ||
                            format.contains("QuickTime", true) ||
                            format.contains("AVI", true)
                        if (extension !in playableExtensions && !supportedFormat) continue
                        if (name.contains("sample", true) || name.contains("thumbnail", true)) continue

                        val height = file.optString("height").toLongOrNull() ?: 0L
                        val width = file.optString("width").toLongOrNull() ?: 0L
                        val size = file.optString("size").toLongOrNull()?.coerceAtMost(4_000_000_000L) ?: 0L
                        val rank = (height.coerceAtMost(4320L) * 1_000_000L) +
                            (width.coerceAtMost(7680L) * 100L) + (size / 10_000L)
                        candidates.add(Triple(rank, name, format))
                    }

                    val selected = candidates
                        .sortedByDescending { it.first }
                        .distinctBy { it.second }
                        .take(6)
                    if (selected.isEmpty()) {
                        throw IllegalStateException("В этой записи не найден видеофайл поддерживаемого формата")
                    }

                    // Archive metadata provides the actual storage host and directory.
                    // These direct links avoid unnecessary redirect hops through /download/.
                    val hostCandidates = listOf(record.optString("d1"), record.optString("d2"))
                        .map { it.trim().removePrefix("https://").removePrefix("http://").substringBefore('/') }
                        .filter { it.matches(Regex("[A-Za-z0-9.-]{1,180}")) && it.endsWith(".archive.org", true) }
                        .distinct()
                    val archiveDir = record.optString("dir").ifBlank { "/download/$identifier" }
                        .let { if (it.startsWith("/")) it else "/$it" }
                        .trimEnd('/')

                    val videoUrls = ArrayList<String>()
                    selected.forEach { (_, fileName, _) ->
                        val encodedPath = fileName.split('/').joinToString("/") { Uri.encode(it) }
                        hostCandidates.forEach { host ->
                            videoUrls.add("https://$host$archiveDir/$encodedPath")
                        }
                        videoUrls.add("https://archive.org/download/$identifier/$encodedPath")
                    }
                    val safeUrls = ArrayList(videoUrls.distinct().take(18))
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putStringArrayListExtra(PlayerActivity.EXTRA_URLS, safeUrls)
                        putExtra(PlayerActivity.EXTRA_URL, safeUrls.first())
                        putExtra(PlayerActivity.EXTRA_TITLE, title)
                        putExtra(PlayerActivity.EXTRA_SOURCE, "Internet Archive")
                        putExtra(PlayerActivity.EXTRA_LICENSE, license)
                    }
                    runOnUiThread { startActivity(intent) }
                } catch (error: Exception) {
                    Log.e("SultanCinema", "Unable to open Archive item", error)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            error.message ?: "Не удалось открыть видео",
                            Toast.LENGTH_LONG
                        ).show()
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
        val base = buildMovieOnlyQuery(searchText)
        return "$base AND ${ArchiveLicensePolicy.searchClause()}"
    }

    private fun buildMovieOnlyQuery(searchText: String): String {
        val base = "mediatype:movies"
        val clean = searchText.trim().take(80)
        if (clean.isEmpty()) return base
        val safe = clean.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' }
        if (safe.isBlank()) return base
        return "$base AND title:\"$safe\""
    }

    private fun isApprovedLicense(value: String): Boolean =
        ArchiveLicensePolicy.isApproved(value)

    private fun readUrl(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6500
            readTimeout = 8500
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "SultanCinema/0.3 Android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) return body
            throw IllegalStateException("Internet Archive вернул HTTP $code")
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
