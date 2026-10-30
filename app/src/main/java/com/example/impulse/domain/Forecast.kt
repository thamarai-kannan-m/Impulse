package com.example.impulse.domain

import com.example.impulse.data.BatchStatus
import com.example.impulse.data.ConsumptionMode
import com.example.impulse.data.InventoryBatch
import com.example.impulse.data.InventoryItem
import kotlin.math.floor
import kotlin.math.max

enum class StockCondition { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

enum class ExpiryCondition { FRESH, EXPIRING_SOON, EXPIRES_TODAY, EXPIRED, NONE }

/** Expiry view of one active batch. */
data class BatchExpiry(
    val batch: InventoryBatch,
    val daysLeft: Long?,
    val condition: ExpiryCondition,
)

/**
 * Transparent, rule-based forecast for one inventory item.
 * Stock and expiry conditions are independent: milk can be LOW_STOCK and EXPIRED at once.
 */
data class StockForecast(
    val item: InventoryItem,
    /** Quantity recorded at the last stock check. */
    val recordedQty: Double,
    /** Best estimate right now (recorded minus projected usage since the last check). */
    val estimatedQty: Double,
    val isEstimated: Boolean,
    val dailyUsage: Double?,
    val daysRemaining: Double?,
    val depletionDate: Long?,           // epochDay
    val reorderDate: Long?,             // epochDay = depletion - lead days
    val stockCondition: StockCondition,
    val batches: List<BatchExpiry>,
    val expiryCondition: ExpiryCondition,
    /** Fill level 0..1 relative to the latest purchase size, for gauges. */
    val fillFraction: Float,
    val lastPurchaseQty: Double,
    val confidence: Confidence,
) {
    enum class Confidence(val label: String) { NONE("No estimate"), LOW("Rough estimate"), MEDIUM("Estimate"), HIGH("Learned from usage") }

    val needsAttention: Boolean
        get() = stockCondition != StockCondition.IN_STOCK ||
            expiryCondition == ExpiryCondition.EXPIRED ||
            expiryCondition == ExpiryCondition.EXPIRES_TODAY ||
            expiryCondition == ExpiryCondition.EXPIRING_SOON

    val nearestExpiry: BatchExpiry? get() = batches.filter { it.daysLeft != null }.minByOrNull { it.daysLeft!! }
}

object ForecastEngine {
    private const val EPS = 1e-6

    /** Daily usage for an item given its mode and active batches. */
    fun dailyUsage(item: InventoryItem, active: List<InventoryBatch>): Double? = when (item.consumptionMode) {
        ConsumptionMode.MANUAL -> null
        ConsumptionMode.USAGE -> item.dailyUsage ?: durationUsage(item, active)
        ConsumptionMode.DURATION -> durationUsage(item, active) ?: item.dailyUsage
    }

    private fun durationUsage(item: InventoryItem, active: List<InventoryBatch>): Double? {
        val latest = active.maxByOrNull { it.purchaseDate * 1_000_000 + it.id }
        val days = item.typicalDurationDays ?: latest?.expectedDurationDays
        val qty = latest?.quantity
        return if (days != null && days > 0 && qty != null && qty > 0) qty / days else null
    }

