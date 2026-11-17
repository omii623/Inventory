package com.example.inventory.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.inventory.domain.model.FieldType
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode

/*
 * Adatmodell (TERV.md 5. pont). A saját mezők értékei külön táblában vannak (EAV),
 * így SQL-lel szűrhetők, és egy mező archiválása nem rontja el a régi adatokat.
 *
 * categories ─┬─< subcategories
 *             ├─< field_definitions
 *             └─< items ─┬─< item_photos
 *                        └─< item_field_values >── field_definitions
 */

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Ikon kulcs (pl. `laptop`) vagy `letter:` + 1–2 karakter – mint a cashAppban. */
    val icon: String,
    /** ARGB szín, pl. 0xFF5C6BC0. */
    val color: Long,
    val trackingMode: TrackingMode = TrackingMode.UNIQUE,
    /** Archivált kategória: rejtve van, és nem számít bele a készletértékbe. */
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "subcategories",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "field_definitions",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class FieldDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val type: FieldType,
    val required: Boolean = false,
    /** Csak NUMBER típusnál: pl. "GB", "g", "%". */
    val unit: String? = null,
    /** Csak SELECT típusnál: a választható opciók (JSON-ként tárolva). */
    val options: List<String> = emptyList(),
    val sortOrder: Int = 0,
    /** „Törölt” mező: eltűnik a felületről, de az értékei megmaradnak. */
    val archived: Boolean = false,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubcategoryEntity::class, ["id"], ["subcategoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("subcategoryId"), Index("status")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    /** Egész forint. Mennyiséges kategóriában egységár. */
    val value: Long = 0,
    /** Csak QUANTITY módban látszik és számít; egyedi módban mindig 1-nek veszi az app. */
    val quantity: Int = 1,
    val subcategoryId: Long? = null,
    val status: ItemStatus = ItemStatus.IN_STOCK,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "item_photos",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class ItemPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** A fájl neve a files/photos/ mappán belül (relatív, hogy a mentés hordozható legyen). */
    val fileName: String,
    /** 0 = borítókép. */
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "item_field_values",
    primaryKeys = ["itemId", "fieldId"],
    foreignKeys = [
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(FieldDefinitionEntity::class, ["id"], ["fieldId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fieldId"), Index(value = ["fieldId", "numberValue"])],
)
data class ItemFieldValueEntity(
    val itemId: Long,
    val fieldId: Long,
    /** TEXT, LONG_TEXT, SELECT, LINK */
    val textValue: String? = null,
    /** NUMBER */
    val numberValue: Double? = null,
    /** BOOLEAN */
    val boolValue: Boolean? = null,
    /** DATE – epoch day (LocalDate.toEpochDay) */
    val dateValue: Long? = null,
)
