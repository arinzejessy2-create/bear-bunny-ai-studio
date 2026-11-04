package com.jessy.browser

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        if (savedInstanceState == null) showHome() else web.restoreState(savedInstanceState)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0B1020.toInt()) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 6) }
        val brand = TextView(this).apply {
            text = "JESSY 🫟🫟"; textSize = 25f; setTextColor(-1); gravity = Gravity.CENTER; setPadding(4, 4, 4, 10)
        }
        address = EditText(this).apply {
            hint = "Search or enter website"; setSingleLine(true); textSize = 16f
            setTextColor(0xFF111827.toInt()); setHintTextColor(0xFF64748B.toInt())
            setPadding(14, 8, 14, 8); setBackgroundColor(-1); imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) { navigate(text.toString()); true } else false
            }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun addAction(label: String, callback: () -> Unit) {
            val b = Button(this).apply { text = label; isAllCaps = false; textSize = 12f; setOnClickListener { callback() } }
            actions.addView(b, LinearLayout.LayoutParams(0, 46, 1f).apply { setMargins(2, 0, 2, 0) })
        }
        addAction("Home") { showHome() }
        addAction("Back") { if (web.canGoBack()) web.goBack() }
        addAction("Reload") { web.reload() }
        addAction("Clear") { web.clearHistory(); web.clearCache(true); CookieManager.getInstance().removeAllCookies(null); showHome() }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        header.addView(brand)
        header.addView(address, LinearLayout.LayoutParams(-1, 52))
        header.addView(actions)
        root.addView(header)
        root.addView(progress, LinearLayout.LayoutParams(-1, 3))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    address.setText(url ?: ""); this@MainActivity.progress.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    address.setText(url ?: ""); this@MainActivity.progress.visibility = View.GONE
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, value: Int) { this@MainActivity.progress.progress = value; this@MainActivity.progress.visibility = if (value == 100) View.GONE else View.VISIBLE }
            }
            setDownloadListener { url, agent, disposition, mime, _ ->
                try {
                    val name = URLUtil.guessFileName(url, disposition, mime)
                    val req = DownloadManager.Request(Uri.parse(url)).setMimeType(mime)
                        .addRequestHeader("User-Agent", agent).setTitle(name)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    Toast.makeText(this@MainActivity, "Download started", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(this@MainActivity, "Download could not start", Toast.LENGTH_SHORT).show() }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val target = if (value.matches(Regex("(?i)^[a-z][a-z0-9+.-]*://.*"))) value
        else if (value.contains(".") && !value.contains(" ")) "https://$value"
        else "https://html.duckduckgo.com/html/?q=" + Uri.encode(value)
        web.loadUrl(target)
    }

    private fun showHome() {
        address.setText("")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>*{box-sizing:border-box}body{margin:0;background:#0b1020;color:white;font-family:Arial;min-height:100vh;display:flex;align-items:center;justify-content:center}
main{width:100%;max-width:620px;padding:28px;text-align:center}.logo{font-size:42px;font-weight:800;letter-spacing:1px}
p{color:#9caecb;margin:12px 0 30px}.search{display:flex;gap:8px}input{flex:1;min-width:0;padding:16px;border-radius:14px;border:1px solid #34415c;font-size:16px}
button{padding:0 20px;border:0;border-radius:14px;background:#8b5cf6;color:white;font-size:16px;font-weight:bold}small{display:block;margin-top:25px;color:#9caecb}</style>
</head><body><main><div class="logo">JESSY 🫟🫟</div><p>Your search. Your space.</p>
<form class="search" onsubmit="event.preventDefault();AndroidSearch.go(document.getElementById('q').value)">
<input id="q" placeholder="Search the web or enter a website"><button>Go</button></form>
<small>Search results open inside JESSY, not your phone's Google app.</small></main></body></html>"""
        web.addJavascriptInterface(SearchBridge(), "AndroidSearch")
        web.loadDataWithBaseURL("https://jessy.local/", html, "text/html", "UTF-8", null)
    }

    inner class SearchBridge {
        @JavascriptInterface fun go(query: String) { runOnUiThread { navigate(query) } }
    }
    override fun onSaveInstanceState(outState: Bundle) { web.saveState(outState); super.onSaveInstanceState(outState) }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() { if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onDestroy() { if (::web.isInitialized) web.destroy(); super.onDestroy() }
}