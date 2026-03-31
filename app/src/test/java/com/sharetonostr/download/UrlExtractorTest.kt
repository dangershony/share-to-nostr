package com.sharetonostr.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UrlExtractor].
 *
 * Covers the specific YouTube Shorts URL from issue #24
 * (https://github.com/dangershony/share-to-nostr/issues/24) as well as general
 * platform URL extraction and platform-name detection.
 */
class UrlExtractorTest {

    // ─── YouTube Shorts (issue #24 reproduction) ────────────────────────────

    @Test
    fun `extractUrl returns YouTube Shorts URL from bare URL`() {
        val url = "https://youtube.com/shorts/nlF9i_t59Mo?si=YFt71QcebQYIn_-M"
        val result = UrlExtractor.extractUrl(url)
        assertNotNull("Expected a URL to be extracted", result)
        assertTrue("Extracted URL should contain the video ID", result!!.contains("nlF9i_t59Mo"))
    }

    @Test
    fun `extractUrl returns YouTube Shorts URL when embedded in share text`() {
        // YouTube app typically shares as "Title\nURL"
        val sharedText = "Stay in your lane 🤷‍♂️\nhttps://youtube.com/shorts/nlF9i_t59Mo?si=YFt71QcebQYIn_-M"
        val result = UrlExtractor.extractUrl(sharedText)
        assertNotNull("Expected a URL to be extracted from share text", result)
        assertTrue("Extracted URL should contain the video ID", result!!.contains("nlF9i_t59Mo"))
    }

    @Test
    fun `isShortFormVideo returns true for YouTube Shorts URL from issue 24`() {
        val url = "https://youtube.com/shorts/nlF9i_t59Mo?si=YFt71QcebQYIn_-M"
        assertTrue("YouTube Shorts URL should be identified as short-form", UrlExtractor.isShortFormVideo(url))
    }

    @Test
    fun `getPlatformName returns YouTube for Shorts URL from issue 24`() {
        val url = "https://youtube.com/shorts/nlF9i_t59Mo?si=YFt71QcebQYIn_-M"
        assertEquals("YouTube", UrlExtractor.getPlatformName(url))
    }

    // ─── General YouTube URLs ────────────────────────────────────────────────

    @Test
    fun `extractUrl handles youtu_be short links`() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val result = UrlExtractor.extractUrl(url)
        assertNotNull(result)
        assertTrue(result!!.contains("dQw4w9WgXcQ"))
    }

    @Test
    fun `extractUrl handles youtube_com watch URLs`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val result = UrlExtractor.extractUrl(url)
        assertNotNull(result)
        assertTrue(result!!.contains("dQw4w9WgXcQ"))
    }

    // ─── TikTok ─────────────────────────────────────────────────────────────

    @Test
    fun `extractUrl handles TikTok video URL`() {
        val url = "https://www.tiktok.com/@user/video/1234567890"
        val result = UrlExtractor.extractUrl(url)
        assertNotNull(result)
        assertTrue(result!!.contains("tiktok.com"))
    }

    @Test
    fun `isShortFormVideo returns true for TikTok URL`() {
        assertTrue(UrlExtractor.isShortFormVideo("https://www.tiktok.com/@user/video/1234567890"))
    }

    @Test
    fun `getPlatformName returns TikTok for tiktok URL`() {
        assertEquals("TikTok", UrlExtractor.getPlatformName("https://vm.tiktok.com/abc123"))
    }

    // ─── Instagram ──────────────────────────────────────────────────────────

    @Test
    fun `extractUrl handles Instagram reel URL`() {
        val url = "https://www.instagram.com/reel/CxYzAbCdEfG/"
        val result = UrlExtractor.extractUrl(url)
        assertNotNull(result)
        assertTrue(result!!.contains("instagram.com"))
    }

    @Test
    fun `isShortFormVideo returns true for Instagram reel`() {
        assertTrue(UrlExtractor.isShortFormVideo("https://www.instagram.com/reel/CxYzAbCdEfG/"))
    }

    // ─── X / Twitter ────────────────────────────────────────────────────────

    @Test
    fun `extractUrl handles X status URL`() {
        val url = "https://x.com/user/status/1234567890123456789"
        val result = UrlExtractor.extractUrl(url)
        assertNotNull(result)
        assertTrue(result!!.contains("x.com"))
    }

    @Test
    fun `getPlatformName returns X for x_com URL`() {
        assertEquals("X", UrlExtractor.getPlatformName("https://x.com/user/status/123"))
    }

    // ─── Edge cases ──────────────────────────────────────────────────────────

    @Test
    fun `extractUrl returns null for null input`() {
        assertNull(UrlExtractor.extractUrl(null))
    }

    @Test
    fun `extractUrl returns null for blank input`() {
        assertNull(UrlExtractor.extractUrl("   "))
    }

    @Test
    fun `extractUrl returns null for text with no URL`() {
        assertNull(UrlExtractor.extractUrl("Just some plain text without a link"))
    }

    @Test
    fun `extractUrl strips trailing punctuation from URL`() {
        val sharedText = "Check this out: https://youtube.com/shorts/nlF9i_t59Mo."
        val result = UrlExtractor.extractUrl(sharedText)
        assertNotNull(result)
        assertTrue("Trailing dot should be stripped", !result!!.endsWith("."))
    }
}
