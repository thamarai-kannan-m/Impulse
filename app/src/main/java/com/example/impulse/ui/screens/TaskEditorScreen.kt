@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.impulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.impulse.data.MissedPolicy
import com.example.impulse.data.Priority
import com.example.impulse.data.RecurrenceRule
import com.example.impulse.data.RepeatType
import com.example.impulse.data.Task
import com.example.impulse.data.TaskCategory
import com.example.impulse.domain.Dates
import com.example.impulse.domain.RecurrenceEngine
import com.example.impulse.domain.TaskTemplate
import com.example.impulse.domain.Templates
import com.example.impulse.ui.TaskEditorViewModel
import com.example.impulse.ui.components.ChoiceChips
import com.example.impulse.ui.components.ConfirmDialog
import com.example.impulse.ui.components.DatePickerSheet
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.QuantityStepper
import com.example.impulse.ui.components.TimePickerSheet
import com.example.impulse.ui.loopViewModel
import java.time.DayOfWeek

private val reminderOptions = listOf<Int?>(null, 0, 15, 60, 180, 24 * 60)
private fun reminderLabel(m: Int?) = when (m) {
    null -> "No reminder"; 0 -> "At due time"; 15 -> "15 min before"; 60 -> "1 hour before"; 180 -> "3 hours before"; else -> "1 day before"
}

