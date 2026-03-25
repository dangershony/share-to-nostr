package com.sharetonostr

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ShareToNostrApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            var lastException: Exception? = null
            for (attempt in 1..MAX_INIT_RETRIES) {
                try {
                    Log.i(TAG, "Initializing yt-dlp (attempt $attempt/$MAX_INIT_RETRIES)")
                    YoutubeDL.getInstance().init(this@ShareToNostrApp)
                    FFmpeg.getInstance().init(this@ShareToNostrApp)
                    Log.i(TAG, "yt-dlp initialized successfully")
                    ytDlpReady.complete(true)
                    return@launch
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "yt-dlp init attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}", e)
                    if (attempt < MAX_INIT_RETRIES) {
                        delay(RETRY_DELAY_MS * attempt)
                    }
                }
            }
            Log.e(TAG, "yt-dlp initialization failed after $MAX_INIT_RETRIES attempts", lastException)
            ytDlpReady.complete(false)
        }
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

    companion object {
        private const val TAG = "ShareToNostr"
        private const val MAX_INIT_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        lateinit var instance: ShareToNostrApp
            private set
    }
}

/** Non-suspending peek at a CompletableDeferred that's already completed. */
private fun <T> CompletableDeferred<T>.await_safe(): T? {
    return if (isCompleted) getCompleted() else null
}
