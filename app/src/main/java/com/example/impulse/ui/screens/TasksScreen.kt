package com.example.impulse.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.impulse.data.OccurrenceStatus
import com.example.impulse.data.OccurrenceWithTask
import com.example.impulse.data.RepeatType
import com.example.impulse.data.Task
import com.example.impulse.domain.Dates
import com.example.impulse.domain.RecurrenceEngine
import com.example.impulse.ui.Routes
import com.example.impulse.ui.TasksViewModel
import com.example.impulse.ui.components.EmojiBadge
import com.example.impulse.ui.components.EmptyState
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.PillTabs
import com.example.impulse.ui.components.ScreenHeader
import com.example.impulse.ui.components.SectionHeader
import com.example.impulse.ui.components.StatusPill
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.theme.LoopTheme

@Composable
fun TasksScreen(nav: NavController) {
    val vm = loopViewModel { TasksViewModel(it) }
    val s by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(s.today) }
    var sheetFor by remember { mutableStateOf<OccurrenceWithTask?>(null) }
    val today = s.today
    val open = s.occurrences.filter { it.isOpen }
    val overdue = open.filter { it.task.hasDueDate && it.occurrence.dueDate < today }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Tasks", subtitle = "${open.size} open · ${overdue.size} overdue")
            PillTabs(listOf("Agenda", "Routines", "Done"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> Agenda(s.occurrences, today, selected, { selected = it }, vm, onOpen = { sheetFor = it }, onAdd = { nav.navigate(Routes.taskEdit()) })
                1 -> Routines(s.tasks, s.occurrences, today, nav, onArchive = vm::archiveTask)
                else -> DoneList(s.occurrences, today, vm) { sheetFor = it }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.navigate(Routes.taskEdit()) },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New task") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = RoundedCornerShape(20.dp),
        )
    }
    sheetFor?.let { ot -> TaskActionsSheet(ot, today, vm, nav) { sheetFor = null } }
}

