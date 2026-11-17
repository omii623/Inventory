package com.example.inventory.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.inventory.data.db.entity.SubcategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubcategoryDao {

    @Insert
    suspend fun insert(subcategory: SubcategoryEntity): Long

    @Update
    suspend fun update(subcategory: SubcategoryEntity)

    /** A tételeknél az alkategória ilyenkor NULL lesz (SET_NULL) → „Egyéb”. */
    @Delete
    suspend fun delete(subcategory: SubcategoryEntity)

    @Query("SELECT * FROM subcategories WHERE categoryId = :categoryId ORDER BY sortOrder, id")
    fun observeForCategory(categoryId: Long): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategories WHERE categoryId = :categoryId ORDER BY sortOrder, id")
    suspend fun getForCategory(categoryId: Long): List<SubcategoryEntity>

    @Query("DELETE FROM subcategories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM subcategories WHERE categoryId = :categoryId")
    suspend fun nextSortOrder(categoryId: Long): Int
}
