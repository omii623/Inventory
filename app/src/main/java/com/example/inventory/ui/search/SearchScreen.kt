package com.example.inventory.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.R
import com.example.inventory.data.db.model.SearchRow
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.AppViewModelProvider
import com.example.inventory.ui.components.EmptyState
import com.example.inventory.ui.components.PhotoImage
import com.example.inventory.ui.components.StatusBadge
import com.example.inventory.ui.theme.InventoryTheme
import com.example.inventory.util.rememberMoneyFormat

/** Globális keresés az összes nem archivált kategóriában (név és szöveges mezők, ékezet nélkül is). */
@Composable
fun SearchScreen(
    onOpenItem: (Long) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val money = rememberMoneyFormat()
    // A Keresés fülre érve rögtön lehessen gépelni (a kezdőlap keresőmezője is ide visz).
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.form_clear)) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).focusRequester(focus),
        )
        val list = results
        when {
            list == null -> EmptyState(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.search_start_title),
                text = stringResource(R.string.search_empty_text),
            )
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.search_no_results_title),
                text = stringResource(R.string.search_no_results_text),
            )
            else -> {
                Text(
                    pluralStringResource(R.plurals.search_result_count, list.size, list.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.item.id }) { row ->
                        Column(Modifier.animateItem()) { ResultRow(row, money, onClick = { onOpenItem(row.item.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(row: SearchRow, money: (Long) -> String, onClick: () -> Unit) {
    val item = row.item
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = Modifier.fillMaxWidth().alpha(if (item.status == ItemStatus.SOLD) 0.55f else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                PhotoImage(row.coverFileName, Modifier.fillMaxSize(), maxSide = 160, placeholderSize = 20.dp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.categoryName + " • " + (row.subcategoryName ?: stringResource(R.string.subcategory_other)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(4.dp))
                StatusBadge(item.status)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(item.value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (row.trackingMode == TrackingMode.QUANTITY) {
                    Text("× ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
