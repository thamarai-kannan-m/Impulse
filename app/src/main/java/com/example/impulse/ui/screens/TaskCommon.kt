@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.impulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.impulse.data.OccurrenceStatus
import com.example.impulse.data.OccurrenceWithTask
import com.example.impulse.data.Priority
import com.example.impulse.domain.Dates
import com.example.impulse.domain.RecurrenceEngine
import com.example.impulse.ui.LoopViewModel
import com.example.impulse.ui.Routes
import com.example.impulse.ui.components.ConfirmDialog
import com.example.impulse.ui.components.DatePickerSheet
import com.example.impulse.ui.components.EmojiBadge
import com.example.impulse.ui.components.LocalSnackbar
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.LoopCheck
import com.example.impulse.ui.components.StatusPill
import com.example.impulse.ui.components.SwipeActionRow
import com.example.impulse.ui.theme.LoopTheme
import kotlinx.coroutines.launch

@Composable
fun priorityColor(p: Priority): Color = when (p) {
    Priority.LOW -> MaterialTheme.colorScheme.outline
    Priority.MEDIUM -> MaterialTheme.colorScheme.primary
    Priority.HIGH -> LoopTheme.colors.warning
    Priority.URGENT -> LoopTheme.colors.danger
}

val OccurrenceWithTask.isOpen get() = occurrence.status == OccurrenceStatus.PENDING || occurrence.status == OccurrenceStatus.IN_PROGRESS
val OccurrenceWithTask.isDone get() = occurrence.status == OccurrenceStatus.COMPLETED

