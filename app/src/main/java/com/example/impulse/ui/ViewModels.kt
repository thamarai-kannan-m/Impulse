package com.example.impulse.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.impulse.AppContainer
import com.example.impulse.LifeLoopApp
import com.example.impulse.data.Habit
import com.example.impulse.data.InventoryBatch
import com.example.impulse.data.InventoryItem
import com.example.impulse.data.ItemCategory
import com.example.impulse.data.NewBatch
import com.example.impulse.data.OccurrenceStatus
import com.example.impulse.data.OccurrenceWithTask
import com.example.impulse.data.ReminderSource
import com.example.impulse.data.ReminderState
import com.example.impulse.data.SampleData
import com.example.impulse.data.ShoppingListItem
import com.example.impulse.data.ShoppingRepository
import com.example.impulse.data.StockTransaction
import com.example.impulse.data.Task
import com.example.impulse.data.TxReason
import com.example.impulse.data.UserSettings
import com.example.impulse.data.clockFlow
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ExpiryCondition
import com.example.impulse.domain.HabitSummary
import com.example.impulse.domain.StockCondition
import com.example.impulse.domain.StockForecast
import com.example.impulse.domain.fmtQty
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.abs

@Composable
inline fun <reified VM : ViewModel> loopViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as LifeLoopApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Today's epochDay as a flow that ticks over at midnight. */
fun todayFlow() = clockFlow().map { Dates.today(it) }.distinctUntilChanged()

fun <T> kotlinx.coroutines.flow.Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

