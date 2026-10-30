# LifeLoop — *Everything in life, right on time.*

An offline-first Android app (Kotlin · Jetpack Compose · Material 3) that brings groceries, expiry dates, chores, recurring routines and habits into one calm daily dashboard.

## Open & run

1. Open this folder in **Android Studio** (the project was generated with AGP 9.3 / Gradle 9.5).
2. Let Gradle sync, then run the `app` configuration on a device or emulator (Android 8.0 / API 26+).
3. On first launch, keep **"Start with an example home"** switched on to explore every feature with sample data.

```bash
./gradlew :app:testDebugUnitTest   # 26 domain tests (forecasting, recurrence, habits, reminders)
./gradlew :app:assembleDebug
```

> If Gradle can't resolve a library version, use Android Studio's *"Update to latest"* quick-fix in
> `gradle/libs.versions.toml`. If KSP reports a problem with AGP's built-in Kotlin, bump `ksp` to the
> newest 2.x release.

## What's inside

| Area | Highlights |
| --- | --- |
| **Today** | Gradient "today's loop" progress ring, 4 live stat tiles, prioritised *Needs attention* (overdue → expiry → low stock), today's tasks, pending chores, habit bubbles, 7-day look-ahead, quick-add speed dial |
| **Inventory** | Search + filters (attention / low / expiring / location), animated stock bars, per-item forecast card (remaining, daily use, days left, reorder date), quick stock checks (*Used some · Half left · Almost empty · Finished · Set exact*), purchase batches with their own expiry, full history |
| **Shopping list** | Smart "running low" suggestions, one-tap add-all, check-off, *Restock* straight from the list |
| **Tasks** | Week strip agenda, swipe right = done, swipe left = tomorrow, action sheet (start, postpone, pick date, skip, delete one / delete series), routines overview, completed history with undo |
| **Recurrence** | Daily, weekly (multi-day, every N weeks), monthly (day clamping), yearly, every N days, *N days after completion*, end dates, missed-occurrence policy (keep overdue / auto-skip) |
| **Habits** | Yes/No, amount, duration, times-per-week, avoidance; streaks, best streak, 30-day rate, week rings, 15-week heatmap (tap to fix a day) |
| **Insights** | Weekly completion chart, on-time / completion / habit rates, top streaks, 30-day spend by category, discarded food, most-used items |
| **Reminders** | Low stock, expiry (warn + day-of), tasks, habits, daily digest; Done / Snooze / Add to list / Used it / Log it actions; quiet hours; per-module switches; never duplicated |
| **Settings** | Theme (system / light / dark), Material You colours, currency, household members for chore assignment, JSON backup export / restore, example data |
| **Polish** | Onboarding with animated loop, haptics, bouncy check animations, snackbar undo, edge-to-edge, dark mode, app shortcuts (Add item / Add task / Shopping list), custom adaptive icon |

## Architecture

```
ui/            Compose screens, components, theme, ViewModels (MVVM, StateFlow)
domain/        Pure Kotlin business rules — no Android imports, fully unit-tested
  Forecast.kt        stock projection, FEFO consumption, usage learning
  Recurrence.kt      calendar + completion-based schedules
  HabitStats.kt      streaks, rates, weekly targets
  ReminderPlanner.kt which reminders should exist / fire now / next alarm
  Templates.kt       smart defaults (26 items, 12 chores, 9 habits)
data/          Room entities + DAOs, repositories, DataStore settings, JSON backup, sample data
notifications/ ReminderEngine (reconcile loop), Notifier, receivers, WorkManager safety net
```

**Key design decisions**

- **Reconcile, don't schedule-and-forget.** After any data change (and on alarm, boot, time-zone change, app resume, and a 6-hourly WorkManager job) the engine derives every reminder that *should* exist, compares with a log keyed by stable IDs (`expiry:{batch}:warn:{date}`), posts what's due, cancels what's resolved and sets **one** alarm for the next trigger. This gives duplicate-free, self-healing reminders that survive reboots and edits.
- **Stock and expiry are independent.** Milk can be *low* and *expired* at the same time. Each purchase is its own batch with its own expiry; consumption is allocated first-expiring-first-out.
- **Transparent predictions.** Duration mode (*5 kg lasts 30 days*), learn-from-usage mode (exponential smoothing over stock checks) or manual. Every estimate is labelled as an estimate.
- **Idempotent recurrence.** Occurrences have a unique `(taskId, originalDate)` index; postponing changes `dueDate` but never the original date, so regeneration can't create duplicates.
- **Habits never become backlog.** A missed day lowers a streak; it never creates an overdue task.
- **Date semantics.** Dates are floating local `epochDay`s, times are minutes after midnight, instants are UTC millis; triggers are recomputed whenever the zone changes.
- **Manual DI** (`AppContainer`) instead of Hilt to keep the build simple; swapping in Hilt later is mechanical.
- **Room migrations, not destructive resets.** Schemas are exported to `app/schemas`; add `Migration`s to `LifeLoopDatabase.MIGRATIONS`.

## Acceptance criteria coverage

AC-01…AC-07, AC-10, AC-13, AC-14 are covered by `app/src/test/.../DomainTest.kt`. AC-08/09 (overdue handling), AC-11 (offline — no network permission at all), AC-12 (`BOOT_COMPLETED` → reconcile) and AC-15 (restock = new batch + forecast reset) are implemented in the repositories and engine.

## Not included (need a backend or extra SDKs)

Cloud sync / shared households across phones, barcode & receipt OCR scanning, and AI suggestions (Phase 3). Household members exist locally for chore assignment; the data model and JSON backup are ready for a sync layer.
