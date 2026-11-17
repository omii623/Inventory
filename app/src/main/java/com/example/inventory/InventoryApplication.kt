package com.example.inventory

import android.app.Application
import com.example.inventory.data.seed.SampleData
import com.example.inventory.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InventoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Az app teljes élettartamára szóló háttérmunkákhoz. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Debug buildben az első indításkor mintaadatok (Laptop, Ékszer); release-ben nem csinál semmit.
        applicationScope.launch {
            SampleData.seedOnce(this@InventoryApplication, container.categoryRepository, container.itemRepository)
            container.itemRepository.cleanupPhotoFiles()
        }
    }
}
