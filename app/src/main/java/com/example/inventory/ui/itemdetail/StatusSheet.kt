package com.example.inventory.ui.itemdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.domain.model.ItemStatus
import com.example.inventory.ui.components.StatusDot
import com.example.inventory.ui.components.StatusOrder
import com.example.inventory.ui.components.colors
import com.example.inventory.ui.components.description
import com.example.inventory.ui.components.label
import com.example.inventory.ui.theme.InventoryTheme

/**
 * Státusz módosítása (látványterv 7. képernyő, TERV.md 3.6): a három lehetőség rövid leírással és Mentés.
 * Más mező nincs rajta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSheet(itemName: String, current: ItemStatus, onSave: (ItemStatus) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.status_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(itemName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            StatusOrder.forEach { status ->
                val active = status == selected
                val (color, container) = status.colors()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) container else MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) color else InventoryTheme.colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = active, onClick = { selected = status }, role = Role.RadioButton),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                        StatusDot(status, size = 12)
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(status.label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(status.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = active, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = color))
                    }
                }
            }
            Button(
                onClick = { onSave(selected) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).height(52.dp),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold) }
        }
    }
}
