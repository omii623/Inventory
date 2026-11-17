package com.example.inventory.ui.categoryedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.db.entity.CategoryEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.FieldDraft
import com.example.inventory.data.repository.SubcategoryDraft
import com.example.inventory.domain.model.CategoryIcon
import com.example.inventory.domain.model.FieldType
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.components.CategoryPalette
import com.example.inventory.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Egy alkategória a szerkesztőben. A [key] a felületi azonosító (új elemnél negatív). */
data class EditableSubcategory(val key: Long, val id: Long?, val name: String)

/**
 * Egy saját mező a szerkesztőben. A [key] a felületi azonosító (új elemnél negatív),
 * az [id] null, ha még nincs elmentve.
 */
data class EditableField(
    val key: Long,
    val id: Long?,
    val name: String,
    val type: FieldType,
    val required: Boolean = false,
    val unit: String? = null,
    val options: List<String> = emptyList(),
)

/** A szerkesztő éppen nyitott párbeszédablaka. */
sealed interface CategoryEditDialog {
    /** Új mező ([editing] == null) vagy meglévő szerkesztése – alsó panelen. */
    data class Field(val editing: EditableField?) : CategoryEditDialog
    /** Új alkategória ([editing] == null) vagy átnevezés. */
    data class Subcategory(val editing: EditableSubcategory?) : CategoryEditDialog
    data class ModeWarning(val affectedItems: Int) : CategoryEditDialog
    data object Archive : CategoryEditDialog
    data class Delete(val itemCount: Int) : CategoryEditDialog
}

data class CategoryEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val nameError: Boolean = false,
    val color: Long = CategoryPalette.colors[4],
    val icon: String = "box",
    val trackingMode: TrackingMode = TrackingMode.UNIQUE,
    val subcategories: List<EditableSubcategory> = emptyList(),
    val fields: List<EditableField> = emptyList(),
    val dialog: CategoryEditDialog? = null,
    /** Mentés / archiválás / törlés után true → a képernyő bezárul. */
    val finished: Boolean = false,
)

/**
 * Kategória létrehozása és szerkesztése (TERV.md 3.1–3.3). A módosítások a [state]-ben gyűlnek,
 * és a Mentés egy tranzakcióban írja be őket. Archiválás és törlés azonnal történik (megerősítés után).
 */
class CategoryEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {

    private val categoryId: Long = savedStateHandle.get<Long>(Routes.ARG_CATEGORY_ID) ?: 0L
    private var original: CategoryEntity? = null
    private var nextNewKey = -1L

    /** A betöltés utáni állapot – ehhez képest van-e mentetlen változás. */
    private var initialSnapshot: List<Any?> = blankState().snapshot()

    private fun blankState() = CategoryEditUiState(isNew = categoryId == 0L)

    private fun CategoryEditUiState.snapshot(): List<Any?> =
        listOf(name.trim(), color, icon, trackingMode, subcategories.map { it.id to it.name }, fields.map { it.copy(key = 0) })

    /** Van-e mentetlen változás (a „vissza” előtti megerősítéshez). */
    fun hasChanges(): Boolean = !_state.value.loading && initialSnapshot != _state.value.snapshot()

    private val _state = MutableStateFlow(CategoryEditUiState(isNew = categoryId == 0L, loading = categoryId != 0L))
    val state: StateFlow<CategoryEditUiState> = _state.asStateFlow()

    init {
        if (categoryId != 0L) viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val category = repository.get(categoryId) ?: run { _state.update { it.copy(finished = true) }; return }
        original = category
        val subs = repository.getSubcategories(categoryId).map { EditableSubcategory(key = it.id, id = it.id, name = it.name) }
        val fields = repository.getFields(categoryId).map {
            EditableField(key = it.id, id = it.id, name = it.name, type = it.type, required = it.required, unit = it.unit, options = it.options)
        }
        _state.update {
            it.copy(
                loading = false,
                isNew = false,
                name = category.name,
                color = category.color,
                icon = category.icon,
                trackingMode = category.trackingMode,
                subcategories = subs,
                fields = fields,
            )
        }
        initialSnapshot = _state.value.snapshot()
    }

    fun onNameChange(name: String) = _state.update { it.copy(name = name, nameError = false) }

    fun onColorSelect(color: Long) = _state.update { it.copy(color = color) }

    fun onIconSelect(icon: CategoryIcon) = _state.update { it.copy(icon = icon.encode()) }

