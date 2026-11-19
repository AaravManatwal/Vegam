package com.vegam.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vegam.core.ui.AnimatedVegamLogo
import com.vegam.core.ui.VegamGradient
import com.vegam.core.ui.vegamBackground
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ICONS: List<ImageVector> = listOf(Icons.Filled.Bolt, Icons.Filled.Restore, Icons.Filled.Schedule)

/** 3 parallax pages + aakhri page par notification permission card. `onFinished` = Home par jao. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val total = ONBOARDING_PAGES.size
    val pager = rememberPagerState(pageCount = { total })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    fun done() { viewModel.finish(); onFinished() }

    Column(Modifier.fillMaxSize().vegamBackground().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (showSkip(page, total)) TextButton(onClick = ::done) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            // Parallax: illustration aur text alag raftaar se khiskte hain.
            val offset = (pager.currentPage - i) + pager.currentPageOffsetFraction
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.graphicsLayer { translationX = offset * size.width * 0.35f; alpha = 1f - abs(offset).coerceIn(0f, 1f) * 0.6f }) {
                    Art(i)
                }
                Column(
                    Modifier.padding(top = 36.dp).graphicsLayer { translationX = offset * size.width * 0.12f; alpha = 1f - abs(offset).coerceIn(0f, 1f) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(ONBOARDING_PAGES[i].title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(ONBOARDING_PAGES[i].body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (i == total - 1) PermissionCard()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(total) { d ->
                    Box(Modifier.size(width = if (d == page) 22.dp else 8.dp, height = 8.dp).background(if (d == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                }
            }
            Button(onClick = { if (page >= total - 1) done() else scope.launch { pager.animateScrollToPage(page + 1) } }, shape = MaterialTheme.shapes.medium) {
                Text(nextLabel(page, total))
            }
        }
    }
}

@Composable
private fun Art(index: Int) {
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(VegamGradient, alpha = 0.18f) }
        if (index == 0) {
            // Pehle page par asli logo khud ko "draw" karta hai.
            AnimatedVegamLogo(size = 120.dp)
        } else {
            Box(Modifier.size(112.dp).background(VegamGradient, CircleShape), contentAlignment = Alignment.Center) {
                Icon(ICONS[index], contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
    }
}

/** Notification permission sirf Android 13+ par maangi jaati hai; neeche wale par card hi nahi dikhta. */
@Composable
private fun PermissionCard() {
    if (Build.VERSION.SDK_INT < 33) return
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }
    Card(
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Notifications", fontWeight = FontWeight.Bold)
            Text("Progress, speed aur Pause button notification mein dikhane ke liye.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (asked) Text("Ho gaya ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            else OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
        }
    }
}
