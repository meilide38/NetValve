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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.R
import dev.netvalve.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onOpenApp: (String) -> Unit,
    viewModel: AppSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCount = state.apps.count { it.selected }

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
