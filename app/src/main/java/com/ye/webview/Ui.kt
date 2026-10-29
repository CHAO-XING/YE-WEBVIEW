package com.ye.webview

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

const val DEMO_PAGE = "https://www.wikipedia.org/"

@Composable
fun BrowserApp(b: Browser, shot: String?, onSettings: () -> Unit, onFinish: () -> Unit) {
    var showMore by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var listSheet by remember { mutableStateOf<String?>(null) }
    var showAi by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(shot) {
        when (shot) {
            "page" -> b.go(DEMO_PAGE)
            "edit" -> { delay(2500); b.demoDialog = true }
            "more" -> { b.go(DEMO_PAGE); delay(6000); showMore = true }
            "tabs" -> {
                b.go(DEMO_PAGE); delay(5000)
                b.newTab("https://github.com"); delay(6000)
                b.newTab(); delay(300)
                b.captureThumb(); showTabs = true
            }
        }
    }

    BackHandler {
        if (showTabs) showTabs = false else if (!b.back()) onFinish()
    }

    Box(Modifier.fillMaxSize()) {
    Surface(Modifier.fillMaxSize(), color = cs.surfaceContainer) {
        if (showTabs) {
            TabsScreen(b) { showTabs = false }
        } else {
            val t = b.tab
            val barsShown = b.barsVisible || t.isHome
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                AnimatedVisibility(barsShown, enter = expandVertically(), exit = shrinkVertically()) {
                    AddressBar(b) { showAi = true }
                }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (!t.isHome && t.progress < 100) {
                        LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxSize())
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(cs.surface)) {
                    val w = t.webView
                    if (t.isHome || w == null) HomePage(b)
                    else key(t.id) {
                        AndroidView(factory = { (w.parent as? ViewGroup)?.removeView(w); w }, modifier = Modifier.fillMaxSize())
                    }
                }
                AnimatedVisibility(barsShown, enter = expandVertically(), exit = shrinkVertically()) {
                    BottomBar(b, onTabs = { b.captureThumb(); showTabs = true }, onMore = { showMore = true })
                }
            }
        }
    }

    if (showMore) MoreSheet(b, onDismiss = { showMore = false }) { k ->
        showMore = false
        val t = b.tab
        when (k) {
            "new" -> b.newTab()
            "bookmark" -> if (t.isHome) Toast.makeText(ctx, "主页无法收藏", Toast.LENGTH_SHORT).show() else {
                b.store.addBookmark(Entry(t.title.ifBlank { t.url ?: "" }, t.url ?: ""))
                Toast.makeText(ctx, "已添加书签", Toast.LENGTH_SHORT).show()
            }
            "bookmarks", "history" -> listSheet = k
            "reload" -> t.webView?.reload()
            "share" -> t.url?.let {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it), "分享"))
            }
            "ai" -> if (t.isHome) Toast.makeText(ctx, "请先打开一个网页", Toast.LENGTH_SHORT).show() else showAi = true
            "tts" -> {
                if (t.isHome) Toast.makeText(ctx, "请先打开一个网页", Toast.LENGTH_SHORT).show()
                else if (!b.store.ttsEnabled) Toast.makeText(ctx, "朗读已关闭，请在设置中开启", Toast.LENGTH_SHORT).show()
                else {
                    val w = t.webView
                    if (w == null) Toast.makeText(ctx, "请先打开一个网页", Toast.LENGTH_SHORT).show()
                    else {
                        Toast.makeText(ctx, "正在提取正文…", Toast.LENGTH_SHORT).show()
                        w.evaluateJavascript(EXTRACT_JS) { raw ->
                            try {
                                val json = JSONObject(JSONArray("[$raw]").getString(0))
                                val body = json.optString("text").trim()
                                if (body.isBlank()) Toast.makeText(ctx, "未能提取到正文", Toast.LENGTH_SHORT).show()
                                else b.speakText(body.take(12000))
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "提取失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            "settings" -> onSettings()
            "tohome" -> if (t.isHome) Toast.makeText(ctx, "请先打开一个网页", Toast.LENGTH_SHORT).show() else {
                b.updateShortcuts(b.shortcuts.filter { it.url != t.url } + Entry(t.title.ifBlank { displayUrl(t.url) }.take(20), t.url ?: ""))
                Toast.makeText(ctx, "已添加到主页", Toast.LENGTH_SHORT).show()
            }
        }
    }
    listSheet?.let { kind -> EntrySheet(kind, b) { listSheet = null } }
    if (showAi) AiSheet(b) { showAi = false }
    if (b.ttsPlaying || b.ttsStatus != null) TtsBar(b)
    } // Box overlay root
}

fun displayUrl(u: String?): String = u?.let { Uri.parse(it).host?.removePrefix("www.") ?: it } ?: ""
fun hostOf(u: String): String = Uri.parse(u).host?.removePrefix("www.") ?: u

@Composable
fun AddressBar(b: Browser, onAi: () -> Unit) {
    val t = b.tab
    val cs = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    var value by remember(t.id) { mutableStateOf(TextFieldValue(displayUrl(t.url))) }
    LaunchedEffect(t.url, focused) { if (!focused) value = TextFieldValue(displayUrl(t.url)) }
    val fm = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(50), color = cs.surfaceContainerHighest, modifier = Modifier.weight(1f).height(48.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.url?.startsWith("https") == true) Icons.Filled.Lock else Icons.Filled.Search, null,
                    Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { b.go(value.text); fm.clearFocus() }),
                    modifier = Modifier.weight(1f).onFocusChanged { f ->
                        if (f.isFocused && !focused) {
                            val s = t.url ?: ""
                            value = TextFieldValue(s, TextRange(0, s.length))
                        }
                        focused = f.isFocused
                    },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.text.isEmpty()) Text("搜索或输入网址", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                    }
                )
                if (focused && value.text.isNotEmpty()) {
                    IconButton({ value = TextFieldValue("") }) { Icon(Icons.Filled.Close, "清除", tint = cs.onSurfaceVariant) }
                } else {
                    IconButton(onAi, enabled = !t.isHome) {
                        Icon(Icons.Filled.AutoAwesome, "AI 摘要", Modifier.size(20.dp),
                            tint = if (t.isHome) cs.onSurfaceVariant.copy(alpha = 0.38f) else cs.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBar(b: Browser, onTabs: () -> Unit, onMore: () -> Unit) {
    val t = b.tab
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        IconButton({ b.back() }, enabled = !t.isHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退") }
        IconButton({ t.webView?.goForward() }, enabled = t.canFwd) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "前进") }
        IconButton({ b.home() }) { Icon(Icons.Outlined.Home, "主页") }
        IconButton(onTabs) {
            Box(Modifier.size(22.dp).border(2.dp, LocalContentColor.current, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text("${b.tabs.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onMore) { Icon(Icons.Filled.MoreVert, "更多") }
    }
}

data class Site(val title: String, val url: String)

val DEFAULT_SITES = listOf(
    Site("Bing", "https://www.bing.com"), Site("百度", "https://www.baidu.com"),
    Site("知乎", "https://www.zhihu.com"), Site("哔哩哔哩", "https://www.bilibili.com"),
    Site("GitHub", "https://github.com"), Site("微博", "https://weibo.com"),
    Site("豆瓣", "https://www.douban.com"), Site("维基百科", "https://zh.wikipedia.org"),
)

private val AVATAR_COLORS = listOf(0xFF1A73E8, 0xFFD93025, 0xFF188038, 0xFFE37400, 0xFF9334E6, 0xFF007B83, 0xFFC5221F, 0xFF3949AB)

@Composable
fun SiteIcon(url: String, title: String, size: Dp) {
    val host = hostOf(url)
    var ok by remember(host) { mutableStateOf(false) }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        if (!ok) {
            Box(Modifier.fillMaxSize().background(Color(AVATAR_COLORS[(host.hashCode() and 0x7fffffff) % AVATAR_COLORS.size])),
                contentAlignment = Alignment.Center) {
                Text((title.ifBlank { host }).take(1).uppercase(), color = Color.White,
                    style = if (size > 36.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
            }
        }
        AsyncImage(model = "https://icons.duckduckgo.com/ip3/$host.ico", contentDescription = null,
            modifier = Modifier.size(size * 0.6f), onSuccess = { ok = true })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePage(b: Browser) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Pair<Int, Entry>?>(null) } // index -1 = add
    LaunchedEffect(b.demoDialog) { if (b.demoDialog) { dialog = -1 to Entry("", ""); b.demoDialog = false } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val list = b.shortcuts

    fun delete(i: Int) {
        val old = b.shortcuts
        val e = old[i]
        b.updateShortcuts(old.filterIndexed { j, _ -> j != i })
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("已删除「${e.title}」", actionLabel = "撤销", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) b.updateShortcuts(old)
        }
    }
    fun move(i: Int, d: Int) {
        val l = b.shortcuts.toMutableList(); val j = i + d
        if (j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        b.updateShortcuts(l)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(56.dp))
            Text("YE", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = cs.primary)
            Text("干净 · 无广告 · AI 阅读", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Surface(shape = RoundedCornerShape(50), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(q, { q = it }, singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { b.go(q) }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (q.isEmpty()) Text("搜索", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                                inner()
                            }
                        })
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("快捷方式", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton({ editMode = !editMode }) {
                    Icon(if (editMode) Icons.Filled.Check else Icons.Filled.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editMode) "完成" else "编辑")
                }
            }
            val tiles: List<Pair<Int, Entry?>> = list.mapIndexed { i, e -> i to e } + listOf(-1 to null)
            tiles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (i, e) ->
                        Box(Modifier.weight(1f)) {
                            if (e == null) {
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable { dialog = -1 to Entry("", "") }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, cs.outlineVariant, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "添加", tint = cs.primary) }
                                    Spacer(Modifier.height(6.dp))
                                    Text("添加", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                }
                            } else key(e.url) {
                                var menu by remember { mutableStateOf(false) }
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(onClick = { if (editMode) dialog = i to e else b.go(e.url) }, onLongClick = { menu = true })
                                    .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        SiteIcon(e.url, e.title, 48.dp)
                                        if (editMode) Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape)
                                            .background(cs.error).clickable { delete(i) }, contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Close, "删除", Modifier.size(14.dp), tint = cs.onError)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(e.title.ifBlank { hostOf(e.url) }, style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                        onClick = { menu = false; dialog = i to e })
                                    DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = { menu = false; delete(i) })
                                    if (i > 0) DropdownMenuItem(text = { Text("移到前面") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }, onClick = { menu = false; move(i, -1) })
                                    if (i < list.size - 1) DropdownMenuItem(text = { Text("移到后面") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }, onClick = { menu = false; move(i, 1) })
                                }
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    dialog?.let { (i, e) ->
        ShortcutDialog(e, i < 0, onDismiss = { dialog = null }) { ne ->
            val l = b.shortcuts.toMutableList()
            if (i < 0 || i >= l.size) { l.removeAll { it.url == ne.url }; l.add(ne) } else l[i] = ne
            b.updateShortcuts(l); dialog = null
        }
    }
}

@Composable
fun ShortcutDialog(init: Entry, isNew: Boolean, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var name by remember { mutableStateOf(init.title) }
    var url by remember { mutableStateOf(init.url) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (isNew) Icons.Filled.Add else Icons.Filled.Edit, null) },
        title = { Text(if (isNew) "添加快捷方式" else "编辑快捷方式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(url, { url = it; err = null }, Modifier.fillMaxWidth(), label = { Text("网址") },
                    placeholder = { Text("example.com") }, singleLine = true, isError = err != null,
                    supportingText = err?.let { m -> { Text(m) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(16.dp))
            }
        },
        confirmButton = {
            TextButton({
                val n = Store.normalizeUrl(url)
                if (n == null) err = "请输入有效的网址" else onSave(Entry(name.trim().ifBlank { hostOf(n) }, n))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

@Composable
fun TabIcon(t: Tab, size: Dp) {
    val icon = t.icon
    when {
        icon != null -> Image(icon.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(4.dp)))
        t.isHome -> Icon(Icons.Outlined.Home, null, Modifier.size(size), tint = MaterialTheme.colorScheme.primary)
        else -> SiteIcon(t.url ?: "", t.title, size)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(b: Browser, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.surfaceContainer,
        topBar = {
            TopAppBar(title = { Text("${b.tabs.size} 个标签页") },
                navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surfaceContainer))
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { b.newTab(); onDone() },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("新建标签页") })
        }
    ) { pad ->
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(b.tabs, key = { _, t -> t.id }) { i, t ->
                val sel = i == b.current
                Card(onClick = { b.select(i); onDone() }, shape = RoundedCornerShape(20.dp),
                    border = if (sel) BorderStroke(2.dp, cs.primary) else null,
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
                    Row(Modifier.padding(start = 12.dp, end = 2.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        TabIcon(t, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (t.isHome) "主页" else t.title.ifBlank { displayUrl(t.url) }, Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton({ b.close(i) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "关闭", Modifier.size(18.dp)) }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(14.dp)).background(cs.surface), contentAlignment = Alignment.Center) {
                        val th = t.thumb
                        when {
                            t.isHome -> Text("YE", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                            th != null -> Image(th.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                            else -> TabIcon(t, 40.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(b: Browser, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = b.tab
    val bookmarked = remember(t.url) { t.url != null && b.store.bookmarks().any { it.url == t.url } }
    val actions: List<Triple<String, String, ImageVector>> = listOf(
        Triple("new", "新建标签", Icons.Filled.Add),
        Triple("bookmark", if (bookmarked) "已收藏" else "添加书签", if (bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder),
        Triple("bookmarks", "书签", Icons.Outlined.Bookmarks),
        Triple("history", "历史记录", Icons.Outlined.History),
        Triple("reload", "刷新", Icons.Filled.Refresh),
        Triple("share", "分享", Icons.Outlined.Share),
        Triple("ai", "AI 摘要", Icons.Filled.AutoAwesome),
        Triple("tts", "朗读本页", Icons.Filled.RecordVoiceOver),
        Triple("tohome", "添加到主页", Icons.Filled.AddToHomeScreen),
        Triple("settings", "设置", Icons.Outlined.Settings),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            actions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (k, label, icon) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onAction(k) }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(cs.secondaryContainer),
                                contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = cs.onSecondaryContainer)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
                ListItem(
                    headlineContent = { Text("广告拦截") },
                    supportingContent = { Text("已加载 ${AdBlocker.count()} 条域名规则") },
                    leadingContent = { Icon(Icons.Filled.Shield, null, tint = cs.primary) },
                    trailingContent = { Switch(b.adBlock, { b.adBlock = it; b.store.adBlock = it }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(kind: String, b: Browser, onDismiss: () -> Unit) {
    val isBm = kind == "bookmarks"
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Entry>>(if (isBm) b.store.bookmarks() else b.store.history()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isBm) "书签" else "历史记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!isBm && list.isNotEmpty()) TextButton({ b.store.clearHistory(); list = emptyList() }) { Text("清空") }
        }
        if (list.isEmpty()) Text("暂无", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.url }) { e ->
                ListItem(
                    headlineContent = { Text(e.title.ifBlank { e.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(e.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { SiteIcon(e.url, e.title, 40.dp) },
                    trailingContent = if (isBm) { {
                        Row {
                            IconButton({
                                b.updateShortcuts(b.shortcuts.filter { it.url != e.url } + Entry(e.title.ifBlank { hostOf(e.url) }.take(20), e.url))
                                Toast.makeText(ctx, "已添加到主页", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Filled.AddToHomeScreen, "添加到主页") }
                            IconButton({ b.store.removeBookmark(e.url); list = b.store.bookmarks() }) { Icon(Icons.Outlined.Delete, "删除") }
                        }
                    } } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { b.go(e.url); onDismiss() }
                )
            }
        }
    }
}

@Composable
fun TtsBar(b: Browser) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = cs.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.RecordVoiceOver, null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("朗读中", style = MaterialTheme.typography.titleSmall)
                    Text(
                        b.ttsStatus ?: "播放中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(onClick = { b.stopTts() }) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("停止")
                }
            }
        }
    }
}

// Readability-style extraction: prefer <article>/<main>, else the element with the most paragraph text.
const val EXTRACT_JS = """
(function(){
  function txt(e){return (e.innerText||'').replace(/\n{3,}/g,'\n\n').trim();}
  var c=document.querySelector('article')||document.querySelector('main')||document.querySelector('[role=main]');
  if(!c||txt(c).length<200){
    var best=null,score=0;
    document.querySelectorAll('div,section,td').forEach(function(e){
      var s=0;e.querySelectorAll(':scope > p').forEach(function(p){s+=(p.innerText||'').length;});
      if(s>score){score=s;best=e;}
    });
    if(best&&score>200)c=best;
  }
  if(!c)c=document.body;
  return JSON.stringify({title:document.title,url:location.href,text:txt(c).substring(0,12000)});
})()
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(b: Browser, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val ai = remember { AiClient(b.store) }
    val msgs = remember { mutableStateListOf<Pair<String, String>>() }
    var status by remember { mutableStateOf<String?>("正在提取页面正文…") }
    var loading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }

    fun ask() {
        loading = true; status = null
        scope.launch {
            try { msgs.add("assistant" to ai.chat(msgs.toList())) } catch (e: Exception) { status = "⚠️ ${e.message}" }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        val w = b.tab.webView
        if (w == null) { status = "请先打开一个网页"; return@LaunchedEffect }
        w.evaluateJavascript(EXTRACT_JS) { raw ->
            try {
                val json = JSONObject(JSONArray("[$raw]").getString(0))
                val text = json.optString("text")
                pageTitle = json.optString("title")
                if (text.isBlank()) { status = "未能提取到正文"; return@evaluateJavascript }
                msgs.add("system" to "你是网页阅读助手。根据用户提供的网页正文，用中文回答。")
                msgs.add("user" to "网页标题：$pageTitle\n网址：${json.optString("url")}\n\n正文：\n$text\n\n请用中文给出简洁的要点摘要。")
                ask()
            } catch (e: Exception) { status = "提取失败：${e.message}" }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = cs.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI 阅读助手", style = MaterialTheme.typography.titleLarge)
                    if (pageTitle.isNotBlank()) Text(pageTitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(msgs.drop(2)) { (role, content) ->
                    val user = role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(18.dp), color = if (user) cs.primaryContainer else cs.surfaceContainerHighest,
                            modifier = Modifier.widthIn(max = 320.dp)) {
                            SelectionContainer { Text(content, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                if (loading) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("思考中…", color = cs.onSurfaceVariant)
                    }
                }
                status?.let { s -> item { Text(s, color = cs.onSurfaceVariant) } }
            }
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("继续提问…") },
                    shape = RoundedCornerShape(24.dp), maxLines = 4)
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = {
                    val q = input.trim()
                    if (q.isNotEmpty() && msgs.size >= 2 && !loading) { input = ""; msgs.add("user" to q); ask() }
                }, enabled = !loading) { Icon(Icons.AutoMirrored.Filled.Send, "发送") }
            }
        }
    }
}
