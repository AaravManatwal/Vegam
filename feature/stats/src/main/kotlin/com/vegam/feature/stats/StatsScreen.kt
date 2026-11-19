package com.vegam.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.core.ui.NumberTicker
import com.vegam.core.ui.VegamCyan
import com.vegam.core.ui.VegamIndigo
import com.vegam.core.ui.fileKindOf
import com.vegam.engine.Formatters

@Composable
fun StatsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Panel {
                Text("TOTAL DOWNLOADED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberTicker(text = Formatters.bytes(s.totalBytes), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Mini("Files", s.completedCount.toString(), Modifier.weight(1f))
                    Mini("Aaj", Formatters.bytes(s.todayBytes), Modifier.weight(1f))
                    Mini("Average", Formatters.bytes(s.averageBytes), Modifier.weight(1f))
                    Mini("Failed", s.failedCount.toString(), Modifier.weight(1f))
                }
            }
        }
        item {
            Panel {
                Text("Pichhle 7 din", fontWeight = FontWeight.Bold)
                WeekBars(s.week)
            }
        }
        item {
            Panel {
                Text("File type ke hisaab se", fontWeight = FontWeight.Bold)
                if (s.types.isEmpty()) {
                    Text("Abhi koi download complete nahi.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                } else TypeBreakdown(s.types)
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 7 bars, aaj wala gradient mein, baaki halke. Bars screen khulte hi upar uthte hain. */
@Composable
private fun WeekBars(week: List<DayBar>) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, com.vegam.core.ui.vegamSpring()) }
    val max = (week.maxOfOrNull { it.bytes } ?: 0L).coerceAtLeast(1L)
    val faint = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp)) {
        if (week.isEmpty()) return@Canvas
        val slot = size.width / week.size
        val barW = slot * 0.55f
        week.forEachIndexed { i, d ->
            val h = (d.bytes.toFloat() / max * size.height * grow.value).coerceAtLeast(if (d.bytes > 0) 4.dp.toPx() else 2.dp.toPx())
            val x = i * slot + (slot - barW) / 2
            val brush = if (d.isToday) Brush.verticalGradient(listOf(VegamCyan, VegamIndigo), startY = size.height - h, endY = size.height) else Brush.linearGradient(listOf(faint, faint))
            drawRoundRect(brush, Offset(x, size.height - h), Size(barW, h), CornerRadius(8.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        week.forEach { d ->
            Text(
                d.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = if (d.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (d.isToday) FontWeight.ExtraBold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TypeBreakdown(types: List<TypeShare>) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
        types.forEach { t ->
            Box(Modifier.weight(t.fraction.coerceAtLeast(0.02f)).fillMaxSize().background(colorFor(t.label)))
        }
    }
    types.forEach { t ->
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(colorFor(t.label), CircleShape))
            Text(t.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${Formatters.bytes(t.bytes)} · ${(t.fraction * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Label se wahi rang jo file tile mein hai (fileKindOf ek sample naam se). */
private fun colorFor(label: String) = fileKindOf("x." + when (label) {
    "ISO" -> "iso"; "VID" -> "mp4"; "ZIP" -> "zip"; "APK" -> "apk"; "PDF" -> "pdf"; "AUD" -> "mp3"; "IMG" -> "jpg"; else -> "bin"
}).color
