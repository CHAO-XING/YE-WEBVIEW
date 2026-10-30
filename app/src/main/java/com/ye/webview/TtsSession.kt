package com.ye.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class TtsSession(
    context: Context,
    private val client: TtsClient,
) {

    data class State(
        val playing: Boolean,
        val source: String,
        val index: Int = 0,
        val total: Int = 0,
        val status: String? = null,
        val error: String? = null,
        val text: String? = null,
    )

    fun interface Listener {
        fun onState(state: State)
    }

    private class Run {
        @Volatile var cancelled = false
    }

    private val player = TtsPlayer(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var listener: Listener? = null

    @Volatile
    private var currentRun: Run? = null

    @Volatile
    private var job: Job? = null

    @Volatile
    private var lastSource = "选区"

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun speak(text: String, source: String = "选区") {
        val utterances = client.splitUtterances(text)
        if (utterances.isEmpty()) {
            emit(State(false, source))
            return
        }

        lastSource = source
        val run = Run()
        val previous = currentRun
        currentRun = run
        previous?.cancelled = true
        job?.cancel()
        player.stop()

        job = scope.launch {
            try {
                runSession(utterances, source, run)
            } catch (_: CancellationException) {
                // stopped or superseded
            } catch (e: Exception) {
                emit(run, State(false, source, 0, utterances.size, null, e.message ?: "朗读失败"))
            }
        }
    }

    fun stop() {
        currentRun?.cancelled = true
        currentRun = null
        job?.cancel()
        job = null
        player.stop()
        emit(State(false, lastSource))
    }

    fun release() {
        stop()
        listener = null
        scope.cancel()
    }

    private suspend fun runSession(utterances: List<String>, source: String, run: Run) {
        val total = utterances.size
        emit(run, State(true, source, 0, total, "准备中"))

        var lastError: String? = null
        coroutineScope {
            val cache = arrayOfNulls<Deferred<Result<ByteArray>>>(total)

            fun prefetch(index: Int) {
                if (index < 0 || index >= total || cache[index] != null || run.cancelled) return
                val text = utterances[index]
                cache[index] = async(Dispatchers.IO) {
                    runCatching { client.synthesize(text) }
                }
            }

            prefetch(0)
            prefetch(1)

            for (i in 0 until total) {
                if (run.cancelled) break

                val deferred = cache[i]
                cache[i] = null
                val result = deferred?.await()
                    ?: runCatching { client.synthesize(utterances[i]) }
                if (run.cancelled) break

                prefetch(i + 2)
                result.onSuccess { bytes ->
                    emit(run, State(true, source, i + 1, total, "播放中", null, utterances[i]))
                    try {
                        player.play(bytes) { run.cancelled }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e.message ?: "播放失败"
                        emit(run, State(true, source, i + 1, total, "第 ${i + 1} 句播放失败", lastError))
                    }
                }.onFailure { e ->
                    lastError = e.message ?: "合成失败"
                    emit(run, State(true, source, i + 1, total, "第 ${i + 1} 句合成失败", lastError))
                }
            }
        }

        if (!run.cancelled) {
            emit(State(false, source, total, total, null, lastError))
        }
    }

    private fun emit(run: Run, state: State) {
        if (run.cancelled) return
        emit(state)
    }

    private fun emit(state: State) {
        val target = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target.onState(state)
        } else {
            mainHandler.post { target.onState(state) }
        }
    }
}
