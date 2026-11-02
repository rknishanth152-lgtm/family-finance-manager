package com.family.finance

import androidx.test.platform.app.InstrumentationRegistry
import com.family.finance.di.AppContainer
import com.family.finance.data.session.SessionManager
import com.family.finance.data.session.SessionState
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Role
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.sync.ConflictDetector
import com.family.finance.domain.usecase.SaveResult
import com.family.finance.domain.validation.AccountDraft
import com.family.finance.domain.validation.ExpenseDraft
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDate

/**
 * Two phones, one family, against the local Firebase emulator (PRD 11.6, 15.2).
 * Each "phone" is a separate Firebase app instance with its own offline cache.
 *
 * Run with the emulator up and forwarded to the phone:
 *   adb reverse tcp:8080 tcp:8080 && adb reverse tcp:9099 tcp:9099
 *   firebase emulators:exec --project demo-family-finance --only auth,firestore "gradlew connectedDebugAndroidTest"
 * Skipped (not failed) when the emulator is not reachable.
 */
class FirebaseSyncTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val run = System.nanoTime().toString(36)
    private val phones = mutableListOf<Phone>()

    private inner class Phone(val name: String, val email: String) {
        val app: FirebaseApp = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setProjectId(PROJECT)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key")
                .build(),
            "$name-$run",
        )
        val auth: FirebaseAuth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        var db: FirebaseFirestore = newFirestore()
        lateinit var session: SessionManager
        /** Owns the session's background listeners so cleanup can stop them. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        fun newFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            firestoreSettings = firestoreSettings { setLocalCacheSettings(persistentCacheSettings {}) }
        }

        /** The Auth emulator accepts an unsigned Google ID token describing the account. */
        suspend fun signIn() {
            val token = """{"sub":"$name-$run","email":"$email","email_verified":true,"name":"${name.replaceFirstChar { it.uppercase() }}"}"""
            step("$name signs in") { auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await() }
            session = SessionManager(context, auth, db, scope = scope)
        }

        /**
         * Firestore runs operations in order, so writes made after this call are offline.
         * Its completion signal is not awaited: it can wait on in-flight traffic.
         */
        fun goOffline() { db.disableNetwork() }
        fun goOnline() { db.enableNetwork() }

        suspend fun ready(): AppContainer = step("$name's app is ready") {
            session.state.filterIsInstance<SessionState.Ready>().first().container
        }
    }

    private fun phone(name: String) = Phone(name, "$name.$run@example.com").also { phones += it }

    @Before fun emulatorIsRunning() {
        val reachable = runCatching { Socket().use { it.connect(InetSocketAddress(HOST, FIRESTORE_PORT), 1_000) } }.isSuccess
        assumeTrue("Firebase emulator not reachable; run via firebase emulators:exec with adb reverse", reachable)
    }

    @After fun cleanUp() {
        phones.forEach { p ->
            // Apps are not deleted: Firebase Auth cannot start for new apps after one is deleted.
            p.scope.cancel()
            runCatching { p.auth.signOut() }
            // Not awaited: waiting on terminate once blocked the runner for over an hour. Each
            // run uses fresh app names, and the test app (with its cache) is uninstalled after.
            runCatching { p.db.terminate() }
        }
    }

    /** Father creates the family and invites mother; mother joins. Returns both containers. */
    private suspend fun family(): Triple<Phone, Phone, Pair<AppContainer, AppContainer>> {
        val father = phone("father").apply { signIn() }
        step("father is allowed to start a family") { allowFounder(father.email) }
        val mother = phone("mother").apply { signIn() }
        step("father creates the family") { father.session.createFamily("Test family") }
        val fatherApp = father.ready()
        fatherApp.familyRepository.invite(mother.email)
        // The join screen listens for the invite, so it appears without refreshing.
        val invite = step("mother's phone shows the invite") {
            mother.session.state.filterIsInstance<SessionState.NeedsFamily>().first { it.invite != null }.invite!!
        }
        step("mother joins") { mother.session.joinFamily(invite) }
        val motherApp = mother.ready()
        step("father adds an account") { fatherApp.accountService.save(AccountId("cash"), AccountDraft("ரொக்கம்", AccountType.CASH, "1000", LocalDate.parse("2026-01-01"))) }
        return Triple(father, mother, fatherApp to motherApp)
    }

    /**
     * Adds an email to the founders allowlist the way the project owner does in the console.
     * The emulator accepts "Bearer owner" as an admin that bypasses the rules.
     */
    private suspend fun allowFounder(email: String) = withContext(Dispatchers.IO) {
        // Firestore REST "createDocument": POST to the collection with the document ID as a parameter.
        val id = java.net.URLEncoder.encode(email, "UTF-8")
        val url = java.net.URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT/databases/(default)/documents/founders?documentId=$id")
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer owner")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write("{\"fields\":{}}".toByteArray()) }
            check(conn.responseCode in 200..299) { "founders write failed: ${conn.responseCode}" }
        } finally {
            conn.disconnect()
        }
    }

    private fun expense(amount: String, title: String = "உரம்") =
        ExpenseDraft(amount, title, DefaultCategories.idFor("fertilizers"), LocalDate.parse("2026-11-01"), AccountId("cash"))

    private suspend fun AppContainer.waitFor(predicate: (List<com.family.finance.domain.model.Transaction>) -> Boolean) =
        step("transaction list updates") { transactionRepository.observeAll().first(predicate) }

    @Test fun inviteAndJoin_bothPhonesSeeTheSameFamily() = runBlocking<Unit> {
        val (_, _, apps) = family()
        val (fatherApp, motherApp) = apps
        assertThat(fatherApp.familyRepository.me.role).isEqualTo(Role.ADMIN)
        assertThat(motherApp.familyRepository.me.role).isEqualTo(Role.MEMBER)
        assertThat(motherApp.familyRepository.workspace.id).isEqualTo(fatherApp.familyRepository.workspace.id)
        val members = step("father sees two members") { fatherApp.familyRepository.observeMembers().first { it.size == 2 } }
        assertThat(members.map { it.role }).containsExactly(Role.ADMIN, Role.MEMBER)
    }

    @Test fun anEntryOnOnePhoneAppearsOnTheOther_withTamilTextAndExactPaise() = runBlocking<Unit> {
        val (_, _, apps) = family()
        val (fatherApp, motherApp) = apps
        fatherApp.transactionService.saveExpense(TransactionId("t-$run"), expense("2650.50", "யூரியா – 10 மூட்டை"))

        val seen = motherApp.waitFor { list -> list.any { it.id.value == "t-$run" } }.single { it.id.value == "t-$run" }
        assertThat(seen.amount).isEqualTo(Money(265_050))
        assertThat(seen.title).isEqualTo("யூரியா – 10 மூட்டை")
        assertThat(seen.createdBy).isEqualTo(fatherApp.currentUser())
    }

    @Test fun offlineEntry_isSavedAtOnceAndSyncsWhenBackOnline() = runBlocking<Unit> {
        val (father, _, apps) = family()
        val (fatherApp, motherApp) = apps
        father.goOffline()
        fatherApp.transactionService.saveExpense(TransactionId("off-$run"), expense("120"))

        // Saved on the phone straight away, while offline (PRD 4.5).
        fatherApp.waitFor { list -> list.any { it.id.value == "off-$run" } }
        delay(1_500)
        assertThat(motherApp.transactionRepository.observeAll().first().none { it.id.value == "off-$run" }).isTrue()

        father.goOnline()
        motherApp.waitFor { list -> list.any { it.id.value == "off-$run" } }
    }

    @Test fun pendingOfflineChange_survivesAnAppRestart() = runBlocking<Unit> {
        val (father, mother, _) = family()
        val fatherApp = father.ready()
        father.goOffline()
        fatherApp.transactionService.saveExpense(TransactionId("restart-$run"), expense("75"))
        fatherApp.waitFor { list -> list.any { it.id.value == "restart-$run" } }

        // "Restart": shut Firestore down and open it again from the phone's storage.
        // Not awaited: the shutdown confirmation can wait on the (disabled) network.
        father.db.terminate()
        delay(2_000)
        father.db = father.newFirestore()
        father.goOnline()

        mother.ready().waitFor { list -> list.any { it.id.value == "restart-$run" } }
    }

    @Test fun deletingOnOnePhoneRemovesItOnTheOther() = runBlocking<Unit> {
        val (_, _, apps) = family()
        val (fatherApp, motherApp) = apps
        val id = TransactionId("del-$run")
        fatherApp.transactionService.saveExpense(id, expense("10"))
        motherApp.waitFor { list -> list.any { it.id == id } }

        motherApp.transactionService.delete(id)
        fatherApp.waitFor { list -> list.none { it.id == id } }
    }

    @Test fun bothPhonesEditingTheSameEntryOffline_isFlaggedAndBothVersionsKept() = runBlocking<Unit> {
        val (father, mother, apps) = family()
        val (fatherApp, motherApp) = apps
        val id = TransactionId("clash-$run")
        fatherApp.transactionService.saveExpense(id, expense("100"))
        motherApp.waitFor { list -> list.any { it.id == id } }

        // Like the real app, where Home has already loaded the family's accounts on each phone.
        step("mother's phone has the accounts") { motherApp.accountRepository.observeAll().first { it.isNotEmpty() } }
        father.goOffline(); mother.goOffline()
        val fatherSave = step("father edits offline") { fatherApp.transactionService.saveExpense(id, expense("300")) }
        val motherSave = step("mother edits offline") { motherApp.transactionService.saveExpense(id, expense("400")) }
        assertThat(fatherSave).isInstanceOf(SaveResult.Saved::class.java)
        assertThat(motherSave).isInstanceOf(SaveResult.Saved::class.java)
        father.goOnline(); mother.goOnline()

        var lastSeen: List<com.family.finance.domain.sync.EditEntry> = emptyList()
        val log = withTimeoutOrNull(TIMEOUT) {
            motherApp.transactionRepository.observeEditLog().first {
                lastSeen = it[id].orEmpty()
                ConflictDetector.hasConflict(lastSeen)
            }
        } ?: throw AssertionError(
            "Mother never saw the conflict. Her edit log for this entry: " +
                lastSeen.joinToString { "v${it.baseVersion}->v${it.version} by ${it.editedBy.value.substringBefore('-')} amount=${it.state?.amount?.paise}" },
        )
        val competing = ConflictDetector.competingEdits(log.getValue(id))
        assertThat(competing.mapNotNull { it.state?.amount }).containsExactly(Money(30_000), Money(40_000))
        assertThat(competing.map { it.editedBy }.toSet()).containsExactly(fatherApp.currentUser(), motherApp.currentUser())

        // Saving again after seeing both versions closes the conflict, on both phones.
        motherApp.transactionService.confirm(id)
        step("conflict closes on father's phone") {
            fatherApp.transactionRepository.observeEditLog().first { !ConflictDetector.hasConflict(it[id].orEmpty()) && it[id].orEmpty().size >= 4 }
        }
    }

    /** A timed step whose name appears in the failure if it hangs. */
    private suspend fun <T> step(name: String, block: suspend () -> T): T {
        val start = System.nanoTime()
        val result = withTimeoutOrNull(TIMEOUT) { block() } ?: throw AssertionError("Timed out after ${TIMEOUT / 1000}s: $name")
        android.util.Log.i("FirebaseSyncTest", "step '$name' took ${(System.nanoTime() - start) / 1_000_000} ms")
        return result
    }

    private companion object {
        const val PROJECT = "demo-family-finance"
        const val HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099
        const val TIMEOUT = 20_000L
    }
}
