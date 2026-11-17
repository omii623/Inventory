package com.example.inventory.ui.itemlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.domain.filter.FieldFilter
import com.example.inventory.domain.filter.ItemFilter
import com.example.inventory.domain.model.FieldType
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.ui.components.label
import com.example.inventory.ui.itemedit.DateField
import com.example.inventory.util.MoneyFormatter
import com.example.inventory.util.NumberFormats

/**
 * Szűrőpanel (TERV.md 3.7a): a kategória fix és saját mezőiből épül fel. A szűrők ÉS kapcsolatban vannak,
 * egy mezőn belül a kiválasztott opciók VAGY kapcsolatban. Az alsó gomb élőben mutatja a találatok számát.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    sheet: FilterSheetState,
    fields: List<FieldDefinitionEntity>,
    quantityMode: Boolean,
    onChange: ((ItemFilter) -> ItemFilter) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = sheet.draft
    val locale = LocalConfiguration.current.locales[0]
    // A „Szűrők törlése” után a mezők helyi szövegállapota is újrainduljon.
    var generation by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            key(generation) { Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                SectionLabel(stringResource(R.string.field_status))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ItemStatus.RESERVED, ItemStatus.IN_STOCK, ItemStatus.SOLD).forEach { status ->
                        FilterChip(
                            selected = status in draft.statuses,
                            onClick = {
                                onChange { f -> f.copy(statuses = if (status in f.statuses) f.statuses - status else f.statuses + status) }
                            },
                            label = { Text(stringResource(status.label)) },
                        )
                    }
                }

                SectionLabel(stringResource(if (quantityMode) R.string.item_unit_value else R.string.field_value))
                WholeRange(
                    min = draft.valueMin,
                    max = draft.valueMax,
                    suffix = MoneyFormatter.CURRENCY,
                    onChange = { min, max -> onChange { it.copy(valueMin = min, valueMax = max) } },
                )
                if (quantityMode) {
                    SectionLabel(stringResource(R.string.field_quantity))
                    WholeRange(
                        min = draft.quantityMin,
                        max = draft.quantityMax,
                        suffix = stringResource(R.string.unit_pcs),
                        onChange = { min, max -> onChange { it.copy(quantityMin = min, quantityMax = max) } },
                    )
                }

                fields.forEach { field ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SectionLabel(field.name)
                    FieldFilterControl(field, draft.fields[field.id], locale) { newFilter ->
                        onChange { f -> f.copy(fields = if (newFilter == null || !newFilter.isActive) f.fields - field.id else f.fields + (field.id to newFilter)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                TextButton(onClick = { generation++; onClear() }, enabled = draft.activeCount > 0) { Text(stringResource(R.string.filter_clear)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = onApply, shape = RoundedCornerShape(14.dp)) {
                    val count = sheet.count
                    Text(
                        if (count == null) stringResource(R.string.filter_show)
                        else stringResource(R.string.filter_show_count, pluralStringResource(R.plurals.item_count, count, count))
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FieldFilterControl(field: FieldDefinitionEntity, current: FieldFilter?, locale: java.util.Locale, onChange: (FieldFilter?) -> Unit) {
    when (field.type) {
        FieldType.NUMBER -> {
            val range = current as? FieldFilter.NumberRange
            DecimalRange(range?.min, range?.max, field.unit, locale) { min, max -> onChange(FieldFilter.NumberRange(min, max)) }
        }
        FieldType.SELECT -> {
            val selected = (current as? FieldFilter.Options)?.values.orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    FilterChip(
                        selected = option in selected,
                        onClick = { onChange(FieldFilter.Options(if (option in selected) selected - option else selected + option)) },
                        label = { Text(option) },
                    )
                }
            }
        }
        FieldType.BOOLEAN -> {
            val value = (current as? FieldFilter.Bool)?.value
            val options = listOf(null to R.string.filter_any, true to R.string.yes, false to R.string.no)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (v, label) ->
                    SegmentedButton(
                        selected = value == v,
                        onClick = { onChange(v?.let { FieldFilter.Bool(it) }) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(stringResource(label)) }
                }
            }
        }
        FieldType.DATE -> {
            val range = current as? FieldFilter.DateRange
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    DateField(stringResource(R.string.filter_from), range?.from, onSelect = { onChange(FieldFilter.DateRange(it, range?.to)) })
                }
                Column(Modifier.weight(1f)) {
                    DateField(stringResource(R.string.filter_to), range?.to, onSelect = { onChange(FieldFilter.DateRange(range?.from, it)) })
                }
            }
        }
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.LINK -> {
            var text by remember(field.id) { mutableStateOf((current as? FieldFilter.Contains)?.text.orEmpty()) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onChange(FieldFilter.Contains(it)) },
                label = { Text(stringResource(R.string.filter_contains)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Egész tól–ig (Ft, darab). Érvénytelen vagy üres mező = nincs határ. */
@Composable
private fun WholeRange(min: Long?, max: Long?, suffix: String, onChange: (Long?, Long?) -> Unit) {
    var minText by remember { mutableStateOf(min?.toString().orEmpty()) }
    var maxText by remember { mutableStateOf(max?.toString().orEmpty()) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = minText,
            onValueChange = { t -> minText = t.filter(Char::isDigit); onChange(NumberFormats.parseWhole(minText), NumberFormats.parseWhole(maxText)) },
            label = { Text(stringResource(R.string.filter_from)) },
            suffix = { Text(suffix) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = maxText,
            onValueChange = { t -> maxText = t.filter(Char::isDigit); onChange(NumberFormats.parseWhole(minText), NumberFormats.parseWhole(maxText)) },
            label = { Text(stringResource(R.string.filter_to)) },
            suffix = { Text(suffix) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Tizedes tól–ig (Szám mező) a mértékegységgel. */
@Composable
private fun DecimalRange(min: Double?, max: Double?, unit: String?, locale: java.util.Locale, onChange: (Double?, Double?) -> Unit) {
    var minText by remember { mutableStateOf(min?.let { NumberFormats.toInput(it, locale) }.orEmpty()) }
    var maxText by remember { mutableStateOf(max?.let { NumberFormats.toInput(it, locale) }.orEmpty()) }
    val clean = { t: String -> t.filter { it.isDigit() || it in ",.-" } }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = minText,
            onValueChange = { t -> minText = clean(t); onChange(NumberFormats.parseDecimal(minText), NumberFormats.parseDecimal(maxText)) },
            label = { Text(stringResource(R.string.filter_from)) },
            suffix = unit?.let { u -> { Text(u) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = maxText,
            onValueChange = { t -> maxText = clean(t); onChange(NumberFormats.parseDecimal(minText), NumberFormats.parseDecimal(maxText)) },
            label = { Text(stringResource(R.string.filter_to)) },
            suffix = unit?.let { u -> { Text(u) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}
