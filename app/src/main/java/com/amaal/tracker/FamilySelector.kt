package com.amaal.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared above the tab content, so selection is available on all three tabs. */
@Composable
fun FamilySelector(vm: TrackerViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = vm.families.isNotEmpty() && !vm.busy,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Select family" },
            shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Sage),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Sage, contentColor = Forest),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Icon(Icons.Outlined.Home, null, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(vm.circle?.name ?: if (vm.familiesLoading) "Finding your families…" else "No family selected", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(if (vm.familiesLoading) "Please wait" else "${vm.families.size} joined ${if (vm.families.size == 1) "family" else "families"}", fontSize = 10.sp, lineHeight = 15.sp, color = Muted)
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            vm.families.forEach { family ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(family.name, fontWeight = if (family.id == vm.selectedFamilyId) FontWeight.SemiBold else FontWeight.Normal)
                            Text("${family.members.size} member${if (family.members.size == 1) "" else "s"} · ${if (family.owner == vm.uid) "Admin" else "Member"}", color = Muted, fontSize = 11.sp)
                        }
                    },
                    onClick = { expanded = false; vm.selectFamily(family.id) },
                    leadingIcon = { Icon(Icons.Outlined.PeopleOutline, null, tint = Forest) },
                    trailingIcon = { if (family.id == vm.selectedFamilyId) Icon(Icons.Outlined.Check, "Selected", tint = Forest) }
                )
            }
        }
    }
}

@Composable
fun FamilyLoadState(vm: TrackerViewModel, content: @Composable () -> Unit) {
    val error = vm.familiesError ?: vm.familyError
    when {
        vm.circle?.deleting == true -> Column(Modifier.padding(horizontal = 24.dp)) {
            SurfaceCard {
                Text("Family deletion in progress", fontWeight = FontWeight.SemiBold)
                Text("Tracking and editing are disabled. The owner can finish deletion if it was interrupted.", color = Muted, fontSize = 12.sp)
                if (vm.circle?.owner == vm.uid) TextButton(onClick = { vm.deleteFamily() }, enabled = !vm.busy) {
                    Text(if (vm.busy) "Deleting…" else "Finish deleting family")
                }
            }
        }
        error != null -> Column(Modifier.padding(horizontal = 24.dp)) {
            SurfaceCard {
                Text("Your family couldn't load", fontWeight = FontWeight.SemiBold)
                Text(error, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = vm::retryFamilies) { Text("Retry families") }
            }
        }
        vm.familiesLoading || vm.familyLoading -> Column(Modifier.padding(horizontal = 24.dp)) {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Forest)
                    Text(if (vm.familiesLoading) "Finding your families…" else "Loading ${vm.circle?.name ?: "family"}…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
        else -> content()
    }
}
