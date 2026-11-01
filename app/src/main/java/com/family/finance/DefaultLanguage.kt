package com.family.finance

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The app's interface language. The choice is stored per phone by Android, survives
 * restarts, and never changes the other parent's phone (PRD 7.1). Switching only changes
 * labels; financial values are untouched (PRD 7.6).
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    TAMIL("ta", R.string.language_tamil),
    ENGLISH("en", R.string.language_english);

    companion object {
        fun current(): AppLanguage {
            val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return if (tags.startsWith(ENGLISH.tag)) ENGLISH else TAMIL
        }

        /** Applies immediately; Android recreates the screen in the new language. */
        fun select(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}

/** Tamil is the default interface language (PRD 7.1) until the user picks another one. */
internal object DefaultLanguage {
    fun applyIfUnset() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) AppLanguage.select(AppLanguage.TAMIL)
    }
}
