package com.cliproxy

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    private const val PREFERENCES_NAME = "app_settings"
    private const val LANGUAGE_KEY = "interface_language"

    fun get(context: Context): String {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, CHINESE)
            ?: CHINESE
    }

    fun set(context: Context, language: String) {
        require(language == CHINESE || language == ENGLISH)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language)
            .apply()
    }

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(get(context))
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
