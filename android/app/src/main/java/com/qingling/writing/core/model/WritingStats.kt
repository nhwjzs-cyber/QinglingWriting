package com.qingling.writing.core.model
import com.qingling.writing.core.database.entities.WritingDayEntity
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class WritingStats(val todayNet: Long, val weekNet: Long, val streak: Int)
fun writingStats(rows: List<WritingDayEntity>, today: LocalDate): WritingStats {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
    val key = today.toString()
    val active = rows.filter { it.edits > 0 }.map { it.day }.toSet()
    var date = if (key in active) today else today.minusDays(1)
    var streak = 0
    while (date.toString() in active) { streak++; date = date.minusDays(1) }
    return WritingStats(rows.firstOrNull { it.day == key }?.netWords ?: 0,
        rows.filter { it.day >= start && it.day <= key }.sumOf { it.netWords },streak)
}
