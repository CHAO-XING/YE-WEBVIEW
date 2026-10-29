package com.ye.webview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent { YeTheme { SettingsScreen(store) { finish() } } }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var engine by remember { mutableStateOf(store.engine) }
    var custom by remember { mutableStateOf(store.customTemplate) }
    var adBlock by remember { mutableStateOf(store.adBlock) }
    var listUrl by remember { mutableStateOf(store.easyListUrl) }
    var base by remember { mutableStateOf(store.aiBase) }
    var model by remember { mutableStateOf(store.aiModel) }
    var key by remember { mutableStateOf(store.aiKey) }
    var ttsOn by remember { mutableStateOf(store.ttsEnabled) }
    var ttsBase by remember { mutableStateOf(store.ttsBase) }
    var ttsKey by remember { mutableStateOf(store.ttsKey) }
    var ttsVoice by remember { mutableStateOf(store.ttsVoice) }
    var ttsAutoVoice by remember { mutableStateOf(store.ttsAutoVoice) }
    var ttsVoiceZh by remember { mutableStateOf(store.ttsVoiceZh) }
    var ttsVoiceEn by remember { mutableStateOf(store.ttsVoiceEn) }
    var importStatus by remember { mutableStateOf("当前规则数：${AdBlocker.count()}") }
    var importing by remember { mutableStateOf(false) }
    val engines = listOf("bing" to "Bing", "google" to "Google", "baidu" to "百度", "duckduckgo" to "DuckDuckGo", "custom" to "自定义")

    fun save() {
        store.engine = engine; store.customTemplate = custom.trim(); store.adBlock = adBlock
        store.easyListUrl = listUrl.trim(); store.aiBase = base.trim(); store.aiModel = model.trim(); store.aiKey = key.trim()
        store.ttsEnabled = ttsOn; store.ttsBase = ttsBase.trim(); store.ttsKey = ttsKey.trim(); store.ttsVoice = ttsVoice.trim()
        store.ttsAutoVoice = ttsAutoVoice; store.ttsVoiceZh = ttsVoiceZh.trim(); store.ttsVoiceEn = ttsVoiceEn.trim()
        android.widget.Toast.makeText(ctx, "已保存", android.widget.Toast.LENGTH_SHORT).show()
        onBack()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("设置") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = { TextButton(::save) { Text("保存") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Section("搜索引擎") {
                engines.forEach { (k, n) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { engine = k },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(engine == k, { engine = k }); Text(n)
                    }
                }
                if (engine == "custom") OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(),
                    label = { Text("模板（%s 代表关键词）") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            }
            Section("广告拦截") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用广告拦截", Modifier.weight(1f)); Switch(adBlock, { adBlock = it })
                }
                OutlinedTextField(listUrl, { listUrl = it }, Modifier.fillMaxWidth(), label = { Text("EasyList 订阅地址") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                FilledTonalButton(enabled = !importing, onClick = {
                    importing = true; importStatus = "下载中…"
                    scope.launch {
                        importStatus = try {
                            val n = withContext(Dispatchers.IO) { AdBlocker.importFromUrl(ctx.applicationContext, listUrl.trim()) }
                            store.easyListUrl = listUrl.trim()
                            "已导入 $n 个域名，当前规则数：${AdBlocker.count()}"
                        } catch (e: Exception) { "导入失败：${e.message}" }
                        importing = false
                    }
                }) { Text("导入 / 更新 EasyList") }
                Text(importStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section("AI（OpenAI 兼容接口）") {
                OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key（仅保存在本机）") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
            }
            Section("朗读（edge-tts）") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用朗读", Modifier.weight(1f)); Switch(ttsOn, { ttsOn = it })
                }
                OutlinedTextField(ttsBase, { ttsBase = it }, Modifier.fillMaxWidth(), label = { Text("TTS Base URL") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(ttsKey, { ttsKey = it }, Modifier.fillMaxWidth(), label = { Text("Bearer Token（仅保存在本机）") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动选中/英音色", Modifier.weight(1f)); Switch(ttsAutoVoice, { ttsAutoVoice = it })
                }
                OutlinedTextField(ttsVoice, { ttsVoice = it }, Modifier.fillMaxWidth(), label = { Text("兜底 Voice（难判时）") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(ttsVoiceZh, { ttsVoiceZh = it }, Modifier.fillMaxWidth(), label = { Text("中文 Voice") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(ttsVoiceEn, { ttsVoiceEn = it }, Modifier.fillMaxWidth(), label = { Text("英文 Voice") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
            }
            Section("主页快捷方式") {
                Text("在主页点「编辑」或长按图标可增删改；此处可恢复默认。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({
                    store.resetShortcuts()
                    android.widget.Toast.makeText(ctx, "已恢复默认快捷方式", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("恢复默认") }
            }
            Button(::save, Modifier.fillMaxWidth().height(52.dp)) { Text("保存") }
        }
    }
}
