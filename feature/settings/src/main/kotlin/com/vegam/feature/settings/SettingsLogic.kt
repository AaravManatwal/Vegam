package com.vegam.feature.settings

/** Speed limit slider ke "gol" steps (MB/s). 0 = unlimited. Slider free number nahi, in steps par ruktaa hai. */
val SPEED_STEPS = listOf(0, 1, 2, 5, 10, 20, 50)

/** Saved value ko sabse paas wale step par le aao (purani/alag value par bhi slider sahi jagah dikhe). */
fun stepIndexOf(mbps: Int): Int =
    SPEED_STEPS.indices.minByOrNull { kotlin.math.abs(SPEED_STEPS[it] - mbps) } ?: 0

fun speedLabel(mbps: Int): String = if (mbps <= 0) "Unlimited" else "$mbps MB/s"
