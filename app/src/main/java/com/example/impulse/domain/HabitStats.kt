package com.example.impulse.domain

import com.example.impulse.data.Habit
import com.example.impulse.data.HabitLog
import com.example.impulse.data.HabitType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HabitDay(val date: Long, val scheduled: Boolean, val value: Double?, val done: Boolean, val slipped: Boolean)

data class HabitSummary(
    val habit: Habit,
    val todayValue: Double,
    val todayFraction: Float,
    val doneToday: Boolean,
    val scheduledToday: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
    val streakUnit: String,
    /** Completion rate over the last 30 days (0..1). */
    val rate30: Float,
    /** This week's progress for FREQUENCY habits (count, target). */
    val weekCount: Int,
    val last7: List<HabitDay>,
)

/**
 * Habit maths. Missed habit days lower streaks and rates but never create backlog work.
 */
object HabitEngine {

    fun isScheduled(habit: Habit, date: Long): Boolean =
        habit.type == HabitType.FREQUENCY || Dates.hasDay(habit.weekdays, LocalDate.ofEpochDay(date).dayOfWeek)

    fun isDone(habit: Habit, value: Double?): Boolean {
        if (value == null) return false
        return when (habit.type) {
            HabitType.BINARY, HabitType.FREQUENCY, HabitType.AVOIDANCE -> value >= 1.0
            HabitType.QUANTITY, HabitType.DURATION -> value >= habit.target
        }
    }

    fun fraction(habit: Habit, value: Double?): Float = when (habit.type) {
        HabitType.QUANTITY, HabitType.DURATION ->
            if (habit.target <= 0) 0f else ((value ?: 0.0) / habit.target).toFloat().coerceIn(0f, 1f)
        else -> if (isDone(habit, value)) 1f else 0f
    }

    private fun weekStart(date: Long): Long =
        LocalDate.ofEpochDay(date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

    fun summarize(habit: Habit, logs: List<HabitLog>, today: Long): HabitSummary {
        val byDate = logs.associate { it.date to it.value }
        val created = Dates.epochDayOf(habit.createdAt).coerceAtMost(today)
        val firstDay = minOf(created, logs.minOfOrNull { it.date } ?: created)
        val todayValue = byDate[today] ?: 0.0

        val current: Int
        val best: Int
        val unit: String
        val weekCount = (weekStart(today)..today).count { isDone(habit, byDate[it]) }

        if (habit.type == HabitType.FREQUENCY) {
            unit = "wk"
            val target = habit.target.toInt().coerceAtLeast(1)
            fun weekMet(ws: Long) = (ws..ws + 6).count { isDone(habit, byDate[it]) } >= target
            val thisWeek = weekStart(today)
            var ws = if (weekMet(thisWeek)) thisWeek else thisWeek - 7
            var c = 0
            while (ws >= weekStart(firstDay) && weekMet(ws)) { c++; ws -= 7 }
            current = c
            var b = 0; var run = 0; var w = weekStart(firstDay)
            while (w <= thisWeek) { if (weekMet(w)) { run++; b = maxOf(b, run) } else if (w != thisWeek) run = 0; w += 7 }
            best = maxOf(b, current)
        } else {
            unit = "day"
            var d = if (isDone(habit, byDate[today]) || !isScheduled(habit, today)) today else today - 1
            var c = 0
            while (d >= firstDay) {
                if (isScheduled(habit, d)) { if (isDone(habit, byDate[d])) c++ else break }
                d--
            }
            current = c
            var b = 0; var run = 0
            for (day in firstDay..today) {
                if (!isScheduled(habit, day)) continue
                if (isDone(habit, byDate[day])) { run++; b = maxOf(b, run) } else if (day != today) run = 0
            }
            best = maxOf(b, current)
        }

        val windowStart = maxOf(firstDay, today - 29)
        val rate = if (habit.type == HabitType.FREQUENCY) {
            val target = habit.target.coerceAtLeast(1.0)
            val days = (today - windowStart + 1).toDouble()
            val done = (windowStart..today).count { isDone(habit, byDate[it]) }
            (done / (target * days / 7.0)).toFloat().coerceIn(0f, 1f)
        } else {
            // today only counts once it is done, so the rate doesn't dip every morning
            val scheduled = (windowStart..today).filter { isScheduled(habit, it) && (it != today || isDone(habit, byDate[it])) }
            if (scheduled.isEmpty()) 0f else scheduled.count { isDone(habit, byDate[it]) }.toFloat() / scheduled.size
        }

        val last7 = (today - 6..today).map { day ->
            val v = byDate[day]
            HabitDay(day, isScheduled(habit, day), v, isDone(habit, v), habit.type == HabitType.AVOIDANCE && (v ?: 0.0) < 0)
        }

        return HabitSummary(
            habit = habit,
            todayValue = todayValue,
            todayFraction = if (habit.type == HabitType.FREQUENCY)
                (weekCount / habit.target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) else fraction(habit, byDate[today]),
            doneToday = isDone(habit, byDate[today]),
            scheduledToday = isScheduled(habit, today),
            currentStreak = current, bestStreak = best, streakUnit = unit,
            rate30 = rate, weekCount = weekCount, last7 = last7,
        )
    }
}
