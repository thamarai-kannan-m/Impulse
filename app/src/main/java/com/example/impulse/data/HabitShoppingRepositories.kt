package com.example.impulse.data

import com.example.impulse.domain.Dates
import com.example.impulse.domain.HabitEngine
import com.example.impulse.domain.HabitSummary
import com.example.impulse.domain.StockCondition
import com.example.impulse.domain.StockForecast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class HabitRepository(
    private val db: LifeLoopDatabase,
    private val onChanged: () -> Unit,
) {
    private val dao = db.habitDao()

    val habits: Flow<List<Habit>> = dao.observeHabits()

    /** Summaries recomputed whenever data changes or the day rolls over. */
    val summaries: Flow<List<HabitSummary>> = combine(
        dao.observeHabits(),
        dao.observeLogsSince(Dates.today() - 400),
        clockFlow().map { Dates.today(it) }.distinctUntilChanged(),
    ) { habits, logs, today ->
        val byHabit = logs.groupBy { it.habitId }
        habits.map { HabitEngine.summarize(it, byHabit[it.id].orEmpty(), today) }
    }

    fun logs(habitId: Long) = dao.observeLogs(habitId)

    suspend fun snapshotSummaries(today: Long = Dates.today()): List<HabitSummary> {
        val logs = dao.allLogs().groupBy { it.habitId }
        return dao.allHabits().filter { !it.archived }.map { HabitEngine.summarize(it, logs[it.id].orEmpty(), today) }
    }

    suspend fun habit(id: Long) = dao.habit(id)

    suspend fun add(h: Habit): Long = dao.insertHabit(h).also { onChanged() }
    suspend fun update(h: Habit) { dao.updateHabit(h); onChanged() }
    suspend fun delete(id: Long) { dao.deleteHabit(id); onChanged() }
    suspend fun setArchived(id: Long, archived: Boolean) {
        dao.habit(id)?.let { dao.updateHabit(it.copy(archived = archived)) }; onChanged()
    }

    suspend fun setValue(habitId: Long, date: Long, value: Double?) {
        if (value == null || value == 0.0) dao.deleteLog(habitId, date)
        else {
            val existing = dao.log(habitId, date)
            dao.upsertLog(HabitLog(id = existing?.id ?: 0, habitId = habitId, date = date, value = value))
        }
        onChanged()
    }

    /** Primary tap action: toggles binary-like habits, adds one step to measurable ones. */
    suspend fun quickLog(habitId: Long, date: Long = Dates.today()) {
        val h = dao.habit(habitId) ?: return
        val current = dao.log(habitId, date)?.value ?: 0.0
        val next = when (h.type) {
            HabitType.BINARY, HabitType.FREQUENCY, HabitType.AVOIDANCE -> if (current >= 1.0) 0.0 else 1.0
            HabitType.QUANTITY -> current + stepFor(h)
            HabitType.DURATION -> current + stepFor(h)
        }
        setValue(habitId, date, next)
    }

    companion object {
        fun stepFor(h: Habit): Double = when (h.type) {
            HabitType.QUANTITY -> when {
                h.unit.equals("mL", true) -> 250.0
                h.target >= 1000 -> (h.target / 8).let { Math.round(it / 100.0) * 100.0 }.coerceAtLeast(1.0)
                h.target >= 10 -> (h.target / 4).let { Math.round(it).toDouble() }.coerceAtLeast(1.0)
                else -> 1.0
            }
            HabitType.DURATION -> if (h.target >= 30) 10.0 else 5.0
            else -> 1.0
        }
    }
}

class ShoppingRepository(
    private val db: LifeLoopDatabase,
    private val onChanged: () -> Unit,
) {
    private val dao = db.shoppingDao()

    val items: Flow<List<ShoppingListItem>> = dao.observe()

    suspend fun add(item: ShoppingListItem) { dao.insert(item); onChanged() }

    /** Adds an inventory item to the list once (no duplicates while it's still needed). */
    suspend fun addForItem(inv: InventoryItem, qty: Double? = null, suggested: Boolean = false): Boolean {
        if (dao.neededFor(inv.id) != null) return false
        dao.insert(ShoppingListItem(inventoryItemId = inv.id, name = inv.name, emoji = inv.emoji, quantity = qty, unit = inv.unit, suggested = suggested))
        onChanged()
        return true
    }

    suspend fun setPurchased(item: ShoppingListItem, purchased: Boolean) {
        dao.update(item.copy(state = if (purchased) ShoppingState.PURCHASED else ShoppingState.NEEDED, purchasedAt = if (purchased) System.currentTimeMillis() else null))
        onChanged()
    }

    suspend fun delete(item: ShoppingListItem) { dao.delete(item); onChanged() }
    suspend fun clearPurchased() { dao.clearPurchased(); onChanged() }

    companion object {
        /** Items that are low/out (or will be soon) and aren't on the list yet. */
        fun suggestions(forecasts: List<StockForecast>, list: List<ShoppingListItem>): List<StockForecast> {
            val listed = list.filter { it.state == ShoppingState.NEEDED }.mapNotNull { it.inventoryItemId }.toSet()
            return forecasts.filter { !it.item.archived && it.item.essential && it.item.id !in listed && it.stockCondition != StockCondition.IN_STOCK }
                .sortedWith(compareBy({ it.stockCondition != StockCondition.OUT_OF_STOCK }, { it.daysRemaining ?: 0.0 }))
        }
    }
}
