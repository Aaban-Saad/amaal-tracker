package com.amaal.tracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.UUID

fun memberName(vm: TrackerViewModel, uid: String): String =
    vm.memberNames[uid]?.takeIf { it.isNotBlank() } ?: if (uid == vm.uid) vm.displayName else "Family member"

@Composable
fun MemberAvatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(CircleShape).background(Sage), contentAlignment = Alignment.Center) {
        Text(name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase(), fontWeight = FontWeight.SemiBold, color = Forest, fontSize = 15.sp)
    }
}

@Composable
fun FamilyScreen(vm: TrackerViewModel, share: (String) -> Unit, invite: String?, onInviteConsumed: () -> Unit = {}) {
    val family = vm.circle
    val isAdmin = family?.owner == vm.uid
    val context = LocalContext.current
    var editor by remember { mutableStateOf<Amal?>(null) }
    var deleting by remember { mutableStateOf<Amal?>(null) }
    var deletingFamily by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<String?>(if (invite != null) "join" else null) }
    var showArchived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (invite != null) onInviteConsumed() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("Faith grows in a family.", "Encourage each other, one small deed at a time.")
        if (family == null) {
            SurfaceCard {
                Icon(Icons.Outlined.PeopleOutline, null, tint = Forest, modifier = Modifier.size(42.dp))
                Text("A place to grow together", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                Text("Create your family or accept an invitation. Members share daily progress and weekly and monthly reviews.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 14.dp))
                Button(onClick = { setup = "create" }, modifier = Modifier.fillMaxWidth()) { Text("Create a family") }
                OutlinedButton(onClick = { setup = "join" }, modifier = Modifier.fillMaxWidth()) { Text("Join a family") }
            }
        } else {
            val today = LocalDate.now()
            val activeMembers = family.members.count { member -> vm.entries.any { it.uid == member && it.date == today.toString() } }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Forest, Color(0xFF527D62)))).padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Home, null, tint = Color(0xFFDDEACB), modifier = Modifier.size(22.dp))
                    Text("  YOUR FAMILY", color = Color(0xFFDDEACB), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                    if (isAdmin) IconButton(onClick = { renaming = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Edit, "Rename family", tint = Color.White, modifier = Modifier.size(19.dp)) }
                }
                Text(family.name, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Text("${family.members.size} member${if (family.members.size == 1) "" else "s"} · Private family", color = Color(0xFFDDEACB), fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Color.White.copy(alpha = .2f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Spa, null, tint = Color(0xFFDFE8B9), modifier = Modifier.size(20.dp))
                    Text("  $activeMembers of ${family.members.size} members recorded a deed today", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
            if (!vm.preview) SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Bring your family closer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text("Share this private invitation with someone you trust.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp) }
                    Icon(Icons.Outlined.FavoriteBorder, null, tint = Forest, modifier = Modifier.padding(start = 12.dp))
                }
                Row(Modifier.padding(top = 15.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cream).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(family.inviteCode, fontSize = 13.sp, letterSpacing = 1.sp, color = Forest, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Family invite code", family.inviteCode))
                        vm.message = "Family invite code copied."
                    }) { Icon(Icons.Outlined.ContentCopy, "Copy invite code", tint = Forest, modifier = Modifier.size(18.dp)) }
                }
                Button(onClick = { share(family.inviteCode) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Icon(Icons.Outlined.Share, null, modifier = Modifier.size(17.dp)); Text("  Invite a family member") }
            } else Text("Preview stays on this device. Sign in with Google to invite your family.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            SectionTitle("Everyone belongs here", "FAMILY MEMBERS · ${family.members.size}")
            family.members.sortedWith(compareBy<String> { it != family.owner }.thenBy { it != vm.uid }.thenBy { memberName(vm, it) }).forEach { member ->
                val name = memberName(vm, member)
                val percent = completion(vm.amals, vm.entries, today, member)
                val points = vm.entries.filter { it.uid == member && it.date == today.toString() }.sumOf { it.score }
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(name)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(name + if (member == vm.uid) " (you)" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (member == family.owner) "Family admin" else "Family member", fontSize = 11.sp, color = Muted)
                        }
                        Text("$percent%", color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                    }
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 15.dp).height(5.dp).clip(CircleShape), color = Forest, trackColor = Sage)
                    Text("Today's amals · $points points", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Your shared intentions", "FAMILY AMALS")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { editor = Amal(UUID.randomUUID().toString()) }, enabled = !vm.busy) { Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp)); Text("Add amal") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active (${vm.amals.count { it.active }})") })
                FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived (${vm.amals.count { !it.active }})") })
            }
            val visible = vm.amals.filter { it.active != showArchived }
            if (visible.isEmpty()) SurfaceCard { Text(if (showArchived) "No archived amals." else "No active amals. Add an intention to get started.", color = Muted, fontSize = 13.sp) }
            visible.forEach { amal ->
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (amal.category == "Prayer") Icons.Outlined.NightsStay else Icons.Outlined.Spa, null, tint = Forest, modifier = Modifier.size(21.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(amal.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("${amal.category} · ${when (amal.frequency) { "weekly" -> "Weekly"; "islamic" -> "Islamic occasion"; else -> "Daily" }}", fontSize = 11.sp, color = Muted)
                            if (!amal.canDelete()) Text("Protected built-in amal", fontSize = 10.sp, color = Forest)
                        }
                        run {
                            IconButton(onClick = { editor = amal }, enabled = !vm.busy, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Edit, "Edit ${amal.name}", tint = Muted, modifier = Modifier.size(18.dp)) }
                            if (isAdmin && amal.canDelete()) IconButton(onClick = { deleting = amal }, enabled = !vm.busy, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.DeleteOutline, "Delete ${amal.name}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp)) }
                        }
                    }
                }
            }
            Text(if (isAdmin) "Built-in salah and daily Quran Tilawat are protected. Other amals can be deleted; past records remain available in reviews." else "Every member can add, edit, activate or archive amals. Only the owner can delete amals or rename the family.", color = Muted, fontSize = 11.sp, lineHeight = 18.sp)
            if (isAdmin) TextButton(onClick = { deletingFamily = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Delete family", color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = { setup = if (vm.preview) "create" else "join" }, modifier = Modifier.fillMaxWidth()) { Text(if (vm.preview) "Create another local family" else "Create or join another family") }
        }
        Spacer(Modifier.height(20.dp))
    }
    editor?.let { amal ->
        EditAmalDialog(amal, { vm.saveAmal(it, family?.id); editor = null }, { editor = null },
            if (isAdmin && amal.name.isNotEmpty() && amal.canDelete()) ({ editor = null; deleting = amal }) else null)
    }
    deleting?.let { amal ->
        AlertDialog(onDismissRequest = { deleting = null }, icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${amal.name}?") },
            text = { Text("This removes the amal from your family's tracking list. Past tracking records stay in weekly and monthly reviews. Deletion cannot be undone.") },
            confirmButton = { Button(onClick = { vm.deleteAmal(amal, family?.id); deleting = null }, enabled = !vm.busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete amal") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep amal") } })
    }
    if (deletingFamily && family != null) {
        AlertDialog(onDismissRequest = { deletingFamily = false }, title = { Text("Delete ${family.name}?") },
            text = { Text("Permanently delete this family for every member, including all amals, tracking history and invitations. This cannot be undone.") },
            confirmButton = { Button(onClick = { deletingFamily = false; vm.deleteFamily(family.id) }, enabled = !vm.busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete permanently") } },
            dismissButton = { TextButton(onClick = { deletingFamily = false }) { Text("Cancel") } })
    }
    if (renaming && family != null) {
        var name by remember(family.id) { mutableStateOf(family.name) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Rename family") },
            text = { Column { Text("Give your family a name everyone recognizes.", color = Muted, fontSize = 12.sp); OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Family name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) } },
            confirmButton = { Button(onClick = { vm.renameFamily(name, family.id); renaming = false }, enabled = name.isNotBlank() && !vm.busy) { Text("Save name") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } })
    }
    setup?.let { mode ->
        FamilySetupDialog(vm, mode, invite, { setup = it }, { setup = null })
    }
}

@Composable
private fun FamilySetupDialog(vm: TrackerViewModel, mode: String, invite: String?, select: (String) -> Unit, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf(invite ?: "") }
    val originalId = remember { vm.circle?.id }
    LaunchedEffect(vm.circle?.id) { if (vm.circle != null && vm.circle?.id != originalId) dismiss() }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Find your family") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "create", onClick = { select("create") }, label = { Text("Create") })
                if (!vm.preview) FilterChip(selected = mode == "join", onClick = { select("join") }, label = { Text("Join") })
            }
            Text(if (vm.preview) "Create a separate local family to explore switching. Its records stay on this device." else if (mode == "create") "Start a private family and invite the people you love." else "Paste the code or invitation link shared by your family.", fontSize = 12.sp, color = Muted)
            if (mode == "create") OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Family name") }, singleLine = true)
            else OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Invite code or link") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = { if (mode == "create") vm.createCircle(name, dismiss) else vm.joinCircle(code, dismiss) }, enabled = !vm.busy && (if (mode == "create") name.isNotBlank() else code.isNotBlank())) { Text(if (mode == "create") "Create family" else "Join family") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
