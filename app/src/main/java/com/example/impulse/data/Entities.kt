package com.example.impulse.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Date/time semantics used throughout LifeLoop
 * --------------------------------------------
 *  - Calendar dates are stored as `epochDay` (Long, days since 1970-01-01) and are
 *    "floating" local dates: Wednesday stays Wednesday wherever the user travels.
 *  - Times of day are stored as minutes after local midnight (0..1439).
 *  - Instants (created/completed timestamps, reminder triggers) are epoch millis UTC.
 *  - Trigger instants are always derived from (date, minutes) using the device's
 *    *current* zone, and every alarm is recomputed on TIMEZONE_CHANGED.
 */

// ---------------------------------------------------------------- enums

enum class ItemCategory(val label: String, val emoji: String) {
    GROCERY("Grocery", "🛒"),
    VEGETABLE("Vegetables", "🥬"),
    FRUIT("Fruits", "🍎"),
    DAIRY("Dairy", "🥛"),
    BAKERY("Bakery", "🍞"),
    BEVERAGE("Beverages", "☕"),
    HOUSEHOLD("Household", "🧴"),
    PERSONAL_CARE("Personal care", "🪥"),
    OTHER("Other", "📦"),
}

enum class StockUnit(val label: String, val step: Double, val base: String, val toBase: Double) {
    KG("kg", 0.25, "g", 1000.0),
    G("g", 50.0, "g", 1.0),
    L("L", 0.25, "mL", 1000.0),
    ML("mL", 100.0, "mL", 1.0),
    PCS("pcs", 1.0, "pcs", 1.0),
    PACK("pack", 1.0, "pack", 1.0),
    DOZEN("dozen", 1.0, "pcs", 12.0),
    ;

    /** Converts [value] in this unit into [target]; returns null when the units are incompatible. */
    fun convert(value: Double, target: StockUnit): Double? =
        if (base != target.base) null else value * toBase / target.toBase
}

enum class StorageLocation(val label: String, val emoji: String) {
    FRIDGE("Fridge", "🧊"),
    FREEZER("Freezer", "❄️"),
    KITCHEN("Kitchen", "🍳"),
    PANTRY("Pantry", "🥫"),
    CABINET("Cabinet", "🗄️"),
    BATHROOM("Bathroom", "🛁"),
    OTHER("Other", "📍"),
}

enum class ConsumptionMode(val label: String, val description: String) {
    DURATION("Duration", "I know roughly how long a pack lasts"),
    USAGE("Learn from usage", "Learn my real usage from stock updates"),
    MANUAL("Manual", "I'll update the quantity myself"),
}

enum class ExpiryKind(val label: String) {
    USE_BY("Use by"),
    BEST_BEFORE("Best before"),
    ESTIMATED("Est. freshness"),
}

enum class BatchStatus { ACTIVE, CONSUMED, DISCARDED }

enum class TxReason(val label: String) {
    PURCHASE("Purchased"),
    CONSUME("Used"),
    ADJUST("Stock check"),
    DISCARD("Discarded"),
}

enum class TaskCategory(val label: String, val emoji: String) {
    CLEANING("Cleaning", "🧹"),
    LAUNDRY("Laundry", "👕"),
    KITCHEN("Kitchen", "🍽️"),
    SHOPPING("Shopping", "🛍️"),
    BILLS("Bills", "💡"),
    MAINTENANCE("Maintenance", "🔧"),
    HEALTH("Health", "💊"),
    PERSONAL("Personal", "✨"),
    WORK("Work", "💼"),
    OTHER("Other", "📌"),
}

enum class Priority(val label: String, val weight: Int) {
    LOW("Low", 0), MEDIUM("Medium", 1), HIGH("High", 2), URGENT("Urgent", 3)
}

enum class RepeatType(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    EVERY_N_DAYS("Every N days"),
    AFTER_COMPLETION("After completion"),
}

