package com.vegam.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ek speed line: (x0,y0) -> (x1,y1), 0..1 box mein. */
data class LogoLine(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** Meeting point: teeno lines yahin milti hain, V ka neeche ka nok. */
const val LOGO_APEX_X = 0.5f
const val LOGO_APEX_Y = 0.86f

/**
 * Logo = "V" jo 3 converging speed lines se bana hai: do tirchhi (V ki bhujaayen) aur beech ki chhoti seedhi line.
 * Teeno ek hi nok (apex) par milti hain, isliye ye "speed" aur "V" dono padhta hai.
 */
val LOGO_LINES = listOf(
    LogoLine(0.10f, 0.14f, LOGO_APEX_X, LOGO_APEX_Y),
    LogoLine(0.90f, 0.14f, LOGO_APEX_X, LOGO_APEX_Y),
    LogoLine(LOGO_APEX_X, 0.38f, LOGO_APEX_X, LOGO_APEX_Y),
)

/** `draw` 0..1: lines upar se neeche apex ki taraf "kheenchti" hain (intro animation). 1 = poora logo. */
@Composable
fun VegamLogo(modifier: Modifier = Modifier, size: Dp = 96.dp, draw: Float = 1f) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.11f
        val brush = Brush.linearGradient(listOf(VegamIndigo, VegamCyan), Offset(0f, 0f), Offset(w, w))
        LOGO_LINES.forEach { l ->
            val ex = l.x0 + (l.x1 - l.x0) * draw
            val ey = l.y0 + (l.y1 - l.y0) * draw
            drawLine(brush, Offset(l.x0 * w, l.y0 * w), Offset(ex * w, ey * w), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

/** Screen khulte hi logo khud ko "draw" karta hai (onboarding, splash ke baad pehli jhalak). */
@Composable
fun AnimatedVegamLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(700)) }
    VegamLogo(modifier, size, t.value)
}
