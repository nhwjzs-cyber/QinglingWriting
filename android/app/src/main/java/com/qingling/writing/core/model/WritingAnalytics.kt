package com.qingling.writing.core.model

import com.qingling.writing.core.database.entities.BookWritingDayEntity
import com.qingling.writing.core.database.entities.WritingDayEntity
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

enum class ChartPeriod(val label: String) { DAY("近 7 天"),WEEK("近 8 周"),MONTH("近 6 月") }
data class WritingBucket(val label: String,val from: LocalDate,val through: LocalDate,val netWords: Long)
/** Whole weeks use seven days; month buckets use their actual covered dates. */
fun bucketGoal(bucket: WritingBucket, dailyGoal: Long, period: ChartPeriod): Double {
    val days = when(period) {
        ChartPeriod.DAY -> 1L
        ChartPeriod.WEEK -> 7L
        ChartPeriod.MONTH -> ChronoUnit.DAYS.between(bucket.from,bucket.through).coerceAtLeast(0) + 1
    }
    return dailyGoal.coerceAtLeast(0).toDouble() * days
}
fun activityHeatLevel(netWords:Long, edits:Long, dailyGoal:Long):Int {
    val goal=dailyGoal.coerceAtLeast(1)
    return when {
        netWords < 0 -> -1
        netWords == 0L -> if(edits>0) 1 else 0
        netWords <= goal/4 -> 1
        netWords <= goal/2 -> 2
        netWords < goal -> 3
        else -> 4
    }
}
data class CalendarActivity(val date: LocalDate,val netWords: Long,val edits: Long)

fun calendarActivity(rows: List<WritingDayEntity>,month: YearMonth): List<CalendarActivity> {
    val byDate = rows.groupBy { it.day }
    return (1..month.lengthOfMonth()).map { n ->
        val date = month.atDay(n);val found = byDate[date.toString()].orEmpty()
        CalendarActivity(date,found.sumOf { it.netWords },found.sumOf { it.edits })
    }
}
fun writingBuckets(rows: List<WritingDayEntity>,today: LocalDate,period: ChartPeriod): List<WritingBucket> {
    val ranges = when(period) {
        ChartPeriod.DAY -> (6 downTo 0).map { offset -> val date=today.minusDays(offset.toLong());Triple("${date.monthValue}/${date.dayOfMonth}",date,date) }
        ChartPeriod.WEEK -> (7 downTo 0).map { offset ->
            val start=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(offset.toLong())
            Triple("${start.monthValue}/${start.dayOfMonth}",start,minOf(start.plusDays(6),today))
        }
        ChartPeriod.MONTH -> (5 downTo 0).map { offset -> val month=YearMonth.from(today).minusMonths(offset.toLong())
            Triple("${month.monthValue}月",month.atDay(1),minOf(month.atEndOfMonth(),today)) }
    }
    return ranges.map { (label,from,through) -> WritingBucket(label,from,through,
        rows.filter { it.day >= from.toString() && it.day <= through.toString() }.sumOf { it.netWords }) }
}

data class BookForecast(val remaining: Long,val progress: Float,val averagePerDay: Double,
    val observedDays: Int,val estimatedDate: LocalDate?,val reached: Boolean)

fun bookForecast(currentWords: Long,target: Long,rows: List<BookWritingDayEntity>,today: LocalDate): BookForecast {
    if (target <= 0) return BookForecast(0,0f,0.0,0,null,false)
    val remaining = (target-currentWords.coerceAtLeast(0)).coerceAtLeast(0)
    val progress = (currentWords.toDouble()/target).coerceIn(0.0,1.0).toFloat()
    val recorded = rows.filter { it.edits > 0 && it.day <= today.toString() }
    val first = recorded.minOfOrNull { it.day }?.let(LocalDate::parse)
    val span = first?.let { (ChronoUnit.DAYS.between(it,today)+1).coerceIn(1,14).toInt() } ?: 0
    val start = today.minusDays((span-1).coerceAtLeast(0).toLong()).toString()
    val net = recorded.filter { it.day >= start }.sumOf { it.netWords }
    val average = if (span == 0) 0.0 else net.toDouble()/span
    val daysNeeded = if (average > 0) ceil(remaining/average) else Double.POSITIVE_INFINITY
    val estimate = if (remaining > 0 && daysNeeded in 1.0..36500.0) today.plusDays(daysNeeded.toLong()) else null
    return BookForecast(remaining,progress,average,span,estimate,remaining == 0L)
}
