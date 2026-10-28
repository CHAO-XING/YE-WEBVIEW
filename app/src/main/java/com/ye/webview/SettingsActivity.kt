package com.ye.webview

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun header(s: String) = l.addView(TextView(this).apply { text = s; textSize = 18f; setPadding(0, pad, 0, pad / 2) })
        fun field(label: String, v: String, pwd: Boolean = false): EditText {
            l.addView(TextView(this).apply { text = label })
            return EditText(this).apply {
                setText(v); isSingleLine = true
                if (pwd) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                l.addView(this)
            }
        }

        header("搜索引擎")
        val engines = listOf("bing" to "Bing", "google" to "Google", "baidu" to "百度", "duckduckgo" to "DuckDuckGo", "custom" to "自定义")
        val group = RadioGroup(this)
        engines.forEachIndexed { i, (k, name) ->
            group.addView(RadioButton(this).apply { id = 1000 + i; text = name; isChecked = store.engine == k })
        }
        l.addView(group)
        val custom = field("自定义模板（用 %s 代表关键词）", store.customTemplate)

        header("广告拦截")
        @Suppress("DEPRECATION")
        val sw = Switch(this).apply { text = "启用广告拦截"; isChecked = store.adBlock }
        l.addView(sw)
        val status = TextView(this).apply { text = "当前规则数：${AdBlocker.count()}" }
        l.addView(status)
        val listUrl = field("EasyList 订阅地址", store.easyListUrl)
        l.addView(Button(this).apply {
            text = "导入 / 更新 EasyList"
            setOnClickListener {
                isEnabled = false; status.text = "下载中…"
                lifecycleScope.launch {
                    status.text = try {
                        val n = withContext(Dispatchers.IO) { AdBlocker.importFromUrl(applicationContext, listUrl.text.toString().trim()) }
                        store.easyListUrl = listUrl.text.toString().trim()
                        "已导入 $n 个域名，当前规则数：${AdBlocker.count()}"
                    } catch (e: Exception) { "导入失败：${e.message}" }
                    isEnabled = true
                }
            }
        })

        header("AI（OpenAI 兼容接口）")
        val base = field("Base URL", store.aiBase)
        val model = field("模型", store.aiModel)
        val key = field("API Key（仅保存在本机）", store.aiKey, true)

        l.addView(Button(this).apply {
            text = "保存"
            setOnClickListener {
                val idx = group.checkedRadioButtonId - 1000
                if (idx in engines.indices) store.engine = engines[idx].first
                store.customTemplate = custom.text.toString().trim()
                store.adBlock = sw.isChecked
                store.aiBase = base.text.toString().trim()
                store.aiModel = model.text.toString().trim()
                store.aiKey = key.text.toString().trim()
                Toast.makeText(this@SettingsActivity, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(l) })
    }
}
