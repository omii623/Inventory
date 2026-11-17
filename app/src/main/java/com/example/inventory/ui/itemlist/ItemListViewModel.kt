package com.example.inventory.ui.itemlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.SubcategoryEntity
import com.example.inventory.data.db.model.CategorySummary
import com.example.inventory.data.db.model.ItemListRow
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.FilterStore
import com.example.inventory.data.repository.ItemRepository
import com.example.inventory.domain.filter.ItemFilter
import com.example.inventory.domain.filter.SubcategoryChoice
import com.example.inventory.ui.navigation.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemListUiState(
    val loading: Boolean = true,
    /** null: a kategória nem létezik vagy archiválták → a képernyő bezárul. */
    val summary: CategorySummary? = null,
    val subcategories: List<SubcategoryEntity> = emptyList(),
    val fields: List<FieldDefinitionEntity> = emptyList(),
    val filter: ItemFilter = ItemFilter(),
    val rows: List<ItemListRow> = emptyList(),
)

/** A szűrőpanel állapota: a szerkesztett (még nem alkalmazott) szűrő és az élő találatszám. */
data class FilterSheetState(val draft: ItemFilter, val count: Int? = null)

/** Az aktív szűrők egyenként eltávolítható részei (a címkéken ✕). */
sealed interface FilterKey {
    data object Status : FilterKey
    data object Value : FilterKey
    data object Quantity : FilterKey
    data class Field(val id: Long) : FilterKey
}

/** Egy kategória tételei keresővel, alkategória-címkékkel és szűrőkkel (TERV.md 3.7, 3.7a). */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ItemListViewModel(
    savedStateHandle: SavedStateHandle,
    categories: CategoryRepository,
    private val items: ItemRepository,
    private val filterStore: FilterStore,
) : ViewModel() {

    val categoryId: Long = savedStateHandle.get<Long>(Routes.ARG_CATEGORY_ID) ?: 0L

    private val filter = MutableStateFlow(filterStore.get(categoryId))

    val state: StateFlow<ItemListUiState> = combine(
        categories.observeSummary(categoryId),
        categories.observeSubcategories(categoryId),
        categories.observeFields(categoryId),
        filter,
        filter.flatMapLatest { items.observeFiltered(categoryId, it) },
    ) { summary, subcategories, fields, filter, rows ->
        ItemListUiState(loading = false, summary = summary, subcategories = subcategories, fields = fields, filter = filter, rows = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemListUiState())

    private fun setFilter(transform: (ItemFilter) -> ItemFilter) {
        filter.update(transform)
        filterStore.set(categoryId, filter.value)
    }

    fun onQueryChange(query: String) = setFilter { it.copy(query = query) }

    fun onSubcategorySelect(choice: SubcategoryChoice) = setFilter { it.copy(subcategory = choice) }

    fun removeFilter(key: FilterKey) = setFilter {
        when (key) {
            FilterKey.Status -> it.copy(statuses = emptySet())
            FilterKey.Value -> it.copy(valueMin = null, valueMax = null)
            FilterKey.Quantity -> it.copy(quantityMin = null, quantityMax = null)
            is FilterKey.Field -> it.copy(fields = it.fields - key.id)
        }
    }

    fun clearAllFilters() = setFilter { ItemFilter() }

    // ------------------------------------------------------------ szűrőpanel

    private val _sheet = MutableStateFlow<FilterSheetState?>(null)
    val sheet: StateFlow<FilterSheetState?> = _sheet.asStateFlow()

    init {
        // Élő találatszám a panel gombján („Mutasd (12 tétel)”).
        viewModelScope.launch {
            _sheet.filterNotNull()
                .debounce(150)
                .mapLatest { it.draft to items.countFiltered(categoryId, it.draft) }
                .collect { (draft, count) -> _sheet.update { s -> if (s?.draft == draft) s.copy(count = count) else s } }
        }
    }

    fun openSheet() {
        _sheet.value = FilterSheetState(draft = filter.value)
    }

    fun updateDraft(transform: (ItemFilter) -> ItemFilter) = _sheet.update { s -> s?.let { FilterSheetState(transform(it.draft), it.count) } }

    fun clearDraft() = updateDraft { it.clearPanel() }

    fun applyDraft() {
        val draft = _sheet.value?.draft ?: return
        setFilter { draft }
        _sheet.value = null
    }

    fun closeSheet() {
        _sheet.value = null
    }
}
