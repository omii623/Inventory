package com.example.inventory.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Szám- és dátumkezelés a saját mezőkhöz. */
object NumberFormats {

    /**
     * Beírt szám → Double. Elfogadja a tizedesvesszőt és -pontot, valamint a szóközöket ezres tagolásként.
     * Üres vagy érvénytelen bemenet → null.
     */
    fun parseDecimal(input: String): Double? {
        val cleaned = input.trim().replace(Regex("[\\s\\u00A0\\u202F]"), "").replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        if (!Regex("^-?(\\d+\\.?\\d*|\\.\\d+)$").matches(cleaned)) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Beírt egész (Ft, darab) → Long. Csak számjegyek (és szóközök) megengedettek. */
    fun parseWhole(input: String): Long? {
        val cleaned = input.trim().replace(Regex("[\\s\\u00A0\\u202F.,]"), "")
        if (cleaned.isEmpty() || !cleaned.all { it.isDigit() } || cleaned.length > 15) return null
        return cleaned.toLongOrNull()
    }

    /** Szám a szerkesztőmezőbe: egészként tizedes nélkül, egyébként a nyelv tizedesjelével, tagolás nélkül. */
    fun toInput(value: Double, locale: Locale): String {
        if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) return value.toLong().toString()
        val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return value.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', separator)
    }

    /** Szám kijelzése (adatlap): ezres tagolással, legfeljebb 2 tizedessel. */
    fun display(value: Double, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)

    /** Dátum kijelzése a nyelv közepes formátumában (pl. „2026. nov. 17.” / „Nov 17, 2026”). */
    fun displayDate(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
