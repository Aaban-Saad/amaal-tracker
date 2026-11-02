package com.amaal.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

val Gold = Color(0xFFD9AE3C)
private val Silver = Color(0xFFB4BEC4)
private val Bronze = Color(0xFFC9915E)
private val HeroAccent = Color(0xFFE5DFAC)
private val Falling = Color(0xFFB56868)
// Validated for colorblind separation on white; two sit below 3:1, so the chart legend always names each line.
private val memberColors = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFF6250D6), Color(0xFFE87BA4), Color(0xFF8A7A1E))

fun shortName(name: String): String = if (name == "Family member") name else name.substringBefore(' ')

@Composable
fun RankBadge(rank: Int?, modifier: Modifier = Modifier) {
    val fill = when (rank) { 1 -> Gold; 2 -> Silver; 3 -> Bronze; else -> Sage }
    Box(modifier.size(28.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Text(rank?.toString() ?: "–", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (rank != null && rank <= 3) Ink else Muted)
    }
}

@Composable
fun RaceInsights(vm: TrackerViewModel, mode: ReviewMode, period: ReviewPeriod, member: String?, reviews: List<MemberReview>, logs: List<Entry>, openMember: (String) -> Unit) {
    val today = LocalDate.now()
    val family = vm.circle!!.members
    val table = standings(reviews)
    val name = { uid: String -> memberName(vm, uid) }
    val windows = comparisonWindows(mode, period, today)
    val everyone = family.toSet()
    val moves = rankMoves(family, insightRecords(vm.reviewEntries, everyone, windows.current, today), insightRecords(vm.reviewEntries, everyone, windows.previous, today))
    val familyLogs = insightRecords(vm.reviewEntries, everyone, period, today)
    val focus = member ?: vm.uid
    val unit = if (mode == ReviewMode.WEEKLY) "week" else "month"
    val eyebrow = if (table.size < 2) "YOUR SOLO RUN" else if (period.end >= today) "LEADING THIS ${unit.uppercase()}" else "${unit.uppercase()} CHAMPION"
    RaceHero(table, eyebrow, raceStatus(table, focus, focus == vm.uid) { shortName(name(it)) }, name)
    SurfaceCard {
        SectionTitle("Leaderboard", "TAP A NAME TO SEE THEIR DAY")
        Spacer(Modifier.height(8.dp))
        table.forEach { StandingRow(vm, it, table.first().review.score, moves[it.review.uid], it.review.uid == member) { openMember(it.review.uid) } }
    }
    val awards = periodAwards(reviews, familyLogs, (vm.retiredAmals + vm.amals).associateBy { it.id })
    if (table.size > 1 && awards.isNotEmpty()) {
        SectionTitle("Title holders", "${unit.uppercase()} AWARDS")
        awards.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { AwardTile(it, name, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    val dates = period.dates(today)
    if (table.size > 1 && dates.size > 1) SurfaceCard {
        SectionTitle("The race so far", "RUNNING POINTS")
        RaceChart(dates, table.take(memberColors.size).sortedBy { family.indexOf(it.review.uid) }, focus, name)
    }
    val current = insightRecords(vm.reviewEntries, if (member == null) everyone else setOf(member), windows.current, today)
    val previous = insightRecords(vm.reviewEntries, if (member == null) everyone else setOf(member), windows.previous, today)
    SurfaceCard {
        SectionTitle(if (member == null) "Family momentum" else "${name(member)}'s momentum", "PREVIOUS PERIOD COMPARISON")
        Text("vs ${dateLabel(windows.previous.start)}–${dateLabel(windows.previous.end)}, over the same number of days. A check-in is one member active on one day.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        MomentumRow("Points", "${logs.sumOf { it.score }}", current.sumOf { it.score }, previous.sumOf { it.score })
        MomentumRow("Deeds", "${logs.size}", current.size.toLong(), previous.size.toLong())
        MomentumRow("Check-ins", "${activeMemberDays(logs)}", activeMemberDays(current).toLong(), activeMemberDays(previous).toLong())
    }
}

@Composable
private fun RaceHero(table: List<Standing>, eyebrow: String, status: String, name: (String) -> String) {
    val scorers = table.filter { it.review.score > 0 }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Forest, Color(0xFF48755F)))).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EmojiEvents, null, tint = HeroAccent, modifier = Modifier.size(16.dp))
            Text(eyebrow, fontSize = 10.sp, color = HeroAccent, letterSpacing = 1.6.sp, modifier = Modifier.padding(start = 6.dp))
        }
        if (scorers.isEmpty()) {
            Text("The race is wide open.", fontSize = 23.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text("Record the first deed to take the lead.", fontSize = 12.sp, color = Color(0xFFD7E5D6), modifier = Modifier.padding(top = 6.dp))
        } else {
            val podium = scorers.take(3).let { top -> if (top.size == 1) top else listOf(top[1], top[0]) + top.drop(2) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                podium.forEach { PodiumStep(it, name(it.review.uid), Modifier.weight(1f)) }
            }
            Text(status, fontSize = 13.sp, color = Forest, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50)).background(HeroAccent).padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun PodiumStep(standing: Standing, name: String, modifier: Modifier) {
    val rank = standing.rank
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (rank == 1) Icon(Icons.Outlined.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(22.dp).padding(bottom = 2.dp))
        MemberAvatar(name, Modifier.size(if (rank == 1) 54.dp else 44.dp))
        Text(shortName(name), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${standing.review.score}", fontSize = if (rank == 1) 26.sp else 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(" pts", fontSize = 11.sp, color = Color(0xFFD7E5D6), modifier = Modifier.padding(bottom = 4.dp))
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(when (rank) { 1 -> 44.dp; 2 -> 34.dp; else -> 26.dp }).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            RankBadge(rank, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StandingRow(vm: TrackerViewModel, standing: Standing, top: Long, move: Int?, focused: Boolean, open: () -> Unit) {
    val review = standing.review
    val name = memberName(vm, review.uid)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(if (focused) Sage else Color.Transparent).clickable(onClick = open).padding(vertical = 8.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RankBadge(if (review.score > 0) standing.rank else null)
        MemberAvatar(name, Modifier.padding(start = 8.dp).size(36.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(name + if (review.uid == vm.uid) " (you)" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${review.activeDays}/${review.totalDays} days", fontSize = 11.sp, color = Muted)
                if (review.bestStreak > 1) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = Color(0xFFEB6834), modifier = Modifier.padding(start = 6.dp).size(13.dp))
                    Text("${review.bestStreak} streak", fontSize = 11.sp, color = Muted, maxLines = 1)
                }
                if (move != null && move != 0) RankMove(move)
            }
            MiniBar(if (top == 0L) 0f else review.score.toFloat() / top, if (standing.rank == 1) Gold else Forest, Modifier.padding(top = 5.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${review.score}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Forest)
            Text(if (standing.gap > 0 && review.score > 0) "-${standing.gap} pts" else "pts", fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun RankMove(move: Int) {
    val up = move > 0
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "${if (up) "Up" else "Down"} ${kotlin.math.abs(move)} since the previous period" }, verticalAlignment = Alignment.CenterVertically) {
        Icon(if (up) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown, null, tint = if (up) Forest else Falling, modifier = Modifier.size(16.dp))
        Text("${kotlin.math.abs(move)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (up) Forest else Falling)
    }
}

@Composable
private fun AwardTile(award: Award, name: (String) -> String, modifier: Modifier) {
    val icon = when (award.kind) {
        AwardKind.DAYS -> Icons.Outlined.EmojiEvents
        AwardKind.STREAK -> Icons.Outlined.LocalFireDepartment
        AwardKind.SALAH -> Icons.Outlined.NightsStay
        AwardKind.TAKBIR -> Icons.Outlined.StarOutline
        AwardKind.QURAN -> Icons.AutoMirrored.Outlined.MenuBook
    }
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFBF2DC)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF9A7414), modifier = Modifier.size(18.dp)) }
        Text(award.title, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        Text(award.holders.take(2).joinToString(" & ") { shortName(name(it)) } + if (award.holders.size > 2) " +${award.holders.size - 2}" else "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2)
        Text(award.value, fontSize = 12.sp, color = Forest)
    }
}

@Composable
private fun RaceChart(dates: List<LocalDate>, shown: List<Standing>, focus: String, name: (String) -> String) {
    val series = shown.map { standing ->
        var total = 0L
        dates.map { day -> total += standing.review.dailyScores[day] ?: 0; total }
    }
    val top = maxOf(1L, series.maxOf { it.last() })
    val summary = shown.joinToString { "${name(it.review.uid)} ${it.review.score} points" }
    Row(Modifier.padding(top = 14.dp)) {
        Column(Modifier.height(150.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Text("$top", fontSize = 10.sp, color = Muted)
            Text("0", fontSize = 10.sp, color = Muted)
        }
        Canvas(Modifier.weight(1f).height(150.dp).semantics { contentDescription = "Running points. $summary" }) {
            val inset = 6.dp.toPx()
            val plot = size.height - inset * 2
            val width = size.width - inset * 2
            fun x(index: Int) = inset + width * index / (dates.size - 1)
            fun y(value: Long) = inset + plot * (1f - value.toFloat() / top)
            listOf(0L, top / 2, top).forEach { drawLine(Sage, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx()) }
            // The focused member draws last so a close race never hides them.
            series.indices.sortedBy { shown[it].review.uid == focus }.forEach { index ->
                val color = memberColors[index]
                val path = Path().apply { series[index].forEachIndexed { i, value -> if (i == 0) moveTo(x(i), y(value)) else lineTo(x(i), y(value)) } }
                drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val end = Offset(x(dates.size - 1), y(series[index].last()))
                drawCircle(Color.White, 6.dp.toPx(), end)
                drawCircle(color, 4.dp.toPx(), end)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp)) {
        Text(dateLabel(dates.first()), fontSize = 10.sp, color = Muted, modifier = Modifier.weight(1f))
        Text(dateLabel(dates.last()), fontSize = 10.sp, color = Muted)
    }
    shown.withIndex().chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { (index, standing) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(memberColors[index]))
                    Text(shortName(name(standing.review.uid)), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                    Text("${standing.review.score}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MomentumRow(label: String, total: String, current: Long, previous: Long) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(total, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Forest)
            Text(changeLabel(current, previous), fontSize = 11.sp, color = if (current > previous) Forest else if (current < previous) Falling else Muted)
        }
    }
}
