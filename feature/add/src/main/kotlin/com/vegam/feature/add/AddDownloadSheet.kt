package com.vegam.feature.add

import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.core.ui.FileTypeTile
import com.vegam.core.ui.shimmer
import com.vegam.engine.Formatters
import com.vegam.engine.Schedule
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class When { NOW, HOUR, NIGHT, CUSTOM }

/**
 * Poora "Add download" sheet. `prefillUrl` = browser se Share kiya hua link.
 * Sheet khulte hi ViewModel reset hota hai, taaki pichhli baar ka data na dikhe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadSheet(
    prefillUrl: String?,
    onDismiss: () -> Unit,
    viewModel: AddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var whenChoice by remember { mutableStateOf(When.NOW) }

    LaunchedEffect(Unit) { viewModel.reset(prefillUrl) }

    // SAF folder picker: permission persist karna zaroori hai, warna app band hote hi access chala jaata hai.
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onFolderPicked(uri)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add download", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = state.url, onValueChange = viewModel::onUrlChange,
                label = { Text("Link paste karo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    TextButton(onClick = { clipboard.getText()?.text?.let(viewModel::onUrlChange) }) { Text("Paste") }
                },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )

            MetaCard(state, viewModel::onNameChange)

            // Connections
            Column {
                Text("Connections: ${state.segments}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = state.segments.toFloat(), onValueChange = { viewModel.onSegments(it.roundToInt()) },
                    valueRange = 1f..16f, steps = 14, enabled = state.rangeSupported,
                )
                if (!state.rangeSupported) {
                    Text("Server Range support nahi karta, isliye 1 connection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Priority
            Column {
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                val labels = listOf("Low", "Normal", "High")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = state.priority == i, onClick = { viewModel.onPriority(i) },
                            shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                        ) { Text(label) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sirf Wi-Fi par", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.wifiOnly, onCheckedChange = viewModel::onWifiOnly)
            }

            // Schedule
            Column {
                Text("Kab shuru karein", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = whenChoice == When.NOW, onClick = { whenChoice = When.NOW; viewModel.onSchedule(null) }, label = { Text("Abhi") })
                    FilterChip(
                        selected = whenChoice == When.HOUR,
                        onClick = { whenChoice = When.HOUR; viewModel.onSchedule(Schedule.inHours(System.currentTimeMillis(), 1)) },
                        label = { Text("1 ghante baad") },
                    )
                    FilterChip(
                        selected = whenChoice == When.NIGHT,
                        onClick = { whenChoice = When.NIGHT; viewModel.onSchedule(nextAt(2, 0)) },
                        label = { Text("Raat 2:00 baje") },
                    )
                    FilterChip(
                        selected = whenChoice == When.CUSTOM,
                        onClick = {
                            TimePickerDialog(context, { _, h, m -> whenChoice = When.CUSTOM; viewModel.onSchedule(nextAt(h, m)) }, 2, 0, false).show()
                        },
                        label = { Text("Time chunein") },
                    )
                }
                state.scheduledAt?.let {
                    Text(
                        "Shuru hoga: " + SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(Date(it)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Save location (SAF)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Save to", style = MaterialTheme.typography.labelLarge)
                    Text(state.folderLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.folderLabel != "App folder (private)") {
                    TextButton(onClick = viewModel::useAppFolder) { Text("App folder") }
                }
                TextButton(onClick = { folderPicker.launch(null) }) { Text("Change") }
            }

            Button(
                onClick = { viewModel.start(onAdded = onDismiss) }, enabled = state.canStart,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.scheduledAt != null) "Schedule download" else "Start download") }
        }
    }
}

private fun nextAt(hour: Int, minute: Int): Long =
    Schedule.nextOccurrence(System.currentTimeMillis(), ZoneId.systemDefault(), hour, minute)

/** Metadata: shimmer (loading) -> naam + size + Range support. Naam edit ho sakta hai. */
@Composable
private fun MetaCard(state: AddUiState, onName: (String) -> Unit) {
    when (val meta = state.meta) {
        MetaState.Idle -> Text(
            "Link daalte hi naam, size aur type yahan dikhega.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetaState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).shimmer())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(8.dp)).shimmer())
            }
        }
        is MetaState.Ready, is MetaState.Error -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileTypeTile(state.name.ifBlank { "x.bin" })
                OutlinedTextField(
                    value = state.name, onValueChange = onName, label = { Text("File ka naam") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
                )
            }
            if (meta is MetaState.Ready) {
                Text(
                    Formatters.bytes(meta.info.totalBytes) + " · " + if (meta.info.acceptRanges) "Multi-connection chalega" else "Sirf 1 connection",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (meta is MetaState.Error) {
                Text("Info nahi mili (${meta.message}). Phir bhi try kar sakte ho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
