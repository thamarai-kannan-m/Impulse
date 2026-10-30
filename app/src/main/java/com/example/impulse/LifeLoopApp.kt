package com.example.impulse

import android.app.Application
import android.content.Context
import com.example.impulse.data.BackupManager
import com.example.impulse.data.HabitRepository
import com.example.impulse.data.InventoryRepository
import com.example.impulse.data.LifeLoopDatabase
import com.example.impulse.data.SettingsStore
import com.example.impulse.data.ShoppingRepository
import com.example.impulse.data.TaskRepository
import com.example.impulse.notifications.Notifier
import com.example.impulse.notifications.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LifeLoopApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.reminders.start()
        ReminderEngine.schedulePeriodic(this)
    }
}

/**
 * Manual dependency container (a lightweight stand-in for Hilt that keeps the build simple).
 * Every repository reports changes to the reminder engine so alarms always match the data.
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: LifeLoopDatabase = LifeLoopDatabase.build(context)
    val settings = SettingsStore(context)
    val notifier = Notifier(context)

    private val changed: () -> Unit = { reminders.requestReconcile() }

    val inventory = InventoryRepository(db, changed)
    val tasks = TaskRepository(db, changed)
    val habits = HabitRepository(db, changed)
    val shopping = ShoppingRepository(db, changed)
    val backup = BackupManager(db)

    val reminders: ReminderEngine = ReminderEngine(
        context = context, db = db, settings = settings,
        inventory = { inventory }, tasks = { tasks }, habits = { habits },
        notifier = notifier, scope = appScope,
    )
}
