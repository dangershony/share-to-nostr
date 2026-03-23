package com.sharetonostr.nostr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

/**
 * Represents a Nostr event.
 * The app constructs unsigned events and sends them to Amber for signing.
 */
@Serializable
data class NostrEvent(
    val id: String = "",
    val pubkey: String = "",
    val created_at: Long = System.currentTimeMillis() / 1000,
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
    val sig: String = ""
) {
    /**
     * Compute the event ID as per NIP-01:
     * SHA-256 of the serialized event array [0, pubkey, created_at, kind, tags, content]
     */
    fun computeId(): String {
        val serialized = buildJsonArray {
            add(JsonPrimitive(0))
            add(JsonPrimitive(pubkey))
            add(JsonPrimitive(created_at))
            add(JsonPrimitive(kind))
            add(buildJsonArray {
                for (tag in tags) {
                    add(buildJsonArray {
                        for (item in tag) {
                            add(JsonPrimitive(item))
                        }
                    })
                }
            })
            add(JsonPrimitive(content))
        }
        val json = Json.encodeToString(JsonArray.serializer(), serialized)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Return this event with the computed ID filled in.
     */
    fun withComputedId(): NostrEvent {
        return copy(id = computeId())
    }

    /**
     * Serialize to JSON string for sending to Amber or relays.
     */
    fun toJson(): String {
        return Json.encodeToString(serializer(), this)
    }

    /**
     * Serialize to the relay wire format: ["EVENT", {event}]
     */
    fun toRelayMessage(): String {
        val eventJson = Json.encodeToJsonElement(serializer(), this)
        val message = buildJsonArray {
            add(JsonPrimitive("EVENT"))
            add(eventJson)
        }
        return Json.encodeToString(JsonArray.serializer(), message)
    }

    companion object {
        fun fromJson(json: String): NostrEvent {
            return Json.decodeFromString(serializer(), json)
        }
    }
}
