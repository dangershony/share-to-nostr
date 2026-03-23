package com.sharetonostr.data.models

import kotlinx.serialization.Serializable

@Serializable
data class NostrAccount(
    val pubkey: String,
    val displayName: String = ""
) {
    /** Returns the npub-style shortened display like "npub1abc...xyz" */
    fun shortPubkey(): String {
        if (pubkey.length <= 16) return pubkey
        return pubkey.take(8) + "..." + pubkey.takeLast(4)
    }
}
