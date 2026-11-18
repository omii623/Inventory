package com.example.inventory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.inventory.data.backup.BackupFormatException
import com.example.inventory.data.backup.BackupManager
import com.example.inventory.data.backup.BackupSettings
import com.example.inventory.data.backup.CsvLabels
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.FilterStore
import com.example.inventory.data.repository.ItemRepository
import com.example.inventory.domain.filter.ItemFilter
import com.example.inventory.domain.model.FieldType
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A teljes mentés–visszaállítás kör (TERV.md 3.11). Memóriában futó adatbázissal, saját ideiglenes fotómappával
 * és álbeállításokkal – így a teszt nem nyúl az app valódi adataihoz.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private class FakeSettings(var values: Map<String, String> = emptyMap()) : BackupSettings {
        override suspend fun snapshot() = values
        override suspend fun restore(values: Map<String, String>) { this.values = values }
    }

    private lateinit var db: InventoryDatabase
    private lateinit var photoDir: File
    private lateinit var photos: PhotoStorage
    private lateinit var settings: FakeSettings
    private lateinit var filters: FilterStore
    private lateinit var categories: CategoryRepository
    private lateinit var items: ItemRepository
    private lateinit var backups: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        photoDir = File(context.cacheDir, "backup-test-${System.nanoTime()}")
        photos = PhotoStorage(context, photoDir)
        settings = FakeSettings(mapOf("themeMode" to "DARK"))
        filters = FilterStore()
        categories = CategoryRepository(db, photos)
        items = ItemRepository(db, photos)
        backups = BackupManager(context, db, photos, settings, filters)
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun photo(text: String) = File(photoDir, "p-${System.nanoTime()}.jpg").apply { writeText(text) }.name

    /** Laptop (egyedi) 2 tétellel, fotókkal és mezőkkel + Ékszer (mennyiséges). */
    private suspend fun seed(): Pair<Long, Long> {
        val laptop = categories.create(CategoryEntity(name = "Laptop", icon = "laptop", color = 0xFF5C6BC0))
        val sub = categories.addSubcategory(laptop, "Üzleti")
        val ram = categories.addField(FieldDefinitionEntity(categoryId = laptop, name = "RAM", type = FieldType.NUMBER, unit = "GB"))
        val note = categories.addField(FieldDefinitionEntity(categoryId = laptop, name = "Megjegyzés", type = FieldType.LONG_TEXT))
        val (a, b) = photo("A") to photo("B")
        items.save(
            ItemEntity(categoryId = laptop, name = "ThinkPad; T14", value = 185_000, subcategoryId = sub),
            listOf(ItemFieldValueEntity(0, ram, numberValue = 16.5), ItemFieldValueEntity(0, note, textValue = "karc a \"fedlapon\"")),
            photoFileNames = listOf(a, b),
        )
        items.save(ItemEntity(categoryId = laptop, name = "XPS", value = 240_000, status = ItemStatus.SOLD), emptyList())
        val jewelry = categories.create(CategoryEntity(name = "Ékszer", icon = "jewelry", color = 0xFFEC407A, trackingMode = TrackingMode.QUANTITY))
        items.save(ItemEntity(categoryId = jewelry, name = "Gyűrű", value = 8_000, quantity = 5), emptyList())
        return laptop to jewelry
    }

    private suspend fun exportBytes(): ByteArray = ByteArrayOutputStream().also { backups.export(it) }.toByteArray()

    @Test
    fun backup_thenChange_thenRestore_bringsEverythingBack() = runBlocking {
        val (laptop, _) = seed()
        val totalsBefore = categories.observeTotals().first()
        val photosBefore = photoDir.listFiles()!!.associate { it.name to it.readText() }
        val zip = exportBytes()

        // Változtatások a mentés után: kategória törlése (fotókkal), új kategória, más beállítás, szűrő.
        categories.delete(laptop)
        categories.create(CategoryEntity(name = "Új", icon = "box", color = 0))
        settings.values = mapOf("themeMode" to "LIGHT")
        filters.set(laptop, ItemFilter(query = "x"))
        assertTrue(photoDir.listFiles()!!.isEmpty())

        val pending = backups.read(ByteArrayInputStream(zip))
        assertEquals(BackupManager.Summary(pending.summary.exportedAt, categories = 2, items = 3, photos = 2), pending.summary)
        backups.restore(pending)

        assertEquals(listOf("Laptop", "Ékszer"), categories.observeSummaries().first().map { it.category.name })
        assertEquals(totalsBefore, categories.observeTotals().first())
        val thinkpad = items.observeRows(laptop).first().first { it.item.name == "ThinkPad; T14" }
        assertEquals("Üzleti", thinkpad.subcategoryName)
        assertEquals(setOf(16.5, null), items.getValues(thinkpad.item.id).map { it.numberValue }.toSet())
        assertEquals(photosBefore, photoDir.listFiles()!!.associate { it.name to it.readText() })
        assertEquals(photosBefore.keys.toList(), items.getPhotoFileNames(thinkpad.item.id).sorted().let { photosBefore.keys.toList().sorted() })
        assertEquals(mapOf("themeMode" to "DARK"), settings.values)
        assertEquals(ItemFilter(), filters.get(laptop))
        assertFalse(pending.photoDir.exists())   // az ideiglenes mappa eltakarítva
    }

    @Test
    fun invalidFiles_areRejected_andDataStaysUnchanged() = runBlocking {
        seed()
        val before = categories.observeTotals().first()

        fun reasonOf(bytes: ByteArray): BackupFormatException.Reason = try {
            runBlocking { backups.read(ByteArrayInputStream(bytes)) }
            fail("nem dobott hibát"); throw IllegalStateException()
        } catch (e: BackupFormatException) { e.reason }

        assertEquals(BackupFormatException.Reason.NOT_ZIP, reasonOf("nem zip".toByteArray()))
        assertEquals(BackupFormatException.Reason.NOT_INVENTORY, reasonOf(zipOf("valami.txt" to "x")))
        assertEquals(BackupFormatException.Reason.NOT_INVENTORY, reasonOf(zipOf("backup.json" to """{"app":"cashApp","format":1,"dbVersion":1,"tables":{}}""")))

        // Sérült hivatkozás: a tétel nem létező kategóriára mutat → a visszaállítás semmit nem változtat.
        val broken = """{"app":"inventory","format":1,"dbVersion":1,"exportedAt":1,"settings":{},
            "tables":{"categories":[],"items":[{"id":1,"categoryId":999,"name":"X","value":0,"quantity":1,"status":"IN_STOCK","createdAt":1,"updatedAt":1}]}}"""
        // Mappát tartalmazó fotónév kihagyva (a „..”-t tartalmazó zipet már maga az Android 14+ eldobja).
        val pending = backups.read(ByteArrayInputStream(zipOf("backup.json" to broken, "photos/a/evil.jpg" to "x")))
        assertEquals(0, pending.summary.photos)
        try {
            backups.restore(pending); fail("nem dobott hibát")
        } catch (e: BackupFormatException) {
            assertEquals(BackupFormatException.Reason.BROKEN_REFERENCE, e.reason)
        }
        assertEquals(before, categories.observeTotals().first())
        assertEquals(2, photoDir.listFiles()!!.size)
    }

    @Test
    fun csvExport_onePerCategory_withCustomFieldColumns() = runBlocking {
        seed()
        val labels = CsvLabels("Név", "Alkategória", "Egyéb", "Státusz", "Érték (Ft)", "Egységár (Ft)", "Mennyiség", "Felvéve",
            "Készleten", "Lefoglalva", "Eladva", "Igen", "Nem", decimalComma = true)
        val out = ByteArrayOutputStream().also { backups.exportCsv(it, labels) }.toByteArray()
        val files = unzip(out)
        assertEquals(setOf("Laptop.csv", "Ékszer.csv"), files.keys)
        val laptop = files.getValue("Laptop.csv").lines()
        assertEquals("\uFEFFNév;Alkategória;Státusz;Érték (Ft);RAM (GB);Megjegyzés;Felvéve", laptop[0])
        assertTrue(laptop[1].startsWith("\"ThinkPad; T14\";Üzleti;Készleten;185000;16,5;\"karc a \"\"fedlapon\"\"\";"))
        assertTrue(laptop[2].startsWith("XPS;Egyéb;Eladva;240000;;;"))
        assertTrue(files.getValue("Ékszer.csv").lines()[1].startsWith("Gyűrű;Egyéb;Készleten;8000;5;"))
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
    }.toByteArray()

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; put(e.name, zip.readBytes().toString(Charsets.UTF_8)) }
        }
    }
}
