package com.example.inventory.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.db.model.CategorySummary
import com.example.inventory.data.db.model.InventoryTotals
import kotlinx.coroutines.flow.Flow

/*
 * Egy tétel darabszáma: mennyiséges kategóriában a quantity, egyébként 1.
 * Ez a kifejezés több lekérdezésben is szerepel – ha változik, mindenhol változtatni kell.
 */
private const val PIECES = "(CASE c.trackingMode WHEN 'QUANTITY' THEN i.quantity ELSE 1 END)"

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observe(id: Long): Flow<CategoryEntity?>

    @Query("UPDATE categories SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** Kategóriakártyák (archivált nélkül vagy csak archiváltak) tételszámmal és értékkel. */
    @Query(
        """
        SELECT c.*,
            COALESCE(SUM(CASE WHEN i.status != 'SOLD' THEN $PIECES ELSE 0 END), 0) AS itemCount,
            COALESCE(SUM(CASE WHEN i.status != 'SOLD' THEN i.value * $PIECES ELSE 0 END), 0) AS totalValue
        FROM categories c
        LEFT JOIN items i ON i.categoryId = c.id
        WHERE c.archived = :archived
        GROUP BY c.id
        ORDER BY c.sortOrder, c.id
        """
    )
    fun observeSummaries(archived: Boolean = false): Flow<List<CategorySummary>>

    /** A kezdőlap összesítője: teljes készletérték, tételszám, kategóriaszám (archivált nélkül). */
    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(i.value * $PIECES), 0)
                FROM items i JOIN categories c ON c.id = i.categoryId
                WHERE i.status != 'SOLD' AND c.archived = 0) AS totalValue,
            (SELECT COALESCE(SUM($PIECES), 0)
                FROM items i JOIN categories c ON c.id = i.categoryId
                WHERE i.status != 'SOLD' AND c.archived = 0) AS itemCount,
            (SELECT COUNT(*) FROM categories WHERE archived = 0) AS categoryCount
        """
    )
    fun observeTotals(): Flow<InventoryTotals>
}
