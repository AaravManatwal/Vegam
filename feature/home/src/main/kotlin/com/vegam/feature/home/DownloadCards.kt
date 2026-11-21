package com.vegam.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vegam.core.database.DownloadStatus
import com.vegam.core.download.DownloadItem
import com.vegam.core.ui.FileTypeTile
import com.vegam.core.ui.GradientRing
import com.vegam.core.ui.NumberTicker
import com.vegam.core.ui.VegamGradient
import com.vegam.core.ui.pressScale
import com.vegam.engine.Formatters

private val PRIORITY = listOf("Low", "Normal", "High")

/** Chalti / queue mein / paused / failed download ka card. */
@Composable
fun ActiveDownloadCard(
    item: DownloadItem,
    onTogglePause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val running = item.isRunning
    val outline = if (running) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
    Card(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().pressScale(source),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileTypeTile(item.name, sharedKey = "tile-${item.id}")
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(item), style = MaterialTheme.typography.bodySmall, color = subtitleColor(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GradientRing(progress = item.progress, active = running) {
                    Text("${(item.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running) {
                    NumberTicker(Formatters.speed(item.bytesPerSec), Modifier.weight(1f))
                } else {
                    Text(statusLabel(item), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                if (running) {
                    Text("ETA ${Formatters.eta(item.etaSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(PRIORITY.getOrElse(item.priority) { "Normal" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onTogglePause) {
                    Text(
                        when (item.status) {
                            DownloadStatus.PAUSED -> "Resume"
                            DownloadStatus.FAILED -> "Retry"
                            else -> "Pause"
                        },
                    )
                }
            }
            GradientBar(item.progress)
        }
    }
}

/** Poori hui download ki chhoti row. */
@Composable
fun CompletedRow(item: DownloadItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileTypeTile(item.name, sharedKey = "tile-${item.id}")
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Formatters.bytes(item.totalBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

/** Patli gradient progress bar. */
@Composable
private fun GradientBar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(RoundedCornerShape(6.dp)).background(VegamGradient))
    }
}

private fun subtitle(i: DownloadItem): String = when {
    i.status == DownloadStatus.FAILED -> i.error ?: "Download fail ho gayi"
    i.totalBytes > 0 -> "${Formatters.bytes(i.downloadedBytes)} / ${Formatters.bytes(i.totalBytes)}"
    i.downloadedBytes > 0 -> "${Formatters.bytes(i.downloadedBytes)} (size pata nahi)"
    else -> "Size pata kar rahe hain..."
}

@Composable
private fun subtitleColor(i: DownloadItem) =
    if (i.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

private fun statusLabel(i: DownloadItem) = when (i.status) {
    DownloadStatus.QUEUED -> "Waiting"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.FAILED -> "Failed"
    else -> ""
}
