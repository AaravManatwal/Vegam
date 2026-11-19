package com.vegam.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spec: dampingRatio 0.6, stiffness 300 (zyadatar transitions ke liye). */
fun <T> vegamSpring() = spring<T>(dampingRatio = 0.6f, stiffness = 300f)

/** Button/card press par 0.95x scale, spring ke saath. `interactionSource` wahi do jo clickable/Button ko diya. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, vegamSpring(), label = "pressScale")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Progress ring: gradient (indigo -> cyan) arc. `active` = download chal raha hai: halka pulse glow.
 * Arc ka animation 150ms linear, taaki 500ms ke progress updates "smooth sweep" lagein.
 */
@Composable
fun GradientRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 6.dp,
    active: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(150, easing = LinearEasing), label = "ring")
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.10f, targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glowAlpha",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(strokeWidth)) {
            val sw = strokeWidth.toPx()
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
            if (animated > 0f) {
                // Gradient arc ke hisaab se compress: shuru indigo, progress par cyan.
                val brush = Brush.sweepGradient(
                    0f to VegamIndigo, animated.coerceAtLeast(0.02f) to VegamCyan, 1f to VegamCyan,
                    center = Offset(this.size.width / 2f, this.size.height / 2f),
                )
                rotate(-90f) {
                    if (active) {
                        drawArc(VegamCyan.copy(alpha = glow), 0f, 360f * animated, false, style = Stroke(sw * 1.8f, cap = StrokeCap.Round))
                    }
                    drawArc(brush, startAngle = 0f, sweepAngle = 360f * animated, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
        }
        content()
    }
}

/** Number ticker: har digit upar/neeche "roll" hota hai (speed counter). Non-digit (".", " ", "M") normal text. */
@Composable
fun NumberTicker(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    val tabular = style.copy(fontFeatureSettings = "tnum")
    Row(modifier) {
        text.forEachIndexed { index, ch ->
            // Key = end se position, taaki "9.9" -> "10.1" mein baaki digits ki jagah na khiske.
            key(text.length - index) {
                if (ch.isDigit()) {
                    AnimatedContent(
                        targetState = ch,
                        modifier = Modifier.clipToBounds(),
                        transitionSpec = {
                            // Digit badhe to neeche se upar, ghate to upar se neeche: counter jaisa mehsoos ho.
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInVertically(vegamSpring()) { dir * it } + fadeIn(tween(120))) togetherWith
                                (slideOutVertically(tween(180)) { -dir * it } + fadeOut(tween(120)))
                        },
                        label = "digit",
                    ) { Text(it.toString(), style = tabular) }
                } else {
                    Text(ch.toString(), style = tabular)
                }
            }
        }
    }
}

/** File type ka rangeen tile (ISO, VID, ZIP...). */
@Composable
fun FileTypeTile(fileName: String, modifier: Modifier = Modifier, size: Dp = 44.dp, sharedKey: String? = null) {
    val kind = fileKindOf(fileName)
    Box(
        (if (sharedKey != null) modifier.vegamShared(sharedKey) else modifier).size(size).clip(RoundedCornerShape(14.dp)).background(kind.color),
        contentAlignment = Alignment.Center,
    ) {
        Text(kind.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

data class FileKind(val label: String, val color: Color)

fun fileKindOf(fileName: String): FileKind = when (fileName.substringAfterLast('.', "").lowercase()) {
    "iso" -> FileKind("ISO", Color(0xFF6C5CE7))
    "mkv", "mp4", "webm", "avi", "mov" -> FileKind("VID", Color(0xFFE1306C))
    "zip", "rar", "7z", "tar", "gz" -> FileKind("ZIP", Color(0xFFF59E0B))
    "apk" -> FileKind("APK", Color(0xFF16A34A))
    "pdf" -> FileKind("PDF", Color(0xFFDC2626))
    "mp3", "flac", "wav", "m4a", "ogg" -> FileKind("AUD", Color(0xFF0EA5E9))
    "jpg", "jpeg", "png", "webp", "gif" -> FileKind("IMG", Color(0xFF8B5CF6))
    else -> FileKind("FILE", Color(0xFF64748B))
}

/** Shimmer: metadata aate waqt placeholder par chamakti patti (skeleton loading). */
@Composable
fun Modifier.shimmer(): Modifier {
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val high = MaterialTheme.colorScheme.outline
    return this.drawBehind {
        drawRect(
            Brush.linearGradient(
                listOf(base, high, base),
                start = Offset(size.width * x, 0f), end = Offset(size.width * (x + 1f), 0f),
            ),
        )
    }
}
