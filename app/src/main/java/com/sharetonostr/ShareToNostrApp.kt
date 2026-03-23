package com.sharetonostr

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShareToNostrApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        initYoutubeDL()
    }

    private fun initYoutubeDL() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@ShareToNostrApp)
                FFmpeg.getInstance().init(this@ShareToNostrApp)
                Log.i(TAG, "yt-dlp initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize yt-dlp", e)
            }
        }
    }

    companion object {
        private const val TAG = "ShareToNostr"
        lateinit var instance: ShareToNostrApp
            private set
    }
}
