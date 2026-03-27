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
            duration = info.duration.toLong(),
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

        fun buildRequest(formatString: String) = YoutubeDLRequest(url).apply {
            addOption("-f", formatString)
            addOption("--merge-output-format", "mp4")
            addOption("-o", "${outputDir.absolutePath}/%(id)s.%(ext)s")
            addOption("--no-playlist")
            // Limit to 5 minutes max to avoid accidentally downloading full movies
            addOption("--match-filter", "duration < 600 | !duration")
        }

        // Preferred format: best video + best audio merged, capped at maxResolution.
        val preferredFormat = "bestvideo[height<=$maxResolution]+bestaudio/best[height<=$maxResolution]/best"
        // Fallback format: avoids the bestvideo+bestaudio merge which can trigger a yt-dlp
        // format-sort bug ("'<' not supported between instances of 'int' and 'str'") on
        // certain sources (e.g. YouTube Shorts with HLS/M3U8 formats).
        val fallbackFormat = "best[height<=$maxResolution]/best"

        Log.d(TAG, "Starting download: $url")
        fun executeDownload(req: YoutubeDLRequest): File {
            YoutubeDL.getInstance().execute(req) { progress, eta, line ->
                Log.d(TAG, "Download progress: $progress% ETA: ${eta}s [$line]")
                onProgress(progress, eta)
            }
            return outputDir.listFiles()?.firstOrNull()
                ?: throw VideoDownloadException("Download completed but no file found")
        }

        val downloaded = try {
            executeDownload(buildRequest(preferredFormat))
        } catch (e: Exception) {
            if (isOutdatedYtDlpError(e)) {
                Log.w(TAG, "Download failed due to outdated yt-dlp; updating and retrying…")
                // updateYoutubeDL is a blocking network call; running inside withContext(IO) is intentional.
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                // Clear any partial output before retry
                outputDir.listFiles()?.forEach { it.delete() }
                executeDownload(buildRequest(preferredFormat))
            } else if (isFormatSortError(e)) {
                Log.w(TAG, "Format sort error; retrying with simpler format selection…")
                // Clear any partial output before retry
                outputDir.listFiles()?.forEach { it.delete() }
                try {
                    executeDownload(buildRequest(fallbackFormat))
                } catch (e2: Exception) {
                    if (isFormatSortError(e2)) {
                        // Both formats trigger the sort bug — update yt-dlp and retry with
                        // the fallback format (preferred was already tried and failed).
                        // If the fallback still fails, try a plain "best" with no resolution
                        // filter as a last resort.
                        Log.w(TAG, "Format sort error on fallback too; updating yt-dlp and retrying…")
                        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                        outputDir.listFiles()?.forEach { it.delete() }
                        try {
                            executeDownload(buildRequest(fallbackFormat))
                        } catch (e3: Exception) {
                            if (isFormatSortError(e3)) {
                                Log.w(TAG, "Format sort error after update; retrying with plain best…")
                                outputDir.listFiles()?.forEach { it.delete() }
                                executeDownload(buildRequest("best"))
                            } else {
                                throw e3
                            }
                        }
                    } else {
                        throw e2
                    }
                }
            } else {
                throw e
            }
        }
        Log.i(TAG, "Downloaded: ${downloaded.name} (${downloaded.length()} bytes)")
        downloaded
    }

    /**
     * Returns true when the exception indicates the bundled yt-dlp binary is too old.
     * In that case the download should be retried after an update.
     */
    private fun isOutdatedYtDlpError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("is older than")
    }

    /**
     * Returns true when the exception is a yt-dlp format-sort TypeError
     * ("'<' not supported between instances of 'int' and 'str'").
     * This can occur with certain sources (e.g. YouTube Shorts / HLS streams) when
     * yt-dlp tries to compare mixed-type format fields.  The recovery strategy is to
     * retry with progressively simpler format strings, updating yt-dlp in between when
     * both the preferred and fallback strings have already failed.
     */
    private fun isFormatSortError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("not supported between instances of 'int' and 'str'")
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
