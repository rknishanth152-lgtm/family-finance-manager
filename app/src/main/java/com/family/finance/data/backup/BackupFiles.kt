package com.family.finance.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes backup files at places the person picked in the system file dialogs. */
class BackupFiles(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = checkNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot write to $uri" }
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Refuses files larger than [MAX_BYTES], so a wrong pick (a video, say) cannot exhaust memory. */
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = checkNotNull(resolver.openInputStream(uri)) { "Cannot read $uri" }
        input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                require(out.size() <= MAX_BYTES) { "File too large" }
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private companion object {
        /** Decades of entries fit in a few megabytes. */
        const val MAX_BYTES = 50 * 1024 * 1024
    }
}
