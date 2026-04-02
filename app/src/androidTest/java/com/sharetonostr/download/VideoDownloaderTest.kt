package com.sharetonostr.download

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoDownloaderTest {
    private lateinit var downloader: VideoDownloader

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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
}
