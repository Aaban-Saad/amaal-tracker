package com.amaal.tracker

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ReviewMode { WEEKLY, MONTHLY }

data class ReviewPeriod(val start: LocalDate, val end: LocalDate) {
    fun dates(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val last = minOf(end, today)
        return if (last < start) emptyList() else generateSequence(start) { it.plusDays(1) }.takeWhile { it <= last }.toList()
    }
}

fun reviewPeriod(mode: ReviewMode, anchor: LocalDate): ReviewPeriod = when (mode) {
    ReviewMode.WEEKLY -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { ReviewPeriod(it, it.plusDays(6)) }
    ReviewMode.MONTHLY -> anchor.withDayOfMonth(1).let { ReviewPeriod(it, it.withDayOfMonth(it.lengthOfMonth())) }
}

data class MemberReview(
    val uid: String,
    val score: Long,
    val deeds: Int,
    val activeDays: Int,
    val totalDays: Int,
    val prayerCounts: Map<String, Int>,
    val tilawatDays: Set<LocalDate>,
    val dailyScores: Map<LocalDate, Long>
) {
    val consistency: Int get() = if (totalDays == 0) 0 else activeDays * 100 / totalDays
    val bestStreak: Int get() = longestStreak(dailyScores.filterValues { it > 0 }.keys)
}

fun memberReviews(
    members: List<String>, amals: List<Amal>, retiredAmals: List<Amal>, entries: List<Entry>,
    period: ReviewPeriod, today: LocalDate = LocalDate.now()
): List<MemberReview> {
    val dates = period.dates(today)
    val dateStrings = dates.map { it.toString() }.toSet()
    val definitions = (retiredAmals + amals).associateBy { it.id }
    val periodEntries = entries.filter { it.date in dateStrings }.distinctBy { it.id }
    return members.distinct().map { uid ->
        val logs = periodEntries.filter { it.uid == uid }
        val prayers = logs.filter { definitions[it.amalId]?.category == "Prayer" }
        MemberReview(
            uid = uid, score = logs.sumOf { it.score }, deeds = logs.size,
            activeDays = logs.map { it.date }.distinct().size, totalDays = dates.size,
            prayerCounts = prayerStatuses.keys.associateWith { status -> prayers.count { it.status == status } },
            tilawatDays = logs.filter { definitions[it.amalId]?.category == "Tilawat" }.map { LocalDate.parse(it.date) }.toSet(),
            dailyScores = dates.associateWith { date -> logs.filter { it.date == date.toString() }.sumOf { it.score } }
        )
    }
}

// Stable IDs also protect existing families created before this app update.
val protectedPrayerIds = setOf("fajr", "zuhr", "asr", "maghrib", "isha")
val protectedAmalIds = protectedPrayerIds + "tilawat"
fun Amal.canDelete(): Boolean = id !in protectedAmalIds
