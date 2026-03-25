package com.sharetonostr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.sharetonostr.data.SettingsRepository
import com.sharetonostr.nostr.AmberSigner
import com.sharetonostr.ui.screens.SettingsScreen
import com.sharetonostr.ui.theme.ShareToNostrTheme
import com.sharetonostr.download.VideoDownloader
import com.sharetonostr.util.LogCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var amberSigner: AmberSigner

    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pubkey = amberSigner.parsePublicKeyResult(result.resultCode, result.data)
        if (pubkey != null) {
            lifecycleScope.launch {
                settings.setPubkey(pubkey)
            }
            Toast.makeText(this, "Connected to Amber", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to get public key from Amber", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = SettingsRepository(this)
        amberSigner = AmberSigner(this)

        setContent {
            ShareToNostrTheme {
                val blossomServers by settings.blossomServers.collectAsState(initial = emptyList())
                val activeBlossomServer by settings.activeBlossomServer.collectAsState(initial = null)
                val relays by settings.relays.collectAsState(initial = SettingsRepository.DEFAULT_RELAYS)
                val pubkey by settings.pubkey.collectAsState(initial = null)
                val maxResolution by settings.maxResolution.collectAsState(initial = "1080")
                val isAmberInstalled = remember { amberSigner.isInstalled() }

                SettingsScreen(
                    blossomServers = blossomServers,
                    activeBlossomServer = activeBlossomServer,
                    relays = relays,
                    pubkey = pubkey,
                    maxResolution = maxResolution,
                    isAmberInstalled = isAmberInstalled,
                    onAddBlossomServer = { url ->
                        lifecycleScope.launch {
                            val current = settings.blossomServers.first()
                            settings.setBlossomServers(current + url)
                            if (current.isEmpty()) {
                                settings.setActiveBlossomServer(url)
                            }
                        }
                    },
                    onRemoveBlossomServer = { url ->
                        lifecycleScope.launch {
                            val current = settings.blossomServers.first()
                            settings.setBlossomServers(current - url)
                            if (activeBlossomServer == url) {
                                val remaining = current - url
                                if (remaining.isNotEmpty()) {
                                    settings.setActiveBlossomServer(remaining.first())
                                }
                            }
                        }
                    },
                    onSetActiveBlossomServer = { url ->
                        lifecycleScope.launch {
                            settings.setActiveBlossomServer(url)
                        }
                    },
                    onAddRelay = { url ->
                        lifecycleScope.launch {
                            val current = settings.relays.first()
                            settings.setRelays(current + url)
                        }
                    },
                    onRemoveRelay = { url ->
                        lifecycleScope.launch {
                            val current = settings.relays.first()
                            settings.setRelays(current - url)
                        }
                    },
                    onConnectAmber = {
                        if (amberSigner.isInstalled()) {
                            amberLauncher.launch(amberSigner.getPublicKeyIntent())
                        } else {
                            Toast.makeText(
                                this,
                                "Amber is not installed",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onDisconnectAmber = {
                        lifecycleScope.launch {
                            settings.clearPubkey()
                        }
                    },
                    onSetMaxResolution = { res ->
                        lifecycleScope.launch {
                            settings.setMaxResolution(res)
                        }
                    },
                    onUpdateYtDlp = {
                        lifecycleScope.launch {
                            try {
                                val downloader = VideoDownloader(this@MainActivity)
                                val result = downloader.updateYtDlp()
                                Toast.makeText(
                                    this@MainActivity,
                                    result,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Update failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onCopyLogs = {
                        lifecycleScope.launch {
                            try {
                                val logs = LogCollector.collectLogs()
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Share to Nostr Logs", logs))
                                Toast.makeText(
                                    this@MainActivity,
                                    "Logs copied to clipboard",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to copy logs: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onDeleteLogs = {
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { LogCollector.clearLogs() }
                                Toast.makeText(
                                    this@MainActivity,
                                    "Logs cleared",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to clear logs: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}
