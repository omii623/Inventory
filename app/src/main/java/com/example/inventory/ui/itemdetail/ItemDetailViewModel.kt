package com.example.inventory.ui.itemdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.ItemRepository
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.navigation.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Egy saját mező és a tétel értéke (null: nincs kitöltve). */
data class Property(val field: FieldDefinitionEntity, val value: ItemFieldValueEntity?)

data class ItemDetailUiState(
    val loading: Boolean = true,
    /** A tételt törölték → a képernyő bezárul. */
    val missing: Boolean = false,
    val item: ItemEntity? = null,
    val category: CategoryEntity? = null,
    val subcategoryName: String? = null,
    val properties: List<Property> = emptyList(),
    /** Fotók sorrendben (fájlnevek); az első a borítókép. */
    val photos: List<String> = emptyList(),
) {
    val quantityMode: Boolean get() = category?.trackingMode == TrackingMode.QUANTITY
}

/** Párbeszédablakok az adatlapon. */
sealed interface ItemDetailDialog {
    data object Delete : ItemDetailDialog
    /** Státusz módosítása panel (TERV.md 3.6). */
    data object Status : ItemDetailDialog
    /** A mennyiség 0-ra csökkent → felajánljuk az „Eladva” státuszt (TERV.md 3.2a). */
    data object ZeroQuantity : ItemDetailDialog
}

/** Tétel adatlapja (TERV.md 4. pont, 3. képernyő): adatok, státuszváltás, mennyiség-léptető, törlés. */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    categories: CategoryRepository,
    private val items: ItemRepository,
) : ViewModel() {

    private val itemId: Long = savedStateHandle.get<Long>(Routes.ARG_ITEM_ID) ?: 0L

    val state: StateFlow<ItemDetailUiState> = items.observe(itemId).flatMapLatest { item ->
        if (item == null) {
            flowOf(ItemDetailUiState(loading = false, missing = true))
        } else {
            combine(
                categories.observe(item.categoryId),
                categories.observeFields(item.categoryId),
                items.observeValues(itemId),
                categories.observeSubcategories(item.categoryId),
                items.observePhotos(itemId),
            ) { category, fields, values, subcategories, photos ->
                val byField = values.associateBy { it.fieldId }
                ItemDetailUiState(
                    loading = false,
                    item = item,
                    category = category,
                    subcategoryName = subcategories.firstOrNull { it.id == item.subcategoryId }?.name,
                    properties = fields.map { Property(it, byField[it.id]) },
                    photos = photos.map { it.fileName },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    private val _dialog = MutableStateFlow<ItemDetailDialog?>(null)
    val dialog: StateFlow<ItemDetailDialog?> = _dialog.asStateFlow()

    fun changeQuantity(delta: Int) {
        val item = state.value.item ?: return
        val newQuantity = (item.quantity + delta).coerceAtLeast(0)
        if (newQuantity == item.quantity) return
        viewModelScope.launch {
            items.setQuantity(item.id, newQuantity)
            if (newQuantity == 0 && item.status != ItemStatus.SOLD) _dialog.value = ItemDetailDialog.ZeroQuantity
        }
    }

    /** Státuszváltás (gyorsváltóból vagy a panelről). Az Eladva tétel kiesik a készletértékből. */
    fun setStatus(status: ItemStatus) {
        _dialog.value = null
        val item = state.value.item ?: return
        if (item.status == status) return
        viewModelScope.launch { items.setStatus(item.id, status) }
    }

    fun openStatusSheet() {
        _dialog.value = ItemDetailDialog.Status
    }

    fun markSold() {
        _dialog.value = null
        viewModelScope.launch { items.setStatus(itemId, ItemStatus.SOLD) }
    }

    fun requestDelete() {
        _dialog.value = ItemDetailDialog.Delete
    }

    fun confirmDelete() {
        _dialog.value = null
        viewModelScope.launch { items.delete(itemId) }
    }

    fun dismissDialog() {
        _dialog.value = null
    }
}
