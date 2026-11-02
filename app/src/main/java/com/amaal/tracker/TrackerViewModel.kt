package com.amaal.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class TrackerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("amaal", 0)
    val configured = FirebaseApp.getApps(app).isNotEmpty()
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    var preview by mutableStateOf(false); private set
    var uid by mutableStateOf(if (configured) auth.currentUser?.uid ?: "" else ""); private set
    var displayName by mutableStateOf(if (configured) auth.currentUser?.displayName ?: "Friend" else "Friend"); private set
    var circle by mutableStateOf<Circle?>(null); private set
    var families by mutableStateOf(emptyList<Circle>()); private set
    var selectedFamilyId by mutableStateOf<String?>(null); private set
    var familiesLoading by mutableStateOf(false); private set
    var familyLoading by mutableStateOf(false); private set
    var familiesError by mutableStateOf<String?>(null); private set
    var familyError by mutableStateOf<String?>(null); private set
    private val familyScope = FamilyScopeGuard()
    private var membershipListener: ListenerRegistration? = null
    private var accountRevision = 0L
    private var preferredFamilyId: String? = null
    private var requestedFamilyId: String? = null
    private var reviewRevision = 0L
    var amals by mutableStateOf(defaultAmals); private set
    var entries by mutableStateOf(emptyList<Entry>()); private set
    var retiredAmals by mutableStateOf(emptyList<Amal>()); private set
    var reviewEntries by mutableStateOf(emptyList<Entry>()); private set
    var reviewLoading by mutableStateOf(false); private set
    var reviewError by mutableStateOf<String?>(null); private set
    private var reviewListener: ListenerRegistration? = null
    var memberNames by mutableStateOf(mapOf<String, String>()); private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    private val listeners = mutableListOf<ListenerRegistration>()
    init { if (uid.isNotEmpty()) observeUser() }
    private fun runAction(block: suspend () -> Unit) { viewModelScope.launch { busy = true; try { block() } catch (e: Exception) { message = e.localizedMessage ?: "Something went wrong. Please try again." }; busy = false } }
    // Firestore applies writes to its on-device cache and listeners immediately, then syncs them in the
    // background, even across app restarts. Waiting for the server would tie the UI to network speed.
    private fun runInstant(block: () -> Unit) { try { block() } catch (e: Exception) { message = e.localizedMessage ?: "Something went wrong. Please try again." } }
    private fun syncInBackground(write: Task<Void>) { write.addOnFailureListener { message = it.localizedMessage ?: "A change couldn't be saved. Please try again." } }
    @android.annotation.SuppressLint("DiscouragedApi") // Google generates this optional resource only when OAuth is configured.
    fun signIn(activity: android.app.Activity) = runAction {
        check(configured) { "Add your Firebase google-services.json and rebuild to enable Google sign-in." }
        val app = getApplication<Application>()
        val clientIdResource = app.resources.getIdentifier("default_web_client_id", "string", app.packageName)
        val clientId = if (clientIdResource == 0) "" else app.getString(clientIdResource)
        check(clientId.isNotBlank()) { "Enable Google sign-in in Firebase and download the updated configuration." }
        val option = GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(false).build()
        val response = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Unexpected credential type from Google sign-in." }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user!!
        uid = user.uid; displayName = user.displayName ?: "Friend"
        syncInBackground(db.collection("users").document(uid).set(mapOf("name" to displayName), com.google.firebase.firestore.SetOptions.merge()))
        observeUser()
    }
    fun startPreview() {
        preview = true; uid = "preview"; displayName = "Friend"
        val stored = JSONArray(prefs.getString("previewFamilies", "[]"))
        families = if (!prefs.contains("previewFamilies")) listOf(Circle("preview", prefs.getString("familyName", "My family")!!, uid, "LOCAL", listOf(uid)))
        else (0 until stored.length()).map { stored.getJSONObject(it).let { o -> Circle(o.getString("id"), o.getString("name"), uid, "LOCAL", listOf(uid)) } }
        families = joinedFamilies(families, uid)
        preferredFamilyId = prefs.getString("activePreviewFamily", "preview")
        chooseFamily(families, null, preferredFamilyId)?.let { activateFamily(it) }
    }
    private fun previewKey(key: String, id: String = selectedFamilyId ?: "preview"): String = if (id == "preview") key else "${id}_$key"
    private fun loadPreviewFamily() {
        val raw = JSONArray(prefs.getString(previewKey("entries"), "[]"))
        entries = (0 until raw.length()).map { raw.getJSONObject(it).let { o -> Entry(o.getString("id"), uid, o.getString("amalId"), o.getString("date"), o.getString("status"), o.getLong("score")) } }
        val retired = JSONArray(prefs.getString(previewKey("retiredAmals"), "[]"))
        retiredAmals = (0 until retired.length()).map { retired.getJSONObject(it).let { o -> Amal(o.getString("id"), o.getString("name"), o.getString("category")) } }
        val custom = JSONArray(prefs.getString(previewKey("amals"), "[]"))
        amals = if (!prefs.contains(previewKey("amals"))) defaultAmals else (0 until custom.length()).map { custom.getJSONObject(it).let { o -> Amal(o.getString("id"), o.getString("name"), o.getString("category"), o.getString("frequency"), o.getInt("weekday"), o.getInt("hijriMonth"), o.getInt("hijriDay"), o.getBoolean("active")) } }
        memberNames = mapOf(uid to displayName)
        familyLoading = false
        updateReminderCache()
    }
    private fun persistPreviewFamilies() {
        prefs.edit().putString("previewFamilies", JSONArray(families.map { JSONObject(mapOf("id" to it.id, "name" to it.name)) }).toString()).apply()
    }
    private fun observeUser() {
        membershipListener?.remove()
        val account = uid
        val revision = ++accountRevision
        familiesLoading = true; familiesError = null
        val savedFamilyId = prefs.getString("activeFamily_$account", null)
        if (savedFamilyId != null) { preferredFamilyId = savedFamilyId; observeMemberships(account, revision); return }
        // Only a new device needs the account's last family; memberships report any real load failure.
        db.collection("users").document(account).get().addOnCompleteListener { task ->
            if (uid != account || revision != accountRevision) return@addOnCompleteListener
            preferredFamilyId = if (task.isSuccessful) task.result.getString("circleId") else null
            observeMemberships(account, revision)
        }
    }
    private fun observeMemberships(account: String, revision: Long) {
        membershipListener?.remove()
        membershipListener = db.collection("circles").whereArrayContains("members", account).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (uid != account || revision != accountRevision) return@addSnapshotListener
            familiesLoading = false
            if (error != null) {
                familiesError = error.localizedMessage ?: "Your families couldn't load."
                families = emptyList(); clearFamily(); return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            familiesError = null
            families = confirmedMemberships(
                snap.documents.mapNotNull { it.toObject(Circle::class.java)?.copy(id = it.id) },
                snap.documents.filter { it.metadata.hasPendingWrites() }.map { it.id }.toSet(),
                families.map { it.id }.toSet(), account
            )
            val selected = chooseFamily(families, selectedFamilyId, preferredFamilyId, requestedFamilyId)
            if (selected == null) clearFamily()
            else if (selected.id != selectedFamilyId) activateFamily(selected)
            else {
                circle = selected // Reflect name/member changes without restarting data listeners.
                if (selected.deleting) prefs.edit().remove("cacheDate").remove("remaining").apply()
            }
            if (selected?.id == requestedFamilyId) requestedFamilyId = null
        }
    }
    fun selectFamily(id: String) {
        if (busy || id == selectedFamilyId) return
        val selected = families.find { it.id == id && uid in it.members } ?: return
        activateFamily(selected)
    }
    private fun requestFamily(id: String) {
        if (selectedFamilyId == id) { requestedFamilyId = null; return }
        requestedFamilyId = id
        families.find { it.id == id && uid in it.members }?.let {
            activateFamily(it); requestedFamilyId = null
        }
    }
    private fun clearFamily() {
        familyScope.clear()
        listeners.forEach { it.remove() }; listeners.clear()
        stopReview(); circle = null; selectedFamilyId = null
        entries = emptyList(); amals = emptyList(); retiredAmals = emptyList(); memberNames = emptyMap()
        familyLoading = false; familyError = null
        prefs.edit().remove("cacheDate").remove("remaining").apply()
    }
    private fun activateFamily(family: Circle) {
        if (preview && circle != null && !familyLoading) persist()
        clearFamily()
        val token = familyScope.select(family.id)
        selectedFamilyId = family.id; circle = family; preferredFamilyId = family.id
        familyLoading = true
        if (preview) {
            prefs.edit().putString("activePreviewFamily", family.id).apply()
            persistPreviewFamilies(); loadPreviewFamily(); return
        }
        prefs.edit().putString("activeFamily_$uid", family.id).apply()
        val ref = db.collection("circles").document(family.id)
        val ready = mutableSetOf<String>()
        fun listen(name: String, query: Query, apply: (QuerySnapshot) -> Unit) {
            listeners += query.addSnapshotListener { snapshot, error ->
                if (!familyScope.accepts(token)) return@addSnapshotListener
                if (error != null) {
                    familyError = error.localizedMessage ?: "This family's data couldn't load."
                    familyLoading = false; return@addSnapshotListener
                }
                if (snapshot != null) {
                    apply(snapshot); ready += name
                    if (ready.size == 4 && familyError == null) {
                        familyLoading = false; updateReminderCache()
                    }
                }
            }
        }
        listen("amals", ref.collection("amals")) { snapshot ->
            amals = snapshot.documents.mapNotNull { it.toObject(Amal::class.java)?.copy(id = it.id) }.sortedBy { it.id }
        }
        listen("retired", ref.collection("retiredAmals")) { snapshot ->
            retiredAmals = snapshot.documents.mapNotNull { it.toObject(Amal::class.java)?.copy(id = it.id) }
        }
        listen("logs", ref.collection("logs").whereGreaterThanOrEqualTo("date", LocalDate.now().minusDays(35).toString())) { snapshot ->
            entries = snapshot.documents.mapNotNull { it.toObject(Entry::class.java)?.copy(id = it.id) }
        }
        listen("profiles", ref.collection("profiles")) { snapshot ->
            memberNames = snapshot.documents.associate { it.id to (it.getString("name") ?: "Family member") }
        }
    }
    fun retryFamilies() {
        if (preview) return
        if (familiesError != null || selectedFamilyId == null) observeUser()
        else families.find { it.id == selectedFamilyId }?.let { activateFamily(it) }
    }
    private fun editableFamily(token: FamilyScopeToken?, expectedId: String?): Circle {
        check(familyScope.accepts(token) && expectedId == selectedFamilyId && !familyLoading && familyError == null) { "The selected family changed or is still loading. Please try again." }
        return (circle ?: error("Select a family first.")).also { check(!it.deleting) { "This family is being deleted." } }
    }
    fun observeReview(period: ReviewPeriod) {
        stopReview()
        val familyId = circle?.id ?: return
        if (preview) { reviewEntries = entries; return }
        val token = familyScope.current
        val revision = ++reviewRevision
        reviewLoading = true
        reviewListener = db.collection("circles").document(familyId).collection("logs")
            .whereGreaterThanOrEqualTo("date", period.start.toString())
            .whereLessThanOrEqualTo("date", minOf(period.end, LocalDate.now()).toString())
            .addSnapshotListener { snap, error ->
                if (!familyScope.accepts(token) || revision != reviewRevision) return@addSnapshotListener
                reviewLoading = false
                reviewError = error?.localizedMessage
                if (snap != null) reviewEntries = snap.documents.mapNotNull { it.toObject(Entry::class.java)?.copy(id = it.id) }
            }
    }
    fun stopReview() {
        reviewRevision++
        reviewListener?.remove(); reviewListener = null
        reviewEntries = emptyList(); reviewLoading = false; reviewError = null
    }
    fun renameFamily(name: String, familyId: String? = selectedFamilyId) {
        val token = familyScope.current
        runInstant {
            val family = editableFamily(token, familyId)
            check(family.owner == uid) { "Only the family admin can rename the family." }
            val trimmed = name.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= 80) { "Use a family name between 1 and 80 characters." }
            if (preview) {
                circle = family.copy(name = trimmed)
                families = joinedFamilies(families.map { if (it.id == family.id) circle!! else it }, uid)
                prefs.edit().putString(previewKey("familyName"), trimmed).apply(); persistPreviewFamilies()
            }
            else syncInBackground(db.collection("circles").document(family.id).update("name", trimmed))
        }
    }
    fun deleteFamily(familyId: String? = selectedFamilyId) {
        val token = familyScope.current
        runAction {
            check(familyScope.accepts(token) && familyId == selectedFamilyId) { "Select the family again." }
            val family = circle ?: error("Select a family first.")
            check(family.owner == uid) { "Only the owner can delete a family." }
            if (preview) {
                clearFamily() // Do not persist deleted records when activating the next family.
                val edit = prefs.edit()
                listOf("entries", "amals", "retiredAmals", "familyName").forEach { edit.remove(previewKey(it, family.id)) }
                edit.remove("activePreviewFamily").apply()
                families = families.filterNot { it.id == family.id }
                persistPreviewFamilies()
                families.firstOrNull()?.let { activateFamily(it) }
            } else {
                val ref = db.collection("circles").document(family.id)
                // Freeze all new writes before cleanup. Keep the parent until all pages finish.
                ref.update("deleting", true).await()
                for (collection in listOf("logs", "amals", "retiredAmals", "profiles")) {
                    while (true) {
                        val page = ref.collection(collection).limit(200).get(com.google.firebase.firestore.Source.SERVER).await()
                        if (page.isEmpty) break
                        db.runBatch { batch -> page.documents.forEach { batch.delete(it.reference) } }.await()
                    }
                }
                db.runBatch { batch ->
                    batch.delete(db.collection("invites").document(family.inviteCode))
                    batch.delete(ref)
                }.await()
            }
            message = "Family and its tracking history deleted."
        }
    }
    fun deleteAmal(amal: Amal, familyId: String? = selectedFamilyId) {
        val token = familyScope.current
        runAction {
            val family = editableFamily(token, familyId)
            check(family.owner == uid) { "Only the family admin can delete amals." }
            check(amal.canDelete()) { "Built-in salah and daily Quran Tilawat cannot be deleted." }
            if (preview) {
                retiredAmals = retiredAmals.filterNot { it.id == amal.id } + amal
                amals = amals.filterNot { it.id == amal.id }
                persist(); updateReminderCache()
            } else {
                val ref = db.collection("circles").document(family.id)
                // Retain the definition for historical reviews, while removing the active tracking item.
                db.runTransaction { transaction ->
                    val activeRef = ref.collection("amals").document(amal.id)
                    val snapshot = transaction.get(activeRef)
                    check(snapshot.exists()) { "This amal has already been deleted." }
                    transaction.set(ref.collection("retiredAmals").document(amal.id), snapshot.data!!)
                    transaction.delete(activeRef)
                }.await()
            }
            message = "Amal deleted. Past records remain in reviews."
        }
    }
    fun createCircle(name: String, onSuccess: () -> Unit = {}) = runAction {
        require(name.trim().isNotEmpty() && name.trim().length <= 80) { "Use a family name between 1 and 80 characters." }
        if (preview) {
            val family = Circle("preview_" + UUID.randomUUID(), name.trim(), uid, "LOCAL", listOf(uid))
            families = joinedFamilies(families + family, uid)
            activateFamily(family); onSuccess(); return@runAction
        }
        val ref = db.collection("circles").document()
        val code = UUID.randomUUID().toString().replace("-", "").take(16).uppercase()
        val c = Circle(ref.id, name.trim(), uid, code, listOf(uid))
        val batch = db.batch(); batch.set(ref, c)
        batch.set(db.collection("invites").document(code), mapOf("circleId" to ref.id, "owner" to uid))
        defaultAmals.forEach { batch.set(ref.collection("amals").document(it.id), it) }
        batch.set(ref.collection("profiles").document(uid), mapOf("name" to displayName))
        batch.set(db.collection("users").document(uid), mapOf("circleId" to ref.id), com.google.firebase.firestore.SetOptions.merge())
        batch.commit().await(); requestFamily(ref.id); onSuccess()
    }
    fun joinCircle(input: String, onSuccess: () -> Unit = {}) = runAction {
        check(!preview) { "Sign in with Google to join a shared family." }
        val code = input.substringAfterLast('/').substringBefore('?').trim().uppercase()
        val invite = db.collection("invites").document(code).get().await()
        val id = invite.getString("circleId") ?: error("Invite not found. Check your family code.")
        val ref = db.collection("circles").document(id)
        db.runBatch { batch ->
            batch.update(ref, mapOf("members" to FieldValue.arrayUnion(uid), "joinProof" to code))
            batch.set(ref.collection("profiles").document(uid), mapOf("name" to displayName))
            batch.set(db.collection("users").document(uid), mapOf("circleId" to id), com.google.firebase.firestore.SetOptions.merge())
        }.await()
        requestFamily(id); onSuccess()
    }
    fun track(amal: Amal, date: LocalDate, status: String?, familyId: String? = selectedFamilyId) {
        val token = familyScope.current
        runInstant {
            val family = editableFamily(token, familyId)
            val id = "${uid}_${date}_${amal.id}"
            val entry = Entry(id, uid, amal.id, date.toString(), status ?: "", if (amal.category == "Prayer") prayerStatuses[status] ?: 0L else 10L)
            if (preview) { entries = entries.filterNot { it.id == id } + if (status != null) listOf(entry) else emptyList(); persist(); updateReminderCache() }
            else {
                val ref = db.collection("circles").document(family.id).collection("logs").document(id)
                syncInBackground(if (status == null) ref.delete() else ref.set(entry))
            }
        }
    }
    fun saveAmal(amal: Amal, familyId: String? = selectedFamilyId) {
        val token = familyScope.current
        runInstant {
            val family = editableFamily(token, familyId)
            check(uid in family.members) { "Only family members can edit amals." }
            check(amal.id !in protectedPrayerIds || (amal.category == "Prayer" && amal.frequency == "daily")) { "Built-in salah must remain daily prayers." }
            check(amal.id != "tilawat" || (amal.category == "Tilawat" && amal.frequency == "daily")) { "Built-in Quran Tilawat must remain daily Tilawat." }
            if (preview) { amals = amals.filterNot { it.id == amal.id } + amal; persist(); updateReminderCache() }
            else syncInBackground(db.collection("circles").document(family.id).collection("amals").document(amal.id).set(amal))
        }
    }
    private fun persist() {
        prefs.edit().putString(previewKey("retiredAmals"), JSONArray(retiredAmals.map { JSONObject(mapOf("id" to it.id, "name" to it.name, "category" to it.category)) }).toString()).putString(previewKey("entries"), JSONArray(entries.map { JSONObject(mapOf("id" to it.id, "amalId" to it.amalId, "date" to it.date, "status" to it.status, "score" to it.score)) }).toString())
            .putString(previewKey("amals"), JSONArray(amals.map { JSONObject(mapOf("id" to it.id, "name" to it.name, "category" to it.category, "frequency" to it.frequency, "weekday" to it.weekday, "hijriMonth" to it.hijriMonth, "hijriDay" to it.hijriDay, "active" to it.active)) }).toString()).apply()
    }
    private fun updateReminderCache() {
        if (circle == null || circle?.deleting == true || familyLoading || familyError != null) return
        val today = LocalDate.now()
        prefs.edit().putString("cacheDate", today.toString()).putInt("remaining", amals.count { it.occurs(today) && entries.none { e -> e.uid == uid && e.date == today.toString() && e.amalId == it.id } }).apply()
    }
    fun signOut() {
        accountRevision++; membershipListener?.remove()
        if (configured && !preview) auth.signOut()
        clearFamily(); uid = ""; preview = false; families = emptyList()
        familiesLoading = false; familiesError = null; preferredFamilyId = null; requestedFamilyId = null
        prefs.edit().putBoolean("reminders", false).apply(); ReminderScheduler.cancel(getApplication())
    }
    override fun onCleared() {
        accountRevision++; membershipListener?.remove()
        familyScope.clear(); listeners.forEach { it.remove() }; stopReview()
    }
}
