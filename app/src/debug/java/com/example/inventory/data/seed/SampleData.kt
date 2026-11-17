package com.example.inventory.data.seed

import android.content.Context
import androidx.core.content.edit
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.ItemRepository
import com.example.inventory.domain.model.FieldType
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode
import java.time.LocalDate

/**
 * Mintaadatok – CSAK a debug buildben (a release változat a src/release-ben egy üres függvény).
 * Az első indításkor egyszer fut le: ha később minden kategóriát törölsz, nem tölti vissza.
 * Újratöltéshez: az app adatainak törlése (Beállítások → Alkalmazások → Inventory → Tárhely → Adatok törlése).
 */
object SampleData {

    private const val PREFS = "debug_sample_data"
    private const val KEY_SEEDED = "seeded"

    suspend fun seedOnce(context: Context, categories: CategoryRepository, items: ItemRepository) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (categories.count() == 0) {
            seedLaptops(categories, items)
            seedJewelry(categories, items)
        }
        prefs.edit { putBoolean(KEY_SEEDED, true) }
    }

    // ------------------------------------------------------------ Laptop (egyedi darab)

    private suspend fun seedLaptops(categories: CategoryRepository, items: ItemRepository) {
        val cat = categories.create(
            CategoryEntity(name = "Laptop", icon = "laptop", color = 0xFF5C6BC0, trackingMode = TrackingMode.UNIQUE)
        )
        val sub = listOf("Üzleti", "Gamer", "Ultrabook", "MacBook").associateWith { categories.addSubcategory(cat, it) }

        val maker = categories.addField(
            FieldDefinitionEntity(
                categoryId = cat, name = "Gyártó", type = FieldType.SELECT, required = true,
                options = listOf("Lenovo", "Dell", "HP", "Apple", "ASUS", "Acer"),
            )
        )
        val cpu = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Processzor", type = FieldType.TEXT))
        val ram = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "RAM", type = FieldType.NUMBER, unit = "GB"))
        val storage = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Tárhely", type = FieldType.NUMBER, unit = "GB"))
        val battery = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Akku állapota", type = FieldType.NUMBER, unit = "%"))
        val charger = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Van töltő?", type = FieldType.BOOLEAN))
        val bought = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Beszerzés dátuma", type = FieldType.DATE))
        val link = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Hirdetés link", type = FieldType.LINK))
        val note = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Megjegyzés", type = FieldType.LONG_TEXT))

        data class Laptop(
            val name: String, val subcategory: String, val value: Long, val status: ItemStatus,
            val maker: String, val cpu: String, val ram: Int, val storage: Int, val battery: Int,
            val charger: Boolean, val daysAgo: Long, val link: String? = null, val note: String? = null,
        )

        val laptops = listOf(
            Laptop("Lenovo ThinkPad T14", "Üzleti", 185_000, ItemStatus.IN_STOCK, "Lenovo", "Intel Core i5-1135G7", 16, 512, 87, true, 40,
                link = "https://www.jofogas.hu/", note = "Billentyűzet hibátlan, a fedlapon apró karc."),
            Laptop("Dell XPS 13 9310", "Ultrabook", 240_000, ItemStatus.IN_STOCK, "Dell", "Intel Core i7-1165G7", 16, 1024, 91, true, 30),
            Laptop("MacBook Air M1", "MacBook", 210_000, ItemStatus.RESERVED, "Apple", "Apple M1", 8, 256, 89, true, 25),
            Laptop("ASUS ROG Strix G15", "Gamer", 320_000, ItemStatus.IN_STOCK, "ASUS", "AMD Ryzen 7 4800H", 16, 512, 78, true, 20),
            Laptop("HP EliteBook 840 G7", "Üzleti", 150_000, ItemStatus.SOLD, "HP", "Intel Core i5-10210U", 8, 256, 82, false, 60,
                note = "Eladva, a vevő személyesen vitte el."),
            Laptop("Acer Swift 3", "Ultrabook", 135_000, ItemStatus.IN_STOCK, "Acer", "AMD Ryzen 5 4500U", 8, 512, 85, false, 10),
        )

        val now = System.currentTimeMillis()
        laptops.forEachIndexed { index, l ->
            val createdAt = now - l.daysAgo * DAY_MS
            items.save(
                ItemEntity(
                    categoryId = cat, name = l.name, value = l.value, subcategoryId = sub[l.subcategory],
                    status = l.status, createdAt = createdAt,
                ),
                values = buildList {
                    add(ItemFieldValueEntity(0, maker, textValue = l.maker))
                    add(ItemFieldValueEntity(0, cpu, textValue = l.cpu))
                    add(ItemFieldValueEntity(0, ram, numberValue = l.ram.toDouble()))
                    add(ItemFieldValueEntity(0, storage, numberValue = l.storage.toDouble()))
                    add(ItemFieldValueEntity(0, battery, numberValue = l.battery.toDouble()))
                    add(ItemFieldValueEntity(0, charger, boolValue = l.charger))
                    add(ItemFieldValueEntity(0, bought, dateValue = LocalDate.now().minusDays(l.daysAgo + index).toEpochDay()))
                    l.link?.let { add(ItemFieldValueEntity(0, link, textValue = it)) }
                    l.note?.let { add(ItemFieldValueEntity(0, note, textValue = it)) }
                },
            )
        }
    }

    // ------------------------------------------------------------ Ékszer (mennyiséggel)

    private suspend fun seedJewelry(categories: CategoryRepository, items: ItemRepository) {
        val cat = categories.create(
            CategoryEntity(name = "Ékszer", icon = "jewelry", color = 0xFFEC407A, trackingMode = TrackingMode.QUANTITY)
        )
        val sub = listOf("Gyűrű", "Nyaklánc", "Karkötő", "Fülbevaló").associateWith { categories.addSubcategory(cat, it) }

        val material = categories.addField(
            FieldDefinitionEntity(
                categoryId = cat, name = "Anyag", type = FieldType.SELECT, required = true,
                options = listOf("Arany", "Fehérarany", "Ezüst", "Platina"),
            )
        )
        val karat = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Finomság", type = FieldType.NUMBER, unit = "K"))
        val weight = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Súly", type = FieldType.NUMBER, unit = "g"))
        val stone = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Kő", type = FieldType.TEXT))
        val size = categories.addField(FieldDefinitionEntity(categoryId = cat, name = "Méret", type = FieldType.TEXT))

        data class Jewel(
            val name: String, val subcategory: String, val value: Long, val quantity: Int, val status: ItemStatus,
            val material: String, val karat: Int?, val weight: Double, val stone: String?, val size: String?, val daysAgo: Long,
        )

        val jewels = listOf(
            Jewel("Ezüst karikagyűrű", "Gyűrű", 8_000, 5, ItemStatus.IN_STOCK, "Ezüst", null, 3.2, null, "54", 50),
            Jewel("Arany jegygyűrű", "Gyűrű", 145_000, 1, ItemStatus.RESERVED, "Arany", 14, 4.1, "Briliáns", "52", 35),
            Jewel("Fehérarany nyaklánc", "Nyaklánc", 98_000, 2, ItemStatus.IN_STOCK, "Fehérarany", 14, 6.8, null, "45 cm", 28),
            Jewel("Ezüst karkötő", "Karkötő", 12_500, 3, ItemStatus.IN_STOCK, "Ezüst", null, 9.5, null, "19 cm", 15),
            Jewel("Arany fülbevaló", "Fülbevaló", 56_000, 1, ItemStatus.SOLD, "Arany", 18, 2.4, "Gyöngy", null, 45),
        )

        val now = System.currentTimeMillis()
        jewels.forEach { j ->
            val createdAt = now - j.daysAgo * DAY_MS
            items.save(
                ItemEntity(
                    categoryId = cat, name = j.name, value = j.value, quantity = j.quantity,
                    subcategoryId = sub[j.subcategory], status = j.status, createdAt = createdAt,
                ),
                values = buildList {
                    add(ItemFieldValueEntity(0, material, textValue = j.material))
                    j.karat?.let { add(ItemFieldValueEntity(0, karat, numberValue = it.toDouble())) }
                    add(ItemFieldValueEntity(0, weight, numberValue = j.weight))
                    j.stone?.let { add(ItemFieldValueEntity(0, stone, textValue = it)) }
                    j.size?.let { add(ItemFieldValueEntity(0, size, textValue = it)) }
                },
            )
        }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
