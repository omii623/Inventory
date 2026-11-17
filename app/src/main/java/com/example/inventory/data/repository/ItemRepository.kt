package com.example.inventory.data.repository

import androidx.room.withTransaction
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.data.db.entity.ItemPhotoEntity
import com.example.inventory.data.db.ItemFilterQueryBuilder
import com.example.inventory.data.db.model.ItemListRow
import com.example.inventory.data.db.model.ItemText
import com.example.inventory.data.db.model.SearchRow
import com.example.inventory.domain.filter.ItemFilter
import com.example.inventory.domain.filter.ItemTextMatcher
import com.example.inventory.data.db.model.RecentItem
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.domain.model.ItemStatus
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Tételek, a saját mezők értékei és a fotók. */
class ItemRepository(
    private val db: InventoryDatabase,
    private val photos: PhotoStorage,
) {
    private val items = db.itemDao()

    fun observeForCategory(categoryId: Long): Flow<List<ItemEntity>> = items.observeForCategory(categoryId)

    fun observe(id: Long): Flow<ItemEntity?> = items.observe(id)

    fun observeRows(categoryId: Long): Flow<List<ItemListRow>> = items.observeRowsForCategory(categoryId)

    /**
     * Szűrt tétellista (TERV.md 3.7, 3.7a): a szerkezeti feltételek SQL-ben, a szöveges keresés
     * (keresőszó, „tartalmazza”) ékezet- és kisbetű-függetlenül Kotlinban.
     */
    fun observeFiltered(categoryId: Long, filter: ItemFilter): Flow<List<ItemListRow>> {
        val built = ItemFilterQueryBuilder.build(categoryId, filter)
        val rows = items.observeRowsRaw(SimpleSQLiteQuery(built.sql, built.args.toTypedArray()))
        if (filter.query.isBlank() && filter.containsFilters.isEmpty()) return rows
        return combine(rows, items.observeTextsForCategory(categoryId)) { list, texts -> applyText(list, texts, filter) }
    }

    /** A szűrőpanel élő találatszámához: ugyanaz, mint [observeFiltered], egyszeri lekérdezéssel. */
    suspend fun countFiltered(categoryId: Long, filter: ItemFilter): Int {
        val built = ItemFilterQueryBuilder.build(categoryId, filter)
        val rows = items.rowsRaw(SimpleSQLiteQuery(built.sql, built.args.toTypedArray()))
        if (filter.query.isBlank() && filter.containsFilters.isEmpty()) return rows.size
        return applyText(rows, items.textsForCategory(categoryId), filter).size
    }

    private fun applyText(rows: List<ItemListRow>, texts: List<ItemText>, filter: ItemFilter): List<ItemListRow> {
        val byItem = texts.groupBy { it.itemId }.mapValues { (_, list) -> list.associate { it.fieldId to it.text } }
        val contains = filter.containsFilters
        return rows.filter { ItemTextMatcher.matches(it.item.name, byItem[it.item.id].orEmpty(), filter.query, contains) }
    }

    /** Globális keresés (TERV.md 3.7): név és szöveges mezők, az összes nem archivált kategóriában. */
    fun observeSearch(query: String): Flow<List<SearchRow>> =
        combine(items.observeSearchRows(), items.observeAllTexts()) { rows, texts ->
            val byItem = texts.groupBy { it.itemId }.mapValues { (_, list) -> list.associate { it.fieldId to it.text } }
            rows.filter { ItemTextMatcher.matches(it.item.name, byItem[it.item.id].orEmpty(), query, emptyMap()) }
        }

    fun observeLatest(): Flow<RecentItem?> = items.observeLatest()

    fun observeValues(itemId: Long): Flow<List<ItemFieldValueEntity>> = items.observeValues(itemId)

    fun observePhotos(itemId: Long): Flow<List<ItemPhotoEntity>> = items.observePhotos(itemId)

    suspend fun get(id: Long): ItemEntity? = items.get(id)

    suspend fun getValues(itemId: Long): List<ItemFieldValueEntity> = items.getValues(itemId)

    /** A tétel fotóinak fájlnevei sorrendben (az első a borítókép). */
    suspend fun getPhotoFileNames(itemId: Long): List<String> = items.getPhotos(itemId).map { it.fileName }

    /**
     * Új vagy meglévő tétel mentése a saját mezők értékeivel, egy tranzakcióban (TERV.md 7. pont).
     *
     * @param values a kitöltött mezők értékei (az itemId-t itt állítjuk be)
     * @param clearedFieldIds azok a mezők, amiket a felhasználó kiürített – ezek értéke törlődik
     * @param photoFileNames a tétel fotói sorrendben (az első a borítókép), legfeljebb [PhotoStorage.MAX_PHOTOS];
     *   null: a fotók nem változnak. A listából kimaradt fotók fájljai a sikeres mentés után törlődnek.
     * @return a tétel id-ja
     */
    suspend fun save(
        item: ItemEntity,
        values: List<ItemFieldValueEntity>,
        clearedFieldIds: List<Long> = emptyList(),
        photoFileNames: List<String>? = null,
    ): Long {
        val (id, removedFiles) = db.withTransaction {
            val id = if (item.id == 0L) {
                // Új tétel: a createdAt marad (alapból a létrehozás pillanata).
                items.insert(item.copy(updatedAt = item.createdAt))
            } else {
                items.update(item.copy(updatedAt = System.currentTimeMillis()))
                item.id
            }
            if (clearedFieldIds.isNotEmpty()) items.deleteValues(id, clearedFieldIds)
            if (values.isNotEmpty()) items.upsertValues(values.map { it.copy(itemId = id) })
            id to (photoFileNames?.let { syncPhotos(id, it) } ?: emptyList())
        }
        photos.delete(removedFiles)
        return id
    }

    /** A tétel fotósorainak igazítása a megadott listához. @return a kimaradt (törlendő) fájlok neve */
    private suspend fun syncPhotos(itemId: Long, fileNames: List<String>): List<String> {
        val wanted = fileNames.distinct().take(PhotoStorage.MAX_PHOTOS)
        val existing = items.getPhotos(itemId)
        val removed = existing.filter { it.fileName !in wanted }
        if (removed.isNotEmpty()) items.deletePhotosByIds(removed.map { it.id })
        val byName = existing.associateBy { it.fileName }
        val inserts = mutableListOf<ItemPhotoEntity>()
        wanted.forEachIndexed { index, name ->
            val row = byName[name]
            if (row == null) inserts += ItemPhotoEntity(itemId = itemId, fileName = name, sortOrder = index)
            else if (row.sortOrder != index) items.setPhotoOrder(row.id, index)
        }
        if (inserts.isNotEmpty()) items.insertPhotos(inserts)
        return removed.map { it.fileName }
    }

    /** Elárvult fotófájlok takarítása (induláskor). */
    suspend fun cleanupPhotoFiles() = photos.cleanup(items.allPhotoFileNames().toSet())

    suspend fun setStatus(id: Long, status: ItemStatus) = items.setStatus(id, status)

    suspend fun setQuantity(id: Long, quantity: Int) = items.setQuantity(id, quantity.coerceAtLeast(0))

    /** Tétel törlése a fotófájlokkal együtt (a fájlok a sikeres adatbázis-törlés után mennek). */
    suspend fun delete(id: Long) {
        val item = items.get(id) ?: return
        val fileNames = db.withTransaction {
            items.photoFileNames(id).also { items.delete(item) }
        }
        photos.delete(fileNames)
    }
}