/** A task occurrence row with swipe-right-to-complete and swipe-left-to-postpone. */
@Composable
fun TaskRow(
    ot: OccurrenceWithTask,
    today: Long,
    vm: LoopViewModel,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    onOpen: () -> Unit,
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val t = ot.task
    val o = ot.occurrence
    val done = ot.isDone
    val overdue = ot.isOpen && t.hasDueDate && o.dueDate < today
    val accent = priorityColor(t.priority)

    fun complete() {
        vm.complete(o.id)
        scope.launch {
            val r = snackbar.showSnackbar("“${t.title}” done 🎉", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.reopen(o.id)
        }
    }

    SwipeActionRow(
        modifier = modifier,
        rightIcon = Icons.Rounded.CheckCircle, rightColor = LoopTheme.colors.success,
        onSwipeRight = if (ot.isOpen) ({ complete() }) else null,
        leftIcon = Icons.Rounded.Snooze, leftColor = LoopTheme.colors.warning,
        onSwipeLeft = if (ot.isOpen && t.hasDueDate) ({
            val from = maxOf(o.dueDate, today)
            vm.postpone(o.id, from + 1)
            scope.launch {
                val r = snackbar.showSnackbar("Moved to ${Dates.relative(from + 1, today).lowercase()}", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.postpone(o.id, o.dueDate)
            }
        }) else null,
    ) {
        LoopCard(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(if (done) Color.Transparent else accent))
                Row(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp).alpha(if (done || o.status == OccurrenceStatus.SKIPPED) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(t.emoji.ifBlank { t.category.emoji }, accent, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            when {
                                overdue -> StatusPill("Overdue · ${Dates.relative(o.dueDate, today)}", LoopTheme.colors.danger)
                                !t.hasDueDate && ot.isOpen -> StatusPill("Pending", LoopTheme.colors.warning)
                                o.status == OccurrenceStatus.IN_PROGRESS -> StatusPill("In progress", LoopTheme.colors.info, icon = Icons.Rounded.PlayArrow)
                                o.status == OccurrenceStatus.SKIPPED -> StatusPill("Skipped", MaterialTheme.colorScheme.outline)
                            }
                            if (showDate && t.hasDueDate && !overdue) StatusPill(Dates.relative(o.dueDate, today), MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.Event)
                            o.dueTime?.let { StatusPill(Dates.formatTime(it), MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.Schedule) }
                            if (t.isRecurring) StatusPill(RecurrenceEngine.describe(t.rule), MaterialTheme.colorScheme.primary, icon = Icons.Rounded.EventRepeat)
                            t.assignee?.let { StatusPill(it, MaterialTheme.colorScheme.secondary, icon = Icons.Rounded.Person) }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (o.status != OccurrenceStatus.SKIPPED) LoopCheck(done, LoopTheme.colors.success, onToggle = {
                        if (done) vm.reopen(o.id) else complete()
                    }, label = t.title)
                }
            }
        }
    }
}

/** Bottom sheet with every action for a task occurrence. */
@Composable
fun TaskActionsSheet(ot: OccurrenceWithTask, today: Long, vm: LoopViewModel, nav: NavController, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickDate by remember { mutableStateOf(false) }
    var confirmSeries by remember { mutableStateOf(false) }
    val t = ot.task
    val o = ot.occurrence
    val from = maxOf(o.dueDate, today)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(t.emoji.ifBlank { t.category.emoji }, priorityColor(t.priority), size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        buildString {
                            append(if (t.hasDueDate) "Due ${Dates.relative(o.dueDate, today).lowercase()}" else "No due date")
                            o.dueTime?.let { append(" · ").append(Dates.formatTime(it)) }
                            if (t.isRecurring) append(" · ").append(RecurrenceEngine.describe(t.rule))
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (t.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(t.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
                val mod = Modifier.weight(1f)
                if (ot.isOpen) {
                    SheetAction(Icons.Rounded.CheckCircle, "Complete", LoopTheme.colors.success, mod) { vm.complete(o.id); onDismiss() }
                    if (o.status != OccurrenceStatus.IN_PROGRESS) SheetAction(Icons.Rounded.PlayArrow, "Start", LoopTheme.colors.info, mod) { vm.start(o.id); onDismiss() }
                    if (t.hasDueDate) {
                        SheetAction(Icons.Rounded.Today, "Tomorrow", LoopTheme.colors.warning, mod) { vm.postpone(o.id, from + 1); onDismiss() }
                        SheetAction(Icons.Rounded.Snooze, "+3 days", LoopTheme.colors.warning, mod) { vm.postpone(o.id, from + 3); onDismiss() }
                    }
                    SheetAction(Icons.Rounded.Event, "Pick date", MaterialTheme.colorScheme.primary, mod) { pickDate = true }
                    if (t.isRecurring) SheetAction(Icons.Rounded.SkipNext, "Skip this", MaterialTheme.colorScheme.outline, mod) { vm.skip(o.id); onDismiss() }
                } else {
                    SheetAction(Icons.Rounded.Replay, "Reopen", MaterialTheme.colorScheme.primary, mod) { vm.reopen(o.id); onDismiss() }
                }
                SheetAction(Icons.Rounded.Edit, "Edit", MaterialTheme.colorScheme.primary, mod) { onDismiss(); nav.navigate(Routes.taskEdit(t.id)) }
                if (t.isRecurring) {
                    SheetAction(Icons.Rounded.Cancel, "Delete this one", LoopTheme.colors.danger, mod) { vm.cancelOccurrence(o.id); onDismiss() }
                    SheetAction(Icons.Rounded.DeleteOutline, "Delete series", LoopTheme.colors.danger, mod) { confirmSeries = true }
                } else {
                    SheetAction(Icons.Rounded.DeleteOutline, "Delete", LoopTheme.colors.danger, mod) { confirmSeries = true }
                }
            }
        }
    }
    if (pickDate) DatePickerSheet(o.dueDate, onDismiss = { pickDate = false }, onPick = { vm.postpone(o.id, it); onDismiss() }, title = "Reschedule to")
    if (confirmSeries) ConfirmDialog(
        title = if (t.isRecurring) "Delete the whole series?" else "Delete task?",
        message = if (t.isRecurring) "“${t.title}” and all its history will be removed. To remove only this date, use “Delete this one”."
        else "“${t.title}” will be removed.",
        confirm = "Delete", destructive = true,
        onDismiss = { confirmSeries = false },
        onConfirm = { vm.deleteSeries(t.id); onDismiss() },
    )
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.10f)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
