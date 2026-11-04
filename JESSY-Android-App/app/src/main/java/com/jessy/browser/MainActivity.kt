package com.jessy.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private val prefs by lazy { getSharedPreferences("jessy_private", Context.MODE_PRIVATE) }
    private val uiExecutor: Executor by lazy { Executor { runOnUiThread(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        if (savedInstanceState == null) showHome() else web.restoreState(savedInstanceState)
        refreshStatus()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF080B14.toInt()) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 4); setBackgroundColor(0xFF101727.toInt()) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val brand = TextView(this).apply { text = "JESSY 🫟🫟"; textSize = 22f; setTextColor(-1); setTypeface(null, 1) }
        status = TextView(this).apply { text = "PRIVATE SEARCH"; textSize = 10f; setTextColor(0xFF9CA3AF.toInt()); gravity = Gravity.END }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f)); top.addView(status)
        address = EditText(this).apply {
            hint = "Search or enter a URL"; setSingleLine(true); textSize = 15f
            setTextColor(0xFF111827.toInt()); setHintTextColor(0xFF64748B.toInt())
            setPadding(14, 5, 14, 5); setBackgroundColor(-1); imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) { navigate(text.toString()); true } else false
            }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun action(label: String, click: () -> Unit) {
            val b = Button(this).apply { text = label; isAllCaps = false; textSize = 10f; setOnClickListener { click() } }
            actions.addView(b, LinearLayout.LayoutParams(0, 44, 1f).apply { setMargins(2, 2, 2, 2) })
        }
        action("Home") { showHome() }
        action("Back") { if (web.canGoBack()) web.goBack() }
        action("Tabs") { tabInfo() }
        action("Proxy") { proxyDialog() }
        action("Tor") { torDialog() }
        action("Private") { togglePrivate() }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        header.addView(top)
        header.addView(address, LinearLayout.LayoutParams(-1, 50).apply { topMargin = 8 })
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
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    address.setText(url ?: "")
                    this@MainActivity.progress.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    address.setText(url ?: "")
                    this@MainActivity.progress.visibility = View.GONE
                    refreshStatus()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, value: Int) {
                    this@MainActivity.progress.progress = value
                    this@MainActivity.progress.visibility = if (value == 100) View.GONE else View.VISIBLE
                }
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
        web.addJavascriptInterface(SearchBridge(), "Jessy")
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val target = when {
            value.startsWith("http://", true) || value.startsWith("https://", true) -> value
            value.endsWith(".onion", true) || value.contains(".onion/") -> "http://$value"
            value.contains(".") && !value.contains(" ") -> "https://$value"
            else -> "https://html.duckduckgo.com/html/?q=" + Uri.encode(value)
        }
        web.loadUrl(target)
    }

    private fun showHome() {
        address.setText("")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*{box-sizing:border-box}body{margin:0;background:#080b14;color:#f8fafc;font-family:Arial,sans-serif}
main{max-width:850px;margin:auto;padding:28px 18px}.logo{text-align:center;font-size:38px;font-weight:800;letter-spacing:1px;margin-top:26px}
.tag{text-align:center;color:#94a3b8;margin:8px 0 25px}.search{display:flex;gap:8px;max-width:650px;margin:0 auto 26px}
input{flex:1;min-width:0;padding:15px;border-radius:12px;border:1px solid #334155;background:#111827;color:white;font-size:16px}
button{padding:13px 18px;border:0;border-radius:11px;background:#7c3aed;color:white;font-weight:bold}
h3{font-size:14px;color:#a78bfa;letter-spacing:1px;margin:25px 0 12px}
.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.card{display:block;text-decoration:none;color:white;background:#111827;border:1px solid #253148;border-radius:13px;padding:15px}
.card b{display:block;margin-bottom:6px}.card small{color:#94a3b8;line-height:1.4}
.note{color:#94a3b8;font-size:12px;line-height:1.5;margin-top:22px}
@media(min-width:650px){.grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
</style></head><body><main>
<div class="logo">JESSY 🫟🫟</div><div class="tag">Private search · Proxy controls · Open resources</div>
<form class="search" onsubmit="event.preventDefault();Jessy.search(document.getElementById('q').value)">
<input id="q" placeholder="Search the web, a site, or a .onion address"><button>Search</button></form>
<h3>DISCOVER FREE & OPEN RESOURCES</h3><div class="grid">
<a class="card" href="https://www.gutenberg.org/"><b>Project Gutenberg</b><small>Free public-domain ebooks</small></a>
<a class="card" href="https://openstax.org/"><b>OpenStax</b><small>Free textbooks and learning</small></a>
<a class="card" href="https://www.f-droid.org/"><b>F-Droid</b><small>Open-source Android apps</small></a>
<a class="card" href="https://arxiv.org/"><b>arXiv</b><small>Research papers and preprints</small></a>
<a class="card" href="https://archive.org/"><b>Internet Archive</b><small>Books, media and archived pages</small></a>
<a class="card" href="https://www.wikibooks.org/"><b>Wikibooks</b><small>Free educational books</small></a>
<a class="card" href="https://github.com/explore"><b>GitHub Explore</b><small>Open-source software projects</small></a>
<a class="card" href="https://www.khanacademy.org/"><b>Khan Academy</b><small>Free lessons and courses</small></a>
<a class="card" href="https://commons.wikimedia.org/"><b>Wikimedia Commons</b><small>Free-use media collection</small></a>
</div>
<h3>ONION / TOR ACCESS</h3>
<div class="card"><b>Open a .onion address</b><small>Start Tor with the Tor button, enable JESSY in Orbot's app-routing settings, then enter an onion address in the address bar. Tor requires Orbot installed and connected.</small></div>
<div class="note">Privacy note: Private mode clears local cookies and browsing data when you leave it. It does not make you anonymous by itself. Free proxy servers can monitor traffic; use a proxy you trust. JESSY does not provide or endorse illegal content.</div>
</main></body></html>"""
        web.loadDataWithBaseURL("https://jessy.home/", html, "text/html", "UTF-8", null)
    }

    private fun proxyDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 8, 22, 0) }
        val country = EditText(this).apply { hint = "Server country / label (e.g. Netherlands)" }
        val host = EditText(this).apply { hint = "Proxy hostname or IP"; setSingleLine(true) }
        val port = EditText(this).apply { hint = "Port (e.g. 8080)"; inputType = 2; setSingleLine(true) }
        layout.addView(country); layout.addView(host); layout.addView(port)
        val active = prefs.getString("proxy_label", null)
        val dialog = AlertDialog.Builder(this).setTitle(if (active == null) "Add proxy server" else "Proxy server")
            .setMessage("Enter an HTTP proxy you trust. Country is a label for the server you provide; JESSY cannot create a foreign IP without a working proxy.")
            .setView(layout)
            .setPositiveButton("Connect") { _, _ ->
                val h = host.text.toString().trim()
                val p = port.text.toString().trim().toIntOrNull()
                if (h.isBlank() || p == null || p !in 1..65535) {
                    toast("Enter a valid proxy host and port")
                } else if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    toast("This Android WebView does not support proxy control")
                } else {
                    try {
                        val config = ProxyConfig.Builder().addProxyRule("$h:$p").build()
                        ProxyController.getInstance().setProxyOverride(config, uiExecutor) {
                            prefs.edit().putString("proxy_host", h).putInt("proxy_port", p)
                                .putString("proxy_label", country.text.toString().ifBlank { "Custom proxy" }).apply()
                            refreshStatus()
                            toast("Proxy applied to JESSY")
                            web.reload()
                        }
                    } catch (e: Exception) { toast("Could not apply proxy: ${e.message}") }
                }
            }
            .setNeutralButton("Disconnect") { _, _ ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    ProxyController.getInstance().clearProxyOverride(uiExecutor) {
                        prefs.edit().remove("proxy_host").remove("proxy_port").remove("proxy_label").apply()
                        refreshStatus(); toast("Proxy disconnected"); web.reload()
                    }
                }
            }
            .setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            if (active != null) {
                country.setText(active); host.setText(prefs.getString("proxy_host", "")); port.setText(prefs.getInt("proxy_port", 0).takeIf { it > 0 }?.toString() ?: "")
            }
        }
        dialog.show()
    }

    private fun torDialog() {
        AlertDialog.Builder(this).setTitle("Tor / onion access")
            .setMessage("JESSY supports .onion addresses when your traffic is routed through Tor. Install and open Orbot, connect it, then enable JESSY in Orbot's app-routing list. Return here and enter the .onion address. Tor is separate from the HTTP proxy setting.")
            .setPositiveButton("Open Orbot") { _, _ ->
                val launch = packageManager.getLaunchIntentForPackage("org.torproject.android")
                if (launch != null) startActivity(launch)
                else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://orbot.app/")))
            }
            .setNegativeButton("How it works", null).show()
    }

    private fun togglePrivate() {
        val enabled = !prefs.getBoolean("private_mode", false)
        prefs.edit().putBoolean("private_mode", enabled).apply()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        web.clearHistory(); web.clearCache(true); web.clearFormData()
        if (enabled) toast("Private mode on — local browsing data cleared")
        else toast("Private mode off — private session data cleared")
        refreshStatus()
    }

    private fun tabInfo() {
        AlertDialog.Builder(this).setTitle("JESSY tabs")
            .setMessage("This build currently uses one active browsing tab. Use Home to return to the search dashboard. Multi-tab support is planned for a later build.")
            .setPositiveButton("OK", null).show()
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val privateLabel = if (prefs.getBoolean("private_mode", false)) "PRIVATE ON" else "PRIVATE OFF"
        val proxyLabel = prefs.getString("proxy_label", null)?.let { " · PROXY: $it" } ?: ""
        status.text = privateLabel + proxyLabel
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    inner class SearchBridge {
        @JavascriptInterface fun search(query: String) { runOnUiThread { navigate(query) } }
    }

    override fun onSaveInstanceState(outState: Bundle) { web.saveState(outState); super.onSaveInstanceState(outState) }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() { if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onDestroy() { if (::web.isInitialized) web.destroy(); super.onDestroy() }
}
