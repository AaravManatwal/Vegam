package com.vegam.feature.stats

import com.vegam.core.database.DownloadStatus
import com.vegam.core.download.DownloadItem
import java.time.Instant
import java.time.ZoneId

data class DayBar(val label: String, val bytes: Long, val isToday: Boolean)
data class TypeShare(val label: String, val bytes: Long, val fraction: Float)

data class StatsUi(
    val totalBytes: Long = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val todayBytes: Long = 0,
    val averageBytes: Long = 0,
    val week: List<DayBar> = emptyList(),
    val types: List<TypeShare> = emptyList(),
)

/**
 * Pure function: items -> Stats screen. Sirf COMPLETED files gini jaati hain (adhoori/cancel nahi).
 * Hafte ka bar `completedAt` ke local din se; `zone` parameter taaki test kisi bhi timezone mein same chale.
 * `kindOf` = file name -> "VID"/"ZIP"... (UI module se aata hai, yahan pure rakha).
 */
fun buildStats(items: List<DownloadItem>, nowMs: Long, zone: ZoneId, kindOf: (String) -> String): StatsUi {
    val done = items.filter { it.status == DownloadStatus.COMPLETED }
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val perDay = done.filter { it.completedAt != null }
        .groupBy { Instant.ofEpochMilli(it.completedAt!!).atZone(zone).toLocalDate() }
        .mapValues { (_, v) -> v.sumOf { it.totalBytes } }
    val week = days.map { d ->
        DayBar(d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, perDay[d] ?: 0L, d == today)
    }
    val total = done.sumOf { it.totalBytes }
    val byType = done.groupBy { kindOf(it.name) }.mapValues { (_, v) -> v.sumOf { it.totalBytes } }
        .entries.sortedByDescending { it.value }
    val types = byType.map { TypeShare(it.key, it.value, if (total > 0) it.value.toFloat() / total else 0f) }
    return StatsUi(
        totalBytes = total,
        completedCount = done.size,
        failedCount = items.count { it.status == DownloadStatus.FAILED },
        todayBytes = perDay[today] ?: 0L,
        averageBytes = if (done.isEmpty()) 0 else total / done.size,
        week = week,
        types = types,
    )
}
