package dev.netvalve.throttle

import dev.netvalve.data.model.Direction
import dev.netvalve.log.LogCategory
import dev.netvalve.log.LogLevel
import dev.netvalve.log.Logger
import dev.netvalve.rules.EffectivePolicy
import dev.netvalve.rules.RuleEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the per-app, per-direction [TokenBucket]s and exposes the single hot-path
 * primitive the relay uses: [pace].
 *
 * ### Where throttling happens (see docs/THROTTLING.md)
 * ```
 * UPLOAD  : app → TUN → netstack → relay → [pace(uid,UPLOAD)]  → upstream.write() → net
 * DOWNLOAD: net → upstream.read() → [pace(uid,DOWNLOAD)] → relay → netstack → TUN → app
 * ```
 * A bucket is created lazily per (uid, direction) at flow-open with the cap from
 * [RuleEngine.policyForUid]. When rules or device state change, [revision] fires
 * and every live bucket's rate is refreshed in place, so a cap edit takes effect
 * on in-flight connections without reconnecting.
 *
 * Unlimited directions get a shared zero-rate bucket. This lets later schedule
 * changes update already-open flows; [pace] on a zero-rate bucket is a no-op.
 */
class ThrottleManager(
    private val ruleEngine: RuleEngine,
    scope: CoroutineScope,
    private val logger: Logger? = null,
) {
    private val buckets = ConcurrentHashMap<Long, TokenBucket>()

    init {
        // Refresh live buckets whenever the resolved rule set changes.
        ruleEngine.revision
            .onEach { refreshAll() }
            .launchIn(scope)
    }

    private fun key(uid: Int, direction: Direction): Long =
        (uid.toLong() shl 1) or (if (direction == Direction.DOWNLOAD) 1L else 0L)

    private fun capFor(policy: EffectivePolicy, direction: Direction): Long? =
        if (direction == Direction.DOWNLOAD) policy.downloadBytesPerSec else policy.uploadBytesPerSec

    /**
     * Always returns a shared bucket, including for currently-unlimited rules.
     * A zero-rate bucket is a no-op today but can be raised when a schedule or
     * policy starts throttling an already-open connection.
     */
    fun bucketFor(uid: Int, direction: Direction): TokenBucket {
        val k = key(uid, direction)
        val cap = capFor(ruleEngine.policyForUid(uid), direction)
        val rate = cap ?: 0L
        val burst = cap?.let { burstFor(it) } ?: UNLIMITED_BURST_BYTES
        val created = !buckets.containsKey(k)
        val bucket = buckets.getOrPut(k) { TokenBucket(capacityBytes = burst, rateBytesPerSec = rate) }
        bucket.updateRate(rate, burst)
        if (created) {
            logger?.i(
                LogCategory.THROTTLE,
                "bucket created dir=$direction rate=$rate B/s burst=$burst B",
                uid = uid,
            )
        }
        return bucket
    }

    /**
     * Suspend until [bytes] of allowance are available on [bucket], then return.
     * A null bucket returns immediately (unlimited). Never drops.
     *
     * The full reservation wait is honored. Truncating a wait would repay only
     * part of the token debt and allow sustained throughput to exceed the cap,
     * especially at low configured rates.
     */
    suspend fun pace(bucket: TokenBucket?, bytes: Long) {
        if (bucket == null || bytes <= 0) return
        val initialWait = bucket.reserveNanos(bytes)

        if (logger?.isEnabled(LogLevel.DEBUG) == true) {
            logger.d(
                LogCategory.THROTTLE,
                "pace requested=$bytes rate=${bucket.configuredRate} burst=${bucket.configuredBurst} " +
                    "tokens=${bucket.availableTokens()} refill=${bucket.lastRefillAmount} waitNs=$initialWait",
            )
        }
        try {
            // Re-check periodically so a scheduled rule change (including
            // unlimited) affects an already-waiting flow without waiting out
            // the old reservation. The 100 ms poll bounds change latency.
            while (true) {
                val waitNanos = bucket.remainingWaitNanos()
                if (waitNanos <= 0L) break
                val sliceNanos = waitNanos.coerceAtMost(PACE_RECHECK_NANOS)
                val waitMillis = sliceNanos / 1_000_000L +
                    if (sliceNanos % 1_000_000L == 0L) 0L else 1L
                delay(waitMillis)
            }
        } catch (cancelled: CancellationException) {
            bucket.refund(bytes)
            throw cancelled
        }
    }

    fun releaseFlow(uid: Int) {
        // Buckets are shared per-uid across flows and retained so a later
        // schedule can update a connection that began while unlimited.
    }

    private fun refreshAll() {
        // Recompute the rate of every live bucket from the current policy.
        buckets.forEach { (k, bucket) ->
            val uid = (k ushr 1).toInt()
            val direction = if (k and 1L == 1L) Direction.DOWNLOAD else Direction.UPLOAD
            val cap = capFor(ruleEngine.policyForUid(uid), direction)
            if (cap == null) {
                // Keep the bucket reachable so active flows observe a later
                // re-enable without having to reconnect.
                bucket.updateRate(0)
            } else {
                bucket.updateRate(cap, burstFor(cap))
            }
        }
    }

    companion object {
        private const val PACE_RECHECK_NANOS = 100_000_000L

        private const val UNLIMITED_BURST_BYTES = 64 * 1024L

        /** ~250 ms of burst, floored so a full app-layer write never deadlocks. */
        fun burstFor(rateBytesPerSec: Long): Long =
            (rateBytesPerSec / 4).coerceAtLeast(64 * 1024)

    }
}
