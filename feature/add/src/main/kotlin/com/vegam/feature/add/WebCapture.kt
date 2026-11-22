package com.vegam.feature.add

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.download.DownloadRepository
import com.vegam.engine.Formatters
import com.vegam.engine.smart.FileTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Page se pakdi hui file: WebView ne jo download shuru kiya uski jaankari + wahi browser session ke headers. */
data class CapturedFile(val url: String, val name: String, val sizeBytes: Long, val mime: String?, val headers: Map<String, String>)

/** Header banana: UA + Referer (page) + Cookie (is session ki). Pure function, test hota hai. */
fun captureHeaders(userAgent: String?, pageUrl: String?, cookies: String?): Map<String, String> = buildMap {
    if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
    if (!pageUrl.isNullOrBlank()) put("Referer", pageUrl)
    if (!cookies.isNullOrBlank()) put("Cookie", cookies)
}

@HiltViewModel
class CaptureViewModel @Inject constructor(private val repo: DownloadRepository) : ViewModel() {
    fun add(c: CapturedFile, onAdded: () -> Unit) {
        viewModelScope.launch {
            repo.add(c.url, c.name, headers = c.headers)
            onAdded()
        }
    }
}

/**
 * "Page kholo, asli download pakdo": jab link file nahi balki web page (download page / countdown / button) ho.
 * Page app ke andar khulta hai; user khud button dabata hai; jaise hi page file download shuru kare, Vegam use pakad leta hai
 * aur poochta hai. Vegam koi captcha/login bypass nahi karta: wo sab user khud karta hai.
 * Limits: blob:/data: downloads (page ke andar bani file) pakad nahi sakte; kuch hosts ke links ek baar hi chalte hain.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebCaptureScreen(startUrl: String, onBack: () -> Unit, onAdded: () -> Unit, vm: CaptureViewModel = hiltViewModel()) {
    var captured by remember { mutableStateOf<CapturedFile?>(null) }
    var unsupported by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf(startUrl) }
    var web by remember { mutableStateOf<WebView?>(null) }

    BackHandler { val w = web; if (w != null && w.canGoBack()) w.goBack() else onBack() }
    DisposableEffect(Unit) { onDispose { web?.apply { stopLoading(); destroy() }; web = null } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBack) { Text("← Wapas") }
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            }
            Text(
                "Page ka download button dabao. Jaise hi file shuru hogi, Vegam pakad lega.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true          // download pages ko chahiye (countdown/button)
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) { loading = true }
                            override fun onPageFinished(v: WebView, url: String?) { loading = false; title = v.title?.ifBlank { null } ?: url ?: startUrl }
                        }
                        setDownloadListener { url, userAgent, disposition, mime, length ->
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                unsupported = true                  // blob:/data: : page ke andar bani file
                            } else {
                                val cookies = CookieManager.getInstance().getCookie(url)
                                captured = CapturedFile(
                                    url = url, name = FileTypes.resolveName(url, disposition), sizeBytes = length, mime = mime,
                                    headers = captureHeaders(userAgent, this.url, cookies),
                                )
                            }
                        }
                        web = this
                        loadUrl(startUrl)
                    }
                },
            )
        }
    }

    captured?.let { c ->
        AlertDialog(
            onDismissRequest = { captured = null },
            title = { Text("Vegam se download?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, fontWeight = FontWeight.SemiBold)
                    Text(if (c.sizeBytes > 0) Formatters.bytes(c.sizeBytes) else "Size pata nahi", style = MaterialTheme.typography.bodySmall)
                    Text(c.url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { captured = null; vm.add(c, onAdded) }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { captured = null }) { Text("Cancel") } },
        )
    }
    if (unsupported) AlertDialog(
        onDismissRequest = { unsupported = false },
        title = { Text("Ye file pakad nahi sakte") },
        text = { Text("Is page ne file page ke andar hi banayi (blob). Vegam ise nahi utha sakta: browser se download karo.") },
        confirmButton = { TextButton(onClick = { unsupported = false }) { Text("Theek hai") } },
    )
}
