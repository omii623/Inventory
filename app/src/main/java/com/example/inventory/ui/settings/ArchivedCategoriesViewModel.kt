package com.example.inventory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.db.model.CategorySummary
import com.example.inventory.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Törlésre váró kategória a megerősítő ablakhoz. */
data class PendingDelete(val id: Long, val name: String, val itemCount: Int)

/** Archivált kategóriák (TERV.md 3.1): visszaállítás vagy végleges törlés. */
class ArchivedCategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {

    val categories: StateFlow<List<CategorySummary>?> =
        repository.observeArchivedSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingDelete = MutableStateFlow<PendingDelete?>(null)
    val pendingDelete: StateFlow<PendingDelete?> = _pendingDelete.asStateFlow()

    fun restore(id: Long) {
        viewModelScope.launch { repository.setArchived(id, false) }
    }

    fun requestDelete(summary: CategorySummary) {
        viewModelScope.launch {
            val count = repository.countItems(summary.category.id)
            _pendingDelete.value = PendingDelete(summary.category.id, summary.category.name, count)
        }
    }

    fun confirmDelete() {
        val pending = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { repository.delete(pending.id) }
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }
}