    /** Mennyiséggel → Egyedi váltásnál figyelmeztet, ha van 1-nél nagyobb mennyiségű tétel (TERV.md 3.2a). */
    fun onTrackingModeSelect(mode: TrackingMode) {
        val current = _state.value
        if (mode == current.trackingMode) return
        if (mode == TrackingMode.UNIQUE && original?.trackingMode == TrackingMode.QUANTITY) {
            viewModelScope.launch {
                val affected = repository.countItemsWithQuantityAboveOne(categoryId)
                if (affected > 0) _state.update { it.copy(dialog = CategoryEditDialog.ModeWarning(affected)) }
                else _state.update { it.copy(trackingMode = mode) }
            }
        } else {
            _state.update { it.copy(trackingMode = mode) }
        }
    }

    fun confirmModeWarning() = _state.update { it.copy(trackingMode = TrackingMode.UNIQUE, dialog = null) }

    // ------------------------------------------------------------ alkategóriák

    fun onAddSubcategory() = _state.update { it.copy(dialog = CategoryEditDialog.Subcategory(editing = null)) }

    fun onEditSubcategory(sub: EditableSubcategory) = _state.update { it.copy(dialog = CategoryEditDialog.Subcategory(editing = sub)) }

    fun onSubcategoryNameConfirm(name: String) {
        val trimmed = name.trim()
        val dialog = _state.value.dialog as? CategoryEditDialog.Subcategory ?: return
        _state.update { s ->
            if (trimmed.isEmpty()) return@update s.copy(dialog = null)
            val list = if (dialog.editing == null) {
                s.subcategories + EditableSubcategory(key = nextNewKey--, id = null, name = trimmed)
            } else {
                s.subcategories.map { if (it.key == dialog.editing.key) it.copy(name = trimmed) else it }
            }
            s.copy(subcategories = list, dialog = null)
        }
    }

    fun onRemoveSubcategory(key: Long) = _state.update { s -> s.copy(subcategories = s.subcategories.filterNot { it.key == key }) }

    // ------------------------------------------------------------ saját mezők (TERV.md 3.4)

    fun onAddField() = _state.update { it.copy(dialog = CategoryEditDialog.Field(editing = null)) }

    fun onEditField(field: EditableField) = _state.update { it.copy(dialog = CategoryEditDialog.Field(editing = field)) }

    /** A mezőszerkesztő panel mentése: új mező a lista végére, meglévő a helyén frissül. */
    fun onFieldSave(field: EditableField) {
        _state.update { s ->
            val list = if (s.fields.any { it.key == field.key }) {
                s.fields.map { if (it.key == field.key) field else it }
            } else {
                s.fields + field.copy(key = nextNewKey--)
            }
            s.copy(fields = list, dialog = null)
        }
    }

    /** „Törlés”: mentéskor archiválódik, a tételek értékei megmaradnak. */
    fun onFieldDelete(key: Long) = _state.update { s -> s.copy(fields = s.fields.filterNot { it.key == key }, dialog = null) }

    /** Húzással átrendezés: a [from] helyen lévő mező a [to] helyre kerül. */
    fun onMoveField(from: Int, to: Int) = _state.update { s ->
        if (from !in s.fields.indices || to !in s.fields.indices) return@update s
        s.copy(fields = s.fields.toMutableList().apply { add(to, removeAt(from)) })
    }

    // ------------------------------------------------------------ mentés, archiválás, törlés

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        val base = original ?: CategoryEntity(name = "", icon = "", color = 0)
        val category = base.copy(name = s.name.trim(), color = s.color, icon = s.icon, trackingMode = s.trackingMode)
        viewModelScope.launch {
            repository.save(
                category,
                s.subcategories.map { SubcategoryDraft(it.id, it.name) },
                s.fields.map { FieldDraft(it.id, it.name, it.type, it.required, it.unit, it.options) },
            )
            _state.update { it.copy(finished = true) }
        }
    }

    fun onArchiveRequest() = _state.update { it.copy(dialog = CategoryEditDialog.Archive) }

    fun confirmArchive() {
        viewModelScope.launch {
            repository.setArchived(categoryId, true)
            _state.update { it.copy(dialog = null, finished = true) }
        }
    }

    fun onDeleteRequest() {
        viewModelScope.launch {
            val count = repository.countItems(categoryId)
            _state.update { it.copy(dialog = CategoryEditDialog.Delete(count)) }
        }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            repository.delete(categoryId)
            _state.update { it.copy(dialog = null, finished = true) }
        }
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }
}
