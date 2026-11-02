package com.amaal.tracker
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.chrono.HijrahDate
class TrackingTest {
    @Test fun weeklyAmalAppearsOnlyOnDesignatedDay() {
        val amal = Amal("kahf", "Kahf", frequency = "weekly", weekday = 5)
        assertTrue(amal.occurs(LocalDate.of(2026, 10, 30)))
        assertFalse(amal.occurs(LocalDate.of(2026, 10, 31)))
    }
    @Test fun islamicAmalFollowsHijriDate() {
        val date = LocalDate.from(HijrahDate.of(1448, 1, 10))
        val amal = Amal("ashura", "Ashura", frequency = "islamic", hijriMonth = 1, hijriDay = 10)
        assertTrue(amal.occurs(date)); assertFalse(amal.occurs(date.plusDays(1)))
    }
    @Test fun completionExcludesOtherMembersAndDatesAndArchivedAmals() {
        val date = LocalDate.of(2026, 10, 31)
        val amals = listOf(Amal("a", "A"), Amal("b", "B"), Amal("c", "C", active = false))
        val logs = listOf(Entry("1", "me", "a", date.toString()), Entry("2", "other", "b", date.toString()), Entry("3", "me", "b", date.minusDays(1).toString()))
        assertEquals(50, completion(amals, logs, date, "me"))
        assertEquals(0, completion(emptyList(), logs, date, "me"))
    }
    @Test fun prayerScoresFavorTimelyCongregation() { assertEquals(10L, prayerStatuses["Takbir-e-Ula"]); assertTrue(prayerStatuses.getValue("Prayed in Jamat") > prayerStatuses.getValue("Qada")) }
    @Test fun reminderTimesRejectInvalidAndRolloverTimes() {
        assertEquals(5, ReminderScheduler.parseTimes("05:00,13:00,16:00,18:00,20:00").size)
        assertTrue(ReminderScheduler.parseTimes("24:00").isEmpty())
        assertTrue(ReminderScheduler.parseTimes("12:60").isEmpty())
        assertTrue(ReminderScheduler.parseTimes("invalid").isEmpty())
    }
    @Test fun archivedAmalNeverAppears() { assertFalse(Amal(active = false).occurs(LocalDate.now())) }
}
