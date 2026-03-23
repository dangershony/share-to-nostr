package com.sharetonostr.nostr

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*

/**
 * Publishes signed Nostr events to relays via WebSocket.
 */
class RelayPublisher(
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        private const val TAG = "RelayPublisher"
        private const val PUBLISH_TIMEOUT_MS = 15_000L
    }

    data class PublishResult(
        val relayUrl: String,
        val success: Boolean,
        val message: String = ""
    )

    /**
     * Publish a signed event to multiple relays.
     * Returns results for each relay.
     */
    suspend fun publishToRelays(
        signedEvent: NostrEvent,
        relayUrls: List<String>
    ): List<PublishResult> = coroutineScope {
        val relayMessage = signedEvent.toRelayMessage()
        Log.d(TAG, "Publishing event ${signedEvent.id.take(8)} to ${relayUrls.size} relays")

        relayUrls.map { relayUrl ->
            async(Dispatchers.IO) {
                publishToRelay(relayUrl, relayMessage, signedEvent.id)
            }
        }.awaitAll()
    }

    private suspend fun publishToRelay(
        relayUrl: String,
        message: String,
        eventId: String
    ): PublishResult {
        val result = CompletableDeferred<PublishResult>()

        return try {
            val request = Request.Builder()
                .url(relayUrl)
                .build()

            httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connected to $relayUrl, sending event")
                    webSocket.send(message)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Response from $relayUrl: $text")
                    try {
                        val json = Json.parseToJsonElement(text).jsonArray
                        val type = json[0].jsonPrimitive.content

                        if (type == "OK") {
                            val accepted = json[2].jsonPrimitive.boolean
                            val msg = if (json.size > 3) json[3].jsonPrimitive.content else ""
                            webSocket.close(1000, "Done")
                            result.complete(
                                PublishResult(relayUrl, accepted, msg)
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse relay response: $text", e)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure for $relayUrl", t)
                    result.complete(
                        PublishResult(relayUrl, false, t.message ?: "Connection failed")
                    )
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            })

            withTimeout(PUBLISH_TIMEOUT_MS) {
                result.await()
            }
        } catch (e: TimeoutCancellationException) {
            PublishResult(relayUrl, false, "Timeout")
        } catch (e: Exception) {
            PublishResult(relayUrl, false, e.message ?: "Unknown error")
        }
    }
}