enum class MissedPolicy(val label: String, val description: String) {
    KEEP_OVERDUE("Keep as overdue", "A missed occurrence stays pending until you deal with it"),
    AUTO_SKIP("Auto-skip", "When the next one comes due, the missed one is skipped automatically"),
}

enum class OccurrenceStatus { PENDING, IN_PROGRESS, COMPLETED, SKIPPED, CANCELLED }

enum class HabitType(val label: String, val description: String) {
    BINARY("Yes / No", "Done or not done"),
    QUANTITY("Amount", "Reach a daily amount, e.g. 2000 mL water"),
    DURATION("Duration", "Spend minutes on it, e.g. 20 min reading"),
    FREQUENCY("Times per week", "Do it N times each week"),
    AVOIDANCE("Avoid", "Stay away from something each day"),
}

enum class ReminderSource { TASK, STOCK, EXPIRY, HABIT, DIGEST }

enum class ReminderState { FIRED, SNOOZED, ACKNOWLEDGED }

enum class ShoppingState { NEEDED, PURCHASED }

// ---------------------------------------------------------------- inventory

@Entity(tableName = "inventory_items", indices = [Index("name")])
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📦",
    val category: ItemCategory = ItemCategory.GROCERY,
    val unit: StockUnit = StockUnit.PCS,
    val location: StorageLocation = StorageLocation.KITCHEN,
    /** Remaining quantity at or below which the item counts as low stock. */
    val lowStockThreshold: Double = 0.0,
    /** Remind this many days before the estimated depletion date. */
    val reorderLeadDays: Int = 5,
    /** Warn this many days before a batch's expiry date. */
    val expiryWarnDays: Int = 2,
    val consumptionMode: ConsumptionMode = ConsumptionMode.DURATION,
    /** Typical days one purchase lasts (duration mode / default for new batches). */
    val typicalDurationDays: Int? = null,
    /** Typical freshness window in days for perishables (used to estimate expiry). */
    val freshnessDays: Int? = null,
    /** Learned or derived average daily usage, in [unit] per day. */
    val dailyUsage: Double? = null,
    /** Number of observations behind [dailyUsage] (confidence indicator). */
    val usageSamples: Int = 0,
    /** Millis of the last time the user confirmed / changed stock. Projections run from here. */
    val stockCheckedAt: Long = System.currentTimeMillis(),
    /** Essentials are suggested for the shopping list automatically when low. */
    val essential: Boolean = true,
    val archived: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "inventory_batches",
    foreignKeys = [ForeignKey(
        entity = InventoryItem::class,
        parentColumns = ["id"], childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("itemId"), Index("expiryDate")],
)
data class InventoryBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val purchaseDate: Long,               // epochDay
    val quantity: Double,                 // purchased amount
    val remaining: Double,                // recorded remaining (as of item.stockCheckedAt)
    val expiryDate: Long? = null,         // epochDay
    val expiryKind: ExpiryKind = ExpiryKind.BEST_BEFORE,
    val price: Double? = null,
    val expectedDurationDays: Int? = null,
    val status: BatchStatus = BatchStatus.ACTIVE,
    val closedAt: Long? = null,
)

@Entity(
    tableName = "stock_transactions",
    foreignKeys = [ForeignKey(
        entity = InventoryItem::class,
        parentColumns = ["id"], childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("itemId"), Index("batchId"), Index("timestamp")],
)
data class StockTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val batchId: Long? = null,
    /** Positive for purchases, negative for consumption / discard. */
    val quantityDelta: Double,
    val reason: TxReason,
    val timestamp: Long = System.currentTimeMillis(),
    val price: Double? = null,
    val note: String = "",
)

// ---------------------------------------------------------------- tasks

