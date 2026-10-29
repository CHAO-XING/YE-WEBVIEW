package com.ye.webview

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TtsPlayer(
    private val context: Context,
    private val client: TtsClient,
) {

    fun interface Listener {
        fun onState(playing: Boolean, status: String?)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speakMutex = Mutex()
    private val stopped = AtomicBoolean(true)
    private val playing = AtomicBoolean(false)
    private val currentPlayer = AtomicReference<MediaPlayer?>(null)
    private val currentDone = AtomicReference<CompletableDeferred<Unit>?>(null)

    @Volatile
    private var listener: Listener? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun isPlaying(): Boolean = playing.get()

    suspend fun speak(text: String) {
        if (text.isBlank()) return

        speakMutex.withLock {
            stopped.set(false)
            setState(true, "准备中")

            try {
                val chunks = client.chunkText(text)
                if (chunks.isEmpty()) {
                    setState(false, null)
                    return
                }

                for ((index, chunk) in chunks.withIndex()) {
                    if (stopped.get()) break

                    setState(true, "合成中 ${index + 1}/${chunks.size}")
                    val audio = client.synthesize(chunk)
                    if (stopped.get()) break

                    val file = writeTemp(audio)
                    try {
                        setState(true, "播放中 ${index + 1}/${chunks.size}")
                        playFile(file)
                    } finally {
                        file.delete()
                    }
                }

                if (!stopped.get()) setState(false, null)
            } catch (e: CancellationException) {
                halt()
                setState(false, null)
                throw e
            } catch (e: Exception) {
                val interrupted = stopped.get()
                halt()
                if (!interrupted) setState(false, e.message ?: "播放失败")
            }
        }
    }

    fun stop() {
        halt()
        setState(false, null)
    }

    fun release() {
        halt()
        playing.set(false)
        listener = null
    }

    private suspend fun writeTemp(bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = File.createTempFile("tts_", ".mp3", context.cacheDir)
        file.writeBytes(bytes)
        file
    }

    private suspend fun playFile(file: File) = withContext(Dispatchers.IO) {
        val player = MediaPlayer()
        val done = CompletableDeferred<Unit>()

        currentPlayer.set(player)
        currentDone.set(done)
        if (stopped.get()) done.complete(Unit)

        try {
            player.setOnCompletionListener { done.complete(Unit) }
            player.setOnErrorListener { _, what, extra ->
                done.completeExceptionally(RuntimeException("MediaPlayer error $what/$extra"))
                true
            }

            player.setDataSource(file.absolutePath)
            player.prepare()

            if (!stopped.get() && !done.isCompleted) {
                runCatching { player.start() }
                    .onFailure { done.completeExceptionally(it) }
            }

            done.await()
        } finally {
            currentDone.compareAndSet(done, null)
            currentPlayer.compareAndSet(player, null)
            runCatching { player.release() }
        }
    }

    private fun halt(): Boolean {
        val wasRunning = !stopped.getAndSet(true)
        releaseCurrent()
        currentDone.getAndSet(null)?.complete(Unit)
        return wasRunning
    }

    private fun releaseCurrent() {
        val player = currentPlayer.getAndSet(null) ?: return
        runCatching { player.setOnCompletionListener(null) }
        runCatching { player.setOnErrorListener(null) }
        runCatching { player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    private fun setState(isPlaying: Boolean, status: String?) {
        playing.set(isPlaying)
        val target = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target.onState(isPlaying, status)
        } else {
            mainHandler.post { target.onState(isPlaying, status) }
        }
    }
}
