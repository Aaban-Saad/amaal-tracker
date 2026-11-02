package com.amaal.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.chrono.HijrahDate
import java.util.Locale
import java.util.UUID

val Forest = Color(0xFF285747)
val Ink = Color(0xFF263E35)
val Muted = Color(0xFF7C8880)
val Cream = Color(0xFFF8F9F5)
val Sage = Color(0xFFE9F0E4)

class MainActivity : ComponentActivity() {
    private val vm: TrackerViewModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ReminderScheduler.schedule(this) else vm.message = "Notifications are disabled. Enable them in Android settings to receive reminders."
    }
    override fun onResume() { super.onResume(); ReminderScheduler.schedule(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, background = Cream, surface = Color.White, onSurface = Ink, secondary = Muted)) {
                AmaalApp(vm, { vm.signIn(this) }, {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ReminderScheduler.schedule(this)
                }, { code -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Join my Amaal Tracker family: amaal://join/$code\nInvite code: $code") }, "Invite your family")) }, intent?.data?.lastPathSegment)
            }
        }
    }
}

@Composable
fun AmaalApp(vm: TrackerViewModel, signIn: () -> Unit, requestReminders: () -> Unit, share: (String) -> Unit, invite: String?) {
    var tab by remember { mutableIntStateOf(if (invite != null) 1 else 0) }
    var settings by remember { mutableStateOf(false) }
    var pendingInvite by remember(invite) { mutableStateOf(invite) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (vm.uid.isNotEmpty()) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            listOf("Today" to Icons.Outlined.WbSunny, "Family" to Icons.Outlined.PeopleOutline, "Insights" to Icons.Outlined.BarChart).forEachIndexed { index, pair ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(pair.second, null) }, label = { Text(pair.first) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Sage, selectedIconColor = Forest, selectedTextColor = Forest, unselectedIconColor = Muted))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (vm.uid.isEmpty()) Welcome(vm, signIn)
            else Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Forest), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.NightsStay, null, tint = Color(0xFFE8EED3), modifier = Modifier.size(22.dp)) }
                    Text("amaal", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.padding(start = 9.dp))
                    Spacer(Modifier.weight(1f))
                    if (vm.preview) Text("LOCAL PREVIEW", fontSize = 9.sp, color = Forest, letterSpacing = 1.sp, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Sage).padding(7.dp))
                    IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Tune, "Settings", tint = Forest) }
                }
                FamilySelector(vm)
                when (tab) {
                    0 -> key(vm.selectedFamilyId) { FamilyLoadState(vm) { DailyScreen(vm) { tab = 1 } } }
                    1 -> key(vm.selectedFamilyId) { FamilyLoadState(vm) { FamilyScreen(vm, share, if (vm.preview) null else pendingInvite) { pendingInvite = null } } }
                    2 -> InsightsScreen(vm) { tab = 1 }
                }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Forest)
        }
    }
    if (settings) SettingsDialog(vm, requestReminders) { settings = false }
}

