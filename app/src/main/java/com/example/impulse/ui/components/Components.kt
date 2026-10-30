@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.impulse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.impulse.domain.Dates
import com.example.impulse.domain.fmtQty
import com.example.impulse.ui.theme.LoopTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** App-wide snackbar host so any screen can offer "Undo". */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

// ------------------------------------------------------------------------------- surfaces

@Composable
fun LoopCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) else null,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Gradient card with soft decorative orbs, used for heroes and highlights. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    brush: Brush = LoopTheme.colors.hero,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.78f, size.height * 1.05f))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.minDimension * 0.18f, center = Offset(size.width * 0.05f, size.height * 0.9f))
        }
        Box(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        actions()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            CountBadge(count)
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun CountBadge(count: Int, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text("$count", style = MaterialTheme.typography.labelMedium, color = color) }
}

// ------------------------------------------------------------------------------- data viz

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.15f),
    brush: Brush? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            drawArc(trackColor, -90f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (animated > 0f) {
                if (brush != null) drawArc(brush, -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                else drawArc(color, -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun StockBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(color.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceAtLeast(0.02f)).clip(CircleShape).background(color))
    }
}

@Composable
fun AnimatedCount(value: Int, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    val v by animateIntAsState(value, tween(600), label = "count")
    Text(v.toString().padStart(2, '0'), style = style, color = color)
}

/** Dashboard metric tile. */
@Composable
fun StatTile(
    value: Int,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    LoopCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            AnimatedCount(value, MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = if (value > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------------------- small atoms

@Composable
fun EmojiBadge(emoji: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp, corner: Dp = 14.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(emoji.ifBlank { "•" }, fontSize = (size.value * 0.48f).sp)
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Bouncy round checkbox used for tasks and habits. */
@Composable
fun LoopCheck(checked: Boolean, color: Color, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Dp = 30.dp, label: String = "Complete") {
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val bg by animateColorAsState(if (checked) color else Color.Transparent, label = "checkbg")
    LaunchedEffect(checked) {
        if (checked) { scale.snapTo(0.7f); scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
    }
    Box(
        modifier
            .size(size)
            .scale(scale.value)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (checked) color else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(role = Role.Checkbox) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() }
            .semantics { contentDescription = if (checked) "$label: done" else label },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(checked, enter = fadeIn(), exit = fadeOut()) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 40.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** Pill-shaped segmented tabs. */
@Composable
fun PillTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent, label = "tab")
            val fg by animateColorAsState(if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabfg")
            Box(
                Modifier.weight(1f).clip(CircleShape).background(bg).clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1) }
        }
    }
}

@Composable
fun QuantityStepper(value: Double, step: Double, unit: String, onChange: (Double) -> Unit, modifier: Modifier = Modifier, min: Double = 0.0) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Icon(Icons.Rounded.Remove, "Decrease") }
        Text(
            "${value.fmtQty()} $unit", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(110.dp), textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onChange(value + step) }) { Icon(Icons.Rounded.Add, "Increase") }
    }
}

// ------------------------------------------------------------------------------- gestures

/**
 * Swipe right to run [onSwipeRight] (e.g. complete), swipe left for [onSwipeLeft] (e.g. postpone).
 * Lightweight custom implementation on stable foundation APIs.
 */
@Composable
fun SwipeActionRow(
    modifier: Modifier = Modifier,
    rightIcon: ImageVector, rightColor: Color, onSwipeRight: (() -> Unit)?,
    leftIcon: ImageVector, leftColor: Color, onSwipeLeft: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var width by remember { mutableStateOf(1f) }
    val threshold = 0.32f
    Box(modifier) {
        val progress = offset.value / width
        if (progress != 0f) {
            val right = progress > 0
            Row(
                Modifier.matchParentSize().clip(RoundedCornerShape(22.dp))
                    .background((if (right) rightColor else leftColor).copy(alpha = (kotlin.math.abs(progress) * 2.2f).coerceIn(0.15f, 1f))),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (right) Arrangement.Start else Arrangement.End,
            ) {
                Icon(if (right) rightIcon else leftIcon, null, tint = Color.White, modifier = Modifier.padding(horizontal = 24.dp).size(26.dp)
                    .scale(if (kotlin.math.abs(progress) > threshold) 1.2f else 1f))
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(onSwipeLeft, onSwipeRight) {
                    width = size.width.toFloat().coerceAtLeast(1f)
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val p = offset.value / width
                            scope.launch {
                                when {
                                    p > threshold && onSwipeRight != null -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        offset.animateTo(width, tween(160)); onSwipeRight(); offset.snapTo(0f)
                                    }
                                    p < -threshold && onSwipeLeft != null -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        offset.animateTo(-width, tween(160)); onSwipeLeft(); offset.snapTo(0f)
                                    }
                                    else -> offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, drag ->
                        change.consume()
                        val next = offset.value + drag
                        val allowed = when {
                            next > 0 && onSwipeRight == null -> 0f
                            next < 0 && onSwipeLeft == null -> 0f
                            else -> next
                        }
                        scope.launch { offset.snapTo(allowed) }
                    }
                }
        ) { content() }
    }
}

// ------------------------------------------------------------------------------- dialogs

@Composable
fun DatePickerSheet(initial: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit, title: String = "Pick a date") {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: Dates.today()) * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86_400_000L)) }; onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
fun TimePickerSheet(initialMinutes: Int?, onDismiss: () -> Unit, onPick: (Int) -> Unit, title: String = "Pick a time") {
    val init = initialMinutes ?: (9 * 60)
    val state = rememberTimePickerState(initialHour = init / 60, initialMinute = init % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A tappable settings-style row with a value. */
@Composable
fun ValueRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
