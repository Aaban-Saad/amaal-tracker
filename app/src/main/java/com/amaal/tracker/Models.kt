package com.amaal.tracker

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class Amal(
    val id: String = "", val name: String = "", val category: String = "Common",
    val frequency: String = "daily", val weekday: Int = 5,
    val hijriMonth: Int = 9, val hijriDay: Int = 1, val active: Boolean = true
) {
    fun occurs(date: LocalDate): Boolean = active && when (frequency) {
        "weekly" -> date.dayOfWeek.value == weekday
        "islamic" -> HijrahDate.from(date).let { it.get(ChronoField.MONTH_OF_YEAR) == hijriMonth && it.get(ChronoField.DAY_OF_MONTH) == hijriDay }
        else -> true
    }
}
data class Entry(val id: String = "", val uid: String = "", val amalId: String = "", val date: String = "", val status: String = "", val score: Long = 0)
data class Circle(val id: String = "", val name: String = "", val owner: String = "", val inviteCode: String = "", val members: List<String> = emptyList(), val deleting: Boolean = false)
val prayerStatuses = linkedMapOf("Takbir-e-Ula" to 10L, "Prayed in Jamat" to 8L, "Masbuk" to 6L, "Prayed in Time" to 5L, "Qada" to 2L)
val defaultAmals = listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha").map { Amal(it.lowercase(), it, "Prayer") } + listOf(
    Amal("tilawat", "Quran Tilawat", "Tilawat")
)
fun completion(amals: List<Amal>, entries: List<Entry>, date: LocalDate, uid: String): Int {
    val due = amals.filter { it.occurs(date) }
    if (due.isEmpty()) return 0
    return due.count { amal -> entries.any { it.uid == uid && it.amalId == amal.id && it.date == date.toString() } } * 100 / due.size
}
