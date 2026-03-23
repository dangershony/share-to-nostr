package com.sharetonostr

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.sharetonostr.blossom.BlossomClient
import com.sharetonostr.data.SettingsRepository
import com.sharetonostr.data.models.ShareJob
import com.sharetonostr.data.models.ShareState
import com.sharetonostr.download.UrlExtractor
import com.sharetonostr.download.VideoDownloader
import com.sharetonostr.nostr.*
import com.sharetonostr.ui.screens.ShareScreen
import com.sharetonostr.ui.theme.ShareToNostrTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Activity that receives shared URLs from other apps (YouTube, TikTok, etc.)
 * and orchestrates the full share-to-Nostr flow:
 *
 * 1. Extract URL from shared text
 * 2. Fetch video metadata via yt-dlp
 * 3. Download the video
 * 4. Compute SHA-256 and sign Blossom auth via Amber
 * 5. Upload to Blossom server
 * 6. Build NIP-71 event and sign via Amber
 * 7. Publish to Nostr relays
 */
class ShareReceiverActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareReceiver"
    }

    private lateinit var settings: SettingsRepository
    private lateinit var amberSigner: AmberSigner
    private lateinit var videoDownloader: VideoDownloader
    private lateinit var blossomClient: BlossomClient
    private lateinit var relayPublisher: RelayPublisher

    // Mutable state for the UI
    private var shareJob by mutableStateOf(ShareJob(sourceUrl = ""))

    // State for the signing flow
    private var pendingSignCallback: ((String?) -> Unit)? = null

    // Amber signing launcher
    private val signEventLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signResult = amberSigner.parseSignEventResult(result.resultCode, result.data)
        val callback = pendingSignCallback
        pendingSignCallback = null

        if (signResult?.signedEventJson != null) {
            callback?.invoke(signResult.signedEventJson)
        } else if (signResult?.signature != null) {
            callback?.invoke(signResult.signature)
        } else {
            callback?.invoke(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = SettingsRepository(this)
        amberSigner = AmberSigner(this)
        videoDownloader = VideoDownloader(this)
        blossomClient = BlossomClient()
        relayPublisher = RelayPublisher()

        // Extract URL from share intent
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val extractedUrl = UrlExtractor.extractUrl(sharedText)

        if (extractedUrl == null) {
            Toast.makeText(this, "No video URL found in shared content", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.i(TAG, "Received shared URL: $extractedUrl")
        shareJob = ShareJob(sourceUrl = extractedUrl)

        // Fetch video info in the background
        lifecycleScope.launch {
            fetchVideoInfo(extractedUrl)
        }

        setContent {
            ShareToNostrTheme {
                val blossomServer by settings.activeBlossomServer.collectAsState(initial = null)
                val pubkey by settings.pubkey.collectAsState(initial = null)

                ShareScreen(
                    shareJob = shareJob,
                    blossomServer = blossomServer,
                    pubkey = pubkey,
                    onCaptionChanged = { caption ->
                        shareJob = shareJob.copy(caption = caption)
                    },
                    onShare = {
                        lifecycleScope.launch {
                            executeShareFlow()
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    /**
     * Fetch video metadata (title, thumbnail, duration) using yt-dlp.
     */
    private suspend fun fetchVideoInfo(url: String) {
        try {
            shareJob = shareJob.copy(state = ShareState.FETCHING_INFO)
            val info = videoDownloader.getVideoInfo(url)
            shareJob = shareJob.copy(
                title = info.title,
                thumbnailUrl = info.thumbnailUrl,
                duration = info.duration,
                state = ShareState.PENDING
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch video info, continuing anyway", e)
            val platform = UrlExtractor.getPlatformName(url)
            shareJob = shareJob.copy(
                title = "$platform video",
                state = ShareState.PENDING
            )
        }
    }

    /**
     * Execute the full share flow: download -> upload -> publish.
     */
    private suspend fun executeShareFlow() {
        val pubkey = settings.pubkey.first()
        val blossomServerUrl = settings.activeBlossomServer.first()
        val relays = settings.relays.first()
        val maxResolution = settings.maxResolution.first()

        if (pubkey.isNullOrBlank() || blossomServerUrl.isNullOrBlank()) {
            shareJob = shareJob.copy(
                state = ShareState.ERROR,
                errorMessage = "Please configure Blossom server and connect to Amber in Settings"
            )
            return
        }

        var downloadedFile: File? = null

        try {
            // Step 1: Download the video
            shareJob = shareJob.copy(state = ShareState.DOWNLOADING, progress = 0f)
            downloadedFile = videoDownloader.download(
                url = shareJob.sourceUrl,
                maxResolution = maxResolution,
                onProgress = { progress, _ ->
                    shareJob = shareJob.copy(progress = progress)
                }
            )
            Log.i(TAG, "Downloaded: ${downloadedFile.name} (${downloadedFile.length()} bytes)")

            // Step 2: Compute SHA-256
            val sha256 = blossomClient.computeSha256(downloadedFile)
            Log.i(TAG, "SHA-256: $sha256")

            // Step 3: Sign Blossom auth event via Amber
            shareJob = shareJob.copy(state = ShareState.SIGNING, progress = 0f)
            val serverDomain = blossomClient.extractDomain(blossomServerUrl)
            val authEvent = EventBuilder.buildBlossomAuthEvent(
                pubkey = pubkey,
                sha256 = sha256,
                serverDomain = serverDomain
            )

            val signedAuthJson = signEventViaAmber(authEvent, pubkey)
                ?: throw Exception("Failed to sign Blossom auth event")

            // Step 4: Upload to Blossom
            shareJob = shareJob.copy(state = ShareState.UPLOADING, progress = 0f)
            val blobDescriptor = blossomClient.upload(
                serverUrl = blossomServerUrl,
                file = downloadedFile,
                signedAuthEventJson = signedAuthJson,
                onProgress = { uploaded, total ->
                    if (total > 0) {
                        shareJob = shareJob.copy(progress = (uploaded * 100f / total))
                    }
                }
            )
            Log.i(TAG, "Uploaded to Blossom: ${blobDescriptor.url}")

            // Step 5: Build and sign NIP-71 video event
            shareJob = shareJob.copy(state = ShareState.SIGNING, progress = 0f)
            val videoEvent = EventBuilder.buildShortVideoEvent(
                pubkey = pubkey,
                videoUrl = blobDescriptor.url,
                sha256 = sha256,
                fileSize = downloadedFile.length(),
                mimeType = blobDescriptor.type ?: "video/mp4",
                title = shareJob.title,
                caption = shareJob.caption,
                sourceUrl = shareJob.sourceUrl
            )

            val signedVideoJson = signEventViaAmber(videoEvent, pubkey)
                ?: throw Exception("Failed to sign video event")

            val signedVideoEvent = NostrEvent.fromJson(signedVideoJson)

            // Step 6: Publish to relays
            shareJob = shareJob.copy(state = ShareState.PUBLISHING, progress = 0f)
            val results = relayPublisher.publishToRelays(
                signedEvent = signedVideoEvent,
                relayUrls = relays.toList()
            )

            val successCount = results.count { it.success }
            Log.i(TAG, "Published to $successCount/${results.size} relays")

            if (successCount > 0) {
                shareJob = shareJob.copy(
                    state = ShareState.COMPLETE,
                    blossomUrl = blobDescriptor.url,
                    noteId = signedVideoEvent.id
                )
            } else {
                val errors = results.map { "${it.relayUrl}: ${it.message}" }.joinToString("\n")
                throw Exception("Failed to publish to any relay:\n$errors")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Share flow failed", e)
            shareJob = shareJob.copy(
                state = ShareState.ERROR,
                errorMessage = e.message ?: "Unknown error"
            )
        } finally {
            // Clean up downloaded file
            downloadedFile?.delete()
        }
    }

    /**
     * Sign a Nostr event via Amber.
     * First tries silent signing (content resolver), then falls back to interactive intent.
     *
     * @return The signed event JSON string, or null if signing failed.
     */
    private suspend fun signEventViaAmber(event: NostrEvent, pubkey: String): String? {
        val eventJson = event.toJson()

        // Try silent signing first
        val silentResult = amberSigner.signEventSilent(eventJson, pubkey)
        if (silentResult != null) {
            Log.d(TAG, "Silent signing succeeded")
            return if (silentResult.startsWith("{")) {
                silentResult
            } else {
                event.copy(sig = silentResult).toJson()
            }
        }

        // Fall back to interactive signing via Amber intent
        Log.d(TAG, "Falling back to interactive signing")
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            pendingSignCallback = { result ->
                if (continuation.isActive) {
                    if (result != null) {
                        val signedJson = if (result.startsWith("{")) {
                            result
                        } else {
                            event.copy(sig = result).toJson()
                        }
                        continuation.resume(signedJson, null)
                    } else {
                        continuation.resume(null, null)
                    }
                }
            }

            val intent = amberSigner.signEventIntent(eventJson, event.id, pubkey)
            signEventLauncher.launch(intent)
        }
    }
}
