package com.qingling.writing
import com.qingling.writing.core.database.entities.WritingDayEntity
import com.qingling.writing.core.model.writingStats
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*

class WritingStatsTest {
    @Test fun noBackfilledActivityOrImaginaryStreak() {
        assertEquals(0,writingStats(emptyList(),LocalDate.parse("2026-11-02")).streak)
    }
    @Test fun mondayWeekBoundaryAndNegativeNetArePreserved() {
        val result = writingStats(listOf(WritingDayEntity("2026-11-01",100,1),WritingDayEntity("2026-11-02",-4,2)),LocalDate.parse("2026-11-02"))
        assertEquals(-4L,result.todayNet); assertEquals(-4L,result.weekNet); assertEquals(2,result.streak)
    }
    @Test fun yesterdayStreakDoesNotRequireTypingTodayAndZeroNetCanBeActive() {
        val rows = listOf(WritingDayEntity("2026-10-31",0,2),WritingDayEntity("2026-11-01",3,1))
        assertEquals(2,writingStats(rows,LocalDate.parse("2026-11-02")).streak)
        assertEquals(0,writingStats(rows,LocalDate.parse("2026-11-03")).streak)
    }
}
