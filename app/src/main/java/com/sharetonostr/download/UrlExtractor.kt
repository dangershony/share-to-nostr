package com.sharetonostr.download

/**
 * Extracts video URLs from shared text content.
 *
 * Different apps share differently - some share just the URL,
 * others include descriptive text around it. This class handles
 * extracting and validating the actual video URL.
 */
object UrlExtractor {

    private val URL_PATTERN = Regex("https?://[\\S]+")

    private val SUPPORTED_PATTERNS = listOf(
        // YouTube Shorts
        Regex("https?://(www\\.)?youtube\\.com/shorts/[\\w-]+"),
        Regex("https?://youtu\\.be/[\\w-]+"),
        Regex("https?://(www\\.)?youtube\\.com/watch\\?v=[\\w-]+"),
        // TikTok
        Regex("https?://(www\\.)?tiktok\\.com/@[\\w.-]+/video/\\d+"),
        Regex("https?://vm\\.tiktok\\.com/[\\w]+"),
        Regex("https?://(www\\.)?tiktok\\.com/t/[\\w]+"),
        // Instagram
        Regex("https?://(www\\.)?instagram\\.com/reel/[\\w-]+"),
        Regex("https?://(www\\.)?instagram\\.com/reels/[\\w-]+"),
        Regex("https?://(www\\.)?instagram\\.com/p/[\\w-]+"),
        // X / Twitter
        Regex("https?://(www\\.)?(x|twitter)\\.com/\\w+/status/\\d+"),
        // Reddit
        Regex("https?://(www\\.)?reddit\\.com/r/\\w+/comments/[\\w]+"),
        Regex("https?://v\\.redd\\.it/[\\w]+"),
        // Facebook
        Regex("https?://(www\\.)?facebook\\.com/.+/videos/\\d+"),
        Regex("https?://(www\\.)?fb\\.watch/[\\w]+"),
    )

    /**
     * Extract a supported video URL from shared text.
     * Returns null if no supported URL is found.
     */
    fun extractUrl(sharedText: String?): String? {
        if (sharedText.isNullOrBlank()) return null

        // Find all URLs in the text
        val urls = URL_PATTERN.findAll(sharedText)
            .map { it.value.trimEnd('.', ',', ')', ']', '!', '?') }
            .toList()

        // Try to match against supported patterns first
        for (url in urls) {
            for (pattern in SUPPORTED_PATTERNS) {
                if (pattern.containsMatchIn(url)) {
                    return url
                }
            }
        }

        // If no supported pattern matches, return the first URL found
        // yt-dlp supports many more sites than we explicitly list
        return urls.firstOrNull()
    }

    /**
     * Check if the URL matches a known short-form video pattern.
     */
    fun isShortFormVideo(url: String): Boolean {
        val shortPatterns = listOf(
            Regex("youtube\\.com/shorts/"),
            Regex("tiktok\\.com"),
            Regex("vm\\.tiktok\\.com"),
            Regex("instagram\\.com/reel"),
            Regex("instagram\\.com/reels"),
        )
        return shortPatterns.any { it.containsMatchIn(url) }
    }

    /**
     * Get a human-readable platform name from a URL.
     */
    fun getPlatformName(url: String): String {
        return when {
            "youtube.com" in url || "youtu.be" in url -> "YouTube"
            "tiktok.com" in url -> "TikTok"
            "instagram.com" in url -> "Instagram"
            "x.com" in url || "twitter.com" in url -> "X"
            "reddit.com" in url || "redd.it" in url -> "Reddit"
            "facebook.com" in url || "fb.watch" in url -> "Facebook"
            else -> "Video"
        }
    }
}
