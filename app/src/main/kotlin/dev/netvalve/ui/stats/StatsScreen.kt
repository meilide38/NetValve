package dev.netvalve.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.MaterialTheme
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
import dev.netvalve.ui.components.LabeledRow
import dev.netvalve.ui.components.SectionCard
import dev.netvalve.ui.components.StatTile
import dev.netvalve.utils.Format

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state.snapshot

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.stats_title)) },
            actions = { TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.stats_reset)) } },
        )
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.stats_totals)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(stringResource(R.string.stats_downloaded), Format.bytes(s.totalDownload))
                        StatTile(stringResource(R.string.stats_uploaded), Format.bytes(s.totalUpload))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(stringResource(R.string.stats_live_down), Format.rate(s.liveDownloadBps))
                        StatTile(stringResource(R.string.stats_avg_down), Format.rate(s.avgDownloadBps))
                        StatTile(stringResource(R.string.stats_peak_down), Format.rate(s.peakDownloadBps))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(stringResource(R.string.stats_live_up), Format.rate(s.liveUploadBps))
                        StatTile(stringResource(R.string.stats_avg_up), Format.rate(s.avgUploadBps))
                        StatTile(stringResource(R.string.stats_peak_up), Format.rate(s.peakUploadBps))
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.stats_connections)) {
                    LabeledRow(stringResource(R.string.stats_active), s.activeConnections.toString())
                    LabeledRow(stringResource(R.string.stats_throttled), s.throttledConnections.toString())
                    LabeledRow(stringResource(R.string.stats_blocked), s.blockedConnections.toString())
                    LabeledRow(stringResource(R.string.stats_dns_queries), s.dnsQueries.toString())
                    LabeledRow(stringResource(R.string.stats_latency), "${s.avgConnectLatencyMillis} ms")
                    LabeledRow(stringResource(R.string.stats_session), Format.duration(s.sessionDurationMillis))
                }
            }
            if (state.apps.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.QueryStats,
                        title = stringResource(R.string.stats_empty_title),
                        subtitle = stringResource(R.string.stats_empty_body),
                        modifier = Modifier.height(220.dp),
                    )
                }
            } else {
                item { Text(stringResource(R.string.stats_per_app), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
                items(state.apps, key = { it.uid }) { app -> AppStatCard(app) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AppStatCard(app: AppStatRow) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (app.active) Text(stringResource(R.string.stats_live), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(stringResource(R.string.stats_total_down), Format.bytes(app.downloadBytes))
            StatTile(stringResource(R.string.stats_total_up), Format.bytes(app.uploadBytes))
            StatTile(stringResource(R.string.stats_live_down_short), Format.rate(app.liveDownloadBps))
            StatTile(stringResource(R.string.stats_live_up_short), Format.rate(app.liveUploadBps))
        }
    }
}
