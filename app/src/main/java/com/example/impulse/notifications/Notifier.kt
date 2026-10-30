package com.example.impulse.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.impulse.MainActivity
import com.example.impulse.R
import com.example.impulse.data.ReminderSource
import com.example.impulse.domain.NotifAction
import com.example.impulse.domain.ReminderCandidate

/** Builds and posts LifeLoop notifications. Notification ids derive from the stable reminder key. */
class Notifier(private val context: Context) {

    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_TASKS, "Tasks & routines", NotificationManager.IMPORTANCE_HIGH).apply { description = "Chores, bills and scheduled routines" },
            NotificationChannel(CH_STOCK, "Low stock", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Items that may run out soon" },
            NotificationChannel(CH_EXPIRY, "Expiry & freshness", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Food nearing its date" },
            NotificationChannel(CH_HABITS, "Habits", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Gentle habit nudges" },
            NotificationChannel(CH_DIGEST, "Daily digest", NotificationManager.IMPORTANCE_LOW).apply { description = "One summary of your day" },
        ).forEach { mgr.createNotificationChannel(it) }
    }

    fun canPost(): Boolean {
        if (!nm.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun post(candidates: List<ReminderCandidate>) {
        if (!canPost() || candidates.isEmpty()) return
        candidates.forEach { postOne(it) }
        // Summaries keep several alerts of one kind tidy in the shade.
        candidates.groupBy { it.source }.filterValues { it.size >= 2 }.forEach { (source, list) ->
            val summary = NotificationCompat.Builder(context, channelFor(source))
                .setSmallIcon(R.drawable.ic_stat_loop)
                .setColor(BRAND)
                .setContentTitle("${list.size} ${labelFor(source)}")
                .setStyle(NotificationCompat.InboxStyle().also { s -> list.take(6).forEach { s.addLine(it.title) } })
                .setGroup(groupFor(source))
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openIntent(deepLinkFor(source, 0)))
                .build()
            safeNotify(SUMMARY_BASE + source.ordinal, summary)
        }
    }

    private fun postOne(c: ReminderCandidate) {
        val id = idFor(c.key)
        val b = NotificationCompat.Builder(context, channelFor(c.source))
            .setSmallIcon(R.drawable.ic_stat_loop)
            .setColor(BRAND)
            .setContentTitle(c.title)
            .setContentText(c.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.body))
            .setAutoCancel(true)
            .setGroup(groupFor(c.source))
            .setCategory(if (c.source == ReminderSource.TASK) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(if (c.important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent(deepLinkFor(c.source, c.sourceId)))
        c.actions.forEach { a ->
            val label = when (a) {
                NotifAction.DONE -> "Done"
                NotifAction.SNOOZE -> if (c.source == ReminderSource.TASK || c.source == ReminderSource.HABIT) "Snooze 1h" else "Remind tomorrow"
                NotifAction.ADD_TO_SHOPPING -> "Add to list"
                NotifAction.MARK_USED -> "Used it"
                NotifAction.LOG_HABIT -> "Log it"
                NotifAction.OPEN -> return@forEach
            }
            b.addAction(0, label, actionIntent(a, c, id))
        }
        safeNotify(id, b.build())
    }

    @Suppress("MissingPermission")
    private fun safeNotify(id: Int, n: android.app.Notification) {
        try { nm.notify(id, n) } catch (_: SecurityException) { /* permission revoked mid-flight */ }
    }

    fun cancel(key: String) = nm.cancel(idFor(key))

    private fun openIntent(uri: String): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, uri.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(a: NotifAction, c: ReminderCandidate, notifId: Int): PendingIntent {
        val i = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.impulse.action.${a.name}"
            putExtra(NotificationActionReceiver.EXTRA_KEY, c.key)
            putExtra(NotificationActionReceiver.EXTRA_SOURCE, c.source.name)
            putExtra(NotificationActionReceiver.EXTRA_SOURCE_ID, c.sourceId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(context, (c.key + a.name).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CH_TASKS = "tasks"
        const val CH_STOCK = "stock"
        const val CH_EXPIRY = "expiry"
        const val CH_HABITS = "habits"
        const val CH_DIGEST = "digest"
        private const val SUMMARY_BASE = 9000
        private const val BRAND = 0xFF5B4FE0.toInt()

        fun idFor(key: String): Int = key.hashCode() and 0x7FFFFFFF

        fun channelFor(s: ReminderSource) = when (s) {
            ReminderSource.TASK -> CH_TASKS
            ReminderSource.STOCK -> CH_STOCK
            ReminderSource.EXPIRY -> CH_EXPIRY
            ReminderSource.HABIT -> CH_HABITS
            ReminderSource.DIGEST -> CH_DIGEST
        }

        private fun groupFor(s: ReminderSource) = "lifeloop.${s.name.lowercase()}"
        private fun labelFor(s: ReminderSource) = when (s) {
            ReminderSource.TASK -> "tasks due"
            ReminderSource.STOCK -> "items running low"
            ReminderSource.EXPIRY -> "items to use soon"
            ReminderSource.HABIT -> "habit nudges"
            ReminderSource.DIGEST -> "summaries"
        }

        fun deepLinkFor(s: ReminderSource, id: Long): String = when (s) {
            ReminderSource.TASK -> "lifeloop://tasks"
            ReminderSource.STOCK -> if (id > 0) "lifeloop://item/$id" else "lifeloop://inventory"
            ReminderSource.EXPIRY -> "lifeloop://inventory"
            ReminderSource.HABIT -> "lifeloop://habits"
            ReminderSource.DIGEST -> "lifeloop://today"
        }
    }
}
