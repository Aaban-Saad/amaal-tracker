package com.amaal.tracker

import java.util.Locale

data class FamilyScopeToken(val familyId: String, val revision: Long)

/** Invalidates callbacks and queued edits even when switching A -> B -> A. */
class FamilyScopeGuard {
    private var revision = 0L
    var current: FamilyScopeToken? = null
        private set
    fun select(id: String): FamilyScopeToken = FamilyScopeToken(id, ++revision).also { current = it }
    fun clear() { revision++; current = null }
    fun accepts(token: FamilyScopeToken?): Boolean = token != null && token == current
}

fun joinedFamilies(families: List<Circle>, uid: String): List<Circle> = families
    .filter { uid in it.members }.distinctBy { it.id }
    .sortedWith(compareBy<Circle> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })

fun chooseFamily(families: List<Circle>, currentId: String?, preferredId: String?, requestedId: String? = null): Circle? =
    listOfNotNull(requestedId, currentId, preferredId).firstNotNullOfOrNull { id -> families.find { it.id == id } }
        ?: families.firstOrNull()

/** New memberships must be committed before activating their protected child collections. */
fun confirmedMemberships(families: List<Circle>, pendingIds: Set<String>, knownIds: Set<String>, uid: String): List<Circle> =
    joinedFamilies(families.filter { it.id !in pendingIds || it.id in knownIds }, uid)
