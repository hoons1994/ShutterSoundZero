package io.github.hoons1994.shuttersoundzero.testing

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider
import java.util.Locale

internal fun targetString(@StringRes id: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id)

internal fun localizedTestContext(locale: Locale): Context {
    val app = ApplicationProvider.getApplicationContext<Context>()
    return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) })
}
