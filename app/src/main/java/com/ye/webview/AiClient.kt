package com.ye.webview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiClient(private val store: Store) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** messages: list of (role, content) */
    suspend fun chat(messages: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        if (store.aiKey.isBlank()) throw IllegalStateException("请先在设置中填写 AI API Key")
        val base = store.aiBase.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base
        else if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val body = JSONObject().put("model", store.aiModel).put("messages", arr).put("stream", false).toString()
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${store.aiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val s = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: ${s.take(300)}")
            JSONObject(s).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }
}
