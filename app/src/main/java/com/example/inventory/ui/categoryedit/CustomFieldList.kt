package com.example.inventory.ui.categoryedit

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.inventory.R
import com.example.inventory.domain.model.FieldType
import com.example.inventory.ui.theme.InventoryTheme

/** A mezőtípusok felirata és ikonja (a típusválasztóban és a mezőlistában). */
enum class FieldTypeUi(val type: FieldType, @StringRes val label: Int, val icon: ImageVector) {
    TEXT(FieldType.TEXT, R.string.field_type_name_text, Icons.Filled.TextFields),
    LONG_TEXT(FieldType.LONG_TEXT, R.string.field_type_name_long_text, Icons.AutoMirrored.Filled.Notes),
    NUMBER(FieldType.NUMBER, R.string.field_type_name_number, Icons.Filled.Numbers),
    SELECT(FieldType.SELECT, R.string.field_type_name_select, Icons.AutoMirrored.Filled.List),
    BOOLEAN(FieldType.BOOLEAN, R.string.field_type_name_boolean, Icons.Filled.ToggleOn),
    DATE(FieldType.DATE, R.string.field_type_name_date, Icons.Filled.CalendarMonth),
    LINK(FieldType.LINK, R.string.field_type_name_link, Icons.Filled.Link);

    companion object {
        fun of(type: FieldType): FieldTypeUi = entries.first { it.type == type }
    }
}

/** A mező második sora a listában, pl. „Szám • GB”, „Lista • 5 opció”. */
@Composable
fun fieldSummary(field: EditableField): String {
    val type = stringResource(FieldTypeUi.of(field.type).label)
    val extra = when (field.type) {
        FieldType.NUMBER -> field.unit?.takeIf { it.isNotBlank() }
        FieldType.SELECT -> pluralStringResource(R.plurals.field_option_count, field.options.size, field.options.size)
        else -> null
    }
    return if (extra != null) "$type • $extra" else type
}

/**
 * A saját mezők listája. A sor bal szélén lévő fogantyúval húzva átrendezhető,
 * a sorra koppintva a mezőszerkesztő nyílik meg.
 */
@Composable
fun CustomFieldList(
    fields: List<EditableField>,
    onEdit: (EditableField) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAdd: () -> Unit,
) {
    if (fields.isNotEmpty()) {
        var draggingKey by remember { mutableStateOf<Long?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val heights = remember { mutableStateMapOf<Long, Int>() }
        val currentFields by rememberUpdatedState(fields)
        val currentOnMove by rememberUpdatedState(onMove)
        val rowColor = MaterialTheme.colorScheme.surfaceContainer

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = rowColor,
            border = BorderStroke(1.dp, InventoryTheme.colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                fields.forEachIndexed { index, field ->
                    key(field.key) {
                        val dragging = draggingKey == field.key
                        Column(
                            Modifier
                                .onSizeChanged { heights[field.key] = it.height }
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (dragging) dragOffset else 0f
                                    shadowElevation = if (dragging) 8.dp.toPx() else 0f
                                }
                                .background(rowColor)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onEdit(field) }.padding(end = 12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.DragIndicator,
                                    contentDescription = stringResource(R.string.field_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(40.dp)
                                        .padding(8.dp)
                                        .pointerInput(field.key) {
                                            detectDragGestures(
                                                onDragStart = { draggingKey = field.key; dragOffset = 0f },
                                                onDragEnd = { draggingKey = null; dragOffset = 0f },
                                                onDragCancel = { draggingKey = null; dragOffset = 0f },
                                            ) { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val list = currentFields
                                                val i = list.indexOfFirst { it.key == field.key }
                                                if (dragOffset > 0 && i < list.lastIndex) {
                                                    val next = heights[list[i + 1].key] ?: return@detectDragGestures
                                                    if (dragOffset > next / 2f) { currentOnMove(i, i + 1); dragOffset -= next }
                                                } else if (dragOffset < 0 && i > 0) {
                                                    val prev = heights[list[i - 1].key] ?: return@detectDragGestures
                                                    if (-dragOffset > prev / 2f) { currentOnMove(i, i - 1); dragOffset += prev }
                                                }
                                            }
                                        },
                                )
                                Icon(
                                    FieldTypeUi.of(field.type).icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text(
                                        field.name + if (field.required) " *" else "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(fieldSummary(field), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (index < fields.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = InventoryTheme.colors.border)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.field_drag_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(
        onClick = onAdd,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.field_add))
    }
}
