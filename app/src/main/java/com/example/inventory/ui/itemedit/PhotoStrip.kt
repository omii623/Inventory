package com.example.inventory.ui.itemedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.ui.components.PhotoImage
import com.example.inventory.ui.theme.InventoryTheme

private val TILE = 96.dp

/**
 * Fotók az űrlap tetején (látványterv 4. képernyő): „Fotó hozzáadása” csempe (kamera vagy galéria),
 * majd a képek sorrendben – az első a borítókép. Egy képre koppintva: borítókép, áthelyezés, eltávolítás.
 */
@Composable
fun PhotoStrip(
    photos: List<String>,
    importing: Boolean,
    error: Boolean,
    remaining: Int,
    onPicked: (List<android.net.Uri>) -> Unit,
    newCameraTarget: () -> android.net.Uri,
    onCameraResult: (Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onMakeCover: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    // A galéria csak annyit enged kiválasztani, amennyi még belefér (a többes választóhoz legalább 2 kell).
    val pickMany = rememberLauncherForActivityResult(
        remember(remaining) { ActivityResultContracts.PickMultipleVisualMedia(remaining.coerceAtLeast(2)) }
    ) { uris -> onPicked(uris) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPicked(listOf(uri)) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> onCameraResult(ok) }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            if (remaining > 0) {
                AddTile(
                    importing = importing,
                    onCamera = { runCatching { camera.launch(newCameraTarget()) }.onFailure { onCameraResult(false) } },
                    onGallery = { if (remaining == 1) pickOne.launch(request) else pickMany.launch(request) },
                )
            }
            photos.forEachIndexed { index, name ->
                PhotoTile(
                    name = name,
                    isCover = index == 0,
                    canMoveLeft = index > 0,
                    canMoveRight = index < photos.lastIndex,
                    onRemove = { onRemove(name) },
                    onMakeCover = { onMakeCover(name) },
                    onMove = { delta -> onMove(name, delta) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                error -> stringResource(R.string.photo_import_failed)
                remaining == 0 -> stringResource(R.string.photo_limit_reached, PhotoStorage.MAX_PHOTOS)
                else -> stringResource(R.string.photo_count, photos.size, PhotoStorage.MAX_PHOTOS)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddTile(importing: Boolean, onCamera: () -> Unit, onGallery: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { if (!importing) menu = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.size(TILE),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (importing) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Filled.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.photo_add),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_take)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = { menu = false; onCamera() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_pick)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = { menu = false; onGallery() },
            )
        }
    }
}

@Composable
private fun PhotoTile(
    name: String,
    isCover: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onRemove: () -> Unit,
    onMakeCover: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { menu = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = if (isCover) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, InventoryTheme.colors.border),
            modifier = Modifier.size(TILE),
        ) {
            Box {
                PhotoImage(name, Modifier.fillMaxSize(), maxSide = 300)
                if (isCover) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomStart),
                    ) {
                        Text(
                            stringResource(R.string.photo_cover),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!isCover) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_make_cover)) },
                    leadingIcon = { Icon(Icons.Outlined.Star, null) },
                    onClick = { menu = false; onMakeCover() },
                )
            }
            if (canMoveLeft) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_move_left)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                    onClick = { menu = false; onMove(-1) },
                )
            }
            if (canMoveRight) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_move_right)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    onClick = { menu = false; onMove(1) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_remove), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}
