package com.uniccomj.jivana.presentation.habitcreate

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitCatalogResourcesTest {
    @Test
    fun categoriesAndTemplatesHaveDistinctNonEmptyEnglishAndPolishTranslations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val english = context.forLocale(Locale.ENGLISH)
        val polish = context.forLocale(Locale.forLanguageTag("pl"))
        val ids = HabitCatalog.flatMap { category ->
            listOf(category.title) + category.templates.flatMap { template ->
                listOfNotNull(template.name, template.description)
            }
        }
        for (id in ids) {
            val en = english.getString(id)
            val pl = polish.getString(id)
            assertTrue(en.isNotBlank())
            assertTrue(pl.isNotBlank())
            assertNotEquals(en, pl)
        }
    }

    private fun Context.forLocale(locale: Locale): Context {
        val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
        return createConfigurationContext(configuration)
    }
}
