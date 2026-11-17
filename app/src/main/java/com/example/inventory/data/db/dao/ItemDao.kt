package com.example.inventory.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Upsert
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.data.db.entity.ItemPhotoEntity
import com.example.inventory.data.db.entity.SubcategoryEntity
import com.example.inventory.data.db.model.ItemText
import com.example.inventory.data.db.model.SearchRow
import com.example.inventory.data.db.model.ItemListRow
import com.example.inventory.data.db.model.RecentItem
import com.example.inventory.domain.model.ItemStatus
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** Alapértelmezett lista-sorrend (TERV.md 3.7): Lefoglalva → Készleten → Eladva, azon belül a legújabb elöl. */
private const val STATUS_ORDER =
    "CASE status WHEN 'RESERVED' THEN 0 WHEN 'IN_STOCK' THEN 1 ELSE 2 END, createdAt DESC, id DESC"

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE categoryId = :categoryId ORDER BY $STATUS_ORDER")
    fun observeForCategory(categoryId: Long): Flow<List<ItemEntity>>

    /** A tétellista sorai az alkategória nevével, a lista-sorrendben. */
    @Query(
        """
        SELECT items.*, s.name AS subcategoryName,
            (SELECT p.fileName FROM item_photos p WHERE p.itemId = items.id ORDER BY p.sortOrder, p.id LIMIT 1) AS coverFileName
        FROM items
        LEFT JOIN subcategories s ON s.id = items.subcategoryId
        WHERE items.categoryId = :categoryId
        ORDER BY CASE items.status WHEN 'RESERVED' THEN 0 WHEN 'IN_STOCK' THEN 1 ELSE 2 END,
            items.createdAt DESC, items.id DESC
        """
    )
    fun observeRowsForCategory(categoryId: Long): Flow<List<ItemListRow>>

    /** A kezdőlap „Legutóbb hozzáadva” sora (csak nem archivált kategóriából), a kategória és alkategória nevével. */
    @Query(
        """
        SELECT i.id, i.name, i.value, i.quantity, i.status, c.trackingMode,
            c.name AS categoryName, s.name AS subcategoryName,
            (SELECT p.fileName FROM item_photos p WHERE p.itemId = i.id ORDER BY p.sortOrder, p.id LIMIT 1) AS coverFileName
        FROM items i
        JOIN categories c ON c.id = i.categoryId
        LEFT JOIN subcategories s ON s.id = i.subcategoryId
        WHERE c.archived = 0
        ORDER BY i.createdAt DESC, i.id DESC LIMIT 1
        """
    )
    fun observeLatest(): Flow<RecentItem?>

    /** Szűrt tétellista (ItemFilterQueryBuilder). Az observedEntities miatt minden érintett tábla változásakor frissül. */
    @RawQuery(observedEntities = [ItemEntity::class, ItemFieldValueEntity::class, ItemPhotoEntity::class, SubcategoryEntity::class])
    fun observeRowsRaw(query: SupportSQLiteQuery): Flow<List<ItemListRow>>

    @RawQuery
    suspend fun rowsRaw(query: SupportSQLiteQuery): List<ItemListRow>

    /** Egy kategória tételeinek szöveges mezőértékei (archivált mezők nélkül) a kereséshez. */
    @Query(
        """
        SELECT v.itemId AS itemId, v.fieldId AS fieldId, v.textValue AS text
        FROM item_field_values v
        JOIN field_definitions f ON f.id = v.fieldId
        WHERE f.categoryId = :categoryId AND f.archived = 0 AND v.textValue IS NOT NULL
        """
    )
    fun observeTextsForCategory(categoryId: Long): Flow<List<ItemText>>

    @Query(
        """
        SELECT v.itemId AS itemId, v.fieldId AS fieldId, v.textValue AS text
        FROM item_field_values v
        JOIN field_definitions f ON f.id = v.fieldId
        WHERE f.categoryId = :categoryId AND f.archived = 0 AND v.textValue IS NOT NULL
        """
    )
    suspend fun textsForCategory(categoryId: Long): List<ItemText>

    /** Globális keresés alapja: minden tétel a nem archivált kategóriákból (a szöveges szűrés Kotlinban). */
    @Query(
        """
        SELECT items.*, c.name AS categoryName, c.trackingMode AS trackingMode, s.name AS subcategoryName,
            (SELECT p.fileName FROM item_photos p WHERE p.itemId = items.id ORDER BY p.sortOrder, p.id LIMIT 1) AS coverFileName
        FROM items
        JOIN categories c ON c.id = items.categoryId
        LEFT JOIN subcategories s ON s.id = items.subcategoryId
        WHERE c.archived = 0
        ORDER BY CASE items.status WHEN 'RESERVED' THEN 0 WHEN 'IN_STOCK' THEN 1 ELSE 2 END, items.createdAt DESC, items.id DESC
        """
    )
    fun observeSearchRows(): Flow<List<SearchRow>>

    @Query(
        """
        SELECT v.itemId AS itemId, v.fieldId AS fieldId, v.textValue AS text
        FROM item_field_values v
        JOIN field_definitions f ON f.id = v.fieldId
        JOIN categories c ON c.id = f.categoryId
        WHERE c.archived = 0 AND f.archived = 0 AND v.textValue IS NOT NULL
        """
    )
    fun observeAllTexts(): Flow<List<ItemText>>

    @Query("UPDATE items SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: ItemStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE items SET quantity = :quantity, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setQuantity(id: Long, quantity: Int, updatedAt: Long = System.currentTimeMillis())

    /** Kategória törlése előtti figyelmeztetéshez: hány tétel törlődik vele. */
    @Query("SELECT COUNT(*) FROM items WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    /** Mennyiséggel → Egyedi váltás előtti figyelmeztetéshez (TERV.md 3.2a). */
    @Query("SELECT COUNT(*) FROM items WHERE categoryId = :categoryId AND quantity > 1")
    suspend fun countWithQuantityAboveOne(categoryId: Long): Int

    // ------------------------------------------------------------ saját mezők értékei

    @Upsert
    suspend fun upsertValues(values: List<ItemFieldValueEntity>)

    @Query("DELETE FROM item_field_values WHERE itemId = :itemId AND fieldId IN (:fieldIds)")
    suspend fun deleteValues(itemId: Long, fieldIds: List<Long>)

    @Query("SELECT * FROM item_field_values WHERE itemId = :itemId")
    suspend fun getValues(itemId: Long): List<ItemFieldValueEntity>

    @Query("SELECT * FROM item_field_values WHERE itemId = :itemId")
    fun observeValues(itemId: Long): Flow<List<ItemFieldValueEntity>>

    // ------------------------------------------------------------ fotók

    @Insert
    suspend fun insertPhotos(photos: List<ItemPhotoEntity>)

    @Query("SELECT * FROM item_photos WHERE itemId = :itemId ORDER BY sortOrder, id")
    fun observePhotos(itemId: Long): Flow<List<ItemPhotoEntity>>

    @Query("SELECT fileName FROM item_photos WHERE itemId = :itemId")
    suspend fun photoFileNames(itemId: Long): List<String>

    @Query("SELECT * FROM item_photos WHERE itemId = :itemId ORDER BY sortOrder, id")
    suspend fun getPhotos(itemId: Long): List<ItemPhotoEntity>

    @Query("DELETE FROM item_photos WHERE id IN (:ids)")
    suspend fun deletePhotosByIds(ids: List<Long>)

    @Query("UPDATE item_photos SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setPhotoOrder(id: Long, sortOrder: Int)

    /** Minden hivatkozott fotófájl – az elárvult fájlok takarításához. */
    @Query("SELECT fileName FROM item_photos")
    suspend fun allPhotoFileNames(): List<String>

    /** Kategória törlésekor: minden tétel minden fotója (a fájlokat is törölni kell). */
    @Query("SELECT p.fileName FROM item_photos p JOIN items i ON i.id = p.itemId WHERE i.categoryId = :categoryId")
    suspend fun photoFileNamesForCategory(categoryId: Long): List<String>
}
