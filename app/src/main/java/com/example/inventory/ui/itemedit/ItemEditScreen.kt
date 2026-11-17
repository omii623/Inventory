package com.example.inventory.ui.itemedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.R
import com.example.inventory.domain.form.FieldInput
import com.example.inventory.data.photo.PhotoStorage
import com.example.inventory.domain.model.TrackingMode
import com.example.inventory.ui.AppViewModelProvider
import com.example.inventory.ui.components.rememberDiscardGuard
import com.example.inventory.util.MoneyFormatter

/** Új tétel / tétel szerkesztése (látványterv 4. képernyő): fotók, alapadatok, a kategória saját mezői. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    onDone: () -> Unit,
    viewModel: ItemEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(Unit) { viewModel.start(locale) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    val leave = rememberDiscardGuard(enabled = !state.finished, hasChanges = viewModel::hasChanges, onLeave = onDone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (state.isNew) R.string.item_new_title else R.string.item_edit_title), fontWeight = FontWeight.Bold)
                        if (state.categoryName.isNotEmpty()) {
                            Text(state.categoryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) } },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.loading && !state.saving) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.photos_section))
            PhotoStrip(
                photos = state.photos,
                importing = state.importingPhotos,
                error = state.photoError,
                remaining = (PhotoStorage.MAX_PHOTOS - state.photos.size).coerceAtLeast(0),
                onPicked = viewModel::onPhotosPicked,
                newCameraTarget = viewModel::newCameraTarget,
                onCameraResult = viewModel::onCameraResult,
                onRemove = viewModel::removePhoto,
                onMakeCover = viewModel::makeCover,
                onMove = viewModel::movePhoto,
            )

            SectionTitle(stringResource(R.string.item_section_basic))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.field_name) + " *") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(stringResource(R.string.form_error_required)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            val quantityMode = state.trackingMode == TrackingMode.QUANTITY
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.value,
                    onValueChange = viewModel::onValueChange,
                    label = { Text(stringResource(if (quantityMode) R.string.item_unit_value else R.string.field_value)) },
                    suffix = { Text(MoneyFormatter.CURRENCY) },
                    isError = state.valueError != null,
                    supportingText = state.valueError?.let { { Text(stringResource(R.string.form_error_number)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                if (quantityMode) {
                    OutlinedTextField(
                        value = state.quantity,
                        onValueChange = viewModel::onQuantityChange,
                        label = { Text(stringResource(R.string.field_quantity)) },
                        suffix = { Text(stringResource(R.string.unit_pcs)) },
                        isError = state.quantityError != null,
                        supportingText = state.quantityError?.let {
                            { Text(stringResource(if (state.isNew) R.string.form_error_quantity_new else R.string.form_error_quantity)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f),
                    )
                }
            }
            SelectField(
                label = stringResource(R.string.field_subcategory),
                selected = state.subcategoryId,
                options = state.subcategories.map { it.id to it.name },
                noneLabel = stringResource(R.string.subcategory_other),
                onSelect = viewModel::onSubcategorySelect,
                // Alkategória nélkül is látszódjon, hogy a tétel „Egyéb” lesz.
                selectedLabel = state.subcategories.firstOrNull { it.id == state.subcategoryId }?.name
                    ?: stringResource(R.string.subcategory_other),
            )

            if (state.fields.isNotEmpty()) {
                SectionTitle(stringResource(R.string.item_section_fields, state.categoryName))
                state.fields.forEach { field ->
                    DynamicField(
                        field = field,
                        input = state.inputs[field.id] ?: FieldInput(),
                        error = state.fieldErrors[field.id],
                        onChange = { viewModel.onFieldChange(field.id, it) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.item_save), fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
