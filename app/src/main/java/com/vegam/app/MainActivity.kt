package com.vegam.app

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.vegam.core.download.ThemeMode
import com.vegam.core.ui.VegamTheme
import com.vegam.core.ui.vegamBackground
import com.vegam.engine.extractUrl
import com.vegam.engine.isLikelyFileUrl
import com.vegam.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var sharedUrl by mutableStateOf<String?>(null)
    private var clipUrl by mutableStateOf<String?>(null)
    private var reopenUrl by mutableStateOf<String?>(null)
    @javax.inject.Inject lateinit var smart: com.vegam.core.download.SmartCoordinator
    private var lastClip: String? = null   // ek hi link baar baar na poochhein

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash tab tak jab tak settings (theme + onboarding flag) load na ho: blank/onboarding ka flash nahi.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge()
        if (savedInstanceState == null) readShare(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            val s = settings
            val dark = when (s?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            VegamTheme(darkTheme = dark, dynamicColor = s?.dynamicColor ?: false) {
                when {
                    s == null -> Box(Modifier.fillMaxSize().vegamBackground())      // settings load ho rahi hain
                    !s.onboardingDone -> OnboardingScreen(onFinished = {})          // flag badalte hi NavHost aa jata hai
                    else -> VegamNavHost(sharedUrl = sharedUrl, onSharedConsumed = { sharedUrl = null }, clipUrl = clipUrl, onClipHandled = { clipUrl = null }, smartOn = s.smartAnalysis, reopenUrl = reopenUrl, onReopenHandled = { reopenUrl = null })
                }
            }
        }
    }

    // launchMode=singleTop: app khula ho tab bhi naya Share yahin aata hai.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }

    override fun onStart() { super.onStart(); smart.setForeground(true) }
    override fun onStop() { smart.setForeground(false); super.onStop() }

    // Clipboard Android 10+ par sirf tab padh sakte hain jab app focus mein ho: isliye yahan.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    private fun checkClipboard() {
        val s = vm.settings.value ?: return
        if (!s.clipboardWatch || !s.onboardingDone) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val text = runCatching { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull()
        val url = extractUrl(text)?.takeIf { isLikelyFileUrl(it) } ?: return
        if (url == lastClip || url == sharedUrl) return
        lastClip = url
        clipUrl = url
    }

    /** Share (ACTION_SEND text) aur "Open with" (ACTION_VIEW http link) dono se link nikalo. */
    private fun readShare(i: Intent?) {
        i?.getStringExtra(com.vegam.core.download.SmartCoordinator.EXTRA_REOPEN_URL)?.let { reopenUrl = it; i.removeExtra(com.vegam.core.download.SmartCoordinator.EXTRA_REOPEN_URL); return }
        val url = when (i?.action) {
            Intent.ACTION_SEND -> extractUrl(i.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> i.dataString?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            else -> null
        }
        if (url != null) { lastClip = url; sharedUrl = url }
    }
}
