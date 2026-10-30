@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.impulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.impulse.data.BatchStatus
import com.example.impulse.data.ConsumptionMode
import com.example.impulse.data.InventoryBatch
import com.example.impulse.data.StockTransaction
import com.example.impulse.data.TxReason
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ForecastEngine
import com.example.impulse.domain.StockForecast
import com.example.impulse.domain.fmtMoney
import com.example.impulse.domain.fmtQty
import com.example.impulse.ui.ItemDetailViewModel
import com.example.impulse.ui.Routes
import com.example.impulse.ui.components.ConfirmDialog
import com.example.impulse.ui.components.DatePickerSheet
import com.example.impulse.ui.components.LocalSnackbar
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.ProgressRing
import com.example.impulse.ui.components.QuantityStepper
import com.example.impulse.ui.components.SectionHeader
import com.example.impulse.ui.components.StatusPill
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.theme.LoopTheme
import kotlinx.coroutines.launch

@Composable
fun ItemDetailScreen(nav: NavController, itemId: Long) {
    val vm = loopViewModel(key = "item-$itemId") { ItemDetailViewModel(it, itemId) }
    val f by vm.forecast.collectAsState()
    val batches by vm.batches.collectAsState()
    val txs by vm.transactions.collectAsState()
    val settings by vm.settings.collectAsState()
    val currency = settings?.currency ?: "₹"
    var menu by remember { mutableStateOf(false) }
    var restock by remember { mutableStateOf(false) }
    var useDialog by remember { mutableStateOf(false) }
    var exactDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var expiryEdit by remember { mutableStateOf<InventoryBatch?>(null) }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(f?.item?.name ?: "") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { nav.navigate(Routes.itemEdit(itemId)) }) { Icon(Icons.Rounded.Edit, "Edit") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Rounded.Archive, null) }, onClick = { menu = false; vm.archive(); nav.popBackStack() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        val fc = f
        if (fc != null) {
            val item = fc.item
            val color = stockColor(fc)

            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(fc.fillFraction, size = 150.dp, stroke = 14.dp, color = color) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(item.emoji, fontSize = 46.sp)
                                Text("${(fc.fillFraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = color)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill("${item.category.emoji} ${item.category.label}", MaterialTheme.colorScheme.primary)
                            StatusPill("${item.location.emoji} ${item.location.label}", MaterialTheme.colorScheme.secondary)
                            StatusPill(item.consumptionMode.label, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item { ForecastCard(fc, Modifier.padding(horizontal = 16.dp)) }

                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Quick stock check", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        val cap = maxOf(fc.lastPurchaseQty, fc.recordedQty)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AssistChip(onClick = { useDialog = true }, label = { Text("➖ Used some") }, shape = CircleShape)
                            AssistChip(onClick = { vm.setRemaining(itemId, cap * 0.5) }, label = { Text("◐ Half left") }, shape = CircleShape)
                            AssistChip(onClick = { vm.setRemaining(itemId, cap * 0.1) }, label = { Text("◔ Almost empty") }, shape = CircleShape)
                            AssistChip(onClick = {
                                vm.setRemaining(itemId, 0.0)
                                scope.launch {
                                    val r = snackbar.showSnackbar("Marked finished. Add to shopping list?", actionLabel = "Add")
                                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.addToShopping(item, fc.lastPurchaseQty.takeIf { it > 0 })
                                }
                            }, label = { Text("○ Finished") }, shape = CircleShape)
                            AssistChip(onClick = { exactDialog = true }, label = { Text("✎ Set exact") }, shape = CircleShape)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { restock = true }, modifier = Modifier.weight(1f).height(50.dp), shape = CircleShape) {
                                Icon(Icons.Rounded.ShoppingBag, null); Spacer(Modifier.width(6.dp)); Text("Restock")
                            }
                            OutlinedButton(onClick = {
                                vm.addToShopping(item, fc.lastPurchaseQty.takeIf { it > 0 })
                                scope.launch { snackbar.showSnackbar("${item.name} added to shopping list") }
                            }, modifier = Modifier.weight(1f).height(50.dp), shape = CircleShape) {
                                Icon(Icons.Rounded.AddShoppingCart, null); Spacer(Modifier.width(6.dp)); Text("Add to list")
                            }
                        }
                    }
                }

                val active = batches.filter { it.status == BatchStatus.ACTIVE }
                item { SectionHeader("Batches", count = active.size) }
                if (active.isEmpty()) item {
                    Text("No stock on hand. Restock to add a new batch.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(active, key = { it.id }) { b ->
                    BatchCard(b, fc, currency, Modifier.padding(horizontal = 16.dp),
                        onUsed = { vm.closeBatch(b.id, false) }, onDiscard = { vm.closeBatch(b.id, true) }, onExpiry = { expiryEdit = b })
                }
                val closed = batches.filter { it.status != BatchStatus.ACTIVE }.take(5)
                if (closed.isNotEmpty()) item {
                    Text(
                        "Past batches: " + closed.joinToString { "${Dates.formatShort(it.purchaseDate)} (${if (it.status == BatchStatus.DISCARDED) "discarded" else "used"})" },
                        Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (txs.isNotEmpty()) {
                    item { SectionHeader("History") }
                    item {
                        LoopCard(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 14.dp)) {
                            txs.take(20).forEachIndexed { i, t ->
                                TxRow(t, item.unit.label, currency)
                                if (i < minOf(txs.size, 20) - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                if (item.notes.isNotBlank()) item {
                    LoopCard(Modifier.padding(horizontal = 16.dp)) { Text("Notes", style = MaterialTheme.typography.titleSmall); Text(item.notes) }
                }
            }

            if (restock) RestockSheet(item, fc, onDismiss = { restock = false }) { vm.restock(itemId, it) }
            if (useDialog) AmountDialog("How much did you use?", item.unit.step, item.unit.label, onDismiss = { useDialog = false }) { vm.useAmount(itemId, it) }
            if (exactDialog) AmountDialog("How much is left?", item.unit.step, item.unit.label, initial = fc.estimatedQty, onDismiss = { exactDialog = false }) { vm.setRemaining(itemId, it) }
            if (confirmDelete) ConfirmDialog(
                "Delete ${item.name}?", "All batches and history for this item will be removed. Archive instead to keep history.",
                "Delete", { confirmDelete = false }, { vm.delete(); nav.popBackStack() }, destructive = true,
            )
            expiryEdit?.let { b ->
                DatePickerSheet(b.expiryDate ?: Dates.today(), onDismiss = { expiryEdit = null }, onPick = { vm.updateExpiry(b.id, it, b.expiryKind) }, title = "${b.expiryKind.label} date")
            }
        }
    }
}

@Composable
private fun ForecastCard(f: StockForecast, modifier: Modifier) {
    val item = f.item
    val color = stockColor(f)
    LoopCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${item.name} – consumption estimate", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(
                when (f.stockCondition) {
                    com.example.impulse.domain.StockCondition.OUT_OF_STOCK -> "Out of stock"
                    com.example.impulse.domain.StockCondition.LOW_STOCK -> "Running low soon"
                    else -> "In stock"
                }, color,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Metric(if (f.isEstimated) "Remaining (est.)" else "Remaining", "${f.estimatedQty.fmtQty()} ${item.unit.label}", Modifier.weight(1f))
            Metric("Daily use", f.dailyUsage?.let { "${it.fmtQty3()} ${item.unit.label}" } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.10f)).padding(14.dp)) {
            Column {
                Text("Estimated days remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    f.daysRemaining?.let { "${it.toInt()} days" } ?: if (item.consumptionMode == ConsumptionMode.MANUAL) "Manual tracking" else "Not enough data",
                    style = MaterialTheme.typography.displaySmall, color = color, fontWeight = FontWeight.ExtraBold,
                )
                f.depletionDate?.let { Text("Runs out around ${Dates.formatLong(it)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        f.reorderDate?.let { r ->
            Spacer(Modifier.height(10.dp))
            val today = Dates.today()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Reorder reminder at ${item.reorderLeadDays} days · " + if (r <= today) "now" else Dates.relative(r, today).lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${f.confidence.label}. Predictions are estimates based on ${if (item.consumptionMode == ConsumptionMode.USAGE) "your stock updates (${item.usageSamples} so far)" else "the usage rate you set"}, not measurements. Correct them anytime with a quick stock check.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Double.fmtQty3(): String = if (this >= 1) fmtQty() else String.format(java.util.Locale.getDefault(), "%.3f", this).trimEnd('0').trimEnd('.')

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun BatchCard(b: InventoryBatch, f: StockForecast, currency: String, modifier: Modifier, onUsed: () -> Unit, onDiscard: () -> Unit, onExpiry: () -> Unit) {
    val today = Dates.today()
    val left = b.expiryDate?.let { it - today }
    val cond = ForecastEngine.expiryCondition(left, f.item.expiryWarnDays)
    LoopCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${b.remaining.fmtQty()} / ${b.quantity.fmtQty()} ${f.item.unit.label}", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull("Bought ${Dates.relative(b.purchaseDate, today).lowercase()}", b.price?.let { it.fmtMoney(currency) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (left != null) StatusPill(expiryText(left, b.expiryKind), expiryColor(cond), icon = Icons.Rounded.Event)
        }
        Row {
            TextButton(onClick = onUsed) { Text("Used up") }
            TextButton(onClick = onDiscard) { Text("Discard", color = LoopTheme.colors.danger) }
            TextButton(onClick = onExpiry) { Text(if (b.expiryDate == null) "Add date" else "Edit date") }
        }
    }
}

@Composable
private fun TxRow(t: StockTransaction, unit: String, currency: String) {
    val (emoji, color) = when (t.reason) {
        TxReason.PURCHASE -> "🛍️" to LoopTheme.colors.success
        TxReason.CONSUME -> "🍽️" to MaterialTheme.colorScheme.primary
        TxReason.ADJUST -> "✎" to MaterialTheme.colorScheme.onSurfaceVariant
        TxReason.DISCARD -> "🗑️" to LoopTheme.colors.danger
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.note.ifBlank { t.reason.label }, style = MaterialTheme.typography.titleSmall)
            Text(Dates.formatShort(Dates.epochDayOf(t.timestamp)) + (t.price?.let { " · ${it.fmtMoney(currency)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text((if (t.quantityDelta > 0) "+" else "") + "${t.quantityDelta.fmtQty()} $unit", style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun AmountDialog(title: String, step: Double, unit: String, initial: Double = step, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QuantityStepper(v, step, unit, { v = it }) } },
        confirmButton = { TextButton(onClick = { onConfirm(v); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

