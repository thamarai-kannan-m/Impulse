package com.example.impulse.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Small helpers so the rest of the code can speak in epochDay / minutes-of-day. */
object Dates {
    fun zone(): ZoneId = ZoneId.systemDefault()

    fun today(clock: Long = System.currentTimeMillis(), zone: ZoneId = zone()): Long =
        Instant.ofEpochMilli(clock).atZone(zone).toLocalDate().toEpochDay()

    fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun epochDayOf(millis: Long, zone: ZoneId = zone()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun minutesOfDay(millis: Long, zone: ZoneId = zone()): Int {
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** Wall-clock (date, minutes) → instant in [zone]. Handles DST gaps via ZonedDateTime rules. */
    fun toMillis(epochDay: Long, minutes: Int, zone: ZoneId = zone()): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.of(minutes / 60 % 24, minutes % 60))
            .atZone(zone).toInstant().toEpochMilli()

    fun daysBetween(fromMillis: Long, toMillis: Long): Double =
        (toMillis - fromMillis).toDouble() / ChronoUnit.DAYS.duration.toMillis()

    // ---- weekday masks (Mon = bit 0 … Sun = bit 6)
    fun bit(dow: DayOfWeek): Int = 1 shl (dow.value - 1)
    fun hasDay(mask: Int, dow: DayOfWeek): Boolean = mask and bit(dow) != 0
    fun maskOf(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or bit(d) }
    const val EVERY_DAY = 127
    const val WEEKDAYS = 31
    const val WEEKENDS = 96

    // ---- formatting
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun formatTime(minutes: Int): String = LocalTime.of(minutes / 60 % 24, minutes % 60).format(timeFmt)
    fun formatShort(epochDay: Long): String = date(epochDay).format(shortDate)
    fun formatLong(epochDay: Long): String = date(epochDay).format(longDate)
    fun formatFull(epochDay: Long): String = date(epochDay).format(fullDate)
    fun dayShort(dow: DayOfWeek): String = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    fun dayNarrow(dow: DayOfWeek): String = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    /** "Today", "Tomorrow", "Yesterday", "in 3 days", "2 days ago", or a date. */
    fun relative(epochDay: Long, today: Long = today()): String = when (val d = epochDay - today) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        in 2..6 -> "in $d days"
        in -6..-2 -> "${-d} days ago"
        else -> formatShort(epochDay)
    }

    fun weekdaysLabel(mask: Int): String = when (mask) {
        EVERY_DAY -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        0 -> "No days"
        else -> DayOfWeek.entries.filter { hasDay(mask, it) }.joinToString(", ") { dayShort(it) }
    }

    fun greeting(minutes: Int): String = when (minutes / 60) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
}

/** Formats a quantity without noisy trailing zeros: 5.0 → "5", 0.25 → "0.25". */
fun Double.fmtQty(): String {
    val r = Math.round(this * 100.0) / 100.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
}

fun Double.fmtMoney(symbol: String = "₹"): String = symbol + String.format(Locale.getDefault(), "%,.0f", this)
