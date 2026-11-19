package com.vegam.feature.smart

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val TEST_URL = "https://example.com/vegam-test.zip"

/** Browser ke hisaab se asli raasta. "Verify" = device par test karna baaki hai, claim nahi kiya. */
data class BrowserGuide(val name: String, val status: String, val steps: List<String>)

val BROWSER_GUIDES = listOf(
    BrowserGuide("Chrome / Edge / Brave", "Sirf Share ya link copy (seedha intercept nahi hota)", listOf(
        "Download wale link par long-press → \"Share link\" → Vegam.",
        "Ya link copy karo, phir Vegam kholo: \"Download?\" dialog khud aayega (Clipboard Detection ON ho).",
        "Chrome apne downloads khud sambhalta hai: Android is par Vegam ko beech mein aane nahi deta.",
    )),
    BrowserGuide("Firefox", "Verify karna baaki hai (device par)", listOf(
        "Link long-press → Share → Vegam sabse pakka tareeka hai.",
        "Kuch Firefox versions mein download ke baad \"Open with\" milta hai: uska behaviour version par depend karta hai, hum guarantee nahi dete.",
    )),
    BrowserGuide("Samsung Internet", "Verify karna baaki hai (device par)", listOf(
        "Link long-press → Share → Vegam.",
        "Samsung ke settings mein external download manager ka option version-wise alag hota hai; hum ise automatic claim nahi karte.",
    )),
    BrowserGuide("Doosre apps (Telegram, WhatsApp, files apps)", "Open with / Share kaam karta hai", listOf(
        "Link tap karo → \"Open with\" mein Vegam chuno (jab app link ko system ko deta hai).",
        "Warna Share → Vegam.",
    )),
)

/** Browser Integration screen: instructions + Test Integration. Vegam kisi browser ki setting khud nahi badalta. */
@Composable
fun BrowserSetupScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var testResult by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Wapas") }
        Text("Browser Integration", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Android kisi app ko dusre browser ke downloads zabardasti lene nahi deta. Vegam jo asli mein kar sakta hai: Share, Open with, aur copy kiya link. " +
                "Vegam kisi browser ki setting chupke se nahi badalta.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BROWSER_GUIDES.forEach { g ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(g.name, fontWeight = FontWeight.SemiBold)
                    Text(g.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    g.steps.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Test Integration", fontWeight = FontWeight.SemiBold)
                Text("Check karta hai ki Android Vegam ko file-links ke liye handler ki tarah jaanta hai ya nahi. Ye browser ka test nahi, system ka hai.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { testResult = runTest(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Test chalao") }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { testResult = "Koi app is link ko khol nahi saka." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Sample .zip link kholo (chooser mein Vegam dikhna chahiye)") }
            }
        }
        OutlinedButton(onClick = {
            val pkg = Uri.parse("package:${ctx.packageName}")
            val i = if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, modifier = Modifier.fillMaxWidth()) { Text("System settings: Open by default") }
        Text("Ye sirf system ki settings kholta hai; Android browser downloads ke liye \"default download manager\" ka option deta hi nahi.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun runTest(ctx: android.content.Context): String {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)).addCategory(Intent.CATEGORY_BROWSABLE)
    val handlers = pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
    val mine = handlers.any { it.activityInfo.packageName == ctx.packageName }
    val others = handlers.count { it.activityInfo.packageName != ctx.packageName }
    return if (mine) "✓ Vegam .zip jaise file-links ke liye handler ke roop mein registered hai ($others aur apps bhi). Browser ka behaviour alag hai: upar dekho."
    else "✗ Android ne Vegam ko is link ke handler mein nahi dikhaya. Share → Vegam phir bhi chalega."
}
