package com.example.impulse.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.impulse.LifeLoopApp
import com.example.impulse.data.ReminderSource
import com.example.impulse.domain.Dates
import com.example.impulse.domain.NotifAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the broadcast alive. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } finally { pending.finish() }
    }
}

/** Fired by the single "next reminder" alarm. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LifeLoopApp
        async { app.container.reminders.reconcile() }
    }
}

/** Boot, app update, time / time-zone change, exact-alarm permission change → recompute everything. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LifeLoopApp
        async {
            app.container.notifier.createChannels()
            ReminderEngine.schedulePeriodic(context)
            app.container.reminders.reconcile()
        }
    }
}

/** Handles Done / Snooze / Add to list / Used it / Log it buttons on notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LifeLoopApp
        val c = app.container
        val action = intent.action?.substringAfterLast('.')?.let { a -> NotifAction.entries.firstOrNull { it.name == a } } ?: return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val source = intent.getStringExtra(EXTRA_SOURCE)?.let { s -> ReminderSource.entries.firstOrNull { it.name == s } } ?: return
        val id = intent.getLongExtra(EXTRA_SOURCE_ID, -1)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        androidx.core.app.NotificationManagerCompat.from(context).cancel(notifId)

        async {
            when (action) {
                NotifAction.DONE -> c.tasks.complete(id)
                NotifAction.SNOOZE -> {
                    val now = System.currentTimeMillis()
                    val until = if (source == ReminderSource.TASK || source == ReminderSource.HABIT) now + 60 * 60_000L
                    else Dates.toMillis(Dates.today(now) + 1, c.settings.current().reminders.morningMinutes)
                    c.reminders.snooze(key, source, id, until)
                }
                NotifAction.ADD_TO_SHOPPING -> {
                    c.inventory.item(id)?.let { c.shopping.addForItem(it) }
                    c.reminders.acknowledge(listOf(key), source, id)
                }
                NotifAction.MARK_USED -> c.inventory.closeBatch(id, discarded = false)
                NotifAction.LOG_HABIT -> c.habits.quickLog(id)
                NotifAction.OPEN -> Unit
            }
        }
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
