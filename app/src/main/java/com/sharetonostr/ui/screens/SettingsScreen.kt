package com.sharetonostr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings screen for configuring Blossom servers, relays, and Nostr account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    blossomServers: List<String>,
    activeBlossomServer: String?,
    relays: Set<String>,
    pubkey: String?,
    maxResolution: String,
    isAmberInstalled: Boolean,
    onAddBlossomServer: (String) -> Unit,
    onRemoveBlossomServer: (String) -> Unit,
    onSetActiveBlossomServer: (String) -> Unit,
    onAddRelay: (String) -> Unit,
    onRemoveRelay: (String) -> Unit,
    onConnectAmber: () -> Unit,
    onDisconnectAmber: () -> Unit,
    onSetMaxResolution: (String) -> Unit,
    onUpdateYtDlp: () -> Unit
) {
    var showAddServerDialog by remember { mutableStateOf(false) }
    var showAddRelayDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Nostr Account Section ---
            item {
                SectionHeader("Nostr Account")
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (pubkey != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Connected", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${pubkey.take(8)}...${pubkey.takeLast(4)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = onDisconnectAmber) {
                                    Text("Disconnect")
                                }
                            }
                        } else {
                            Button(
                                onClick = onConnectAmber,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isAmberInstalled
                            ) {
                                Icon(Icons.Default.Key, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Connect with Amber")
                            }
                            if (!isAmberInstalled) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Amber is not installed. Install it from F-Droid or GitHub.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // --- Blossom Servers Section ---
            item {
                SectionHeader("Blossom Servers")
            }

            items(blossomServers) { server ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = server == activeBlossomServer,
                            onClick = { onSetActiveBlossomServer(server) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = server,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { onRemoveBlossomServer(server) }) {
                            Icon(Icons.Default.Delete, "Remove")
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddServerDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Blossom Server")
                }
            }

            // --- Relays Section ---
            item {
                SectionHeader("Nostr Relays")
            }

            items(relays.toList()) { relay ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cloud, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = relay,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { onRemoveRelay(relay) }) {
                            Icon(Icons.Default.Delete, "Remove")
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddRelayDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Relay")
                }
            }

            // --- Video Quality Section ---
            item {
                SectionHeader("Video Settings")
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Max Resolution", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("480", "720", "1080").forEach { res ->
                                FilterChip(
                                    selected = maxResolution == res,
                                    onClick = { onSetMaxResolution(res) },
                                    label = { Text("${res}p") }
                                )
                            }
                        }
                    }
                }
            }

            // --- yt-dlp Update ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Update yt-dlp", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Update the video downloader to support the latest sites",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = onUpdateYtDlp) {
                            Icon(Icons.Default.Update, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Update")
                        }
                    }
                }
            }

            // Bottom spacer
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // --- Dialogs ---

    if (showAddServerDialog) {
        AddUrlDialog(
            title = "Add Blossom Server",
            placeholder = "https://blossom.example.com",
            onDismiss = { showAddServerDialog = false },
            onConfirm = { url ->
                onAddBlossomServer(url)
                showAddServerDialog = false
            }
        )
    }

    if (showAddRelayDialog) {
        AddUrlDialog(
            title = "Add Relay",
            placeholder = "wss://relay.example.com",
            onDismiss = { showAddRelayDialog = false },
            onConfirm = { url ->
                onAddRelay(url)
                showAddRelayDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun AddUrlDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim()) },
                enabled = url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
