package com.amaal.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen(vm: TrackerViewModel, openFamily: () -> Unit) {
    var mode by remember { mutableStateOf(ReviewMode.WEEKLY) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var selectedMember by remember(vm.circle?.id) { mutableStateOf<String?>(null) }
    var memberMenu by remember(vm.selectedFamilyId) { mutableStateOf(false) }
    var section by remember { mutableStateOf(InsightSection.RACE) }
    val period = reviewPeriod(mode, anchor)
    val today = LocalDate.now()
    var selectedDay by remember(period) { mutableStateOf(minOf(period.end, today)) }
    val family = vm.circle
    val comparison = comparisonWindows(mode, period, today)
    val queryPeriod = ReviewPeriod(comparison.previous.start, period.end)
    DisposableEffect(family?.id, queryPeriod) {
        vm.observeReview(queryPeriod)
        onDispose { vm.stopReview() }
    }
    val members = family?.members ?: emptyList()
    val reviews = memberReviews(members, vm.amals, vm.retiredAmals, vm.reviewEntries, period, today)
    val dates = period.dates(today)
    val selectedName = selectedMember?.let { memberName(vm, it) } ?: "Everyone"
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageTitle("Race to good deeds.", "“So race to all that is good.” — Qur'an 2:148")
        FamilyLoadState(vm) {
        if (family == null) {
            SurfaceCard {
                Text("Your family's journey starts here", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Create or join a family to see everyone's progress.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = openFamily) { Text("Open Family") }
            }
        } else {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { anchor = if (mode == ReviewMode.WEEKLY) anchor.minusWeeks(1) else anchor.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous review period", tint = Forest) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (mode == ReviewMode.MONTHLY) period.start.format(DateTimeFormatter.ofPattern("MMMM yyyy")) else "${period.start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${period.end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (period.end > today) "So far · through ${today.format(DateTimeFormatter.ofPattern("d MMM"))}" else plural(dates.size, "day"), fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
                    }
                    IconButton(onClick = { anchor = if (mode == ReviewMode.WEEKLY) anchor.plusWeeks(1) else anchor.plusMonths(1) }, enabled = period.end < today) { Icon(Icons.Outlined.ChevronRight, "Next review period") }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SegmentedTabs(ReviewMode.entries, mode, { mode = it; anchor = today }, Modifier.weight(1f)) { option -> if (option == ReviewMode.WEEKLY) "Weekly" else "Monthly" }
                    Box(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Sage, RoundedCornerShape(12.dp)).clickable { memberMenu = true }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PeopleOutline, null, tint = Forest, modifier = Modifier.size(16.dp))
                            Text(selectedName, modifier = Modifier.weight(1f).padding(horizontal = 6.dp), fontSize = 12.sp, color = Forest, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ExpandMore, null, tint = Forest, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = memberMenu, onDismissRequest = { memberMenu = false }) {
                            DropdownMenuItem(text = { Text("Everyone") }, onClick = { selectedMember = null; memberMenu = false })
                            members.forEach { uid -> DropdownMenuItem(text = { Text(memberName(vm, uid) + if (uid == vm.uid) " (you)" else "") }, onClick = { selectedMember = uid; memberMenu = false }) }
                        }
                    }
                }
            }
            if (vm.reviewLoading) {
                SurfaceCard {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Forest)
                    Text("Loading family review…", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 12.dp))
                }
            } else if (vm.reviewError != null) {
                SurfaceCard {
                    Text("This review couldn't load", fontWeight = FontWeight.SemiBold)
                    Text(vm.reviewError!!, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { vm.observeReview(queryPeriod) }) { Text("Retry review") }
                }
            } else {
                DetailedInsights(vm, mode, period, selectedMember, reviews, section, { section = it }, selectedDay, { selectedDay = it }) { selectedMember = it }
            }
        }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun <T> SegmentedTabs(options: List<T>, selected: T, select: (T) -> Unit, modifier: Modifier = Modifier, label: (T) -> String) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sage).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(10.dp)).background(if (active) Forest else Color.Transparent).selectable(active, role = Role.Tab) { select(option) }, contentAlignment = Alignment.Center) {
                Text(label(option), fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) Color.White else Ink, maxLines = 1)
            }
        }
    }
}
