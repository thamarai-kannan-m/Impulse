@file:OptIn(ExperimentalFoundationApi::class)

package com.example.impulse.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.example.impulse.data.HabitType
import com.example.impulse.data.OccurrenceWithTask
import com.example.impulse.domain.Dates
import com.example.impulse.domain.HabitSummary
import com.example.impulse.domain.fmtQty
import com.example.impulse.ui.AlertKind
import com.example.impulse.ui.DashboardAlert
import com.example.impulse.ui.Routes
import com.example.impulse.ui.TodayViewModel
import com.example.impulse.ui.components.EmojiBadge
import com.example.impulse.ui.components.EmptyState
import com.example.impulse.ui.components.HeroCard
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.ProgressRing
import com.example.impulse.ui.components.SectionHeader
import com.example.impulse.ui.components.StatTile
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.navigateTop
import com.example.impulse.ui.theme.LoopTheme
import com.example.impulse.ui.theme.paletteColor

@Composable
fun TodayScreen(nav: NavController) {
    val vm = loopViewModel { TodayViewModel(it) }
    val s by vm.state.collectAsState()
    var sheetFor by remember { mutableStateOf<OccurrenceWithTask?>(null) }
    var fabOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOn = it }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Dates.formatLong(s.today).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        val greet = Dates.greeting(Dates.minutesOfDay(System.currentTimeMillis()))
                        Text(
                            if (s.userName.isBlank()) "$greet 👋" else "$greet,\n${s.userName} 👋",
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                    IconButton(onClick = { navigateTop(nav, Routes.inventory(1)) }) {
                        Box {
                            Icon(Icons.Rounded.ShoppingCart, "Shopping list")
                            if (s.shoppingCount > 0) Box(
                                Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center,
                            ) { Text("${s.shoppingCount}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Settings") }
                }
            }

            item(key = "hero") { DayHero(s.doneToday, s.totalToday, s.progress, s.overdue.size, Modifier.padding(horizontal = 16.dp)) }

            item(key = "stats") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(s.pendingCount, "Pending tasks", Icons.Rounded.PendingActions, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { navigateTop(nav, "tasks") }
                        StatTile(s.routinesDue, "Due routines", Icons.Rounded.EventRepeat, MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) { navigateTop(nav, "tasks") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(s.lowStockCount, "Low-stock items", Icons.Rounded.Inventory2, LoopTheme.colors.warning, Modifier.weight(1f)) { navigateTop(nav, Routes.inventory(0)) }
                        StatTile(s.expiryCount, "Expiry alerts", Icons.Rounded.WarningAmber, LoopTheme.colors.danger, Modifier.weight(1f)) { navigateTop(nav, Routes.inventory(0)) }
                    }
                }
            }

            if (!notificationsOn) item(key = "notif") {
                LoopCard(Modifier.padding(horizontal = 16.dp), color = LoopTheme.colors.warningContainer, border = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsOff, null, tint = LoopTheme.colors.warning)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Reminders are off", style = MaterialTheme.typography.titleSmall)
                            Text("Everything still shows here, but we can't nudge you.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else nav.navigate(Routes.SETTINGS)
                        }) { Text("Turn on") }
                    }
                }
            }

            if (!s.loading && s.isEmpty) item(key = "empty") {
                EmptyState("🌱", "A fresh start", "Add your first grocery item, chore or habit — or explore with an example household.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { vm.loadSample() }) { Text("Load example") }
                        Button(onClick = { fabOpen = true }) { Text("Add something") }
                    }
                }
            }

            // 1 — overdue & time-sensitive
            val attention = s.overdue.size + s.alerts.size
            if (attention > 0) item(key = "att-h") { SectionHeader("Needs attention", count = attention) }
            items(s.overdue, key = { "ov-${it.occurrence.id}" }) { ot ->
                TaskRow(ot, s.today, vm, Modifier.padding(horizontal = 16.dp).animateItem()) { sheetFor = ot }
            }
            // 2 & 3 — expiry and low stock
            items(s.alerts, key = { it.id }) { a ->
                AlertCard(a, vm, nav, Modifier.padding(horizontal = 16.dp).animateItem())
            }

            // 4 — today's tasks
            if (s.todayTasks.isNotEmpty()) {
                item(key = "today-h") { SectionHeader("Today", count = s.todayTasks.count { it.isOpen }) }
                items(s.todayTasks, key = { "td-${it.occurrence.id}" }) { ot ->
                    TaskRow(ot, s.today, vm, Modifier.padding(horizontal = 16.dp).animateItem()) { sheetFor = ot }
                }
            }
            if (s.pending.isNotEmpty()) {
                item(key = "pend-h") { SectionHeader("Pending", count = s.pending.size) }
                items(s.pending, key = { "pd-${it.occurrence.id}" }) { ot ->
                    TaskRow(ot, s.today, vm, Modifier.padding(horizontal = 16.dp).animateItem()) { sheetFor = ot }
                }
            }

            // 5 — habits
            if (s.habits.isNotEmpty()) {
                item(key = "hab-h") { SectionHeader("Habits", action = "See all", onAction = { navigateTop(nav, "habits") }) }
                item(key = "habits") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.habits, key = { it.habit.id }) { h ->
                            HabitBubble(h, onTap = { vm.quickLogHabit(h.habit.id) }, onLong = { nav.navigate(Routes.habit(h.habit.id)) })
                        }
                    }
                }
            }

            if (s.upcoming.isNotEmpty()) {
                item(key = "up-h") { SectionHeader("Coming up", action = "All tasks", onAction = { navigateTop(nav, "tasks") }) }
                item(key = "upcoming") {
                    LoopCard(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        s.upcoming.take(8).forEach { ot -> UpcomingRow(ot, s.today) { sheetFor = ot } }
                    }
                }
            }
        }

        QuickAddFab(
            open = fabOpen, onToggle = { fabOpen = !fabOpen },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            onItem = { fabOpen = false; nav.navigate(Routes.itemEdit()) },
            onTask = { fabOpen = false; nav.navigate(Routes.taskEdit()) },
            onHabit = { fabOpen = false; nav.navigate(Routes.habitEdit()) },
        )
    }
    sheetFor?.let { ot -> TaskActionsSheet(ot, s.today, vm, nav) { sheetFor = null } }
}

