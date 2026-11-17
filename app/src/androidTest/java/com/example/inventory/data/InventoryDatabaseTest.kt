package com.example.inventory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.data.db.entity.ItemPhotoEntity
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.FieldDraft
import com.example.inventory.data.repository.ItemRepository
import com.example.inventory.data.repository.SubcategoryDraft
import com.example.inventory.domain.filter.FieldFilter
import com.example.inventory.domain.filter.ItemFilter
import com.example.inventory.domain.filter.SubcategoryChoice
import com.example.inventory.domain.model.FieldType
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Az adatréteg szabályai (TERV.md 3.2a, 3.4, 3.7, 3.8) egy memóriában futó adatbázison. */
@RunWith(AndroidJUnit4::class)
class InventoryDatabaseTest {

    private lateinit var db: InventoryDatabase
    private lateinit var photos: PhotoStorage
    private lateinit var categories: CategoryRepository
    private lateinit var items: ItemRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        photos = PhotoStorage(context)
        categories = CategoryRepository(db, photos)
        items = ItemRepository(db, photos)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun category(name: String, mode: TrackingMode = TrackingMode.UNIQUE) =
        categories.create(CategoryEntity(name = name, icon = "box", color = 0xFF5C6BC0, trackingMode = mode))

    private suspend fun item(
        categoryId: Long,
        value: Long,
        quantity: Int = 1,
        status: ItemStatus = ItemStatus.IN_STOCK,
        createdAt: Long = System.currentTimeMillis(),
    ) = items.save(
        ItemEntity(categoryId = categoryId, name = "Item $value", value = value, quantity = quantity, status = status, createdAt = createdAt),
        values = emptyList(),
    )

    @Test
    fun totals_skipSoldItems_andArchivedCategories_andMultiplyOnlyInQuantityMode() = runBlocking {
        val laptops = category("Laptop", TrackingMode.UNIQUE)
        val jewelry = category("Ékszer", TrackingMode.QUANTITY)
        val archived = category("Régi", TrackingMode.UNIQUE)

        item(laptops, 100_000)
        item(laptops, 50_000, quantity = 3)               // egyedi módban a mennyiség nem számít → 50 000
        item(laptops, 70_000, status = ItemStatus.SOLD)   // eladva → nem számít
        item(laptops, 30_000, status = ItemStatus.RESERVED)
        item(jewelry, 10_000, quantity = 3)               // 3 × 10 000
        item(jewelry, 5_000, quantity = 2, status = ItemStatus.SOLD)
        item(archived, 999_000)
        categories.setArchived(archived, true)

        val totals = categories.observeTotals().first()
        assertEquals(100_000L + 50_000 + 30_000 + 30_000, totals.totalValue)
        assertEquals(3L + 3, totals.itemCount)            // 3 laptop + 3 db ékszer
        assertEquals(2L, totals.categoryCount)

        val summaries = categories.observeSummaries().first().associateBy { it.category.name }
        assertEquals(180_000L, summaries.getValue("Laptop").totalValue)
        assertEquals(3L, summaries.getValue("Laptop").itemCount)
        assertEquals(30_000L, summaries.getValue("Ékszer").totalValue)
        assertEquals(3L, summaries.getValue("Ékszer").itemCount)
        assertFalse("Régi" in summaries)

        val archivedList = categories.observeArchivedSummaries().first()
        assertEquals(listOf("Régi"), archivedList.map { it.category.name })
    }

    @Test
    fun itemList_isSortedReservedThenInStockThenSold_newestFirst() = runBlocking {
        val cat = category("Laptop")
        val sold = item(cat, 1, status = ItemStatus.SOLD, createdAt = 5_000)
        val oldStock = item(cat, 2, status = ItemStatus.IN_STOCK, createdAt = 1_000)
        val reserved = item(cat, 3, status = ItemStatus.RESERVED, createdAt = 2_000)
        val newStock = item(cat, 4, status = ItemStatus.IN_STOCK, createdAt = 3_000)

        val order = items.observeForCategory(cat).first().map { it.id }
        assertEquals(listOf(reserved, newStock, oldStock, sold), order)
    }

