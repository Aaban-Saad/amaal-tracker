package com.amaal.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailyInsights(vm: TrackerViewModel, dates: List<LocalDate>, day: LocalDate, selectDay: (LocalDate) -> Unit, member: String?, logs: List<Entry>) {
    val today = LocalDate.now()
    val family = vm.circle!!.members
    val dayLogs = insightRecords(vm.reviewEntries, family.toSet(), ReviewPeriod(day, day), today)
    val scores = family.associateWith { uid -> dayLogs.filter { it.uid == uid }.sumOf { it.score } }
    val ranks = scoreRanks(scores)
    val best = scores.values.maxOrNull() ?: 0
    DayStrip(dates, logs, day, selectDay)
    Column {
        Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(HijrahDate.from(day).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)) + " AH", fontSize = 12.sp, color = Forest, modifier = Modifier.padding(top = 2.dp))
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFBF2DC)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.EmojiEvents, null, tint = Color(0xFF9A7414), modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (family.size < 2) "YOUR DAY" else "DAY CHAMPION", fontSize = 10.sp, color = Color(0xFF7A5C10), letterSpacing = 1.4.sp)
            Text(if (best == 0L) "No deeds recorded yet" else scores.filterValues { it == best }.keys.joinToString(" & ") { shortName(memberName(vm, it)) }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        if (best > 0) Text("$best pts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Forest)
    }
    family.filter { member == null || it == member }.sortedWith(compareBy<String> { ranks.getValue(it) }.thenBy { memberName(vm, it) }).forEach { uid ->
        val score = scores.getValue(uid)
        MemberDayCard(memberName(vm, uid) + if (uid == vm.uid) " (you)" else "", if (score > 0) ranks.getValue(uid) else null, score, dayChecklist(vm.amals, vm.retiredAmals, dayLogs.filter { it.uid == uid }, day))
    }
}

@Composable
private fun DayStrip(dates: List<LocalDate>, logs: List<Entry>, day: LocalDate, select: (LocalDate) -> Unit) {
    val scores = dates.associateWith { date -> logs.filter { it.date == date.toString() }.sumOf { it.score } }
    val top = maxOf(1L, scores.values.maxOrNull() ?: 1L)
    val position = maxOf(0, dates.indexOf(day) - 3)
    val state = rememberLazyListState(position)
    LaunchedEffect(position) { state.animateScrollToItem(position) }
    LazyRow(state = state, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(dates) { date ->
            val active = date == day
            val score = scores.getValue(date)
            Column(Modifier.width(41.dp).clip(RoundedCornerShape(14.dp)).background(if (active) Forest else Color.White).selectable(active, role = Role.Tab) { select(date) }
                .semantics { contentDescription = "${dateLabel(date)}, $score points. Show this day" }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(DateTimeFormatter.ofPattern("EEE")), fontSize = 10.sp, color = if (active) Color(0xFFD7E5D6) else Muted)
                Text("${date.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else Ink)
                Box(Modifier.padding(top = 6.dp).height(34.dp).width(8.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(if (score == 0L) 2.dp else (34f * score / top).coerceAtLeast(4f).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (active) Gold else if (score == 0L) Sage else Forest))
                }
                Text("$score", fontSize = 10.sp, color = if (active) Color.White else Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun MemberDayCard(name: String, rank: Int?, score: Long, rows: List<DayAmal>) {
    val done = rows.count { it.record != null }
    val (salah, others) = rows.partition { it.category == "Prayer" }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(rank)
            MemberAvatar(name.removeSuffix(" (you)"), Modifier.padding(start = 8.dp).size(36.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$done of ${rows.size} amals done", fontSize = 12.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$score", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Forest)
                Text("pts", fontSize = 10.sp, color = Muted)
            }
        }
        MiniBar(if (rows.isEmpty()) 0f else done.toFloat() / rows.size, Forest, Modifier.padding(top = 10.dp))
        if (salah.isNotEmpty()) DayGroup("SALAH", salah)
        if (others.isNotEmpty()) DayGroup("OTHER AMALS", others)
    }
}

@Composable
private fun DayGroup(title: String, rows: List<DayAmal>) {
    Text(title, fontSize = 10.sp, color = Muted, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
    rows.forEach { DayRow(it) }
}

@Composable
private fun DayRow(amal: DayAmal) {
    val record = amal.record
    val tint = prayerStatuses.keys.indexOf(record?.status).takeIf { it >= 0 }?.let { statusColors[it] } ?: Forest
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (record != null) Icon(Icons.Filled.CheckCircle, "Done", tint = tint, modifier = Modifier.size(20.dp))
        else Icon(Icons.Outlined.RadioButtonUnchecked, "Not recorded", tint = Color(0xFFC4CCC6), modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(amal.name, fontSize = 14.sp, fontWeight = if (record != null) FontWeight.SemiBold else FontWeight.Normal, color = if (record != null) Ink else Muted)
            Text(record?.status ?: "Not recorded", fontSize = 11.sp, color = if (record != null) tint else Muted)
        }
        if (record != null) Text("+${record.score}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Sage).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
