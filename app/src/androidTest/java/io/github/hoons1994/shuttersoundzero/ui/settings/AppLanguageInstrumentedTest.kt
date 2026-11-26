package io.github.hoons1994.shuttersoundzero.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.data.AppLanguage
import io.github.hoons1994.shuttersoundzero.data.withAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageInstrumentedTest {
    @Test fun selectedLanguageAppliesToBackgroundTextAndCanFollowSystemAgain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initial = AppLanguage.current(context)
        val framework = if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java) else null
        val originalLocales = if (Build.VERSION.SDK_INT >= 33) framework?.applicationLocales else null
        try {
            for ((language, label) in listOf(
                AppLanguage.Japanese to "言語",
                AppLanguage.English to "Language",
                AppLanguage.Korean to "언어"
            )) {
                AppLanguage.select(context, language)
                assertEquals(language, AppLanguage.current(context))
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (context.withAppLanguage().getString(R.string.language_title) != label &&
                    SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(50)
                }
                assertEquals(label, context.withAppLanguage().getString(R.string.language_title))
            }
            AppLanguage.select(context, AppLanguage.System)
            assertEquals(AppLanguage.System, AppLanguage.current(context))
            if (Build.VERSION.SDK_INT >= 33) check(requireNotNull(framework).applicationLocales.isEmpty)
        } finally {
            if (Build.VERSION.SDK_INT >= 33) {
                requireNotNull(framework).applicationLocales = requireNotNull(originalLocales)
            } else {
                AppLanguage.select(context, initial)
            }
        }
    }
}
