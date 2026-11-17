package com.example.inventory.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    private val nbsp = " "

    @Test fun `angol ezres tagolás vesszővel`() =
        assertEquals("1,920,000${nbsp}Ft", MoneyFormatter.format(1_920_000, Locale.ENGLISH))

    @Test fun `magyar ezres tagolás szóközzel`() {
        val text = MoneyFormatter.format(1_920_000, Locale.forLanguageTag("hu"))
        // A JDK szóköz-fajtája verziófüggő (sima, nem törhető vagy keskeny nem törhető) – mindegyik elfogadható.
        assertEquals("1 920 000 Ft", text.replace(Regex("[\\u00A0\\u202F]"), " "))
    }

    @Test fun `nulla`() = assertEquals("0${nbsp}Ft", MoneyFormatter.format(0, Locale.ENGLISH))
}
