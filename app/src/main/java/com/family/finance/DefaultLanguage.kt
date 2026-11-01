package com.family.finance

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Tamil is the default interface language (PRD 7.1) until the user picks another one. */
internal object DefaultLanguage {
    const val TAMIL_TAG = "ta"

    fun applyIfUnset() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(TAMIL_TAG))
        }
    }
}
