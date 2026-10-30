package com.example.impulse.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        InventoryItem::class, InventoryBatch::class, StockTransaction::class,
        Task::class, TaskOccurrence::class,
        Habit::class, HabitLog::class,
        Reminder::class, ShoppingListItem::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LifeLoopDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun reminderDao(): ReminderDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        /**
         * Schema changes must ship as explicit migrations (never destructive resets) so users
         * keep their history. Add `Migration(1, 2) { db -> db.execSQL(...) }` entries here; the
         * exported schemas in app/schemas let `MigrationTestHelper` verify them.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): LifeLoopDatabase =
            Room.databaseBuilder(context, LifeLoopDatabase::class.java, "lifeloop.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
