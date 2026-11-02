package com.family.finance.data.crash

import java.time.Instant

/**
 * The text of the on-phone crash log. Kept free of Android types so the rules are unit-tested.
 *
 * Each entry holds only technical facts: when, which app and Android version, which phone
 * model, and where in the code it failed. No amounts, names, notes or other family records
 * are ever written. Error messages are cut short, since a message could quote a value.
 */
object CrashReport {

    /** Only the most recent crashes are kept, so the file stays small. */
    const val MAX_ENTRIES = 10
    private const val MAX_MESSAGE = 200
    private const val MAX_FRAMES = 40
    private const val HEADER = "===== crash ====="
    private const val TIME = "time: "

    fun format(at: Instant, details: List<Pair<String, String>>, thread: String, error: Throwable): String = buildString {
        appendLine(HEADER)
        appendLine("$TIME$at")
        details.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine("thread: $thread")
        var cause: Throwable? = error
        var first = true
        val seen = mutableSetOf<Throwable>()
        while (cause != null && seen.add(cause)) {
            append(if (first) "" else "caused by: ")
            append(cause.javaClass.name)
            cause.message?.let { append(": ").append(it.take(MAX_MESSAGE).replace('\n', ' ')) }
            appendLine()
            cause.stackTrace.take(MAX_FRAMES).forEach { appendLine("    at $it") }
            first = false
            cause = cause.cause
        }
    }

    /** Adds [entry] to the log, dropping the oldest entries beyond [MAX_ENTRIES]. */
    fun append(log: String, entry: String): String =
        (entries(log) + entry.trimEnd()).takeLast(MAX_ENTRIES).joinToString("\n\n", postfix = "\n")

    fun count(log: String): Int = entries(log).size

    /** When the most recent crash happened, or null if there is none. */
    fun lastCrashAt(log: String): Instant? = entries(log).lastOrNull()
        ?.lineSequence()?.firstOrNull { it.startsWith(TIME) }
        ?.let { runCatching { Instant.parse(it.removePrefix(TIME)) }.getOrNull() }

    private fun entries(log: String): List<String> =
        log.split(HEADER).map { it.trim() }.filter { it.isNotEmpty() }.map { "$HEADER\n$it" }
}
