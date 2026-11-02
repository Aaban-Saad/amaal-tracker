package com.amaal.tracker

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReviewTest {
    @Test fun weeklyReviewUsesMondayThroughSunday() {
        assertEquals(ReviewPeriod(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)), reviewPeriod(ReviewMode.WEEKLY, LocalDate.of(2026, 10, 31)))
    }
    @Test fun monthlyReviewHandlesLeapYearsAndExcludesFutureDays() {
        val leap = reviewPeriod(ReviewMode.MONTHLY, LocalDate.of(2024, 2, 10))
        assertEquals(29, leap.dates(LocalDate.of(2024, 3, 1)).size)
        assertEquals(10, leap.dates(LocalDate.of(2024, 2, 10)).size)
        assertTrue(leap.dates(LocalDate.of(2024, 1, 31)).isEmpty())
    }
    @Test fun reviewsKeepMembersPeriodsAndArchivedHistorySeparate() {
        val date = LocalDate.of(2026, 10, 31)
        val entries = listOf(
            Entry("a", "me", "fajr", "2026-10-31", "Takbir-e-Ula", 10),
            Entry("b", "sister", "old-tilawat", "2026-10-31", "Completed", 10),
            Entry("c", "me", "fajr", "2026-09-30", "Qada", 2),
            Entry("d", "outsider", "fajr", "2026-10-31", "Qada", 2),
            Entry("e", "sister", "old-tilawat", "2026-11-01", "Completed", 10)
        )
        val result = memberReviews(listOf("me", "sister", "empty"), listOf(Amal("fajr", "Fajr", "Prayer")), listOf(Amal("old-tilawat", "Reading", "Tilawat")), entries, reviewPeriod(ReviewMode.MONTHLY, date), date)
        assertEquals(3, result.size)
        assertEquals(10L, result[0].score)
        assertEquals(1, result[0].prayerCounts["Takbir-e-Ula"])
        assertEquals(1, result[1].deeds)
        assertEquals(setOf(date), result[1].tilawatDays)
        assertEquals(31, result[1].totalDays)
        assertEquals(3, result[1].consistency)
        assertEquals(0L, result[2].score)
        assertEquals(0, result[2].consistency)
    }
    @Test fun fullMonthsAreNotLimitedToRecent35Days() {
        val date = LocalDate.of(2026, 8, 1)
        val period = reviewPeriod(ReviewMode.MONTHLY, date)
        val entries = listOf(Entry("old", "member", "a", "2026-08-01", "Completed", 10))
        val review = memberReviews(listOf("member"), emptyList(), emptyList(), entries, period, LocalDate.of(2026, 10, 31)).single()
        assertEquals(31, review.totalDays)
        assertEquals(10L, review.score)
    }
    @Test fun multipleDeedsOnOneDayCountAsOneConsistentDay() {
        val day = LocalDate.of(2026, 10, 26)
        val entries = listOf(Entry("a", "me", "a", day.toString(), "Completed", 10), Entry("b", "me", "b", day.toString(), "Completed", 10))
        val result = memberReviews(listOf("me"), emptyList(), emptyList(), entries, reviewPeriod(ReviewMode.WEEKLY, day), day.plusDays(6)).single()
        assertEquals(2, result.deeds)
        assertEquals(1, result.activeDays)
        assertEquals(14, result.consistency)
    }
    @Test fun onlySalahAndDailyTilawatAreProtected() {
        listOf("fajr", "zuhr", "asr", "maghrib", "isha", "tilawat").forEach { assertFalse(Amal(id = it).canDelete()) }
        listOf("kahf", "ashura", "azkar", "kindness").forEach { assertTrue(Amal(id = it).canDelete()) }
        assertEquals(protectedAmalIds, defaultAmals.map { it.id }.toSet())
        assertTrue(Amal("custom", "A custom intention", "Common").canDelete())
        assertTrue(Amal("custom-weekly", frequency = "weekly").canDelete())
    }
}
