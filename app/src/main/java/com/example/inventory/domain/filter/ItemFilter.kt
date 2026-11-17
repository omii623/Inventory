package com.example.inventory.domain.filter

import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.util.TextSearch

/** Egy saját mezőre beállított szűrő (TERV.md 3.7a). */
sealed interface FieldFilter {
    /** Szám mező: tól–ig (bármelyik vége hiányozhat). */
    data class NumberRange(val min: Double? = null, val max: Double? = null) : FieldFilter
    /** Lista mező: a kiválasztott opciók bármelyike (VAGY). */
    data class Options(val values: Set<String>) : FieldFilter
    /** Igen/Nem mező. */
    data class Bool(val value: Boolean) : FieldFilter
    /** Dátum mező: epoch day tól–ig. */
    data class DateRange(val from: Long? = null, val to: Long? = null) : FieldFilter
    /** Szöveg, Hosszú szöveg, Link: tartalmazza (kis-/nagybetű és ékezet nélkül). */
    data class Contains(val text: String) : FieldFilter

    /** Ténylegesen szűr-e (az üres szűrő nem számít). */
    val isActive: Boolean
        get() = when (this) {
            is NumberRange -> min != null || max != null
            is Options -> values.isNotEmpty()
            is Bool -> true
            is DateRange -> from != null || to != null
            is Contains -> text.isNotBlank()
        }
}

/** Alkategória-címke a lista tetején: mind, egy adott alkategória, vagy az alkategória nélküliek („Egyéb”). */
sealed interface SubcategoryChoice {
    data object All : SubcategoryChoice
    data object None : SubcategoryChoice
    data class Id(val id: Long) : SubcategoryChoice
}

/**
 * A tétellista teljes szűrőállapota (TERV.md 3.7, 3.7a). A szűrők ÉS kapcsolatban vannak,
 * egy mezőn belül a több kiválasztott opció VAGY kapcsolatban. Az üres mezőjű tétel nem felel meg
 * az adott mezőre beállított szűrőnek.
 */
data class ItemFilter(
    val query: String = "",
    val subcategory: SubcategoryChoice = SubcategoryChoice.All,
    val statuses: Set<ItemStatus> = emptySet(),
    val valueMin: Long? = null,
    val valueMax: Long? = null,
    val quantityMin: Long? = null,
    val quantityMax: Long? = null,
    val fields: Map<Long, FieldFilter> = emptyMap(),
) {
    /** A szűrőpanelen beállított, aktív szűrők száma (a keresőszó és az alkategória-címke nem számít bele). */
    val activeCount: Int
        get() = (if (statuses.isNotEmpty()) 1 else 0) +
            (if (valueMin != null || valueMax != null) 1 else 0) +
            (if (quantityMin != null || quantityMax != null) 1 else 0) +
            fields.values.count { it.isActive }

    /** A szűrőpanel kiürítése (a keresőszó és az alkategória-címke marad). */
    fun clearPanel(): ItemFilter = copy(statuses = emptySet(), valueMin = null, valueMax = null, quantityMin = null, quantityMax = null, fields = emptyMap())

    /** A csak szövegesen (Kotlinban) ellenőrizhető feltételek. */
    val containsFilters: Map<Long, String>
        get() = fields.mapNotNull { (id, f) -> (f as? FieldFilter.Contains)?.text?.takeIf { it.isNotBlank() }?.let { id to it } }.toMap()
}

/** Egy tétel szöveges értékei a kereséshez: mezőazonosító → szöveg. */
typealias ItemTexts = Map<Long, String>

object ItemTextMatcher {
    /**
     * Igaz, ha a tétel megfelel a keresőszónak (név vagy bármely szöveges mezőérték)
     * és minden „tartalmazza” mezőszűrőnek.
     */
    fun matches(name: String, texts: ItemTexts, query: String, contains: Map<Long, String>): Boolean {
        if (query.isNotBlank() && !TextSearch.contains(name, query) && texts.values.none { TextSearch.contains(it, query) }) return false
        return contains.all { (fieldId, needle) -> texts[fieldId]?.let { TextSearch.contains(it, needle) } == true }
    }
}
