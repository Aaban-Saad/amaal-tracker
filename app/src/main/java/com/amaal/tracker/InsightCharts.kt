package com.amaal.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class InsightSection(val label: String) { RACE("Race"), DAILY("Daily"), SALAH("Salah"), HABITS("Habits") }

private val categoryColors = linkedMapOf("Prayer" to Color(0xFF426B56), "Common" to Color(0xFFB27843), "Azkar" to Color(0xFF7065A0), "Tilawat" to Color(0xFF367C95), "Unknown" to Color(0xFF7B7B7B))
val statusColors = listOf(Color(0xFF32634A), Color(0xFF659077), Color(0xFFA08843), Color(0xFF547BA5), Color(0xFFB56868))
private fun categoryLabel(category: String) = if (category == "Prayer") "Salah" else category
fun dateLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM"))

@Composable
fun DetailedInsights(vm: TrackerViewModel, mode: ReviewMode, period: ReviewPeriod, member: String?, reviews: List<MemberReview>, section: InsightSection, selectSection: (InsightSection) -> Unit, day: LocalDate, selectDay: (LocalDate) -> Unit, selectMember: (String) -> Unit) {
    var calendarCategory by remember { mutableStateOf("All deeds") }
    val today = LocalDate.now()
    val members = if (member == null) vm.circle!!.members.toSet() else setOf(member)
    val definitions = (vm.retiredAmals + vm.amals).associateBy { it.id }
    val retired = vm.retiredAmals.map { it.id }.toSet()
    val logs = insightRecords(vm.reviewEntries, members, period, today)
    val visibleDefinitions = insightDefinitions(vm.amals, vm.retiredAmals, logs)
    val visibleIds = visibleDefinitions.map { it.id }.toSet() + logs.map { it.amalId }
    val visibleCategories = categoryColors.keys.filter { category ->
        visibleDefinitions.any { it.category == category } || (category == "Unknown" && logs.any { definitions[it.amalId] == null })
    }
    LaunchedEffect(visibleCategories) { if (calendarCategory != "All deeds" && calendarCategory !in visibleCategories) calendarCategory = "All deeds" }
    val dates = period.dates(today)
    val calendarLogs = if (calendarCategory == "All deeds") logs else logs.filter { (definitions[it.amalId]?.category ?: "Unknown") == calendarCategory }
    val scored = reviews.size > 1
    SegmentedTabs(InsightSection.entries, section, selectSection) { it.label }
    if (logs.isEmpty() && section in setOf(InsightSection.SALAH, InsightSection.HABITS)) SurfaceCard {
        Text("No deeds recorded for this selection", fontWeight = FontWeight.SemiBold)
        Text("Choose another period or member to explore their records.", color = Muted, fontSize = 12.sp)
    }
    when (section) {
        InsightSection.RACE -> RaceInsights(vm, mode, period, member, reviews, logs) { uid -> selectMember(uid); selectSection(InsightSection.DAILY) }
        InsightSection.DAILY -> DailyInsights(vm, dates, day, selectDay, member, logs)
        InsightSection.SALAH -> {
            val prayerDefinitions = protectedPrayerIds.map { id -> definitions[id] ?: Amal(id, id.replaceFirstChar { it.uppercase() }, "Prayer") } + visibleDefinitions.filter { it.category == "Prayer" && it.id !in protectedPrayerIds }
            val prayerIds = prayerDefinitions.map { it.id }.toSet()
            if (scored) SurfaceCard {
                SectionTitle("Who leads in salah", "SALAH STANDINGS")
                val allLogs = insightRecords(vm.reviewEntries, vm.circle!!.members.toSet(), period, today)
                val scores = categoryScores(allLogs, reviews.map { it.uid }, prayerIds)
                val ranks = scoreRanks(scores)
                val top = maxOf(1L, scores.values.maxOrNull() ?: 1L)
                scores.entries.sortedByDescending { it.value }.forEach { (uid, score) ->
                    val records = allLogs.filter { it.uid == uid && it.amalId in prayerIds }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RankBadge(if (score > 0) ranks.getValue(uid) else null)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(memberName(vm, uid) + if (uid == vm.uid) " (you)" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${plural(records.size, "prayer")} · ${records.count { it.status == "Takbir-e-Ula" }} Takbir-e-Ula", fontSize = 11.sp, color = Muted)
                            StatusBar(records, Modifier.padding(top = 6.dp).fillMaxWidth((score.toFloat() / top).coerceAtLeast(.04f)))
                        }
                        Text("$score", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            SurfaceCard {
                SectionTitle("How prayers were recorded", "PRAYER STATUS DISTRIBUTION")
                val prayers = logs.filter { definitions[it.amalId]?.category == "Prayer" }
                StatusBreakdown(prayers)
                Text("Shares of recorded prayers, not completion rates.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
            SurfaceCard {
                SectionTitle("Prayer by prayer", "EACH SALAH")
                prayerDefinitions.forEach { amal ->
                    val records = logs.filter { it.amalId == amal.id }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(amal.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${records.sumOf { it.score }} pts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Forest)
                    }
                    Text("${plural(records.size, "record")} · ${plural(records.map { it.date }.distinct().size, "day")}", color = Muted, fontSize = 11.sp)
                    if (records.isEmpty()) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Sage)) else StatusBar(records, Modifier.padding(top = 6.dp).fillMaxWidth())
                }
            }
        }
        InsightSection.HABITS -> {
            SurfaceCard {
                SectionTitle("Build a rhythm", "ACTIVITY CALENDAR")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("All deeds") + visibleCategories).forEach { category ->
                        FilterChip(selected = calendarCategory == category, onClick = { calendarCategory = category }, label = { Text(categoryLabel(category)) })
                    }
                }
                ActivityCalendar(dates, calendarLogs, members.size) { selectDay(it); selectSection(InsightSection.DAILY) }
                Text("${calendarLogs.map { it.date }.distinct().size}/${dates.size} days with records · Tap a day to open it", color = Forest, fontSize = 12.sp)
            }
            val recorded = categoryColors.keys.map { category -> category to logs.filter { (definitions[it.amalId]?.category ?: "Unknown") == category } }.filter { it.second.isNotEmpty() }
            if (recorded.isNotEmpty()) SurfaceCard {
                SectionTitle("Where the points came from", "POINTS BY CATEGORY")
                val top = maxOf(1L, recorded.maxOf { (_, records) -> records.sumOf { it.score } })
                recorded.forEach { (category, records) ->
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(categoryLabel(category), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("${records.sumOf { it.score }} pts · ${plural(records.size, "record")}", fontSize = 12.sp, color = Muted)
                    }
                    MiniBar(records.sumOf { it.score }.toFloat() / top, categoryColors.getValue(category), Modifier.padding(top = 6.dp))
                }
            }
            SurfaceCard {
                SectionTitle("Every amal counts", "AMAL BREAKDOWN")
                categoryColors.keys.forEach { category ->
                    val items = visibleIds.filter { (definitions[it]?.category ?: "Unknown") == category }.sortedByDescending { id -> logs.filter { it.amalId == id }.sumOf { it.score } }
                    if (items.isNotEmpty()) {
                        Text(categoryLabel(category), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = categoryColors.getValue(category), modifier = Modifier.padding(top = 16.dp))
                        val top = maxOf(1L, items.maxOf { id -> logs.filter { it.amalId == id }.sumOf { it.score } })
                        items.forEach { id -> AmalRow(vm, id, definitions[id], id in retired, logs.filter { it.amalId == id }, top, categoryColors.getValue(category), member == null && scored) }
                    }
                }
            }
        }
    }
    Text("Rankings count recorded deeds only; a blank is not a missed deed. Future days are excluded. Categories use saved definitions; past schedules and membership dates are unavailable. Comparisons include current members. Deleted and archived amal records remain included.", color = Muted, fontSize = 11.sp)
}

