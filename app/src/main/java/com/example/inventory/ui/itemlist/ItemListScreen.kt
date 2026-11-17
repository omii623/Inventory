package com.example.inventory.ui.itemlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.R
import com.example.inventory.data.db.model.ItemListRow
import com.example.inventory.domain.filter.FieldFilter
import com.example.inventory.domain.filter.SubcategoryChoice
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.AppViewModelProvider
import com.example.inventory.ui.components.EmptyState
import com.example.inventory.ui.components.PhotoImage
import com.example.inventory.ui.components.StatusBadge
import com.example.inventory.ui.components.label
import com.example.inventory.ui.theme.InventoryTheme
import com.example.inventory.util.NumberFormats
import com.example.inventory.util.rememberMoneyFormat

/** Tétellista (látványterv 2. képernyő): kereső, szűrők, alkategória-címkék, rendezett lista, új tétel gomb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onBack: () -> Unit,
    onEditCategory: (Long) -> Unit,
    onNewItem: (Long) -> Unit,
    onOpenItem: (Long) -> Unit,
    viewModel: ItemListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val money = rememberMoneyFormat()
    // Ha a kategóriát a szerkesztőben törölték vagy archiválták, ez a lista bezárul.
    LaunchedEffect(state.loading, state.summary) { if (!state.loading && state.summary == null) onBack() }
    val summary = state.summary ?: return
    val quantityMode = summary.category.trackingMode == TrackingMode.QUANTITY
    val filter = state.filter
    val activeLabels = activeFilterLabels(state, quantityMode, money)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(summary.category.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            pluralStringResource(R.plurals.item_count, summary.itemCount.toInt(), summary.itemCount.toInt()) +
                                " • " + money(summary.totalValue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { onEditCategory(summary.category.id) }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.cat_edit_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNewItem(summary.category.id) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, stringResource(R.string.item_new))
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(
                        value = filter.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.items_search_hint, summary.category.name)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = if (filter.query.isNotEmpty()) {
                            { IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.form_clear)) } }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    Surface(
                        onClick = viewModel::openSheet,
                        shape = RoundedCornerShape(14.dp),
                        color = if (filter.activeCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, InventoryTheme.colors.border),
                        modifier = Modifier.size(56.dp),
                    ) {
                        BadgedBox(
                            badge = { if (filter.activeCount > 0) Badge { Text(filter.activeCount.toString()) } },
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Icon(Icons.Outlined.FilterList, stringResource(R.string.filter_title))
                        }
                    }
                }
            }
            if (state.subcategories.isNotEmpty()) {
                item { SubcategoryChips(state, onSelect = viewModel::onSubcategorySelect) }
            }
            if (activeLabels.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                        items(activeLabels, key = { it.first.toString() }) { (key, text) ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeFilter(key) },
                                label = { Text(text, maxLines = 1) },
                                trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.filter_remove), Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }

            if (state.rows.isEmpty()) {
                item {
                    val filtering = filter.query.isNotBlank() || filter.activeCount > 0 || filter.subcategory != SubcategoryChoice.All
                    if (filtering) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                            EmptyState(
                                icon = Icons.Outlined.SearchOff,
                                title = stringResource(R.string.items_no_match_title),
                                text = stringResource(R.string.items_no_match_text),
                                modifier = Modifier.height(260.dp),
                            )
                            OutlinedButton(onClick = viewModel::clearAllFilters) { Text(stringResource(R.string.filter_clear_all)) }
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Inventory2,
                            title = stringResource(R.string.items_empty_title),
                            text = stringResource(R.string.items_empty_text),
                            modifier = Modifier.height(360.dp),
                        )
                    }
                }
            }
            items(state.rows, key = { it.item.id }) { row ->
                // animateItem: státuszváltáskor / szűréskor a sorok a helyükre csúsznak.
                Column(Modifier.animateItem().padding(horizontal = 24.dp)) {
                    ItemRow(row, quantityMode, money, onClick = { onOpenItem(row.item.id) })
                }
            }
        }
    }

    sheet?.let { s ->
        FilterSheet(
            sheet = s,
            fields = state.fields,
            quantityMode = quantityMode,
            onChange = viewModel::updateDraft,
            onClear = viewModel::clearDraft,
            onApply = viewModel::applyDraft,
            onDismiss = viewModel::closeSheet,
        )
    }
}

@Composable
private fun SubcategoryChips(state: ItemListUiState, onSelect: (SubcategoryChoice) -> Unit) {
    val selected = state.filter.subcategory
    val choices = buildList {
        add(SubcategoryChoice.All to stringResource(R.string.filter_all))
        state.subcategories.forEach { add(SubcategoryChoice.Id(it.id) to it.name) }
        add(SubcategoryChoice.None to stringResource(R.string.subcategory_other))
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
        items(choices, key = { it.first.toString() }) { (choice, text) ->
            FilterChip(selected = choice == selected, onClick = { onSelect(choice) }, label = { Text(text) })
        }
    }
}

/** Az aktív szűrők címkéi (pl. „RAM ≥ 16 GB”, „Anyag: Arany, Platina”). */
@Composable
private fun activeFilterLabels(state: ItemListUiState, quantityMode: Boolean, money: (Long) -> String): List<Pair<FilterKey, String>> {
    val f = state.filter
    val locale = LocalConfiguration.current.locales[0]
    val result = mutableListOf<Pair<FilterKey, String>>()
    if (f.statuses.isNotEmpty()) {
        val names = listOf(ItemStatus.RESERVED, ItemStatus.IN_STOCK, ItemStatus.SOLD).filter { it in f.statuses }.map { stringResource(it.label) }
        result += FilterKey.Status to names.joinToString(", ")
    }
    if (f.valueMin != null || f.valueMax != null) {
        val name = stringResource(if (quantityMode) R.string.item_unit_value else R.string.field_value)
        result += FilterKey.Value to rangeLabel(name, f.valueMin?.let(money), f.valueMax?.let(money))
    }
    if (f.quantityMin != null || f.quantityMax != null) {
        val pcs = stringResource(R.string.unit_pcs)
        result += FilterKey.Quantity to rangeLabel(stringResource(R.string.field_quantity), f.quantityMin?.let { "$it $pcs" }, f.quantityMax?.let { "$it $pcs" })
    }
    state.fields.forEach { field ->
        val filter = f.fields[field.id]?.takeIf { it.isActive } ?: return@forEach
        val unit = field.unit?.let { " $it" }.orEmpty()
        val text = when (filter) {
            is FieldFilter.NumberRange -> rangeLabel(
                field.name,
                filter.min?.let { NumberFormats.display(it, locale) + unit },
                filter.max?.let { NumberFormats.display(it, locale) + unit },
            )
            is FieldFilter.Options -> field.name + ": " + filter.values.sorted().joinToString(", ")
            is FieldFilter.Bool -> field.name + ": " + stringResource(if (filter.value) R.string.yes else R.string.no)
            is FieldFilter.DateRange -> rangeLabel(
                field.name,
                filter.from?.let { NumberFormats.displayDate(it, locale) },
                filter.to?.let { NumberFormats.displayDate(it, locale) },
            )
            is FieldFilter.Contains -> field.name + ": „" + filter.text.trim() + "”"
        }
        result += FilterKey.Field(field.id) to text
    }
    return result
}

private fun rangeLabel(name: String, from: String?, to: String?): String = when {
    from != null && to != null -> "$name: $from – $to"
    from != null -> "$name ≥ $from"
    else -> "$name ≤ $to"
}

@Composable
private fun ItemRow(row: ItemListRow, quantityMode: Boolean, money: (Long) -> String, onClick: () -> Unit) {
    val item = row.item
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        // Az eladott tételek halványítva (TERV.md 3.6).
        modifier = Modifier.fillMaxWidth().alpha(if (item.status == ItemStatus.SOLD) 0.55f else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(60.dp)) {
                PhotoImage(row.coverFileName, Modifier.fillMaxSize(), maxSide = 200)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.subcategoryName ?: stringResource(R.string.subcategory_other),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                StatusBadge(item.status)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(item.value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (quantityMode) {
                    Text("× ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