/** Recurrence definition, embedded into [Task]. */
data class RecurrenceRule(
    val type: RepeatType = RepeatType.NONE,
    /** "Every N" for daily/weekly/monthly/yearly, or N days for EVERY_N_DAYS / AFTER_COMPLETION. */
    val interval: Int = 1,
    /** Bit mask Mon=1<<0 … Sun=1<<6 for weekly rules. */
    val weekdays: Int = 0,
    /** Day of month for monthly rules (1..31, clamped to month length). */
    val dayOfMonth: Int? = null,
    val startDate: Long,                  // epochDay
    val endDate: Long? = null,            // epochDay, inclusive
    val zoneId: String = java.time.ZoneId.systemDefault().id,
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val emoji: String = "",
    val category: TaskCategory = TaskCategory.OTHER,
    val priority: Priority = Priority.MEDIUM,
    /** False for "someday" tasks like "wash vessels" — pending but never overdue. */
    val hasDueDate: Boolean = true,
    /** Minutes after midnight, or null for an all-day task. */
    val timeOfDay: Int? = null,
    /** Minutes before the due time to remind; null = no reminder. */
    val reminderOffsetMinutes: Int? = 0,
    @Embedded(prefix = "rule_") val rule: RecurrenceRule,
    val missedPolicy: MissedPolicy = MissedPolicy.KEEP_OVERDUE,
    val assignee: String? = null,
    /** Occurrences have been generated up to and including this epochDay. */
    val generatedUntil: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isRecurring: Boolean get() = rule.type != RepeatType.NONE
}

@Entity(
    tableName = "task_occurrences",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    // Idempotent generation: one occurrence per (task, originally scheduled date).
    indices = [Index(value = ["taskId", "originalDate"], unique = true), Index("dueDate"), Index("status")],
)
data class TaskOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** The date the rule produced; never changes (dedup key). */
    val originalDate: Long,
    /** Effective due date (differs from [originalDate] after postponing). */
    val dueDate: Long,
    val dueTime: Int? = null,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val completedAt: Long? = null,
    val note: String = "",
)

/** Occurrence joined with its parent task, used by most screens. */
data class OccurrenceWithTask(
    @Embedded val occurrence: TaskOccurrence,
    @androidx.room.Relation(parentColumn = "taskId", entityColumn = "id")
    val task: Task,
)

// ---------------------------------------------------------------- habits

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✅",
    val type: HabitType = HabitType.BINARY,
    /** Daily target for QUANTITY / DURATION; times per week for FREQUENCY; 1 otherwise. */
    val target: Double = 1.0,
    val unit: String = "",
    /** Scheduled days bit mask (Mon=bit0). 127 = every day. Ignored for FREQUENCY. */
    val weekdays: Int = 127,
    val reminderTime: Int? = null,
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "habit_logs",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"], childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["habitId", "date"], unique = true)],
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: Long,                       // epochDay
    /** Measured value; for BINARY/FREQUENCY 1 = done; for AVOIDANCE 1 = stayed clean, -1 = slipped. */
    val value: Double,
    val updatedAt: Long = System.currentTimeMillis(),
)

// ---------------------------------------------------------------- reminders & shopping

/**
 * One row per reminder event + stage (e.g. "expiry:12:warn"). Its stable [key] is what
 * guarantees a reminder is never delivered twice and lets the dashboard acknowledge alerts.
 */
@Entity(tableName = "reminders", indices = [Index("sourceType", "sourceId")])
data class Reminder(
    @PrimaryKey val key: String,
    val sourceType: ReminderSource,
    val sourceId: Long,
    val triggerAt: Long,
    val state: ReminderState,
    val snoozedUntil: Long? = null,
    val firedAt: Long? = null,
    val title: String = "",
)

@Entity(tableName = "shopping_items", indices = [Index("inventoryItemId")])
data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inventoryItemId: Long? = null,
    val name: String,
    val emoji: String = "🛒",
    val quantity: Double? = null,
    @ColumnInfo(defaultValue = "PCS") val unit: StockUnit = StockUnit.PCS,
    val state: ShoppingState = ShoppingState.NEEDED,
    val suggested: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val purchasedAt: Long? = null,
)