@Composable
private fun AmalRow(vm: TrackerViewModel, id: String, amal: Amal?, deleted: Boolean, records: List<Entry>, top: Long, color: Color, showLeader: Boolean) {
    val score = records.sumOf { it.score }
    val leader = records.groupBy { it.uid }.mapValues { (_, own) -> own.sumOf { it.score } }.entries.maxByOrNull { it.value }
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((amal?.name ?: "Unknown amal") + when { deleted -> " · Deleted"; amal?.active == false -> " · Archived"; else -> "" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("$score pts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Forest)
        }
        MiniBar(score.toFloat() / top, color, Modifier.padding(vertical = 5.dp))
        Text(if (records.isEmpty()) "Not recorded in this period" else "${plural(records.map { it.date }.distinct().size, "day")} · ${plural(records.size, "record")}" + if (showLeader && leader != null) " · Top: ${memberName(vm, leader.key)}" else "", fontSize = 11.sp, color = Muted)
    }
}

@Composable
fun MiniBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Sage)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun LegendRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Text(value, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun StatusBar(records: List<Entry>, modifier: Modifier = Modifier) {
    Row(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        prayerStatuses.keys.forEachIndexed { index, status ->
            val count = records.count { it.status == status }
            if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(statusColors[index]))
        }
    }
}

@Composable
private fun StatusBreakdown(records: List<Entry>) {
    if (records.isEmpty()) { Text("Not recorded in this period", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)); return }
    StatusBar(records, Modifier.fillMaxWidth().padding(top = 14.dp))
    prayerStatuses.keys.forEachIndexed { index, status ->
        val count = records.count { it.status == status }
        LegendRow(status, "$count · ${count * 100 / records.size}%", statusColors[index])
    }
}

@Composable
private fun ActivityCalendar(dates: List<LocalDate>, logs: List<Entry>, members: Int, select: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, modifier = Modifier.weight(1f), fontSize = 11.sp, color = Muted) }
    }
    val cells = List<LocalDate?>(dates.firstOrNull()?.dayOfWeek?.value?.minus(1) ?: 0) { null } + dates
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (week + List<LocalDate?>(7 - week.size) { null }).forEach { day ->
                val count = logs.filter { it.date == day?.toString() }.map { it.uid }.distinct().size
                val fill = if (day == null) Color.Transparent else if (count == 0) Sage else Forest.copy(alpha = .35f + .65f * count / maxOf(1, members))
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(fill)
                    .then(if (day != null) Modifier.clickable { select(day) }.semantics { contentDescription = "${dateLabel(day)}: $count of $members members recorded activity. Show details" } else Modifier), contentAlignment = Alignment.Center) {
                    if (day != null) Text("${day.dayOfMonth}", fontSize = 12.sp, color = if (count > 0) Color.White else Forest)
                }
            }
        }
    }
    Text("Light: no records · Dark: all selected members recorded", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 10.dp))
}
