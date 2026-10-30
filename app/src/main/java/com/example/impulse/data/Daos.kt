package com.example.impulse.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items ORDER BY name COLLATE NOCASE")
    suspend fun allItems(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE archived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchived(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    fun observeItem(id: Long): Flow<InventoryItem?>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun item(id: Long): InventoryItem?

    @Insert suspend fun insertItem(item: InventoryItem): Long
    @Update suspend fun updateItem(item: InventoryItem)
    @Delete suspend fun deleteItem(item: InventoryItem)

    @Query("SELECT * FROM inventory_batches")
    fun observeAllBatches(): Flow<List<InventoryBatch>>

    @Query("SELECT * FROM inventory_batches")
    suspend fun allBatches(): List<InventoryBatch>

    @Query("SELECT * FROM inventory_batches WHERE itemId = :itemId ORDER BY purchaseDate DESC, id DESC")
    fun observeBatches(itemId: Long): Flow<List<InventoryBatch>>

    @Query("SELECT * FROM inventory_batches WHERE itemId = :itemId")
    suspend fun batches(itemId: Long): List<InventoryBatch>

    @Query("SELECT * FROM inventory_batches WHERE id = :id")
    suspend fun batch(id: Long): InventoryBatch?

    @Insert suspend fun insertBatch(batch: InventoryBatch): Long
    @Update suspend fun updateBatch(batch: InventoryBatch)
    @Update suspend fun updateBatches(batches: List<InventoryBatch>)

    @Insert suspend fun insertTx(tx: StockTransaction): Long

    @Query("SELECT * FROM stock_transactions WHERE itemId = :itemId ORDER BY timestamp DESC LIMIT 100")
    fun observeTransactions(itemId: Long): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeTransactionsSince(since: Long): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions")
    suspend fun allTransactions(): List<StockTransaction>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY title COLLATE NOCASE")
    fun observeTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): Task?

    @Insert suspend fun insertTask(task: Task): Long
    @Update suspend fun updateTask(task: Task)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)

    /** IGNORE + unique (taskId, originalDate) index = idempotent occurrence generation. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrences(list: List<TaskOccurrence>): List<Long>

    @Update suspend fun updateOccurrence(o: TaskOccurrence)

    @Query("SELECT * FROM task_occurrences WHERE id = :id")
    suspend fun occurrence(id: Long): TaskOccurrence?

    @Transaction
    @Query("SELECT * FROM task_occurrences WHERE id = :id")
    suspend fun occurrenceWithTask(id: Long): OccurrenceWithTask?

    @Query("SELECT * FROM task_occurrences WHERE taskId = :taskId ORDER BY originalDate DESC")
    suspend fun occurrencesFor(taskId: Long): List<TaskOccurrence>

    @Query("SELECT * FROM task_occurrences")
    suspend fun allOccurrences(): List<TaskOccurrence>

    @Query("DELETE FROM task_occurrences WHERE taskId = :taskId AND status = 'PENDING' AND originalDate >= :fromDay")
    suspend fun deletePendingFrom(taskId: Long, fromDay: Long)

    @Transaction
    @Query(
        """SELECT * FROM task_occurrences WHERE dueDate BETWEEN :from AND :to
           OR (status IN ('PENDING','IN_PROGRESS') AND dueDate < :from)
           ORDER BY dueDate, dueTime"""
    )
    fun observeWindow(from: Long, to: Long): Flow<List<OccurrenceWithTask>>

    @Transaction
    @Query("SELECT * FROM task_occurrences WHERE status IN ('PENDING','IN_PROGRESS') AND dueDate <= :until ORDER BY dueDate, dueTime")
    suspend fun openUntil(until: Long): List<OccurrenceWithTask>

    @Transaction
    @Query("SELECT * FROM task_occurrences WHERE status IN ('PENDING','IN_PROGRESS') AND taskId = :taskId AND dueDate < :before")
    suspend fun openBefore(taskId: Long, before: Long): List<TaskOccurrence>

    @Transaction
    @Query("SELECT * FROM task_occurrences WHERE completedAt >= :since AND status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompletedSince(since: Long): Flow<List<OccurrenceWithTask>>

    @Transaction
    @Query("SELECT * FROM task_occurrences WHERE dueDate >= :from AND dueDate <= :to")
    fun observeRange(from: Long, to: Long): Flow<List<OccurrenceWithTask>>
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY createdAt")
    fun observeHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits")
    suspend fun allHabits(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun habit(id: Long): Habit?

    @Insert suspend fun insertHabit(h: Habit): Long
    @Update suspend fun updateHabit(h: Habit)
    @Query("DELETE FROM habits WHERE id = :id") suspend fun deleteHabit(id: Long)

    @Query("SELECT * FROM habit_logs WHERE date >= :since")
    fun observeLogsSince(since: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId")
    fun observeLogs(habitId: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs")
    suspend fun allLogs(): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun log(habitId: Long, date: Long): HabitLog?

    @Upsert suspend fun upsertLog(log: HabitLog)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun deleteLog(habitId: Long, date: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    suspend fun all(): List<Reminder>

    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE `key` = :key")
    suspend fun get(key: String): Reminder?

    @Upsert suspend fun upsert(r: Reminder)
    @Upsert suspend fun upsertAll(r: List<Reminder>)

    @Query("DELETE FROM reminders WHERE `key` IN (:keys)")
    suspend fun delete(keys: List<String>)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY state, addedAt DESC")
    fun observe(): Flow<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_items")
    suspend fun all(): List<ShoppingListItem>

    @Query("SELECT * FROM shopping_items WHERE inventoryItemId = :itemId AND state = 'NEEDED' LIMIT 1")
    suspend fun neededFor(itemId: Long): ShoppingListItem?

    @Insert suspend fun insert(item: ShoppingListItem): Long
    @Update suspend fun update(item: ShoppingListItem)
    @Delete suspend fun delete(item: ShoppingListItem)

    @Query("DELETE FROM shopping_items WHERE state = 'PURCHASED'")
    suspend fun clearPurchased()
}
