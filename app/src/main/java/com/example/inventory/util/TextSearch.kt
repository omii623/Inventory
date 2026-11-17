package com.example.inventory.util

import java.text.Normalizer
import java.util.Locale

/**
 * Szöveges keresés kis- és nagybetű, valamint ékezet nélkül: „ekszer” megtalálja az „Ékszer”-t, „ARANY” az „arany”-at.
 * (Az SQLite LIKE csak az ASCII betűknél kis-/nagybetű-független, ezért a szöveges keresés Kotlinban történik.)
 */
object TextSearch {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT).trim()

    /** Igaz, ha a [haystack] tartalmazza a [needle]-t (normalizálva). Üres keresőszó mindenre igaz. */
    fun contains(haystack: String, needle: String): Boolean {
        val n = normalize(needle)
        return n.isEmpty() || normalize(haystack).contains(n)
    }
}
