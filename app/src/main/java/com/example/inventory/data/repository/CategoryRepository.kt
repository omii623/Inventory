package com.example.inventory.data.repository

import androidx.room.withTransaction
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.SubcategoryEntity
import com.example.inventory.data.db.model.CategorySummary
import com.example.inventory.data.db.model.InventoryTotals
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.domain.model.FieldType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Kategóriák, alkategóriák és meződefiníciók. */
class CategoryRepository(
    private val db: InventoryDatabase,
    private val photos: PhotoStorage,
) {
    private val categories = db.categoryDao()
    private val subcategories = db.subcategoryDao()
    private val fields = db.fieldDefinitionDao()
    private val items = db.itemDao()

    // ------------------------------------------------------------ kategóriák

    fun observeSummaries(): Flow<List<CategorySummary>> = categories.observeSummaries(archived = false)

    fun observeArchivedSummaries(): Flow<List<CategorySummary>> = categories.observeSummaries(archived = true)

    fun observeTotals(): Flow<InventoryTotals> = categories.observeTotals()

    /** Egy kategória kártya-adatai (tételszám, érték) – a tétellista fejlécéhez. */
    fun observeSummary(id: Long): Flow<CategorySummary?> =
        categories.observeSummaries(archived = false).map { list -> list.firstOrNull { it.category.id == id } }

    fun observe(id: Long): Flow<CategoryEntity?> = categories.observe(id)

    suspend fun get(id: Long): CategoryEntity? = categories.get(id)

    /** Az összes kategória száma (archiváltakkal együtt). */
    suspend fun count(): Int = categories.count()

    /** Új kategória a lista végére. */
    suspend fun create(category: CategoryEntity): Long =
        categories.insert(category.copy(id = 0, sortOrder = categories.nextSortOrder()))

    suspend fun update(category: CategoryEntity) = categories.update(category)

    /**
     * A kategória-szerkesztő mentése egy tranzakcióban: a kategória, az alkategóriák és a saját mezők.
     * - Alkategóriák: a listából kimaradtak törlődnek (a tételeik „Egyéb”-ek lesznek), az újak létrejönnek.
     * - Saját mezők: a listából kimaradtak archiválódnak (az értékeik megmaradnak), az újak létrejönnek.
     *   Meglévő mező típusa nem változik (a régi értékek a típushoz tartozó oszlopban vannak).
     * - Mindkét listánál a sorrend a lista sorrendje.
     * @return a kategória id-ja
     */
    suspend fun save(
        category: CategoryEntity,
        subcategoryDrafts: List<SubcategoryDraft>,
        fieldDrafts: List<FieldDraft>,
    ): Long = db.withTransaction {
        val id = if (category.id == 0L) {
            categories.insert(category.copy(sortOrder = categories.nextSortOrder()))
        } else {
            categories.update(category)
            category.id
        }

        val keptSubIds = subcategoryDrafts.mapNotNull { it.id }.toSet()
        val removedSubs = subcategories.getForCategory(id).map { it.id }.filterNot { it in keptSubIds }
        if (removedSubs.isNotEmpty()) subcategories.deleteByIds(removedSubs)
        subcategoryDrafts.forEachIndexed { index, draft ->
            val entity = SubcategoryEntity(id = draft.id ?: 0, categoryId = id, name = draft.name.trim(), sortOrder = index)
            if (draft.id == null) subcategories.insert(entity) else subcategories.update(entity)
        }

        val existingFields = fields.getActive(id).associateBy { it.id }
        val keptFieldIds = fieldDrafts.mapNotNull { it.id }.toSet()
        existingFields.keys.filterNot { it in keptFieldIds }.forEach { fields.archive(it) }
        fieldDrafts.forEachIndexed { index, draft ->
            val old = draft.id?.let { existingFields[it] }
            val entity = FieldDefinitionEntity(
                id = old?.id ?: 0,
                categoryId = id,
                name = draft.name.trim(),
                type = old?.type ?: draft.type,
                required = draft.required,
                unit = draft.unit?.trim()?.takeIf { it.isNotEmpty() && (old?.type ?: draft.type) == FieldType.NUMBER },
                options = if ((old?.type ?: draft.type) == FieldType.SELECT) draft.options.map { it.trim() }.filter { it.isNotEmpty() }.distinct() else emptyList(),
                sortOrder = index,
            )
            if (old == null) fields.insert(entity) else fields.update(entity)
        }
        id
    }

    /** Kategória törlése előtti figyelmeztetéshez. */
    suspend fun countItems(categoryId: Long): Int = items.countForCategory(categoryId)

    suspend fun getSubcategories(categoryId: Long): List<SubcategoryEntity> = subcategories.getForCategory(categoryId)

    suspend fun setArchived(id: Long, archived: Boolean) = categories.setArchived(id, archived)

    /**
     * Végleges törlés: a kategória, minden tétele és azok értékei (CASCADE), majd a fotófájlok.
     * A fájlok csak a sikeres adatbázis-törlés után mennek.
     */
    suspend fun delete(id: Long) {
        val category = categories.get(id) ?: return
        val fileNames = db.withTransaction {
            items.photoFileNamesForCategory(id).also { categories.delete(category) }
        }
        photos.delete(fileNames)
    }

    /** Mennyiséggel → Egyedi váltás előtt: hány tétel mennyisége vész el (TERV.md 3.2a). */
    suspend fun countItemsWithQuantityAboveOne(categoryId: Long): Int =
        items.countWithQuantityAboveOne(categoryId)

    // ------------------------------------------------------------ alkategóriák

    fun observeSubcategories(categoryId: Long): Flow<List<SubcategoryEntity>> =
        subcategories.observeForCategory(categoryId)

    suspend fun addSubcategory(categoryId: Long, name: String): Long =
        subcategories.insert(
            SubcategoryEntity(categoryId = categoryId, name = name.trim(), sortOrder = subcategories.nextSortOrder(categoryId))
        )

    suspend fun renameSubcategory(subcategory: SubcategoryEntity, name: String) =
        subcategories.update(subcategory.copy(name = name.trim()))

    suspend fun deleteSubcategory(subcategory: SubcategoryEntity) = subcategories.delete(subcategory)

    // ------------------------------------------------------------ saját mezők

    fun observeFields(categoryId: Long): Flow<List<FieldDefinitionEntity>> = fields.observeActive(categoryId)

    suspend fun getFields(categoryId: Long): List<FieldDefinitionEntity> = fields.getActive(categoryId)

    /** Új mező a kategória mezőinek végére. */
    suspend fun addField(field: FieldDefinitionEntity): Long =
        fields.insert(field.copy(id = 0, sortOrder = fields.nextSortOrder(field.categoryId)))

    suspend fun updateField(field: FieldDefinitionEntity) = fields.update(field)

    /** „Törlés” = archiválás: a mező eltűnik, a tételek értékei megmaradnak. */
    suspend fun archiveField(id: Long) = fields.archive(id)

    suspend fun reorderFields(idsInOrder: List<Long>) = fields.reorder(idsInOrder)
}

/** Egy alkategória a szerkesztőben: [id] null, ha még nincs elmentve. */
data class SubcategoryDraft(val id: Long?, val name: String)

/** Egy saját mező a szerkesztőben: [id] null, ha még nincs elmentve. */
data class FieldDraft(
    val id: Long?,
    val name: String,
    val type: FieldType,
    val required: Boolean = false,
    val unit: String? = null,
    val options: List<String> = emptyList(),
)
