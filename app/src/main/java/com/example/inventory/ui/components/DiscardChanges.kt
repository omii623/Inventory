package com.example.inventory.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.inventory.R

/**
 * Mentetlen változások védelme egy szerkesztőn: a rendszer „vissza” gomb és a felső sáv vissza/bezárás gombja
 * előbb rákérdez, ha [hasChanges] igaz. Visszaadja a felső sáv gombjához használandó műveletet.
 */
@Composable
fun rememberDiscardGuard(enabled: Boolean, hasChanges: () -> Boolean, onLeave: () -> Unit): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    val attemptLeave = { if (hasChanges()) asking = true else onLeave() }
    BackHandler(enabled = enabled) { attemptLeave() }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_text)) },
            confirmButton = {
                TextButton(onClick = { asking = false; onLeave() }) {
                    Text(stringResource(R.string.discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.discard_keep)) } },
        )
    }
    return attemptLeave
}
