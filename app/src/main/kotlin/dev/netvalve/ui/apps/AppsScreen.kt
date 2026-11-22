package dev.netvalve.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.R
import dev.netvalve.data.model.BandwidthUnit
import dev.netvalve.ui.components.EmptyState
import java.text.DateFormatSymbols
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onOpenApp: (String) -> Unit,
    viewModel: AppSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCount = state.apps.count { it.selected }
    var showBulkDialog by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.apps_title)) })

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.includeSystem,
                onClick = { viewModel.setIncludeSystem(!state.includeSystem) },
                label = { Text(stringResource(R.string.apps_show_system)) },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.apps_selected_count, selectedCount), style = MaterialTheme.typography.labelMedium)
            Row {
                TextButton(onClick = viewModel::selectAllVisible, enabled = state.apps.isNotEmpty()) {
                    Text(stringResource(R.string.apps_select_all))
                }
                TextButton(onClick = viewModel::invertVisibleSelection, enabled = state.apps.isNotEmpty()) {
                    Text(stringResource(R.string.apps_invert))
                }
            }
        }

        Button(
            onClick = { showBulkDialog = true },
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(stringResource(
                if (state.bulkSettings.bulkThrottleEnabled) R.string.apps_bulk_throttle_edit
                else R.string.apps_bulk_throttle,
            ))
        }
        if (state.bulkSettings.bulkThrottleEnabled) {
            Text(
                stringResource(R.string.apps_bulk_throttle_active, state.bulkSettings.bulkThrottlePackages.size),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (state.pendingRestart) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.apps_selection_changed), color = MaterialTheme.colorScheme.tertiary)
                TextButton(onClick = viewModel::applyChanges) { Text(stringResource(R.string.action_apply)) }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.apps.isEmpty() -> EmptyState(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.apps_empty_title),
                subtitle = stringResource(R.string.apps_empty_hint),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.apps, key = { it.app.packageName }) { row ->
                    AppRow(
                        label = row.app.label,
                        packageName = row.app.packageName,
                        isSystem = row.app.isSystem,
                        selected = row.selected,
                        onToggle = { viewModel.toggle(row.app.packageName, it) },
                        onOpen = { onOpenApp(row.app.packageName) },
                    )
                }
            }
        }
    }

    if (showBulkDialog) {
        BulkThrottleDialog(
            initialDownload = state.bulkSettings.bulkDownload.value.takeIf { !state.bulkSettings.bulkDownload.isUnlimited }?.toString() ?: "1",
            initialUpload = state.bulkSettings.bulkUpload.value.takeIf { !state.bulkSettings.bulkUpload.isUnlimited }?.toString() ?: "1",
            initialStart = state.bulkSettings.bulkStartMinute.toClockText(state.bulkSettings.use24HourTime),
            initialEnd = state.bulkSettings.bulkEndMinute.toClockText(state.bulkSettings.use24HourTime),
            initialDownloadUnit = state.bulkSettings.bulkDownload.unit,
            initialUploadUnit = state.bulkSettings.bulkUpload.unit,
            use24HourTime = state.bulkSettings.use24HourTime,
            canDisable = state.bulkSettings.bulkThrottleEnabled,
            onDismiss = { showBulkDialog = false },
            onSave = { down, downUnit, up, upUnit, start, end ->
                viewModel.saveBulkThrottle(down, downUnit, up, upUnit, start, end)
                showBulkDialog = false
            },
            onDisable = {
                viewModel.disableBulkThrottle()
                showBulkDialog = false
            },
        )
    }
}

