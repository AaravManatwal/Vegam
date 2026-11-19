package com.vegam.feature.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.core.download.SmartSession
import com.vegam.core.ui.FileTypeTile
import com.vegam.engine.smart.AnalysisStep
import com.vegam.engine.smart.Compat
import com.vegam.engine.smart.RiskLevel

private fun stepText(s: AnalysisStep) = when (s) {
    AnalysisStep.PARSING -> "Link samajh raha hoon…"
    AnalysisStep.CONTACTING -> "Server se baat ho rahi hai…"
    AnalysisStep.READING_HEADERS -> "File ki jaankari padh raha hoon…"
    AnalysisStep.VALIDATING -> "Link jaanch raha hoon…"
    AnalysisStep.DONE -> "Taiyaar"
}

@Composable
private fun toneColor(t: Tone): Color = when (t) {
    Tone.OK -> MaterialTheme.colorScheme.primary
    Tone.WARN -> MaterialTheme.colorScheme.tertiary
    Tone.BAD -> MaterialTheme.colorScheme.error
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * "Download with Vegam?" dialog. Jab tak user Download na dabaye kuch download nahi hota.
 * RED = sirf band karna (koi bypass nahi). YELLOW/duplicate = Download ke baad ek aur confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDownloadSheet(
    onOpenManual: (String) -> Unit,
    onAdded: () -> Unit = {},
    vm: SmartViewModel = hiltViewModel(),
) {
    val session by vm.session.collectAsStateWithLifecycle()
    val confirming by vm.confirming.collectAsStateWithLifecycle()
    val s = session ?: return
    ModalBottomSheet(onDismissRequest = vm::cancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (s) {
                is SmartSession.Analyzing -> {
                    Text("Vegam se download?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(s.url, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stepText(s.step))
                    OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                is SmartSession.Ready -> ReadyContent(s, confirming, vm, onOpenManual, onAdded)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    s: SmartSession.Ready, confirming: Boolean, vm: SmartViewModel,
    onOpenManual: (String) -> Unit, onAdded: () -> Unit,
) {
    val r = s.result
    val rep = r.report
    var advanced by remember(s.url) { mutableStateOf(false) }

    if (confirming) {
        Text("Pakka download karein?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        r.decision.extraConfirm.forEach { Text("• $it") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) { Text("Wapas") }
            Button(
                onClick = { vm.onDownload(s, onAdded) }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) { Text("Phir bhi download") }
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        FileTypeTile(rep.fileName)
        Column(Modifier.weight(1f)) {
            Text(rep.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${rep.category.name.lowercase().replaceFirstChar { it.uppercase() }} · ${SmartText.sizeText(rep.sizeBytes)} · ${rep.host.orEmpty()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(rep.inputUrl, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SmartText.rows(r).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.label, modifier = Modifier.weight(0.32f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(row.value, modifier = Modifier.weight(0.68f), style = MaterialTheme.typography.bodyMedium, color = toneColor(row.tone))
        }
    }
    val warnings = SmartText.warnings(r)
    if (warnings.isNotEmpty()) {
        Text("Dhyan dein", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }

    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "ADVANCED DETAILS ▲" else "ADVANCED DETAILS ▼") }
    if (advanced) SmartText.advanced(r).forEach { (k, v) ->
        Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val blocked = !r.decision.canDownload
    if (blocked) {
        Text(r.decision.blockReason ?: "Download possible nahi", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
    }
    r.alternative?.let { alt ->
        Text(alt.explanation, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { vm.cancel(); vm.submit(alt.url, s.source) }, modifier = Modifier.fillMaxWidth()) { Text(alt.title) }
    }
    // RED par koi bypass nahi. Baaki "possible nahi" par purana Add screen (manual) dikhate hain, jo wahi engine use karta hai.
    if (blocked && r.security?.level != RiskLevel.RED && r.compatibility.level == Compat.NOT_POSSIBLE) {
        TextButton(onClick = { vm.cancel(); onOpenManual(s.url) }) { Text("Add screen mein kholo (manual)") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = vm::cancel, modifier = Modifier.weight(1f)) { Text(if (blocked) "Band karo" else "CANCEL") }
        if (!blocked) Button(onClick = { vm.onDownload(s, onAdded) }, modifier = Modifier.weight(1f)) { Text("DOWNLOAD") }
    }
}
