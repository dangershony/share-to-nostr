package com.sharetonostr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharetonostr.data.models.ShareJob
import com.sharetonostr.data.models.ShareState

/**
 * The share confirmation screen shown when a user shares a video URL.
 * Shows video info, optional caption input, and a share button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    shareJob: ShareJob,
    blossomServer: String?,
    pubkey: String?,
    onCaptionChanged: (String) -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onReportError: () -> Unit = {}
) {
    val isConfigured = !blossomServer.isNullOrBlank() && !pubkey.isNullOrBlank()
    val isProcessing = shareJob.state != ShareState.PENDING &&
            shareJob.state != ShareState.COMPLETE &&
            shareJob.state != ShareState.ERROR

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share to Nostr") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Video info card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Thumbnail
                    if (shareJob.thumbnailUrl != null) {
                        AsyncImage(
                            model = shareJob.thumbnailUrl,
                            contentDescription = "Video thumbnail",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }

                    // Title
                    Text(
                        text = shareJob.title.ifBlank { "Loading video info..." },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Source URL
                    Text(
                        text = shareJob.sourceUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Duration
                    if (shareJob.duration > 0) {
                        Text(
                            text = formatDuration(shareJob.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Caption input
            OutlinedTextField(
                value = shareJob.caption,
                onValueChange = onCaptionChanged,
                label = { Text("Caption (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                enabled = !isProcessing
            )

            // Server info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = blossomServer ?: "No Blossom server configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (blossomServer != null)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (pubkey != null) "${pubkey.take(8)}...${pubkey.takeLast(4)}"
                            else "Not connected to Amber",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (pubkey != null)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Progress section
            if (isProcessing) {
                ProgressSection(shareJob)
            }

            // Error message
            if (shareJob.state == ShareState.ERROR && shareJob.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = shareJob.errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Success message
            if (shareJob.state == ShareState.COMPLETE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Shared successfully!",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Action buttons
            if (!isConfigured && shareJob.state == ShareState.PENDING) {
                Text(
                    text = "Please configure a Blossom server and connect to Amber in Settings first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (shareJob.state == ShareState.COMPLETE) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                } else if (shareJob.state == ShareState.ERROR) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = onReportError,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Report Error")
                    }
                } else {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        enabled = isConfigured && !isProcessing && shareJob.state == ShareState.PENDING
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Share to Nostr")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(shareJob: ShareJob) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val statusText = when (shareJob.state) {
            ShareState.FETCHING_INFO -> "Fetching video info..."
            ShareState.DOWNLOADING -> "Downloading video... ${shareJob.progress.toInt()}%"
            ShareState.UPLOADING -> "Uploading to Blossom... ${shareJob.progress.toInt()}%"
            ShareState.SIGNING -> "Waiting for Amber to sign..."
            ShareState.PUBLISHING -> "Publishing to Nostr relays..."
            else -> ""
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium
        )

        if (shareJob.state == ShareState.DOWNLOADING || shareJob.state == ShareState.UPLOADING) {
            LinearProgressIndicator(
                progress = { shareJob.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins}:${secs.toString().padStart(2, '0')}"
}
