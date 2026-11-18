package com.example.inventory.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.backup.BackupFormatException
import com.example.inventory.data.backup.BackupManager
import com.example.inventory.data.backup.CsvLabels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupMessage { BACKUP_SAVED, CSV_SAVED, RESTORED, WRITE_FAILED }

sealed interface BackupError {
    data class Format(val reason: BackupFormatException.Reason) : BackupError
    data object Unknown : BackupError
}

data class BackupUiState(
    val working: Boolean = false,
    /** Beolvasott, jóváhagyásra váró mentés összesítője. */
    val pendingRestore: BackupManager.Summary? = null,
    val message: BackupMessage? = null,
    /** Beolvasási / visszaállítási hiba; a jelenlegi adatok ilyenkor nem változnak. */
    val error: BackupError? = null,
)

/** Adatmentés képernyő (TERV.md 3.11, a cashApp mintájára). */
class BackupViewModel(
    private val backups: BackupManager,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var pending: BackupManager.Pending? = null

    fun export(uri: Uri) = write(uri, BackupMessage.BACKUP_SAVED) { out -> backups.export(out) }

    fun exportCsv(uri: Uri, labels: CsvLabels) = write(uri, BackupMessage.CSV_SAVED) { out -> backups.exportCsv(out, labels) }

    /** 1. lépés: a fájl beolvasása és ellenőrzése, majd megerősítés kérése. */
    fun pickRestoreFile(uri: Uri) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val input = resolver.openInputStream(uri) ?: throw BackupFormatException(BackupFormatException.Reason.UNREADABLE)
                input.use { backups.read(it) }
            }.onSuccess { p ->
                pending?.discard()
                pending = p
                _state.update { it.copy(working = false, pendingRestore = p.summary) }
            }.onFailure { e -> _state.update { it.copy(working = false, error = e.toError()) } }
        }
    }

    /** 2. lépés: megerősítés után a jelenlegi adatok cseréje. */
    fun confirmRestore() {
        val p = pending ?: return
        pending = null
        _state.update { it.copy(pendingRestore = null, working = true) }
        viewModelScope.launch {
            runCatching { backups.restore(p) }
                .onSuccess { _state.update { it.copy(working = false, message = BackupMessage.RESTORED) } }
                .onFailure { e -> _state.update { it.copy(working = false, error = e.toError()) } }
        }
    }

    fun cancelRestore() {
        pending?.discard()
        pending = null
        _state.update { it.copy(pendingRestore = null) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun errorShown() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        pending?.discard()
    }

    private fun write(uri: Uri, success: BackupMessage, block: suspend (java.io.OutputStream) -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val ok = runCatching {
                val out = resolver.openOutputStream(uri, "wt") ?: error("A fájl nem írható")
                out.use { block(it) }
            }.isSuccess
            _state.update { it.copy(working = false, message = if (ok) success else BackupMessage.WRITE_FAILED) }
        }
    }

    private fun Throwable.toError(): BackupError = (this as? BackupFormatException)?.let { BackupError.Format(it.reason) } ?: BackupError.Unknown
}
