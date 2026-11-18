package com.example.inventory.data.backup

import java.math.BigDecimal

/** A CSV feliratai a felület nyelvén (a képernyő állítja össze a szövegforrásokból). */
data class CsvLabels(
    val name: String,
    val subcategory: String,
    val other: String,
    val status: String,
    val value: String,
    val unitValue: String,
    val quantity: String,
    val createdAt: String,
    val inStock: String,
    val reserved: String,
    val sold: String,
    val yes: String,
    val no: String,
    /** Magyar Excelhez tizedesvessző, angolhoz tizedespont. */
    val decimalComma: Boolean,
)

/** Egy kategória exportja: oszlopnevek és sorok, már szövegként. */
data class CsvTable(val fileName: String, val content: String)

/**
 * Táblázatkezelőbe szánt CSV (csak export): `;` elválasztó, UTF-8 BOM (különben az Excel az ékezeteket rosszul mutatja),
 * a szöveg idézőjelben, ha elválasztót, idézőjelet vagy sortörést tartalmaz. Kategóriánként egy fájl, mert mindegyiknek
 * más mezői vannak (TERV.md 3.11).
 */
object CsvExport {
    private const val SEP = ";"
    private const val BOM = "\uFEFF"

    fun table(header: List<String>, rows: List<List<String>>): String = buildString {
        append(BOM)
        appendLine(header.joinToString(SEP) { escape(it) })
        rows.forEach { row -> appendLine(row.joinToString(SEP) { escape(it) }) }
    }

    /** Pontosvessző az elválasztó, így a tizedesvesszős számokat nem kell idézőjelezni (az Excel számként olvassa). */
    fun escape(text: String): String =
        if (text.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    /** Szám a CSV-be: tudományos jelölés és felesleges nullák nélkül, a nyelv tizedesjelével. */
    fun number(value: Double, decimalComma: Boolean): String {
        val plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        return if (decimalComma) plain.replace('.', ',') else plain
    }

    /** Fájlnév a kategória nevéből: tiltott karakterek nélkül, egyedien (azonos nevek sorszámot kapnak). */
    fun fileNames(names: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return names.map { raw ->
            val base = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifBlank { "category" }.take(60)
            var candidate = "$base.csv"
            var n = 2
            while (!used.add(candidate.lowercase())) candidate = "$base ($n).csv".also { n++ }
            candidate
        }
    }
}
