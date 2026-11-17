package com.example.inventory.data.db

import com.example.inventory.domain.filter.FieldFilter
import com.example.inventory.domain.filter.ItemFilter
import com.example.inventory.domain.filter.SubcategoryChoice

/** Összeállított lekérdezés: SQL és a paraméterek (`?`) sorrendben. Android-független, tesztelhető. */
data class BuiltQuery(val sql: String, val args: List<Any>)

/**
 * A tétellista szűrt lekérdezése (TERV.md 5. pont). Az értékek mindig paraméterként mennek, soha nem
 * szövegösszefűzéssel. A szöveges keresés (keresőszó, „tartalmazza” szűrők) nem itt, hanem Kotlinban történik
 * (lásd ItemTextMatcher), mert az SQLite LIKE az ékezetes betűknél nem kis-/nagybetű-független.
 */
object ItemFilterQueryBuilder {

    private const val COVER =
        "(SELECT p.fileName FROM item_photos p WHERE p.itemId = items.id ORDER BY p.sortOrder, p.id LIMIT 1) AS coverFileName"

    const val ORDER = "ORDER BY CASE items.status WHEN 'RESERVED' THEN 0 WHEN 'IN_STOCK' THEN 1 ELSE 2 END, items.createdAt DESC, items.id DESC"

    fun build(categoryId: Long, filter: ItemFilter): BuiltQuery {
        val where = mutableListOf("items.categoryId = ?")
        val args = mutableListOf<Any>(categoryId)

        when (val sub = filter.subcategory) {
            SubcategoryChoice.All -> Unit
            SubcategoryChoice.None -> where += "items.subcategoryId IS NULL"
            is SubcategoryChoice.Id -> { where += "items.subcategoryId = ?"; args += sub.id }
        }
        if (filter.statuses.isNotEmpty()) {
            val statuses = filter.statuses.map { it.name }.sorted()
            where += "items.status IN (${statuses.joinToString { "?" }})"
            args.addAll(statuses)
        }
        filter.valueMin?.let { where += "items.value >= ?"; args += it }
        filter.valueMax?.let { where += "items.value <= ?"; args += it }
        filter.quantityMin?.let { where += "items.quantity >= ?"; args += it }
        filter.quantityMax?.let { where += "items.quantity <= ?"; args += it }

        filter.fields.toSortedMap().forEach { (fieldId, f) ->
            if (!f.isActive || f is FieldFilter.Contains) return@forEach
            val conditions = mutableListOf<String>()
            val fieldArgs = mutableListOf<Any>()
            when (f) {
                is FieldFilter.NumberRange -> {
                    f.min?.let { conditions += "v.numberValue >= ?"; fieldArgs += it }
                    f.max?.let { conditions += "v.numberValue <= ?"; fieldArgs += it }
                }
                is FieldFilter.Options -> {
                    val values = f.values.sorted()
                    conditions += "v.textValue IN (${values.joinToString { "?" }})"
                    fieldArgs.addAll(values)
                }
                is FieldFilter.Bool -> { conditions += "v.boolValue = ?"; fieldArgs += if (f.value) 1 else 0 }
                is FieldFilter.DateRange -> {
                    f.from?.let { conditions += "v.dateValue >= ?"; fieldArgs += it }
                    f.to?.let { conditions += "v.dateValue <= ?"; fieldArgs += it }
                }
                is FieldFilter.Contains -> Unit
            }
            where += "EXISTS (SELECT 1 FROM item_field_values v WHERE v.itemId = items.id AND v.fieldId = ? AND " +
                conditions.joinToString(" AND ") + ")"
            args += fieldId
            args.addAll(fieldArgs)
        }

        val sql = "SELECT items.*, s.name AS subcategoryName, $COVER FROM items " +
            "LEFT JOIN subcategories s ON s.id = items.subcategoryId " +
            "WHERE ${where.joinToString(" AND ")} $ORDER"
        return BuiltQuery(sql, args)
    }
}
