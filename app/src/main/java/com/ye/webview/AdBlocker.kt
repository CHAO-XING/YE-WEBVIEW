package com.ye.webview

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object AdBlocker {
    @Volatile private var hosts: Set<String> = emptySet()

    fun init(ctx: Context) {
        val set = HashSet<String>()
        ctx.assets.open("hosts.txt").bufferedReader().forEachLine { addLine(it, set) }
        val f = File(ctx.filesDir, "easylist_hosts.txt")
        if (f.exists()) f.forEachLine { addLine(it, set) }
        hosts = set
    }

    private fun addLine(l: String, set: MutableSet<String>) {
        val s = l.trim().lowercase()
        if (s.isNotEmpty() && !s.startsWith("#")) set.add(s)
    }

    fun count() = hosts.size

    /** Parse EasyList: take only `||domain^` rules without path/options (or with simple options). */
    fun parseEasyList(text: String): Set<String> {
        val out = HashSet<String>()
        val re = Regex("^\\|\\|([a-z0-9.-]+)\\^(\\$.*)?$")
        text.lineSequence().forEach { raw ->
            val l = raw.trim().lowercase()
            if (l.startsWith("!") || l.startsWith("@@")) return@forEach
            val m = re.matchEntire(l) ?: return@forEach
            val opts = m.groupValues[2]
            if (opts.contains("domain=")) return@forEach
            out.add(m.groupValues[1].trimEnd('.'))
        }
        return out
    }

    /** Blocking call; run on IO dispatcher. Returns number of hosts imported. */
    fun importFromUrl(ctx: Context, url: String): Int {
        val client = OkHttpClient()
        val body = client.newCall(Request.Builder().url(url).build()).execute().use {
            if (!it.isSuccessful) throw RuntimeException("HTTP ${it.code}")
            it.body!!.string()
        }
        val set = parseEasyList(body)
        File(ctx.filesDir, "easylist_hosts.txt").writeText(set.joinToString("\n"))
        init(ctx)
        return set.size
    }

    fun isBlocked(host: String?): Boolean {
        if (host == null) return false
        var h = host.lowercase()
        val hs = hosts
        while (true) {
            if (hs.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0 || h.indexOf('.', i + 1) < 0) return false
            h = h.substring(i + 1)
        }
    }
}
