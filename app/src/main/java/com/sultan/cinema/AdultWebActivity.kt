package com.sultan.cinema

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Opens approved HTTPS adult sources inside the app.
 * No ACTION_VIEW/browser intent is used; unsafe URL schemes are blocked.
 */
class AdultWebActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var mainWebView: WebView
    private var popupWebView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenOwner: WebView? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val startUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!isSecureWebUrl(Uri.parse(startUrl))) {
            Toast.makeText(this, "Источник не прошёл проверку HTTPS", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        CookieManager.getInstance().setAcceptCookie(true)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        mainWebView = createWebView(allowPopups = true)
        root.addView(mainWebView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
        mainWebView.requestFocus()
        mainWebView.loadUrl(startUrl)
    }

    private fun createWebView(allowPopups: Boolean): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                    !isSecureWebUrl(request.url)

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean =
                    !isSecureWebUrl(Uri.parse(url))
            }
            webChromeClient = createChromeClient(this, allowPopups)
            setDownloadListener { _, _, _, _, _ ->
                Toast.makeText(this@AdultWebActivity, "Загрузка файлов из этого раздела отключена", Toast.LENGTH_SHORT).show()
            }
        }.also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(it, true)
            }
        }
    }

    private fun createChromeClient(owner: WebView, allowPopups: Boolean): WebChromeClient {
        return object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (fullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenOwner = owner
                fullscreenCallback = callback
                owner.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                enterImmersiveMode()
            }

            override fun onHideCustomView() {
                leaveFullscreen()
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!allowPopups || resultMsg == null || popupWebView != null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = createWebView(allowPopups = false)
                popupWebView = popup
                root.addView(popup, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                popup.requestFocus()
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                if (window != null && window === popupWebView) closePopup()
            }
        }
    }

    private fun isSecureWebUrl(uri: Uri): Boolean =
        uri.scheme.equals("https", true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null

    private fun closePopup() {
        popupWebView?.let {
            root.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        popupWebView = null
        mainWebView.visibility = View.VISIBLE
        mainWebView.requestFocus()
    }

    private fun leaveFullscreen() {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        fullscreenOwner?.visibility = View.VISIBLE
        fullscreenOwner?.requestFocus()
        fullscreenOwner = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        enterImmersiveMode()
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

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (fullscreenView != null) {
            leaveFullscreen()
            return
        }
        val popup = popupWebView
        if (popup != null) {
            if (popup.canGoBack()) popup.goBack() else closePopup()
            return
        }
        if (::mainWebView.isInitialized && mainWebView.canGoBack()) {
            mainWebView.goBack()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        if (fullscreenView != null) leaveFullscreen()
        popupWebView?.let {
            root.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        popupWebView = null
        if (::mainWebView.isInitialized) {
            mainWebView.stopLoading()
            mainWebView.loadUrl("about:blank")
            mainWebView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "adult_source_url"
        const val EXTRA_TITLE = "adult_source_title"
    }
}
