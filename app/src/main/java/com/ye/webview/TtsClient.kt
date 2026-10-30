package com.ye.webview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TtsClient(private val store: Store) {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String, voice: String? = null): ByteArray = withContext(Dispatchers.IO) {
        if (text.isBlank()) throw RuntimeException("TTS text is empty")

        val base = store.ttsBase.trim().trimEnd('/')
        if (base.isEmpty()) throw RuntimeException("TTS base url is empty")
        if (store.ttsKey.isBlank()) throw IllegalStateException("请先在设置中填写 TTS Token")

        val payload = JSONObject()
            .put("text", text)
            .put("voice", voice?.takeIf { it.isNotBlank() } ?: resolveVoice(text))
            .put("rate", "+0%")
            .put("volume", "+0%")
            .put("pitch", "+0Hz")
            .toString()
            .toRequestBody(jsonType)

        val request = Request.Builder()
            .url("$base/tts")
            .post(payload)
            .apply {
                val key = store.ttsKey.trim()
                if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            }
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.trim()?.take(160).orEmpty()
                throw RuntimeException(
                    buildString {
                        append("TTS HTTP ").append(response.code)
                        if (detail.isNotEmpty()) append(": ").append(detail)
                    }
                )
            }
            val bytes = response.body?.bytes()
            if (bytes == null || bytes.isEmpty()) {
                throw RuntimeException("TTS HTTP ${response.code}: empty audio body")
            }
            bytes
        }
    }

    fun resolveVoice(text: String): String {
        if (!store.ttsAutoVoice) return store.ttsVoice.ifBlank { DEFAULT_VOICE }
        return when (detectLanguage(text)) {
            "zh" -> store.ttsVoiceZh.ifBlank { DEFAULT_VOICE_ZH }
            "en" -> store.ttsVoiceEn.ifBlank { DEFAULT_VOICE_EN }
            else -> store.ttsVoice.ifBlank { DEFAULT_VOICE }
        }
    }

    fun detectLanguage(text: String): String {
        var cjk = 0
        var latin = 0
        for (c in text) {
            when {
                c in '\u4E00'..'\u9FFF' -> cjk++
                c in 'A'..'Z' || c in 'a'..'z' -> latin++
            }
        }
        if (cjk < 3 && latin < 3) return "unknown"
        if (cjk + latin < 3) return "unknown"
        return if (cjk >= latin) "zh" else "en"
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val base = store.ttsBase.trim().trimEnd('/')
        if (base.isEmpty()) return@withContext false
        runCatching {
            http.newCall(Request.Builder().url("$base/health").get().build())
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    fun chunkText(text: String, maxChars: Int = 1200): List<String> {
        val limit = maxChars.coerceAtLeast(80)
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return emptyList()

        val chunks = ArrayList<String>()
        val current = StringBuilder()

        for (sentence in splitSentences(normalized)) {
            if (sentence.isBlank()) continue

            if (current.isNotEmpty() && current.length + sentence.length > limit) {
                chunks += current.toString().trim()
                current.setLength(0)
            }

            var rest = sentence
            while (rest.length > limit) {
                val cut = breakIndex(rest, limit)
                val head = rest.substring(0, cut).trim()
                if (head.isNotEmpty()) chunks += head
                rest = rest.substring(cut)
            }

            if (rest.isNotBlank()) current.append(rest)
        }

        if (current.isNotBlank()) chunks += current.toString().trim()
        return mergeShort(chunks, limit)
    }

    fun splitUtterances(text: String, maxChars: Int = 400): List<String> {
        val limit = maxChars.coerceAtLeast(40)
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return emptyList()

        val result = ArrayList<String>()
        for (sentence in splitSentences(normalized)) {
            var rest = sentence.trim()
            if (rest.isEmpty()) continue
            while (rest.length > limit) {
                val cut = breakIndex(rest, limit)
                val head = rest.substring(0, cut).trim()
                if (head.isNotEmpty()) result += head
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) result += rest
        }
        return result
    }

    private fun splitSentences(text: String): List<String> {
        val result = ArrayList<String>()
        val buffer = StringBuilder()
        for (c in text) {
            buffer.append(c)
            if (c == '\n' || c in SENTENCE_ENDS) {
                result += buffer.toString()
                buffer.setLength(0)
            }
        }
        if (buffer.isNotBlank()) result += buffer.toString()
        return result
    }

    private fun breakIndex(text: String, limit: Int): Int {
        val floor = limit * 3 / 5
        for (i in limit downTo floor) {
            val c = text[i - 1]
            if (c == '\n' || c.isWhitespace() || c in BREAK_CHARS) return i
        }
        return limit
    }

    private fun mergeShort(chunks: List<String>, limit: Int): List<String> {
        if (chunks.size < 2) return chunks
        val merged = ArrayList<String>(chunks.size)
        for (chunk in chunks) {
            val last = merged.lastOrNull()
            if (last != null && last.length < MIN_CHUNK && last.length + chunk.length + 1 <= limit) {
                merged[merged.size - 1] = last + "\n" + chunk
            } else {
                merged += chunk
            }
        }
        return merged
    }

    private companion object {
        const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"
        const val DEFAULT_VOICE_ZH = "zh-CN-XiaoxiaoNeural"
        const val DEFAULT_VOICE_EN = "en-US-JennyNeural"
        const val MIN_CHUNK = 800
        const val BREAK_CHARS = "。！？!?；;，,、.…"
        const val SENTENCE_ENDS = "。！？!?；;."
    }
}
