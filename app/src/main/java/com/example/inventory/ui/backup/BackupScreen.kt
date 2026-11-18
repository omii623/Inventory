package com.example.inventory.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.R
import com.example.inventory.data.backup.BackupFormatException
import com.example.inventory.data.backup.CsvLabels
import com.example.inventory.ui.AppViewModelProvider
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Adatmentés (TERV.md 3.11): mentés zipbe (adatok + fotók), visszaállítás, CSV export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val locale = LocalConfiguration.current.locales[0]
    val labels = csvLabels(decimalComma = DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',')

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportCsv(it, labels) }
    }
    // Néhány fájlkezelő a .zip-et nem application/zip-ként adja → minden fájl választható, a tartalmat ellenőrizzük.
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickRestoreFile)
    }

    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let {
        stringResource(
            when (it) {
                BackupMessage.BACKUP_SAVED -> R.string.bk_msg_saved
                BackupMessage.CSV_SAVED -> R.string.bk_msg_csv_saved
                BackupMessage.RESTORED -> R.string.bk_msg_restored
                BackupMessage.WRITE_FAILED -> R.string.bk_msg_write_failed
            }
        )
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bk_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                icon = Icons.Outlined.Save,
                title = stringResource(R.string.bk_save_title),
                text = stringResource(R.string.bk_save_text),
                button = stringResource(R.string.bk_save_button),
                enabled = !state.working,
                onClick = { exportBackup.launch("inventory-backup-$today.zip") },
            )
            ActionCard(
                icon = Icons.Outlined.Restore,
                title = stringResource(R.string.bk_restore_title),
                text = stringResource(R.string.bk_restore_text),
                button = stringResource(R.string.bk_restore_button),
                enabled = !state.working,
                danger = true,
                onClick = { pickBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) },
            )
            ActionCard(
                icon = Icons.Outlined.TableChart,
                title = stringResource(R.string.bk_csv_title),
                text = stringResource(R.string.bk_csv_text),
                button = stringResource(R.string.bk_csv_button),
                enabled = !state.working,
                onClick = { exportCsv.launch("inventory-csv-$today.zip") },
            )
            if (state.working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.bk_working), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    state.pendingRestore?.let { s ->
        val time = Instant.ofEpochMilli(s.exportedAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            icon = { Icon(Icons.Outlined.Restore, null) },
            title = { Text(stringResource(R.string.bk_confirm_title)) },
            text = { Text(stringResource(R.string.bk_confirm_text, time, s.categories, s.items, s.photos)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.bk_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.error?.let { e ->
        AlertDialog(
            onDismissRequest = viewModel::errorShown,
            title = { Text(stringResource(R.string.bk_error_title)) },
            text = { Text(stringResource(R.string.bk_error_text, errorReason(e))) },
            confirmButton = { TextButton(onClick = viewModel::errorShown) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun csvLabels(decimalComma: Boolean) = CsvLabels(
    name = stringResource(R.string.field_name),
    subcategory = stringResource(R.string.field_subcategory),
    other = stringResource(R.string.subcategory_other),
    status = stringResource(R.string.field_status),
    value = stringResource(R.string.bk_csv_value),
    unitValue = stringResource(R.string.bk_csv_unit_value),
    quantity = stringResource(R.string.field_quantity),
    createdAt = stringResource(R.string.bk_csv_created),
    inStock = stringResource(R.string.status_in_stock),
    reserved = stringResource(R.string.status_reserved),
    sold = stringResource(R.string.status_sold),
    yes = stringResource(R.string.yes),
    no = stringResource(R.string.no),
    decimalComma = decimalComma,
)

@Composable
private fun errorReason(e: BackupError): String = stringResource(
    when (e) {
        BackupError.Unknown -> R.string.bk_err_unknown
        is BackupError.Format -> when (e.reason) {
            BackupFormatException.Reason.NOT_ZIP -> R.string.bk_err_not_zip
            BackupFormatException.Reason.NOT_JSON -> R.string.bk_err_corrupt
            BackupFormatException.Reason.NOT_INVENTORY -> R.string.bk_err_not_inventory
            BackupFormatException.Reason.UNKNOWN_FORMAT -> R.string.bk_err_unknown_format
            BackupFormatException.Reason.NEWER_VERSION -> R.string.bk_err_newer_version
            BackupFormatException.Reason.CORRUPT -> R.string.bk_err_corrupt
            BackupFormatException.Reason.UNREADABLE -> R.string.bk_err_unreadable
            BackupFormatException.Reason.BROKEN_REFERENCE -> R.string.bk_err_broken_reference
        }
    }
)

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    text: String,
    button: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (danger) {
                OutlinedButton(
                    onClick = onClick,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(button) }
            } else {
                Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(button) }
            }
        }
    }
}
