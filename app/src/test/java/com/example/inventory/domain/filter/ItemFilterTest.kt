package com.example.inventory.domain.filter

import com.example.inventory.data.db.ItemFilterQueryBuilder
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.util.TextSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemFilterTest {

    @Test fun `ékezet- és kisbetű-független keresés`() {
        assertTrue(TextSearch.contains("Ékszer", "ekszer"))
        assertTrue(TextSearch.contains("Fehérarany nyaklánc", "ARANY"))
        assertTrue(TextSearch.contains("Árvíztűrő tükörfúrógép", "tukorfuro"))
        assertFalse(TextSearch.contains("Laptop", "telefon"))
        assertTrue(TextSearch.contains("bármi", "  "))
    }

    @Test fun `szöveges egyezés - név, mezők, tartalmazza-szűrők`() {
        val texts = mapOf(1L to "Intel Core i5", 2L to "Billentyűzet hibátlan")
        assertTrue(ItemTextMatcher.matches("ThinkPad", texts, "core", emptyMap()))
        assertTrue(ItemTextMatcher.matches("ThinkPad", texts, "think", emptyMap()))
        assertFalse(ItemTextMatcher.matches("ThinkPad", texts, "ryzen", emptyMap()))
        assertTrue(ItemTextMatcher.matches("ThinkPad", texts, "", mapOf(2L to "billentyuzet")))
        assertFalse(ItemTextMatcher.matches("ThinkPad", texts, "", mapOf(3L to "x")))   // üres mező nem felel meg
    }

    @Test fun `aktív szűrők száma`() {
        val f = ItemFilter(
            query = "x",
            statuses = setOf(ItemStatus.IN_STOCK),
            valueMin = 10,
            fields = mapOf(1L to FieldFilter.NumberRange(min = 16.0), 2L to FieldFilter.Options(emptySet()), 3L to FieldFilter.Contains(" ")),
        )
        assertEquals(3, f.activeCount)
        assertEquals(0, f.clearPanel().activeCount)
        assertEquals("x", f.clearPanel().query)
    }

    @Test fun `SQL - paraméterek a feltételek sorrendjében, szöveges szűrő nélkül`() {
        val q = ItemFilterQueryBuilder.build(
            categoryId = 5,
            filter = ItemFilter(
                subcategory = SubcategoryChoice.Id(9),
                statuses = setOf(ItemStatus.RESERVED, ItemStatus.IN_STOCK),
                valueMin = 1000,
                fields = mapOf(
                    7L to FieldFilter.Options(setOf("Platina", "Arany")),
                    3L to FieldFilter.NumberRange(min = 16.0, max = 32.0),
                    4L to FieldFilter.Contains("karc"),
                    8L to FieldFilter.Bool(false),
                ),
            ),
        )
        assertEquals(listOf<Any>(5L, 9L, "IN_STOCK", "RESERVED", 1000L, 3L, 16.0, 32.0, 7L, "Arany", "Platina", 8L, 0), q.args)
        assertTrue(q.sql.contains("items.subcategoryId = ?"))
        assertTrue(q.sql.contains("items.status IN (?, ?)"))
        assertEquals(3, Regex("EXISTS").findAll(q.sql).count())
        assertFalse(q.sql.contains("karc"))
        assertTrue(q.sql.endsWith(ItemFilterQueryBuilder.ORDER))
    }

    @Test fun `SQL - Egyéb alkategória`() {
        val q = ItemFilterQueryBuilder.build(1, ItemFilter(subcategory = SubcategoryChoice.None))
        assertTrue(q.sql.contains("items.subcategoryId IS NULL"))
        assertEquals(listOf<Any>(1L), q.args)
    }
}
