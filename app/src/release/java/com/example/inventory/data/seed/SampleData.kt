package com.example.inventory.data.seed

import android.content.Context
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.ItemRepository

/** Release buildben nincsenek mintaadatok (a debug változat a src/debug-ban van). */
object SampleData {
    @Suppress("UNUSED_PARAMETER")
    suspend fun seedOnce(context: Context, categories: CategoryRepository, items: ItemRepository) = Unit
}
