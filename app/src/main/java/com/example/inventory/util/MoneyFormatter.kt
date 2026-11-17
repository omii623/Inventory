package com.example.inventory.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.util.Locale

/**
 * Pénzösszeg formázása (TERV.md 3.8): mindig Ft, tizedesjegy nélkül, a számformátum a nyelvet követi.
 * Magyarul `1 920 000 Ft` (nem törhető szóközzel), angolul `1,920,000 Ft`.
 */
object MoneyFormatter {
    const val CURRENCY = "Ft"

    fun format(value: Long, locale: Locale): String =
        NumberFormat.getIntegerInstance(locale).format(value) + " " + CURRENCY
}

/** A felület aktuális nyelvéhez igazított formázó. */
@Composable
fun rememberMoneyFormat(): (Long) -> String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { { value: Long -> MoneyFormatter.format(value, locale) } }
}
