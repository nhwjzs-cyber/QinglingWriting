package com.qingling.writing

import com.qingling.writing.core.database.entities.*
import com.qingling.writing.core.model.*
import com.qingling.writing.core.files.BackupCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.io.*

class WritingAnalyticsTest {
    private val today=LocalDate.parse("2026-11-02")
    private fun book(day: String,words: Long)=BookWritingDayEntity("b",day,words,1)
    @Test fun chartsUseCalendarBoundariesAndKeepNegativeNet() {
        val rows=listOf(WritingDayEntity("2026-10-01",100,1),WritingDayEntity("2026-11-01",10,1),WritingDayEntity("2026-11-02",-2,1),WritingDayEntity("2026-11-03",999,1))
        val daily=writingBuckets(rows,today,ChartPeriod.DAY)
        assertEquals(7,daily.size);assertEquals(-2L,daily.last().netWords)
        val weeks=writingBuckets(rows,today,ChartPeriod.WEEK)
        assertEquals(-2L,weeks.last().netWords);assertEquals(10L,weeks[6].netWords)
        val months=writingBuckets(rows,today,ChartPeriod.MONTH)
        assertEquals(8L,months.last().netWords);assertEquals(100L,months[4].netWords)
    }
    @Test fun calendarRepresentsLeapDayAndZeroNetActivityWithoutFabricatingEdits() {
        val days=calendarActivity(listOf(WritingDayEntity("2028-02-29",0,2)),YearMonth.of(2028,2))
        assertEquals(29,days.size);assertEquals(2L,days.last().edits);assertEquals(0L,days.first().edits)
        assertEquals(0L,days.last().netWords)
    }
    @Test fun noRecordsOrNonpositiveSpeedCannotInventAFinishDate() {
        assertNull(bookForecast(10,100,emptyList(),today).estimatedDate)
        assertNull(bookForecast(10,100,listOf(book(today.toString(),-1)),today).estimatedDate)
        assertNull(bookForecast(10,100,listOf(book(today.plusDays(1).toString(),999)),today).estimatedDate)
    }
    @Test fun forecastIncludesUnwrittenDaysAndNegativeChanges() {
        val date=LocalDate.parse("2026-11-04")
        val f=bookForecast(100,200,listOf(book("2026-11-02",90),book("2026-11-04",-30)),date)
        assertEquals(3,f.observedDays);assertEquals(20.0,f.averagePerDay,0.0001)
        assertEquals(date.plusDays(5),f.estimatedDate);assertEquals(0.5f,f.progress,0.0001f)
    }
    @Test fun forecastWindowCapsAtFourteenDaysAndVeryLongPredictionsAreHidden() {
        val rows=listOf(book("2026-10-01",999999),book("2026-11-02",140))
        val f=bookForecast(100,120,rows,today)
        assertEquals(14,f.observedDays);assertEquals(10.0,f.averagePerDay,0.0001);assertEquals(today.plusDays(2),f.estimatedDate)
        assertNull(bookForecast(0,10_000_000,listOf(book("2026-10-01",0),book(today.toString(),1)),today).estimatedDate)
    }
    @Test fun reachedAndDisabledGoalsDoNotClaimAnActualCompletionDate() {
        val reached=bookForecast(120,100,emptyList(),today)
        assertTrue(reached.reached);assertEquals(1f,reached.progress);assertEquals(0L,reached.remaining);assertNull(reached.estimatedDate)
        assertFalse(bookForecast(120,0,emptyList(),today).reached)
    }
    @Test fun backupFourKeepsGoalsAndLegacyVersionsCannotSilentlyDropThem() {
        val old=BackupFixture.sample();val withGoal=old.copy(books=old.books.map { it.copy(targetWords=12345) })
        val out=ByteArrayOutputStream();BackupCodec().write(withGoal,out)
        assertEquals(withGoal,BackupCodec().read(ByteArrayInputStream(out.toByteArray())))
        for(version in 1..3) {
            val legacy=ByteArrayOutputStream();BackupCodec(writeVersion=version).write(old,legacy)
            assertEquals(old,BackupCodec().read(ByteArrayInputStream(legacy.toByteArray())))
            assertThrows(IllegalArgumentException::class.java) { BackupCodec(writeVersion=version).write(withGoal,ByteArrayOutputStream()) }
        }
    }
}
