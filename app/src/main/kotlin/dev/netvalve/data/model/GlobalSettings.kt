package dev.netvalve.data.model

import kotlinx.serialization.Serializable

/**
 * Tunnel-wide settings, persisted via DataStore. These are the knobs that are
 * not specific to any single app.
 */
@Serializable
data class GlobalSettings(
    val selectionMode: SelectionMode = SelectionMode.ONLY_SELECTED,
    val defaultDownload: BandwidthLimit = BandwidthLimit.Unlimited,
    val defaultUpload: BandwidthLimit = BandwidthLimit.Unlimited,
    val ipv6Mode: Ipv6Mode = Ipv6Mode.RELAY,
    val exemptDns: Boolean = true,
    val logLevel: Int = 1,
    val autoStartOnBoot: Boolean = false,
    val enabled: Boolean = false,
    /** Snapshot of selected apps targeted by the bulk scheduled throttle. */
    val bulkThrottlePackages: Set<String> = emptySet(),
    val bulkThrottleEnabled: Boolean = false,
    val bulkDownload: BandwidthLimit = BandwidthLimit.Unlimited,
    val bulkUpload: BandwidthLimit = BandwidthLimit.Unlimited,
    /** Local device time, minutes since midnight. */
    val bulkStartMinute: Int = 20 * 60,
    val bulkEndMinute: Int = 8 * 60,
) {
    companion object {
        val Default = GlobalSettings()
    }
}
