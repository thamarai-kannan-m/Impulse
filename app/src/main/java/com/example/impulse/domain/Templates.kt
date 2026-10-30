package com.example.impulse.domain

import com.example.impulse.data.ExpiryKind
import com.example.impulse.data.HabitType
import com.example.impulse.data.ItemCategory
import com.example.impulse.data.ItemCategory.*
import com.example.impulse.data.Priority
import com.example.impulse.data.RepeatType
import com.example.impulse.data.StockUnit
import com.example.impulse.data.StorageLocation
import com.example.impulse.data.TaskCategory
import java.time.DayOfWeek

/** Smart defaults so common entries take a single tap. */
data class ItemTemplate(
    val name: String,
    val emoji: String,
    val category: ItemCategory,
    val unit: StockUnit,
    val defaultQty: Double,
    val durationDays: Int?,
    val freshnessDays: Int? = null,
    val expiryKind: ExpiryKind = ExpiryKind.BEST_BEFORE,
    val location: StorageLocation = StorageLocation.PANTRY,
    val lowThreshold: Double = 0.0,
)

data class TaskTemplate(
    val title: String,
    val emoji: String,
    val category: TaskCategory,
    val repeat: RepeatType,
    val interval: Int = 1,
    val weekdays: Int = 0,
    val dayOfMonth: Int? = null,
    val time: Int? = null,
    val priority: Priority = Priority.MEDIUM,
    val hasDueDate: Boolean = true,
)

data class HabitTemplate(
    val name: String,
    val emoji: String,
    val type: HabitType,
    val target: Double = 1.0,
    val unit: String = "",
    val reminder: Int? = null,
    val color: Int = 0,
)

object Templates {
    val items = listOf(
        ItemTemplate("Rice", "🍚", GROCERY, StockUnit.KG, 5.0, 30, lowThreshold = 1.0),
        ItemTemplate("Wheat flour (atta)", "🌾", GROCERY, StockUnit.KG, 5.0, 30, lowThreshold = 1.0),
        ItemTemplate("Toor dal", "🫘", GROCERY, StockUnit.KG, 1.0, 20, lowThreshold = 0.25),
        ItemTemplate("Sugar", "🧂", GROCERY, StockUnit.KG, 1.0, 30, lowThreshold = 0.2),
        ItemTemplate("Salt", "🧂", GROCERY, StockUnit.KG, 1.0, 60),
        ItemTemplate("Cooking oil", "🫒", GROCERY, StockUnit.L, 1.0, 25, location = StorageLocation.KITCHEN, lowThreshold = 0.2),
        ItemTemplate("Tea powder", "🍵", BEVERAGE, StockUnit.G, 250.0, 20, lowThreshold = 50.0),
        ItemTemplate("Coffee", "☕", BEVERAGE, StockUnit.G, 200.0, 20, lowThreshold = 40.0),
        ItemTemplate("Milk", "🥛", DAIRY, StockUnit.L, 1.0, 2, freshnessDays = 3, expiryKind = ExpiryKind.USE_BY, location = StorageLocation.FRIDGE),
        ItemTemplate("Curd", "🥣", DAIRY, StockUnit.G, 500.0, 3, freshnessDays = 4, expiryKind = ExpiryKind.USE_BY, location = StorageLocation.FRIDGE),
        ItemTemplate("Paneer", "🧀", DAIRY, StockUnit.G, 200.0, 3, freshnessDays = 4, expiryKind = ExpiryKind.USE_BY, location = StorageLocation.FRIDGE),
        ItemTemplate("Eggs", "🥚", DAIRY, StockUnit.PCS, 12.0, 7, freshnessDays = 21, location = StorageLocation.FRIDGE, lowThreshold = 2.0),
        ItemTemplate("Bread", "🍞", BAKERY, StockUnit.PACK, 1.0, 4, freshnessDays = 4, location = StorageLocation.KITCHEN),
        ItemTemplate("Tomatoes", "🍅", VEGETABLE, StockUnit.KG, 1.0, 5, freshnessDays = 5, expiryKind = ExpiryKind.ESTIMATED, location = StorageLocation.KITCHEN),
        ItemTemplate("Onions", "🧅", VEGETABLE, StockUnit.KG, 2.0, 10, freshnessDays = 20, expiryKind = ExpiryKind.ESTIMATED, location = StorageLocation.PANTRY, lowThreshold = 0.5),
        ItemTemplate("Potatoes", "🥔", VEGETABLE, StockUnit.KG, 2.0, 10, freshnessDays = 21, expiryKind = ExpiryKind.ESTIMATED, location = StorageLocation.PANTRY),
        ItemTemplate("Leafy greens", "🥬", VEGETABLE, StockUnit.PACK, 1.0, 3, freshnessDays = 3, expiryKind = ExpiryKind.ESTIMATED, location = StorageLocation.FRIDGE),
        ItemTemplate("Bananas", "🍌", FRUIT, StockUnit.PCS, 6.0, 4, freshnessDays = 4, expiryKind = ExpiryKind.ESTIMATED, location = StorageLocation.KITCHEN),
        ItemTemplate("Apples", "🍎", FRUIT, StockUnit.PCS, 6.0, 7, freshnessDays = 14, expiryKind = ExpiryKind.ESTIMATED, location = StorageLocation.FRIDGE),
        ItemTemplate("Detergent", "🧺", HOUSEHOLD, StockUnit.KG, 2.0, 40, location = StorageLocation.CABINET, lowThreshold = 0.3),
        ItemTemplate("Dishwash liquid", "🧽", HOUSEHOLD, StockUnit.ML, 750.0, 30, location = StorageLocation.KITCHEN, lowThreshold = 100.0),
        ItemTemplate("Floor cleaner", "🧴", HOUSEHOLD, StockUnit.L, 1.0, 45, location = StorageLocation.CABINET),
        ItemTemplate("Garbage bags", "🗑️", HOUSEHOLD, StockUnit.PCS, 30.0, 30, location = StorageLocation.CABINET, lowThreshold = 5.0),
        ItemTemplate("Toothpaste", "🪥", PERSONAL_CARE, StockUnit.PCS, 1.0, 45, location = StorageLocation.BATHROOM),
        ItemTemplate("Soap", "🧼", PERSONAL_CARE, StockUnit.PCS, 4.0, 60, location = StorageLocation.BATHROOM, lowThreshold = 1.0),
        ItemTemplate("Shampoo", "🧴", PERSONAL_CARE, StockUnit.ML, 340.0, 40, location = StorageLocation.BATHROOM),
    )

