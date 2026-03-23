package com.sharetonostr.nostr

/**
 * Builds Nostr events for NIP-71 video posts and Blossom authorization.
 */
object EventBuilder {

    /**
     * Build a NIP-71 kind 34236 (addressable short-form video) event.
     *
     * @param pubkey Publisher's public key (hex)
     * @param videoUrl Blossom URL of the uploaded video
     * @param sha256 SHA-256 hash of the video file (hex)
     * @param fileSize Size of the video file in bytes
     * @param mimeType MIME type (e.g. "video/mp4")
     * @param title Video title
     * @param caption Optional user caption
     * @param sourceUrl Original video URL (YouTube/TikTok/etc)
     * @param dimensions Optional video dimensions (e.g. "1080x1920")
     */
    fun buildShortVideoEvent(
        pubkey: String,
        videoUrl: String,
        sha256: String,
        fileSize: Long,
        mimeType: String = "video/mp4",
        title: String,
        caption: String = "",
        sourceUrl: String,
        dimensions: String? = null
    ): NostrEvent {
        val dTag = sha256.take(16)

        // Build imeta tag parts (NIP-92)
        val imetaParts = mutableListOf(
            "url $videoUrl",
            "m $mimeType",
            "x $sha256",
            "size $fileSize"
        )
        dimensions?.let { imetaParts.add("dim $it") }

        val tags = mutableListOf(
            listOf("d", dTag),
            listOf("title", title),
            listOf("url", videoUrl),
            listOf("imeta") + imetaParts,
            listOf("r", sourceUrl),
            listOf("t", "shorts")
        )

        if (caption.isNotBlank()) {
            tags.add(listOf("summary", caption))
        }

        return NostrEvent(
            kind = 34236,
            pubkey = pubkey,
            tags = tags,
            content = caption
        ).withComputedId()
    }

    /**
     * Build a Blossom authorization event (kind 24242) for uploading.
     *
     * @param pubkey Uploader's public key (hex)
     * @param sha256 SHA-256 hash of the file to upload (hex)
     * @param serverDomain Blossom server domain (e.g. "blossom.example.com")
     */
    fun buildBlossomAuthEvent(
        pubkey: String,
        sha256: String,
        serverDomain: String
    ): NostrEvent {
        val expiration = (System.currentTimeMillis() / 1000 + 300).toString() // 5 minutes

        return NostrEvent(
            kind = 24242,
            pubkey = pubkey,
            tags = listOf(
                listOf("t", "upload"),
                listOf("x", sha256),
                listOf("expiration", expiration),
                listOf("server", serverDomain)
            ),
            content = "Upload video"
        ).withComputedId()
    }
}
