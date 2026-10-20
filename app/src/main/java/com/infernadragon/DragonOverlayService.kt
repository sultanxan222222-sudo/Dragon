package com.infernadragon

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader

class DragonOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null
    private var overlay: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Serve bundled files from a secure HTTPS-like origin. ES modules and
        // relative GLB/module URLs often fail when loaded directly from file://.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = true
            addJavascriptInterface(DragonBridge(), "AndroidDragon")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl("https://appassets.androidplatform.net/assets/dragon3d.html")
        }
        webView = web

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(web, params)
        overlay = web
    }

    private inner class DragonBridge {
        @JavascriptInterface
        fun modelLoaded() {
            // The bundled 3D model loaded successfully.
        }

        @JavascriptInterface
        fun modelFailed() {
            // Keep the on-screen diagnostic; no fake dragon fallback.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BITE_CENTER) {
            webView?.evaluateJavascript("window.dragonBite && window.dragonBite()", null)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlay?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        webView?.let {
            it.stopLoading()
            it.removeJavascriptInterface("AndroidDragon")
            it.destroy()
        }
        webView = null
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_BITE_CENTER = "com.infernadragon.action.BITE_CENTER"
    }
}
