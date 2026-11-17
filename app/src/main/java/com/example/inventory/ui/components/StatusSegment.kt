package com.example.inventory.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.domain.model.ItemStatus

/** A státuszok sorrendje a felületen: Készleten / Lefoglalva / Eladva (látványterv 3. és 7. képernyő). */
val StatusOrder = listOf(ItemStatus.IN_STOCK, ItemStatus.RESERVED, ItemStatus.SOLD)

/** A státusz rövid magyarázata a státusz panelen. */
@get:StringRes
val ItemStatus.description: Int
    get() = when (this) {
        ItemStatus.IN_STOCK -> R.string.status_in_stock_desc
        ItemStatus.RESERVED -> R.string.status_reserved_desc
        ItemStatus.SOLD -> R.string.status_sold_desc
    }

/** Színes pötty a státusz színével. */
@Composable
fun StatusDot(status: ItemStatus, size: Int = 8) {
    Box(Modifier.size(size.dp).background(status.colors().first, CircleShape))
}

/**
 * Háromállású státuszválasztó az adatlapon (TERV.md 3.6): egy koppintással vált.
 * A kiválasztott rész fehér „kártya”, a többi halvány.
 */
@Composable
fun StatusSegment(selected: ItemStatus, onSelect: (ItemStatus) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusOrder.forEach { status ->
                val active = status == selected
                Surface(
                    onClick = { onSelect(status) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (active) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    border = if (active) BorderStroke(1.dp, status.colors().first.copy(alpha = 0.5f)) else null,
                    shadowElevation = if (active) 1.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .semantics { role = Role.RadioButton; this.selected = active },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        StatusDot(status)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(status.label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
