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

class DragonOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null
    private var overlay: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // The remote rigged GLB is the only dragon renderer.
        // Never create or reveal the old hand-drawn DragonView as a fallback.
        val web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = true
            addJavascriptInterface(DragonBridge(), "AndroidDragon")
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/dragon3d.html")
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
            // The 3D model loaded successfully. No native substitute exists.
        }

        @JavascriptInterface
        fun modelFailed() {
            // Keep the HTML diagnostic visible; do not resurrect the removed dragon.
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