/** Shared one-tap actions available from any screen. */
open class LoopViewModel(val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = c.settings.settings.map<UserSettings, UserSettings?> { it }.stateIn(this, null)

    protected fun io(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun complete(occId: Long) = io { c.tasks.complete(occId) }
    fun reopen(occId: Long) = io { c.tasks.reopen(occId) }
    fun start(occId: Long) = io { c.tasks.setStatus(occId, OccurrenceStatus.IN_PROGRESS) }
    fun skip(occId: Long) = io { c.tasks.setStatus(occId, OccurrenceStatus.SKIPPED) }
    fun cancelOccurrence(occId: Long) = io { c.tasks.setStatus(occId, OccurrenceStatus.CANCELLED) }
    fun postpone(occId: Long, toDay: Long) = io { c.tasks.postpone(occId, toDay) }
    fun deleteSeries(taskId: Long) = io { c.tasks.deleteSeries(taskId) }

    fun quickLogHabit(id: Long) = io { c.habits.quickLog(id) }
    fun setHabitValue(id: Long, date: Long, value: Double?) = io { c.habits.setValue(id, date, value) }

    fun addToShopping(item: InventoryItem, qty: Double? = null) = io { c.shopping.addForItem(item, qty) }
    fun closeBatch(batchId: Long, discarded: Boolean) = io { c.inventory.closeBatch(batchId, discarded) }
    fun setRemaining(itemId: Long, qty: Double) = io { c.inventory.setRemaining(itemId, qty) }
    fun useAmount(itemId: Long, qty: Double) = io { c.inventory.useAmount(itemId, qty) }
    fun restock(itemId: Long, batch: NewBatch) = io { c.inventory.restock(itemId, batch) }

    fun acknowledge(alert: DashboardAlert) = io { c.reminders.acknowledge(alert.ackKeys, alert.source, alert.sourceId) }
}

// ====================================================================== TODAY

enum class AlertKind { EXPIRED, EXPIRES_TODAY, EXPIRING, OUT_OF_STOCK, LOW_STOCK }

data class DashboardAlert(
    val id: String,
    val kind: AlertKind,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val item: InventoryItem,
    val batch: InventoryBatch?,
    val ackKeys: List<String>,
    val source: ReminderSource,
    val sourceId: Long,
)

data class TodayState(
    val loading: Boolean = true,
    val today: Long = Dates.today(),
    val userName: String = "",
    val overdue: List<OccurrenceWithTask> = emptyList(),
    val pending: List<OccurrenceWithTask> = emptyList(),
    val todayTasks: List<OccurrenceWithTask> = emptyList(),
    val upcoming: List<OccurrenceWithTask> = emptyList(),
    val alerts: List<DashboardAlert> = emptyList(),
    val habits: List<HabitSummary> = emptyList(),
    val pendingCount: Int = 0,
    val routinesDue: Int = 0,
    val lowStockCount: Int = 0,
    val expiryCount: Int = 0,
    val doneToday: Int = 0,
    val totalToday: Int = 0,
    val shoppingCount: Int = 0,
) {
    val progress: Float get() = if (totalToday == 0) 0f else doneToday.toFloat() / totalToday
    val isEmpty: Boolean get() = overdue.isEmpty() && pending.isEmpty() && todayTasks.isEmpty() && alerts.isEmpty() && habits.isEmpty() && upcoming.isEmpty()
}

object AlertBuilder {
    fun build(forecasts: List<StockForecast>, acknowledged: Set<String>, today: Long): List<DashboardAlert> {
        val out = ArrayList<DashboardAlert>()
        for (f in forecasts) {
            val item = f.item
            for (be in f.batches) {
                val exp = be.batch.expiryDate ?: continue
                val kind = when (be.condition) {
                    ExpiryCondition.EXPIRED -> AlertKind.EXPIRED
                    ExpiryCondition.EXPIRES_TODAY -> AlertKind.EXPIRES_TODAY
                    ExpiryCondition.EXPIRING_SOON -> AlertKind.EXPIRING
                    else -> null
                } ?: continue
                val keys = listOf("expiry:${be.batch.id}:warn:$exp", "expiry:${be.batch.id}:day:$exp")
                if (keys.all { it in acknowledged }) continue
                val est = be.batch.expiryKind == com.example.impulse.data.ExpiryKind.ESTIMATED
                val title = when (kind) {
                    AlertKind.EXPIRED -> if (est) "${item.name} may have spoiled" else "${item.name} is past its date"
                    AlertKind.EXPIRES_TODAY -> if (est) "${item.name} — freshness ends today" else "${item.name} expires today"
                    else -> if (est) "${item.name} may spoil soon" else "${item.name} expires ${Dates.relative(exp, today).lowercase()}"
                }
                out += DashboardAlert(
                    id = "exp-${be.batch.id}", kind = kind, emoji = item.emoji, title = title,
                    subtitle = "${be.batch.remaining.fmtQty()} ${item.unit.label} · ${be.batch.expiryKind.label} ${Dates.formatShort(exp)}",
                    item = item, batch = be.batch, ackKeys = keys, source = ReminderSource.EXPIRY, sourceId = be.batch.id,
                )
            }
            if (f.stockCondition != StockCondition.IN_STOCK) {
                val cycle = f.batches.maxOfOrNull { it.batch.id } ?: 0L
                val keys = listOf("stock:${item.id}:low:$cycle", "stock:${item.id}:out:$cycle")
                if (keys.any { it in acknowledged }) continue
                val out0 = f.stockCondition == StockCondition.OUT_OF_STOCK
                val days = f.daysRemaining
                out += DashboardAlert(
                    id = "stock-${item.id}", kind = if (out0) AlertKind.OUT_OF_STOCK else AlertKind.LOW_STOCK, emoji = item.emoji,
                    title = when {
                        out0 -> "${item.name} is probably out"
                        days != null -> "${item.name} may run out in ${days.toInt()} day${if (days.toInt() == 1) "" else "s"}"
                        else -> "${item.name} is running low"
                    },
                    subtitle = "~${f.estimatedQty.fmtQty()} ${item.unit.label} left${if (f.isEstimated) " (estimate)" else ""}",
                    item = item, batch = null, ackKeys = keys, source = ReminderSource.STOCK, sourceId = item.id,
                )
            }
        }
        return out.sortedBy { it.kind.ordinal }
    }
}

class TodayViewModel(c: AppContainer) : LoopViewModel(c) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val occurrences = todayFlow().flatMapLatest { t -> c.tasks.window(t, t + 7) }

    private val acknowledged = c.db.reminderDao().observeAll()
        .map { list -> list.filter { it.state == ReminderState.ACKNOWLEDGED }.map { it.key }.toSet() }

    val state: StateFlow<TodayState> = combine(
        combine(occurrences, todayFlow()) { o, t -> o to t },
        c.inventory.forecasts,
        c.habits.summaries,
        combine(acknowledged, c.shopping.items) { a, s -> a to s },
        c.settings.settings,
    ) { (occ, today), forecasts, habits, (ack, shopping), settings ->
        val open = occ.filter { it.occurrence.status == OccurrenceStatus.PENDING || it.occurrence.status == OccurrenceStatus.IN_PROGRESS }
        val overdue = open.filter { it.task.hasDueDate && it.occurrence.dueDate < today }
        val pending = open.filter { !it.task.hasDueDate }
        val todays = occ.filter { it.task.hasDueDate && it.occurrence.dueDate == today && it.occurrence.status != OccurrenceStatus.CANCELLED }
            .sortedWith(compareBy({ it.occurrence.status == OccurrenceStatus.COMPLETED || it.occurrence.status == OccurrenceStatus.SKIPPED }, { it.occurrence.dueTime ?: 9999 }, { -it.task.priority.weight }))
        val upcoming = open.filter { it.task.hasDueDate && it.occurrence.dueDate in (today + 1)..(today + 7) }
        val alerts = AlertBuilder.build(forecasts, ack, today)
        val habitsToday = habits.filter { it.scheduledToday }
        val tasksDone = todays.count { it.occurrence.status == OccurrenceStatus.COMPLETED }
        TodayState(
            loading = false, today = today, userName = settings.userName,
            overdue = overdue.sortedWith(compareBy({ -it.task.priority.weight }, { it.occurrence.dueDate })),
            pending = pending, todayTasks = todays, upcoming = upcoming, alerts = alerts, habits = habitsToday,
            pendingCount = overdue.size + pending.size + todays.count { it.occurrence.status == OccurrenceStatus.PENDING || it.occurrence.status == OccurrenceStatus.IN_PROGRESS },
            routinesDue = todays.count { it.task.isRecurring && it.occurrence.status != OccurrenceStatus.COMPLETED && it.occurrence.status != OccurrenceStatus.SKIPPED },
            lowStockCount = forecasts.count { it.stockCondition != StockCondition.IN_STOCK },
            expiryCount = forecasts.sumOf { f -> f.batches.count { it.condition == ExpiryCondition.EXPIRED || it.condition == ExpiryCondition.EXPIRES_TODAY || it.condition == ExpiryCondition.EXPIRING_SOON } },
            doneToday = tasksDone + habitsToday.count { it.doneToday },
            totalToday = todays.count { it.occurrence.status != OccurrenceStatus.SKIPPED } + habitsToday.size,
            shoppingCount = shopping.count { it.state == com.example.impulse.data.ShoppingState.NEEDED },
        )
    }.stateIn(this, TodayState())

    fun loadSample() = io { SampleData.seed(c.db); c.reminders.requestReconcile() }
}

