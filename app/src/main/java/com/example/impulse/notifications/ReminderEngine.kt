package com.example.impulse.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.impulse.LifeLoopApp
import com.example.impulse.data.HabitRepository
import com.example.impulse.data.InventoryRepository
import com.example.impulse.data.LifeLoopDatabase
import com.example.impulse.data.Reminder
import com.example.impulse.data.ReminderSource
import com.example.impulse.data.ReminderState
import com.example.impulse.data.SettingsStore
import com.example.impulse.data.TaskRepository
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ExpiryCondition
import com.example.impulse.domain.ReminderCandidate
import com.example.impulse.domain.ReminderPlanner
import com.example.impulse.domain.StockCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * The heart of LifeLoop's reminders: a *reconcile* loop instead of hundreds of individual alarms.
 *
 *  1. Derive every reminder that should exist from current data (tasks, stock, expiry, habits).
 *  2. Compare against the delivered-reminder log (stable keys → never duplicate).
 *  3. Post what's due (respecting quiet hours & module switches), cancel what's resolved.
 *  4. Set ONE alarm for the next future trigger.
 *
 * It runs after every data change, when that alarm fires, on boot / time-zone change, and from a
 * periodic WorkManager safety net — so reminders survive restarts and self-heal after edits.
 */
class ReminderEngine(
    private val context: Context,
    private val db: LifeLoopDatabase,
    private val settings: SettingsStore,
    private val inventory: () -> InventoryRepository,
    private val tasks: () -> TaskRepository,
    private val habits: () -> HabitRepository,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            requests.consumeAsFlow().debounce(400).collect { runCatching { reconcile() } }
        }
        requestReconcile()
    }

    /** Cheap, debounced request — call after any data change. */
    fun requestReconcile() { requests.trySend(Unit) }

    suspend fun reconcile(now: Long = System.currentTimeMillis()) = mutex.withLock {
        val prefs = settings.current().reminders
        val zone = Dates.zone()
        val today = Dates.today(now, zone)
        tasks().ensureOccurrences(today)

        val forecasts = inventory().snapshotForecasts(now)
        val open = tasks().openUntil(today + 30)
        val summaries = habits().snapshotSummaries(today)

        val digest = buildDigest(
            overdue = open.count { it.task.hasDueDate && it.occurrence.dueDate < today },
            dueToday = open.count { it.occurrence.dueDate == today && it.task.hasDueDate },
            pending = open.count { !it.task.hasDueDate },
            expiring = forecasts.sumOf { f -> f.batches.count { it.condition in URGENT_EXPIRY } },
            low = forecasts.count { it.stockCondition != StockCondition.IN_STOCK },
            habits = summaries.count { it.scheduledToday },
        )

        val candidates: List<ReminderCandidate> =
            ReminderPlanner.taskCandidates(open, prefs, zone) +
                ReminderPlanner.stockCandidates(forecasts, prefs, now, zone) +
                ReminderPlanner.expiryCandidates(forecasts, prefs, zone) +
                ReminderPlanner.habitCandidates(summaries, today, zone) +
                ReminderPlanner.digestCandidates(today, prefs, digest, zone)

        val dao = db.reminderDao()
        val log = dao.all().associateBy { it.key }
        val plan = ReminderPlanner.plan(candidates, log, now, prefs, zone)

        notifier.post(plan.post)
        dao.upsertAll((plan.post + plan.markSilently).map {
            Reminder(it.key, it.source, it.sourceId, it.triggerAt, ReminderState.FIRED, firedAt = now, title = it.title)
        })
        if (plan.obsolete.isNotEmpty()) {
            plan.obsolete.forEach { notifier.cancel(it.key) }
            dao.delete(plan.obsolete.map { it.key })
        }
        scheduleWake(plan.nextWakeAt)
    }

    private fun buildDigest(overdue: Int, dueToday: Int, pending: Int, expiring: Int, low: Int, habits: Int): String {
        val parts = buildList {
            if (overdue > 0) add("$overdue overdue")
            if (dueToday > 0) add("$dueToday due today")
            if (pending > 0) add("$pending pending")
            if (expiring > 0) add("$expiring to use soon")
            if (low > 0) add("$low running low")
            if (habits > 0) add("$habits habits")
        }
        return parts.joinToString(" · ")
    }

    // ------------------------------------------------------------------ user actions from notifications/dashboard

    suspend fun snooze(key: String, source: ReminderSource, sourceId: Long, untilMillis: Long) {
        db.reminderDao().upsert(Reminder(key, source, sourceId, untilMillis, ReminderState.SNOOZED, snoozedUntil = untilMillis))
        notifier.cancel(key)
        requestReconcile()
    }

    /** Hides an alert (and its notification) without resolving the underlying item. */
    suspend fun acknowledge(keys: List<String>, source: ReminderSource, sourceId: Long) {
        val now = System.currentTimeMillis()
        db.reminderDao().upsertAll(keys.map { Reminder(it, source, sourceId, now, ReminderState.ACKNOWLEDGED, firedAt = now) })
        keys.forEach { notifier.cancel(it) }
    }

    // ------------------------------------------------------------------ alarm

    private fun scheduleWake(at: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 7001, Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) { am.cancel(pi); return }
        try {
            if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    companion object {
        private val URGENT_EXPIRY = setOf(ExpiryCondition.EXPIRED, ExpiryCondition.EXPIRES_TODAY, ExpiryCondition.EXPIRING_SOON)

        fun canExact(am: AlarmManager): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReconcileWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("lifeloop-reconcile", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/** Periodic safety net in case an alarm was dropped by the OS. */
class ReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LifeLoopApp
        return runCatching { app.container.reminders.reconcile() }.fold({ Result.success() }, { Result.retry() })
    }
}
