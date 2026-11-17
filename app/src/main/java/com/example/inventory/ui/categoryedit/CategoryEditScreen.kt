package com.example.inventory.ui.categoryedit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.R
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.AppViewModelProvider
import com.example.inventory.ui.components.ColorPicker
import com.example.inventory.ui.components.IconPicker
import com.example.inventory.ui.components.rememberDiscardGuard
import com.example.inventory.ui.theme.InventoryTheme

/** Kategória létrehozása / szerkesztése (látványterv 5. képernyő). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    onDone: () -> Unit,
    viewModel: CategoryEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    val leave = rememberDiscardGuard(enabled = !state.finished, hasChanges = viewModel::hasChanges, onLeave = onDone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.cat_new_title else R.string.cat_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.loading) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                    }
                    if (!state.isNew) OverflowMenu(onArchive = viewModel::onArchiveRequest, onDelete = viewModel::onDeleteRequest)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.cat_name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.cat_name_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.cat_tracking))
            TrackingSelector(state.trackingMode, viewModel::onTrackingModeSelect)

            SectionTitle(stringResource(R.string.cat_color))
            ColorPicker(state.color, viewModel::onColorSelect)

            SectionTitle(stringResource(R.string.cat_icon))
            IconPicker(state.icon, Color(state.color), state.name, viewModel::onIconSelect)

            SectionTitle(stringResource(R.string.cat_subcategories))
            Subcategories(
                state.subcategories,
                onEdit = viewModel::onEditSubcategory,
                onRemove = viewModel::onRemoveSubcategory,
                onAdd = viewModel::onAddSubcategory,
            )

            SectionTitle(stringResource(R.string.cat_fields))
            FixedFields(quantityMode = state.trackingMode == TrackingMode.QUANTITY)
            CustomFieldList(
                fields = state.fields,
                onEdit = viewModel::onEditField,
                onMove = viewModel::onMoveField,
                onAdd = viewModel::onAddField,
            )
            Spacer(Modifier.size(24.dp))
        }
    }

    Dialogs(state, viewModel)
}

@Composable
private fun OverflowMenu(onArchive: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cat_archive)) },
                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                onClick = { open = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cat_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingSelector(selected: TrackingMode, onSelect: (TrackingMode) -> Unit) {
    val options = listOf(TrackingMode.UNIQUE to R.string.tracking_unique, TrackingMode.QUANTITY to R.string.tracking_quantity)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
    Text(
        stringResource(if (selected == TrackingMode.UNIQUE) R.string.tracking_unique_hint else R.string.tracking_quantity_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Subcategories(
    items: List<EditableSubcategory>,
    onEdit: (EditableSubcategory) -> Unit,
    onRemove: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            stringResource(R.string.cat_subcategories_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { sub ->
            InputChip(
                selected = true,
                onClick = { onEdit(sub) },
                label = { Text(sub.name) },
                trailingIcon = {
                    IconButton(onClick = { onRemove(sub.key) }, modifier = Modifier.size(InputChipDefaults.AvatarSize)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                    }
                },
                shape = RoundedCornerShape(17.dp),
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(stringResource(R.string.cat_subcategory_add)) },
            leadingIcon = { Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(17.dp),
        )
    }
}

/** A fix mezők (TERV.md 3.2) – nem szerkeszthetők. A saját mezők alattuk, külön listában vannak. */
@Composable
private fun FixedFields(quantityMode: Boolean) {
    val rows = buildList {
        add(R.string.field_name to R.string.field_type_text)
        add(R.string.field_value to R.string.field_type_number_huf)
        if (quantityMode) add(R.string.field_quantity to R.string.field_type_quantity)
        add(R.string.field_subcategory to R.string.field_type_list)
        add(R.string.field_photo to R.string.field_type_photo)
        add(R.string.field_status to R.string.field_type_status)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            rows.forEachIndexed { index, (name, type) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(11.dp), color = InventoryTheme.colors.soldContainer) {
                        Text(
                            stringResource(R.string.cat_fields_fixed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = InventoryTheme.colors.border)
            }
        }
    }
}

@Composable
private fun Dialogs(state: CategoryEditUiState, viewModel: CategoryEditViewModel) {
    val name = state.name.trim()
    when (val dialog = state.dialog) {
        null -> Unit
        is CategoryEditDialog.Field -> FieldEditorSheet(
            editing = dialog.editing,
            otherNames = state.fields.filterNot { it.key == dialog.editing?.key }.map { it.name },
            onSave = viewModel::onFieldSave,
            onDelete = viewModel::onFieldDelete,
            onDismiss = viewModel::dismissDialog,
        )
        is CategoryEditDialog.Subcategory -> SubcategoryNameDialog(
            initial = dialog.editing?.name.orEmpty(),
            isNew = dialog.editing == null,
            onConfirm = viewModel::onSubcategoryNameConfirm,
            onDismiss = viewModel::dismissDialog,
        )
        is CategoryEditDialog.ModeWarning -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.cat_mode_warning_title)) },
            text = { Text(pluralStringResource(R.plurals.cat_mode_warning_text, dialog.affectedItems, dialog.affectedItems)) },
            confirmButton = { TextButton(onClick = viewModel::confirmModeWarning) { Text(stringResource(R.string.cat_mode_warning_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.cancel)) } },
        )
        CategoryEditDialog.Archive -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            icon = { Icon(Icons.Outlined.Archive, null) },
            title = { Text(stringResource(R.string.cat_archive_title)) },
            text = { Text(stringResource(R.string.cat_archive_text, name)) },
            confirmButton = { TextButton(onClick = viewModel::confirmArchive) { Text(stringResource(R.string.cat_archive)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.cancel)) } },
        )
        is CategoryEditDialog.Delete -> DeleteCategoryDialog(
            name = name,
            itemCount = dialog.itemCount,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/** Végleges törlés megerősítése (a szerkesztőből és az archivált kategóriák közül is). */
@Composable
fun DeleteCategoryDialog(name: String, itemCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.cat_delete_title)) },
        text = {
            Text(
                if (itemCount == 0) stringResource(R.string.cat_delete_text_empty, name)
                else pluralStringResource(R.plurals.cat_delete_text, itemCount, name, itemCount)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SubcategoryNameDialog(initial: String, isNew: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.subcategory_new_title else R.string.subcategory_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.subcategory_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
