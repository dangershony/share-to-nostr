package com.sharetonostr.download

import android.content.Context
import android.util.Log
import com.sharetonostr.ShareToNostrApp
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
        val info = try {
            YoutubeDL.getInstance().getInfo(url)
        } catch (e: Exception) {
            if (isCorruptYtDlpError(e) && repairYoutubeDL()) {
                Log.w(TAG, "yt-dlp metadata lookup failed due to corruption; repaired and retrying...")
                YoutubeDL.getInstance().getInfo(url)
            } else {
                throw e
            }
        }
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

        // Base request with options common to all download strategies.
        fun baseRequest() = YoutubeDLRequest(url).apply {
            addOption("--merge-output-format", "mp4")
            addOption("-o", "${outputDir.absolutePath}/%(id)s.%(ext)s")
            addOption("--no-playlist")
            // Limit to 5 minutes max to avoid accidentally downloading full movies
            addOption("--match-filter", "duration < 600 | !duration")
        }

        fun cleanOutputDir() = outputDir.listFiles()?.forEach { it.delete() }

        Log.d(TAG, "Starting download: $url")

        fun executeDownload(req: YoutubeDLRequest): File {
            YoutubeDL.getInstance().execute(req) { progress, eta, line ->
                Log.d(TAG, "Download progress: $progress% ETA: ${eta}s [$line]")
                onProgress(progress, eta)
            }
            val file = outputDir.listFiles()?.firstOrNull()
                ?: throw VideoDownloadException("Download completed but no file found")
            Log.i(TAG, "Downloaded: ${file.name} (${file.length()} bytes)")
            return file
        }

        // Preferred format: best video + best audio merged, capped at maxResolution.
        val preferredFormat = "bestvideo[height<=$maxResolution]+bestaudio/best[height<=$maxResolution]/best"
        // Fallback format: avoids the bestvideo+bestaudio merge which can trigger a yt-dlp
        // format-sort bug ("'<' not supported between instances of 'int' and 'str'") on
        // certain sources (e.g. YouTube Shorts with HLS/M3U8 formats).
        val fallbackFormat = "best[height<=$maxResolution]/best"

        // Strategies tried in order when a format-sort TypeError occurs. Each entry is a
        // human-readable label and a lambda that builds the YoutubeDLRequest.
        // Progressively strips format constraints to avoid mixed-type sort comparisons:
        //  1. preferred  – explicit best-video+best-audio merge with resolution cap
        //  2. fallback   – single-stream "best", avoids merge that can expose sort bug
        //  3. no format  – omit -f entirely, lets yt-dlp auto-select
        //  4. skip HLS   – drop HLS (m3u8) entries whose tbr/abr types can be mixed
        //  5. skip HLS+DASH – also drop DASH entries for the same reason (final resort)
        val strategies: List<Pair<String, () -> YoutubeDLRequest>> = listOf(
            "preferred format" to { baseRequest().apply { addOption("-f", preferredFormat) } },
            "fallback format" to { baseRequest().apply { addOption("-f", fallbackFormat) } },
            "no explicit format" to { baseRequest() },
            "skip HLS" to {
                baseRequest().apply { addOption("--extractor-args", "youtube:skip=hls") }
            },
            // DASH entries can also carry mixed-type tbr/abr fields. Skipping both HLS
            // and DASH removes all problematic entries from yt-dlp's sort list.
            // Non-YouTube extractors ignore the "youtube:" prefix, so this is safe for
            // other platforms too.
            "skip HLS+DASH" to {
                baseRequest().apply { addOption("--extractor-args", "youtube:skip=hls,dash") }
            },
            "youtube web client mp4" to {
                baseRequest().apply {
                    addOption("--extractor-args", "youtube:player_client=web")
                    addOption("-f", "18/best[ext=mp4]/best")
                }
            },
        )

        var ytDlpUpdated = false
        var lastFormatSortException: Exception? = null

        for ((index, strategyPair) in strategies.withIndex()) {
            val (label, buildReq) = strategyPair
            cleanOutputDir()
            try {
                return@withContext executeDownload(buildReq())
            } catch (e: Exception) {
                when {
                    isCorruptYtDlpError(e) -> {
                        if (!repairYoutubeDL()) throw e
                        cleanOutputDir()
                        return@withContext executeDownload(buildReq())
                    }
                    isOutdatedYtDlpError(e) && !ytDlpUpdated -> {
                        Log.w(TAG, "Outdated yt-dlp; updating and retrying $label…")
                        // updateYoutubeDL is a blocking network call; running inside withContext(IO) is intentional.
                        updateYoutubeDLLocked()
                        ytDlpUpdated = true
                        cleanOutputDir()
                        try {
                            return@withContext executeDownload(buildReq())
                        } catch (e2: Exception) {
                            if (isFormatSortError(e2)) {
                                Log.w(TAG, "Format sort error after yt-dlp update on $label; continuing…")
                                lastFormatSortException = e2
                            } else {
                                throw e2
                            }
                        }
                    }
                    isFormatSortError(e) -> {
                        Log.w(TAG, "Format sort error on $label; trying next strategy…")
                        lastFormatSortException = e
                        // After both preferred and fallback format strings have failed,
                        // update yt-dlp once — a stale binary can be the root cause.
                        if (!ytDlpUpdated && index == 1) {
                            Log.w(TAG, "Updating yt-dlp before continuing…")
                            updateYoutubeDLLocked()
                            ytDlpUpdated = true
                            cleanOutputDir()
                            try {
                                return@withContext executeDownload(buildReq())
                            } catch (e2: Exception) {
                                if (!isFormatSortError(e2)) throw e2
                                Log.w(TAG, "Still format sort error after update; continuing…")
                                lastFormatSortException = e2
                            }
                        }
                    }
                    isNetworkError(e) -> throw VideoDownloadException(
                        "Network error: Unable to reach the video host. Please check your internet connection and try again.",
                        e
                    )
                    else -> throw e
                }
            }
        }

        throw lastFormatSortException ?: VideoDownloadException("Download failed: all format selection strategies exhausted")
    }

    /**
     * Returns true when the exception is caused by a network or DNS failure
     * (e.g. "No address associated with hostname", "Network is unreachable").
     * These errors cannot be resolved by changing the format string or updating
     * yt-dlp, so they should surface a user-friendly connectivity message.
     */
    private fun isNetworkError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("No address associated with hostname", ignoreCase = true) ||
                msg.contains("Network is unreachable", ignoreCase = true) ||
                msg.contains("Unable to connect", ignoreCase = true) ||
                msg.contains("Connection refused", ignoreCase = true) ||
                msg.contains("Connection timed out", ignoreCase = true) ||
                msg.contains("Failed to establish a new connection", ignoreCase = true)
    }

    /**
     * Returns true when the exception indicates the bundled yt-dlp binary is too old.
     * In that case the download should be retried after an update.
     */
    private fun isOutdatedYtDlpError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("is older than")
    }

    private fun isCorruptYtDlpError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("bad local file header", ignoreCase = true) ||
                msg.contains("ZipImportError", ignoreCase = true) ||
                msg.contains("libandroid-support.so", ignoreCase = true)
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
        val status = updateYoutubeDLLocked()
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated successfully"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date"
            else -> "Update status: $status"
        }
    }

    private suspend fun updateYoutubeDLLocked(): YoutubeDL.UpdateStatus {
        val app = context.applicationContext as? ShareToNostrApp
        val status = if (app != null) {
            app.withYtDlpLock {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            }
        } else {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
        }
        return status ?: YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE
    }

    private suspend fun repairYoutubeDL(): Boolean {
        val app = context.applicationContext as? ShareToNostrApp ?: return false
        return app.repairYoutubeDL()
    }
}

class VideoDownloadException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
