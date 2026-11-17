package com.example.inventory.ui.itemedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemEntity
import com.example.inventory.data.db.entity.SubcategoryEntity
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.ItemRepository
import com.example.inventory.domain.form.FieldError
import com.example.inventory.domain.form.FieldInput
import com.example.inventory.domain.form.FieldValues
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.navigation.Routes
import com.example.inventory.util.NumberFormats
import android.net.Uri
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A fix mezők hibái. */
enum class FixedError { NAME_REQUIRED, VALUE_INVALID, QUANTITY_INVALID }

data class ItemEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val categoryName: String = "",
    val trackingMode: TrackingMode = TrackingMode.UNIQUE,
    val name: String = "",
    /** Érték (Ft) szövegként, ahogy beírták; kezdőérték 0 (TERV.md 3.2). */
    val value: String = "0",
    val quantity: String = "1",
    val subcategoryId: Long? = null,
    val subcategories: List<SubcategoryEntity> = emptyList(),
    val fields: List<FieldDefinitionEntity> = emptyList(),
    val inputs: Map<Long, FieldInput> = emptyMap(),
    val fieldErrors: Map<Long, FieldError> = emptyMap(),
    /** A tétel fotói sorrendben (fájlnevek); az első a borítókép. */
    val photos: List<String> = emptyList(),
    /** Épp folyik egy fotó beolvasása. */
    val importingPhotos: Boolean = false,
    /** Legalább egy fotót nem sikerült beolvasni. */
    val photoError: Boolean = false,
    val nameError: FixedError? = null,
    val valueError: FixedError? = null,
    val quantityError: FixedError? = null,
    val saving: Boolean = false,
    /** Sikeres mentés után true → a képernyő bezárul. */
    val finished: Boolean = false,
)

/**
 * Új tétel / tétel szerkesztése (TERV.md 7. pont): a kategória mezőiből dinamikusan épülő űrlap,
 * validálás, és mentés egy tranzakcióban.
 */
class ItemEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val categories: CategoryRepository,
    private val items: ItemRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    private val categoryId: Long = savedStateHandle.get<Long>(Routes.ARG_CATEGORY_ID) ?: 0L
    private val itemId: Long = savedStateHandle.get<Long>(Routes.ARG_ITEM_ID) ?: 0L
    private var original: ItemEntity? = null
    private var started = false

    /** Ebben a szerkesztésben beolvasott, még el nem mentett fotók – mégse esetén törlődnek. */
    private val pendingPhotos = mutableSetOf<String>()

    /** A betöltés utáni állapot – ehhez képest van-e mentetlen változás. */
    private var initialSnapshot: List<Any?>? = null

    private fun ItemEditUiState.snapshot(): List<Any?> = listOf(name, value, quantity, subcategoryId, inputs, photos)

    /** Van-e mentetlen változás (a „vissza” előtti megerősítéshez). */
    fun hasChanges(): Boolean = initialSnapshot?.let { it != _state.value.snapshot() } ?: false
    private var cameraFile: File? = null

    private val _state = MutableStateFlow(ItemEditUiState(isNew = itemId == 0L))
    val state: StateFlow<ItemEditUiState> = _state.asStateFlow()

    /** A képernyő hívja egyszer, a felület nyelvével (a számok tizedesjeléhez). */
    fun start(locale: Locale) {
        if (started) return
        started = true
        viewModelScope.launch { load(locale) }
    }

    private suspend fun load(locale: Locale) {
        val item = if (itemId != 0L) items.get(itemId) else null
        val catId = item?.categoryId ?: categoryId
        val category = categories.get(catId) ?: run { _state.update { it.copy(finished = true) }; return }
        original = item
        val fields = categories.getFields(catId)
        val values = if (item != null) items.getValues(item.id).associateBy { it.fieldId } else emptyMap()
        val photos = if (item != null) items.getPhotoFileNames(item.id) else emptyList()
        _state.update {
            it.copy(
                loading = false,
                categoryName = category.name,
                trackingMode = category.trackingMode,
                name = item?.name.orEmpty(),
                value = (item?.value ?: 0L).toString(),
                quantity = (item?.quantity ?: 1).toString(),
                subcategoryId = item?.subcategoryId,
                subcategories = categories.getSubcategories(catId),
                fields = fields,
                inputs = fields.associate { f -> f.id to FieldValues.toInput(f, values[f.id], locale) },
                photos = photos,
            )
        }
        initialSnapshot = _state.value.snapshot()
    }

    fun onNameChange(text: String) = _state.update { it.copy(name = text, nameError = null) }

    fun onValueChange(text: String) = _state.update { it.copy(value = text.filter { c -> c.isDigit() || c == ' ' }, valueError = null) }

    fun onQuantityChange(text: String) = _state.update { it.copy(quantity = text.filter(Char::isDigit), quantityError = null) }

    fun onSubcategorySelect(id: Long?) = _state.update { it.copy(subcategoryId = id) }

    fun onFieldChange(fieldId: Long, input: FieldInput) =
        _state.update { it.copy(inputs = it.inputs + (fieldId to input), fieldErrors = it.fieldErrors - fieldId) }

    // ------------------------------------------------------------ fotók (TERV.md 3.5)

    /** Ennyi fotó adható még hozzá. */
    val remainingPhotos: Int get() = (PhotoStorage.MAX_PHOTOS - _state.value.photos.size).coerceAtLeast(0)

    /** Galériából választott képek beolvasása (a korláton felülieket figyelmen kívül hagyja). */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importPhotos(uris.take(remainingPhotos))
    }

    /** A kamera célfájlja: ide menti a fotót a kamera app. */
    fun newCameraTarget(): Uri {
        val (file, uri) = photoStorage.newCameraTarget()
        cameraFile = file
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val file = cameraFile ?: return
        cameraFile = null
        if (success && remainingPhotos > 0) importPhotos(listOf(Uri.fromFile(file)), cleanup = file)
        else file.delete()
    }

    private fun importPhotos(uris: List<Uri>, cleanup: File? = null) {
        if (uris.isEmpty()) return
        _state.update { it.copy(importingPhotos = true, photoError = false) }
        viewModelScope.launch {
            var failed = false
            for (uri in uris) {
                if (remainingPhotos == 0) break
                val name = photoStorage.import(uri)
                if (name == null) { failed = true; continue }
                pendingPhotos += name
                _state.update { it.copy(photos = it.photos + name) }
            }
            cleanup?.delete()
            _state.update { it.copy(importingPhotos = false, photoError = failed) }
        }
    }

    /** Eltávolítás a tételről. Az új (még nem mentett) fotó fájlja azonnal törlődik, a régié mentéskor. */
    fun removePhoto(name: String) {
        _state.update { it.copy(photos = it.photos - name) }
        if (pendingPhotos.remove(name)) viewModelScope.launch { photoStorage.delete(listOf(name)) }
    }

    fun makeCover(name: String) = _state.update { s -> s.copy(photos = listOf(name) + (s.photos - name)) }

    /** Fotó áthelyezése eggyel balra (-1) vagy jobbra (+1). */
    fun movePhoto(name: String, delta: Int) = _state.update { s ->
        val from = s.photos.indexOf(name)
        val to = from + delta
        if (from < 0 || to !in s.photos.indices) s
        else s.copy(photos = s.photos.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun dismissPhotoError() = _state.update { it.copy(photoError = false) }

    override fun onCleared() {
        // Mégse: az ebben a szerkesztésben beolvasott, el nem mentett fotók fájljai törlődnek.
        if (!_state.value.finished && pendingPhotos.isNotEmpty()) {
            val leftovers = pendingPhotos.map { photoStorage.file(it) }
            Thread { leftovers.forEach { it.delete() } }.start()
        }
        cameraFile?.delete()
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading || s.importingPhotos) return
        val quantityMode = s.trackingMode == TrackingMode.QUANTITY
        val value = NumberFormats.parseWhole(s.value.ifBlank { "0" })
        val quantity = NumberFormats.parseWhole(s.quantity)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        val minQuantity = if (s.isNew) 1 else 0

        val nameError = if (s.name.isBlank()) FixedError.NAME_REQUIRED else null
        val valueError = if (value == null) FixedError.VALUE_INVALID else null
        val quantityError = if (quantityMode && (quantity == null || quantity < minQuantity)) FixedError.QUANTITY_INVALID else null
        val fieldErrors = s.fields.mapNotNull { f -> FieldValues.validate(f, s.inputs[f.id] ?: FieldInput())?.let { f.id to it } }.toMap()
        if (nameError != null || valueError != null || quantityError != null || fieldErrors.isNotEmpty()) {
            _state.update { it.copy(nameError = nameError, valueError = valueError, quantityError = quantityError, fieldErrors = fieldErrors) }
            return
        }

        val entities = s.fields.associateWith { f -> FieldValues.toEntity(f, s.inputs[f.id] ?: FieldInput()) }
        val base = original ?: ItemEntity(categoryId = categoryId, name = "")
        val item = base.copy(
            name = s.name.trim(),
            value = value!!,
            // Egyedi módban a mennyiség nem látszik: a meglévő érték megmarad (TERV.md 3.2a).
            quantity = if (quantityMode) quantity!! else base.quantity,
            subcategoryId = s.subcategoryId?.takeIf { id -> s.subcategories.any { it.id == id } },
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            items.save(
                item,
                values = entities.values.filterNotNull(),
                clearedFieldIds = entities.filterValues { it == null }.keys.map { it.id },
                photoFileNames = s.photos,
            )
            _state.update { it.copy(saving = false, finished = true) }
        }
    }
}
