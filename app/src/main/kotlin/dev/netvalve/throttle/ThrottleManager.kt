package dev.netvalve.throttle

import dev.netvalve.data.model.Direction
import dev.netvalve.log.LogCategory
import dev.netvalve.log.LogLevel
import dev.netvalve.log.Logger
import dev.netvalve.rules.EffectivePolicy
import dev.netvalve.rules.RuleEngine
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
 * Unlimited directions get a `null` bucket, and [pace] on `null` is a no-op — so
 * an unshaped flow pays essentially zero overhead.
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
     * @return the bucket to use for this flow's [direction], or null if that
     *         direction is unlimited under the current policy.
     */
    fun bucketFor(uid: Int, direction: Direction): TokenBucket? {
        val k = key(uid, direction)
        val cap = capFor(ruleEngine.policyForUid(uid), direction) ?: run {
            // Existing flows may still hold this bucket reference. Switch it to
            // unlimited before removing it so a rule removal takes effect live.
            buckets.remove(k)?.updateRate(0)
            return null
        }
        val k = key(uid, direction)
        val created = !buckets.containsKey(k)
        val bucket = buckets.getOrPut(k) { TokenBucket(capacityBytes = burstFor(cap), rateBytesPerSec = cap) }
        bucket.updateRate(cap, burstFor(cap))
        if (created) {
            logger?.i(
                LogCategory.THROTTLE,
                "bucket created dir=$direction rate=$cap B/s burst=${burstFor(cap)} B",
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
        val waitNanos = bucket.reserveNanos(bytes)

        if (logger?.isEnabled(LogLevel.DEBUG) == true) {
            logger.d(
                LogCategory.THROTTLE,
                "pace requested=$bytes rate=${bucket.configuredRate} burst=${bucket.configuredBurst} " +
                    "tokens=${bucket.availableTokens()} refill=${bucket.lastRefillAmount} waitNs=$waitNanos",
            )
        }
        if (waitNanos > 0) {
            // Round up without overflowing when waitNanos is near Long.MAX_VALUE.
            val waitMillis = waitNanos / 1_000_000L +
                if (waitNanos % 1_000_000L == 0L) 0L else 1L
            delay(waitMillis)
        }
    }

    fun releaseFlow(uid: Int) {
        // Buckets are shared per-uid across a uid's flows, so we keep them until
        // a rule refresh prunes unlimited ones. Nothing to do per-flow here.
    }

    private fun refreshAll() {
        // Recompute the rate of every live bucket from the current policy.
        buckets.forEach { (k, bucket) ->
            val uid = (k ushr 1).toInt()
            val direction = if (k and 1L == 1L) Direction.DOWNLOAD else Direction.UPLOAD
            val cap = capFor(ruleEngine.policyForUid(uid), direction)
            if (cap == null) {
                // Do not strand active flows on a detached, still-limited bucket.
                bucket.updateRate(0)
                buckets.remove(k, bucket)
            } else {
                bucket.updateRate(cap, burstFor(cap))
            }
        }
    }

    companion object {
        /** ~250 ms of burst, floored so a full app-layer write never deadlocks. */
        fun burstFor(rateBytesPerSec: Long): Long =
            (rateBytesPerSec / 4).coerceAtLeast(64 * 1024)

    }
}
