package com.example.impulse.data

import androidx.room.withTransaction
import com.example.impulse.domain.Dates
import com.example.impulse.domain.RecurrenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRepository(
    private val db: LifeLoopDatabase,
    private val onChanged: () -> Unit,
) {
    private val dao = db.taskDao()
    private val genLock = Mutex()

    val tasks: Flow<List<Task>> = dao.observeTasks()

    fun window(from: Long, to: Long): Flow<List<OccurrenceWithTask>> = dao.observeWindow(from, to)
    fun range(from: Long, to: Long): Flow<List<OccurrenceWithTask>> = dao.observeRange(from, to)
    fun completedSince(since: Long) = dao.observeCompletedSince(since)

    suspend fun task(id: Long) = dao.task(id)
    suspend fun occurrence(id: Long) = dao.occurrenceWithTask(id)
    suspend fun openUntil(day: Long) = dao.openUntil(day)
    suspend fun history(taskId: Long) = dao.occurrencesFor(taskId)

    // ------------------------------------------------------------------ generation

    /**
     * Materialises occurrences for every active task up to [HORIZON_DAYS] ahead.
     * Safe to call any number of times: the unique (taskId, originalDate) index makes it idempotent.
     */
    suspend fun ensureOccurrences(today: Long = Dates.today()) = genLock.withLock {
        db.withTransaction {
            dao.allTasks().filter { !it.archived }.forEach { generateFor(it, today) }
        }
    }

    private suspend fun generateFor(task: Task, today: Long) {
        val rule = task.rule
        when (rule.type) {
            RepeatType.NONE -> if (task.generatedUntil == null) {
                dao.insertOccurrences(listOf(TaskOccurrence(taskId = task.id, originalDate = rule.startDate, dueDate = rule.startDate, dueTime = task.timeOfDay)))
                dao.updateTask(task.copy(generatedUntil = rule.startDate))
            }

            RepeatType.AFTER_COMPLETION -> if (task.generatedUntil == null) {
                dao.insertOccurrences(listOf(TaskOccurrence(taskId = task.id, originalDate = rule.startDate, dueDate = rule.startDate, dueTime = task.timeOfDay)))
                dao.updateTask(task.copy(generatedUntil = rule.startDate))
            }

            else -> {
                val from = maxOf(task.generatedUntil?.plus(1) ?: rule.startDate, today - BACKFILL_DAYS)
                val to = today + HORIZON_DAYS
                if (from <= to) {
                    val dates = RecurrenceEngine.datesBetween(rule, from, to)
                    if (dates.isNotEmpty()) dao.insertOccurrences(dates.map {
                        TaskOccurrence(taskId = task.id, originalDate = it, dueDate = it, dueTime = task.timeOfDay)
                    })
                    dao.updateTask(task.copy(generatedUntil = to))
                }
                if (task.missedPolicy == MissedPolicy.AUTO_SKIP) autoSkip(task, today)
            }
        }
    }

    /** With AUTO_SKIP, an open occurrence is skipped once a newer one has come due. */
    private suspend fun autoSkip(task: Task, today: Long) {
        val all = dao.occurrencesFor(task.id)
        val latestDue = all.filter { it.dueDate <= today && it.status != OccurrenceStatus.CANCELLED }.maxOfOrNull { it.dueDate } ?: return
        all.filter { (it.status == OccurrenceStatus.PENDING) && it.dueDate < latestDue }
            .forEach { dao.updateOccurrence(it.copy(status = OccurrenceStatus.SKIPPED)) }
    }

    // ------------------------------------------------------------------ task CRUD

    suspend fun addTask(task: Task): Long {
        val id = dao.insertTask(task.copy(generatedUntil = null))
        dao.task(id)?.let { genLock.withLock { db.withTransaction { generateFor(it, Dates.today()) } } }
        onChanged()
        return id
    }

    /** Saves edits; future pending occurrences are regenerated from the new rule. */
    suspend fun updateTask(task: Task) {
        val today = Dates.today()
        genLock.withLock {
            db.withTransaction {
                val old = dao.task(task.id) ?: return@withTransaction
                val scheduleChanged = old.rule != task.rule || old.timeOfDay != task.timeOfDay || old.hasDueDate != task.hasDueDate
                if (!scheduleChanged) {
                    dao.updateTask(task.copy(generatedUntil = old.generatedUntil)); return@withTransaction
                }
                if (task.rule.type == RepeatType.NONE || task.rule.type == RepeatType.AFTER_COMPLETION) {
                    // Single open occurrence: move it to the new date/time instead of duplicating.
                    val open = dao.occurrencesFor(task.id).filter { it.status == OccurrenceStatus.PENDING || it.status == OccurrenceStatus.IN_PROGRESS }
                    if (open.isNotEmpty()) {
                        open.forEachIndexed { i, o ->
                            if (i == 0) dao.updateOccurrence(o.copy(dueDate = task.rule.startDate, dueTime = task.timeOfDay))
                            else dao.updateOccurrence(o.copy(status = OccurrenceStatus.CANCELLED))
                        }
                        dao.updateTask(task.copy(generatedUntil = task.rule.startDate))
                    } else {
                        dao.updateTask(task.copy(generatedUntil = null))
                        dao.task(task.id)?.let { generateFor(it, today) }
                    }
                } else {
                    dao.deletePendingFrom(task.id, today)
                    dao.updateTask(task.copy(generatedUntil = today - 1))
                    dao.task(task.id)?.let { generateFor(it, today) }
                }
            }
        }
        onChanged()
    }

    suspend fun deleteSeries(taskId: Long) { dao.deleteTask(taskId); onChanged() }

    suspend fun setArchived(taskId: Long, archived: Boolean) {
        dao.task(taskId)?.let { dao.updateTask(it.copy(archived = archived)) }
        if (archived) dao.deletePendingFrom(taskId, Dates.today())
        onChanged()
    }

    // ------------------------------------------------------------------ occurrence actions

    suspend fun complete(occurrenceId: Long) {
        db.withTransaction {
            val ot = dao.occurrenceWithTask(occurrenceId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            dao.updateOccurrence(ot.occurrence.copy(status = OccurrenceStatus.COMPLETED, completedAt = now))
            val t = ot.task
            if (t.rule.type == RepeatType.AFTER_COMPLETION) {
                RecurrenceEngine.nextAfterCompletion(t.rule, Dates.today(now))?.let { next ->
                    dao.insertOccurrences(listOf(TaskOccurrence(taskId = t.id, originalDate = next, dueDate = next, dueTime = t.timeOfDay)))
                    dao.updateTask(t.copy(generatedUntil = next))
                }
            }
        }
        onChanged()
    }

    suspend fun reopen(occurrenceId: Long) {
        db.withTransaction {
            val ot = dao.occurrenceWithTask(occurrenceId) ?: return@withTransaction
            dao.updateOccurrence(ot.occurrence.copy(status = OccurrenceStatus.PENDING, completedAt = null))
            if (ot.task.rule.type == RepeatType.AFTER_COMPLETION) {
                dao.occurrencesFor(ot.task.id)
                    .filter { it.id != occurrenceId && it.status == OccurrenceStatus.PENDING && it.originalDate > ot.occurrence.originalDate }
                    .forEach { dao.updateOccurrence(it.copy(status = OccurrenceStatus.CANCELLED)) }
            }
        }
        onChanged()
    }

    suspend fun setStatus(occurrenceId: Long, status: OccurrenceStatus) {
        dao.occurrence(occurrenceId)?.let { dao.updateOccurrence(it.copy(status = status)) }
        onChanged()
    }

    suspend fun postpone(occurrenceId: Long, newDate: Long, newTime: Int? = null, keepTime: Boolean = true) {
        dao.occurrence(occurrenceId)?.let {
            dao.updateOccurrence(it.copy(dueDate = newDate, dueTime = if (keepTime) it.dueTime else newTime, status = if (it.status == OccurrenceStatus.IN_PROGRESS) it.status else OccurrenceStatus.PENDING))
        }
        onChanged()
    }

    companion object {
        const val HORIZON_DAYS = 30L
        const val BACKFILL_DAYS = 60L
    }
}
