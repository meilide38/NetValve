package dev.netvalve.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.netvalve.R
import dev.netvalve.log.LogEvent
import dev.netvalve.log.LogCategory
import dev.netvalve.log.LogLevel
import dev.netvalve.ui.components.EmptyState
import dev.netvalve.ui.navigation.AppActions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    actions: AppActions,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.logs_title)) },
            actions = {
                IconButton(onClick = { viewModel.export(actions.exportLogs) }) { Icon(Icons.Filled.Share, stringResource(R.string.logs_export)) }
                IconButton(onClick = viewModel::clear) { Icon(Icons.Filled.Delete, stringResource(R.string.logs_clear)) }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.minLevel == level,
                    onClick = { viewModel.setMinLevel(level) },
                    label = { Text(stringResource(level.labelRes())) },
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.logs_filter)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (state.entries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Article,
                title = stringResource(R.string.logs_empty_title),
                subtitle = stringResource(R.string.logs_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.entries) { entry -> LogRow(entry, state.use24HourTime) }
            }
        }
    }
}

private fun LogLevel.labelRes(): Int = when (this) {
    LogLevel.ERROR -> R.string.log_level_error
    LogLevel.WARNING -> R.string.log_level_warning
    LogLevel.INFO -> R.string.log_level_info
    LogLevel.DEBUG -> R.string.log_level_debug
}

private fun LogCategory.labelRes(): Int = when (this) {
    LogCategory.SYSTEM -> R.string.log_category_system
    LogCategory.CONNECTION_OPEN -> R.string.log_category_connection_open
    LogCategory.CONNECTION_CLOSE -> R.string.log_category_connection_close
    LogCategory.THROTTLE -> R.string.log_category_throttle
    LogCategory.RULE_MATCH -> R.string.log_category_rule_match
    LogCategory.BLOCK -> R.string.log_category_block
    LogCategory.DNS -> R.string.log_category_dns
    LogCategory.ERROR -> R.string.log_category_error
}

@Composable
private fun localizeLogMessage(message: String): String = when {
    message.startsWith("establish failed: ") ->
        stringResource(R.string.log_msg_establish_failed, message.substringAfter("establish failed: "))
    message.startsWith("Tunnel running; controlling ") ->
        stringResource(R.string.log_msg_tunnel_running, message.substringAfter("controlling ").substringBefore(" app"))
    message == "ONLY_SELECTED with no apps selected: controlling nothing" ->
        stringResource(R.string.log_msg_no_apps)
    message.startsWith("allowed app not found: ") ->
        stringResource(R.string.log_msg_allowed_app_missing, message.substringAfter("allowed app not found: "))
    message.startsWith("disallowed app not found: ") ->
        stringResource(R.string.log_msg_disallowed_app_missing, message.substringAfter("disallowed app not found: "))
    message == "Paused (passthrough)" -> stringResource(R.string.log_msg_paused)
    message == "Resumed" -> stringResource(R.string.log_msg_resumed)
    message.startsWith("VPN revoked") -> stringResource(R.string.log_msg_vpn_revoked)
    message.startsWith("Packet engine: ") && message.contains("(netstack, real forwarding)") ->
        stringResource(R.string.log_msg_engine_netstack, message.substringAfter("Packet engine: ").substringBefore(" ("))
    message.startsWith("Tunnel engine started (") ->
        stringResource(R.string.log_msg_engine_started, message.substringAfter("Tunnel engine started (").removeSuffix(")").replace("ipv6=", "IPv6=").replace("exemptDns=", "DNS exempt="))
    message == "Tunnel engine stopped" -> stringResource(R.string.log_msg_engine_stopped)
    message.startsWith("reset ") -> stringResource(R.string.log_msg_reset, message.removePrefix("reset "))
    message.startsWith("connect failed ") -> stringResource(R.string.log_msg_connect_failed, message.removePrefix("connect failed "))
    message.startsWith("drop udp ") -> stringResource(R.string.log_msg_udp_dropped, message.removePrefix("drop udp "))
    message.startsWith("udp open failed ") -> stringResource(R.string.log_msg_udp_open_failed, message.removePrefix("udp open failed "))
    message.startsWith("udp tail-drop ") -> stringResource(R.string.log_msg_udp_tail_drop, message.removePrefix("udp tail-drop "))
    message.startsWith("matched ") -> stringResource(R.string.log_msg_rule_matched, message.removePrefix("matched ").replace(" for ", "，连接 "))
    message.startsWith("protect() failed for ") -> stringResource(R.string.log_msg_protect_failed, message.removePrefix("protect() failed for "))
    else -> message
}

@Composable
private fun LogRow(entry: LogEvent, use24HourTime: Boolean) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val time = rememberLogTime(entry.timestampMillis, use24HourTime)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "$time  ${entry.level.name.take(1)}/${stringResource(entry.category.labelRes())}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            localizeLogMessage(entry.message) + (entry.packageName?.let { "  ($it)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun rememberLogTime(timestamp: Long, use24HourTime: Boolean): String {
    val pattern = if (use24HourTime) "HH:mm:ss" else "hh:mm:ss a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}
