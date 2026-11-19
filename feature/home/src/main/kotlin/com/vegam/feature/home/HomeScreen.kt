package com.vegam.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.core.download.DownloadItem
import com.vegam.core.ui.VegamGradient
import com.vegam.core.ui.VegamLogo
import com.vegam.core.ui.rememberVegamHaptics
import com.vegam.core.ui.vegamSpring
import com.vegam.engine.Formatters

@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberVegamHaptics()

    // Koi download poori hui: bhaari haptic. Pehli baar (screen khulte hi) nahi, sirf naya completion.
    var seen by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.completed.size) {
        if (seen != -1 && state.completed.size > seen) haptics.confirm()
        seen = state.completed.size
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,   // FAB ke neeche jagah
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header() }
        item(key = "stats") { StatsCard(state.stats) }
        item(key = "search") {
            OutlinedTextField(
                value = state.query, onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search downloads") }, singleLine = true,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "h-active") { SectionTitle("Chalu downloads (${state.active.size})") }
        if (state.active.isEmpty()) {
            item(key = "empty-active") { EmptyBox("Koi active download nahi.\n+ dabao aur link paste karo.") }
        }
        items(state.active, key = { it.id }) { d ->
            SwipeToDelete(onDelete = { viewModel.delete(d.id) }, modifier = Modifier.animateItem(placementSpec = vegamSpring())) {
                ActiveDownloadCard(item = d, onTogglePause = { haptics.tick(); viewModel.togglePause(d) }, onClick = { onOpenDetail(d.id) })
            }
        }
        item(key = "h-done") { SectionTitle("Poore hue (${state.completed.size})") }
        if (state.completed.isEmpty()) {
            item(key = "empty-done") { EmptyBox("Abhi koi download complete nahi hui.") }
        }
        items(state.completed, key = { it.id }) { d ->
            SwipeToDelete(onDelete = { viewModel.delete(d.id) }, modifier = Modifier.animateItem(placementSpec = vegamSpring())) { CompletedRow(d, onClick = { onOpenDetail(d.id) }) }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VegamLogo(size = 40.dp)
        Column {
            Text(
                "वेगम्", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("हर डाउनलोड, वेग से।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsCard(stats: HomeStats) {
    Card(
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Total downloaded", Formatters.bytes(stats.totalBytes), Modifier.weight(1f))
            Stat("Speed now", Formatters.speed(stats.speedNow), Modifier.weight(1f))
            Stat("Storage used", Formatters.bytes(stats.storageBytes), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, top = 8.dp),
    )
}

@Composable
private fun EmptyBox(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Left swipe = delete (+ haptic). Right swipe band hai taaki galti se na ho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state, modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.error).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White) }
        },
        content = { content() },
    )
}
