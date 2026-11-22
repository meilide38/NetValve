package dev.netvalve.rules

import dev.netvalve.data.model.AppRule
import dev.netvalve.data.model.GlobalSettings
import dev.netvalve.data.model.RuleCondition
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.repository.AppInfoLookup
import dev.netvalve.repository.AppSelectionRepository
import dev.netvalve.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Orchestrates the pure [PolicyEvaluator] against live state.
 */
class RuleEngine(
    private val settingsRepository: SettingsRepository,
    private val selectionRepository: AppSelectionRepository,
    private val appInfo: AppInfoLookup,
    private val deviceStateMonitor: DeviceStateMonitor,
    private val ownUid: Int,
    scope: CoroutineScope,
    private val evaluator: PolicyEvaluator = PolicyEvaluator(),
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
) {
    data class Snapshot(
        val settings: GlobalSettings,
        val rules: Map<String, AppRule>,
        val selected: Set<String>,
        val device: DeviceState,
    )

    val snapshot: StateFlow<Snapshot> =
        combine(
            settingsRepository.settings,
            settingsRepository.rules,
            selectionRepository.selectedPackages,
            deviceStateMonitor.state,
        ) { settings, rules, selected, device ->
            Snapshot(settings, rules, selected, device)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            Snapshot(GlobalSettings.Default, emptyMap(), emptySet(), DeviceState()),
        )

    /**
     * Revision ticks on input changes and every 15 seconds while a bulk schedule
     * is enabled, so already-open flows cross schedule boundaries promptly.
     */
    val revision: StateFlow<Long> = MutableStateFlow(0L).also { flow ->
        snapshot.onEach { flow.value = flow.value + 1 }.launchIn(scope)
        scope.launch {
            while (true) {
                delay(15_000)
                if (snapshot.value.settings.bulkThrottleEnabled) {
                    flow.value = flow.value + 1
                }
            }
        }
    }

    fun isControlled(uid: Int): Boolean {
        if (uid == ownUid) return false
        val snap = snapshot.value
        val pkgs = appInfo.packagesForUid(uid)
        if (pkgs.isEmpty()) {
            return snap.settings.selectionMode == SelectionMode.ALL_EXCEPT
        }
        val anySelected = pkgs.any { it in snap.selected }
        val bulkTarget = snap.settings.bulkThrottleEnabled &&
            pkgs.any { it in snap.settings.bulkThrottlePackages }
        return when (snap.settings.selectionMode) {
            SelectionMode.ONLY_SELECTED -> anySelected
            SelectionMode.ALL_EXCEPT -> bulkTarget || !anySelected
        }
    }

    /** Effective per-app policy, including the optional scheduled bulk cap. */
    fun policyForUid(uid: Int): EffectivePolicy {
        val snap = snapshot.value
        val pkgs = appInfo.packagesForUid(uid)
        val pkg = pkgs.firstOrNull { snap.settings.bulkThrottleEnabled && it in snap.settings.bulkThrottlePackages }
            ?: pkgs.firstOrNull { snap.rules[it]?.isActive == true }
            ?: pkgs.firstOrNull()
        val appRule: AppRule? = pkg?.let { snap.rules[it] }
        val ctx = snap.device.toEvaluationContext(pkg ?: "", clock())
        val policy = evaluator.evaluate(
            rules = appRule?.toPolicyRules().orEmpty(),
            defaultAction = snap.settings.defaultAction(),
            ctx = ctx,
        ).copy(warnThresholdPercent = appRule?.warnThresholdPercent)

        val bulk = snap.settings
        if (policy.blocked || !bulk.bulkThrottleEnabled || pkg == null ||
            pkg !in bulk.bulkThrottlePackages ||
            !RuleCondition.TimeWindow(bulk.bulkStartMinute, bulk.bulkEndMinute).matches(ctx)
        ) return policy

        val bulkDown = bulk.bulkDownload.bytesPerSecondOrNull()
        val bulkUp = bulk.bulkUpload.bytesPerSecondOrNull()
        if (bulkDown == null && bulkUp == null) return policy

        fun stricter(existing: Long?, bulkCap: Long?): Long? = when {
            existing == null -> bulkCap
            bulkCap == null -> existing
            else -> minOf(existing, bulkCap)
        }

        return policy.copy(
            downloadBytesPerSec = stricter(policy.downloadBytesPerSec, bulkDown),
            uploadBytesPerSec = stricter(policy.uploadBytesPerSec, bulkUp),
        )
    }
}
