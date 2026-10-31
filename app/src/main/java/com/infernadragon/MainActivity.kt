package com.vaynax.talkingmusa

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingAudioRequest: PermissionRequest? = null
    private val audioPermissionCode = 731

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(15, 17, 20))
            // Keep WebView's WebGL canvas on Android's hardware-accelerated rendering path.
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val audioOnly = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                        val trustedOrigin = request.origin.scheme == "https" &&
                            request.origin.host == "appassets.androidplatform.net"
                        if (!audioOnly || !trustedOrigin) {
                            request.deny()
                        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                            pendingAudioRequest = request
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionCode)
                        }
                    }
                }
            }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioPermissionCode) {
            val request = pendingAudioRequest
            pendingAudioRequest = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                request?.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                request?.deny()
            }
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