package com.example.inventory.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.R
import com.example.inventory.data.db.model.CategorySummary
import com.example.inventory.data.db.model.InventoryTotals
import com.example.inventory.data.db.model.RecentItem
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.AppViewModelProvider
import com.example.inventory.ui.components.CategoryBadge
import com.example.inventory.ui.components.EmptyState
import com.example.inventory.ui.components.PhotoImage
import com.example.inventory.ui.theme.InventoryTheme
import com.example.inventory.util.rememberMoneyFormat

/** Kezdőlap (látványterv 1. képernyő): keresés, összesítő, kategóriakártyák, legutóbbi tétel. */
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onNewCategory: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = rememberMoneyFormat()

    if (!state.loading && state.categories.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Title(Modifier.padding(horizontal = 24.dp))
            Box(Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = stringResource(R.string.home_empty_title),
                    text = stringResource(R.string.home_empty_text),
                )
            }
            Button(onClick = onNewCategory, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_empty_button))
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item { Title() }
        item { SearchBox(onOpenSearch) }
        item { SummaryCard(state.totals, money) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.home_categories),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onNewCategory) { Text(stringResource(R.string.home_new_category)) }
            }
        }
        items(state.categories.chunked(2), key = { row -> row.first().category.id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { summary ->
                    CategoryCard(summary, money, onClick = { onOpenCategory(summary.category.id) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { NewCategoryCard(onNewCategory) }
        state.recent?.let { recent ->
            item {
                Text(
                    stringResource(R.string.home_recent),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item { RecentRow(recent, money) }
        }
    }
}

@Composable
private fun Title(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.home_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 24.dp),
    )
}

@Composable
private fun SearchBox(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryCard(totals: InventoryTotals, money: (Long) -> String) {
    val colors = InventoryTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = colors.hero, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_total_value), color = colors.onHeroMuted, style = MaterialTheme.typography.labelLarge)
            Text(money(totals.totalValue), color = colors.onHero, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.home_counts,
                    pluralStringResource(R.plurals.item_count, totals.itemCount.toInt(), totals.itemCount.toInt()),
                    pluralStringResource(R.plurals.category_count, totals.categoryCount.toInt(), totals.categoryCount.toInt()),
                ),
                color = colors.onHeroMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CategoryCard(summary: CategorySummary, money: (Long) -> String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val category = summary.category
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            CategoryBadge(category.icon, Color(category.color), category.name, size = 48.dp)
            Spacer(Modifier.height(14.dp))
            Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.item_count, summary.itemCount.toInt(), summary.itemCount.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(money(summary.totalValue), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun NewCategoryCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(stringResource(R.string.home_create_category_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_create_category_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentRow(recent: RecentItem, money: (Long) -> String) {
    val quantityMode = recent.trackingMode == TrackingMode.QUANTITY
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                PhotoImage(recent.coverFileName, Modifier.fillMaxSize(), maxSide = 160, placeholderSize = 20.dp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(recent.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    recent.categoryName + " • " + (recent.subcategoryName ?: stringResource(R.string.subcategory_other)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(recent.value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (quantityMode) {
                    Text("× ${recent.quantity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
