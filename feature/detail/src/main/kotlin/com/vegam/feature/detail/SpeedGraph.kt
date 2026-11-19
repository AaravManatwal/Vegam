package com.vegam.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.vegam.core.ui.VegamCyan
import com.vegam.core.ui.VegamIndigo
import com.vegam.engine.Formatters

/** Pichhle 60 second ka line chart: gradient line, halka area fill, 3 grid lines, end par dot. */
@Composable
fun SpeedGraph(samples: List<Long>, modifier: Modifier = Modifier) {
    val ymax = niceMax(samples.maxOrNull() ?: 0L)
    val grid = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(Formatters.speed(ymax), style = MaterialTheme.typography.labelSmall, color = muted)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            for (k in 0..2) {
                val y = h * k / 2f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            val n = samples.size
            if (n < 2) return@Canvas
            // Sample 60 slots mein right-align: naya data hamesha daayein, purana baayein.
            fun x(i: Int) = w * (i + (60 - n)) / 59f
            fun y(v: Long) = h - (v.toFloat() / ymax).coerceIn(0f, 1f) * h
            val line = Path().apply {
                moveTo(x(0), y(samples[0]))
                for (i in 1 until n) lineTo(x(i), y(samples[i]))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(n - 1), h)
                lineTo(x(0), h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(VegamCyan.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(
                line, Brush.horizontalGradient(listOf(VegamIndigo, VegamCyan), startX = 0f, endX = w),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(VegamCyan, 4.dp.toPx(), Offset(x(n - 1), y(samples.last())))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60s ago", style = MaterialTheme.typography.labelSmall, color = muted)
            Text(if (samples.size < 2) "speed data jama ho raha hai..." else "now", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
