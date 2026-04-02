package com.sharetonostr

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ShareToNostrApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ytDlpMutex = Mutex()

    /** Completes when yt-dlp is ready (true = success, false = failed). */
    @Volatile
    var ytDlpReady = CompletableDeferred<Boolean>()
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        initYoutubeDL()
    }

    private fun initYoutubeDL() {
        applicationScope.launch(Dispatchers.IO) {
            val initialized = withYtDlpLock { initializeYoutubeDLLocked() }
            ytDlpReady.complete(initialized)
            if (initialized) {
                autoUpdateYtDlp()
            }
        }
    }

    private suspend fun initializeYoutubeDLLocked(): Boolean {
        var lastException: Exception? = null
        for (attempt in 1..MAX_INIT_RETRIES) {
            try {
                Log.i(TAG, "Initializing yt-dlp (attempt $attempt/$MAX_INIT_RETRIES)")
                YoutubeDL.getInstance().init(this@ShareToNostrApp)
                FFmpeg.getInstance().init(this@ShareToNostrApp)
                Log.i(TAG, "yt-dlp initialized successfully")
                return true
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "yt-dlp init attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}", e)
                if (attempt < MAX_INIT_RETRIES) {
                    delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        Log.e(TAG, "yt-dlp initialization failed after $MAX_INIT_RETRIES attempts", lastException)
        return false
    }

    /**
     * Retry yt-dlp initialization. Call this if the initial init failed
     * and the user wants to try again without restarting the app.
     */
    fun retryInitYoutubeDL() {
        val current = ytDlpReady
        if (!current.isCompleted || current.await_safe() == true) {
            // Already initializing or already succeeded — nothing to do
            return
        }
        Log.i(TAG, "Retrying yt-dlp initialization")
        ytDlpReady = CompletableDeferred()
        initYoutubeDL()
    }

    suspend fun repairYoutubeDL(): Boolean = withYtDlpLock {
        Log.w(TAG, "Repairing yt-dlp installation")
        deleteRecursively(File(noBackupFilesDir, "youtubedl-android"))
        ytDlpReady = CompletableDeferred()
        val initialized = initializeYoutubeDLLocked()
        ytDlpReady.complete(initialized)
        initialized
    }

    suspend fun <T> withYtDlpLock(block: suspend () -> T): T {
        return ytDlpMutex.withLock { block() }
    }

    companion object {
        private const val TAG = "ShareToNostr"
        private const val MAX_INIT_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        lateinit var instance: ShareToNostrApp
            private set
    }

    /**
     * Update yt-dlp to the latest nightly build in a background coroutine.
     * Failures are non-fatal; they are logged but do not affect the ready state.
     */
    private fun autoUpdateYtDlp() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val status = withYtDlpLock {
                    Log.i(TAG, "Checking for yt-dlp updates in background...")
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@ShareToNostrApp,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                }
                Log.i(TAG, "yt-dlp background update: $status")
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp background update failed (non-critical): ${e.message}")
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        file.listFiles()?.forEach { deleteRecursively(it) }
        if (!file.delete()) {
            Log.w(TAG, "Failed to delete ${file.absolutePath} during yt-dlp repair")
        }
    }
}

/** Non-suspending peek at a CompletableDeferred that's already completed. */
private fun <T> CompletableDeferred<T>.await_safe(): T? {
    return if (isCompleted) getCompleted() else null
}
