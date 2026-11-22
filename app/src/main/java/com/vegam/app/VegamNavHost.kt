package com.vegam.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vegam.core.ui.LocalAnimatedScope
import com.vegam.core.ui.LocalSharedTransitionScope
import com.vegam.core.ui.VegamGradient
import com.vegam.core.ui.rememberVegamHaptics
import com.vegam.core.ui.vegamBackground
import com.vegam.engine.UrlNames
import com.vegam.feature.add.AddDownloadSheet
import com.vegam.feature.add.WebCaptureScreen
import com.vegam.feature.detail.DetailScreen
import com.vegam.feature.home.HomeScreen
import com.vegam.feature.settings.SettingsScreen
import com.vegam.feature.stats.StatsScreen
import com.vegam.core.download.SmartSource
import com.vegam.feature.smart.BrowserSetupScreen
import com.vegam.feature.smart.HistoryScreen
import com.vegam.feature.smart.SmartDownloadSheet
import com.vegam.feature.smart.SmartViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("stats", "Stats", Icons.Outlined.BarChart),
    Tab("settings", "Settings", Icons.Outlined.Settings),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VegamNavHost(
    sharedUrl: String? = null,
    onSharedConsumed: () -> Unit = {},
    clipUrl: String? = null,
    onClipHandled: () -> Unit = {},
    smartOn: Boolean = true,
    smartVm: SmartViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    reopenUrl: String? = null,
    onReopenHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var showAdd by remember { mutableStateOf(false) }
    var captureUrl by remember { mutableStateOf<String?>(null) }
    val haptics = rememberVegamHaptics()
    var prefill by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }

    // Clipboard mein file ka link mila: snackbar "Download" ke saath. Action dabaya to Add sheet link ke saath khulti hai.
    LaunchedEffect(clipUrl) {
        val url = clipUrl ?: return@LaunchedEffect
        if (smartOn) { smartVm.submit(url, SmartSource.CLIPBOARD); onClipHandled(); return@LaunchedEffect }
        val r = snack.showSnackbar(
            message = "Link copied: ${UrlNames.fileName(url)}", actionLabel = "Download",
            withDismissAction = true, duration = SnackbarDuration.Long,
        )
        if (r == SnackbarResult.ActionPerformed) { prefill = url; showAdd = true }
        onClipHandled()
    }

    // Dusre app se "Share → Vegam" aaye to Add sheet link ke saath khul jaye.
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true }
            if (smartOn) smartVm.submit(sharedUrl, SmartSource.SHARE)
            else { prefill = sharedUrl; showAdd = true }
            onSharedConsumed()
        }
    }

    // Notification tap: taiyaar nateeja dialog mein wapas.
    LaunchedEffect(reopenUrl) {
        val u = reopenUrl ?: return@LaunchedEffect
        smartVm.reopen(u); onReopenHandled()
    }

    Scaffold(
        modifier = Modifier.vegamBackground(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route || (tab.route == "home" && route?.startsWith("detail") == true) || (tab.route == "settings" && (route == "history" || route == "browser-setup")),
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (route == "home") {
                FloatingActionButton(
                    onClick = { haptics.tick(); prefill = null; showAdd = true },
                    containerColor = Color.Transparent,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.background(VegamGradient, MaterialTheme.shapes.large),
                ) { Icon(Icons.Filled.Add, contentDescription = "Add download", tint = Color.White) }
            }
        },
    ) { padding ->
        // Shared element: card ka file tile -> Detail ka tile "ud" kar jaata hai. Scopes CompositionLocal se niche jaate hain.
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        CompositionLocalProvider(LocalAnimatedScope provides this) {
                            HomeScreen(onOpenDetail = { id -> nav.navigate("detail/$id") }, contentPadding = padding)
                        }
                    }
                    composable("stats") { StatsScreen(contentPadding = padding) }
                    composable("settings") {
                        SettingsScreen(
                            contentPadding = padding,
                            onOpenBrowserSetup = { nav.navigate("browser-setup") },
                            onOpenHistory = { nav.navigate("history") },
                        )
                    }
                    composable("browser-setup") { BrowserSetupScreen(contentPadding = padding, onBack = { nav.popBackStack() }) }
                    composable("history") { HistoryScreen(contentPadding = padding, onBack = { nav.popBackStack() }) }
                    composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        CompositionLocalProvider(LocalAnimatedScope provides this) {
                            DetailScreen(onBack = { nav.popBackStack() }, contentPadding = padding)
                        }
                    }
                }
            }
        }
    }

    SmartDownloadSheet(onOpenManual = { url -> prefill = url; showAdd = true }, onOpenCapture = { captureUrl = it })

    if (showAdd) {
        AddDownloadSheet(prefillUrl = prefill, onDismiss = { showAdd = false }, onOpenCapture = { showAdd = false; captureUrl = it })
    }

    // "Page kholo, asli download pakdo": poori screen par WebView.
    captureUrl?.let { u ->
        WebCaptureScreen(startUrl = u, onBack = { captureUrl = null }, onAdded = { captureUrl = null })
    }
}
