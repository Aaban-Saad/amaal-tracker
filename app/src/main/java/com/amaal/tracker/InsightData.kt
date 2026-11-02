package com.amaal.tracker

import java.time.LocalDate

data class ComparisonWindows(val current: ReviewPeriod, val previous: ReviewPeriod)

/** Both windows contain the same number of elapsed days, including shorter months. */
fun comparisonWindows(mode: ReviewMode, period: ReviewPeriod, today: LocalDate): ComparisonWindows {
    val prior = reviewPeriod(mode, if (mode == ReviewMode.WEEKLY) period.start.minusWeeks(1) else period.start.minusMonths(1))
    val days = minOf(period.dates(today).size, prior.dates(today).size)
    return ComparisonWindows(ReviewPeriod(period.start, period.start.plusDays(days.toLong() - 1)),
        ReviewPeriod(prior.start, prior.start.plusDays(days.toLong() - 1)))
}

fun insightRecords(entries: List<Entry>, members: Set<String>, period: ReviewPeriod, today: LocalDate): List<Entry> {
    val days = period.dates(today).map { it.toString() }.toSet()
    return entries.filter { it.uid in members && it.date in days }.distinctBy { it.id }
}

fun activeMemberDays(entries: List<Entry>): Int = entries.map { it.uid to it.date }.distinct().size

fun changeLabel(current: Long, previous: Long): String = when {
    previous == 0L && current == 0L -> "No change"
    previous == 0L -> "+$current · no previous records"
    else -> "${if (current >= previous) "+" else ""}${current - previous} (${if (current >= previous) "+" else ""}${(current - previous) * 100 / previous}%)"
}

/** Optional categories remain only for active definitions or actual period history. */
fun insightDefinitions(amals: List<Amal>, retired: List<Amal>, logs: List<Entry>): List<Amal> {
    val recordedIds = logs.map { it.amalId }.toSet()
    return (retired + amals).associateBy { it.id }.values.filter { amal ->
        amal.id in recordedIds || amals.any { it.id == amal.id && it.active }
    }
}

data class Standing(val review: MemberReview, val rank: Int, val gap: Long)

/** Equal scores share a rank (1, 1, 3), so nobody is placed above an equal. */
fun scoreRanks(scores: Map<String, Long>): Map<String, Int> =
    scores.mapValues { (_, score) -> scores.values.count { it > score } + 1 }

fun standings(reviews: List<MemberReview>): List<Standing> {
    val ranks = scoreRanks(reviews.associate { it.uid to it.score })
    val top = reviews.maxOfOrNull { it.score } ?: 0
    return reviews.sortedWith(compareByDescending<MemberReview> { it.score }.thenByDescending { it.activeDays }.thenBy { it.uid })
        .map { Standing(it, ranks.getValue(it.uid), top - it.score) }
}

/** Places gained since the equal-length previous window; empty when that window has no records to compare with. */
fun rankMoves(members: List<String>, current: List<Entry>, previous: List<Entry>): Map<String, Int> {
    if (previous.isEmpty()) return emptyMap()
    fun ranks(logs: List<Entry>) = scoreRanks(members.associateWith { uid -> logs.filter { it.uid == uid }.sumOf { it.score } })
    val before = ranks(previous)
    return ranks(current).mapValues { (uid, rank) -> before.getValue(uid) - rank }
}

fun ordinal(rank: Int): String = "$rank" + when {
    rank % 100 in 11..13 -> "th"
    rank % 10 == 1 -> "st"
    rank % 10 == 2 -> "nd"
    rank % 10 == 3 -> "rd"
    else -> "th"
}

fun raceStatus(table: List<Standing>, uid: String, you: Boolean, name: (String) -> String): String {
    val mine = table.find { it.review.uid == uid } ?: return ""
    val who = if (you) "You're" else "${name(uid)} is"
    val rivals = table.filter { it.review.uid != uid }
    val score = mine.review.score
    return when {
        score == 0L -> "$who not on the board yet"
        rivals.isEmpty() -> "$who on $score pts"
        rivals.any { it.review.score == score } && mine.rank == 1 -> "$who tied for 1st"
        mine.rank == 1 -> "$who leading by ${score - rivals.first().review.score} pts"
        else -> table.last { it.review.score > score }.let { "$who ${ordinal(mine.rank)} · ${it.review.score - score} pts to catch ${name(it.review.uid)}" }
    }
}

