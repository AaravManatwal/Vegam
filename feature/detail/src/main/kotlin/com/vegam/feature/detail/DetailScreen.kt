package com.vegam.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.core.database.DownloadStatus
import com.vegam.core.download.DownloadItem
import com.vegam.core.ui.FileTypeTile
import com.vegam.core.ui.GradientRing
import com.vegam.core.ui.NumberTicker
import com.vegam.core.ui.SuccessCheck
import com.vegam.core.ui.VegamGradient
import com.vegam.engine.Formatters

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    val item = state.item

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Download detail", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        if (state.loaded && item == null) {
            item { Text("Ye download ab list mein nahi hai.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (item != null) {
            item { HeaderCard(item) }
            item { SectionTitle("Segments (Vegam Engine)") }
            item { SegmentsCard(state.segments, item.isRunning) }
            item { SectionTitle("Speed, last 60 seconds") }
            item { PlainCard { SpeedGraph(state.speedHistory) } }
            item {
                Actions(
                    item,
                    onToggle = viewModel::togglePause,
                    onCancel = { confirmCancel = true },
                )
            }
            item { SectionTitle("File info") }
            item { InfoCard(item, state.checksum, onChecksum = viewModel::computeChecksum) }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Download cancel karein?") },
            text = { Text("Adhoori file hat jaayegi. Ye wapas nahi aayegi.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; viewModel.cancel(onDone = onBack) }) { Text("Cancel karo") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Nahi") } },
        )
    }
}

@Composable
private fun PlainCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth(),
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

@Composable
private fun HeaderCard(item: DownloadItem) {
    PlainCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileTypeTile(item.name, sharedKey = "tile-${item.id}")
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${Formatters.bytes(item.downloadedBytes)} / ${Formatters.bytes(item.totalBytes)} · ${statusLabel(item.status)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GradientRing(progress = item.progress, size = 168.dp, strokeWidth = 12.dp, active = item.isRunning) {
                // Poora hone par % ki jagah success check (spring + tick draw).
                if (item.status == DownloadStatus.COMPLETED) SuccessCheck(size = 84.dp)
                else Text("${(item.progress * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (item.isRunning) NumberTicker(Formatters.speed(item.bytesPerSec), style = MaterialTheme.typography.titleLarge)
                else Text(statusLabel(item.status), style = MaterialTheme.typography.titleLarge)
                if (item.isRunning) Text("ETA ${Formatters.eta(item.etaSeconds)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SegmentsCard(segments: List<SegmentUi>, running: Boolean) {
    PlainCard {
        if (segments.isEmpty()) {
            Text("Download shuru hone par har segment ki progress yahan dikhegi.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { SegmentRow(it, running) }
            }
        }
    }
}

@Composable
private fun SegmentRow(seg: SegmentUi, running: Boolean) {
    val p by animateFloatAsState(seg.progress, label = "segment${seg.index}")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("#${seg.index}", Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(RoundedCornerShape(6.dp)).background(VegamGradient))
        }
        Text(
            "${(seg.progress * 100).toInt()}%", Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium,
            color = if (seg.done || running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Actions(item: DownloadItem, onToggle: () -> Unit, onCancel: () -> Unit) {
    val primary = when (item.status) {
        DownloadStatus.PAUSED -> "Resume"
        DownloadStatus.FAILED -> "Retry"
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> "Pause"
        else -> null
    }
    val canCancel = item.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED, DownloadStatus.FAILED)
    if (primary == null && !canCancel) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (primary != null) FilledTonalButton(onClick = onToggle, Modifier.weight(1f)) { Text(primary) }
        if (canCancel) OutlinedButton(onClick = onCancel, Modifier.weight(1f)) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InfoCard(item: DownloadItem, checksum: ChecksumState, onChecksum: () -> Unit) {
    PlainCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Size", Formatters.bytes(item.totalBytes))
            InfoRow("URL", item.url)
            InfoRow(
                "Saved to",
                if (item.destUri != null && item.status == DownloadStatus.COMPLETED) "Chosen folder" else item.targetPath.ifBlank { "App folder" },
            )
            InfoRow("Priority", listOf("Low", "Normal", "High").getOrElse(item.priority) { "Normal" })
            InfoRow("Connections", item.maxSegments.toString())
            InfoRow("Wi-Fi only", if (item.wifiOnly) "Yes" else "No")
            item.error?.let { InfoRow("Note", it, MaterialTheme.colorScheme.error) }
            if (item.status == DownloadStatus.COMPLETED) {
                when (checksum) {
                    ChecksumState.Idle -> Button(onClick = onChecksum, shape = MaterialTheme.shapes.medium) { Text("SHA-256 nikalo") }
                    ChecksumState.Computing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                        Text("Checksum nikal rahe hain...")
                    }
                    is ChecksumState.Done -> {
                        Text("SHA-256", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(checksum.hex, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    }
                    ChecksumState.Unavailable -> Text(
                        "File app folder mein nahi hai (chune hue folder mein move ho chuki), isliye checksum nahi nikal sakte.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}

private fun statusLabel(s: DownloadStatus) = when (s) {
    DownloadStatus.QUEUED -> "Waiting"
    DownloadStatus.RUNNING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}
