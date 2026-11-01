package com.family.finance.domain.usecase

import java.text.Normalizer
import java.util.Locale

/** Shared text matching for transaction and notebook search (PRD 7.5). */
object TextSearch {
    /** NFC so the same Tamil word typed with different keyboards still matches. */
    fun normalize(s: String): String = Normalizer.normalize(s.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun matches(haystack: String, query: String): Boolean {
        val needle = normalize(query)
        return needle.isEmpty() || normalize(haystack).contains(needle)
    }
}
