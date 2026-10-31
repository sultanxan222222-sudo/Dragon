package com.vaynax.talkingmusa

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(15, 17, 20))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(MusaBridge(), "AndroidMusa")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest) =
                    assetLoader.shouldInterceptRequest(request.url)
            }
            webChromeClient = WebChromeClient()
            loadUrl("https://appassets.androidplatform.net/assets/musa3d.html")
        }
        setContentView(webView)
    }

    private inner class MusaBridge {
        @JavascriptInterface fun ready() = runOnUiThread {
            Toast.makeText(this@MainActivity, "Говорящий Муса запущен", Toast.LENGTH_SHORT).show()
        }
        @JavascriptInterface fun message(value: String) = runOnUiThread {
            Toast.makeText(this@MainActivity, value, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface("AndroidMusa")
            webView.destroy()
        }
        super.onDestroy()
    }
}