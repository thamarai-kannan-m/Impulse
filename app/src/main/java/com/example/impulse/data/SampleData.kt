package com.example.impulse.data

import androidx.room.withTransaction
import com.example.impulse.domain.Dates
import java.time.DayOfWeek
import kotlin.random.Random

/** Seeds a realistic example household so new users can explore every feature immediately. */
object SampleData {

    suspend fun seed(db: LifeLoopDatabase) = db.withTransaction {
        val inv = db.inventoryDao(); val t = db.taskDao(); val h = db.habitDao(); val s = db.shoppingDao()
        val today = Dates.today()
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        suspend fun item(
            name: String, emoji: String, cat: ItemCategory, unit: StockUnit, loc: StorageLocation,
            qty: Double, boughtDaysAgo: Int, duration: Int?, mode: ConsumptionMode = ConsumptionMode.DURATION,
            expiryIn: Int? = null, kind: ExpiryKind = ExpiryKind.BEST_BEFORE, price: Double? = null,
            threshold: Double = 0.0, freshness: Int? = null, remaining: Double = qty,
        ): Long {
            val id = inv.insertItem(
                InventoryItem(
                    name = name, emoji = emoji, category = cat, unit = unit, location = loc, lowStockThreshold = threshold,
                    consumptionMode = mode, typicalDurationDays = duration, freshnessDays = freshness,
                    dailyUsage = duration?.let { qty / it }, stockCheckedAt = now - boughtDaysAgo * day,
                    usageSamples = if (mode == ConsumptionMode.USAGE) 2 else 0,
                )
            )
            val bid = inv.insertBatch(
                InventoryBatch(
                    itemId = id, purchaseDate = today - boughtDaysAgo, quantity = qty, remaining = remaining,
                    expiryDate = expiryIn?.let { today + it }, expiryKind = kind, price = price, expectedDurationDays = duration,
                )
            )
            inv.insertTx(StockTransaction(itemId = id, batchId = bid, quantityDelta = qty, reason = TxReason.PURCHASE, timestamp = now - boughtDaysAgo * day, price = price))
            return id
        }

        item("Rice", "🍚", ItemCategory.GROCERY, StockUnit.KG, StorageLocation.PANTRY, 5.0, 8, 30, price = 340.0, threshold = 1.0)
        val milk = item("Milk", "🥛", ItemCategory.DAIRY, StockUnit.L, StorageLocation.FRIDGE, 1.0, 1, 2, expiryIn = 1, kind = ExpiryKind.USE_BY, price = 56.0)
        // second milk batch with its own expiry date
        inv.insertBatch(InventoryBatch(itemId = milk, purchaseDate = today, quantity = 1.0, remaining = 1.0, expiryDate = today + 3, expiryKind = ExpiryKind.USE_BY, price = 56.0, expectedDurationDays = 2))
        inv.insertTx(StockTransaction(itemId = milk, quantityDelta = 1.0, reason = TxReason.PURCHASE, timestamp = now, price = 56.0))
        item("Tomatoes", "🍅", ItemCategory.VEGETABLE, StockUnit.KG, StorageLocation.KITCHEN, 1.0, 4, 6, expiryIn = 1, kind = ExpiryKind.ESTIMATED, price = 40.0, freshness = 5)
        item("Detergent", "🧺", ItemCategory.HOUSEHOLD, StockUnit.KG, StorageLocation.CABINET, 2.0, 36, 40, price = 420.0, threshold = 0.3)
        item("Toothpaste", "🪥", ItemCategory.PERSONAL_CARE, StockUnit.PCS, StorageLocation.BATHROOM, 1.0, 20, 45, price = 110.0)
        item("Cooking oil", "🫒", ItemCategory.GROCERY, StockUnit.L, StorageLocation.KITCHEN, 1.0, 12, 25, mode = ConsumptionMode.USAGE, price = 180.0, threshold = 0.2)
        item("Eggs", "🥚", ItemCategory.DAIRY, StockUnit.PCS, StorageLocation.FRIDGE, 12.0, 3, null, mode = ConsumptionMode.MANUAL, expiryIn = 18, remaining = 5.0, price = 84.0, threshold = 4.0)
        item("Onions", "🧅", ItemCategory.VEGETABLE, StockUnit.KG, StorageLocation.PANTRY, 2.0, 5, 12, expiryIn = 15, kind = ExpiryKind.ESTIMATED, price = 70.0, threshold = 0.5)

        fun rule(type: RepeatType, start: Long, interval: Int = 1, weekdays: Int = 0, dom: Int? = null) =
            RecurrenceRule(type = type, interval = interval, weekdays = weekdays, dayOfMonth = dom, startDate = start)

        t.insertTask(Task(title = "Wash clothes", emoji = "👕", category = TaskCategory.LAUNDRY, timeOfDay = 19 * 60,
            rule = rule(RepeatType.WEEKLY, today - 21, weekdays = Dates.maskOf(DayOfWeek.WEDNESDAY))))
        val vessels = t.insertTask(Task(title = "Wash vessels", emoji = "🍽️", category = TaskCategory.KITCHEN, hasDueDate = false,
            priority = Priority.HIGH, reminderOffsetMinutes = null, rule = rule(RepeatType.NONE, today - 1)))
        t.insertTask(Task(title = "Clean room", emoji = "🧹", category = TaskCategory.CLEANING, timeOfDay = 10 * 60,
            rule = rule(RepeatType.WEEKLY, today - 14, weekdays = Dates.maskOf(DayOfWeek.SUNDAY))))
        t.insertTask(Task(title = "Pay electricity bill", emoji = "💡", category = TaskCategory.BILLS, priority = Priority.HIGH,
            timeOfDay = 10 * 60, reminderOffsetMinutes = 24 * 60, rule = rule(RepeatType.MONTHLY, today - 40, dom = 10)))
        t.insertTask(Task(title = "Take out garbage", emoji = "🗑️", category = TaskCategory.CLEANING, timeOfDay = 21 * 60,
            missedPolicy = MissedPolicy.AUTO_SKIP, rule = rule(RepeatType.DAILY, today - 3)))
        t.insertTask(Task(title = "Replace water filter", emoji = "💧", category = TaskCategory.MAINTENANCE,
            rule = rule(RepeatType.AFTER_COMPLETION, today + 12, interval = 90)))
        t.insertTask(Task(title = "Clean refrigerator", emoji = "🧊", category = TaskCategory.CLEANING, timeOfDay = 11 * 60,
            rule = rule(RepeatType.EVERY_N_DAYS, today - 13, interval = 15)))
        // Monday vessel washing that was missed stays pending — no duplicates.
        t.insertOccurrences(listOf(TaskOccurrence(taskId = vessels, originalDate = today - 1, dueDate = today - 1)))
        t.task(vessels)?.let { t.updateTask(it.copy(generatedUntil = today - 1)) }

        val rnd = Random(42)
        suspend fun habit(name: String, emoji: String, type: HabitType, target: Double, unit: String, color: Int, reminder: Int?, chance: Double) {
            val id = h.insertHabit(Habit(name = name, emoji = emoji, type = type, target = target, unit = unit, colorIndex = color,
                reminderTime = reminder, createdAt = now - 60 * day))
            for (d in 1..56) if (rnd.nextDouble() < chance) {
                val v = when (type) { HabitType.QUANTITY, HabitType.DURATION -> target * (0.6 + rnd.nextDouble() * 0.6); else -> 1.0 }
                h.upsertLog(HabitLog(habitId = id, date = today - d, value = Math.round(v).toDouble()))
            }
        }
        habit("Exercise", "🏃", HabitType.BINARY, 1.0, "", 0, 7 * 60, 0.8)
        habit("Drink water", "💧", HabitType.QUANTITY, 2000.0, "mL", 1, null, 0.85)
        habit("Read", "📚", HabitType.DURATION, 20.0, "min", 2, 21 * 60, 0.65)
        habit("Workout", "💪", HabitType.FREQUENCY, 4.0, "times", 5, null, 0.55)
        habit("Avoid sugary drinks", "🥤", HabitType.AVOIDANCE, 1.0, "", 6, null, 0.7)

        s.insert(ShoppingListItem(name = "Bananas", emoji = "🍌", quantity = 6.0, unit = StockUnit.PCS))
    }
}