// ====================================================================== INVENTORY

data class InventoryState(
    val loading: Boolean = true,
    val forecasts: List<StockForecast> = emptyList(),
    val archived: List<InventoryItem> = emptyList(),
    val shopping: List<ShoppingListItem> = emptyList(),
    val suggestions: List<StockForecast> = emptyList(),
)

class InventoryViewModel(c: AppContainer) : LoopViewModel(c) {
    val state: StateFlow<InventoryState> = combine(c.inventory.forecasts, c.inventory.archived, c.shopping.items) { f, a, s ->
        InventoryState(false, f, a, s, ShoppingRepository.suggestions(f, s))
    }.stateIn(this, InventoryState())

    fun addShopping(name: String, qty: Double?, unit: com.example.impulse.data.StockUnit) = io {
        val match = state.value.forecasts.firstOrNull { it.item.name.equals(name.trim(), ignoreCase = true) }?.item
        if (match != null) c.shopping.addForItem(match, qty)
        else c.shopping.add(ShoppingListItem(name = name.trim(), quantity = qty, unit = unit, emoji = "🛒"))
    }
    fun addAllSuggestions() = io { state.value.suggestions.forEach { c.shopping.addForItem(it.item, it.lastPurchaseQty.takeIf { q -> q > 0 }, suggested = true) } }
    fun togglePurchased(item: ShoppingListItem) = io { c.shopping.setPurchased(item, item.state == com.example.impulse.data.ShoppingState.NEEDED) }
    fun deleteShopping(item: ShoppingListItem) = io { c.shopping.delete(item) }
    fun clearPurchased() = io { c.shopping.clearPurchased() }
    fun unarchive(id: Long) = io { c.inventory.setArchived(id, false) }
}

