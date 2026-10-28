package com.ye.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {
    lateinit var store: Store
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var container: FrameLayout
    private lateinit var homeView: View
    private lateinit var tabBtn: TextView
    private val tabs = mutableListOf<WebView?>() // null = home page
    private var current = 0

    val currentWeb: WebView? get() = tabs.getOrNull(current)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Thread { AdBlocker.init(applicationContext) }.start()
        buildUi()
        newTab(intent?.dataString)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = currentWeb
                when {
                    w != null && w.canGoBack() -> w.goBack()
                    w != null -> { tabs[current] = null; w.destroy(); showCurrent() }
                    tabs.size > 1 -> closeTab(current)
                    else -> finish()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { newTab(it) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun iconBtn(res: Int, desc: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(res); contentDescription = desc
        setBackgroundColor(0)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        urlBar = EditText(this).apply {
            hint = "搜索或输入网址"; isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, a, ev ->
                if (a == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) { go(text.toString()); true } else false
            }
            setOnFocusChangeListener { _, f -> if (f) selectAll() }
        }
        tabBtn = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 14f
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(44))
            setOnClickListener { showTabs() }
        }
        bar.addView(urlBar)
        bar.addView(iconBtn(android.R.drawable.ic_menu_rotate, "刷新") { currentWeb?.reload() })
        bar.addView(iconBtn(android.R.drawable.ic_menu_help, "AI") { AiSheet(this).show() })
        bar.addView(tabBtn)
        bar.addView(iconBtn(android.R.drawable.ic_menu_more, "菜单") { showMenu() })
        bar.addView(iconBtn(android.R.drawable.ic_menu_preferences, "设置") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; layoutParams = LinearLayout.LayoutParams(-1, dp(3)); visibility = View.GONE
        }
        container = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        homeView = buildHome()
        root.addView(bar); root.addView(progress); root.addView(container)
        setContentView(root)
    }

    private fun buildHome(): View {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(96), dp(24), dp(24))
        }
        l.addView(TextView(this).apply { text = "YE"; textSize = 48f; gravity = Gravity.CENTER })
        l.addView(TextView(this).apply { text = "干净 · 无广告 · AI 阅读"; gravity = Gravity.CENTER; alpha = 0.6f })
        return l
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (store.adBlock && !req.isForMainFrame && AdBlocker.isBlocked(req.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val s = req.url.scheme ?: return false
                if (s == "http" || s == "https") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, req.url)) } catch (_: Exception) {}
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (view == currentWeb) { urlBar.setText(url); this@MainActivity.progress.visibility = View.VISIBLE }
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (view == currentWeb) this@MainActivity.progress.visibility = View.GONE
                store.addHistory(Entry(view.title ?: url ?: "", url ?: ""))
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { if (view == currentWeb) this@MainActivity.progress.progress = p }
        }
    }

    fun go(input: String) {
        if (input.isBlank()) return
        val url = store.toUrl(input)
        var w = currentWeb
        if (w == null) { w = createWebView(); tabs[current] = w; showCurrent() }
        w.loadUrl(url)
        urlBar.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    private fun newTab(url: String? = null) {
        tabs.add(null); current = tabs.size - 1
        showCurrent()
        if (!url.isNullOrBlank()) go(url)
    }

    private fun closeTab(i: Int) {
        tabs.removeAt(i)?.destroy()
        if (tabs.isEmpty()) tabs.add(null)
        current = current.coerceAtMost(tabs.size - 1)
        showCurrent()
    }

    private fun showCurrent() {
        container.removeAllViews()
        val w = currentWeb
        if (w == null) { container.addView(homeView); urlBar.setText("") }
        else { (w.parent as? ViewGroup)?.removeView(w); container.addView(w); urlBar.setText(w.url) }
        tabBtn.text = tabs.size.toString()
    }

    private fun showTabs() {
        val names = tabs.mapIndexed { i, w -> (if (i == current) "● " else "") + (w?.title?.ifBlank { null } ?: w?.url ?: "主页") }
        AlertDialog.Builder(this).setTitle("标签页")
            .setItems(names.toTypedArray()) { _, i -> current = i; showCurrent() }
            .setPositiveButton("新建") { _, _ -> newTab() }
            .setNegativeButton("关闭当前") { _, _ -> closeTab(current) }
            .show()
    }

    private fun showMenu() {
        val items = arrayOf("新建标签页", "添加书签", "书签", "历史记录", "前进", "分享")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> newTab()
                1 -> currentWeb?.let {
                    store.addBookmark(Entry(it.title ?: it.url ?: "", it.url ?: ""))
                    Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show()
                }
                2 -> showEntries("书签", store.bookmarks(), true)
                3 -> showEntries("历史记录", store.history(), false)
                4 -> currentWeb?.takeIf { it.canGoForward() }?.goForward()
                5 -> currentWeb?.url?.let {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it), "分享"))
                }
            }
        }.show()
    }

    private fun showEntries(title: String, list: List<Entry>, bookmarks: Boolean) {
        val b = AlertDialog.Builder(this).setTitle(title)
        if (list.isEmpty()) b.setMessage("暂无")
        else b.setItems(list.map { it.title.ifBlank { it.url } + "\n" + it.url }.toTypedArray()) { _, i -> go(list[i].url) }
        if (bookmarks && list.isNotEmpty()) {
            b.setNeutralButton("管理") { _, _ ->
                AlertDialog.Builder(this).setTitle("删除书签")
                    .setItems(list.map { it.title.ifBlank { it.url } }.toTypedArray()) { _, i -> store.removeBookmark(list[i].url) }
                    .show()
            }
        }
        if (!bookmarks) b.setNeutralButton("清空") { _, _ -> store.clearHistory() }
        b.setPositiveButton("关闭", null).show()
    }

    override fun onDestroy() {
        tabs.forEach { it?.destroy() }
        super.onDestroy()
    }

    @Suppress("unused")
    private fun open(uri: Uri) = go(uri.toString())
}
