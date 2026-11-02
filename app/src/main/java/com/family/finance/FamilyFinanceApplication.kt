package com.family.finance

import android.app.Application
import com.family.finance.data.SessionManager
import com.family.finance.data.crash.CrashLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class FamilyFinanceApplication : Application() {

    /** Private on-phone record of crashes, shared only from More > Crash log. */
    val crashLog: CrashLog by lazy { CrashLog(this) }

    override fun onCreate() {
        super.onCreate()
        // First thing, so a crash anywhere afterwards is recorded.
        crashLog.install()
    }

    /**
     * Created on first use, so instrumented tests can point Firebase at the local emulator first.
     * Firestore keeps a persistent copy on the phone: records open offline, and changes made
     * offline survive restarts and sync later (PRD 4.3, 4.5, 11.3).
     */
    val session: SessionManager by lazy {
        val db = FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings { setLocalCacheSettings(persistentCacheSettings {}) }
        }
        SessionManager(this, FirebaseAuth.getInstance(), db)
    }
}
