package com.ye.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import org.json.JSONArray
import org.json.JSONObject
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
    var ttsPlaying by mutableStateOf(false)
    var ttsStatus by mutableStateOf<String?>(null)
    var ttsSource by mutableStateOf("选区")
    var ttsIndex by mutableIntStateOf(0)
    var ttsTotal by mutableIntStateOf(0)
    var ttsError by mutableStateOf<String?>(null)
    var showSelectionTtsFallback by mutableStateOf(false)
    fun updateShortcuts(l: List<Entry>) { store.saveShortcuts(l); shortcuts = l }
    private var nextId = 1L
    private var selectionMode: ActionMode? = null
    private var lastHighlightKey: String? = null
    private var lastTtsErrorAt = 0L
    private val ttsClient by lazy { TtsClient(store) }
    private val ttsSession by lazy {
        TtsSession(act, ttsClient).also { s ->
            s.setListener { state ->
                ttsPlaying = state.playing
                ttsStatus = state.status
                ttsSource = state.source
                ttsIndex = state.index
                ttsTotal = state.total
                ttsError = state.error
                when {
                    !state.playing -> clearTtsHighlight()
                    state.error != null -> {
                        clearTtsHighlight()
                        val now = System.currentTimeMillis()
                        if (now - lastTtsErrorAt > 1500) {
                            lastTtsErrorAt = now
                            android.widget.Toast.makeText(act, state.error, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    !state.text.isNullOrBlank() -> highlightTts(state.text)
                }
            }
        }
    }

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

    fun destroyAll() {
        ttsSession.release()
        tabs.forEach { it.webView?.destroy() }
    }

    fun speakText(text: String, source: String = "选区") {
        val t = text.trim()
        if (t.isEmpty()) {
            android.widget.Toast.makeText(act, "未选中文字", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!store.ttsEnabled) {
            android.widget.Toast.makeText(act, "朗读已关闭，请在设置中开启", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        showSelectionTtsFallback = false
        ttsSource = source
        ttsIndex = 0
        ttsTotal = 0
        ttsError = null
        lastTtsErrorAt = 0L
        ttsSession.speak(t, source)
    }

    fun stopTts() {
        ttsSession.stop()
        clearTtsHighlight()
    }

    fun readSelection(mode: ActionMode? = selectionMode) {
        val w = tab.webView
        if (w == null) {
            showSelectionTtsFallback = false
            runCatching { mode?.finish() }
            return
        }
        w.evaluateJavascript(
            "(function(){try{return window.getSelection().toString();}catch(e){return '';}})()"
        ) { raw ->
            val selected = decodeJsString(raw)
            showSelectionTtsFallback = false
            if (selected.isBlank()) {
                android.widget.Toast.makeText(act, "未选中文字", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                speakText(selected, "选区")
            }
            runCatching { mode?.finish() }
        }
    }

    fun dismissSelectionFallback() {
        showSelectionTtsFallback = false
        runCatching { selectionMode?.finish() }
    }

    private fun decodeJsString(raw: String?): String = try {
        JSONArray("[$raw]").getString(0)
    } catch (_: Exception) {
        raw?.trim()?.trim('"') ?: ""
    }

    private fun highlightTts(text: String) {
        if (text == lastHighlightKey) return
        val w = tab.webView ?: return
        lastHighlightKey = text
        val js = TTS_HIGHLIGHT_JS.replace("__YE_TTS_TEXT__", JSONObject.quote(text))
        runCatching { w.evaluateJavascript(js, null) }
    }

    private fun clearTtsHighlight() {
        lastHighlightKey = null
        val w = tab.webView ?: return
        runCatching { w.evaluateJavascript(TTS_CLEAR_JS, null) }
    }

    companion object {
        private const val MENU_TTS = 0x54455301 // "TES\x01"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(t: Tab): WebView = object : WebView(act) {
        override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
            val wrapped = wrapSelectionCallback(callback)
            return super.startActionMode(wrapped, type)
        }
        override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
            val wrapped = wrapSelectionCallback(callback)
            return super.startActionMode(wrapped)
        }
    }.apply {
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
                lastHighlightKey = null
                showSelectionTtsFallback = false
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

    private fun wrapSelectionCallback(callback: ActionMode.Callback?): ActionMode.Callback {
        return if (callback is ActionMode.Callback2) {
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean =
                    onCreateSelectionMode(mode, menu, callback)

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
                    onPrepareSelectionMode(mode, menu, callback)

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                    onSelectionItemClicked(mode, item, callback)

                override fun onDestroyActionMode(mode: ActionMode) =
                    onDestroySelectionMode(mode, callback)

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) =
                    callback.onGetContentRect(mode, view, outRect)
            }
        } else {
            object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean =
                    onCreateSelectionMode(mode, menu, callback)

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
                    onPrepareSelectionMode(mode, menu, callback)

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                    onSelectionItemClicked(mode, item, callback)

                override fun onDestroyActionMode(mode: ActionMode) =
                    onDestroySelectionMode(mode, callback)
            }
        }
    }

    private fun onCreateSelectionMode(mode: ActionMode, menu: Menu, callback: ActionMode.Callback?): Boolean {
        val ok = callback?.onCreateActionMode(mode, menu) ?: true
        if (ok) {
            selectionMode = mode
            showSelectionTtsFallback = store.ttsEnabled
        }
        if (store.ttsEnabled) addTtsMenuItem(menu)
        return ok
    }

    private fun onPrepareSelectionMode(mode: ActionMode, menu: Menu, callback: ActionMode.Callback?): Boolean {
        val ok = callback?.onPrepareActionMode(mode, menu) ?: false
        if (store.ttsEnabled) {
            addTtsMenuItem(menu)
        } else {
            menu.findItem(MENU_TTS)?.let { menu.removeItem(it.itemId) }
        }
        return ok
    }

    private fun onSelectionItemClicked(mode: ActionMode, item: MenuItem, callback: ActionMode.Callback?): Boolean {
        if (item.itemId == MENU_TTS) {
            readSelection(mode)
            return true
        }
        return callback?.onActionItemClicked(mode, item) ?: false
    }

    private fun onDestroySelectionMode(mode: ActionMode, callback: ActionMode.Callback?) {
        if (selectionMode === mode) selectionMode = null
        showSelectionTtsFallback = false
        callback?.onDestroyActionMode(mode)
    }

    private fun addTtsMenuItem(menu: Menu) {
        if (menu.findItem(MENU_TTS) == null) {
            menu.add(0, MENU_TTS, Menu.NONE, "朗读")
        }
    }
}

private const val TTS_CLEAR_JS = """
(function(){
  try {
    var ns = document.querySelectorAll('.ye-tts-current');
    for (var i = ns.length - 1; i >= 0; i--) {
      var n = ns[i], p = n.parentNode;
      if (!p) continue;
      while (n.firstChild) p.insertBefore(n.firstChild, n);
      p.removeChild(n);
      p.normalize();
    }
    return true;
  } catch (e) { return false; }
})()
"""

private const val TTS_HIGHLIGHT_JS = """
(function(text){
  try {
    function clearMarks(){
      var ns = document.querySelectorAll('.ye-tts-current');
      for (var i = ns.length - 1; i >= 0; i--) {
        var n = ns[i], p = n.parentNode;
        if (!p) continue;
        while (n.firstChild) p.insertBefore(n.firstChild, n);
        p.removeChild(n);
        p.normalize();
      }
    }
    clearMarks();
    if (!text) return false;
    var style = document.getElementById('ye-tts-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'ye-tts-style';
      style.textContent = '.ye-tts-current{background:rgba(255,213,0,0.45);border-radius:2px;}';
      (document.head || document.documentElement).appendChild(style);
    }
    var root = document.body || document.documentElement;
    if (!root) return false;
    var target = String(text).replace(/\s+/g, '');
    if (!target) return false;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
    var nodes = [], node;
    while (node = walker.nextNode()) {
      var tag = node.parentNode && node.parentNode.nodeName;
      if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'TEXTAREA') continue;
      nodes.push(node);
    }
    var norm = '', map = [];
    for (var i = 0; i < nodes.length; i++) {
      var d = nodes[i].data || '';
      for (var j = 0; j < d.length; j++) {
        var ch = d.charAt(j);
        if (/\s/.test(ch)) continue;
        norm += ch;
        map.push([nodes[i], j]);
      }
    }
    var idx = norm.indexOf(target);
    if (idx < 0) return false;
    var start = map[idx], end = map[idx + target.length - 1];
    if (!start || !end) return false;
    var startNode = start[0], startOff = start[1];
    var endNode = end[0], endOff = end[1] + 1;
    var startIndex = nodes.indexOf(startNode), endIndex = nodes.indexOf(endNode);
    if (startIndex < 0 || endIndex < startIndex) return false;
    for (var k = startIndex; k <= endIndex; k++) {
      var t = nodes[k];
      var a = (k === startIndex) ? startOff : 0;
      var b = (k === endIndex) ? endOff : (t.data || '').length;
      if (b <= a) continue;
      try {
        var r = document.createRange();
        r.setStart(t, a);
        r.setEnd(t, b);
        var m = document.createElement('mark');
        m.className = 'ye-tts-current';
        var frag = r.extractContents();
        m.appendChild(frag);
        r.insertNode(m);
      } catch (e) {}
    }
    return true;
  } catch (e) { return false; }
})(__YE_TTS_TEXT__)
"""
