package com.example.inventory.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun reason(json: String, maxDb: Int = 1): BackupFormatException.Reason? =
        try { BackupCodec.decode(json, maxDb); null } catch (e: BackupFormatException) { e.reason }

    @Test fun `oda-vissza`() {
        val backup = Backup(
            dbVersion = 1,
            exportedAt = 123L,
            settings = mapOf("themeMode" to "DARK"),
            tables = mapOf(
                "categories" to listOf(mapOf("id" to 1L, "name" to "Ékszer", "archived" to 0L, "color" to 4283215696L)),
                "item_field_values" to listOf(mapOf("itemId" to 1L, "fieldId" to 2L, "numberValue" to 4.1, "textValue" to null)),
            ),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(backup), maxDbVersion = 1)
        assertEquals(backup, decoded)
    }

    @Test fun `hibás fájlok`() {
        assertEquals(BackupFormatException.Reason.NOT_JSON, reason("nem json"))
        assertEquals(BackupFormatException.Reason.NOT_INVENTORY, reason("""{"app":"cashApp","format":1,"dbVersion":1,"tables":{}}"""))
        assertEquals(BackupFormatException.Reason.UNKNOWN_FORMAT, reason("""{"app":"inventory","format":9,"dbVersion":1,"tables":{}}"""))
        assertEquals(BackupFormatException.Reason.NEWER_VERSION, reason("""{"app":"inventory","format":1,"dbVersion":2,"tables":{}}"""))
        assertEquals(BackupFormatException.Reason.CORRUPT, reason("""{"app":"inventory","format":1,"dbVersion":1}"""))
        assertEquals(BackupFormatException.Reason.CORRUPT, reason("""{"app":"inventory","format":1,"dbVersion":1,"tables":{"items":[1]}}"""))
    }

    @Test fun `fotónév ellenőrzése`() {
        assertTrue(BackupCodec.isSafePhotoName("3f2c1a9e-1b2c-4d5e-8f90-123456789abc.jpg"))
        listOf("../x.jpg", "a/b.jpg", "..", "", "a b.jpg", "x\\y.jpg").forEach { assertFalse(it, BackupCodec.isSafePhotoName(it)) }
    }

    @Test fun `CSV - elválasztó, idézőjel, BOM, tizedesjel`() {
        val csv = CsvExport.table(listOf("Név", "Megjegyzés"), listOf(listOf("Gyűrű; arany", "azt mondta: \"szép\"\nújsor")))
        assertTrue(csv.startsWith("\uFEFFNév;Megjegyzés\n"))
        assertTrue(csv.contains("\"Gyűrű; arany\";\"azt mondta: \"\"szép\"\"\nújsor\""))
        assertEquals("4,1", CsvExport.number(4.1, decimalComma = true))
        assertEquals("4,1", CsvExport.escape("4,1"))   // tizedesvessző: nincs idézőjel
        assertEquals("1024", CsvExport.number(1024.0, decimalComma = false))
        assertEquals("0.0001", CsvExport.number(1e-4, decimalComma = false))
    }

    @Test fun `CSV fájlnevek`() {
        assertEquals(listOf("Laptop.csv", "Ékszer_Óra.csv", "laptop (2).csv", "category.csv"), CsvExport.fileNames(listOf("Laptop", "Ékszer/Óra", "laptop", "  ")))
    }
}
