package com.example.inventory.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.db.model.SearchRow
import com.example.inventory.data.repository.ItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Globális keresés (TERV.md 3.7): név és szöveges mezők, az összes nem archivált kategóriában. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    items: ItemRepository,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** null: még nincs keresőszó. */
    val results: StateFlow<List<SearchRow>?> = query
        .debounce(200)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(null) else items.observeSearch(q).map { it } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    private companion object {
        const val KEY_QUERY = "query"
    }
}
