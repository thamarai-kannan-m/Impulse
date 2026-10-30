package com.example.impulse.domain

import com.example.impulse.data.RecurrenceRule
import com.example.impulse.data.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure recurrence calculations.
 *
 * Fixed-calendar rules (DAILY, WEEKLY, MONTHLY, YEARLY, EVERY_N_DAYS) produce dates purely from
 * the rule, regardless of completion — a missed Wednesday laundry does not move next Wednesday.
 * AFTER_COMPLETION rules produce the next date from the actual completion date.
 */
object RecurrenceEngine {

    /** All rule dates in [from, to] (inclusive, epochDays) for fixed-calendar rules. */
    fun datesBetween(rule: RecurrenceRule, from: Long, to: Long): List<Long> {
        val start = rule.startDate
        val lo = maxOf(from, start)
        val hi = rule.endDate?.let { minOf(to, it) } ?: to
        if (lo > hi) return emptyList()
        val interval = rule.interval.coerceAtLeast(1)
        val out = ArrayList<Long>()
        when (rule.type) {
            RepeatType.NONE, RepeatType.AFTER_COMPLETION ->
                if (start in lo..hi) out += start

            RepeatType.DAILY, RepeatType.EVERY_N_DAYS -> {
                // first d >= lo with (d - start) % interval == 0
                val offset = Math.floorMod(lo - start, interval.toLong())
                var d = if (offset == 0L) lo else lo + (interval - offset)
                while (d <= hi) { out += d; d += interval }
            }

            RepeatType.WEEKLY -> {
                val startDate = LocalDate.ofEpochDay(start)
                val mask = if (rule.weekdays == 0) Dates.bit(startDate.dayOfWeek) else rule.weekdays
                val anchorMonday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
                var d = lo
                while (d <= hi) {
                    val ld = LocalDate.ofEpochDay(d)
                    val weekIndex = Math.floorDiv(d - anchorMonday, 7L)
                    if (weekIndex % interval == 0L && Dates.hasDay(mask, ld.dayOfWeek)) out += d
                    d++
                }
            }

            RepeatType.MONTHLY -> {
                val startDate = LocalDate.ofEpochDay(start)
                val dom = (rule.dayOfMonth ?: startDate.dayOfMonth).coerceIn(1, 31)
                var month = startDate.withDayOfMonth(1)
                val loDate = LocalDate.ofEpochDay(lo)
                // jump close to `lo` without iterating from the start for old rules
                val monthsToLo = ChronoUnit.MONTHS.between(month, loDate.withDayOfMonth(1))
                if (monthsToLo > 0) month = month.plusMonths(monthsToLo - Math.floorMod(monthsToLo, interval.toLong()))
                while (true) {
                    val candidate = month.withDayOfMonth(minOf(dom, month.lengthOfMonth())).toEpochDay()
                    if (candidate > hi) break
                    if (candidate >= lo) out += candidate
                    month = month.plusMonths(interval.toLong())
                }
            }

            RepeatType.YEARLY -> {
                val startDate = LocalDate.ofEpochDay(start)
                var year = startDate.year
                val loYear = LocalDate.ofEpochDay(lo).year
                if (loYear > year) year += ((loYear - year) / interval) * interval
                while (true) {
                    val ym = java.time.YearMonth.of(year, startDate.month)
                    val candidate = ym.atDay(minOf(startDate.dayOfMonth, ym.lengthOfMonth())).toEpochDay()
                    if (candidate > hi) break
                    if (candidate >= lo) out += candidate
                    year += interval
                }
            }
        }
        return out
    }

    /** Next due date for a completion-based rule. */
    fun nextAfterCompletion(rule: RecurrenceRule, completedOn: Long): Long? {
        val next = completedOn + rule.interval.coerceAtLeast(1)
        return if (rule.endDate != null && next > rule.endDate) null else next
    }

    /** First rule date strictly after [after], or null if the rule has ended. */
    fun nextDate(rule: RecurrenceRule, after: Long): Long? {
        if (rule.type == RepeatType.AFTER_COMPLETION) return null
        var from = after + 1
        repeat(6) { // look ahead up to ~6 years in yearly chunks
            val found = datesBetween(rule, from, from + 400).firstOrNull()
            if (found != null) return found
            from += 401
            if (rule.endDate != null && from > rule.endDate) return null
        }
        return null
    }

    fun describe(rule: RecurrenceRule): String {
        val n = rule.interval.coerceAtLeast(1)
        val start = LocalDate.ofEpochDay(rule.startDate)
        return when (rule.type) {
            RepeatType.NONE -> "One time"
            RepeatType.DAILY -> if (n == 1) "Every day" else "Every $n days"
            RepeatType.EVERY_N_DAYS -> "Every $n days"
            RepeatType.WEEKLY -> {
                val mask = if (rule.weekdays == 0) Dates.bit(start.dayOfWeek) else rule.weekdays
                val days = when (mask) {
                    Dates.EVERY_DAY -> "every day"
                    Dates.WEEKDAYS -> "weekdays"
                    Dates.WEEKENDS -> "weekends"
                    else -> DayOfWeek.entries.filter { Dates.hasDay(mask, it) }
                        .joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }
                }
                if (n == 1) "Every $days" else "Every $n weeks · $days"
            }
            RepeatType.MONTHLY -> {
                val dom = rule.dayOfMonth ?: start.dayOfMonth
                if (n == 1) "Monthly on the ${ordinal(dom)}" else "Every $n months on the ${ordinal(dom)}"
            }
            RepeatType.YEARLY -> "Yearly on ${Dates.formatShort(rule.startDate)}"
            RepeatType.AFTER_COMPLETION -> "$n days after completion"
        }
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}