    @Test
    fun save_updatesAndClearsFieldValues() = runBlocking {
        val cat = category("Laptop")
        val ram = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "RAM", type = FieldType.NUMBER, unit = "GB"))
        val cpu = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "CPU", type = FieldType.TEXT))

        val id = items.save(
            ItemEntity(categoryId = cat, name = "ThinkPad"),
            values = listOf(ItemFieldValueEntity(0, ram, numberValue = 8.0), ItemFieldValueEntity(0, cpu, textValue = "i5")),
        )
        val saved = items.get(id)!!
        items.save(saved.copy(value = 1000), listOf(ItemFieldValueEntity(0, ram, numberValue = 16.0)), clearedFieldIds = listOf(cpu))

        val values = items.getValues(id)
        assertEquals(1, values.size)
        assertEquals(16.0, values.single().numberValue!!, 0.0)
        assertEquals(1000L, items.get(id)!!.value)
        assertEquals(saved.createdAt, items.get(id)!!.createdAt)
    }

    @Test
    fun archivedField_disappears_butItsValuesStay() = runBlocking {
        val cat = category("Laptop")
        val f = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Szín", type = FieldType.TEXT))
        val g = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Gyártó", type = FieldType.SELECT, options = listOf("A", "B")))
        val id = items.save(ItemEntity(categoryId = cat, name = "X"), listOf(ItemFieldValueEntity(0, f, textValue = "fekete")))

        categories.archiveField(f)

        assertEquals(listOf(g), categories.getFields(cat).map { it.id })
        assertEquals(listOf("A", "B"), categories.getFields(cat).single().options)
        assertEquals("fekete", items.getValues(id).single().textValue)
    }

    @Test
    fun reorderFields_changesOrder() = runBlocking {
        val cat = category("Laptop")
        val a = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "A", type = FieldType.TEXT))
        val b = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "B", type = FieldType.TEXT))
        val c = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "C", type = FieldType.TEXT))

        categories.reorderFields(listOf(c, a, b))

        assertEquals(listOf(c, a, b), categories.getFields(cat).map { it.id })
    }

    @Test
    fun deletingSubcategory_setsItemsSubcategoryToNull() = runBlocking {
        val cat = category("Laptop")
        val sub = categories.addSubcategory(cat, "Gamer")
        val id = items.save(ItemEntity(categoryId = cat, name = "ROG", subcategoryId = sub), emptyList())

        categories.deleteSubcategory(categories.observeSubcategories(cat).first().single())

        assertNull(items.get(id)!!.subcategoryId)
    }

    @Test
    fun deletingCategory_removesItemsValuesAndPhotoFiles() = runBlocking {
        val cat = category("Laptop")
        val field = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "RAM", type = FieldType.NUMBER))
        val id = items.save(ItemEntity(categoryId = cat, name = "X"), listOf(ItemFieldValueEntity(0, field, numberValue = 8.0)))
        val photo = File(photos.directory, "test-${System.nanoTime()}.jpg").apply { writeText("x") }
        db.itemDao().insertPhotos(listOf(ItemPhotoEntity(itemId = id, fileName = photo.name)))

        categories.delete(cat)

        assertNull(categories.get(cat))
        assertNull(items.get(id))
        assertTrue(items.getValues(id).isEmpty())
        assertFalse(photo.exists())
    }

    @Test
    fun saveCategory_addsRenamesRemovesAndOrdersSubcategories() = runBlocking {
        val id = categories.save(
            CategoryEntity(name = "Laptop", icon = "laptop", color = 0xFF5C6BC0),
            listOf(SubcategoryDraft(null, "Üzleti"), SubcategoryDraft(null, "Gamer"), SubcategoryDraft(null, "Régi")),
            fieldDrafts = emptyList(),
        )
        val saved = categories.getSubcategories(id)
        assertEquals(listOf("Üzleti", "Gamer", "Régi"), saved.map { it.name })
        val (business, gamer, old) = saved
        val itemInOld = items.save(ItemEntity(categoryId = id, name = "X", subcategoryId = old.id), emptyList())

        // Átnevezés + sorrendcsere + egy törlése + egy új
        categories.save(
            categories.get(id)!!.copy(name = "Laptopok", trackingMode = TrackingMode.QUANTITY),
            listOf(SubcategoryDraft(gamer.id, "Gamer"), SubcategoryDraft(business.id, "Business"), SubcategoryDraft(null, "Ultrabook")),
            fieldDrafts = emptyList(),
        )

        assertEquals("Laptopok", categories.get(id)!!.name)
        assertEquals(TrackingMode.QUANTITY, categories.get(id)!!.trackingMode)
        assertEquals(listOf("Gamer", "Business", "Ultrabook"), categories.getSubcategories(id).map { it.name })
        assertEquals(business.id, categories.getSubcategories(id)[1].id)
        assertNull(items.get(itemInOld)!!.subcategoryId)   // a törölt alkategória tétele „Egyéb” lett
        assertEquals(1, categories.countItems(id))
    }

    @Test
    fun saveCategory_addsUpdatesReordersAndArchivesFields_keepingValues() = runBlocking {
        val id = categories.save(
            CategoryEntity(name = "Laptop", icon = "laptop", color = 0xFF5C6BC0),
            subcategoryDrafts = emptyList(),
            fieldDrafts = listOf(
                FieldDraft(null, " RAM ", FieldType.NUMBER, required = true, unit = " GB "),
                FieldDraft(null, "Gyártó", FieldType.SELECT, options = listOf("Dell", " Lenovo ", "", "Dell")),
                FieldDraft(null, "Szín", FieldType.TEXT, unit = "ignored"),
            ),
        )
        val (ram, maker, color) = categories.getFields(id)
        assertEquals("RAM", ram.name)
        assertEquals("GB", ram.unit)
        assertTrue(ram.required)
        assertEquals(listOf("Dell", "Lenovo"), maker.options)   // üres és ismétlődő opció kiszűrve
        assertNull(color.unit)                                   // mértékegység csak Szám típusnál
        val item = items.save(ItemEntity(categoryId = id, name = "X"), listOf(ItemFieldValueEntity(0, color.id, textValue = "fekete")))

        // Átrendezés, a Szín törlése (archiválás), a RAM átnevezése, és egy próbálkozás a típus megváltoztatására
        categories.save(
            categories.get(id)!!,
            subcategoryDrafts = emptyList(),
            fieldDrafts = listOf(
                FieldDraft(maker.id, "Gyártó", FieldType.SELECT, options = listOf("Dell", "Lenovo", "HP")),
                FieldDraft(ram.id, "Memória", FieldType.TEXT, required = false, unit = "GB"),
                FieldDraft(null, "Megjegyzés", FieldType.LONG_TEXT),
            ),
        )
        val fields = categories.getFields(id)
        assertEquals(listOf("Gyártó", "Memória", "Megjegyzés"), fields.map { it.name })
        assertEquals(listOf(maker.id, ram.id), fields.take(2).map { it.id })
        assertEquals(FieldType.NUMBER, fields[1].type)            // a mentett mező típusa nem változik
        assertEquals("GB", fields[1].unit)
        assertEquals(listOf("Dell", "Lenovo", "HP"), fields[0].options)
        assertEquals(FieldType.LONG_TEXT, fields[2].type)
        assertEquals("fekete", items.getValues(item).single().textValue)   // az archivált mező értéke megmaradt
    }

    @Test
    fun savePhotos_keepsOrder_removesDroppedFiles_capsAtTen_andShowsCover() = runBlocking {
        val cat = category("Laptop")
        fun photo(tag: String) = File(photos.directory, "t-$tag-${System.nanoTime()}.jpg").apply { writeText(tag) }.name
        val (a, b, c) = listOf(photo("a"), photo("b"), photo("c"))

        val id = items.save(ItemEntity(categoryId = cat, name = "X"), emptyList(), photoFileNames = listOf(a, b, c))
        assertEquals(listOf(a, b, c), items.getPhotoFileNames(id))
        assertEquals(a, items.observeRows(cat).first().single().coverFileName)

        // b kimarad, c lesz a borítókép
        items.save(items.get(id)!!, emptyList(), photoFileNames = listOf(c, a))
        assertEquals(listOf(c, a), items.getPhotoFileNames(id))
        assertFalse(File(photos.directory, b).exists())
        assertTrue(File(photos.directory, a).exists())
        assertEquals(c, items.observeLatest().first()!!.coverFileName)

        // null: a fotók nem változnak
        items.save(items.get(id)!!.copy(name = "Y"), emptyList())
        assertEquals(listOf(c, a), items.getPhotoFileNames(id))

        // legfeljebb 10
        val many = (1..12).map { photo("m$it") }
        items.save(items.get(id)!!, emptyList(), photoFileNames = many)
        assertEquals(many.take(10), items.getPhotoFileNames(id))
        items.delete(id)
        assertTrue(many.take(10).none { File(photos.directory, it).exists() })
        many.drop(10).forEach { File(photos.directory, it).delete() }
    }

    @Test
    fun filters_combineStructuredSqlAndAccentInsensitiveText() = runBlocking {
        val cat = category("Laptop")
        val sub = categories.addSubcategory(cat, "Üzleti")
        val ram = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "RAM", type = FieldType.NUMBER))
        val maker = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Gyártó", type = FieldType.SELECT, options = listOf("Dell", "HP", "Lenovo")))
        val charger = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Töltő", type = FieldType.BOOLEAN))
        val bought = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Vétel", type = FieldType.DATE))
        val note = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Megjegyzés", type = FieldType.LONG_TEXT))

        suspend fun laptop(name: String, value: Long, status: ItemStatus, ramGb: Double?, make: String, hasCharger: Boolean, day: Long, text: String? = null, subId: Long? = null) =
            items.save(
                ItemEntity(categoryId = cat, name = name, value = value, status = status, subcategoryId = subId),
                buildList {
                    ramGb?.let { add(ItemFieldValueEntity(0, ram, numberValue = it)) }
                    add(ItemFieldValueEntity(0, maker, textValue = make))
                    add(ItemFieldValueEntity(0, charger, boolValue = hasCharger))
                    add(ItemFieldValueEntity(0, bought, dateValue = day))
                    text?.let { add(ItemFieldValueEntity(0, note, textValue = it)) }
                },
            )
        val a = laptop("ThinkPad T14", 185_000, ItemStatus.IN_STOCK, 16.0, "Lenovo", true, 100, "Fedlapon karc", sub)
        val b = laptop("XPS 13", 240_000, ItemStatus.RESERVED, 32.0, "Dell", false, 200)
        val c = laptop("EliteBook", 150_000, ItemStatus.SOLD, 8.0, "HP", false, 300, "Billentyűzet hibás")
        val d = laptop("Nincs RAM adat", 90_000, ItemStatus.IN_STOCK, null, "Dell", true, 400)

        suspend fun ids(filter: ItemFilter) = items.observeFiltered(cat, filter).first().map { it.item.id }.toSet()

        assertEquals(setOf(a, b), ids(ItemFilter(fields = mapOf(ram to FieldFilter.NumberRange(min = 16.0)))))       // d: üres → kiesik
        assertEquals(setOf(b, c, d), ids(ItemFilter(fields = mapOf(maker to FieldFilter.Options(setOf("Dell", "HP"))))))
        assertEquals(setOf(a, d), ids(ItemFilter(fields = mapOf(charger to FieldFilter.Bool(true)))))
        assertEquals(setOf(b, c), ids(ItemFilter(fields = mapOf(bought to FieldFilter.DateRange(150, 350)))))
        assertEquals(setOf(a, d), ids(ItemFilter(statuses = setOf(ItemStatus.IN_STOCK))))
        assertEquals(setOf(a, c), ids(ItemFilter(valueMin = 100_000, valueMax = 200_000)))
        assertEquals(setOf(a), ids(ItemFilter(subcategory = SubcategoryChoice.Id(sub))))
        assertEquals(setOf(b, c, d), ids(ItemFilter(subcategory = SubcategoryChoice.None)))
        // ÉS kapcsolat
        assertEquals(setOf(b), ids(ItemFilter(statuses = setOf(ItemStatus.RESERVED, ItemStatus.IN_STOCK), fields = mapOf(maker to FieldFilter.Options(setOf("Dell")), ram to FieldFilter.NumberRange(min = 1.0)))))
        // Szöveg: név, mezőérték, ékezet nélkül
        assertEquals(setOf(a), ids(ItemFilter(query = "thinkpad")))
        assertEquals(setOf(c), ids(ItemFilter(query = "billentyuzet")))
        assertEquals(setOf(b, d), ids(ItemFilter(query = "dell")))
        assertEquals(setOf(a), ids(ItemFilter(fields = mapOf(note to FieldFilter.Contains("KARC")))))
        assertEquals(2, items.countFiltered(cat, ItemFilter(query = "dell")))
        // Sorrend: Lefoglalva → Készleten → Eladva
        assertEquals(b, items.observeFiltered(cat, ItemFilter()).first().first().item.id)

        // Globális keresés: archivált kategória nélkül
        val other = category("Régi")
        items.save(ItemEntity(categoryId = other, name = "Régi Dell"), emptyList())
        categories.setArchived(other, true)
        assertEquals(setOf(b, d), items.observeSearch("DELL").first().map { it.item.id }.toSet())
    }

    @Test
    fun statusChange_movesItemInListAndOutOfInventoryValue() = runBlocking {
        val cat = category("Ékszer", TrackingMode.QUANTITY)
        val ring = item(cat, 10_000, quantity = 3, createdAt = 1_000)
        val chain = item(cat, 5_000, quantity = 2, createdAt = 2_000)
        assertEquals(40_000L, categories.observeTotals().first().totalValue)

        items.setStatus(ring, ItemStatus.RESERVED)
        assertEquals(listOf(ring, chain), items.observeRows(cat).first().map { it.item.id })   // a lefoglalt előre kerül
        assertEquals(40_000L, categories.observeTotals().first().totalValue)                   // a lefoglalt még számít

        items.setStatus(ring, ItemStatus.SOLD)
        assertEquals(listOf(chain, ring), items.observeRows(cat).first().map { it.item.id })   // az eladott a végére
        assertEquals(10_000L, categories.observeTotals().first().totalValue)
        assertEquals(2L, categories.observeTotals().first().itemCount)
    }

    @Test
    fun countItemsWithQuantityAboveOne_forModeSwitchWarning() = runBlocking {
        val cat = category("Ékszer", TrackingMode.QUANTITY)
        item(cat, 1, quantity = 1)
        item(cat, 2, quantity = 3)
        item(cat, 3, quantity = 5)

        assertEquals(2, categories.countItemsWithQuantityAboveOne(cat))
    }
}
