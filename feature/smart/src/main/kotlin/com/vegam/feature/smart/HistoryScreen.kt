package com.vegam.feature.smart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.core.database.DetectionRow
import java.text.DateFormat
import java.util.Date

/** Detection History: sirf phone par. Search, filter, detail, ek-ek ya sab saaf. */
@Composable
fun HistoryScreen(contentPadding: PaddingValues, onBack: () -> Unit, vm: SmartViewModel = hiltViewModel()) {
    val rows by vm.history.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<DetectionRow?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("← Wapas") }
                TextButton(onClick = { confirmClear = true }, enabled = rows.isNotEmpty()) { Text("Sab saaf") }
            }
            Text("Detection History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Ye list sirf is phone par hai. Kahin bheji nahi jaati.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(query, { vm.query.value = it }, label = { Text("Naam ya domain khojo") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { vm.filter.value = f }, label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
        if (rows.isEmpty()) item { Text("Abhi kuch nahi.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rows, key = { it.d.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable { open = r }) {
                Column(Modifier.padding(14.dp)) {
                    Text(r.d.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.d.domain} · ${SmartText.sizeText(r.d.sizeBytes.takeIf { it > 0 })} · ${r.outcome()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }

    open?.let { r ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(r.d.fileName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "URL" to r.d.url, "Domain" to r.d.domain, "Type" to (r.d.mime ?: r.d.fileType),
                        "Size" to SmartText.sizeText(r.d.sizeBytes.takeIf { it > 0 }),
                        "Link" to "${r.d.linkState} ${r.d.linkDetail}".trim(), "Duplicate" to r.d.duplicate,
                        "Security" to r.d.securityLevel, "Source" to r.d.source, "Natija" to r.outcome(),
                        "Time" to DateFormat.getDateTimeInstance().format(Date(r.d.detectedAt)),
                    ).forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
                    if (r.d.warnings.isNotBlank()) Text("Chetavani:\n${r.d.warnings}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Band") } },
            dismissButton = { TextButton(onClick = { vm.delete(r.d.id); open = null }) { Text("Ye entry hatao") } },
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Poori history saaf?") },
        text = { Text("Sirf detection history hategi. Downloads aur files nahi.") },
        confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text("Saaf karo") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Rehne do") } },
    )
}
