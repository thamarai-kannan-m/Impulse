@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.impulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.impulse.data.ConsumptionMode
import com.example.impulse.data.ExpiryKind
import com.example.impulse.data.InventoryItem
import com.example.impulse.data.NewBatch
import com.example.impulse.data.ShoppingListItem
import com.example.impulse.data.ShoppingState
import com.example.impulse.data.StorageLocation
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ExpiryCondition
import com.example.impulse.domain.StockCondition
import com.example.impulse.domain.StockForecast
import com.example.impulse.domain.Templates
import com.example.impulse.domain.fmtQty
import com.example.impulse.ui.InventoryViewModel
import com.example.impulse.ui.Routes
import com.example.impulse.ui.components.ChoiceChips
import com.example.impulse.ui.components.DatePickerSheet
import com.example.impulse.ui.components.EmojiBadge
import com.example.impulse.ui.components.EmptyState
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.LoopCheck
import com.example.impulse.ui.components.PillTabs
import com.example.impulse.ui.components.QuantityStepper
import com.example.impulse.ui.components.ScreenHeader
import com.example.impulse.ui.components.SectionHeader
import com.example.impulse.ui.components.StatusPill
import com.example.impulse.ui.components.StockBar
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.theme.LoopTheme

private enum class StockFilter(val label: String) { ALL("All"), ATTENTION("Needs attention"), LOW("Low stock"), EXPIRING("Expiring") }

@Composable
fun stockColor(f: StockForecast): Color = when (f.stockCondition) {
    StockCondition.OUT_OF_STOCK -> LoopTheme.colors.danger
    StockCondition.LOW_STOCK -> LoopTheme.colors.warning
    StockCondition.IN_STOCK -> LoopTheme.colors.success
}

