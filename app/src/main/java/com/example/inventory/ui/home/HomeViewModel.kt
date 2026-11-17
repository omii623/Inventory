package com.example.inventory.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.db.model.CategorySummary
import com.example.inventory.data.db.model.InventoryTotals
import com.example.inventory.data.db.model.RecentItem
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.ItemRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val totals: InventoryTotals = InventoryTotals(0, 0, 0),
    val categories: List<CategorySummary> = emptyList(),
    val recent: RecentItem? = null,
)

class HomeViewModel(categories: CategoryRepository, items: ItemRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        categories.observeTotals(),
        categories.observeSummaries(),
        items.observeLatest(),
    ) { totals, summaries, recent ->
        HomeUiState(loading = false, totals = totals, categories = summaries, recent = recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
