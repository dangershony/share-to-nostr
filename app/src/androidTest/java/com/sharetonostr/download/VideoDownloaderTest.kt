package com.sharetonostr.download

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoDownloaderTest {
    private lateinit var downloader: VideoDownloader
    private lateinit var context: android.content.Context

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<android.content.Context>()
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        downloader = VideoDownloader(context)
    }

    @Test
    fun getVideoInfo_returnsMetadata() = runBlocking {
        val info = downloader.getVideoInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertNotNull(info)
        assertTrue(info.title.isNotBlank())
        assertTrue(info.duration >= 0)
    }

    @Test
    fun download_createsNonEmptyMp4File() = runBlocking {
        val file = downloader.download(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            maxResolution = "360"
        ) { progress, eta ->
            println("VideoDownloaderTest progress=$progress eta=$eta")
        }

        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertTrue(file.extension.equals("mp4", ignoreCase = true))
    }

    @Test
    fun download_shortVideo_integrationTest() = runBlocking {
        // Test with a short YouTube video (under 1 minute)
        val shortVideoUrl = "https://www.youtube.com/watch?v=jNQXAC9IVRw" // 56 second video
        
        val file = downloader.download(
            url = shortVideoUrl,
            maxResolution = "360"
        ) { progress, eta ->
            println("Short video download progress=$progress eta=$eta")
        }

        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertTrue(file.extension.equals("mp4", ignoreCase = true))
        assertTrue(file.length() < 10 * 1024 * 1024) // Should be less than 10MB for a short video
    }

    @Test
    fun download_withDifferentResolutions() = runBlocking {
        val testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        
        // Test with different resolution settings
        val resolutions = listOf("720", "480", "360")
        
        for (resolution in resolutions) {
            val file = downloader.download(
                url = testUrl,
                maxResolution = resolution
            )
            
            assertTrue("File should exist for resolution $resolution", file.exists())
            assertTrue("File should not be empty for resolution $resolution", file.length() > 0)
            assertTrue("File should be MP4 for resolution $resolution", file.extension.equals("mp4", ignoreCase = true))
        }
    }

    @Test
    fun download_progressCallback_receivesUpdates() = runBlocking {
        val testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        var progressReceived = false
        var etaReceived = false
        
        val file = downloader.download(
            url = testUrl,
            maxResolution = "360"
        ) { progress, eta ->
            if (progress > 0) progressReceived = true
            if (eta >= 0) etaReceived = true
            println("Progress callback: progress=$progress eta=$eta")
        }

        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertTrue("Progress callback should receive progress updates", progressReceived)
        assertTrue("Progress callback should receive ETA updates", etaReceived)
    }

    @Test
    fun download_outputDirectory_cleanup() = runBlocking {
        val testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val outputDir = File(context.cacheDir, "downloads")
        
        // Ensure directory is empty before test
        outputDir.listFiles()?.forEach { it.delete() }
        
        // First download
        val file1 = downloader.download(url = testUrl, maxResolution = "360")
        val filesAfterFirstDownload = outputDir.listFiles()?.size ?: 0
        
        assertTrue("Should have exactly one file after download", filesAfterFirstDownload == 1)
        
        // Second download should clean up previous files
        val file2 = downloader.download(url = testUrl, maxResolution = "360")
        val filesAfterSecondDownload = outputDir.listFiles()?.size ?: 0
        
        assertTrue("Should have exactly one file after second download", filesAfterSecondDownload == 1)
        assertTrue("Second download should create a new file", !file1.name.equals(file2.name))
    }

    @Test
    fun getVideoInfo_multipleUrls() = runBlocking {
        val testUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            "https://www.youtube.com/watch?v=9bZkp7q19f0"
        )
        
        for (url in testUrls) {
            val info = downloader.getVideoInfo(url)
            
            assertNotNull("Video info should not be null for $url", info)
            assertTrue("Title should not be blank for $url", info.title.isNotBlank())
            assertTrue("Duration should be valid for $url", info.duration >= 0)
            assertTrue("URL should match input for $url", info.url == url)
        }
    }

    @Test(expected = VideoDownloadException::class)
    fun download_invalidUrl_throwsException() {
        runBlocking {
            // This should throw an exception for invalid URLs
            downloader.download(url = "https://www.youtube.com/watch?v=INVALID_URL_12345")
        }
    }
}
