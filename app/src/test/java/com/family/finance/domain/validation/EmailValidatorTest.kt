package com.family.finance.domain.validation

import com.family.finance.domain.model.normalizeEmail
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmailValidatorTest {
    @Test fun `accepts normal addresses, with surrounding spaces trimmed`() {
        listOf("amma@gmail.com", " Appa.Family@Gmail.com ", "name+tag@example.co.in").forEach {
            assertThat(EmailValidator.isValid(it)).isTrue()
        }
    }

    @Test fun `rejects things that are not email addresses`() {
        listOf("", "amma", "amma@", "@gmail.com", "amma@gmail", "am ma@gmail.com", "amma@gmail.c").forEach {
            assertThat(EmailValidator.isValid(it)).isFalse()
        }
    }

    @Test fun `emails are compared case-insensitively, as the security rules do`() {
        assertThat(normalizeEmail(" Amma@Gmail.COM ")).isEqualTo("amma@gmail.com")
    }
}
