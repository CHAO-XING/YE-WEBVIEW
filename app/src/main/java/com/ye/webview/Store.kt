package com.ye.webview

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Entry(val title: String, val url: String, val time: Long = System.currentTimeMillis())

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ye", Context.MODE_PRIVATE)

    var engine: String
        get() = sp.getString("engine", "bing")!!
        set(v) = sp.edit().putString("engine", v).apply()
    var customTemplate: String
        get() = sp.getString("custom", "https://www.bing.com/search?q=%s")!!
        set(v) = sp.edit().putString("custom", v).apply()
    var adBlock: Boolean
        get() = sp.getBoolean("adblock", true)
        set(v) = sp.edit().putBoolean("adblock", v).apply()
    var easyListUrl: String
        get() = sp.getString("easylist", "https://easylist.to/easylist/easylist.txt")!!
        set(v) = sp.edit().putString("easylist", v).apply()
    var aiBase: String
        get() = sp.getString("ai_base", "https://api.deepseek.com")!!
        set(v) = sp.edit().putString("ai_base", v).apply()
    var aiModel: String
        get() = sp.getString("ai_model", "deepseek-chat")!!
        set(v) = sp.edit().putString("ai_model", v).apply()
    var aiKey: String
        get() = sp.getString("ai_key", "")!!
        set(v) = sp.edit().putString("ai_key", v).apply()

    fun searchUrl(q: String): String {
        val e = java.net.URLEncoder.encode(q, "UTF-8")
        val t = when (engine) {
            "google" -> "https://www.google.com/search?q=%s"
            "baidu" -> "https://www.baidu.com/s?wd=%s"
            "duckduckgo" -> "https://duckduckgo.com/?q=%s"
            "custom" -> customTemplate
            else -> "https://www.bing.com/search?q=%s"
        }
        return t.replace("%s", e)
    }

    fun toUrl(input: String): String {
        val s = input.trim()
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("about:") || s.startsWith("file:")) return s
        if (!s.contains(' ') && s.contains('.') && !s.endsWith('.')) return "https://$s"
        return searchUrl(s)
    }

    private fun load(key: String): MutableList<Entry> {
        val arr = JSONArray(sp.getString(key, "[]"))
        return MutableList(arr.length()) {
            val o = arr.getJSONObject(it)
            Entry(o.optString("t"), o.optString("u"), o.optLong("d"))
        }
    }

    private fun save(key: String, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url).put("d", it.time)) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    fun bookmarks() = load("bookmarks")
    fun addBookmark(e: Entry) { val l = bookmarks(); l.removeAll { it.url == e.url }; l.add(0, e); save("bookmarks", l) }
    fun removeBookmark(url: String) { val l = bookmarks(); l.removeAll { it.url == url }; save("bookmarks", l) }
    fun history() = load("history")
    fun addHistory(e: Entry) {
        if (e.url.isBlank() || e.url.startsWith("about:")) return
        val l = history(); l.removeAll { it.url == e.url }; l.add(0, e)
        save("history", l.take(500))
    }
    fun clearHistory() = save("history", emptyList())
}