@Composable
private fun Agenda(
    all: List<OccurrenceWithTask>, today: Long, selected: Long, onSelect: (Long) -> Unit,
    vm: TasksViewModel, onOpen: (OccurrenceWithTask) -> Unit, onAdd: () -> Unit,
) {
    val overdue = all.filter { it.isOpen && it.task.hasDueDate && it.occurrence.dueDate < today }
    val pending = all.filter { it.isOpen && !it.task.hasDueDate }
    val day = all.filter { it.task.hasDueDate && it.occurrence.dueDate == selected && it.occurrence.status != OccurrenceStatus.CANCELLED }
        .sortedWith(compareBy({ it.isDone }, { it.occurrence.dueTime ?: 9999 }))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 2)

    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((today - 3..today + 21).toList()) { d ->
                    val count = all.count { it.isOpen && it.task.hasDueDate && it.occurrence.dueDate == d }
                    DayChip(d, today, d == selected, count) { onSelect(d) }
                }
            }
        }
        if (overdue.isNotEmpty() && selected == today) {
            item { SectionHeader("Overdue", count = overdue.size) }
            items(overdue, key = { "o${it.occurrence.id}" }) { TaskRow(it, today, vm, Modifier.padding(horizontal = 16.dp).animateItem()) { onOpen(it) } }
        }
        item { SectionHeader(if (selected == today) "Today" else Dates.formatLong(selected), count = day.count { it.isOpen }) }
        if (day.isEmpty()) item {
            EmptyState("🌤️", "Nothing scheduled", if (selected == today) "A clear day. Add a chore or enjoy it." else "No tasks on this day.") {
                Button(onClick = onAdd) { Text("Add task") }
            }
        }
        items(day, key = { "d${it.occurrence.id}" }) { TaskRow(it, today, vm, Modifier.padding(horizontal = 16.dp).animateItem()) { onOpen(it) } }
        if (pending.isNotEmpty() && selected == today) {
            item { SectionHeader("Pending (no date)", count = pending.size) }
            items(pending, key = { "p${it.occurrence.id}" }) { TaskRow(it, today, vm, Modifier.padding(horizontal = 16.dp).animateItem()) { onOpen(it) } }
        }
        item {
            Text(
                "Tip: swipe right to complete, left to push to tomorrow.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DayChip(day: Long, today: Long, selected: Boolean, count: Int, onClick: () -> Unit) {
    val date = Dates.date(day)
    val bg by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary
            day == today -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        }, label = "day",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.width(52.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Dates.dayShort(date.dayOfWeek).uppercase(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, color = fg)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(6.dp).clip(CircleShape).background(
                when {
                    count == 0 -> Color.Transparent
                    selected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        )
    }
}

@Composable
private fun Routines(tasks: List<Task>, occ: List<OccurrenceWithTask>, today: Long, nav: NavController, onArchive: (Long) -> Unit) {
    val recurring = tasks.filter { it.isRecurring }
    val oneTime = tasks.filter { !it.isRecurring }
    val byTask = occ.groupBy { it.task.id }
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tasks.isEmpty()) item {
            EmptyState("🔁", "No routines yet", "Create weekly laundry, monthly bills or a filter change every 90 days — LifeLoop schedules every occurrence.") {
                Button(onClick = { nav.navigate(Routes.taskEdit()) }) { Text("Create a routine") }
            }
        }
        if (recurring.isNotEmpty()) item { SectionHeader("Recurring", count = recurring.size) }
        items(recurring, key = { "r${it.id}" }) { t ->
            val list = byTask[t.id].orEmpty()
            val next = list.filter { it.isOpen && it.occurrence.dueDate >= today }.minOfOrNull { it.occurrence.dueDate }
                ?: if (t.rule.type != RepeatType.AFTER_COMPLETION) RecurrenceEngine.nextDate(t.rule, today - 1) else null
            val past = list.filter { it.occurrence.dueDate < today || it.isDone }
            val done = past.count { it.isDone }
            RoutineCard(t, next, today, if (past.isEmpty()) null else done to past.size, Modifier.padding(horizontal = 16.dp),
                onClick = { nav.navigate(Routes.taskEdit(t.id)) }, onArchive = { onArchive(t.id) })
        }
        if (oneTime.isNotEmpty()) item { SectionHeader("One-time", count = oneTime.size) }
        items(oneTime, key = { "t${it.id}" }) { t ->
            val o = byTask[t.id].orEmpty().firstOrNull()
            RoutineCard(t, o?.occurrence?.dueDate?.takeIf { t.hasDueDate }, today, null, Modifier.padding(horizontal = 16.dp),
                onClick = { nav.navigate(Routes.taskEdit(t.id)) }, onArchive = { onArchive(t.id) }, doneLabel = if (o?.isDone == true) "Done" else null)
        }
    }
}

@Composable
private fun RoutineCard(t: Task, next: Long?, today: Long, stats: Pair<Int, Int>?, modifier: Modifier, onClick: () -> Unit, onArchive: () -> Unit, doneLabel: String? = null) {
    LoopCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(t.emoji.ifBlank { t.category.emoji }, priorityColor(t.priority), size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(RecurrenceEngine.describe(t.rule))
                        t.timeOfDay?.let { append(" · ").append(Dates.formatTime(it)) }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (doneLabel != null) StatusPill(doneLabel, LoopTheme.colors.success)
                    else if (next != null) StatusPill("Next: ${Dates.relative(next, today)}", MaterialTheme.colorScheme.primary, icon = Icons.Rounded.EventRepeat)
                    else if (!t.hasDueDate) StatusPill("No due date", LoopTheme.colors.warning)
                    stats?.let { (d, n) -> StatusPill("$d/$n done", if (d == n) LoopTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, "Archive", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DoneList(all: List<OccurrenceWithTask>, today: Long, vm: TasksViewModel, onOpen: (OccurrenceWithTask) -> Unit) {
    val done = all.filter { it.isDone }.sortedByDescending { it.occurrence.completedAt ?: 0 }
    val grouped = done.groupBy { it.occurrence.completedAt?.let { ms -> Dates.epochDayOf(ms) } ?: it.occurrence.dueDate }
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (done.isEmpty()) item { EmptyState("✅", "Nothing completed yet", "Completed tasks from the last week show up here.") }
        grouped.forEach { (day, list) ->
            item(key = "h$day") { SectionHeader(Dates.relative(day, today), count = list.size) }
            items(list, key = { "c${it.occurrence.id}" }) { TaskRow(it, today, vm, Modifier.padding(horizontal = 16.dp)) { onOpen(it) } }
        }
    }
}
