package com.example.inventory.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.inventory.ui.AppViewModelProvider
import com.example.inventory.ui.categoryedit.DeleteCategoryDialog
import com.example.inventory.ui.components.CategoryBadge
import com.example.inventory.ui.components.EmptyState
import com.example.inventory.ui.theme.InventoryTheme
import com.example.inventory.util.rememberMoneyFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedCategoriesScreen(
    onBack: () -> Unit,
    viewModel: ArchivedCategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val money = rememberMoneyFormat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archived_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val list = categories
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Archive,
                title = stringResource(R.string.archived_empty_title),
                text = stringResource(R.string.archived_empty_text),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(list, key = { it.category.id }) { summary ->
                    Column(Modifier.animateItem()) { ArchivedRow(summary, money, onRestore = { viewModel.restore(summary.category.id) }, onDelete = { viewModel.requestDelete(summary) }) }
                }
            }
        }
    }

    pendingDelete?.let { pending ->
        DeleteCategoryDialog(
            name = pending.name,
            itemCount = pending.itemCount,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun ArchivedRow(summary: CategorySummary, money: (Long) -> String, onRestore: () -> Unit, onDelete: () -> Unit) {
    val category = summary.category
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, InventoryTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category.icon, Color(category.color), category.name, size = 40.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.item_count, summary.itemCount.toInt(), summary.itemCount.toInt()) + " • " + money(summary.totalValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.archived_delete), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onRestore) { Text(stringResource(R.string.archived_restore)) }
            }
        }
    }
}
