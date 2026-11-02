package com.family.finance.domain.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonTest {

    @Test fun `round trip of every supported value`() {
        val value = linkedMapOf(
            "text" to "தமிழ் \"quoted\" \\ back\nline\ttab\u0001",
            "long" to Long.MAX_VALUE,
            "negative" to -12_345L,
            "yes" to true,
            "no" to false,
            "nothing" to null,
            "list" to listOf(1L, "a", null, listOf<Any?>(), linkedMapOf<String, Any?>()),
        )
        assertThat(Json.parse(Json.write(value))).isEqualTo(value)
    }

    @Test fun `Tamil is written as plain UTF-8, not escaped`() {
        assertThat(Json.write("ஸ்ரீ")).isEqualTo("\"ஸ்ரீ\"")
    }

    @Test fun `decimals are rejected so money stays whole paise`() {
        assertThrows(Json.ParseException::class.java) { Json.parse("12.5") }
        assertThrows(Json.ParseException::class.java) { Json.parse("1e3") }
    }

    @Test fun `malformed input is rejected, never half-read`() {
        for (bad in listOf("", "{", "[1,]", "{\"a\":1,}", "{\"a\" 1}", "\"open", "tru", "[1] x", "{\"a\":1,\"a\":2}", "\"bad \\q\"", "99999999999999999999")) {
            assertThrows(bad, Json.ParseException::class.java) { Json.parse(bad) }
        }
    }

    @Test fun `deep nesting is refused`() {
        assertThrows(Json.ParseException::class.java) { Json.parse("[".repeat(100) + "]".repeat(100)) }
    }

    @Test fun `unicode escapes are understood`() {
        assertThat(Json.parse("\"\\u0BA4\"")).isEqualTo("த")
    }
}