@Composable
fun Welcome(vm: TrackerViewModel, signIn: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(100.dp).clip(RoundedCornerShape(32.dp)).background(Forest), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.NightsStay, null, tint = Color(0xFFE2EDCA), modifier = Modifier.size(54.dp)) }
        Spacer(Modifier.height(32.dp))
        Text("Small deeds.\nLasting growth.", color = Ink, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
        Text("A M A A L   T R A C K E R", color = Forest, fontSize = 12.sp, modifier = Modifier.padding(vertical = 20.dp))
        Text("A little more intention, every day. Track your amals, grow with your family, and celebrate consistency.", color = Muted, lineHeight = 25.sp)
        Spacer(Modifier.height(38.dp))
        Button(onClick = signIn, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Continue with Google", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = vm::startPreview, modifier = Modifier.fillMaxWidth()) { Text("Explore local preview") }
        Text("Your journey is personal. Your family is private.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
fun DailyScreen(vm: TrackerViewModel, openCircle: () -> Unit) {
    val familyId = vm.selectedFamilyId
    var date by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember { mutableStateOf<Amal?>(null) }
    val due = vm.amals.filter { it.occurs(date) }
    val mine = vm.entries.filter { it.uid == vm.uid && it.date == date.toString() }
    val done = due.count { a -> mine.any { it.amalId == a.id } }
    val animatedProgress by animateFloatAsState(targetValue = if (due.isEmpty()) 0f else done.toFloat() / due.size, label = "daily completion")
    val score = mine.filter { e -> due.any { it.id == e.amalId } }.sumOf { it.score }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text("Assalamu alaikum, ${vm.displayName.substringBefore(' ')}", fontSize = 13.sp, color = Muted)
            Text("Make today meaningful.", fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 5.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = date.minusDays(1) }, enabled = date > LocalDate.now().minusDays(34), modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronLeft, "Previous day", tint = Muted) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(HijrahDate.from(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)) + " AH", fontSize = 12.sp, color = Forest, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = { if (date < LocalDate.now()) date = date.plusDays(1) }, enabled = date < LocalDate.now(), modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronRight, "Next day", tint = Muted) }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Forest, Color(0xFF48755F)))).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR DAILY INTENTION", fontSize = 10.sp, color = Color(0xFFD0DFC9), letterSpacing = 1.6.sp)
                    Text("Every deed counts.", fontSize = 23.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Text("$done of ${due.size} amals completed", fontSize = 12.sp, color = Color(0xFFD7E5D6), modifier = Modifier.padding(top = 7.dp))
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFFE5DFAC), modifier = Modifier.size(15.dp)); Text("  $score intention points", color = Color(0xFFE5DFAC), fontSize = 11.sp) }
                }
                Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxSize(), color = Color(0xFFDBE8B4), trackColor = Color.White.copy(alpha = .15f), strokeWidth = 5.dp, strokeCap = StrokeCap.Round)
                    Text("${if (due.isEmpty()) 0 else done * 100 / due.size}%", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 22.sp)
                }
            }
        }
        if (vm.circle == null) SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Grow together", fontWeight = FontWeight.SemiBold); Text("Create or join a family to start tracking.", fontSize = 12.sp, color = Muted) }; TextButton(onClick = openCircle) { Text("Join") } } }
        Column {
            SectionTitle("The five daily prayers", "SALAH")
            Spacer(Modifier.height(12.dp))
            due.filter { it.category == "Prayer" }.forEachIndexed { index, amal ->
                val entry = mine.find { it.amalId == amal.id }
                val colors = listOf(Color(0xFFEDF0FA), Color(0xFFFBF2DC), Color(0xFFF8EAE1), Color(0xFFF1E9F5), Color(0xFFE6EDEB))
                Row(Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(18.dp)).background(colors[index % colors.size]).clickable(enabled = vm.circle != null) { selected = amal }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (index == 0 || index == 4) Icons.Outlined.NightsStay else Icons.Outlined.WbSunny, null, tint = Forest, modifier = Modifier.size(25.dp))
                    Column(Modifier.weight(1f).padding(start = 15.dp)) { Text(amal.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(entry?.status ?: "Pause. Pray. Reconnect.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 3.dp)) }
                    if (entry != null) Icon(Icons.Outlined.CheckCircle, "Recorded", tint = Forest) else Icon(Icons.Outlined.AddCircleOutline, "Record prayer", tint = Muted)
                }
            }
        }
        Column {
            SectionTitle("Nourish your soul", "DAILY AMALS")
            Spacer(Modifier.height(12.dp))
            due.filter { it.category != "Prayer" }.forEach { amal ->
                val entry = mine.find { it.amalId == amal.id }
                SurfaceCard(Modifier.padding(bottom = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (amal.category == "Tilawat") Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.FavoriteBorder, null, tint = Forest, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(amal.name, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text(if (amal.frequency == "weekly") "Weekly special · +10 points" else if (amal.frequency == "islamic") "Islamic day special · +10 points" else "${amal.category} · +10 points", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
                        Checkbox(checked = entry != null, onCheckedChange = { vm.track(amal, date, if (it) "Completed" else null, familyId) }, enabled = vm.circle != null)
                    }
                }
            }
        }
        Text("“The most beloved deeds to Allah are those done consistently, even if they are small.”\n— Sahih al-Bukhari 6464", color = Muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 24.dp))
    }
    selected?.let { amal ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("${amal.name} prayer") }, text = {
            Column {
                Text("Record how you prayed. Points are a personal reflection tool.", color = Muted, fontSize = 12.sp)
                prayerStatuses.forEach { (status, points) ->
                    TextButton(onClick = { vm.track(amal, date, status, familyId); selected = null }, modifier = Modifier.fillMaxWidth()) { Text(status, modifier = Modifier.weight(1f)); Text("+$points") }
                }
                TextButton(onClick = { vm.track(amal, date, null, familyId); selected = null }) { Text("Clear record") }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } })
    }
}

