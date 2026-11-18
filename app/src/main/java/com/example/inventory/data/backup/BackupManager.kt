package com.example.inventory.data.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.data.repository.FilterStore
import com.example.inventory.domain.model.FieldType
import com.example.inventory.domain.model.ItemStatus
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adatmentés (TERV.md 3.11), a cashApp megoldása kiegészítve a fotókkal:
 * - Mentés: egy zip, benne `backup.json` (minden tábla nyers SQL-lel + a beállítások) és `photos/<fájlnév>`.
 * - Visszaállítás: ellenőrzött beolvasás, összesítő, majd egyetlen tranzakcióban a jelenlegi adatok cseréje
 *   (`PRAGMA foreign_key_check`-kel). A fotók előbb ideiglenes mappába kerülnek, és csak a sikeres
 *   adatbázis-csere után cserélik le a régieket.
 * - CSV: kategóriánként egy táblázat, egy zipben (csak export).
 *
 * A táblák nyers SQL-lel mennek, így a mentés minden oszlopot tartalmaz, és új oszlopnál sem kell ide nyúlni.
 */
class BackupManager(
    context: Context,
    private val database: InventoryDatabase,
    private val photos: PhotoStorage,
    private val settings: BackupSettings,
    private val filterStore: FilterStore,
) {
    private val cacheDir = context.applicationContext.cacheDir

    /** A visszaállítás előtti megerősítéshez. */
    data class Summary(val exportedAt: Long, val categories: Int, val items: Int, val photos: Int)

    /** Beolvasott, ellenőrzött mentés: az adatbázis-rész és a kicsomagolt fotók ideiglenes mappája. */
    class Pending(val backup: Backup, val photoDir: File, val photoNames: Set<String>) {
        val summary = Summary(backup.exportedAt, backup.count("categories"), backup.count("items"), photoNames.size)
        fun discard() { photoDir.deleteRecursively() }
    }

    // ---------------------------------------------------------------- mentés

    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        val backup = database.withTransaction {
            Backup(
                dbVersion = sql.version,
                exportedAt = System.currentTimeMillis(),
                settings = settings.snapshot(),
                tables = TABLES.associateWith { readTable(it) },
            )
        }
        val photoNames = backup.tables["item_photos"].orEmpty().mapNotNull { it["fileName"] as? String }.toSet()
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(BackupCodec.encode(backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photoNames.forEach { name ->
                val file = photos.file(name)
                if (!file.exists()) return@forEach
                zip.putNextEntry(ZipEntry(PHOTO_PREFIX + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // ---------------------------------------------------------------- visszaállítás

    /** Beolvas és ellenőriz egy mentést (még nem ír semmit az adatbázisba). */
    suspend fun read(input: InputStream): Pending = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var json: String? = null
            val names = mutableSetOf<String>()
            var sawEntry = false
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = try { zip.nextEntry } catch (e: ZipException) { throw BackupFormatException(BackupFormatException.Reason.NOT_ZIP) } ?: break
                    sawEntry = true
                    when {
                        entry.isDirectory -> Unit
                        entry.name == JSON_ENTRY -> json = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith(PHOTO_PREFIX) -> {
                            val name = entry.name.removePrefix(PHOTO_PREFIX)
                            // Csak egyszerű fájlnév fogadható el (nincs mappa, nincs „..”).
                            if (BackupCodec.isSafePhotoName(name)) {
                                File(dir, name).outputStream().use { zip.copyTo(it) }
                                names += name
                            }
                        }
                    }
                }
            }
            if (!sawEntry) throw BackupFormatException(BackupFormatException.Reason.NOT_ZIP)
            val text = json ?: throw BackupFormatException(BackupFormatException.Reason.NOT_INVENTORY)
            Pending(BackupCodec.decode(text, maxDbVersion = sql.version), dir, names)
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw when (e) {
                is BackupFormatException -> e
                is java.io.IOException -> BackupFormatException(BackupFormatException.Reason.UNREADABLE, e.message)
                else -> e
            }
        }
    }

    /**
     * A jelenlegi adatok cseréje a mentésre, egyetlen tranzakcióban: ha bármi hibás (pl. sérült hivatkozás),
     * semmi nem változik. A mentésben hivatkozott, de a zipből hiányzó fotók sorai kimaradnak.
     */
    suspend fun restore(pending: Pending) = withContext(Dispatchers.IO) {
        try {
            try {
                database.withTransaction {
                    TABLES.asReversed().forEach { sql.execSQL("DELETE FROM $it") }
                    TABLES.forEach { table ->
                        val rows = pending.backup.tables[table].orEmpty().let { rows ->
                            if (table == "item_photos") rows.filter { (it["fileName"] as? String) in pending.photoNames } else rows
                        }
                        insertRows(table, rows)
                    }
                    sql.query("PRAGMA foreign_key_check").use { c ->
                        if (c.moveToFirst()) throw BackupFormatException(BackupFormatException.Reason.BROKEN_REFERENCE, c.getString(0))
                    }
                }
            } catch (e: SQLiteConstraintException) {
                // A Room bekapcsolja az idegen kulcsokat, így a hibás hivatkozás már beszúráskor kiderül (a tranzakció visszagörgetve).
                val reason = if (e.message?.contains("FOREIGN KEY") == true) BackupFormatException.Reason.BROKEN_REFERENCE
                else BackupFormatException.Reason.CORRUPT
                throw BackupFormatException(reason, e.message)
            } catch (e: SQLiteException) {
                throw BackupFormatException(BackupFormatException.Reason.CORRUPT, e.message)
            }
            // Az adatbázis már az új: a fotómappa cseréje.
            photos.directory.listFiles()?.forEach { it.delete() }
            pending.photoNames.forEach { name ->
                val src = File(pending.photoDir, name)
                val dst = photos.file(name)
                if (!src.renameTo(dst)) src.copyTo(dst, overwrite = true)
            }
            settings.restore(pending.backup.settings)
            filterStore.clear()
        } finally {
            pending.discard()
        }
    }

    // ---------------------------------------------------------------- CSV

    /** Kategóriánként egy CSV egy zipben (csak export). Az archivált mezők nem kerülnek bele. */
    suspend fun exportCsv(out: OutputStream, labels: CsvLabels) = withContext(Dispatchers.IO) {
        val tables = database.withTransaction { buildCsvTables(labels) }
        ZipOutputStream(out.buffered()).use { zip ->
            tables.forEach { t ->
                zip.putNextEntry(ZipEntry(t.fileName))
                zip.write(t.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun buildCsvTables(labels: CsvLabels): List<CsvTable> {
        data class Cat(val id: Long, val name: String, val quantity: Boolean)
        data class Field(val id: Long, val name: String, val type: FieldType, val unit: String?)

        val categories = sql.query("SELECT id, name, trackingMode FROM categories ORDER BY archived, sortOrder, id").use { c ->
            buildList { while (c.moveToNext()) add(Cat(c.getLong(0), c.getString(1), c.getString(2) == "QUANTITY")) }
        }
        val fileNames = CsvExport.fileNames(categories.map { it.name })
        return categories.mapIndexed { index, cat ->
            val fields = sql.query("SELECT id, name, type, unit FROM field_definitions WHERE categoryId = ? AND archived = 0 ORDER BY sortOrder, id", arrayOf(cat.id)).use { c ->
                buildList { while (c.moveToNext()) add(Field(c.getLong(0), c.getString(1), FieldType.valueOf(c.getString(2)), c.stringOrNull(3))) }
            }
            val values = mutableMapOf<Pair<Long, Long>, String>()
            sql.query(
                "SELECT v.itemId, v.fieldId, v.textValue, v.numberValue, v.boolValue, v.dateValue FROM item_field_values v " +
                    "JOIN items i ON i.id = v.itemId WHERE i.categoryId = ?",
                arrayOf(cat.id),
            ).use { c ->
                while (c.moveToNext()) {
                    val text = when {
                        !c.isNull(2) -> c.getString(2)
                        !c.isNull(3) -> CsvExport.number(c.getDouble(3), labels.decimalComma)
                        !c.isNull(4) -> if (c.getLong(4) != 0L) labels.yes else labels.no
                        !c.isNull(5) -> java.time.LocalDate.ofEpochDay(c.getLong(5)).toString()
                        else -> ""
                    }
                    values[c.getLong(0) to c.getLong(1)] = text
                }
            }
            val header = buildList {
                add(labels.name); add(labels.subcategory); add(labels.status)
                add(if (cat.quantity) labels.unitValue else labels.value)
                if (cat.quantity) add(labels.quantity)
                fields.forEach { f -> add(f.name + (f.unit?.let { " ($it)" } ?: "")) }
                add(labels.createdAt)
            }
            val rows = sql.query(
                "SELECT i.id, i.name, s.name, i.status, i.value, i.quantity, i.createdAt FROM items i " +
                    "LEFT JOIN subcategories s ON s.id = i.subcategoryId WHERE i.categoryId = ? " +
                    "ORDER BY CASE i.status WHEN 'RESERVED' THEN 0 WHEN 'IN_STOCK' THEN 1 ELSE 2 END, i.createdAt DESC, i.id DESC",
                arrayOf(cat.id),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val itemId = c.getLong(0)
                        add(buildList {
                            add(c.getString(1))
                            add(c.stringOrNull(2) ?: labels.other)
                            add(when (c.getString(3)) { ItemStatus.RESERVED.name -> labels.reserved; ItemStatus.SOLD.name -> labels.sold; else -> labels.inStock })
                            add(c.getLong(4).toString())
                            if (cat.quantity) add(c.getInt(5).toString())
                            fields.forEach { f -> add(values[itemId to f.id].orEmpty()) }
                            add(Instant.ofEpochMilli(c.getLong(6)).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                        })
                    }
                }
            }
            CsvTable(fileNames[index], CsvExport.table(header, rows))
        }
    }

    // ---------------------------------------------------------------- segédek

    private val sql get() = database.openHelper.writableDatabase

    private fun readTable(table: String): Rows = sql.query("SELECT * FROM $table").use { c ->
        buildList {
            while (c.moveToNext()) add((0 until c.columnCount).associate { i -> c.getColumnName(i) to c.value(i) })
        }
    }

    /** Csak a táblában ténylegesen létező oszlopok kerülnek be (régebbi mentésnél a hiányzók alapértéket kapnak). */
    private fun insertRows(table: String, rows: Rows) {
        if (rows.isEmpty()) return
        val columns = sql.query("PRAGMA table_info($table)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
        rows.forEach { row ->
            val entries = row.filterKeys { it in columns }
            if (entries.isEmpty()) return@forEach
            val names = entries.keys.joinToString(",") { "`$it`" }
            val marks = entries.keys.joinToString(",") { "?" }
            sql.execSQL("INSERT INTO $table ($names) VALUES ($marks)", entries.values.toTypedArray())
        }
    }

    private fun Cursor.value(i: Int): Any? = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
        else -> getString(i)
    }

    private fun Cursor.stringOrNull(i: Int) = if (isNull(i)) null else getString(i)

    companion object {
        const val JSON_ENTRY = "backup.json"
        const val PHOTO_PREFIX = "photos/"

        /** Beszúrási sorrend: előbb az, amire hivatkoznak (törlés fordítva). */
        val TABLES = listOf("categories", "subcategories", "field_definitions", "items", "item_photos", "item_field_values")
    }
}
