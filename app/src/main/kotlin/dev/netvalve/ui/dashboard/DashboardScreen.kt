package dev.netvalve.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.netvalve.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.BuildConfig
import dev.netvalve.data.model.Ipv6Mode
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.service.TunnelState
import dev.netvalve.ui.components.SectionCard
import dev.netvalve.ui.components.StatTile
import dev.netvalve.ui.navigation.AppActions
import dev.netvalve.utils.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    actions: AppActions,
    onOpenApps: () -> Unit,
    onOpenApp: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("NetValve") })
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusHero(state, actions, onPauseResume = viewModel::pauseResume) }

            if (!BuildConfig.USE_NETSTACK) {
                item {
                    SectionCard {
                        Text(
                            stringResource(R.string.dash_engine_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.dash_engine_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!state.device.usageAccessGranted) {
                item {
                    SetupCard(
                        icon = { Icon(Icons.Filled.Visibility, null) },
                        title = stringResource(R.string.dash_usage_title),
                        body = stringResource(R.string.dash_usage_body),
                        buttonText = stringResource(R.string.dash_usage_button),
                        onClick = actions.requestUsageAccess,
                    )
                }
            }
            if (!state.ignoringBatteryOptimizations) {
                item {
                    SetupCard(
                        icon = { Icon(Icons.Filled.BatteryAlert, null) },
                        title = stringResource(R.string.dash_battery_title),
                        body = stringResource(R.string.dash_battery_body),
                        buttonText = stringResource(R.string.dash_battery_button),
                        onClick = actions.requestBatteryExemption,
                        secondaryText = stringResource(R.string.dash_vendor_settings),
                        onSecondary = actions.openVendorSettings,
                    )
                }
            }

            item { SummaryCard(state, onOpenApps, onOpenApp) }
            item { SettingsCard(state, viewModel) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun StatusHero(state: DashboardUiState, actions: AppActions, onPauseResume: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (state.status.state) {
                        TunnelState.RUNNING -> stringResource(R.string.dash_protected)
                        TunnelState.PAUSED -> stringResource(R.string.dash_paused)
                        TunnelState.STARTING -> stringResource(R.string.dash_starting)
                        TunnelState.ERROR -> stringResource(R.string.dash_error)
                        TunnelState.REVOKED -> stringResource(R.string.dash_revoked)
                        TunnelState.STOPPED -> stringResource(R.string.dash_off)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                state.status.message?.let {
                    Text(
                        if (it.startsWith("Could not establish VPN: "))
                            stringResource(R.string.log_msg_establish_failed, it.removePrefix("Could not establish VPN: "))
                        else it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.running) {
                    Text(
                        stringResource(R.string.dash_session, Format.duration(state.stats.sessionDurationMillis)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = state.status.isActive,
                onCheckedChange = { actions.toggleVpn(it) },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(stringResource(R.string.dash_download), Format.rate(state.stats.liveDownloadBps))
            StatTile(stringResource(R.string.dash_upload), Format.rate(state.stats.liveUploadBps))
            StatTile(stringResource(R.string.dash_active), state.stats.activeConnections.toString())
        }
        if (state.running) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onPauseResume) {
                Icon(Icons.Filled.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.status.state == TunnelState.PAUSED) stringResource(R.string.dash_resume) else stringResource(R.string.dash_pause))
            }
        }
    }
}

@Composable
private fun SetupCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    buttonText: String,
    onClick: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClick) { Text(buttonText) }
            if (secondaryText != null && onSecondary != null) {
                FilledTonalButton(onClick = onSecondary) { Text(secondaryText) }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: DashboardUiState, onOpenApps: () -> Unit, onOpenApp: (String) -> Unit) {
    SectionCard(title = stringResource(R.string.dash_controlled_apps)) {
        val modeText = when (state.settings.selectionMode) {
            SelectionMode.ONLY_SELECTED -> stringResource(R.string.dash_selected_count, state.selectedCount)
            SelectionMode.ALL_EXCEPT -> stringResource(R.string.dash_all_except, state.selectedCount)
        }
        Text(stringResource(R.string.dash_active_rules, modeText, state.activeRuleCount), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        if (state.controlledApps.isEmpty()) {
            Text(
                stringResource(R.string.dash_no_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.controlledApps.take(12).forEach { app ->
                    AssistChip(onClick = { onOpenApp(app.packageName) }, label = { Text(app.label) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenApps) { Text(stringResource(R.string.dash_manage_apps)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(state: DashboardUiState, viewModel: DashboardViewModel) {
    SectionCard(title = stringResource(R.string.dash_tunnel_settings)) {
        // Selection mode
        LabeledSwitchRow(
            label = stringResource(R.string.dash_only_selected),
            checked = state.settings.selectionMode == SelectionMode.ONLY_SELECTED,
            onCheckedChange = {
                viewModel.setSelectionMode(if (it) SelectionMode.ONLY_SELECTED else SelectionMode.ALL_EXCEPT)
                viewModel.applyAndMaybeRestart()
            },
        )
        LabeledSwitchRow(
            label = stringResource(R.string.dash_ipv6),
            checked = state.settings.ipv6Mode == Ipv6Mode.RELAY,
            onCheckedChange = {
                viewModel.setIpv6Mode(if (it) Ipv6Mode.RELAY else Ipv6Mode.FAST_REJECT)
                viewModel.applyAndMaybeRestart()
            },
        )
        LabeledSwitchRow(
            label = stringResource(R.string.dash_dns),
            checked = state.settings.exemptDns,
            onCheckedChange = { viewModel.setExemptDns(it) },
        )
        LabeledSwitchRow(
            label = stringResource(if (state.settings.use24HourTime) R.string.time_format_24 else R.string.time_format_12),
            checked = state.settings.use24HourTime,
            onCheckedChange = { viewModel.set24HourTime(it) },
        )
        LabeledSwitchRow(
            label = stringResource(R.string.dash_autostart),
            checked = state.settings.autoStartOnBoot,
            onCheckedChange = { viewModel.setAutoStart(it) },
        )
    }
}

@Composable
private fun LabeledSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
