package com.example.impulse.domain

import com.example.impulse.data.OccurrenceStatus
import com.example.impulse.data.OccurrenceWithTask
import com.example.impulse.data.Reminder
import com.example.impulse.data.ReminderSource
import com.example.impulse.data.ReminderState
import java.time.ZoneId

enum class NotifAction { DONE, SNOOZE, ADD_TO_SHOPPING, MARK_USED, LOG_HABIT, OPEN }

/** A reminder that *should* exist given the current data. */
data class ReminderCandidate(
    val key: String,
    /** Candidates sharing a group are alternative stages of the same event; only the latest due one is shown. */
    val group: String,
    val source: ReminderSource,
    val sourceId: Long,
    val triggerAt: Long,
    val title: String,
    val body: String,
    val actions: List<NotifAction> = emptyList(),
    val important: Boolean = false,
)

data class ReminderSettings(
    val morningMinutes: Int = 9 * 60,
    val quietEnabled: Boolean = true,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60,
    val inventoryEnabled: Boolean = true,
    val expiryEnabled: Boolean = true,
    val tasksEnabled: Boolean = true,
    val habitsEnabled: Boolean = true,
    val digestEnabled: Boolean = true,
    val digestMinutes: Int = 8 * 60,
)

data class ReminderPlan(
    val post: List<ReminderCandidate>,
    /** Keys to record as delivered without posting (superseded stages). */
    val markSilently: List<ReminderCandidate>,
    /** Previously delivered reminders whose cause is resolved — cancel their notifications. */
    val obsolete: List<Reminder>,
    val nextWakeAt: Long?,
)

object ReminderPlanner {
    const val STALE_MS = 3 * 60 * 60 * 1000L

    fun isQuiet(minutes: Int, s: ReminderSettings): Boolean {
        if (!s.quietEnabled || s.quietStart == s.quietEnd) return false
        return if (s.quietStart < s.quietEnd) minutes in s.quietStart until s.quietEnd
        else minutes >= s.quietStart || minutes < s.quietEnd
    }

    /** Next instant (millis) at which quiet hours end, assuming [now] is inside them. */
    fun quietEndAfter(now: Long, s: ReminderSettings, zone: ZoneId): Long {
        val today = Dates.today(now, zone)
        val endToday = Dates.toMillis(today, s.quietEnd, zone)
        return if (endToday > now) endToday else Dates.toMillis(today + 1, s.quietEnd, zone)
    }

    private fun enabled(c: ReminderCandidate, s: ReminderSettings) = when (c.source) {
        ReminderSource.TASK -> s.tasksEnabled
        ReminderSource.STOCK -> s.inventoryEnabled
        ReminderSource.EXPIRY -> s.expiryEnabled
        ReminderSource.HABIT -> s.habitsEnabled
        ReminderSource.DIGEST -> s.digestEnabled
    }

    fun plan(
        candidates: List<ReminderCandidate>,
        log: Map<String, Reminder>,
        now: Long,
        settings: ReminderSettings,
        zone: ZoneId = Dates.zone(),
    ): ReminderPlan {
        val active = candidates.filter { enabled(it, settings) }
        val quiet = isQuiet(Dates.minutesOfDay(now, zone), settings)

        fun dueAt(c: ReminderCandidate): Long? {
            val r = log[c.key] ?: return c.triggerAt
            return when (r.state) {
                ReminderState.SNOOZED -> r.snoozedUntil ?: now
                ReminderState.FIRED, ReminderState.ACKNOWLEDGED -> null
            }
        }

        val due = active.filter { c -> dueAt(c)?.let { it <= now } == true }
        val post = ArrayList<ReminderCandidate>()
        val silent = ArrayList<ReminderCandidate>()
        var deferredByQuiet = false
        // within a group, only the latest-stage due reminder is posted
        due.groupBy { it.group }.values.forEach { stages ->
            val sorted = stages.sortedBy { it.triggerAt }
            val latest = sorted.last()
            silent += sorted.dropLast(1)
            // Time-of-day nudges (habits, digest) are pointless hours later: record them silently.
            val stale = (latest.source == ReminderSource.HABIT || latest.source == ReminderSource.DIGEST) &&
                now - (dueAt(latest) ?: now) > STALE_MS
            when {
                stale -> silent += latest
                quiet && !latest.important -> deferredByQuiet = true
                else -> post += latest
            }
        }
        // Also silence earlier stages of groups whose later stage was already delivered.
        val firedKeys = log.filterValues { it.state != ReminderState.SNOOZED }.keys
        active.groupBy { it.group }.values.forEach { stages ->
            val latestFired = stages.filter { it.key in firedKeys }.maxOfOrNull { it.triggerAt } ?: return@forEach
            stages.filter { it.triggerAt < latestFired && it.key !in firedKeys && it !in silent && it !in post }
                .forEach { silent += it }
        }
        post.removeAll { p -> silent.any { it.key == p.key } }

        val candidateKeys = candidates.map { it.key }.toSet()
        val obsolete = log.values.filter { it.key !in candidateKeys }

        val handled = (post + silent).map { it.key }.toSet()
        var next = active.filter { it.key !in handled }
            .mapNotNull { dueAt(it) }
            .filter { it > now }
            .minOrNull()
        if (deferredByQuiet) {
            val end = quietEndAfter(now, settings, zone)
            next = next?.let { minOf(it, end) } ?: end
        }
        return ReminderPlan(post, silent, obsolete, next)
    }

