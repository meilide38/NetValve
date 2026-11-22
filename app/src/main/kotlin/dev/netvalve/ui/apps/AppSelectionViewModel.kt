package dev.netvalve.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.netvalve.data.model.BandwidthLimit
import dev.netvalve.data.model.BandwidthUnit
import dev.netvalve.data.model.GlobalSettings
import dev.netvalve.data.model.InstalledApp
import dev.netvalve.repository.AppSelectionRepository
import dev.netvalve.repository.InstalledAppsRepository
import dev.netvalve.repository.SettingsRepository
import dev.netvalve.service.VpnController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRowUi(val app: InstalledApp, val selected: Boolean)

data class AppsUiState(
    val apps: List<AppRowUi> = emptyList(),
    val query: String = "",
    val includeSystem: Boolean = false,
    val loading: Boolean = true,
    val pendingRestart: Boolean = false,
    val tunnelActive: Boolean = false,
    val bulkSettings: GlobalSettings = GlobalSettings.Default,
)

@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    private val installedApps: InstalledAppsRepository,
    private val selectionRepository: AppSelectionRepository,
    private val settingsRepository: SettingsRepository,
    private val controller: VpnController,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val includeSystem = MutableStateFlow(false)
    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val pendingRestart = MutableStateFlow(false)

    init { reload() }

    val uiState: StateFlow<AppsUiState> = combine(
        allApps, selectionRepository.selectedPackages, query, includeSystem, loading, settingsRepository.settings,
    ) { apps, selected, q, include, isLoading, settings ->
        val filtered = apps
            .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .map { AppRowUi(it, it.packageName in selected) }
        AppsUiState(
            apps = filtered,
            query = q,
            includeSystem = include,
            loading = isLoading,
            bulkSettings = settings,
        )
    }.combine(controller.status) { base, status ->
        base.copy(
            pendingRestart = pendingRestart.value && status.isActive,
            tunnelActive = status.isActive,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun setQuery(q: String) { query.value = q }

    fun setIncludeSystem(value: Boolean) {
        includeSystem.value = value
        reload()
    }

    fun toggle(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            selectionRepository.setSelected(packageName, selected)
            markRestartIfNeeded()
        }
    }

    /** Select every app currently visible, preserving selections hidden by the current filter. */
    fun selectAllVisible() {
        viewModelScope.launch {
            val current = selectionRepository.current()
            val visible = uiState.value.apps.map { it.app.packageName }.toSet()
            selectionRepository.setSelected(current + visible)
            markRestartIfNeeded()
        }
    }

    /** Toggle selection for visible apps only; hidden selections remain unchanged. */
    fun invertVisibleSelection() {
        viewModelScope.launch {
            val current = selectionRepository.current().toMutableSet()
            uiState.value.apps.forEach { row ->
                val packageName = row.app.packageName
                if (!current.add(packageName)) current.remove(packageName)
            }
            selectionRepository.setSelected(current)
            markRestartIfNeeded()
        }
    }

    /** Apply one shared cap and daily local-time window to the currently selected package set. */
    fun saveBulkThrottle(
        downloadMbps: Long?,
        uploadMbps: Long?,
        startMinute: Int,
        endMinute: Int,
    ) {
        viewModelScope.launch {
            val packages = selectionRepository.current()
            if (packages.isEmpty() || startMinute == endMinute) return@launch
            settingsRepository.updateSettings { current ->
                current.copy(
                    bulkThrottlePackages = packages,
                    bulkThrottleEnabled = true,
                    bulkDownload = downloadMbps?.let { BandwidthLimit(it, BandwidthUnit.MBPS, true) }
                        ?: BandwidthLimit.Unlimited,
                    bulkUpload = uploadMbps?.let { BandwidthLimit(it, BandwidthUnit.MBPS, true) }
                        ?: BandwidthLimit.Unlimited,
                    bulkStartMinute = startMinute,
                    bulkEndMinute = endMinute,
                )
            }
        }
    }

    fun disableBulkThrottle() {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(bulkThrottleEnabled = false, bulkThrottlePackages = emptySet())
            }
        }
    }

    private fun markRestartIfNeeded() {
        if (controller.status.value.isActive) pendingRestart.value = true
    }

    /** Rebuild the tunnel so the new allow/deny list takes effect. */
    fun applyChanges() {
        controller.restart()
        pendingRestart.value = false
    }

    private fun reload() {
        viewModelScope.launch {
            loading.value = true
            allApps.value = installedApps.listUserApps(includeSystem = includeSystem.value)
            loading.value = false
        }
    }
}