@Composable
private fun DayHero(done: Int, total: Int, progress: Float, overdue: Int, modifier: Modifier) {
    HeroCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TODAY'S LOOP", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$done", style = MaterialTheme.typography.displayLarge, color = Color.White)
                    Text(" / $total", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    when {
                        total == 0 -> "Nothing scheduled — enjoy the calm ☕"
                        progress >= 1f -> "Loop closed! Everything done 🎉"
                        progress >= 0.5f -> "More than halfway there. Keep going!"
                        overdue > 0 -> "$overdue overdue — knock one out first."
                        else -> "Small steps. One thing at a time."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f),
                )
            }
            ProgressRing(progress, size = 96.dp, stroke = 11.dp, color = Color.White, trackColor = Color.White.copy(alpha = 0.22f)) {
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun AlertCard(a: DashboardAlert, vm: TodayViewModel, nav: NavController, modifier: Modifier) {
    val (color, label) = when (a.kind) {
        AlertKind.EXPIRED -> LoopTheme.colors.danger to "EXPIRED"
        AlertKind.EXPIRES_TODAY -> LoopTheme.colors.danger to "TODAY"
        AlertKind.EXPIRING -> LoopTheme.colors.warning to "USE SOON"
        AlertKind.OUT_OF_STOCK -> LoopTheme.colors.danger to "OUT"
        AlertKind.LOW_STOCK -> LoopTheme.colors.warning to "LOW"
    }
    LoopCard(modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.item(a.item.id)) }, contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 8.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(a.emoji, color, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(a.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.acknowledge(a) }) { Icon(Icons.Rounded.Close, "Dismiss alert", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (a.batch != null) {
                TextButton(onClick = { vm.closeBatch(a.batch.id, discarded = false) }) {
                    Icon(Icons.Rounded.Done, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Used it")
                }
                TextButton(onClick = { vm.closeBatch(a.batch.id, discarded = true) }) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Discarded")
                }
            } else {
                TextButton(onClick = { vm.addToShopping(a.item); vm.acknowledge(a) }) {
                    Icon(Icons.Rounded.AddShoppingCart, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add to list")
                }
                TextButton(onClick = { nav.navigate(Routes.item(a.item.id)) }) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Update stock")
                }
            }
        }
    }
}

@Composable
fun HabitBubble(h: HabitSummary, onTap: () -> Unit, onLong: () -> Unit) {
    val color = paletteColor(h.habit.colorIndex)
    LoopCard(
        Modifier.width(118.dp).clip(RoundedCornerShape(22.dp)).combinedClickable(onClick = onTap, onLongClick = onLong),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(h.todayFraction, size = 64.dp, stroke = 7.dp, color = color) {
                Text(h.habit.emoji, fontSize = 26.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(h.habit.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (h.habit.type) {
                    HabitType.QUANTITY, HabitType.DURATION -> "${h.todayValue.fmtQty()}/${h.habit.target.fmtQty()} ${h.habit.unit}"
                    HabitType.FREQUENCY -> "${h.weekCount}/${h.habit.target.toInt()} this week"
                    else -> if (h.doneToday) "Done ✓" else "Tap to log"
                },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            if (h.currentStreak > 0) Text("🔥 ${h.currentStreak}", style = MaterialTheme.typography.labelMedium, color = LoopTheme.colors.warning)
        }
    }
}

@Composable
private fun UpcomingRow(ot: OccurrenceWithTask, today: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Dates.dayShort(Dates.date(ot.occurrence.dueDate).dayOfWeek).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("${Dates.date(ot.occurrence.dueDate).dayOfMonth}", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(8.dp))
        Text(ot.task.emoji.ifBlank { ot.task.category.emoji }, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ot.task.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(Dates.relative(ot.occurrence.dueDate, today), ot.occurrence.dueTime?.let { Dates.formatTime(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(priorityColor(ot.task.priority)))
    }
}

@Composable
fun QuickAddFab(open: Boolean, onToggle: () -> Unit, modifier: Modifier, onItem: () -> Unit, onTask: () -> Unit, onHabit: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 45f else 0f, label = "fab")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(open, enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.8f), exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.8f)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniFab("Habit", Icons.Rounded.SelfImprovement, MaterialTheme.colorScheme.tertiaryContainer, onHabit)
                MiniFab("Task or chore", Icons.Rounded.TaskAlt, MaterialTheme.colorScheme.secondaryContainer, onTask)
                MiniFab("Grocery / item", Icons.Rounded.Inventory2, MaterialTheme.colorScheme.primaryContainer, onItem)
            }
        }
        FloatingActionButton(onClick = onToggle, shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.primary) {
            Icon(Icons.Rounded.Add, if (open) "Close" else "Quick add", Modifier.rotate(rotation).size(28.dp))
        }
    }
}

@Composable
private fun MiniFab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick, containerColor = color, shape = CircleShape,
        icon = { Icon(icon, null) }, text = { Text(label) },
    )
}

