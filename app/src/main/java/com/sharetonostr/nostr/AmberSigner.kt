package com.sharetonostr.nostr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Handles communication with Amber (NIP-55) for Nostr event signing.
 *
 * Amber supports two mechanisms:
 * 1. Intents - Interactive, user sees Amber popup to approve
 * 2. Content Resolvers - Silent background signing after user selects "remember my choice"
 */
class AmberSigner(private val context: Context) {

    companion object {
        private const val TAG = "AmberSigner"
        const val AMBER_PACKAGE = "com.greenart7c3.nostrsigner"

        // Result keys from Amber
        const val RESULT_KEY_SIGNATURE = "signature"
        const val RESULT_KEY_EVENT = "event"
        const val RESULT_KEY_PACKAGE = "package"
    }

    /**
     * Check if Amber is installed on the device.
     */
    fun isInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }

    /**
     * Create an intent to request the user's public key from Amber.
     * Launch this with an ActivityResultLauncher.
     */
    fun getPublicKeyIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
            `package` = AMBER_PACKAGE
            putExtra("type", "get_public_key")
        }
    }

    /**
     * Create an intent to sign a Nostr event via Amber.
     * Launch this with an ActivityResultLauncher.
     *
     * @param unsignedEventJson The unsigned event JSON
     * @param eventId The computed event ID
     * @param currentUserPubkey The current user's pubkey (for multi-account support)
     */
    fun signEventIntent(
        unsignedEventJson: String,
        eventId: String,
        currentUserPubkey: String
    ): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("nostrsigner:$unsignedEventJson")
        ).apply {
            `package` = AMBER_PACKAGE
            putExtra("type", "sign_event")
            putExtra("id", eventId)
            putExtra("current_user", currentUserPubkey)
        }
    }

    /**
     * Try to sign an event silently using content resolver.
     * This only works if the user previously approved signing and selected "remember my choice".
     *
     * @return The signature string, or null if silent signing is not available.
     */
    fun signEventSilent(unsignedEventJson: String, currentUserPubkey: String): String? {
        return try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://$AMBER_PACKAGE.SIGN_EVENT"),
                arrayOf(unsignedEventJson, "", currentUserPubkey),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val result = it.getString(0)
                    Log.d(TAG, "Silent signing succeeded")
                    result
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent signing not available: ${e.message}")
            null
        }
    }

    /**
     * Parse the public key from Amber's get_public_key result.
     */
    fun parsePublicKeyResult(resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val pubkey = data.getStringExtra("signature") // Amber returns pubkey in "signature" field
            ?: data.getStringExtra("result")
        Log.d(TAG, "Got pubkey from Amber: ${pubkey?.take(16)}...")
        return pubkey
    }

    /**
     * Parse the signed event from Amber's sign_event result.
     */
    fun parseSignEventResult(resultCode: Int, data: Intent?): SignedEventResult? {
        if (resultCode != Activity.RESULT_OK || data == null) return null

        val signature = data.getStringExtra(RESULT_KEY_SIGNATURE)
        val signedEventJson = data.getStringExtra(RESULT_KEY_EVENT)

        Log.d(TAG, "Got signature from Amber: ${signature?.take(16)}...")

        return if (signature != null || signedEventJson != null) {
            SignedEventResult(
                signature = signature,
                signedEventJson = signedEventJson
            )
        } else {
            null
        }
    }

    data class SignedEventResult(
        val signature: String?,
        val signedEventJson: String?
    )
}
