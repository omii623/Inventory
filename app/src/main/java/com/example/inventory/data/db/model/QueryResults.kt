package com.example.inventory.data.db.model

import androidx.room.Embedded
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode

/*
 * Összesítő lekérdezések eredményei.
 * Szabály (TERV.md 3.8): az Eladva tételek és az archivált kategóriák nem számítanak bele;
 * mennyiséges kategóriában a tétel értéke Érték × Mennyiség, a tételszám a darabszámok összege.
 */

/** Egy kategória a kártyához: tételszám és érték (a nem eladott tételekből). */
data class CategorySummary(
    @Embedded val category: CategoryEntity,
    val itemCount: Long,
    val totalValue: Long,
)

/** A kezdőlap összesítő kártyája. */
data class InventoryTotals(
    val totalValue: Long,
    val itemCount: Long,
    val categoryCount: Long,
)

/** A kezdőlap „Legutóbb hozzáadva” sora. */
data class RecentItem(
    val id: Long,
    val name: String,
    val value: Long,
    val quantity: Int,
    val status: ItemStatus,
    val trackingMode: TrackingMode,
    val categoryName: String,
    val subcategoryName: String?,
    /** Az első fotó (borítókép) fájlneve, ha van. */
    val coverFileName: String?,
)

/** Egy tétel egy szöveges mezőértéke (keresés és „tartalmazza” szűrők). */
data class ItemText(val itemId: Long, val fieldId: Long, val text: String)

/** Egy találat a globális keresésben (TERV.md 3.7): a tétel a kategória nevével. */
data class SearchRow(
    @Embedded val item: com.example.inventory.data.db.entity.ItemEntity,
    val categoryName: String,
    val trackingMode: TrackingMode,
    val subcategoryName: String?,
    val coverFileName: String?,
)

/** Egy sor a tétellistában: a tétel és az alkategóriája neve (null → „Egyéb”). */
data class ItemListRow(
    @Embedded val item: com.example.inventory.data.db.entity.ItemEntity,
    val subcategoryName: String?,
    /** Az első fotó (borítókép) fájlneve, ha van. */
    val coverFileName: String?,
)
