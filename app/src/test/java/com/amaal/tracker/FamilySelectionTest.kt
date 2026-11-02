package com.amaal.tracker

import org.junit.Assert.*
import org.junit.Test

class FamilySelectionTest {
    private val a = Circle("a", "Home", "me", members = listOf("me"))
    private val b = Circle("b", "Friends", "other", members = listOf("other", "me"))
    private val foreign = Circle("private", "Private", "outsider", members = listOf("outsider"))

    @Test fun findsAllJoinedFamiliesIncludingOlderMemberships() {
        val result = joinedFamilies(listOf(a, foreign, b, a), "me")
        assertEquals(listOf("b", "a"), result.map { it.id })
    }
    @Test fun restoresSavedSelectionAndKeepsItWhenAnotherFamilyIsRenamed() {
        val joined = joinedFamilies(listOf(a, b), "me")
        assertEquals(a, chooseFamily(joined, null, "a"))
        val renamed = joinedFamilies(listOf(a, b.copy(name = "A new name")), "me")
        assertEquals("a", chooseFamily(renamed, "a", "b")?.id)
    }
    @Test fun newlyJoinedFamilyCanBecomeSelectedWithoutLosingOlderOnes() {
        val joined = joinedFamilies(listOf(a, b), "me")
        assertEquals("b", chooseFamily(joined, "a", "a", "b")?.id)
        assertEquals(2, joined.size)
    }
    @Test fun missingOrRevokedMembershipFallsBackOnlyToAnotherJoinedFamily() {
        val joined = joinedFamilies(listOf(b, foreign), "me")
        assertEquals(b, chooseFamily(joined, "a", "private"))
        assertNull(chooseFamily(emptyList(), "a", "a"))
    }
    @Test fun switchingDiscardsPendingCallbacksAndEditsFromOtherFamilies() {
        val guard = FamilyScopeGuard()
        val old = guard.select("a")
        assertTrue(guard.accepts(old))
        val selected = guard.select("b")
        assertFalse(guard.accepts(old))
        assertTrue(guard.accepts(selected))
        val back = guard.select("a")
        assertFalse(guard.accepts(old))
        assertFalse(guard.accepts(selected))
        assertTrue(guard.accepts(back))
        guard.clear()
        assertFalse(guard.accepts(back))
        assertFalse(guard.accepts(null))
    }
    @Test fun duplicateNamesDoNotMergeDifferentFamilies() {
        val joined = joinedFamilies(listOf(a, b.copy(name = a.name)), "me")
        assertEquals(2, joined.size)
        assertEquals("b", chooseFamily(joined, null, "b")?.id)
    }
    @Test fun unconfirmedJoinsWaitForCommitWhileExistingFamilyEditsStayVisible() {
        assertEquals(listOf("a"), confirmedMemberships(listOf(a, b), setOf("b"), setOf("a"), "me").map { it.id })
        assertEquals(2, confirmedMemberships(listOf(a, b), emptySet(), setOf("a"), "me").size)
        assertEquals(listOf("a"), confirmedMemberships(listOf(a.copy(name = "New name"), foreign), setOf("a"), setOf("a", "private"), "me").map { it.id })
    }
}