@Composable
fun TaskEditorScreen(nav: NavController, taskId: Long?, templateIndex: Int) {
    val vm = loopViewModel(key = "task-edit-${taskId ?: "new"}") { TaskEditorViewModel(it, taskId) }
    val settings by vm.settings.collectAsState()
    val isNew = taskId == null
    val today = Dates.today()

    var original by remember { mutableStateOf<Task?>(null) }
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var title by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(TaskCategory.OTHER) }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM) }
    var hasDue by rememberSaveable { mutableStateOf(true) }
    var start by rememberSaveable { mutableStateOf(today) }
    var time by rememberSaveable { mutableStateOf<Int?>(null) }
    var repeat by rememberSaveable { mutableStateOf(RepeatType.NONE) }
    var interval by rememberSaveable { mutableStateOf(1) }
    var weekdays by rememberSaveable { mutableStateOf(Dates.bit(Dates.date(today).dayOfWeek)) }
    var dayOfMonth by rememberSaveable { mutableStateOf(Dates.date(today).dayOfMonth) }
    var hasEnd by rememberSaveable { mutableStateOf(false) }
    var end by rememberSaveable { mutableStateOf(today + 90) }
    var missed by rememberSaveable { mutableStateOf(MissedPolicy.KEEP_OVERDUE) }
    var reminder by rememberSaveable { mutableStateOf<Int?>(0) }
    var assignee by rememberSaveable { mutableStateOf<String?>(null) }
    var templateUsed by rememberSaveable { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun applyTemplate(t: TaskTemplate) {
        title = t.title; emoji = t.emoji; category = t.category; repeat = t.repeat; interval = t.interval
        time = t.time; priority = t.priority; hasDue = t.hasDueDate
        if (t.weekdays != 0) {
            weekdays = t.weekdays
            // start on the next matching weekday so the first occurrence is sensible
            start = (today..today + 6).first { Dates.hasDay(t.weekdays, Dates.date(it).dayOfWeek) }
        }
        t.dayOfMonth?.let { dayOfMonth = it }
        reminder = if (t.hasDueDate) 0 else null
        missed = if (t.repeat == RepeatType.DAILY) MissedPolicy.AUTO_SKIP else MissedPolicy.KEEP_OVERDUE
        templateUsed = true
    }

    LaunchedEffect(taskId) {
        if (isNew) {
            if (templateIndex in Templates.tasks.indices && !templateUsed) applyTemplate(Templates.tasks[templateIndex])
            return@LaunchedEffect
        }
        val t = vm.load() ?: return@LaunchedEffect
        original = t
        if (!loaded) {
            title = t.title; emoji = t.emoji; description = t.description; category = t.category; priority = t.priority
            hasDue = t.hasDueDate; start = t.rule.startDate; time = t.timeOfDay; repeat = t.rule.type; interval = t.rule.interval
            if (t.rule.weekdays != 0) weekdays = t.rule.weekdays
            t.rule.dayOfMonth?.let { dayOfMonth = it }
            hasEnd = t.rule.endDate != null; t.rule.endDate?.let { end = it }
            missed = t.missedPolicy; reminder = t.reminderOffsetMinutes; assignee = t.assignee
            loaded = true
        }
    }

    val rule = RecurrenceRule(
        type = repeat, interval = interval.coerceAtLeast(1),
        weekdays = if (repeat == RepeatType.WEEKLY) weekdays else 0,
        dayOfMonth = if (repeat == RepeatType.MONTHLY) dayOfMonth else null,
        startDate = start, endDate = if (hasEnd && repeat != RepeatType.NONE) end else null,
        zoneId = original?.rule?.zoneId ?: java.time.ZoneId.systemDefault().id,
    )
    val canSave = title.isNotBlank() && (repeat != RepeatType.WEEKLY || weekdays != 0) && (isNew || original != null)

    fun save() {
        val base = original ?: Task(title = title, rule = rule)
        vm.save(
            base.copy(
                title = title.trim(), emoji = emoji, description = description.trim(), category = category, priority = priority,
                hasDueDate = hasDue || repeat != RepeatType.NONE, timeOfDay = if (hasDue || repeat != RepeatType.NONE) time else null,
                reminderOffsetMinutes = reminder, rule = rule, missedPolicy = missed, assignee = assignee,
            )
        ) { nav.popBackStack() }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (isNew) "New task" else "Edit task") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Close") } },
            actions = {
                if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                TextButton(onClick = ::save, enabled = canSave) { Text("Save") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("What needs doing?") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                leadingIcon = if (emoji.isNotBlank()) ({ Text(emoji, fontSize = 22.sp) }) else null,
            )
            if (isNew && !templateUsed) {
                Text("Popular", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Templates.tasks) { t ->
                        Row(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { applyTemplate(t) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text(t.emoji); Spacer(Modifier.width(6.dp)); Text(t.title, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Templates.taskEmoji) { e ->
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .then(if (e == emoji) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e.ifBlank { "∅" }, fontSize = 18.sp) }
                }
            }
            EditorSection("Category") { ChoiceChips(TaskCategory.entries, category, { "${it.emoji} ${it.label}" }, { category = it }) }
            EditorSection("Priority") { ChoiceChips(Priority.entries, priority, { it.label }, { priority = it }) }

            LoopCard {
                Text("Schedule", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(RepeatType.entries, repeat, { it.label }, { repeat = it; if (it == RepeatType.AFTER_COMPLETION && interval < 2) interval = 30 })
                if (repeat == RepeatType.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Has a due date", style = MaterialTheme.typography.titleSmall)
                            Text("Off = stays pending until done (never overdue)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(hasDue, { hasDue = it })
                    }
                }
                AnimatedVisibility(repeat == RepeatType.WEEKLY) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        DayOfWeek.entries.forEach { d ->
                            val on = Dates.hasDay(weekdays, d)
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { weekdays = weekdays xor Dates.bit(d) },
                                contentAlignment = Alignment.Center,
                            ) { Text(Dates.dayNarrow(d), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
                if (repeat in setOf(RepeatType.DAILY, RepeatType.WEEKLY, RepeatType.MONTHLY, RepeatType.YEARLY, RepeatType.EVERY_N_DAYS, RepeatType.AFTER_COMPLETION)) {
                    val unit = when (repeat) {
                        RepeatType.WEEKLY -> "week(s)"; RepeatType.MONTHLY -> "month(s)"; RepeatType.YEARLY -> "year(s)"
                        RepeatType.AFTER_COMPLETION -> "days after done"; else -> "day(s)"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Text("Every", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        QuantityStepper(interval.toDouble(), if (repeat == RepeatType.AFTER_COMPLETION) 5.0 else 1.0, unit, { interval = it.toInt().coerceAtLeast(1) }, min = 1.0)
                    }
                }
                if (repeat == RepeatType.MONTHLY) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("On day", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        QuantityStepper(dayOfMonth.toDouble(), 1.0, "", { dayOfMonth = it.toInt().coerceIn(1, 31) }, min = 1.0)
                    }
                }
                if (hasDue || repeat != RepeatType.NONE) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickStart = true }) {
                            Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text((if (repeat == RepeatType.NONE) "Due " else "Starts ") + Dates.relative(start, today).lowercase())
                        }
                        TextButton(onClick = { pickTime = true }) {
                            Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(time?.let { Dates.formatTime(it) } ?: "Any time")
                        }
                        if (time != null) TextButton(onClick = { time = null }) { Text("Clear") }
                    }
                }
                if (repeat != RepeatType.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ends", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        if (hasEnd) TextButton(onClick = { pickEnd = true }) { Text(Dates.formatFull(end)) }
                        Switch(hasEnd, { hasEnd = it })
                    }
                    val preview = when (repeat) {
                        RepeatType.AFTER_COMPLETION -> listOf(start)
                        else -> RecurrenceEngine.datesBetween(rule, today, today + 400).take(4)
                    }
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(12.dp)) {
                        Column {
                            Text(RecurrenceEngine.describe(rule), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (preview.isEmpty()) "No upcoming dates" else "Next: " + preview.joinToString(" · ") { Dates.formatShort(it) } +
                                    if (repeat == RepeatType.AFTER_COMPLETION) ", then every $interval days after you complete it" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (repeat != RepeatType.NONE && repeat != RepeatType.AFTER_COMPLETION) LoopCard {
                Text("If I miss one", style = MaterialTheme.typography.titleMedium)
                MissedPolicy.entries.forEach { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { missed = p }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(missed == p, { missed = p })
                        Column {
                            Text(p.label, style = MaterialTheme.typography.titleSmall)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (hasDue || repeat != RepeatType.NONE) EditorSection("Reminder") {
                ChoiceChips(reminderOptions, reminder, ::reminderLabel, { reminder = it })
            }
            val members = settings?.members.orEmpty()
            if (members.isNotEmpty()) EditorSection("Assigned to") {
                ChoiceChips(listOf<String?>(null) + members, assignee, { it ?: "Me" }, { assignee = it })
            }
            OutlinedTextField(
                value = description, onValueChange = { description = it }, label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 2,
            )
        }
        Button(
            onClick = ::save, enabled = canSave,
            modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().height(54.dp), shape = CircleShape,
        ) { Text(if (isNew) "Create task" else "Save changes", style = MaterialTheme.typography.titleSmall) }
    }

    if (pickStart) DatePickerSheet(start, { pickStart = false }, { start = it; dayOfMonth = Dates.date(it).dayOfMonth }, if (repeat == RepeatType.NONE) "Due date" else "Start date")
    if (pickEnd) DatePickerSheet(end, { pickEnd = false }, { end = it }, "End date")
    if (pickTime) TimePickerSheet(time ?: (settings?.reminders?.morningMinutes ?: 540), { pickTime = false }, { time = it })
    if (confirmDelete) ConfirmDialog(
        "Delete “$title”?", "The task and its whole history will be removed.", "Delete",
        { confirmDelete = false }, { original?.let { vm.deleteSeries(it.id) }; nav.popBackStack() }, destructive = true,
    )
}

