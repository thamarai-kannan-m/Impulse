package com.example.impulse.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain-JSON export / import of everything LifeLoop stores. The file is human-readable and
 * versioned so future releases can migrate older backups.
 */
class BackupManager(private val db: LifeLoopDatabase) {

    suspend fun export(): String {
        val inv = db.inventoryDao(); val tasks = db.taskDao(); val habits = db.habitDao(); val shop = db.shoppingDao()
        val root = JSONObject()
        root.put("format", "lifeloop-backup")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("items", JSONArray(inv.allItems().map { it.toJson() }))
        root.put("batches", JSONArray(inv.allBatches().map { it.toJson() }))
        root.put("transactions", JSONArray(inv.allTransactions().map { it.toJson() }))
        root.put("tasks", JSONArray(tasks.allTasks().map { it.toJson() }))
        root.put("occurrences", JSONArray(tasks.allOccurrences().map { it.toJson() }))
        root.put("habits", JSONArray(habits.allHabits().map { it.toJson() }))
        root.put("habitLogs", JSONArray(habits.allLogs().map { it.toJson() }))
        root.put("shopping", JSONArray(shop.all().map { it.toJson() }))
        return root.toString(2)
    }

    /** Replaces all data with the backup's contents. Throws IllegalArgumentException on bad files. */
    suspend fun import(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "lifeloop-backup") { "Not a LifeLoop backup file" }
        val items = root.getJSONArray("items").objects().map { it.toItem() }
        val batches = root.getJSONArray("batches").objects().map { it.toBatch() }
        val txs = root.getJSONArray("transactions").objects().map { it.toTx() }
        val tasks = root.getJSONArray("tasks").objects().map { it.toTask() }
        val occ = root.getJSONArray("occurrences").objects().map { it.toOccurrence() }
        val habits = root.getJSONArray("habits").objects().map { it.toHabit() }
        val logs = root.getJSONArray("habitLogs").objects().map { it.toHabitLog() }
        val shopping = root.optJSONArray("shopping")?.objects()?.map { it.toShopping() }.orEmpty()

        db.withTransaction {
            val inv = db.inventoryDao(); val t = db.taskDao(); val h = db.habitDao(); val s = db.shoppingDao()
            // wipe (children cascade)
            inv.allItems().forEach { inv.deleteItem(it) }
            t.allTasks().forEach { t.deleteTask(it.id) }
            h.allHabits().forEach { h.deleteHabit(it.id) }
            s.all().forEach { s.delete(it) }
            db.reminderDao().delete(db.reminderDao().all().map { it.key })

            items.forEach { inv.insertItem(it) }
            batches.forEach { inv.insertBatch(it) }
            txs.forEach { inv.insertTx(it) }
            tasks.forEach { t.insertTask(it) }
            t.insertOccurrences(occ)
            habits.forEach { h.insertHabit(it) }
            logs.forEach { h.upsertLog(it) }
            shopping.forEach { s.insert(it) }
        }
    }

    companion object { const val FORMAT_VERSION = 1 }
}

// ---------------------------------------------------------------- helpers

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)
private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k) || !has(k)) null else getDouble(k)
private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
private inline fun <reified E : Enum<E>> JSONObject.enum(k: String, def: E): E =
    optStringOrNull(k)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: def
private fun JSONObject.putN(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)

private fun InventoryItem.toJson() = JSONObject().put("id", id).put("name", name).put("emoji", emoji)
    .put("category", category.name).put("unit", unit.name).put("location", location.name)
    .put("lowStockThreshold", lowStockThreshold).put("reorderLeadDays", reorderLeadDays).put("expiryWarnDays", expiryWarnDays)
    .put("consumptionMode", consumptionMode.name).putN("typicalDurationDays", typicalDurationDays).putN("freshnessDays", freshnessDays)
    .putN("dailyUsage", dailyUsage).put("usageSamples", usageSamples).put("stockCheckedAt", stockCheckedAt)
    .put("essential", essential).put("archived", archived).put("notes", notes).put("createdAt", createdAt)

private fun JSONObject.toItem() = InventoryItem(
    id = getLong("id"), name = getString("name"), emoji = optString("emoji", "📦"),
    category = enum("category", ItemCategory.OTHER), unit = enum("unit", StockUnit.PCS), location = enum("location", StorageLocation.OTHER),
    lowStockThreshold = optDouble("lowStockThreshold", 0.0), reorderLeadDays = optInt("reorderLeadDays", 5), expiryWarnDays = optInt("expiryWarnDays", 2),
    consumptionMode = enum("consumptionMode", ConsumptionMode.DURATION), typicalDurationDays = optIntOrNull("typicalDurationDays"),
    freshnessDays = optIntOrNull("freshnessDays"), dailyUsage = optDoubleOrNull("dailyUsage"), usageSamples = optInt("usageSamples", 0),
    stockCheckedAt = optLong("stockCheckedAt", System.currentTimeMillis()), essential = optBoolean("essential", true),
    archived = optBoolean("archived", false), notes = optString("notes", ""), createdAt = optLong("createdAt", System.currentTimeMillis()),
)

private fun InventoryBatch.toJson() = JSONObject().put("id", id).put("itemId", itemId).put("purchaseDate", purchaseDate)
    .put("quantity", quantity).put("remaining", remaining).putN("expiryDate", expiryDate).put("expiryKind", expiryKind.name)
    .putN("price", price).putN("expectedDurationDays", expectedDurationDays).put("status", status.name).putN("closedAt", closedAt)

