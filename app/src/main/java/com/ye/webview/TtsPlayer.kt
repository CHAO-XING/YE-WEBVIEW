package com.ye.webview

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class TtsPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val playMutex = Mutex()

    private class PlayToken {
        @Volatile var stopped = false
    }

    private val currentPlayer = AtomicReference<MediaPlayer?>(null)
    private val currentDone = AtomicReference<CompletableDeferred<Unit>?>(null)
    private val currentToken = AtomicReference<PlayToken?>(null)

    suspend fun play(bytes: ByteArray, isCancelled: () -> Boolean = { false }): Boolean =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext false

            playMutex.withLock {
                if (isCancelled()) return@withLock false
                val token = PlayToken()
                currentToken.set(token)
                val file = File.createTempFile("tts_", ".mp3", appContext.cacheDir)
                try {
                    file.writeBytes(bytes)
                    playFile(file, token, isCancelled)
                } finally {
                    currentToken.compareAndSet(token, null)
                    file.delete()
                }
            }
        }

    fun stop() {
        currentToken.get()?.stopped = true
        releaseCurrent()
        currentDone.getAndSet(null)?.complete(Unit)
    }

    fun release() {
        stop()
        currentToken.set(null)
    }

    fun isPlaying(): Boolean = currentPlayer.get() != null

    private suspend fun playFile(file: File, token: PlayToken, isCancelled: () -> Boolean): Boolean {
        val player = MediaPlayer()
        val done = CompletableDeferred<Unit>()

        currentPlayer.set(player)
        currentDone.set(done)
        if (token.stopped) done.complete(Unit)

        try {
            player.setOnCompletionListener { done.complete(Unit) }
            player.setOnErrorListener { _, what, extra ->
                done.completeExceptionally(RuntimeException("MediaPlayer error $what/$extra"))
                true
            }

            player.setDataSource(file.absolutePath)
            player.prepare()

            if (!token.stopped && !isCancelled() && !done.isCompleted) {
                runCatching { player.start() }
                    .onFailure { done.completeExceptionally(it) }
            }

            done.await()
            return !token.stopped && !isCancelled()
        } finally {
            currentDone.compareAndSet(done, null)
            currentPlayer.compareAndSet(player, null)
            runCatching { player.release() }
        }
    }

    private fun releaseCurrent() {
        val player = currentPlayer.getAndSet(null) ?: return
        runCatching { player.setOnCompletionListener(null) }
        runCatching { player.setOnErrorListener(null) }
        runCatching { player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }
}
