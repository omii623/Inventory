package com.example.inventory.ui.categoryedit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.domain.model.FieldType
import com.example.inventory.ui.theme.InventoryTheme

/**
 * Új mező / mező szerkesztése (látványterv 6. képernyő): név, típus, mértékegység vagy opciók, kötelező-e.
 * - Kép típus nincs, mert a Fénykép fix mező (TERV.md 3.4).
 * - Mentett mező típusa nem változtatható (a régi értékek a típushoz tartozó oszlopban vannak).
 *
 * @param otherNames a kategória többi mezőjének neve (ismétlődés ellenőrzéséhez)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditorSheet(
    editing: EditableField?,
    otherNames: List<String>,
    onSave: (EditableField) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val typeLocked = editing?.id != null

    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var type by remember { mutableStateOf(editing?.type ?: FieldType.TEXT) }
    var required by remember { mutableStateOf(editing?.required ?: false) }
    var unit by remember { mutableStateOf(editing?.unit.orEmpty()) }
    val options = remember { mutableStateListOf<String>().apply { addAll(editing?.options.orEmpty()) } }
    var newOption by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<Int?>(null) }
    var optionsError by remember { mutableStateOf(false) }

    fun addOption() {
        val text = newOption.trim()
        if (text.isNotEmpty() && options.none { it.equals(text, ignoreCase = true) }) options.add(text)
        newOption = ""
        optionsError = false
    }

    fun save() {
        addOption()
        val trimmed = name.trim()
        nameError = when {
            trimmed.isEmpty() -> R.string.field_name_error_empty
            otherNames.any { it.equals(trimmed, ignoreCase = true) } -> R.string.field_name_error_duplicate
            else -> null
        }
        optionsError = type == FieldType.SELECT && options.isEmpty()
        if (nameError != null || optionsError) return
        onSave(
            EditableField(
                key = editing?.key ?: 0,
                id = editing?.id,
                name = trimmed,
                type = type,
                required = required,
                unit = unit.trim().takeIf { type == FieldType.NUMBER && it.isNotEmpty() },
                options = if (type == FieldType.SELECT) options.toList() else emptyList(),
            )
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editing == null) R.string.field_new_title else R.string.field_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text(stringResource(R.string.field_name_label)) },
                placeholder = { Text(stringResource(R.string.field_name_hint)) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.field_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TypeGrid(selected = type, locked = typeLocked, onSelect = { type = it; optionsError = false })
            Note(stringResource(if (typeLocked) R.string.field_type_locked else R.string.field_photo_note))

            when (type) {
                FieldType.NUMBER -> OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.take(12) },
                    label = { Text(stringResource(R.string.field_unit)) },
                    placeholder = { Text(stringResource(R.string.field_unit_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldType.SELECT -> OptionsEditor(
                    options = options,
                    newOption = newOption,
                    onNewOptionChange = { newOption = it },
                    onAdd = ::addOption,
                    onRemove = { options.removeAt(it) },
                    error = optionsError,
                )
                else -> Unit
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.field_required), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.field_required_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = required, onCheckedChange = { required = it })
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)) {
                if (editing != null) {
                    TextButton(onClick = { onDelete(editing.key) }) {
                        Text(stringResource(R.string.field_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(if (editing == null) R.string.field_add else R.string.field_done))
                }
            }
            if (editing?.id != null) Note(stringResource(R.string.field_delete_note))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TypeGrid(selected: FieldType, locked: Boolean, onSelect: (FieldType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTypeUi.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val isSelected = option.type == selected
                    val enabled = !locked || isSelected
                    Surface(
                        onClick = { onSelect(option.type) },
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else InventoryTheme.colors.border,
                        ),
                        modifier = Modifier.weight(1f).height(72.dp),
                    ) {
                        val tint = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            enabled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(option.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(option.label),
                                color = tint,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OptionsEditor(
    options: List<String>,
    newOption: String,
    onNewOptionChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    error: Boolean,
) {
    Text(stringResource(R.string.field_options), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (options.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, InventoryTheme.colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                        Text(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.delete), modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < options.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = InventoryTheme.colors.border)
                }
            }
        }
    }
    OutlinedTextField(
        value = newOption,
        onValueChange = onNewOptionChange,
        label = { Text(stringResource(R.string.field_option_new)) },
        isError = error,
        supportingText = if (error) ({ Text(stringResource(R.string.field_options_error)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAdd() }),
        trailingIcon = {
            IconButton(onClick = onAdd, enabled = newOption.isNotBlank()) { Icon(Icons.Filled.Add, stringResource(R.string.field_option_add)) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Note(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = InventoryTheme.colors.soldContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