class ItemDetailViewModel(c: AppContainer, val itemId: Long) : LoopViewModel(c) {
    val forecast: StateFlow<StockForecast?> = c.inventory.forecast(itemId).stateIn(this, null)
    val batches: StateFlow<List<InventoryBatch>> = c.inventory.batches(itemId).stateIn(this, emptyList())
    val transactions: StateFlow<List<StockTransaction>> = c.inventory.transactions(itemId).stateIn(this, emptyList())

    fun archive() = io { c.inventory.setArchived(itemId, true) }
    fun delete() = io { c.inventory.deleteItem(itemId) }
    fun updateExpiry(batchId: Long, day: Long?, kind: com.example.impulse.data.ExpiryKind) = io { c.inventory.updateBatchExpiry(batchId, day, kind) }
}

class ItemEditorViewModel(c: AppContainer, val itemId: Long?) : LoopViewModel(c) {
    suspend fun load(): InventoryItem? = itemId?.let { c.inventory.item(it) }
    fun save(item: InventoryItem, first: NewBatch?, done: (Long) -> Unit) = io {
        if (item.id == 0L) done(c.inventory.addItem(item, first))
        else { c.inventory.updateItem(item); done(item.id) }
    }
}

// ====================================================================== TASKS

data class TasksState(
    val loading: Boolean = true,
    val today: Long = Dates.today(),
    val occurrences: List<OccurrenceWithTask> = emptyList(),
    val tasks: List<Task> = emptyList(),
)

class TasksViewModel(c: AppContainer) : LoopViewModel(c) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TasksState> = todayFlow().flatMapLatest { today ->
        combine(c.tasks.window(today - 7, today + 30), c.tasks.tasks) { occ, tasks -> TasksState(false, today, occ, tasks) }
    }.stateIn(this, TasksState())

    fun archiveTask(id: Long) = io { c.tasks.setArchived(id, true) }
}

class TaskEditorViewModel(c: AppContainer, val taskId: Long?) : LoopViewModel(c) {
    suspend fun load(): Task? = taskId?.let { c.tasks.task(it) }
    fun save(task: Task, done: () -> Unit) = io {
        if (task.id == 0L) c.tasks.addTask(task) else c.tasks.updateTask(task)
        done()
    }
}

// ====================================================================== HABITS

class HabitsViewModel(c: AppContainer) : LoopViewModel(c) {
    val summaries: StateFlow<List<HabitSummary>?> = c.habits.summaries.map<List<HabitSummary>, List<HabitSummary>?> { it }.stateIn(this, null)
    fun logsFor(id: Long) = c.habits.logs(id)
    fun archive(id: Long) = io { c.habits.setArchived(id, true) }
    fun delete(id: Long) = io { c.habits.delete(id) }
}

class HabitEditorViewModel(c: AppContainer, val habitId: Long?) : LoopViewModel(c) {
    suspend fun load(): Habit? = habitId?.let { c.habits.habit(it) }
    fun save(h: Habit, done: () -> Unit) = io { if (h.id == 0L) c.habits.add(h) else c.habits.update(h); done() }
}

// ====================================================================== INSIGHTS

data class DayBar(val label: String, val value: Float, val isToday: Boolean)
data class CategorySpend(val category: ItemCategory, val amount: Double)
data class ConsumedItem(val item: InventoryItem, val qty: Double)

data class InsightsState(
    val loading: Boolean = true,
    val weekBars: List<DayBar> = emptyList(),
    val completed7: Int = 0,
    val onTimeRate: Float = 0f,
    val completionRate: Float = 0f,
    val missed30: Int = 0,
    val habitRate: Float = 0f,
    val topStreaks: List<HabitSummary> = emptyList(),
    val spend30: Double = 0.0,
    val spendByCategory: List<CategorySpend> = emptyList(),
    val discardedCount: Int = 0,
    val discardedValue: Double = 0.0,
    val topConsumed: List<ConsumedItem> = emptyList(),
    val itemsTracked: Int = 0,
    val recurringCount: Int = 0,
    val currency: String = "₹",
)

