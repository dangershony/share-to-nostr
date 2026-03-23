package com.sharetonostr.blossom

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.buffer
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client for uploading files to a Blossom server.
 *
 * Blossom uses signed Nostr events (kind 24242) as authorization tokens.
 * The token is base64url-encoded and sent in the Authorization header.
 */
class BlossomClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofMinutes(10))
        .writeTimeout(java.time.Duration.ofMinutes(5))
        .readTimeout(java.time.Duration.ofMinutes(5))
        .build()
) {

    companion object {
        private const val TAG = "BlossomClient"
        private val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    data class BlobDescriptor(
        val url: String,
        val sha256: String,
        val size: Long,
        val type: String? = null,
        val uploaded: Long? = null
    )

    /**
     * Upload a file to a Blossom server.
     *
     * @param serverUrl Base URL of the Blossom server (e.g. "https://blossom.example.com")
     * @param file The file to upload
     * @param signedAuthEventJson The signed kind 24242 authorization event as JSON string
     * @param onProgress Callback with (bytesUploaded, totalBytes)
     * @return BlobDescriptor with the URL and metadata of the uploaded blob
     */
    suspend fun upload(
        serverUrl: String,
        file: File,
        signedAuthEventJson: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): BlobDescriptor = withContext(Dispatchers.IO) {
        val authToken = base64UrlEncode(signedAuthEventJson)
        val mimeType = guessMimeType(file)

        Log.d(TAG, "Uploading ${file.name} (${file.length()} bytes) to $serverUrl")

        val requestBody = file.asRequestBody(mimeType.toMediaType())
        val progressBody = ProgressRequestBody(requestBody, onProgress)

        val uploadUrl = serverUrl.trimEnd('/') + "/upload"
        val request = Request.Builder()
            .url(uploadUrl)
            .put(progressBody)
            .header("Authorization", "Nostr $authToken")
            .header("Content-Type", mimeType)
            .build()

        val response = suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(
                        BlossomUploadException("Upload failed: ${e.message}", e)
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                throw BlossomUploadException("Upload failed (${resp.code}): $body")
            }

            val body = resp.body?.string()
                ?: throw BlossomUploadException("Empty response from server")

            Log.i(TAG, "Upload successful: $body")
            json.decodeFromString<BlobDescriptor>(body)
        }
    }

    /**
     * Compute SHA-256 hash of a file.
     */
    suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extract the domain from a server URL for the "server" tag.
     */
    fun extractDomain(serverUrl: String): String {
        return try {
            java.net.URI(serverUrl).host ?: serverUrl
        } catch (e: Exception) {
            serverUrl
        }
    }

    /**
     * Base64url-encode a string (no padding), as required by Blossom auth.
     */
    private fun base64UrlEncode(input: String): String {
        return Base64.encodeToString(
            input.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    private fun guessMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            else -> "video/mp4"
        }
    }
}

/**
 * OkHttp RequestBody wrapper that reports upload progress.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Long, Long) -> Unit
) : RequestBody() {

    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: okio.BufferedSink) {
        val totalBytes = contentLength()
        val countingSink = object : okio.ForwardingSink(sink) {
            var bytesWritten = 0L
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                onProgress(bytesWritten, totalBytes)
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

class BlossomUploadException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