private fun JSONObject.toBatch() = InventoryBatch(
    id = getLong("id"), itemId = getLong("itemId"), purchaseDate = getLong("purchaseDate"), quantity = getDouble("quantity"),
    remaining = getDouble("remaining"), expiryDate = optLongOrNull("expiryDate"), expiryKind = enum("expiryKind", ExpiryKind.BEST_BEFORE),
    price = optDoubleOrNull("price"), expectedDurationDays = optIntOrNull("expectedDurationDays"),
    status = enum("status", BatchStatus.ACTIVE), closedAt = optLongOrNull("closedAt"),
)

private fun StockTransaction.toJson() = JSONObject().put("id", id).put("itemId", itemId).putN("batchId", batchId)
    .put("quantityDelta", quantityDelta).put("reason", reason.name).put("timestamp", timestamp).putN("price", price).put("note", note)

private fun JSONObject.toTx() = StockTransaction(
    id = getLong("id"), itemId = getLong("itemId"), batchId = optLongOrNull("batchId"), quantityDelta = getDouble("quantityDelta"),
    reason = enum("reason", TxReason.ADJUST), timestamp = getLong("timestamp"), price = optDoubleOrNull("price"), note = optString("note", ""),
)

private fun Task.toJson() = JSONObject().put("id", id).put("title", title).put("description", description).put("emoji", emoji)
    .put("category", category.name).put("priority", priority.name).put("hasDueDate", hasDueDate).putN("timeOfDay", timeOfDay)
    .putN("reminderOffsetMinutes", reminderOffsetMinutes).put("missedPolicy", missedPolicy.name).putN("assignee", assignee)
    .putN("generatedUntil", generatedUntil).put("archived", archived).put("createdAt", createdAt)
    .put("rule", JSONObject().put("type", rule.type.name).put("interval", rule.interval).put("weekdays", rule.weekdays)
        .putN("dayOfMonth", rule.dayOfMonth).put("startDate", rule.startDate).putN("endDate", rule.endDate).put("zoneId", rule.zoneId))

private fun JSONObject.toTask(): Task {
    val r = getJSONObject("rule")
    return Task(
        id = getLong("id"), title = getString("title"), description = optString("description", ""), emoji = optString("emoji", ""),
        category = enum("category", TaskCategory.OTHER), priority = enum("priority", Priority.MEDIUM), hasDueDate = optBoolean("hasDueDate", true),
        timeOfDay = optIntOrNull("timeOfDay"), reminderOffsetMinutes = optIntOrNull("reminderOffsetMinutes"),
        missedPolicy = enum("missedPolicy", MissedPolicy.KEEP_OVERDUE), assignee = optStringOrNull("assignee"),
        generatedUntil = optLongOrNull("generatedUntil"), archived = optBoolean("archived", false), createdAt = optLong("createdAt", System.currentTimeMillis()),
        rule = RecurrenceRule(
            type = r.enum("type", RepeatType.NONE), interval = r.optInt("interval", 1), weekdays = r.optInt("weekdays", 0),
            dayOfMonth = r.optIntOrNull("dayOfMonth"), startDate = r.getLong("startDate"), endDate = r.optLongOrNull("endDate"),
            zoneId = r.optString("zoneId", java.time.ZoneId.systemDefault().id),
        ),
    )
}

private fun TaskOccurrence.toJson() = JSONObject().put("id", id).put("taskId", taskId).put("originalDate", originalDate)
    .put("dueDate", dueDate).putN("dueTime", dueTime).put("status", status.name).putN("completedAt", completedAt).put("note", note)

private fun JSONObject.toOccurrence() = TaskOccurrence(
    id = getLong("id"), taskId = getLong("taskId"), originalDate = getLong("originalDate"), dueDate = getLong("dueDate"),
    dueTime = optIntOrNull("dueTime"), status = enum("status", OccurrenceStatus.PENDING), completedAt = optLongOrNull("completedAt"),
    note = optString("note", ""),
)

private fun Habit.toJson() = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("type", type.name)
    .put("target", target).put("unit", unit).put("weekdays", weekdays).putN("reminderTime", reminderTime)
    .put("colorIndex", colorIndex).put("archived", archived).put("createdAt", createdAt)

private fun JSONObject.toHabit() = Habit(
    id = getLong("id"), name = getString("name"), emoji = optString("emoji", "✅"), type = enum("type", HabitType.BINARY),
    target = optDouble("target", 1.0), unit = optString("unit", ""), weekdays = optInt("weekdays", 127),
    reminderTime = optIntOrNull("reminderTime"), colorIndex = optInt("colorIndex", 0), archived = optBoolean("archived", false),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
)

private fun HabitLog.toJson() = JSONObject().put("id", id).put("habitId", habitId).put("date", date).put("value", value).put("updatedAt", updatedAt)
private fun JSONObject.toHabitLog() = HabitLog(id = getLong("id"), habitId = getLong("habitId"), date = getLong("date"), value = getDouble("value"), updatedAt = optLong("updatedAt", 0))

private fun ShoppingListItem.toJson() = JSONObject().put("id", id).putN("inventoryItemId", inventoryItemId).put("name", name)
    .put("emoji", emoji).putN("quantity", quantity).put("unit", unit.name).put("state", state.name).put("suggested", suggested)
    .put("addedAt", addedAt).putN("purchasedAt", purchasedAt)

private fun JSONObject.toShopping() = ShoppingListItem(
    id = getLong("id"), inventoryItemId = optLongOrNull("inventoryItemId"), name = getString("name"), emoji = optString("emoji", "🛒"),
    quantity = optDoubleOrNull("quantity"), unit = enum("unit", StockUnit.PCS), state = enum("state", ShoppingState.NEEDED),
    suggested = optBoolean("suggested", false), addedAt = optLong("addedAt", 0), purchasedAt = optLongOrNull("purchasedAt"),
)
