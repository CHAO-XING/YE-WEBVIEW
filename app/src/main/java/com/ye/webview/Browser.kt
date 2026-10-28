package com.ye.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

class Tab(val id: Long) {
    var webView: WebView? = null
    var url by mutableStateOf<String?>(null) // null = home page
    var title by mutableStateOf("")
    var icon by mutableStateOf<Bitmap?>(null)
    var thumb by mutableStateOf<Bitmap?>(null)
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canFwd by mutableStateOf(false)
    val isHome get() = url == null
}

class Browser(private val act: Activity, val store: Store) {
    val tabs = mutableStateListOf<Tab>()
    var current by mutableIntStateOf(0)
    var barsVisible by mutableStateOf(true)
    var adBlock by mutableStateOf(store.adBlock)
    var shortcuts by mutableStateOf<List<Entry>>(store.shortcuts())
    var demoDialog by mutableStateOf(false)
    fun updateShortcuts(l: List<Entry>) { store.saveShortcuts(l); shortcuts = l }
    private var nextId = 1L

    val tab: Tab get() = tabs[current.coerceIn(0, tabs.size - 1)]

    fun newTab(url: String? = null) {
        if (tabs.isNotEmpty()) captureThumb()
        tabs.add(Tab(nextId++)); current = tabs.size - 1; barsVisible = true
        if (!url.isNullOrBlank()) go(url)
    }

    fun select(i: Int) { current = i; barsVisible = true }

    fun close(i: Int) {
        val t = tabs.removeAt(i)
        destroyLater(t.webView); t.webView = null
        if (tabs.isEmpty()) tabs.add(Tab(nextId++))
        if (i < current) current-- 
        if (current >= tabs.size) current = tabs.size - 1
    }

    private fun destroyLater(w: WebView?) {
        w ?: return
        w.stopLoading()
        act.window.decorView.post { (w.parent as? ViewGroup)?.removeView(w); w.destroy() }
    }

    fun go(input: String) {
        if (input.isBlank()) return
        val url = store.toUrl(input)
        val t = tab
        val w = t.webView ?: createWebView(t).also { t.webView = it }
        t.title = ""; t.icon = null; t.progress = 0
        t.url = url
        barsVisible = true
        w.loadUrl(url)
    }

    fun home() {
        val t = tab
        val w = t.webView
        t.webView = null; t.url = null; t.title = ""; t.icon = null; t.canBack = false; t.canFwd = false; t.progress = 100
        destroyLater(w)
        barsVisible = true
    }

    fun back(): Boolean {
        val t = tab; val w = t.webView
        return when {
            w != null && w.canGoBack() -> { w.goBack(); true }
            !t.isHome -> { home(); true }
            tabs.size > 1 -> { close(current); true }
            else -> false
        }
    }

    fun captureThumb() {
        val t = tab; val w = t.webView ?: return
        if (w.width == 0 || w.height == 0) return
        try {
            val s = 0.4f
            val bmp = Bitmap.createBitmap((w.width * s).toInt(), (w.height * s).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(s, s)
            c.translate(-w.scrollX.toFloat(), -w.scrollY.toFloat())
            w.draw(c)
            t.thumb = bmp
        } catch (_: Throwable) {}
    }

    fun destroyAll() = tabs.forEach { it.webView?.destroy() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(t: Tab): WebView = WebView(act).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        }
        setOnScrollChangeListener { _, _, y, _, oldY ->
            if (t !== tab) return@setOnScrollChangeListener
            val dy = y - oldY
            if (y < 16) barsVisible = true
            else if (dy > 12) barsVisible = false
            else if (dy < -12) barsVisible = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (adBlock && !req.isForMainFrame && AdBlocker.isBlocked(req.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val s = req.url.scheme ?: return false
                if (s == "http" || s == "https") return false
                try { act.startActivity(Intent(Intent.ACTION_VIEW, req.url)) } catch (_: Exception) {}
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null) t.url = url
                t.progress = 5
            }
            override fun onPageFinished(view: WebView, url: String?) {
                t.progress = 100
                t.title = view.title ?: ""
                store.addHistory(Entry(view.title ?: url ?: "", url ?: ""))
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != null) t.url = url
                t.canBack = view.canGoBack(); t.canFwd = view.canGoForward()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { t.progress = p }
            override fun onReceivedTitle(view: WebView, title: String?) { t.title = title ?: "" }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) { t.icon = icon }
        }
    }
}
