package com.example.impulse

import com.example.impulse.data.BatchStatus
import com.example.impulse.data.ConsumptionMode
import com.example.impulse.data.ExpiryKind
import com.example.impulse.data.Habit
import com.example.impulse.data.HabitLog
import com.example.impulse.data.HabitType
import com.example.impulse.data.InventoryBatch
import com.example.impulse.data.InventoryItem
import com.example.impulse.data.OccurrenceWithTask
import com.example.impulse.data.RecurrenceRule
import com.example.impulse.data.Reminder
import com.example.impulse.data.ReminderSource
import com.example.impulse.data.ReminderState
import com.example.impulse.data.RepeatType
import com.example.impulse.data.StockUnit
import com.example.impulse.data.Task
import com.example.impulse.data.TaskOccurrence
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ExpiryCondition
import com.example.impulse.domain.ForecastEngine
import com.example.impulse.domain.HabitEngine
import com.example.impulse.domain.RecurrenceEngine
import com.example.impulse.domain.ReminderCandidate
import com.example.impulse.domain.ReminderPlanner
import com.example.impulse.domain.ReminderSettings
import com.example.impulse.domain.StockCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Pure-Kotlin tests for LifeLoop's business rules, mapped to the spec's acceptance criteria. */
class DomainTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = 86_400_000L
    private val today = LocalDate.of(2026, 10, 28).toEpochDay()            // a Wednesday
    private val now = Dates.toMillis(today, 10 * 60, zone)

    private fun rice(checkedDaysAgo: Double = 0.0) = InventoryItem(
        id = 1, name = "Rice", unit = StockUnit.KG, consumptionMode = ConsumptionMode.DURATION,
        typicalDurationDays = 30, reorderLeadDays = 5, stockCheckedAt = now - (checkedDaysAgo * day).toLong(),
    )
    private fun batch(id: Long, qty: Double, remaining: Double = qty, expiry: Long? = null, purchase: Long = today) =
        InventoryBatch(id = id, itemId = 1, purchaseDate = purchase, quantity = qty, remaining = remaining, expiryDate = expiry, expectedDurationDays = 30)

    // ------------------------------------------------------------------ inventory

    @Test fun ac01_riceDepletionEstimated() {
        val f = ForecastEngine.forecast(rice(), listOf(batch(1, 5.0)), now, today)
        assertEquals(5.0 / 30, f.dailyUsage!!, 1e-9)
        assertEquals(30.0, f.daysRemaining!!, 1e-6)
        assertEquals(today + 30, f.depletionDate)
        assertEquals(today + 25, f.reorderDate)
        assertEquals(StockCondition.IN_STOCK, f.stockCondition)
    }

    @Test fun ac01_projectionDecaysOverTime() {
        val f = ForecastEngine.forecast(rice(checkedDaysAgo = 6.0), listOf(batch(1, 5.0, purchase = today - 6)), now, today)
        assertEquals(4.0, f.estimatedQty, 1e-6)
        assertEquals(24.0, f.daysRemaining!!, 1e-6)
        assertTrue(f.isEstimated)
    }

    @Test fun ac02_adjustingQuantityRecalculates() {
        val f = ForecastEngine.forecast(rice(), listOf(batch(1, 5.0, remaining = 1.0)), now, today)
        assertEquals(6.0, f.daysRemaining!!, 1e-6)
        assertEquals(StockCondition.IN_STOCK, f.stockCondition)
        val g = ForecastEngine.forecast(rice(), listOf(batch(1, 5.0, remaining = 0.8)), now, today)
        assertEquals(StockCondition.LOW_STOCK, g.stockCondition) // 4.8 days <= 5 lead days
    }

    @Test fun stockAndExpiryAreIndependent() {
        val milk = rice().copy(name = "Milk", typicalDurationDays = 2, expiryWarnDays = 2)
        val f = ForecastEngine.forecast(milk, listOf(batch(1, 1.0, remaining = 0.1, expiry = today - 1)), now, today)
        assertEquals(StockCondition.LOW_STOCK, f.stockCondition)
        assertEquals(ExpiryCondition.EXPIRED, f.expiryCondition)
    }

    @Test fun ac05_batchesKeepOwnExpiry() {
        val milk = rice().copy(name = "Milk", typicalDurationDays = 2, expiryWarnDays = 1)
        val f = ForecastEngine.forecast(milk, listOf(batch(1, 1.0, expiry = today + 1), batch(2, 1.0, expiry = today + 4)), now, today)
        assertEquals(2, f.batches.size)
        assertEquals(ExpiryCondition.EXPIRING_SOON, f.batches[0].condition)
        assertEquals(ExpiryCondition.FRESH, f.batches[1].condition)
    }

    @Test fun consumptionIsFirstExpiringFirstOut() {
        val alloc = ForecastEngine.allocateConsumption(listOf(batch(1, 1.0, expiry = today + 5), batch(2, 1.0, expiry = today + 1)), 1.5)
        assertEquals(0.0, alloc[2]!!, 1e-9)
        assertEquals(0.5, alloc[1]!!, 1e-9)
    }

    @Test fun usageLearningSmooths() {
        val (u1, s1) = ForecastEngine.learnUsage(null, 0, previousQty = 1.0, newQty = 0.6, days = 4.0)
        assertEquals(0.1, u1!!, 1e-9); assertEquals(1, s1)
        val (u2, s2) = ForecastEngine.learnUsage(u1, s1, previousQty = 0.6, newQty = 0.0, days = 2.0)
        assertEquals(0.2, u2!!, 1e-9); assertEquals(2, s2)
        val (u3, _) = ForecastEngine.learnUsage(u2, s2, 0.5, 0.9, 1.0) // stock went up: ignored
        assertEquals(u2, u3!!, 1e-9)
    }

    @Test fun manualModeHasNoPrediction() {
        val eggs = rice().copy(consumptionMode = ConsumptionMode.MANUAL, lowStockThreshold = 4.0, unit = StockUnit.PCS)
        val f = ForecastEngine.forecast(eggs, listOf(batch(1, 12.0, remaining = 3.0)), now, today)
        assertNull(f.daysRemaining)
        assertEquals(StockCondition.LOW_STOCK, f.stockCondition)
    }

    @Test fun unitConversion() {
        assertEquals(500.0, StockUnit.KG.convert(0.5, StockUnit.G)!!, 1e-9)
        assertNull(StockUnit.KG.convert(1.0, StockUnit.L))
    }

    // ------------------------------------------------------------------ recurrence

    private fun rule(type: RepeatType, start: Long = today, interval: Int = 1, weekdays: Int = 0, dom: Int? = null, end: Long? = null) =
        RecurrenceRule(type, interval, weekdays, dom, start, end)

    @Test fun ac06_weeklyLaundryOnWednesdays() {
        val dates = RecurrenceEngine.datesBetween(rule(RepeatType.WEEKLY, weekdays = Dates.maskOf(DayOfWeek.WEDNESDAY)), today, today + 27)
        assertEquals(listOf(today, today + 7, today + 14, today + 21), dates)
        assertTrue(dates.all { LocalDate.ofEpochDay(it).dayOfWeek == DayOfWeek.WEDNESDAY })
    }

    @Test fun multipleWeekdaysAndBiweekly() {
        val mt = RecurrenceEngine.datesBetween(rule(RepeatType.WEEKLY, weekdays = Dates.maskOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), today, today + 13)
        assertEquals(4, mt.size)
        val bi = RecurrenceEngine.datesBetween(rule(RepeatType.WEEKLY, interval = 2, weekdays = Dates.maskOf(DayOfWeek.WEDNESDAY)), today, today + 35)
        assertEquals(listOf(today, today + 14, today + 28), bi)
    }

    @Test fun monthlyOnTheTenthAndClamping() {
        val r = rule(RepeatType.MONTHLY, start = LocalDate.of(2026, 1, 31).toEpochDay(), dom = 31)
        val dates = RecurrenceEngine.datesBetween(r, LocalDate.of(2026, 2, 1).toEpochDay(), LocalDate.of(2026, 4, 30).toEpochDay())
        assertEquals(listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)).map { it.toEpochDay() }, dates)
        val bill = RecurrenceEngine.datesBetween(rule(RepeatType.MONTHLY, start = today - 100, dom = 10), today, today + 60)
        assertTrue(bill.all { LocalDate.ofEpochDay(it).dayOfMonth == 10 })
        assertEquals(2, bill.size)
    }

    @Test fun everyNDaysAndEndDate() {
        val d = RecurrenceEngine.datesBetween(rule(RepeatType.EVERY_N_DAYS, start = today - 13, interval = 15, end = today + 20), today - 20, today + 40)
        assertEquals(listOf(today - 13, today + 2, today + 17), d)
    }

    @Test fun yearly() {
        val r = rule(RepeatType.YEARLY, start = LocalDate.of(2024, 2, 29).toEpochDay())
        val d = RecurrenceEngine.datesBetween(r, LocalDate.of(2025, 1, 1).toEpochDay(), LocalDate.of(2028, 12, 31).toEpochDay())
        assertEquals(listOf(LocalDate.of(2025, 2, 28), LocalDate.of(2026, 2, 28), LocalDate.of(2027, 2, 28), LocalDate.of(2028, 2, 29)).map { it.toEpochDay() }, d)
    }

    @Test fun completionBasedFromActualDate() {
        val r = rule(RepeatType.AFTER_COMPLETION, interval = 90)
        assertEquals(today + 5 + 90, RecurrenceEngine.nextAfterCompletion(r, today + 5))
        assertNotNull(RecurrenceEngine.describe(r))
    }

    @Test fun ac07_missedOccurrenceDoesNotShiftFixedSchedule() {
        // Fixed-calendar: the next date only depends on the rule, never on completion.
        val r = rule(RepeatType.WEEKLY, weekdays = Dates.maskOf(DayOfWeek.WEDNESDAY))
        assertEquals(today + 7, RecurrenceEngine.nextDate(r, today))
        assertEquals(today + 7, RecurrenceEngine.nextDate(r, today + 3))
    }

    // ------------------------------------------------------------------ habits

    @Test fun ac10_habitStreaksAndRate() {
        val h = Habit(id = 1, name = "Exercise", createdAt = now - 20 * day)
        val logs = (1..5).map { HabitLog(habitId = 1, date = today - it, value = 1.0) } + HabitLog(habitId = 1, date = today - 7, value = 1.0)
        val s = HabitEngine.summarize(h, logs, today)
        assertEquals(5, s.currentStreak)  // today not done yet does not break the streak
        assertFalse(s.doneToday)
        val s2 = HabitEngine.summarize(h, logs + HabitLog(habitId = 1, date = today, value = 1.0), today)
        assertEquals(6, s2.currentStreak)
        assertTrue(s2.doneToday)
    }

    @Test fun quantityHabitTarget() {
        val h = Habit(id = 2, name = "Water", type = HabitType.QUANTITY, target = 2000.0, unit = "mL", createdAt = now)
        val s = HabitEngine.summarize(h, listOf(HabitLog(habitId = 2, date = today, value = 1500.0)), today)
        assertEquals(0.75f, s.todayFraction, 1e-4f)
        assertFalse(s.doneToday)
    }

    @Test fun frequencyHabitWeeks() {
        val h = Habit(id = 3, name = "Workout", type = HabitType.FREQUENCY, target = 2.0, createdAt = now - 30 * day)
        // Mon & Tue this week (today is Wed) + two days in each of the previous 2 weeks
        val days = listOf(today - 2, today - 1, today - 9, today - 8, today - 16, today - 15)
        val s = HabitEngine.summarize(h, days.map { HabitLog(habitId = 3, date = it, value = 1.0) }, today)
        assertEquals(2, s.weekCount)
        assertEquals(3, s.currentStreak)
        assertEquals("wk", s.streakUnit)
    }

    // ------------------------------------------------------------------ reminders

    private val settings = ReminderSettings(quietEnabled = true, quietStart = 22 * 60, quietEnd = 7 * 60, morningMinutes = 9 * 60)

    private fun cand(key: String, at: Long, group: String = key, source: ReminderSource = ReminderSource.TASK) =
        ReminderCandidate(key, group, source, 1, at, key, "")

    @Test fun ac03_reminderFiresOncePerStage() {
        val c = cand("stock:1:low:1", now - 1000, source = ReminderSource.STOCK)
        val p1 = ReminderPlanner.plan(listOf(c), emptyMap(), now, settings, zone)
        assertEquals(1, p1.post.size)
        val log = mapOf(c.key to Reminder(c.key, ReminderSource.STOCK, 1, c.triggerAt, ReminderState.FIRED))
        val p2 = ReminderPlanner.plan(listOf(c), log, now + 60_000, settings, zone)
        assertTrue(p2.post.isEmpty())
    }

    @Test fun onlyLatestStageOfAGroupIsPosted() {
        val warn = cand("expiry:5:warn", now - 3 * day, group = "expiry:5", source = ReminderSource.EXPIRY)
        val dayOf = cand("expiry:5:day", now - day, group = "expiry:5", source = ReminderSource.EXPIRY)
        val p = ReminderPlanner.plan(listOf(warn, dayOf), emptyMap(), now, settings, zone)
        assertEquals(listOf("expiry:5:day"), p.post.map { it.key })
        assertEquals(listOf("expiry:5:warn"), p.markSilently.map { it.key })
    }

    @Test fun ac13_resolvedRemindersBecomeObsoleteAndNextAlarmIsEarliest() {
        val old = Reminder("task:9:1:-1:0", ReminderSource.TASK, 9, now - day, ReminderState.FIRED)
        val future1 = cand("a", now + 3_600_000); val future2 = cand("b", now + 7_200_000)
        val p = ReminderPlanner.plan(listOf(future1, future2), mapOf(old.key to old), now, settings, zone)
        assertEquals(listOf(old.key), p.obsolete.map { it.key })
        assertEquals(now + 3_600_000, p.nextWakeAt)
    }

    @Test fun quietHoursDeferNonUrgent() {
        val night = Dates.toMillis(today, 23 * 60, zone)
        val c = cand("t", night - 1000)
        val p = ReminderPlanner.plan(listOf(c), emptyMap(), night, settings, zone)
        assertTrue(p.post.isEmpty())
        assertEquals(Dates.toMillis(today + 1, 7 * 60, zone), p.nextWakeAt)
        val urgent = c.copy(important = true)
        assertEquals(1, ReminderPlanner.plan(listOf(urgent), emptyMap(), night, settings, zone).post.size)
    }

    @Test fun snoozedReminderComesBack() {
        val c = cand("t", now - day)
        val log = mapOf("t" to Reminder("t", ReminderSource.TASK, 1, now - day, ReminderState.SNOOZED, snoozedUntil = now + 3_600_000))
        val p = ReminderPlanner.plan(listOf(c), log, now, settings, zone)
        assertTrue(p.post.isEmpty())
        assertEquals(now + 3_600_000, p.nextWakeAt)
        assertEquals(1, ReminderPlanner.plan(listOf(c), log, now + 3_600_001, settings, zone).post.size)
    }

    @Test fun ac04_taskAndExpiryCandidates() {
        val task = Task(id = 1, title = "Wash clothes", timeOfDay = 19 * 60, rule = RecurrenceRule(RepeatType.WEEKLY, weekdays = Dates.maskOf(DayOfWeek.WEDNESDAY), startDate = today))
        val occ = OccurrenceWithTask(TaskOccurrence(id = 7, taskId = 1, originalDate = today, dueDate = today, dueTime = 19 * 60), task)
        val tc = ReminderPlanner.taskCandidates(listOf(occ), settings, zone)
        assertEquals(Dates.toMillis(today, 19 * 60, zone), tc.single().triggerAt)

        val milk = rice().copy(name = "Milk", expiryWarnDays = 2)
        val f = ForecastEngine.forecast(milk, listOf(batch(3, 2.0, expiry = today + 6).copy(expiryKind = ExpiryKind.USE_BY)), now, today)
        val ec = ReminderPlanner.expiryCandidates(listOf(f), settings, zone)
        assertEquals(setOf(Dates.toMillis(today + 4, 9 * 60, zone), Dates.toMillis(today + 6, 9 * 60, zone)), ec.map { it.triggerAt }.toSet())
    }

    @Test fun ac14_discardedBatchHasNoExpiryAlerts() {
        val milk = rice().copy(name = "Milk")
        val f = ForecastEngine.forecast(milk, listOf(batch(3, 1.0, remaining = 0.0, expiry = today - 1).copy(status = BatchStatus.DISCARDED)), now, today)
        assertTrue(f.batches.isEmpty())
        assertEquals(StockCondition.OUT_OF_STOCK, f.stockCondition)
        assertTrue(ReminderPlanner.expiryCandidates(listOf(f), settings, zone).isEmpty())
    }
}