class InsightsViewModel(c: AppContainer) : LoopViewModel(c) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsState> = todayFlow().flatMapLatest { today ->
        val since = Dates.toMillis(today - 29, 0)
        combine(
            c.tasks.range(today - 29, today),
            c.habits.summaries,
            c.inventory.transactionsSince(since),
            c.inventory.forecasts,
            combine(c.tasks.tasks, c.settings.settings) { t, s -> t to s },
        ) { occ, habits, txs, forecasts, (tasks, settings) ->
            val zone = Dates.zone()
            val completed = occ.filter { it.occurrence.status == OccurrenceStatus.COMPLETED }
            val bars = (today - 6..today).map { d ->
                val count = completed.count { it.occurrence.completedAt?.let { ms -> Dates.epochDayOf(ms, zone) } == d }
                DayBar(Dates.dayNarrow(Dates.date(d).dayOfWeek), count.toFloat(), d == today)
            }
            val withDue = completed.filter { it.task.hasDueDate }
            val onTime = withDue.count { o -> o.occurrence.completedAt?.let { Dates.epochDayOf(it, zone) <= o.occurrence.dueDate } == true }
            val past = occ.filter { it.task.hasDueDate && it.occurrence.dueDate < today && it.occurrence.status != OccurrenceStatus.CANCELLED }
            val missed = past.count { it.occurrence.status == OccurrenceStatus.PENDING || it.occurrence.status == OccurrenceStatus.SKIPPED }
            val items = forecasts.associateBy { it.item.id }
            val purchases = txs.filter { it.reason == TxReason.PURCHASE && it.price != null }
            val byCat = purchases.groupBy { items[it.itemId]?.item?.category ?: ItemCategory.OTHER }
                .map { (cat, l) -> CategorySpend(cat, l.sumOf { it.price ?: 0.0 }) }.sortedByDescending { it.amount }
            val discards = txs.filter { it.reason == TxReason.DISCARD }
            val consumed = txs.filter { it.reason == TxReason.CONSUME || (it.reason == TxReason.ADJUST && it.quantityDelta < 0) }
                .groupBy { it.itemId }.mapNotNull { (id, l) -> items[id]?.item?.let { ConsumedItem(it, l.sumOf { t -> abs(t.quantityDelta) }) } }
                .sortedByDescending { it.qty / (it.item.dailyUsage ?: 1.0) }.take(5)
            InsightsState(
                loading = false, weekBars = bars, completed7 = bars.sumOf { it.value.toInt() },
                onTimeRate = if (withDue.isEmpty()) 0f else onTime.toFloat() / withDue.size,
                completionRate = if (past.isEmpty()) 0f else past.count { it.occurrence.status == OccurrenceStatus.COMPLETED }.toFloat() / past.size,
                missed30 = missed,
                habitRate = if (habits.isEmpty()) 0f else habits.map { it.rate30 }.average().toFloat(),
                topStreaks = habits.sortedByDescending { it.currentStreak }.take(3),
                spend30 = purchases.sumOf { it.price ?: 0.0 }, spendByCategory = byCat,
                discardedCount = discards.size, discardedValue = discards.sumOf { it.price ?: 0.0 },
                topConsumed = consumed, itemsTracked = forecasts.size,
                recurringCount = tasks.count { it.isRecurring }, currency = settings.currency,
            )
        }
    }.stateIn(this, InsightsState())
}

// ====================================================================== SETTINGS

class SettingsViewModel(c: AppContainer) : LoopViewModel(c) {
    fun update(transform: (UserSettings) -> UserSettings) = io { c.settings.update(transform); c.reminders.requestReconcile() }
    suspend fun exportJson(): String = c.backup.export()
    suspend fun importJson(json: String) { c.backup.import(json); c.reminders.requestReconcile() }
    fun loadSample() = io { SampleData.seed(c.db); c.reminders.requestReconcile() }
    fun completeOnboarding(name: String, sample: Boolean) = io {
        if (sample) SampleData.seed(c.db)
        c.settings.update { it.copy(onboarded = true, userName = name.trim()) }
        c.reminders.requestReconcile()
    }
    fun testReminderNow() = io { c.reminders.reconcile() }
}
