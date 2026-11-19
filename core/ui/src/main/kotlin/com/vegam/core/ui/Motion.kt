package com.vegam.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween

// ---------- Haptics: do hi level, poori app mein ek jaisi ----------

/** tick = halka (toggle, pause/resume, FAB); confirm = bhaari (download complete, delete). */
class VegamHaptics(private val h: HapticFeedback) {
    fun tick() = h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun confirm() = h.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberVegamHaptics(): VegamHaptics {
    val h = LocalHapticFeedback.current
    return remember(h) { VegamHaptics(h) }
}

// ---------- Shared element: list ka tile -> Detail ka tile ----------

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> = compositionLocalOf { null }
val LocalAnimatedScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> = compositionLocalOf { null }

/**
 * Dono screens par same `key` wale element ek se doosre mein "ud" kar jaate hain (spring 0.6/300).
 * Scope na mile (preview, test) to chupchaap kuch nahi karta.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.vegamShared(key: String): Modifier {
    val st = LocalSharedTransitionScope.current ?: return this
    val av = LocalAnimatedScope.current ?: return this
    return with(st) {
        this@vegamShared.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = av,
            boundsTransform = { _, _ -> vegamSpring<Rect>() },
        )
    }
}

// ---------- Success check: download poora hone par ----------

/** Check ke 3 bindu (0..1 box). Alag rakha taaki test ho sake. */
val CHECK_POINTS = listOf(Offset(0.26f, 0.53f), Offset(0.43f, 0.69f), Offset(0.74f, 0.34f))

/**
 * Gradient circle spring se phoolta hai, phir safed tick "likha" jaata hai.
 * (Lottie ki jagah native: koi extra dependency ya JSON asset nahi, aur theme ke rang apne aap.)
 */
@Composable
fun SuccessCheck(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val pop = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, vegamSpring())
        tick.animateTo(1f, tween(320))
    }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawCircle(VegamGradient, radius = w / 2f * pop.value.coerceAtLeast(0f))
        val full = Path().apply {
            moveTo(CHECK_POINTS[0].x * w, CHECK_POINTS[0].y * w)
            lineTo(CHECK_POINTS[1].x * w, CHECK_POINTS[1].y * w)
            lineTo(CHECK_POINTS[2].x * w, CHECK_POINTS[2].y * w)
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * tick.value, part, true)
        drawPath(part, Color.White, style = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
