package com.example.inventory.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldDefinitionDao {

    @Insert
    suspend fun insert(field: FieldDefinitionEntity): Long

    @Update
    suspend fun update(field: FieldDefinitionEntity)

    @Query("SELECT * FROM field_definitions WHERE id = :id")
    suspend fun get(id: Long): FieldDefinitionEntity?

    /** A kategória élő (nem archivált) mezői, a felhasználó által beállított sorrendben. */
    @Query("SELECT * FROM field_definitions WHERE categoryId = :categoryId AND archived = 0 ORDER BY sortOrder, id")
    fun observeActive(categoryId: Long): Flow<List<FieldDefinitionEntity>>

    @Query("SELECT * FROM field_definitions WHERE categoryId = :categoryId AND archived = 0 ORDER BY sortOrder, id")
    suspend fun getActive(categoryId: Long): List<FieldDefinitionEntity>

    /** „Törlés”: a mező eltűnik, az értékei megmaradnak (TERV.md 3.4). */
    @Query("UPDATE field_definitions SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM field_definitions WHERE categoryId = :categoryId")
    suspend fun nextSortOrder(categoryId: Long): Int

    @Query("UPDATE field_definitions SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Húzással átrendezés után: a lista sorrendje lesz az új sortOrder. */
    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}
