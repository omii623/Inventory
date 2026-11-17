package com.example.inventory.ui.itemdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.R
import com.example.inventory.domain.model.FieldType
import com.example.inventory.ui.AppViewModelProvider
import com.example.inventory.ui.components.StatusSegment
import com.example.inventory.ui.components.TagBadge
import com.example.inventory.ui.theme.InventoryTheme
import com.example.inventory.util.NumberFormats
import com.example.inventory.util.UrlUtils
import com.example.inventory.util.rememberMoneyFormat

/** Tétel adatlapja (látványterv 3. képernyő): fotók, adatok, státuszváltó, tulajdonságok, szerkesztés. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onEdit: (itemId: Long, categoryId: Long) -> Unit,
    viewModel: ItemDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val money = rememberMoneyFormat()
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    val item = state.item ?: return
    val category = state.category ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { onEdit(item.id, item.categoryId) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.item_edit_title)) }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_sheet_title)) },
                                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                                onClick = { menu = false; viewModel.openStatusSheet() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.item_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; viewModel.requestDelete() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            PhotoPager(state.photos)
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val color = Color(category.color)
                    TagBadge(category.name, color, color.copy(alpha = 0.14f))
                    TagBadge(
                        state.subcategoryName ?: stringResource(R.string.subcategory_other),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        InventoryTheme.colors.soldContainer,
                    )
                }
                Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                if (state.quantityMode) {
                    Text(
                        money(item.value * item.quantity),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.item_unit_price_line, money(item.value)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusSegment(item.status, onSelect = viewModel::setStatus)
                    QuantityStepper(item.quantity, onChange = viewModel::changeQuantity)
                } else {
                    Text(
                        money(item.value),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    StatusSegment(item.status, onSelect = viewModel::setStatus)
                }

                if (state.properties.isNotEmpty()) {
                    Text(
                        stringResource(R.string.item_properties),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    PropertiesCard(state.properties)
                }

                Button(
                    onClick = { onEdit(item.id, item.categoryId) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp),
                ) { Text(stringResource(R.string.item_edit_title), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    when (dialog) {
        null -> Unit
        ItemDetailDialog.Status -> StatusSheet(
            itemName = item.name,
            current = item.status,
            onSave = viewModel::setStatus,
            onDismiss = viewModel::dismissDialog,
        )
        ItemDetailDialog.Delete -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.item_delete_title)) },
            text = { Text(stringResource(R.string.item_delete_text, item.name)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.cancel)) } },
        )
        ItemDetailDialog.ZeroQuantity -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.item_zero_title)) },
            text = { Text(stringResource(R.string.item_zero_text)) },
            confirmButton = { TextButton(onClick = viewModel::markSold) { Text(stringResource(R.string.item_zero_mark_sold)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.item_zero_keep)) } },
        )
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.field_quantity), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onChange(-1) }, enabled = quantity > 0) {
                Icon(Icons.Filled.Remove, stringResource(R.string.item_quantity_decrease))
            }
            Text(
                pluralStringResource(R.plurals.pieces, quantity, quantity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            FilledTonalIconButton(onClick = { onChange(1) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.item_quantity_increase))
            }
        }
    }
}

@Composable
private fun PropertiesCard(properties: List<Property>) {
    val locale = LocalConfiguration.current.locales[0]
    val uriHandler = LocalUriHandler.current
    val empty = "—"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            properties.forEachIndexed { index, (field, value) ->
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                when (field.type) {
                    // Hosszú szöveg: a címke alatt, teljes szélességben (TERV.md 3.4).
                    FieldType.LONG_TEXT -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(field.name, style = MaterialTheme.typography.bodyMedium, color = muted)
                        Spacer(Modifier.height(4.dp))
                        Text(value?.textValue ?: empty, style = MaterialTheme.typography.bodyMedium, color = if (value == null) muted else MaterialTheme.colorScheme.onSurface)
                    }
                    FieldType.LINK -> {
                        val url = value?.textValue
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (url != null) Modifier.clickable { runCatching { uriHandler.openUri(url) } } else Modifier)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(field.name, style = MaterialTheme.typography.bodyMedium, color = muted)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                url?.let(UrlUtils::display) ?: empty,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (url != null) MaterialTheme.colorScheme.primary else muted,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (url != null) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.item_open_link), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    else -> {
                        val text = when (field.type) {
                            FieldType.NUMBER -> value?.numberValue?.let { n ->
                                NumberFormats.display(n, locale) + (field.unit?.let { " $it" } ?: "")
                            }
                            FieldType.BOOLEAN -> value?.boolValue?.let { stringResource(if (it) R.string.yes else R.string.no) }
                            FieldType.DATE -> value?.dateValue?.let { NumberFormats.displayDate(it, locale) }
                            else -> value?.textValue
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(field.name, style = MaterialTheme.typography.bodyMedium, color = muted)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text ?: empty,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (text != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (text != null) MaterialTheme.colorScheme.onSurface else muted,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (index < properties.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = InventoryTheme.colors.border)
            }
        }
    }
}
