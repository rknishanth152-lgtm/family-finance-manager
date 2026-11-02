package com.family.finance.data.crash

import android.content.Context
import android.os.Build
import com.family.finance.data.export.ExportService
import java.io.File
import java.time.Clock

/** What the crash log screen needs; a fake stands in for it in tests. */
interface CrashLogStore {
    fun read(): String
    fun clear()
}

/**
 * Saves the details of any crash to a private file on the phone, so it can be shared later
 * from More > Crash log. Nothing is ever sent anywhere by itself.
 */
class CrashLog(context: Context, private val clock: Clock = Clock.systemUTC()) : CrashLogStore {

    private val app = context.applicationContext
    private val file = File(app.filesDir, FILE_NAME)

    /** Records the crash, then lets Android close the app as it normally would. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Writing the log must never stop the normal crash handling.
            runCatching { record(thread.name, error) }
                .onFailure { android.util.Log.e("CrashLog", "could not record crash", it) }
            previous?.uncaughtException(thread, error)
        }
    }

    @Synchronized
    fun record(thread: String, error: Throwable) {
        val entry = CrashReport.format(clock.instant(), details(), thread, error)
        file.writeText(CrashReport.append(read(), entry))
    }

    @Synchronized
    override fun read(): String = if (file.exists()) file.readText() else ""

    @Synchronized
    override fun clear() {
        file.delete()
    }

    /** A copy in the shareable export folder, handed only to the app the person picks. */
    fun copyForSharing(): File {
        val dir = File(app.cacheDir, ExportService.EXPORT_DIR).apply { mkdirs() }
        return File(dir, SHARED_NAME).apply { writeText(read()) }
    }

    private fun details(): List<Pair<String, String>> {
        val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val versionCode = info?.versionCode?.toString().orEmpty()
        return listOf(
            "app" to "${info?.versionName.orEmpty()} ($versionCode)",
            "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    companion object {
        private const val FILE_NAME = "crash-log.txt"
        const val SHARED_NAME = "family-finance-crash-log.txt"
    }
}