@Composable
fun EditAmalDialog(amal: Amal, save: (Amal) -> Unit, dismiss: () -> Unit, delete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(amal.name) }
    var category by remember { mutableStateOf(if (amal.id == "tilawat") "Tilawat" else amal.category) }
    var frequency by remember { mutableStateOf(if (amal.id in protectedAmalIds) "daily" else amal.frequency) }
    var day by remember { mutableStateOf(amal.weekday.toString()) }
    var month by remember { mutableStateOf(amal.hijriMonth.toString()) }
    var hijriDay by remember { mutableStateOf(amal.hijriDay.toString()) }
    var active by remember { mutableStateOf(amal.active) }
    val valid = name.isNotBlank() && (frequency != "weekly" || day.toIntOrNull() in 1..7) && (frequency != "islamic" || (month.toIntOrNull() in 1..12 && hijriDay.toIntOrNull() in 1..30))
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (amal.name.isEmpty()) "A new intention" else "Edit amal") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Amal name") }, singleLine = true)
            Text("Category", color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Common", "Azkar", "Tilawat", "Prayer").forEach { FilterChip(enabled = amal.id !in protectedAmalIds, selected = category == it, onClick = { category = it }, label = { Text(it, fontSize = 11.sp) }) } }
            Text("When should it appear?", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("daily", "weekly", "islamic").forEach { FilterChip(enabled = amal.id !in protectedAmalIds, selected = frequency == it, onClick = { frequency = it }, label = { Text(it.replaceFirstChar { c -> c.uppercase() }, fontSize = 11.sp) }) } }
            if (frequency == "weekly") OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("Weekday: 1 Mon – 7 Sun") }, singleLine = true)
            if (frequency == "islamic") { OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("Hijri month: 1–12") }, singleLine = true); OutlinedTextField(value = hijriDay, onValueChange = { hijriDay = it }, label = { Text("Hijri day: 1–30") }, singleLine = true) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Active", modifier = Modifier.weight(1f)); Switch(checked = active, onCheckedChange = { active = it }) }
            if (amal.id in protectedAmalIds) Text("This built-in amal is protected from deletion.", color = Muted, fontSize = 11.sp)
            if (delete != null) TextButton(onClick = delete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp)); Text("Delete amal", modifier = Modifier.padding(start = 7.dp)) }
        }
    }, confirmButton = { Button(onClick = { save(amal.copy(name = name.trim(), category = category, frequency = frequency, weekday = day.toIntOrNull() ?: 5, hijriMonth = month.toIntOrNull() ?: 9, hijriDay = hijriDay.toIntOrNull() ?: 1, active = active)) }, enabled = valid) { Text("Save amal") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun SettingsDialog(vm: TrackerViewModel, request: () -> Unit, dismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("amaal", 0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("reminders", false)) }
    var times by remember { mutableStateOf(prefs.getString("prayerTimes", "05:00,13:00,16:00,18:00,20:00")!!) }
    var nudge by remember { mutableStateOf(prefs.getString("nudgeTime", "21:00")!!) }
    val valid = ReminderScheduler.parseTimes(times).size == 5 && ReminderScheduler.parseTimes(nudge).size == 1
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your preferences") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Local reminders", modifier = Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it }) }
            Text("Set local prayer times in 24-hour format, in order: Fajr, Zuhr, Asr, Maghrib, Isha. Update these as prayer times change.", fontSize = 12.sp, color = Muted)
            OutlinedTextField(value = times, onValueChange = { times = it }, label = { Text("Five times, separated by commas") })
            OutlinedTextField(value = nudge, onValueChange = { nudge = it }, label = { Text("Daily tracking reminder (HH:mm)") }, singleLine = true)
            Text("Reminders work on this device. Android may delay them to save battery. Enable precise reminders for prayer alerts at the time you set.", fontSize = 11.sp, color = Muted)
            TextButton(onClick = { if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Enable precise reminders") }
            Text("Hijri dates use the Umm al-Qura calendar and may differ from local moon sightings. Family members can see each other's tracking records.", fontSize = 11.sp, color = Muted)
            TextButton(onClick = { vm.signOut(); dismiss() }) { Text(if (vm.preview) "Exit preview" else "Sign out") }
        }
    }, confirmButton = { Button(onClick = {
        prefs.edit().putBoolean("reminders", enabled).putString("prayerTimes", times).putString("nudgeTime", nudge).apply()
        if (enabled) request() else ReminderScheduler.cancel(context)
        dismiss()
    }, enabled = valid) { Text("Save preferences") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp), content = content)
}
@Composable
fun SectionTitle(title: String, eyebrow: String) { Column { Text(eyebrow, color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp); Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 5.dp)) } }
@Composable
fun PageTitle(title: String, subtitle: String) { Column { Text(title, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text(subtitle, fontSize = 12.sp, lineHeight = 19.sp, color = Muted, modifier = Modifier.padding(top = 8.dp)) } }
