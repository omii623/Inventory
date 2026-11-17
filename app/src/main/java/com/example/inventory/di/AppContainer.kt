package com.example.inventory.di

import android.content.Context
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.FilterStore
import com.example.inventory.data.repository.ItemRepository
import com.example.inventory.data.settings.SettingsRepository

/** Kézi függőségkezelés (mint a cashAppban): az app-szintű objektumok egy helyen jönnek létre. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: InventoryDatabase by lazy { InventoryDatabase.create(appContext) }
    val photoStorage: PhotoStorage by lazy { PhotoStorage(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database, photoStorage) }
    val itemRepository: ItemRepository by lazy { ItemRepository(database, photoStorage) }
    val filterStore: FilterStore by lazy { FilterStore() }
}
