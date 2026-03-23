package com.sharetonostr.download

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps youtubedl-android to download videos from various platforms.
 */
class VideoDownloader(private val context: Context) {

    companion object {
        private const val TAG = "VideoDownloader"
    }

    data class VideoInfo(
        val title: String,
        val thumbnailUrl: String?,
        val duration: Long,
        val url: String,
        val uploaderName: String?,
        val description: String?
    )

    /**
     * Extract metadata about a video without downloading it.
     */
    suspend fun getVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching info for: $url")
        val info = YoutubeDL.getInstance().getInfo(url)
        VideoInfo(
            title = info.title ?: "Untitled",
            thumbnailUrl = info.thumbnail,
            duration = info.duration?.toLong() ?: 0,
            url = url,
            uploaderName = info.uploader,
            description = info.description
        )
    }

    /**
     * Download a video to a temporary directory.
     *
     * @param url The video URL to download
     * @param maxResolution Maximum video height (e.g. "1080", "720", "480")
     * @param onProgress Callback with (progress percentage 0-100, ETA in seconds)
     * @return The downloaded file
     */
    suspend fun download(
        url: String,
        maxResolution: String = "1080",
        onProgress: (Float, Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.cacheDir, "downloads").apply { mkdirs() }

        // Clean up any previous downloads
        outputDir.listFiles()?.forEach { it.delete() }

        val request = YoutubeDLRequest(url).apply {
            addOption("-f", "bestvideo[height<=$maxResolution]+bestaudio/best[height<=$maxResolution]/best")
            addOption("--merge-output-format", "mp4")
            addOption("-o", "${outputDir.absolutePath}/%(id)s.%(ext)s")
            addOption("--no-playlist")
            // Limit to 5 minutes max to avoid accidentally downloading full movies
            addOption("--match-filter", "duration < 600 | !duration")
        }

        Log.d(TAG, "Starting download: $url")
        YoutubeDL.getInstance().execute(request) { progress, eta ->
            Log.d(TAG, "Download progress: $progress% ETA: ${eta}s")
            onProgress(progress, eta)
        }

        val downloaded = outputDir.listFiles()?.firstOrNull()
            ?: throw VideoDownloadException("Download completed but no file found")

        Log.i(TAG, "Downloaded: ${downloaded.name} (${downloaded.length()} bytes)")
        downloaded
    }

    /**
     * Update yt-dlp to the latest version at runtime.
     */
    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Updating yt-dlp...")
        val status = YoutubeDL.getInstance().updateYoutubeDL(
            context,
            YoutubeDL.UpdateChannel.NIGHTLY
        )
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated successfully"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date"
            else -> "Update status: $status"
        }
    }
}

class VideoDownloadException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
