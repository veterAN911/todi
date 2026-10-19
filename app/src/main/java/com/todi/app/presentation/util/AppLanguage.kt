package com.todi.app.presentation.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.todi.app.domain.model.LanguageMode
import java.util.Locale

object AppLanguage {
    private const val PREFS_NAME = "todi_language"
    private const val KEY_TAG = "language_tag"

    fun languageTag(mode: LanguageMode): String =
        when (mode) {
            LanguageMode.SYSTEM -> ""
            LanguageMode.RU -> "ru"
            LanguageMode.EN -> "en"
        }

    fun apply(
        context: Context,
        mode: LanguageMode,
    ) {
        val tag = languageTag(mode)
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
            localeManager.applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TAG, tag)
                .apply()
        }
    }

    fun wrapBaseContext(base: Context): Context? {
        if (Build.VERSION.SDK_INT >= 33) return null
        val stored =
            base
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TAG, null)
                ?: return null
        if (stored.isBlank()) return null
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(stored))
        return base.createConfigurationContext(config)
    }
}
