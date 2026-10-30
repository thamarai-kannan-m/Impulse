package com.example.impulse.data

import androidx.room.withTransaction
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ForecastEngine
import com.example.impulse.domain.StockForecast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlin.math.max

/** Emits the current time every [periodMs] so time-based projections stay fresh on screen. */
fun clockFlow(periodMs: Long = 60_000L): Flow<Long> = flow {
    while (true) { emit(System.currentTimeMillis()); delay(periodMs) }
}

data class NewBatch(
    val quantity: Double,
    val purchaseDate: Long = Dates.today(),
    val expiryDate: Long? = null,
    val expiryKind: ExpiryKind = ExpiryKind.BEST_BEFORE,
    val price: Double? = null,
    val durationDays: Int? = null,
)

class InventoryRepository(
    private val db: LifeLoopDatabase,
    private val onChanged: () -> Unit,
) {
    private val dao = db.inventoryDao()
    private val shopping = db.shoppingDao()

    val items: Flow<List<InventoryItem>> = dao.observeItems()
    val archived: Flow<List<InventoryItem>> = dao.observeArchived()

    val forecasts: Flow<List<StockForecast>> =
        combine(dao.observeItems(), dao.observeAllBatches(), clockFlow()) { items, batches, now ->
            val byItem = batches.groupBy { it.itemId }
            items.map { ForecastEngine.forecast(it, byItem[it.id].orEmpty(), now) }
        }

    fun forecast(itemId: Long): Flow<StockForecast?> =
        combine(dao.observeItem(itemId), dao.observeBatches(itemId), clockFlow()) { item, batches, now ->
            item?.let { ForecastEngine.forecast(it, batches, now) }
        }

    fun batches(itemId: Long) = dao.observeBatches(itemId)
    fun transactions(itemId: Long) = dao.observeTransactions(itemId)
    fun transactionsSince(since: Long) = dao.observeTransactionsSince(since)

    suspend fun snapshotForecasts(now: Long = System.currentTimeMillis()): List<StockForecast> {
        val batches = dao.allBatches().groupBy { it.itemId }
        return dao.allItems().filter { !it.archived }.map { ForecastEngine.forecast(it, batches[it.id].orEmpty(), now) }
    }

    suspend fun item(id: Long) = dao.item(id)

    // ------------------------------------------------------------------ create / edit

    suspend fun addItem(item: InventoryItem, first: NewBatch?): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val duration = first?.durationDays ?: item.typicalDurationDays
        val usage = if (first != null && duration != null && duration > 0) first.quantity / duration else item.dailyUsage
        val id = dao.insertItem(item.copy(stockCheckedAt = now, dailyUsage = usage, typicalDurationDays = duration))
        if (first != null && first.quantity > 0) insertPurchase(id, first, now)
        id
    }.also { onChanged() }

    suspend fun updateItem(item: InventoryItem) {
        dao.updateItem(item); onChanged()
    }

    suspend fun setArchived(itemId: Long, archived: Boolean) {
        dao.item(itemId)?.let { dao.updateItem(it.copy(archived = archived)) }; onChanged()
    }

    suspend fun deleteItem(itemId: Long) {
        dao.item(itemId)?.let { dao.deleteItem(it) }; onChanged()
    }

    private suspend fun insertPurchase(itemId: Long, b: NewBatch, now: Long): Long {
        val batchId = dao.insertBatch(
            InventoryBatch(
                itemId = itemId, purchaseDate = b.purchaseDate, quantity = b.quantity, remaining = b.quantity,
                expiryDate = b.expiryDate, expiryKind = b.expiryKind, price = b.price, expectedDurationDays = b.durationDays,
            )
        )
        dao.insertTx(StockTransaction(itemId = itemId, batchId = batchId, quantityDelta = b.quantity, reason = TxReason.PURCHASE, timestamp = now, price = b.price))
        return batchId
    }

    // ------------------------------------------------------------------ stock movements

    /**
     * Turns the time-based estimate into recorded stock (used before restocking/discarding so a
     * new purchase doesn't "reset" the consumption that has probably happened).
     */
    private suspend fun commitProjection(item: InventoryItem, now: Long): InventoryItem {
        val batches = dao.batches(item.id)
        val f = ForecastEngine.forecast(item, batches, now)
        val used = f.recordedQty - f.estimatedQty
        if (used > 1e-6) {
            val active = batches.filter { it.status == BatchStatus.ACTIVE }
            applyAllocation(active, ForecastEngine.allocateConsumption(active, used), now)
            dao.insertTx(StockTransaction(itemId = item.id, quantityDelta = -used, reason = TxReason.CONSUME, timestamp = now, note = "Estimated usage"))
        }
        val updated = item.copy(stockCheckedAt = now)
        dao.updateItem(updated)
        return updated
    }

    private suspend fun applyAllocation(active: List<InventoryBatch>, alloc: Map<Long, Double>, now: Long) {
        val changed = active.filter { it.id in alloc }.map { b ->
            val rem = max(0.0, alloc.getValue(b.id))
            if (rem <= 1e-6) b.copy(remaining = 0.0, status = BatchStatus.CONSUMED, closedAt = now) else b.copy(remaining = rem)
        }
        if (changed.isNotEmpty()) dao.updateBatches(changed)
    }

    suspend fun restock(itemId: Long, batch: NewBatch) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val item = dao.item(itemId) ?: return@withTransaction
            val committed = commitProjection(item, now)
            insertPurchase(itemId, batch, now)
            val duration = batch.durationDays ?: committed.typicalDurationDays
            val updated = if (committed.consumptionMode == ConsumptionMode.DURATION && duration != null && duration > 0)
                committed.copy(dailyUsage = batch.quantity / duration, typicalDurationDays = duration) else committed
            dao.updateItem(updated.copy(archived = false, stockCheckedAt = now))
            shopping.neededFor(itemId)?.let { shopping.update(it.copy(state = ShoppingState.PURCHASED, purchasedAt = now)) }
        }
        onChanged()
    }

    /**
     * The user tells us how much is left right now. In USAGE mode this observation also teaches
     * the forecast how fast the item is actually consumed.
     */
    suspend fun setRemaining(itemId: Long, newQty: Double, reason: TxReason = TxReason.ADJUST, note: String = "") {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val item = dao.item(itemId) ?: return@withTransaction
            val batches = dao.batches(itemId)
            val active = batches.filter { it.status == BatchStatus.ACTIVE }
            val recorded = active.sumOf { it.remaining }
            val target = max(0.0, newQty)
            val days = Dates.daysBetween(item.stockCheckedAt, now)

            var learnedUsage = item.dailyUsage
            var samples = item.usageSamples
            if (item.consumptionMode == ConsumptionMode.USAGE) {
                val (u, s) = ForecastEngine.learnUsage(item.dailyUsage, item.usageSamples, recorded, target, days)
                learnedUsage = u; samples = s
            }

            val delta = target - recorded
            if (delta < 0) {
                applyAllocation(active, ForecastEngine.allocateConsumption(active, -delta), now)
            } else if (delta > 1e-6) {
                val latest = active.maxByOrNull { it.purchaseDate * 1_000_000 + it.id }
                if (latest != null) dao.updateBatch(latest.copy(remaining = latest.remaining + delta))
                else dao.insertBatch(InventoryBatch(itemId = itemId, purchaseDate = Dates.today(now), quantity = delta, remaining = delta))
            }
            if (kotlin.math.abs(delta) > 1e-6) {
                dao.insertTx(StockTransaction(itemId = itemId, quantityDelta = delta, reason = if (delta < 0) reason else TxReason.ADJUST, timestamp = now, note = note))
            }
            dao.updateItem(item.copy(stockCheckedAt = now, dailyUsage = learnedUsage, usageSamples = samples))
        }
        onChanged()
    }

    /** "Used 0.5 kg" — subtracts from the current best estimate. */
    suspend fun useAmount(itemId: Long, amount: Double) {
        val item = dao.item(itemId) ?: return
        val f = ForecastEngine.forecast(item, dao.batches(itemId))
        setRemaining(itemId, f.estimatedQty - amount, TxReason.CONSUME)
    }

    suspend fun closeBatch(batchId: Long, discarded: Boolean) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val b0 = dao.batch(batchId) ?: return@withTransaction
            val item = dao.item(b0.itemId) ?: return@withTransaction
            commitProjection(item, now)
            val b = dao.batch(batchId) ?: return@withTransaction
            if (b.status == BatchStatus.ACTIVE) {
                dao.updateBatch(b.copy(remaining = 0.0, status = if (discarded) BatchStatus.DISCARDED else BatchStatus.CONSUMED, closedAt = now))
                if (b.remaining > 1e-6) dao.insertTx(
                    StockTransaction(
                        itemId = b.itemId, batchId = b.id, quantityDelta = -b.remaining,
                        reason = if (discarded) TxReason.DISCARD else TxReason.CONSUME, timestamp = now,
                        price = if (discarded && b.price != null && b.quantity > 0) b.price * b.remaining / b.quantity else null,
                    )
                )
            }
        }
        onChanged()
    }

    suspend fun updateBatchExpiry(batchId: Long, expiry: Long?, kind: ExpiryKind) {
        dao.batch(batchId)?.let { dao.updateBatch(it.copy(expiryDate = expiry, expiryKind = kind)) }
        onChanged()
    }
}
