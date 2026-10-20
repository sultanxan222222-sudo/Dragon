package com.infernadragon

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.webkit.JavascriptInterface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class DragonOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null
    private var fallbackDragon: DragonView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Always put a native dragon on screen first. The remote 3D model is an
        // enhancement, never a single point of failure: if WebGL/CDN/model loading
        // fails, the user still sees the dragon rather than a blank overlay.
        val fallback = DragonView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        fallbackDragon = fallback

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
            addJavascriptInterface(DragonBridge { success ->
                post {
                    if (success) fallback.visibility = View.GONE
                    else fallback.visibility = View.VISIBLE
                }
            }, "AndroidDragon")
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/dragon3d.html")
        }
        webView = web

        val stack = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(fallback, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(web, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(stack, params)
        overlay = stack
    }

    private var overlay: View? = null

    private inner class DragonBridge(private val report: (Boolean) -> Unit) {
        @JavascriptInterface
        fun modelLoaded() { report(true) }

        @JavascriptInterface
        fun modelFailed() { report(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BITE_CENTER) {
            webView?.evaluateJavascript("window.dragonBite && window.dragonBite()", null)
            fallbackDragon?.triggerBiteAtCenter()
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
        fallbackDragon = null
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_BITE_CENTER = "com.infernadragon.action.BITE_CENTER"
    }
}
