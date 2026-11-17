package com.example.inventory.ui.itemedit

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.domain.form.FieldError
import com.example.inventory.domain.form.FieldInput
import com.example.inventory.domain.model.FieldType
import com.example.inventory.util.NumberFormats

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Egy saját mező vezérlője a típusa alapján (TERV.md 7. pont, 3.4). */
@Composable
fun DynamicField(field: FieldDefinitionEntity, input: FieldInput, error: FieldError?, onChange: (FieldInput) -> Unit) {
    val label = field.name + if (field.required) " *" else ""
    val supporting: (@Composable () -> Unit)? = error?.let { e -> { Text(stringResource(e.message())) } }
    when (field.type) {
        FieldType.TEXT -> OutlinedTextField(
            value = input.text,
            onValueChange = { onChange(input.copy(text = it)) },
            label = { Text(label) },
            isError = error != null,
            supportingText = supporting,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.LONG_TEXT -> OutlinedTextField(
            value = input.text,
            onValueChange = { onChange(input.copy(text = it)) },
            label = { Text(label) },
            isError = error != null,
            supportingText = supporting,
            minLines = 3,
            maxLines = 10,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.NUMBER -> OutlinedTextField(
            value = input.text,
            onValueChange = { text -> onChange(input.copy(text = text.filter { it.isDigit() || it in ",.- " })) },
            label = { Text(label) },
            suffix = field.unit?.let { u -> { Text(u) } },
            isError = error != null,
            supportingText = supporting,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.LINK -> OutlinedTextField(
            value = input.text,
            onValueChange = { onChange(input.copy(text = it.trim())) },
            label = { Text(label) },
            placeholder = { Text("https://…") },
            leadingIcon = { Icon(Icons.Filled.Link, null) },
            isError = error != null,
            supportingText = supporting,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.SELECT -> {
            // Ha egy opciót azóta töröltek a kategóriából, a tétel régi értéke is választható marad.
            val options = if (input.text.isNotBlank() && input.text !in field.options) listOf(input.text) + field.options else field.options
            SelectField(
                label = label,
                selected = input.text.ifBlank { null },
                options = options.map { it to it },
                noneLabel = stringResource(R.string.form_none),
                onSelect = { onChange(input.copy(text = it.orEmpty())) },
                isError = error != null,
                supporting = supporting,
            )
        }
        FieldType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (input.bool) R.string.yes else R.string.no),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = input.bool, onCheckedChange = { onChange(input.copy(bool = it)) })
        }
        FieldType.DATE -> DateField(label, input.date, error != null, supporting, onSelect = { onChange(input.copy(date = it)) })
    }
}

private fun FieldError.message(): Int = when (this) {
    FieldError.REQUIRED -> R.string.form_error_required
    FieldError.INVALID_NUMBER -> R.string.form_error_number
    FieldError.INVALID_LINK -> R.string.form_error_link
}

/** Csak olvasható mező, ami koppintásra [onClick]-et hív (legördülőhöz, dátumválasztóhoz). */
@Composable
fun ClickableField(
    value: String,
    label: String,
    onClick: () -> Unit,
    trailingIcon: @Composable () -> Unit,
    isError: Boolean = false,
    supporting: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) currentOnClick() }
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = placeholder?.let { p -> { Text(p) } },
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supporting,
        singleLine = true,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Legördülő választó. [options]: (érték, felirat) párok; a „nincs” elem null értéket ad. */
@Composable
fun <T> SelectField(
    label: String,
    selected: T?,
    options: List<Pair<T, String>>,
    noneLabel: String?,
    onSelect: (T?) -> Unit,
    isError: Boolean = false,
    supporting: (@Composable () -> Unit)? = null,
    selectedLabel: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ClickableField(
            value = selectedLabel ?: options.firstOrNull { it.first == selected }?.second.orEmpty(),
            label = label,
            onClick = { open = true },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            isError = isError,
            supporting = supporting,
            placeholder = noneLabel,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { open = false; onSelect(null) })
            }
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochDay: Long?, isError: Boolean = false, supporting: (@Composable () -> Unit)? = null, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    ClickableField(
        value = epochDay?.let { NumberFormats.displayDate(it, locale) }.orEmpty(),
        label = label,
        onClick = { open = true },
        trailingIcon = {
            if (epochDay != null) {
                IconButton(onClick = { onSelect(null) }) { Icon(Icons.Filled.Clear, stringResource(R.string.form_clear)) }
            } else {
                Icon(Icons.Filled.CalendarMonth, null)
            }
        },
        isError = isError,
        supporting = supporting,
    )
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochDay?.let { it * DAY_MS })
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onSelect(Math.floorDiv(it, DAY_MS)) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}
