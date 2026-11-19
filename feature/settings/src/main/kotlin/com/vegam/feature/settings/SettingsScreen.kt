package com.vegam.feature.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.core.download.ThemeMode

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenBrowserSetup: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val s = ui.settings
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onFolderPicked(uri)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            Section("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(selected = s.themeMode == m, onClick = { viewModel.setTheme(m) }, label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                // Material You sirf Android 12+ par hota hai; purane phone par option dikhana jhooth hoga.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ToggleRow("Dynamic color", "Wallpaper ke rang (Material You). Off = Vegam ka indigo-cyan.", s.dynamicColor, viewModel::setDynamic)
                }
            }
        }

        item {
            Section("Downloads") {
                SliderRow("Ek saath kitni downloads", "${s.maxParallel}", s.maxParallel.toFloat(), 1f..10f, 8) { viewModel.setMaxParallel(it.toInt()) }
                SliderRow("Default connections", "${s.defaultSegments}", s.defaultSegments.toFloat(), 1f..16f, 14) { viewModel.setSegments(it.toInt()) }
                SliderRow(
                    "Speed limit", speedLabel(s.speedLimitMbps), stepIndexOf(s.speedLimitMbps).toFloat(),
                    0f..(SPEED_STEPS.size - 1).toFloat(), SPEED_STEPS.size - 2,
                ) { viewModel.setSpeedLimit(SPEED_STEPS[it.toInt()]) }
                ToggleRow("Naye downloads: Wi-Fi only", "Add screen mein default ON aayega.", s.wifiOnlyDefault, viewModel::setWifiDefault)
                ToggleRow("Sirf charging par", "Nayi starts par lagu hota hai.", s.chargingOnly, viewModel::setCharging)
            }
        }

        item {
            Section("Smart Detection Settings") {
                ToggleRow(
                    "Clipboard Detection",
                    "File ka link copy karo to Vegam khulte hi \"Download?\" poochhe. Android 12+ par 'clipboard padha' ka chhota message aa sakta hai.",
                    s.clipboardWatch, viewModel::setClipboard,
                )
                OutlinedButton(onClick = onOpenBrowserSetup) { Text("Browser Integration (Configure)") }
                ToggleRow("Smart Link Analysis", "Download se pehle size, type, server jaanchna (HEAD / chhota Range request). Band = sirf link ka naam.", s.smartAnalysis, viewModel::setSmartAnalysis)
                ToggleRow("Duplicate Detection", "Pehle se download ya chal rahi file pakadna (sirf naam se nahi).", s.duplicateDetection, viewModel::setDuplicate)
                ToggleRow("Link Validation", "Link chal raha hai ya nahi, server ke asli jawab se.", s.linkValidation, viewModel::setValidation)
                ToggleRow("Security Check", "Phone par hi simple niyam (double extension, HTTP, nakli domain...). Ye antivirus nahi hai.", s.securityCheck, viewModel::setSecurity)
                ToggleRow("Download Notifications", "Vegam background mein ho aur jaanch poori ho to notification.", s.smartNotifications, viewModel::setSmartNotif)
                ToggleRow("Detection History", "Jaanche gaye links phone par save. Band = kuch save nahi.", s.detectionHistory, viewModel::setHistory)
                OutlinedButton(onClick = onOpenHistory) { Text("History dekho") }
                ToggleRow("Background Analysis", "App background mein jaye to bhi jaanch poori ho. Band = background mein jaate hi ruk jaati hai.", s.backgroundAnalysis, viewModel::setBackground)
            }
        }

        item {
            Section("Storage") {
                Text("Save folder", fontWeight = FontWeight.SemiBold)
                Text(ui.folderLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(null) }) { Text("Folder chuno") }
                    OutlinedButton(onClick = viewModel::useAppFolder) { Text("App folder") }
                }
            }
        }

        item {
            Section("About") {
                OutlinedButton(onClick = viewModel::replayOnboarding) { Text("Onboarding dobara dekho") }
                Text("Vegam · वेगम् · v0.4", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("Powered by VegamEngine", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        // Local value: drag smooth rahe; DataStore mein sirf ungli uthne par likhte hain.
        var drag by remember(current) { mutableFloatStateOf(current) }
        Slider(value = drag, onValueChange = { drag = it }, onValueChangeFinished = { onChange(drag) }, valueRange = range, steps = steps)
    }
}
