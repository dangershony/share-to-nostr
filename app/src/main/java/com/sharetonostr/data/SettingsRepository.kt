package com.sharetonostr.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_BLOSSOM_SERVERS = stringPreferencesKey("blossom_servers")
        private val KEY_ACTIVE_BLOSSOM_SERVER = stringPreferencesKey("active_blossom_server")
        private val KEY_RELAYS = stringSetPreferencesKey("relays")
        private val KEY_PUBKEY = stringPreferencesKey("pubkey")
        private val KEY_MAX_RESOLUTION = stringPreferencesKey("max_resolution")

        val DEFAULT_RELAYS = setOf(
            "wss://relay.damus.io",
            "wss://relay.nostr.band",
            "wss://nos.lol",
            "wss://relay.snort.social"
        )
    }

    // --- Blossom Servers ---

    /** Stored as JSON array string, e.g. ["https://blossom.example.com"] */
    val blossomServers: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_BLOSSOM_SERVERS] ?: "[]"
        raw.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }

    val activeBlossomServer: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_BLOSSOM_SERVER]
    }

    suspend fun setBlossomServers(servers: List<String>) {
        context.dataStore.edit { prefs ->
            val json = servers.joinToString(",") { "\"$it\"" }
            prefs[KEY_BLOSSOM_SERVERS] = "[$json]"
        }
    }

    suspend fun setActiveBlossomServer(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_BLOSSOM_SERVER] = url
        }
    }

    // --- Relays ---

    val relays: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_RELAYS] ?: DEFAULT_RELAYS
    }

    suspend fun setRelays(relays: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RELAYS] = relays
        }
    }

    // --- Nostr Account ---

    val pubkey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PUBKEY]
    }

    suspend fun setPubkey(pubkey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PUBKEY] = pubkey
        }
    }

    suspend fun clearPubkey() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PUBKEY)
        }
    }

    // --- Video Quality ---

    val maxResolution: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAX_RESOLUTION] ?: "1080"
    }

    suspend fun setMaxResolution(resolution: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MAX_RESOLUTION] = resolution
        }
    }
}
