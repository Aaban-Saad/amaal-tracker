package com.amaal.tracker

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InsightDataTest {
    @Test fun unfinishedWeekComparesSameElapsedDays() {
        val today = LocalDate.of(2026, 10, 28)
        val windows = comparisonWindows(ReviewMode.WEEKLY, reviewPeriod(ReviewMode.WEEKLY, today), today)
        assertEquals(3, windows.current.dates(today).size)
        assertEquals(3, windows.previous.dates(today).size)
        assertEquals(LocalDate.of(2026, 10, 19), windows.previous.start)
        assertEquals(LocalDate.of(2026, 10, 21), windows.previous.end)
    }
    @Test fun longerMonthUsesSharedLengthWithPreviousMonth() {
        val today = LocalDate.of(2026, 3, 31)
        val windows = comparisonWindows(ReviewMode.MONTHLY, reviewPeriod(ReviewMode.MONTHLY, today), today)
        assertEquals(28, windows.current.dates(today).size)
        assertEquals(28, windows.previous.dates(today).size)
        assertEquals(LocalDate.of(2026, 3, 28), windows.current.end)
    }
    @Test fun leapMonthAndYearBoundaryStayCorrect() {
        val today = LocalDate.of(2024, 3, 31)
        assertEquals(29, comparisonWindows(ReviewMode.MONTHLY, reviewPeriod(ReviewMode.MONTHLY, today), today).previous.dates(today).size)
        val jan = LocalDate.of(2026, 1, 5)
        val windows = comparisonWindows(ReviewMode.MONTHLY, reviewPeriod(ReviewMode.MONTHLY, jan), jan)
        assertEquals(LocalDate.of(2025, 12, 1), windows.previous.start)
        assertEquals(5, windows.previous.dates(jan).size)
    }
    @Test fun recordsExcludeOtherMembersOtherPeriodsFutureDaysAndDuplicates() {
        val today = LocalDate.of(2026, 10, 28)
        val a = Entry("a", "me", "fajr", today.toString(), "Qada", 2)
        val records = listOf(a, a, a.copy(id = "b", uid = "other"), a.copy(id = "c", date = "2026-10-29"), a.copy(id = "d", date = "2026-10-19"))
        assertEquals(listOf(a), insightRecords(records, setOf("me"), reviewPeriod(ReviewMode.WEEKLY, today), today))
    }
    @Test fun memberDaysDoNotCountMultipleDeedsTwice() {
        val a = Entry("a", "one", "fajr", "2026-10-28")
        assertEquals(3, activeMemberDays(listOf(a, a.copy(id = "b", amalId = "asr"), a.copy(id = "c", uid = "two"), a.copy(id = "d", date = "2026-10-27"))))
    }
    @Test fun zeroBaselineDoesNotInventPercentageGrowth() {
        assertEquals("No change", changeLabel(0, 0))
        assertEquals("+10 · no previous records", changeLabel(10, 0))
        assertEquals("-5 (-50%)", changeLabel(5, 10))
        assertEquals("+5 (+50%)", changeLabel(15, 10))
    }
    @Test fun optionalCategoriesDisappearUnlessActiveOrRecordedInThePeriod() {
        val azkar = Amal("azkar", "Azkar", "Azkar")
        val log = Entry("record", "me", "azkar", "2026-10-28", "Completed", 10)
        assertTrue(insightDefinitions(defaultAmals, listOf(azkar), emptyList()).none { it.category == "Azkar" })
        assertTrue(insightDefinitions(defaultAmals, listOf(azkar), listOf(log)).any { it.id == "azkar" })
        assertTrue(insightDefinitions(defaultAmals + azkar, emptyList(), emptyList()).any { it.id == "azkar" })
        assertTrue(insightDefinitions(defaultAmals + azkar.copy(active = false), emptyList(), emptyList()).none { it.id == "azkar" })
    }
    private fun review(uid: String, score: Long, activeDays: Int = 1, daily: Map<LocalDate, Long> = emptyMap(), takbir: Int = 0, tilawat: Set<LocalDate> = emptySet()) =
        MemberReview(uid, score, 1, activeDays, 7, mapOf("Takbir-e-Ula" to takbir), tilawat, daily)
    @Test fun equalScoresShareARankAndTheNextPlaceIsSkipped() {
        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 3), scoreRanks(mapOf("a" to 10L, "b" to 10L, "c" to 4L)))
        val table = standings(listOf(review("low", 4), review("second", 10, 1), review("first", 10, 3)))
        assertEquals(listOf("first", "second", "low"), table.map { it.review.uid })
        assertEquals(listOf(1, 1, 3), table.map { it.rank })
        assertEquals(listOf(0L, 0L, 6L), table.map { it.gap })
    }
    @Test fun ordinalsHandleTeens() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(::ordinal))
    }
    @Test fun raceStatusDescribesEachPositionInTheRace() {
        val names = { uid: String -> uid.replaceFirstChar { it.uppercase() } }
        val table = standings(listOf(review("ali", 30), review("sara", 24), review("zaid", 10), review("nur", 0)))
        assertEquals("You're leading by 6 pts", raceStatus(table, "ali", true, names))
        assertEquals("You're 2nd · 6 pts to catch Ali", raceStatus(table, "sara", true, names))
        assertEquals("Zaid is 3rd · 14 pts to catch Sara", raceStatus(table, "zaid", false, names))
        assertEquals("You're not on the board yet", raceStatus(table, "nur", true, names))
        assertEquals("You're tied for 1st", raceStatus(standings(listOf(review("a", 5), review("b", 5))), "a", true, names))
        assertEquals("You're on 5 pts", raceStatus(standings(listOf(review("a", 5))), "a", true, names))
    }
    @Test fun rankMovesCompareWindowsAndNeedPreviousRecords() {
        val up = Entry("1", "a", "fajr", "2026-10-28", "Qada", 10)
        val down = up.copy(id = "2", uid = "b", score = 5)
        assertEquals(mapOf("a" to 1, "b" to -1), rankMoves(listOf("a", "b"), listOf(up, down), listOf(up.copy(score = 2), down)))
        assertTrue(rankMoves(listOf("a", "b"), listOf(up), emptyList()).isEmpty())
    }
    @Test fun streaksNeedConsecutiveDays() {
        val start = LocalDate.of(2026, 2, 27)
        assertEquals(0, longestStreak(emptyList()))
        assertEquals(3, longestStreak(listOf(start.plusDays(1), start, start.plusDays(2), start.plusDays(5), start)))
    }
    @Test fun tiedDayWinsCountForEveryLeaderAndEmptyDaysForNobody() {
        val one = LocalDate.of(2026, 10, 26)
        val two = one.plusDays(1)
        val three = one.plusDays(2)
        val a = review("a", 0, daily = mapOf(one to 10L, two to 5L, three to 0L))
        val b = review("b", 0, daily = mapOf(one to 10L, two to 8L, three to 0L))
        assertEquals(mapOf("a" to 1, "b" to 2), dayWins(listOf(a, b)))
    }
    @Test fun awardsSkipEmptyTitlesAndKeepTiedHolders() {
        val day = LocalDate.of(2026, 10, 26)
        val a = review("a", 10, daily = mapOf(day to 10L), takbir = 1)
        val b = review("b", 10, daily = mapOf(day to 10L))
        val logs = listOf(Entry("1", "a", "fajr", day.toString(), "Takbir-e-Ula", 10), Entry("2", "b", "fajr", day.toString(), "Takbir-e-Ula", 10))
        val awards = periodAwards(listOf(a, b), logs, defaultAmals.associateBy { it.id }).associateBy { it.kind }
        assertEquals(listOf("a", "b"), awards.getValue(AwardKind.SALAH).holders)
        assertEquals("1 day won", awards.getValue(AwardKind.DAYS).value)
        assertEquals(listOf("a"), awards.getValue(AwardKind.TAKBIR).holders)
        assertFalse(AwardKind.QURAN in awards)
    }
    @Test fun dayChecklistListsPrayersInOrderWithRecordedRetiredAndUnrecordedAmals() {
        val date = LocalDate.of(2026, 10, 30) // A Friday.
        val friday = Amal("kahf", "Surah Kahf", "Common", "weekly", 5)
        val monday = Amal("monday", "Monday fast", "Common", "weekly", 1)
        val shuffled = listOf(monday, Amal("tilawat", "Quran Tilawat", "Tilawat"), friday) + defaultAmals.filter { it.category == "Prayer" }.sortedBy { it.id }
        val old = Entry("old", "me", "old", date.toString(), "Completed", 10)
        val isha = Entry("isha", "me", "isha", date.toString(), "Qada", 2)
        val mystery = Entry("gone", "me", "gone", date.toString(), "Completed", 10)
        val rows = dayChecklist(shuffled, listOf(Amal("old", "Retired dhikr", "Azkar")), listOf(isha, old, mystery), date)
        assertEquals(listOf("fajr", "zuhr", "asr", "maghrib", "isha", "tilawat", "old", "kahf", "gone"), rows.map { it.id })
        assertEquals(isha, rows.first { it.id == "isha" }.record)
        assertNull(rows.first { it.id == "fajr" }.record)
        assertEquals("Retired dhikr", rows.first { it.id == "old" }.name)
        assertEquals("Unknown amal", rows.last().name)
    }
}
