package com.example.inventory.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.inventory.InventoryApplication
import com.example.inventory.di.AppContainer
import com.example.inventory.ui.categoryedit.CategoryEditViewModel
import com.example.inventory.ui.home.HomeViewModel
import com.example.inventory.ui.itemdetail.ItemDetailViewModel
import com.example.inventory.ui.itemedit.ItemEditViewModel
import com.example.inventory.ui.itemlist.ItemListViewModel
import com.example.inventory.ui.search.SearchViewModel
import com.example.inventory.ui.settings.ArchivedCategoriesViewModel
import com.example.inventory.ui.settings.SettingsViewModel

/** Minden ViewModel itt kapja meg a függőségeit (mint a cashAppban). Új képernyő → új `initializer`. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SettingsViewModel(container().settingsRepository) }
        initializer { HomeViewModel(container().categoryRepository, container().itemRepository) }
        initializer { CategoryEditViewModel(createSavedStateHandle(), container().categoryRepository) }
        initializer { ArchivedCategoriesViewModel(container().categoryRepository) }
        initializer {
            ItemListViewModel(createSavedStateHandle(), container().categoryRepository, container().itemRepository, container().filterStore)
        }
        initializer { SearchViewModel(createSavedStateHandle(), container().itemRepository) }
        initializer {
            ItemEditViewModel(createSavedStateHandle(), container().categoryRepository, container().itemRepository, container().photoStorage)
        }
        initializer { ItemDetailViewModel(createSavedStateHandle(), container().categoryRepository, container().itemRepository) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as InventoryApplication).container