    val tasks = listOf(
        TaskTemplate("Wash clothes", "👕", TaskCategory.LAUNDRY, RepeatType.WEEKLY, weekdays = Dates.maskOf(DayOfWeek.WEDNESDAY), time = 19 * 60),
        TaskTemplate("Wash vessels", "🍽️", TaskCategory.KITCHEN, RepeatType.NONE, hasDueDate = false),
        TaskTemplate("Clean room", "🧹", TaskCategory.CLEANING, RepeatType.WEEKLY, weekdays = Dates.maskOf(DayOfWeek.SUNDAY), time = 10 * 60),
        TaskTemplate("Clean kitchen", "🧽", TaskCategory.KITCHEN, RepeatType.WEEKLY, weekdays = Dates.maskOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), time = 18 * 60),
        TaskTemplate("Take out garbage", "🗑️", TaskCategory.CLEANING, RepeatType.DAILY, time = 21 * 60),
        TaskTemplate("Pay electricity bill", "💡", TaskCategory.BILLS, RepeatType.MONTHLY, dayOfMonth = 10, time = 10 * 60, priority = Priority.HIGH),
        TaskTemplate("Pay rent", "🏠", TaskCategory.BILLS, RepeatType.MONTHLY, dayOfMonth = 1, time = 10 * 60, priority = Priority.HIGH),
        TaskTemplate("Clean refrigerator", "🧊", TaskCategory.CLEANING, RepeatType.EVERY_N_DAYS, interval = 15, time = 11 * 60),
        TaskTemplate("Replace water filter", "💧", TaskCategory.MAINTENANCE, RepeatType.AFTER_COMPLETION, interval = 90),
        TaskTemplate("Service AC", "❄️", TaskCategory.MAINTENANCE, RepeatType.YEARLY),
        TaskTemplate("Water the plants", "🪴", TaskCategory.PERSONAL, RepeatType.EVERY_N_DAYS, interval = 2, time = 8 * 60),
        TaskTemplate("Change bedsheets", "🛏️", TaskCategory.LAUNDRY, RepeatType.WEEKLY, interval = 2, weekdays = Dates.maskOf(DayOfWeek.SATURDAY)),
    )

    val habits = listOf(
        HabitTemplate("Exercise", "🏃", HabitType.BINARY, reminder = 7 * 60, color = 0),
        HabitTemplate("Drink water", "💧", HabitType.QUANTITY, 2000.0, "mL", color = 1),
        HabitTemplate("Read", "📚", HabitType.DURATION, 20.0, "min", reminder = 21 * 60, color = 2),
        HabitTemplate("Meditate", "🧘", HabitType.DURATION, 10.0, "min", color = 3),
        HabitTemplate("Make the bed", "🛏️", HabitType.BINARY, color = 4),
        HabitTemplate("Workout", "💪", HabitType.FREQUENCY, 4.0, "times", color = 5),
        HabitTemplate("Avoid sugary drinks", "🥤", HabitType.AVOIDANCE, color = 6),
        HabitTemplate("Walk", "🚶", HabitType.QUANTITY, 8000.0, "steps", color = 7),
        HabitTemplate("Sleep by 11 PM", "😴", HabitType.BINARY, reminder = 22 * 60 + 30, color = 3),
    )

    /** Finds the best template for a typed name (used for auto-fill suggestions). */
    fun matchItem(query: String): List<ItemTemplate> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { it.name.lowercase().contains(q) }
    }

    val emojiChoices = listOf(
        "📦", "🍚", "🌾", "🫘", "🧂", "🫒", "🍵", "☕", "🥛", "🧀", "🥚", "🍞", "🍅", "🧅", "🥔", "🥕", "🥬", "🍌", "🍎", "🍊",
        "🥭", "🍇", "🧈", "🍯", "🥫", "🍪", "🧃", "🧺", "🧽", "🧴", "🧼", "🪥", "🧻", "🗑️", "💊", "🔋", "💡", "🐶", "🌶️", "🧄",
    )
    val taskEmoji = listOf("", "👕", "🍽️", "🧹", "🧽", "🗑️", "💡", "🏠", "🧊", "💧", "🪴", "🛏️", "🚗", "📞", "💊", "🛒", "📚", "🐶", "🔧", "💳")
    val habitEmoji = listOf("✅", "🏃", "💧", "📚", "🧘", "🛏️", "💪", "🥤", "🚶", "😴", "🍎", "🎸", "✍️", "🧠", "🌞", "🚭", "📵", "🙏", "🥗", "🦷")
}
