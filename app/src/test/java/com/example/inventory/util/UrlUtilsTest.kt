package com.example.inventory.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test fun `https elé kerül, ha nincs séma`() = assertEquals("https://jofogas.hu/x", UrlUtils.normalize("  jofogas.hu/x "))
    @Test fun `meglévő séma marad`() = assertEquals("http://example.com", UrlUtils.normalize("http://example.com"))
    @Test fun `üres null`() = assertNull(UrlUtils.normalize("   "))

    @Test fun `érvényes címek`() {
        listOf("jofogas.hu", "https://www.vatera.hu/termek?id=5", "http://localhost:8080/a").forEach {
            assertTrue(it, UrlUtils.isValid(it))
        }
    }

    @Test fun `érvénytelen címek`() {
        listOf("", "hello", "https://", "ftp://example.com", "jofogas hu", "mailto:a@b.hu").forEach {
            assertFalse(it, UrlUtils.isValid(it))
        }
    }

    @Test fun `rövid kijelzés`() = assertEquals("jofogas.hu/hirdetes/123", UrlUtils.display("https://www.jofogas.hu/hirdetes/123/"))
}