    fun forecast(
        item: InventoryItem,
        batches: List<InventoryBatch>,
        now: Long = System.currentTimeMillis(),
        today: Long = Dates.today(now),
    ): StockForecast {
        val active = batches.filter { it.status == BatchStatus.ACTIVE }
        val recorded = active.sumOf { it.remaining }
        val usage = dailyUsage(item, active)
        val elapsedDays = max(0.0, Dates.daysBetween(item.stockCheckedAt, now))
        val projecting = usage != null && usage > 0
        val estimated = if (projecting) max(0.0, recorded - usage!! * elapsedDays) else recorded

        val daysRemaining = if (projecting) estimated / usage!! else null
        val depletion = daysRemaining?.let { today + floor(it).toLong() }
        val reorder = depletion?.let { it - item.reorderLeadDays }

        val stock = when {
            estimated <= EPS -> StockCondition.OUT_OF_STOCK
            item.lowStockThreshold > 0 && estimated <= item.lowStockThreshold + EPS -> StockCondition.LOW_STOCK
            daysRemaining != null && daysRemaining <= item.reorderLeadDays -> StockCondition.LOW_STOCK
            else -> StockCondition.IN_STOCK
        }

        val batchExp = active.filter { it.remaining > EPS }.map { b ->
            val left = b.expiryDate?.let { it - today }
            BatchExpiry(b, left, expiryCondition(left, item.expiryWarnDays))
        }.sortedWith(compareBy(nullsLast()) { it.daysLeft })

        val worst = batchExp.map { it.condition }.minByOrNull { it.ordinalSeverity() } ?: ExpiryCondition.NONE

        val lastPurchase = active.maxByOrNull { it.purchaseDate * 1_000_000 + it.id }?.quantity
            ?: batches.maxByOrNull { it.purchaseDate * 1_000_000 + it.id }?.quantity ?: 0.0
        val capacity = max(lastPurchase, recorded)
        val fill = if (capacity > EPS) (estimated / capacity).toFloat().coerceIn(0f, 1f) else 0f

        val confidence = when {
            usage == null -> StockForecast.Confidence.NONE
            item.consumptionMode == ConsumptionMode.USAGE && item.usageSamples >= 3 -> StockForecast.Confidence.HIGH
            item.consumptionMode == ConsumptionMode.USAGE && item.usageSamples >= 1 -> StockForecast.Confidence.MEDIUM
            else -> StockForecast.Confidence.LOW
        }

        return StockForecast(
            item = item, recordedQty = recorded, estimatedQty = estimated,
            isEstimated = projecting && elapsedDays > 0.01, dailyUsage = usage,
            daysRemaining = daysRemaining, depletionDate = depletion, reorderDate = reorder,
            stockCondition = stock, batches = batchExp, expiryCondition = worst,
            fillFraction = fill, lastPurchaseQty = lastPurchase, confidence = confidence,
        )
    }

    fun expiryCondition(daysLeft: Long?, warnDays: Int): ExpiryCondition = when {
        daysLeft == null -> ExpiryCondition.NONE
        daysLeft < 0 -> ExpiryCondition.EXPIRED
        daysLeft == 0L -> ExpiryCondition.EXPIRES_TODAY
        daysLeft <= warnDays -> ExpiryCondition.EXPIRING_SOON
        else -> ExpiryCondition.FRESH
    }

    private fun ExpiryCondition.ordinalSeverity(): Int = when (this) {
        ExpiryCondition.EXPIRED -> 0
        ExpiryCondition.EXPIRES_TODAY -> 1
        ExpiryCondition.EXPIRING_SOON -> 2
        ExpiryCondition.FRESH -> 3
        ExpiryCondition.NONE -> 4
    }

    /**
     * Distributes a consumption of [amount] across active batches, first-expiring-first-out.
     * Returns the new remaining value per batch id.
     */
    fun allocateConsumption(active: List<InventoryBatch>, amount: Double): Map<Long, Double> {
        var left = amount
        val result = LinkedHashMap<Long, Double>()
        val ordered = active.sortedWith(compareBy<InventoryBatch>({ it.expiryDate ?: Long.MAX_VALUE }, { it.purchaseDate }, { it.id }))
        for (b in ordered) {
            if (left <= EPS) break
            val take = minOf(b.remaining, left)
            result[b.id] = b.remaining - take
            left -= take
        }
        return result
    }

    /**
     * Learns a new average daily usage from a stock observation: the user said they now have
     * [newQty] while [previousQty] was recorded [days] ago. Exponential smoothing keeps it stable.
     */
    fun learnUsage(oldUsage: Double?, samples: Int, previousQty: Double, newQty: Double, days: Double): Pair<Double?, Int> {
        val consumed = previousQty - newQty
        if (days < 0.5 || consumed <= 0) return oldUsage to samples
        val sample = consumed / days
        val alpha = if (samples == 0 || oldUsage == null) 1.0 else 0.5
        val next = if (oldUsage == null) sample else oldUsage * (1 - alpha) + sample * alpha
        return next to (samples + 1)
    }
}
