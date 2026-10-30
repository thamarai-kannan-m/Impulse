@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.impulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.impulse.data.Habit
import com.example.impulse.data.HabitLog
import com.example.impulse.data.HabitRepository
import com.example.impulse.data.HabitType
import com.example.impulse.domain.Dates
import com.example.impulse.domain.HabitEngine
import com.example.impulse.domain.HabitSummary
import com.example.impulse.domain.HabitTemplate
import com.example.impulse.domain.Templates
import com.example.impulse.domain.fmtQty
import com.example.impulse.ui.HabitEditorViewModel
import com.example.impulse.ui.HabitsViewModel
import com.example.impulse.ui.Routes
import com.example.impulse.ui.components.ChoiceChips
import com.example.impulse.ui.components.ConfirmDialog
import com.example.impulse.ui.components.EmptyState
import com.example.impulse.ui.components.HeroCard
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.LoopCheck
import com.example.impulse.ui.components.ProgressRing
import com.example.impulse.ui.components.ScreenHeader
import com.example.impulse.ui.components.TimePickerSheet
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.theme.LoopTheme
import com.example.impulse.ui.theme.Palette
import com.example.impulse.ui.theme.paletteColor
import java.time.DayOfWeek

// ============================================================================ list

@Composable
fun HabitsScreen(nav: NavController) {
    val vm = loopViewModel { HabitsViewModel(it) }
    val list by vm.summaries.collectAsState()
    val today = Dates.today()

    Box(Modifier.fillMaxSize()) {
        val habits = list.orEmpty()
        val scheduled = habits.filter { it.scheduledToday }
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ScreenHeader("Habits", subtitle = "${scheduled.count { it.doneToday }} of ${scheduled.size} done today") }
            if (habits.isNotEmpty()) item { WeekHero(habits, today, Modifier.padding(horizontal = 16.dp)) }
            if (list != null && habits.isEmpty()) item {
                EmptyState("🌱", "Build your first habit", "Start tiny: drink water, read 20 minutes, or a morning walk. Streaks build themselves.") {
                    Button(onClick = { nav.navigate(Routes.habitEdit()) }) { Text("Create a habit") }
                }
            }
            items(habits, key = { it.habit.id }) { h ->
                HabitCard(h, vm, Modifier.padding(horizontal = 16.dp).animateItem()) { nav.navigate(Routes.habit(h.habit.id)) }
            }
            if (habits.isNotEmpty()) item {
                Text(
                    "Missed days lower a streak but never pile up as overdue work.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.navigate(Routes.habitEdit()) },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New habit") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun WeekHero(habits: List<HabitSummary>, today: Long, modifier: Modifier) {
    HeroCard(modifier.fillMaxWidth()) {
        Column {
            Text("THIS WEEK", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            val best = habits.maxOfOrNull { it.currentStreak } ?: 0
            Text(if (best > 0) "🔥 Best active streak: $best" else "Fresh week, fresh loop ✨", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0 until 7).forEach { i ->
                    val day = today - 6 + i
                    val sched = habits.filter { h -> h.last7[i].scheduled }
                    val frac = if (sched.isEmpty()) 0f else sched.count { it.last7[i].done }.toFloat() / sched.size
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(frac, size = 36.dp, stroke = 5.dp, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f)) {
                            if (frac >= 1f) Text("✓", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(Dates.dayNarrow(Dates.date(day).dayOfWeek), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = if (day == today) 1f else 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitCard(h: HabitSummary, vm: HabitsViewModel, modifier: Modifier, onOpen: () -> Unit) {
    val habit = h.habit
    val color = paletteColor(habit.colorIndex)
    val today = Dates.today()
    LoopCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).clickable { vm.quickLogHabit(habit.id) }) {
                ProgressRing(h.todayFraction, size = 58.dp, stroke = 6.dp, color = color) { Text(habit.emoji, fontSize = 24.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(habitProgressText(h), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (h.currentStreak > 0) Text("🔥 ${h.currentStreak}-${h.streakUnit} streak", style = MaterialTheme.typography.labelMedium, color = LoopTheme.colors.warning)
            }
            when (habit.type) {
                HabitType.QUANTITY, HabitType.DURATION -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val step = HabitRepository.stepFor(habit)
                    FilledTonalIconButton(onClick = { vm.setHabitValue(habit.id, today, (h.todayValue - step).coerceAtLeast(0.0)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Remove, "Less") }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(onClick = { vm.setHabitValue(habit.id, today, h.todayValue + step) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Add, "More") }
                }
                HabitType.AVOIDANCE -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val v = h.last7.last().value
                    SmallToggle("Clean", v != null && v >= 1, LoopTheme.colors.success) { vm.setHabitValue(habit.id, today, if (v != null && v >= 1) null else 1.0) }
                    SmallToggle("Slipped", v != null && v < 0, LoopTheme.colors.danger) { vm.setHabitValue(habit.id, today, if (v != null && v < 0) null else -1.0) }
                }
                else -> LoopCheck(h.doneToday, color, { vm.quickLogHabit(habit.id) }, label = habit.name)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            h.last7.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(
                            when {
                                d.done -> color
                                d.slipped -> LoopTheme.colors.danger.copy(alpha = 0.7f)
                                !d.scheduled -> Color.Transparent
                                else -> color.copy(alpha = 0.12f)
                            }
                        ).then(if (d.date == today) Modifier.border(2.dp, color, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d.done) Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        else if (!d.scheduled) Text("·", color = MaterialTheme.colorScheme.outline)
                    }
                    Text(Dates.dayNarrow(Dates.date(d.date).dayOfWeek), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SmallToggle(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (on) color else color.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else color) }
}

fun habitProgressText(h: HabitSummary): String {
    val habit = h.habit
    return when (habit.type) {
        HabitType.QUANTITY, HabitType.DURATION -> "${h.todayValue.fmtQty()} / ${habit.target.fmtQty()} ${habit.unit} today"
        HabitType.FREQUENCY -> "${h.weekCount} of ${habit.target.toInt()} this week"
        HabitType.AVOIDANCE -> if (h.doneToday) "Stayed clean today" else "Avoid today"
        HabitType.BINARY -> if (!h.scheduledToday) "Rest day · ${Dates.weekdaysLabel(habit.weekdays)}" else if (h.doneToday) "Done today" else "Not yet today"
    }
}

// ============================================================================ detail

@Composable
fun HabitDetailScreen(nav: NavController, habitId: Long) {
    val vm = loopViewModel { HabitsViewModel(it) }
    val list by vm.summaries.collectAsState()
    val logs by remember(habitId) { vm.logsFor(habitId) }.collectAsState(initial = emptyList())
    val h = list?.firstOrNull { it.habit.id == habitId }
    var confirmDelete by remember { mutableStateOf(false) }
    var editDay by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(h?.habit?.name ?: "") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { nav.navigate(Routes.habitEdit(habitId)) }) { Icon(Icons.Rounded.Edit, "Edit") }
                IconButton(onClick = { vm.archive(habitId); nav.popBackStack() }) { Icon(Icons.Rounded.Archive, "Archive") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (h != null) {
            val color = paletteColor(h.habit.colorIndex)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(h.todayFraction, size = 96.dp, stroke = 10.dp, color = color) { Text(h.habit.emoji, fontSize = 40.sp) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(h.habit.type.label, style = MaterialTheme.typography.labelLarge, color = color)
                        Text(habitProgressText(h), style = MaterialTheme.typography.titleMedium)
                        Text(if (h.habit.type == HabitType.FREQUENCY) "Any days" else Dates.weekdaysLabel(h.habit.weekdays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        h.habit.reminderTime?.let { Text("Reminder at ${Dates.formatTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("🔥", "${h.currentStreak}", "Current ${h.streakUnit}s", Modifier.weight(1f))
                    StatBox("🏆", "${h.bestStreak}", "Best streak", Modifier.weight(1f))
                    StatBox("📈", "${(h.rate30 * 100).toInt()}%", "30-day rate", Modifier.weight(1f))
                }
                LoopCard {
                    Text("Last 15 weeks", style = MaterialTheme.typography.titleMedium)
                    Text("Tap a day to fix a missed log", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Heatmap(h.habit, logs, color) { day ->
                        if (h.habit.type == HabitType.QUANTITY || h.habit.type == HabitType.DURATION) editDay = day
                        else {
                            val cur = logs.firstOrNull { it.date == day }?.value
                            vm.setHabitValue(habitId, day, if (cur != null && cur != 0.0) null else 1.0)
                        }
                    }
                }
            }
        }
    }
    val editHabit = h?.habit
    val day = editDay
    if (day != null && editHabit != null) {
        val current = logs.firstOrNull { it.date == day }?.value
        AmountDialog(
            "${editHabit.name} · ${Dates.formatShort(day)}", HabitRepository.stepFor(editHabit), editHabit.unit,
            initial = current ?: editHabit.target, onDismiss = { editDay = null },
        ) { vm.setHabitValue(habitId, day, it) }
    }
    if (confirmDelete) ConfirmDialog("Delete habit?", "Its whole history will be removed. Archive to keep it.", "Delete", { confirmDelete = false }, { vm.delete(habitId); nav.popBackStack() }, destructive = true)
}

@Composable
private fun StatBox(emoji: String, value: String, label: String, modifier: Modifier) {
    LoopCard(modifier, contentPadding = PaddingValues(12.dp)) {
        Text(emoji, fontSize = 20.sp)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Heatmap(habit: Habit, logs: List<HabitLog>, color: Color, onDay: (Long) -> Unit) {
    val today = Dates.today()
    val byDate = logs.associateBy({ it.date }, { it.value })
    val weeks = 15
    val thisMonday = today - (Dates.date(today).dayOfWeek.value - 1)
    val firstMonday = thisMonday - (weeks - 1) * 7
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DayOfWeek.entries.forEach { d ->
                Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                    if (d.value % 2 == 1) Text(Dates.dayNarrow(d), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        (0 until weeks).forEach { w ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 7).forEach { i ->
                    val day = firstMonday + w * 7 + i
                    val v = byDate[day]
                    val intensity = when {
                        day > today -> -1f
                        habit.type == HabitType.QUANTITY || habit.type == HabitType.DURATION -> HabitEngine.fraction(habit, v)
                        HabitEngine.isDone(habit, v) -> 1f
                        else -> 0f
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    intensity < 0 -> Color.Transparent
                                    v != null && v < 0 -> LoopTheme.colors.danger.copy(alpha = 0.6f)
                                    intensity == 0f -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    else -> color.copy(alpha = 0.25f + 0.75f * intensity)
                                }
                            )
                            .then(if (day == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp)) else Modifier)
                            .then(if (day <= today) Modifier.clickable { onDay(day) } else Modifier),
                    )
                }
            }
        }
    }
}

// ============================================================================ editor

@Composable
fun HabitEditorScreen(nav: NavController, habitId: Long?) {
    val vm = loopViewModel(key = "habit-edit-${habitId ?: "new"}") { HabitEditorViewModel(it, habitId) }
    val isNew = habitId == null
    var original by remember { mutableStateOf<Habit?>(null) }
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("✅") }
    var type by rememberSaveable { mutableStateOf(HabitType.BINARY) }
    var target by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf("") }
    var weekdays by rememberSaveable { mutableStateOf(Dates.EVERY_DAY) }
    var hasReminder by rememberSaveable { mutableStateOf(false) }
    var reminder by rememberSaveable { mutableStateOf(8 * 60) }
    var colorIndex by rememberSaveable { mutableStateOf(0) }
    var pickTime by remember { mutableStateOf(false) }
    var templateUsed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(habitId) {
        if (isNew) return@LaunchedEffect
        val h = vm.load() ?: return@LaunchedEffect
        original = h
        if (!loaded) {
            name = h.name; emoji = h.emoji; type = h.type; target = h.target.fmtQty(); unit = h.unit; weekdays = h.weekdays
            hasReminder = h.reminderTime != null; h.reminderTime?.let { reminder = it }; colorIndex = h.colorIndex
            loaded = true
        }
    }

    fun applyTemplate(t: HabitTemplate) {
        name = t.name; emoji = t.emoji; type = t.type; target = t.target.fmtQty(); unit = t.unit; colorIndex = t.color
        hasReminder = t.reminder != null; t.reminder?.let { reminder = it }; templateUsed = true
    }

    val measurable = type == HabitType.QUANTITY || type == HabitType.DURATION || type == HabitType.FREQUENCY
    val canSave = name.isNotBlank() && (!measurable || (target.toDoubleOrNull() ?: 0.0) > 0) && (type == HabitType.FREQUENCY || weekdays != 0) && (isNew || original != null)

    fun save() {
        val base = original ?: Habit(name = name)
        vm.save(
            base.copy(
                name = name.trim(), emoji = emoji, type = type,
                target = if (measurable) target.toDoubleOrNull() ?: 1.0 else 1.0,
                unit = when (type) { HabitType.DURATION -> unit.ifBlank { "min" }; HabitType.FREQUENCY -> "times"; HabitType.QUANTITY -> unit; else -> "" },
                weekdays = weekdays, reminderTime = if (hasReminder) reminder else null, colorIndex = colorIndex,
            )
        ) { nav.popBackStack() }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (isNew) "New habit" else "Edit habit") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Close") } },
            actions = { TextButton(onClick = ::save, enabled = canSave) { Text("Save") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(paletteColor(colorIndex).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 30.sp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Habit name") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            if (isNew && !templateUsed) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Templates.habits) { t ->
                    Row(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { applyTemplate(t) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(t.emoji); Spacer(Modifier.width(6.dp)); Text(t.name, style = MaterialTheme.typography.labelLarge) }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Templates.habitEmoji) { e ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .then(if (e == emoji) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 18.sp) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c).clickable { colorIndex = i }
                            .then(if (i == colorIndex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                    )
                }
            }
            LoopCard {
                Text("Type", style = MaterialTheme.typography.titleMedium)
                HabitType.entries.forEach { t ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { type = t }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(type == t, { type = t })
                        Column {
                            Text(t.label, style = MaterialTheme.typography.titleSmall)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                AnimatedVisibility(measurable) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(if (type == HabitType.FREQUENCY) "Times per week" else "Daily target") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        if (type == HabitType.QUANTITY || type == HabitType.DURATION) OutlinedTextField(
                            value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, placeholder = { Text(if (type == HabitType.DURATION) "min" else "mL, steps…") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
            }
            if (type != HabitType.FREQUENCY) LoopCard {
                Text("Which days?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf(Dates.EVERY_DAY, Dates.WEEKDAYS, Dates.WEEKENDS), weekdays.takeIf { it in setOf(Dates.EVERY_DAY, Dates.WEEKDAYS, Dates.WEEKENDS) }, { Dates.weekdaysLabel(it) }, { weekdays = it })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DayOfWeek.entries.forEach { d ->
                        val on = Dates.hasDay(weekdays, d)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (on) paletteColor(colorIndex) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { weekdays = weekdays xor Dates.bit(d) },
                            contentAlignment = Alignment.Center,
                        ) { Text(Dates.dayNarrow(d), color = if (on) Color.White else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
            LoopCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.titleMedium)
                        Text(if (hasReminder) Dates.formatTime(reminder) else "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (hasReminder) OutlinedButton(onClick = { pickTime = true }) { Text("Change") }
                    Spacer(Modifier.width(8.dp))
                    Switch(hasReminder, { hasReminder = it })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = ::save, enabled = canSave, modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().height(54.dp), shape = CircleShape) {
            Text(if (isNew) "Create habit" else "Save changes", style = MaterialTheme.typography.titleSmall)
        }
    }
    if (pickTime) TimePickerSheet(reminder, { pickTime = false }, { reminder = it })
}
