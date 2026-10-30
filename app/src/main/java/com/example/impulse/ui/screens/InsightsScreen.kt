package com.example.impulse.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.impulse.domain.fmtMoney
import com.example.impulse.domain.fmtQty
import com.example.impulse.ui.DayBar
import com.example.impulse.ui.InsightsViewModel
import com.example.impulse.ui.components.EmptyState
import com.example.impulse.ui.components.HeroCard
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.ProgressRing
import com.example.impulse.ui.components.ScreenHeader
import com.example.impulse.ui.components.SectionHeader
import com.example.impulse.ui.components.StockBar
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.theme.LoopTheme
import com.example.impulse.ui.theme.paletteColor

@Composable
fun InsightsScreen() {
    val vm = loopViewModel { InsightsViewModel(it) }
    val s by vm.state.collectAsState()

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Insights", subtitle = "Last 30 days") }
        item {
            HeroCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column {
                    Text("COMPLETED THIS WEEK", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text("${s.completed7} tasks", style = MaterialTheme.typography.displaySmall, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    BarChart(s.weekBars, Color.White, Color.White.copy(alpha = 0.25f), Modifier.fillMaxWidth().height(110.dp))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RateCard("On time", s.onTimeRate, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                RateCard("Completed", s.completionRate, LoopTheme.colors.success, Modifier.weight(1f))
                RateCard("Habits", s.habitRate, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }
        if (s.missed30 > 0) item {
            LoopCard(Modifier.padding(horizontal = 16.dp), color = LoopTheme.colors.warningContainer, border = false) {
                Text("${s.missed30} scheduled task${if (s.missed30 == 1) " was" else "s were"} missed or skipped this month.", style = MaterialTheme.typography.bodyMedium)
                Text("Try moving routines to a day that suits you better, or enable auto-skip for daily chores.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (s.topStreaks.isNotEmpty()) {
            item { SectionHeader("Streaks") }
            item {
                LoopCard(Modifier.padding(horizontal = 16.dp)) {
                    s.topStreaks.forEachIndexed { i, h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(listOf("🥇", "🥈", "🥉")[i], fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("${h.habit.emoji} ${h.habit.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text("🔥 ${h.currentStreak} ${h.streakUnit}${if (h.currentStreak == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = LoopTheme.colors.warning)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Household") }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("🛍️", s.spend30.fmtMoney(s.currency), "Spent on groceries", Modifier.weight(1f))
                MiniStat("🗑️", "${s.discardedCount}", if (s.discardedValue > 0) "Discarded · ${s.discardedValue.fmtMoney(s.currency)}" else "Items discarded", Modifier.weight(1f))
            }
        }
        if (s.spendByCategory.isNotEmpty()) item {
            LoopCard(Modifier.padding(horizontal = 16.dp)) {
                Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                val max = s.spendByCategory.maxOf { it.amount }.coerceAtLeast(1.0)
                s.spendByCategory.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                        Text("${c.category.emoji} ${c.category.label}", Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium)
                        StockBar((c.amount / max).toFloat(), paletteColor(i), Modifier.weight(1f), height = 10.dp)
                        Text(c.amount.fmtMoney(s.currency), Modifier.width(72.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (s.topConsumed.isNotEmpty()) item {
            LoopCard(Modifier.padding(horizontal = 16.dp)) {
                Text("Most used", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                s.topConsumed.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.item.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(c.item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${c.qty.fmtQty()} ${c.item.unit.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (!s.loading && s.itemsTracked == 0 && s.completed7 == 0 && s.topStreaks.isEmpty()) item {
            EmptyState("📊", "Insights grow with you", "Track a few items, chores and habits — patterns appear here within a week.")
        }
        item {
            Text(
                "${s.itemsTracked} items tracked · ${s.recurringCount} recurring routines",
                Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RateCard(label: String, rate: Float, color: Color, modifier: Modifier) {
    LoopCard(modifier, contentPadding = PaddingValues(12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(rate, size = 64.dp, stroke = 7.dp, color = color) {
                Text("${(rate * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniStat(emoji: String, value: String, label: String, modifier: Modifier) {
    LoopCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun BarChart(bars: List<DayBar>, color: Color, track: Color, modifier: Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val grow by animateFloatAsState(if (started) 1f else 0f, tween(900), label = "bars")
    val max = (bars.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val bw = slot * 0.5f
            bars.forEachIndexed { i, b ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                val h = size.height * (b.value / max) * grow
                if (h > 0) drawRoundRect(color.copy(alpha = if (b.isToday) 1f else 0.85f), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { b ->
                Text(b.label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = if (b.isToday) 1f else 0.75f))
            }
        }
    }
}