    // ------------------------------------------------------------ candidate builders

    fun taskCandidates(
        occurrences: List<OccurrenceWithTask>,
        settings: ReminderSettings,
        zone: ZoneId = Dates.zone(),
    ): List<ReminderCandidate> = occurrences.mapNotNull { ot ->
        val o = ot.occurrence
        val t = ot.task
        if (o.status != OccurrenceStatus.PENDING && o.status != OccurrenceStatus.IN_PROGRESS) return@mapNotNull null
        if (!t.hasDueDate || t.reminderOffsetMinutes == null) return@mapNotNull null
        val minutes = o.dueTime ?: settings.morningMinutes
        val trigger = Dates.toMillis(o.dueDate, minutes, zone) - t.reminderOffsetMinutes * 60_000L
        val whenText = buildString {
            append(Dates.relative(o.dueDate, Dates.today(trigger, zone)))
            o.dueTime?.let { append(" at ").append(Dates.formatTime(it)) }
        }
        ReminderCandidate(
            key = "task:${o.id}:${o.dueDate}:${o.dueTime ?: -1}:${t.reminderOffsetMinutes}",
            group = "task:${o.id}",
            source = ReminderSource.TASK, sourceId = o.id, triggerAt = trigger,
            title = "${t.emoji.ifBlank { t.category.emoji }} ${t.title}",
            body = if (t.isRecurring) "${RecurrenceEngine.describe(t.rule)} · due $whenText" else "Due $whenText",
            actions = listOf(NotifAction.DONE, NotifAction.SNOOZE),
            important = t.priority.weight >= 3,
        )
    }

    fun stockCandidates(forecasts: List<StockForecast>, settings: ReminderSettings, now: Long, zone: ZoneId = Dates.zone()): List<ReminderCandidate> {
        val today = Dates.today(now, zone)
        val out = ArrayList<ReminderCandidate>()
        for (f in forecasts) {
            val item = f.item
            if (item.archived) continue
            val cycle = f.batches.maxOfOrNull { it.batch.id } ?: 0L
            val name = "${item.emoji} ${item.name}"
            val qty = "${f.estimatedQty.fmtQty()} ${item.unit.label}"
            if (f.reorderDate != null && f.depletionDate != null) {
                val lowDay = maxOf(f.reorderDate, today)
                val days = (f.depletionDate - lowDay).coerceAtLeast(0)
                out += ReminderCandidate(
                    key = "stock:${item.id}:low:$cycle", group = "stock:${item.id}:$cycle",
                    source = ReminderSource.STOCK, sourceId = item.id,
                    triggerAt = Dates.toMillis(lowDay, settings.morningMinutes, zone),
                    title = "$name may run out in $days day${if (days == 1L) "" else "s"}",
                    body = "About $qty left (estimate). Add it to your shopping list?",
                    actions = listOf(NotifAction.ADD_TO_SHOPPING, NotifAction.SNOOZE),
                )
                if (f.depletionDate > lowDay) out += ReminderCandidate(
                    key = "stock:${item.id}:out:$cycle", group = "stock:${item.id}:$cycle",
                    source = ReminderSource.STOCK, sourceId = item.id,
                    triggerAt = Dates.toMillis(f.depletionDate, settings.morningMinutes, zone),
                    title = "$name has probably run out",
                    body = "Based on your usage it's likely finished. Update stock or add it to the list.",
                    actions = listOf(NotifAction.ADD_TO_SHOPPING, NotifAction.SNOOZE),
                )
            } else if (f.stockCondition != StockCondition.IN_STOCK) {
                val out0 = f.stockCondition == StockCondition.OUT_OF_STOCK
                out += ReminderCandidate(
                    key = "stock:${item.id}:${if (out0) "out" else "low"}:$cycle", group = "stock:${item.id}:$cycle",
                    source = ReminderSource.STOCK, sourceId = item.id,
                    triggerAt = now,
                    title = if (out0) "$name is out of stock" else "$name is running low",
                    body = if (out0) "Add it to your shopping list?" else "Only $qty left. Add it to your shopping list?",
                    actions = listOf(NotifAction.ADD_TO_SHOPPING, NotifAction.SNOOZE),
                )
            }
        }
        return out
    }