fun longestStreak(days: Collection<LocalDate>): Int {
    var best = 0
    var run = 0
    var last: LocalDate? = null
    days.distinct().sorted().forEach { day ->
        run = if (last?.plusDays(1) == day) run + 1 else 1
        best = maxOf(best, run)
        last = day
    }
    return best
}

/** Every member with the day's top score takes the win, so a tie is never split arbitrarily. */
fun dayWins(reviews: List<MemberReview>): Map<String, Int> {
    val days = reviews.flatMap { it.dailyScores.keys }.distinct()
    return reviews.associate { review ->
        review.uid to days.count { day ->
            val score = review.dailyScores[day] ?: 0
            score > 0 && score == reviews.maxOf { it.dailyScores[day] ?: 0 }
        }
    }
}

fun categoryScores(logs: List<Entry>, members: List<String>, amalIds: Set<String>): Map<String, Long> =
    members.associateWith { uid -> logs.filter { it.uid == uid && it.amalId in amalIds }.sumOf { it.score } }

enum class AwardKind { DAYS, STREAK, SALAH, TAKBIR, QURAN }
data class Award(val kind: AwardKind, val title: String, val holders: List<String>, val value: String)

private fun award(kind: AwardKind, title: String, values: Map<String, Long>, unit: (Long) -> String): Award? {
    val best = values.values.maxOrNull() ?: 0
    return if (best <= 0) null else Award(kind, title, values.filterValues { it == best }.keys.toList(), unit(best))
}

fun plural(count: Number, word: String) = "$count $word${if (count.toLong() == 1L) "" else "s"}"

fun periodAwards(reviews: List<MemberReview>, logs: List<Entry>, definitions: Map<String, Amal>): List<Award> {
    val members = reviews.map { it.uid }
    val prayerIds = definitions.values.filter { it.category == "Prayer" }.map { it.id }.toSet()
    return listOfNotNull(
        award(AwardKind.DAYS, "Day winner", dayWins(reviews).mapValues { it.value.toLong() }) { "${plural(it, "day")} won" },
        award(AwardKind.STREAK, "Best streak", reviews.associate { it.uid to it.bestStreak.toLong() }) { "${plural(it, "day")} in a row" },
        award(AwardKind.SALAH, "Salah champion", categoryScores(logs, members, prayerIds)) { "$it salah pts" },
        award(AwardKind.TAKBIR, "Takbir-e-Ula star", reviews.associate { it.uid to (it.prayerCounts["Takbir-e-Ula"] ?: 0).toLong() }) { plural(it, "time") },
        award(AwardKind.QURAN, "Quran companion", reviews.associate { it.uid to it.tilawatDays.size.toLong() }) { "${plural(it, "day")} of Tilawat" }
    )
}

data class DayAmal(val id: String, val name: String, val category: String, val record: Entry?)

/** The current schedule plus anything recorded that day, so deleted and archived amals stay visible. */
fun dayChecklist(amals: List<Amal>, retired: List<Amal>, records: List<Entry>, date: LocalDate): List<DayAmal> {
    val definitions = (retired + amals).associateBy { it.id }
    val prayerOrder = protectedPrayerIds.toList()
    return (amals.filter { it.occurs(date) }.map { it.id } + records.map { it.amalId }).distinct().map { id ->
        DayAmal(id, definitions[id]?.name ?: "Unknown amal", definitions[id]?.category ?: "Unknown", records.find { it.amalId == id })
    }.sortedWith(compareBy<DayAmal>({ if (it.category == "Prayer") 0 else 1 }, { prayerOrder.indexOf(it.id).let { index -> if (index < 0) prayerOrder.size else index } }, { it.name }))
}
