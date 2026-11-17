package com.example.inventory.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.inventory.data.db.dao.CategoryDao
import com.example.inventory.data.db.dao.FieldDefinitionDao
import com.example.inventory.data.db.dao.ItemDao
import com.example.inventory.data.db.dao.SubcategoryDao
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.data.db.entity.ItemPhotoEntity
import com.example.inventory.data.db.entity.SubcategoryEntity

@Database(
    entities = [
        CategoryEntity::class,
        SubcategoryEntity::class,
        FieldDefinitionEntity::class,
        ItemEntity::class,
        ItemPhotoEntity::class,
        ItemFieldValueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun fieldDefinitionDao(): FieldDefinitionDao
    abstract fun itemDao(): ItemDao

    companion object {
        const val NAME = "inventory.db"

        /**
         * Az adatbázis. Sémaváltozásnál verziót kell emelni és migrációt írni
         * (a séma JSON-ok az app/schemas mappában vannak).
         */
        fun create(context: Context): InventoryDatabase =
            Room.databaseBuilder(context.applicationContext, InventoryDatabase::class.java, NAME).build()
    }
}
