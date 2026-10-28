package com.ye.webview

import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.json.JSONArray

class AiSheet(private val act: MainActivity) {
    private val ai = AiClient(act.store)
    private val messages = mutableListOf<Pair<String, String>>()

    companion object {
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
  var t=txt(c);
  return JSON.stringify({title:document.title,url:location.href,text:t.substring(0,12000)});
})()
"""
    }

    fun show() {
        val d = BottomSheetDialog(act)
        val pad = (16 * act.resources.displayMetrics.density).toInt()
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val out = TextView(act).apply { setTextIsSelectable(true); text = "正在提取页面正文…" }
        val scroll = ScrollView(act).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (act.resources.displayMetrics.heightPixels * 0.5).toInt())
            addView(out)
        }
        val input = EditText(act).apply { hint = "继续提问…" }
        val send = Button(act).apply { text = "发送" }
        val row = LinearLayout(act).apply {
            addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(send)
        }
        root.addView(TextView(act).apply { text = "AI 摘要"; textSize = 18f })
        root.addView(scroll); root.addView(row)
        d.setContentView(root)
        d.show()

        val transcript = StringBuilder()
        fun ask(q: String?) {
            if (q != null) { messages.add("user" to q); transcript.append("\n\n🙋 ").append(q) }
            out.text = transcript.toString() + "\n\n…思考中"
            send.isEnabled = false
            act.lifecycleScope.launch {
                try {
                    val a = ai.chat(messages)
                    messages.add("assistant" to a)
                    transcript.append("\n\n🤖 ").append(a)
                } catch (e: Exception) {
                    transcript.append("\n\n⚠️ ").append(e.message)
                }
                out.text = transcript.toString().trim()
                send.isEnabled = true
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
        send.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isNotEmpty() && messages.isNotEmpty()) { input.setText(""); ask(q) }
        }

        val web = act.currentWeb
        if (web == null) { out.text = "当前是主页，请先打开一个网页。"; return }
        web.evaluateJavascript(EXTRACT_JS) { raw ->
            try {
                // evaluateJavascript returns a JSON-encoded string literal
                val json = org.json.JSONObject(JSONArray("[$raw]").getString(0))
                val text = json.optString("text")
                if (text.isBlank()) { out.text = "未能提取到正文。"; return@evaluateJavascript }
                messages.add("system" to "你是网页阅读助手。根据用户提供的网页正文，用中文回答。")
                messages.add("user" to "网页标题：${json.optString("title")}\n网址：${json.optString("url")}\n\n正文：\n$text\n\n请用中文给出简洁的要点摘要。")
                transcript.append("📄 ").append(json.optString("title"))
                ask(null)
            } catch (e: Exception) {
                out.text = "提取失败：${e.message}"
            }
        }
    }
}
