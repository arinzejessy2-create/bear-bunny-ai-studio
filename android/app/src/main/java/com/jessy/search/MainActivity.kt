package com.jessy.search

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private var showingError = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 7, 11)
        window.navigationBarColor = Color.rgb(7, 7, 11)
        root = FrameLayout(this)
        setContentView(root)
        createWebView()
        loadHome()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        web = WebView(this)
        web.setBackgroundColor(Color.rgb(7, 7, 11))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                if (uri.scheme == "jessy" && uri.host == "retry") { loadHome(); return true }
                return uri.scheme != "https" && uri.scheme != "http"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                showingError = false
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showConnectionError(error.description?.toString() ?: "Connection failed")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 500) {
                    showConnectionError("JESSY server returned error ${response.statusCode}")
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                root.removeView(web)
                web.destroy()
                createWebView()
                showConnectionError("The page renderer stopped. Tap retry to reopen JESSY.")
                return true
            }
        }
        root.addView(web, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun loadHome() {
        showingError = false
        web.loadUrl(BuildConfig.API_BASE_URL)
    }

    private fun showConnectionError(detail: String) {
        if (showingError || isFinishing) return
        showingError = true
        val safeDetail = detail.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val page = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="dark"><style>
            *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;background:radial-gradient(ellipse at 75% 15%,#4b071b,transparent 45%),#07070b;color:#fff;font:16px system-ui;padding:24px}
            main{width:min(720px,100%);border:1px solid #752039;background:#11111beF;border-radius:24px;padding:clamp(24px,5vw,52px);box-shadow:0 24px 80px #0009}
            h1{font-size:clamp(34px,6vw,58px);margin:0;color:#ff3155}p{color:#c9c4cc;line-height:1.6}
            button{background:linear-gradient(110deg,#ff2446,#a80028);border:0;border-radius:12px;padding:15px 26px;color:white;font-weight:800;font-size:16px}
            small{color:#aaa;letter-spacing:.2em}</style></head><body><main><small>JESSY · CONNECTION STATUS</small>
            <h1>We couldn't load JESSY.</h1><p id="reason">$safeDetail</p>
            <p>Check your internet connection, then try again. The app will not leave you on a blank screen.</p>
            <button onclick="location.href='jessy://retry'">Retry connection</button></main></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
    }

    inner class Bridge {
        @JavascriptInterface
        fun setProxy(host: String, port: Int): Boolean {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) ||
                host.isBlank() || host.contains("/") || port !in 1..65535) return false
            return try {
                ProxyController.getInstance().setProxyOverride(
                    ProxyConfig.Builder().addProxyRule("$host:$port").build(), executor
                ) {}
                true
            } catch (_: Exception) { false }
        }

        @JavascriptInterface
        fun clearProxy(): Boolean {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return false
            return try {
                ProxyController.getInstance().clearProxyOverride(executor) {}
                true
            } catch (_: Exception) { false }
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack() && !showingError) web.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.removeJavascriptInterface("AndroidBridge")
            web.destroy()
        }
        super.onDestroy()
    }
}