@Composable
private fun BulkThrottleDialog(
    initialDownload: String,
    initialUpload: String,
    initialStart: String,
    initialEnd: String,
    initialDownloadUnit: BandwidthUnit,
    initialUploadUnit: BandwidthUnit,
    use24HourTime: Boolean,
    canDisable: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long?, BandwidthUnit, Long?, BandwidthUnit, Int, Int) -> Unit,
    onDisable: () -> Unit,
) {
    var download by remember(initialDownload) { mutableStateOf(initialDownload) }
    var upload by remember(initialUpload) { mutableStateOf(initialUpload) }
    var start by remember(initialStart) { mutableStateOf(initialStart) }
    var end by remember(initialEnd) { mutableStateOf(initialEnd) }
    var downloadUnit by remember(initialDownloadUnit) { mutableStateOf(initialDownloadUnit) }
    var uploadUnit by remember(initialUploadUnit) { mutableStateOf(initialUploadUnit) }

    val downValue = download.toLongOrNull()?.takeIf { it > 0 }
    val upValue = upload.toLongOrNull()?.takeIf { it > 0 }
    val startMinute = start.toMinuteOfDay(use24HourTime)
    val endMinute = end.toMinuteOfDay(use24HourTime)
    val valid = (downValue != null || upValue != null) &&
        startMinute != null && endMinute != null && startMinute != endMinute

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_bulk_throttle_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.apps_bulk_throttle_hint))
                OutlinedTextField(
                    value = download,
                    onValueChange = { download = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.apps_bulk_download_limit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BandwidthUnit.entries.forEach { unit ->
                        FilterChip(selected = downloadUnit == unit, onClick = { downloadUnit = unit }, label = { Text(unit.label) })
                    }
                }
                OutlinedTextField(
                    value = upload,
                    onValueChange = { upload = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.apps_bulk_upload_limit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BandwidthUnit.entries.forEach { unit ->
                        FilterChip(selected = uploadUnit == unit, onClick = { uploadUnit = unit }, label = { Text(unit.label) })
                    }
                }
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.take(8) },
                    label = { Text(stringResource(R.string.apps_bulk_start_time)) },
                    supportingText = { Text(stringResource(if (use24HourTime) R.string.apps_bulk_time_format else R.string.apps_bulk_time_format_12)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it.take(8) },
                    label = { Text(stringResource(R.string.apps_bulk_end_time)) },
                    supportingText = { Text(stringResource(if (use24HourTime) R.string.apps_bulk_time_format else R.string.apps_bulk_time_format_12)) },
                    singleLine = true,
                )
                if (!valid) {
                    Text(
                        stringResource(R.string.apps_bulk_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canDisable) {
                    TextButton(onClick = onDisable) { Text(stringResource(R.string.apps_bulk_disable)) }
                }
                Button(
                    onClick = { onSave(downValue, downloadUnit, upValue, uploadUnit, startMinute!!, endMinute!!) },
                    enabled = valid,
                ) { Text(stringResource(R.string.apps_bulk_save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        },
    )
}

private fun Int.toClockText(use24HourTime: Boolean): String {
    val hour = this / 60
    val minute = this % 60
    return if (use24HourTime) "%02d:%02d".format(hour, minute)
    else "%d:%02d %s".format(
        if (hour % 12 == 0) 12 else hour % 12,
        minute,
        DateFormatSymbols(Locale.getDefault()).amPmStrings[if (hour < 12) 0 else 1],
    )
}

private fun String.toMinuteOfDay(use24HourTime: Boolean): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM|上午|下午))?$", RegexOption.IGNORE_CASE).matchEntire(trim()) ?: return null
    var hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    val suffix = match.groupValues[3]
    if (minute !in 0..59) return null
    if (use24HourTime) {
        if (suffix.isNotEmpty() || hour !in 0..23) return null
    } else {
        if (suffix.isEmpty() || hour !in 1..12) return null
        hour = (hour % 12) + if (suffix.equals("PM", true) || suffix == "下午") 12 else 0
    }
    return hour * 60 + minute
}

@Composable
private fun AppRow(
    label: String,
    packageName: String,
    isSystem: Boolean,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!selected) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isSystem) "$packageName · ${stringResource(R.string.apps_system)}" else packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpen) { Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.apps_configure)) }
        Checkbox(checked = selected, onCheckedChange = onToggle)
    }
}

@Composable
private fun LetterAvatar(label: String) {
    Box(
        Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
