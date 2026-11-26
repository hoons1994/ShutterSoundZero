package io.github.hoons1994.shuttersoundzero.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.github.hoons1994.shuttersoundzero.R
import java.util.Locale

internal enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    System("", R.string.language_follow_system),
    Korean("ko", R.string.language_korean),
    Japanese("ja", R.string.language_japanese),
    English("en", R.string.language_english);

    companion object {
        private const val KEY = "language_tag"
        private fun legacyPrefs(context: Context) =
            context.getSharedPreferences("app_language", Context.MODE_PRIVATE)

        fun current(context: Context): AppLanguage {
            val tag = if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language
            } else {
                legacyPrefs(context).getString(KEY, null)
            }
            return entries.firstOrNull { it.tag == tag } ?: System
        }

        fun select(context: Context, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(language.tag)
            } else {
                legacyPrefs(context).edit {
                    if (language == System) remove(KEY) else putString(KEY, language.tag)
                }
            }
        }

        fun migrateAfterOsUpgrade(context: Context) {
            if (Build.VERSION.SDK_INT < 33) return
            val prefs = legacyPrefs(context)
            val tag = prefs.getString(KEY, null) ?: return
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty && entries.any { it != System && it.tag == tag }) {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
            }
            prefs.edit { remove(KEY) }
        }
    }
}

/** Android 13+ already applies the framework preference to the app's contexts. */
internal fun Context.withAppLanguage(): Context {
    if (Build.VERSION.SDK_INT >= 33) return this
    val language = AppLanguage.current(this)
    if (language == AppLanguage.System || resources.configuration.locales[0]?.language == language.tag) return this
    return createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(language.tag)) })
}

internal fun Activity.applyLegacyLanguage() {
    if (Build.VERSION.SDK_INT >= 33) return
    val language = AppLanguage.current(this)
    if (language != AppLanguage.System) {
        applyOverrideConfiguration(Configuration().apply { setLocale(Locale.forLanguageTag(language.tag)) })
    }
}
