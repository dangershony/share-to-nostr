package com.sharetonostr.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the error-classification helpers in [VideoDownloader.Companion].
 *
 * These helpers drive the 6-strategy fallback loop that recovers from the
 * "'<' not supported between instances of 'int' and 'str'" error reported in
 * https://github.com/dangershony/share-to-nostr/issues/24 when downloading
 * YouTube Shorts (e.g. https://youtube.com/shorts/nlF9i_t59Mo).
 *
 * Each test uses the exact error message text seen in real yt-dlp output to
 * ensure classification is correct.
 */
class VideoDownloaderErrorClassifierTest {

    // ─── isFormatSortError ────────────────────────────────────────────────────

    @Test
    fun `isFormatSortError matches exact message from issue 24`() {
        // This is the literal Python TypeError raised by yt-dlp when format metadata
        // has mixed int/str types — exactly what surfaced in issue #24.
        val e = RuntimeException("ERROR: '<' not supported between instances of 'int' and 'str'")
        assertTrue(VideoDownloader.isFormatSortError(e))
    }

    @Test
    fun `isFormatSortError matches when message contains substring`() {
        val e = RuntimeException(
            "Some preamble not supported between instances of 'int' and 'str' some suffix"
        )
        assertTrue(VideoDownloader.isFormatSortError(e))
    }

    @Test
    fun `isFormatSortError returns false for network error`() {
        val e = RuntimeException("No address associated with hostname")
        assertFalse(VideoDownloader.isFormatSortError(e))
    }

    @Test
    fun `isFormatSortError returns false for outdated yt-dlp error`() {
        val e = RuntimeException("yt-dlp is older than 2 weeks")
        assertFalse(VideoDownloader.isFormatSortError(e))
    }

    @Test
    fun `isFormatSortError returns false for generic exception`() {
        val e = RuntimeException("Unexpected error occurred")
        assertFalse(VideoDownloader.isFormatSortError(e))
    }

    @Test
    fun `isFormatSortError returns false for null message`() {
        val e = RuntimeException() // message is null
        assertFalse(VideoDownloader.isFormatSortError(e))
    }

    // ─── isNetworkError ───────────────────────────────────────────────────────

    @Test
    fun `isNetworkError matches no address associated with hostname`() {
        val e = RuntimeException("No address associated with hostname youtube.com")
        assertTrue(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError matches Network is unreachable`() {
        val e = RuntimeException("Network is unreachable")
        assertTrue(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError matches Unable to connect`() {
        val e = RuntimeException("Unable to connect to server")
        assertTrue(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError matches Connection refused`() {
        val e = RuntimeException("Connection refused")
        assertTrue(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError matches Connection timed out`() {
        val e = RuntimeException("Connection timed out after 30s")
        assertTrue(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError matches Failed to establish a new connection`() {
        val e = RuntimeException("Failed to establish a new connection: [Errno -2]")
        assertTrue(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError is case insensitive`() {
        val e = RuntimeException("NETWORK IS UNREACHABLE")
        assertTrue(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError returns false for format sort error`() {
        val e = RuntimeException("'<' not supported between instances of 'int' and 'str'")
        assertFalse(VideoDownloader.isNetworkError(e))
    }

    @Test
    fun `isNetworkError returns false for null message`() {
        val e = RuntimeException()
        assertFalse(VideoDownloader.isNetworkError(e))
    }

    // ─── isOutdatedYtDlpError ─────────────────────────────────────────────────

    @Test
    fun `isOutdatedYtDlpError matches yt-dlp older than message`() {
        val e = RuntimeException("yt-dlp is older than 2 weeks, update recommended")
        assertTrue(VideoDownloader.isOutdatedYtDlpError(e))
    }

    @Test
    fun `isOutdatedYtDlpError matches when message contains is older than`() {
        val e = RuntimeException("ERROR: The version you are running is older than the latest release")
        assertTrue(VideoDownloader.isOutdatedYtDlpError(e))
    }

    @Test
    fun `isOutdatedYtDlpError returns false for format sort error`() {
        val e = RuntimeException("'<' not supported between instances of 'int' and 'str'")
        assertFalse(VideoDownloader.isOutdatedYtDlpError(e))
    }

    @Test
    fun `isOutdatedYtDlpError returns false for network error`() {
        val e = RuntimeException("No address associated with hostname")
        assertFalse(VideoDownloader.isOutdatedYtDlpError(e))
    }

    @Test
    fun `isOutdatedYtDlpError returns false for null message`() {
        val e = RuntimeException()
        assertFalse(VideoDownloader.isOutdatedYtDlpError(e))
    }

    // ─── mutual exclusivity (each error maps to exactly one classifier) ───────

    @Test
    fun `format sort error is not classified as network error or outdated binary`() {
        val e = RuntimeException("'<' not supported between instances of 'int' and 'str'")
        assertTrue(VideoDownloader.isFormatSortError(e))
        assertFalse(VideoDownloader.isNetworkError(e))
        assertFalse(VideoDownloader.isOutdatedYtDlpError(e))
    }

    @Test
    fun `network error is not classified as format sort or outdated binary`() {
        val e = RuntimeException("No address associated with hostname youtube.com")
        assertFalse(VideoDownloader.isFormatSortError(e))
        assertTrue(VideoDownloader.isNetworkError(e))
        assertFalse(VideoDownloader.isOutdatedYtDlpError(e))
    }

    @Test
    fun `outdated binary error is not classified as format sort or network error`() {
        val e = RuntimeException("yt-dlp is older than 2 weeks")
        assertFalse(VideoDownloader.isFormatSortError(e))
        assertFalse(VideoDownloader.isNetworkError(e))
        assertTrue(VideoDownloader.isOutdatedYtDlpError(e))
    }
}