@Composable
fun expiryColor(c: ExpiryCondition): Color = when (c) {
    ExpiryCondition.EXPIRED, ExpiryCondition.EXPIRES_TODAY -> LoopTheme.colors.danger
    ExpiryCondition.EXPIRING_SOON -> LoopTheme.colors.warning
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun expiryText(daysLeft: Long?, kind: ExpiryKind): String = when {
    daysLeft == null -> ""
    daysLeft < 0 -> if (kind == ExpiryKind.ESTIMATED) "Likely spoiled" else "Expired ${-daysLeft}d ago"
    daysLeft == 0L -> "${kind.label} today"
    daysLeft == 1L -> "${kind.label} tomorrow"
    else -> "${kind.label} in ${daysLeft}d"
}

@Composable
fun InventoryScreen(nav: NavController, initialTab: Int = 0) {
    val vm = loopViewModel { InventoryViewModel(it) }
    val s by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var restockFor by remember { mutableStateOf<InventoryItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Inventory", subtitle = "${s.forecasts.size} items tracked")
            PillTabs(
                listOf("Stock", "Shopping list${s.shopping.count { it.state == ShoppingState.NEEDED }.let { if (it > 0) " ($it)" else "" }}"),
                tab, { tab = it }, Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (tab == 0) StockTab(s.forecasts, s.archived, nav, onUnarchive = vm::unarchive)
            else ShoppingTab(vm, s.shopping, s.suggestions, s.forecasts, onRestock = { restockFor = it })
        }
        if (tab == 0) ExtendedFloatingActionButton(
            onClick = { nav.navigate(Routes.itemEdit()) },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add item") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = RoundedCornerShape(20.dp),
        )
    }
    restockFor?.let { item ->
        val f = s.forecasts.firstOrNull { it.item.id == item.id }
        RestockSheet(item, f, onDismiss = { restockFor = null }) { vm.restock(item.id, it) }
    }
}

@Composable
private fun StockTab(forecasts: List<StockForecast>, archived: List<InventoryItem>, nav: NavController, onUnarchive: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StockFilter.ALL) }
    var location by rememberSaveable { mutableStateOf<StorageLocation?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val visible = forecasts.filter { f ->
        (query.isBlank() || f.item.name.contains(query, true)) &&
            (location == null || f.item.location == location) &&
            when (filter) {
                StockFilter.ALL -> true
                StockFilter.ATTENTION -> f.needsAttention
                StockFilter.LOW -> f.stockCondition != StockCondition.IN_STOCK
                StockFilter.EXPIRING -> f.expiryCondition in setOf(ExpiryCondition.EXPIRED, ExpiryCondition.EXPIRES_TODAY, ExpiryCondition.EXPIRING_SOON)
            }
    }.sortedWith(compareBy({ !it.needsAttention }, { it.daysRemaining ?: Double.MAX_VALUE }, { it.item.name.lowercase() }))

    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, placeholder = { Text("Search groceries & supplies") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                singleLine = true, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StockFilter.entries) { f ->
                    val count = when (f) {
                        StockFilter.ALL -> 0
                        StockFilter.ATTENTION -> forecasts.count { it.needsAttention }
                        StockFilter.LOW -> forecasts.count { it.stockCondition != StockCondition.IN_STOCK }
                        StockFilter.EXPIRING -> forecasts.count { it.expiryCondition in setOf(ExpiryCondition.EXPIRED, ExpiryCondition.EXPIRES_TODAY, ExpiryCondition.EXPIRING_SOON) }
                    }
                    FilterChip(
                        selected = filter == f, onClick = { filter = f }, shape = CircleShape,
                        label = { Text(if (count > 0) "${f.label} · $count" else f.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
                items(StorageLocation.entries.filter { loc -> forecasts.any { it.item.location == loc } }) { loc ->
                    FilterChip(
                        selected = location == loc, onClick = { location = if (location == loc) null else loc }, shape = CircleShape,
                        label = { Text("${loc.emoji} ${loc.label}") },
                    )
                }
            }
        }
        if (forecasts.isEmpty()) item {
            EmptyState("🧺", "Your pantry is empty", "Add groceries and supplies to see when they'll run out and what to use first.") {
                Button(onClick = { nav.navigate(Routes.itemEdit()) }) { Text("Add your first item") }
            }
        } else if (visible.isEmpty()) item {
            EmptyState("🔍", "Nothing here", "No items match this filter.")
        }
        items(visible, key = { it.item.id }) { f ->
            ItemCard(f, Modifier.padding(horizontal = 16.dp).animateItem()) { nav.navigate(Routes.item(f.item.id)) }
        }
        if (archived.isNotEmpty()) {
            item {
                TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Archived (${archived.size})")
                    Icon(if (showArchived) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null)
                }
            }
            if (showArchived) items(archived, key = { "ar-${it.id}" }) { item ->
                LoopCard(Modifier.padding(horizontal = 16.dp).alpha(0.7f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { onUnarchive(item.id) }) { Text("Restore") }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemCard(f: StockForecast, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val item = f.item
    val color = stockColor(f)
    LoopCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(item.emoji, color, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Text(item.location.emoji, fontSize = 13.sp)
                }
                Text(
                    buildString {
                        append(if (f.isEstimated) "~" else "")
                        append(f.estimatedQty.fmtQty()).append(" ").append(item.unit.label).append(" left")
                        f.daysRemaining?.let { append(" · ").append(if (it < 1) "runs out today" else "≈ ${it.toInt()} days") }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (f.stockCondition) {
                StockCondition.OUT_OF_STOCK -> StatusPill("Out", color)
                StockCondition.LOW_STOCK -> StatusPill("Low", color)
                else -> {}
            }
        }
        Spacer(Modifier.height(10.dp))
        StockBar(f.fillFraction, color)
        f.nearestExpiry?.let { be ->
            if (be.condition != ExpiryCondition.NONE) {
                Spacer(Modifier.height(8.dp))
                StatusPill(expiryText(be.daysLeft, be.batch.expiryKind), expiryColor(be.condition), icon = Icons.Rounded.Event)
            }
        }
    }
}

// ------------------------------------------------------------------------- shopping

@Composable
private fun ShoppingTab(
    vm: InventoryViewModel,
    list: List<ShoppingListItem>,
    suggestions: List<StockForecast>,
    forecasts: List<StockForecast>,
    onRestock: (InventoryItem) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val needed = list.filter { it.state == ShoppingState.NEEDED }
    val bought = list.filter { it.state == ShoppingState.PURCHASED }
    val itemsById = forecasts.associateBy { it.item.id }

    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, placeholder = { Text("Add to list…") }, singleLine = true,
                    shape = CircleShape, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { vm.addShopping(text, null, com.example.impulse.data.StockUnit.PCS); text = "" } }),
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.FilledIconButton(onClick = { if (text.isNotBlank()) { vm.addShopping(text, null, com.example.impulse.data.StockUnit.PCS); text = "" } }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Add, "Add")
                }
            }
        }
        if (suggestions.isNotEmpty()) item {
            LoopCard(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), border = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Running low", style = MaterialTheme.typography.titleMedium)
                        Text("Based on your stock estimates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = vm::addAllSuggestions) { Text("Add all") }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { f ->
                        Row(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { vm.addToShopping(f.item, f.lastPurchaseQty.takeIf { it > 0 }) }
                                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(f.item.emoji); Spacer(Modifier.width(6.dp))
                            Text(f.item.name, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.AddShoppingCart, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (needed.isEmpty() && suggestions.isEmpty()) item {
            EmptyState("🛒", "List is clear", "Low-stock items will be suggested here automatically.")
        }
        if (needed.isNotEmpty()) item { SectionHeader("To buy", count = needed.size) }
        items(needed, key = { it.id }) { s ->
            ShoppingRow(s, Modifier.padding(horizontal = 16.dp).animateItem(), onToggle = { vm.togglePurchased(s) }, onDelete = { vm.deleteShopping(s) })
        }
        if (bought.isNotEmpty()) {
            item { SectionHeader("Bought", action = "Clear", onAction = vm::clearPurchased) }
            items(bought, key = { it.id }) { s ->
                val inv = s.inventoryItemId?.let { itemsById[it]?.item }
                ShoppingRow(
                    s, Modifier.padding(horizontal = 16.dp).animateItem(), onToggle = { vm.togglePurchased(s) }, onDelete = { vm.deleteShopping(s) },
                    trailing = if (inv != null) ({ TextButton(onClick = { onRestock(inv) }) { Text("Restock") } }) else null,
                )
            }
        }
    }
}

@Composable
private fun ShoppingRow(s: ShoppingListItem, modifier: Modifier, onToggle: () -> Unit, onDelete: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val done = s.state == ShoppingState.PURCHASED
    LoopCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoopCheck(done, LoopTheme.colors.success, onToggle, size = 26.dp, label = s.name)
            Spacer(Modifier.width(12.dp))
            Text(s.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, textDecoration = if (done) TextDecoration.LineThrough else null)
                val sub = listOfNotNull(s.quantity?.let { "${it.fmtQty()} ${s.unit.label}" }, if (s.suggested) "suggested" else null).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ------------------------------------------------------------------------- restock

/** Records a new purchase batch (its own quantity, price and expiry date). */
@Composable
fun RestockSheet(item: InventoryItem, forecast: StockForecast?, onDismiss: () -> Unit, onSave: (NewBatch) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = Dates.today()
    val template = Templates.items.firstOrNull { it.name.equals(item.name, true) }
    var qty by remember { mutableStateOf(forecast?.lastPurchaseQty?.takeIf { it > 0 } ?: template?.defaultQty ?: item.unit.step.coerceAtLeast(1.0)) }
    var purchase by remember { mutableStateOf(today) }
    var hasExpiry by remember { mutableStateOf(item.freshnessDays != null) }
    var expiry by remember { mutableStateOf(today + (item.freshnessDays ?: 7)) }
    var kind by remember { mutableStateOf(template?.takeIf { item.freshnessDays != null }?.expiryKind ?: ExpiryKind.BEST_BEFORE) }
    var price by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(item.typicalDurationDays?.toString() ?: "") }
    var pickPurchase by remember { mutableStateOf(false) }
    var pickExpiry by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(item.emoji, MaterialTheme.colorScheme.primary, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Restock ${item.name}", style = MaterialTheme.typography.titleLarge)
                    Text("Saved as a new batch with its own dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QuantityStepper(qty, item.unit.step, item.unit.label, { qty = it }, min = item.unit.step)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Price (optional)") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (item.consumptionMode == ConsumptionMode.DURATION) OutlinedTextField(
                    value = duration, onValueChange = { duration = it.filter(Char::isDigit) }, label = { Text("Lasts (days)") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            TextButton(onClick = { pickPurchase = true }) { Text("Purchased: ${Dates.relative(purchase, today)} (${Dates.formatShort(purchase)})") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Has an expiry / freshness date", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(hasExpiry, { hasExpiry = it })
            }
            AnimatedVisibility(hasExpiry) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChips(ExpiryKind.entries, kind, { it.label }, { kind = it })
                    TextButton(onClick = { pickExpiry = true }) { Text("${kind.label}: ${Dates.formatFull(expiry)} (${Dates.relative(expiry, today)})") }
                }
            }
            Button(
                onClick = {
                    onSave(NewBatch(qty, purchase, if (hasExpiry) expiry else null, kind, price.toDoubleOrNull(), duration.toIntOrNull()))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
            ) { Icon(Icons.Rounded.Inventory2, null); Spacer(Modifier.width(8.dp)); Text("Add ${qty.fmtQty()} ${item.unit.label}") }
        }
    }
    if (pickPurchase) DatePickerSheet(purchase, { pickPurchase = false }, { purchase = it }, "Purchase date")
    if (pickExpiry) DatePickerSheet(expiry, { pickExpiry = false }, { expiry = it }, "Expiry date")
}
