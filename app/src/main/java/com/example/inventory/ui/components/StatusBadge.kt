package com.example.inventory.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.ui.theme.InventoryTheme

@get:StringRes
val ItemStatus.label: Int
    get() = when (this) {
        ItemStatus.IN_STOCK -> R.string.status_in_stock
        ItemStatus.RESERVED -> R.string.status_reserved
        ItemStatus.SOLD -> R.string.status_sold
    }

/** A státusz színe (szöveg/pötty) és halvány háttere. */
@Composable
fun ItemStatus.colors(): Pair<Color, Color> {
    val c = InventoryTheme.colors
    return when (this) {
        ItemStatus.IN_STOCK -> c.inStock to c.inStockContainer
        ItemStatus.RESERVED -> c.reserved to c.reservedContainer
        ItemStatus.SOLD -> c.sold to c.soldContainer
    }
}

/** Kis színes címke a státusszal (lista, adatlap). */
@Composable
fun StatusBadge(status: ItemStatus, modifier: Modifier = Modifier) {
    val (fg, bg) = status.colors()
    Surface(shape = RoundedCornerShape(11.dp), color = bg, modifier = modifier) {
        Text(
            stringResource(status.label),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Halvány színes címke tetszőleges szöveggel (pl. kategória, alkategória az adatlapon). */
@Composable
fun TagBadge(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(11.dp), color = background, modifier = modifier) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