    fun expiryCandidates(forecasts: List<StockForecast>, settings: ReminderSettings, zone: ZoneId = Dates.zone()): List<ReminderCandidate> {
        val out = ArrayList<ReminderCandidate>()
        for (f in forecasts) {
            val item = f.item
            if (item.archived) continue
            for (be in f.batches) {
                val exp = be.batch.expiryDate ?: continue
                val label = be.batch.expiryKind.label.lowercase()
                val name = "${item.emoji} ${item.name}"
                val group = "expiry:${be.batch.id}"
                val spoil = be.batch.expiryKind == com.example.impulse.data.ExpiryKind.ESTIMATED
                if (item.expiryWarnDays > 0) {
                    val warnDay = exp - item.expiryWarnDays
                    out += ReminderCandidate(
                        key = "expiry:${be.batch.id}:warn:$exp", group = group,
                        source = ReminderSource.EXPIRY, sourceId = be.batch.id,
                        triggerAt = Dates.toMillis(warnDay, settings.morningMinutes, zone),
                        title = if (spoil) "$name may spoil soon" else "$name expires soon",
                        body = "${if (spoil) "Estimated freshness" else label.replaceFirstChar { it.uppercase() }} date: ${Dates.formatShort(exp)}. Plan to use it first.",
                        actions = listOf(NotifAction.MARK_USED, NotifAction.SNOOZE),
                    )
                }
                out += ReminderCandidate(
                    key = "expiry:${be.batch.id}:day:$exp", group = group,
                    source = ReminderSource.EXPIRY, sourceId = be.batch.id,
                    triggerAt = Dates.toMillis(exp, settings.morningMinutes, zone),
                    title = if (spoil) "$name: freshness window ends today" else "$name reaches its $label date today",
                    body = "Check it before use. Mark it used or discarded to keep stock accurate.",
                    actions = listOf(NotifAction.MARK_USED, NotifAction.SNOOZE),
                )
            }
        }
        return out
    }

    fun habitCandidates(summaries: List<HabitSummary>, today: Long, zone: ZoneId = Dates.zone()): List<ReminderCandidate> =
        summaries.flatMap { s ->
            val h = s.habit
            val time = h.reminderTime ?: return@flatMap emptyList()
            if (h.archived) return@flatMap emptyList()
            listOf(today, today + 1).filter { d -> HabitEngine.isScheduled(h, d) && !(d == today && s.doneToday) }.map { d ->
                ReminderCandidate(
                    key = "habit:${h.id}:$d", group = "habit:${h.id}:$d",
                    source = ReminderSource.HABIT, sourceId = h.id,
                    triggerAt = Dates.toMillis(d, time, zone),
                    title = "${h.emoji} ${h.name}",
                    body = if (s.currentStreak > 0) "Keep your ${s.currentStreak}-${s.streakUnit} streak going!" else "A small step today builds the loop.",
                    actions = listOf(NotifAction.LOG_HABIT),
                )
            }
        }

    fun digestCandidates(today: Long, settings: ReminderSettings, body: String, zone: ZoneId = Dates.zone()): List<ReminderCandidate> =
        if (body.isBlank()) emptyList() else listOf(today, today + 1).map { d ->
            ReminderCandidate(
                key = "digest:$d", group = "digest:$d", source = ReminderSource.DIGEST, sourceId = d,
                triggerAt = Dates.toMillis(d, settings.digestMinutes, zone),
                title = "Your day at a glance", body = body, actions = listOf(NotifAction.OPEN),
            )
        }
}
