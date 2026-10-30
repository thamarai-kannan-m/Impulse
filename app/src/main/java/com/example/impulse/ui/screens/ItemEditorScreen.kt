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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.impulse.data.ConsumptionMode
import com.example.impulse.data.ExpiryKind
import com.example.impulse.data.InventoryItem
import com.example.impulse.data.ItemCategory
import com.example.impulse.data.NewBatch
import com.example.impulse.data.StockUnit
import com.example.impulse.data.StorageLocation
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ItemTemplate
import com.example.impulse.domain.Templates
import com.example.impulse.domain.fmtQty
import com.example.impulse.ui.ItemEditorViewModel
import com.example.impulse.ui.Routes
import com.example.impulse.ui.components.ChoiceChips
import com.example.impulse.ui.components.DatePickerSheet
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.QuantityStepper
import com.example.impulse.ui.loopViewModel

@Composable
fun ItemEditorScreen(nav: NavController, itemId: Long?, initialName: String) {
    val vm = loopViewModel(key = "item-edit-${itemId ?: "new"}") { ItemEditorViewModel(it, itemId) }
    val settings by vm.settings.collectAsState()
    val isNew = itemId == null
    val today = Dates.today()

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var original by androidx.compose.runtime.remember { mutableStateOf<InventoryItem?>(null) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var emoji by rememberSaveable { mutableStateOf("📦") }
    var category by rememberSaveable { mutableStateOf(ItemCategory.GROCERY) }
    var unit by rememberSaveable { mutableStateOf(StockUnit.KG) }
    var location by rememberSaveable { mutableStateOf(StorageLocation.PANTRY) }
    var mode by rememberSaveable { mutableStateOf(ConsumptionMode.DURATION) }
    var qty by rememberSaveable { mutableStateOf(1.0) }
    var purchase by rememberSaveable { mutableStateOf(today) }
    var price by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var hasExpiry by rememberSaveable { mutableStateOf(false) }
    var expiryKind by rememberSaveable { mutableStateOf(ExpiryKind.BEST_BEFORE) }
    var expiry by rememberSaveable { mutableStateOf(today + 7) }
    var freshness by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable { mutableStateOf("") }
    var leadDays by rememberSaveable { mutableStateOf(settings?.defaultReorderDays ?: 5) }
    var warnDays by rememberSaveable { mutableStateOf(settings?.defaultExpiryWarnDays ?: 2) }
    var essential by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf("") }
    var pickPurchase by rememberSaveable { mutableStateOf(false) }
    var pickExpiry by rememberSaveable { mutableStateOf(false) }
    var templateApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        if (isNew) return@LaunchedEffect
        val i = vm.load() ?: return@LaunchedEffect
        original = i
        if (!loaded) {
            name = i.name; emoji = i.emoji; category = i.category; unit = i.unit; location = i.location
            mode = i.consumptionMode; duration = i.typicalDurationDays?.toString() ?: ""; freshness = i.freshnessDays?.toString() ?: ""
            threshold = if (i.lowStockThreshold > 0) i.lowStockThreshold.fmtQty() else ""
            leadDays = i.reorderLeadDays; warnDays = i.expiryWarnDays; essential = i.essential; notes = i.notes
            loaded = true
        }
    }

    fun applyTemplate(t: ItemTemplate) {
        name = t.name; emoji = t.emoji; category = t.category; unit = t.unit; location = t.location
        qty = t.defaultQty; duration = t.durationDays?.toString() ?: ""
        threshold = if (t.lowThreshold > 0) t.lowThreshold.fmtQty() else ""
        if (t.freshnessDays != null) {
            hasExpiry = true; expiryKind = t.expiryKind; freshness = t.freshnessDays.toString(); expiry = today + t.freshnessDays
        } else { hasExpiry = false; freshness = "" }
        mode = if (t.durationDays != null) ConsumptionMode.DURATION else ConsumptionMode.MANUAL
        templateApplied = true
    }

    val canSave = name.isNotBlank() && (!isNew || qty > 0) && (isNew || original != null)

    fun save() {
        val base = original ?: InventoryItem(name = name)
        val item = base.copy(
            name = name.trim(), emoji = emoji, category = category, unit = unit, location = location,
            consumptionMode = mode, typicalDurationDays = duration.toIntOrNull(), freshnessDays = freshness.toIntOrNull(),
            lowStockThreshold = threshold.toDoubleOrNull() ?: 0.0, reorderLeadDays = leadDays, expiryWarnDays = warnDays,
            essential = essential, notes = notes.trim(),
        )
        val batch = if (isNew) NewBatch(
            quantity = qty, purchaseDate = purchase, expiryDate = if (hasExpiry) expiry else null, expiryKind = expiryKind,
            price = price.toDoubleOrNull(), durationDays = if (mode == ConsumptionMode.DURATION) duration.toIntOrNull() else null,
        ) else null
        vm.save(item, batch) { id ->
            nav.popBackStack()
            if (isNew) nav.navigate(Routes.item(id))
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (isNew) "New item" else "Edit item") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Close") } },
            actions = { TextButton(onClick = ::save, enabled = canSave) { Text("Save") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 30.sp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it; templateApplied = false }, label = { Text("Item name") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            if (isNew && !templateApplied) {
                val matches = Templates.matchItem(name)
                if (matches.isNotEmpty()) {
                    Text("Quick fill", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(matches) { t ->
                            Row(
                                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { applyTemplate(t) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { Text(t.emoji); Spacer(Modifier.width(6.dp)); Text(t.name, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Templates.emojiChoices) { e ->
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                            .then(if (e == emoji) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
            }

            EditorSection("Category") { ChoiceChips(ItemCategory.entries, category, { "${it.emoji} ${it.label}" }, { category = it }) }
            EditorSection("Unit") { ChoiceChips(StockUnit.entries, unit, { it.label }, { unit = it }) }
            EditorSection("Stored in") { ChoiceChips(StorageLocation.entries, location, { "${it.emoji} ${it.label}" }, { location = it }) }

            if (isNew) LoopCard {
                Text("This purchase", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QuantityStepper(qty, unit.step, unit.label, { qty = it }, min = 0.0) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Price (${settings?.currency ?: "₹"})") }, singleLine = true, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    TextButton(onClick = { pickPurchase = true }, modifier = Modifier.weight(1f)) { Text("Bought ${Dates.relative(purchase, today).lowercase()}") }
                }
            }

            LoopCard {
                Text("How should we predict stock?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ConsumptionMode.entries.forEach { m ->
                    val sel = m == mode
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { mode = m }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(sel, { mode = m })
                        Column {
                            Text(m.label, style = MaterialTheme.typography.titleSmall)
                            Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                AnimatedVisibility(mode != ConsumptionMode.MANUAL) {
                    OutlinedTextField(
                        value = duration, onValueChange = { duration = it.filter(Char::isDigit) },
                        label = { Text(if (mode == ConsumptionMode.DURATION) "One purchase lasts about (days)" else "Starting guess: lasts (days, optional)") },
                        supportingText = {
                            val d = duration.toIntOrNull()
                            if (d != null && d > 0 && isNew) Text("≈ ${(qty / d).let { if (it >= 1) it.fmtQty() else String.format(java.util.Locale.getDefault(), "%.3f", it) }} ${unit.label}/day · runs out ${Dates.formatShort(purchase + d)}")
                        },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            LoopCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Perishable", style = MaterialTheme.typography.titleMedium)
                        Text("Track an expiry or freshness window", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(hasExpiry || freshness.isNotBlank(), { hasExpiry = it; if (!it) freshness = "" })
                }
                AnimatedVisibility(hasExpiry || freshness.isNotBlank()) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = freshness, onValueChange = { v -> freshness = v.filter(Char::isDigit); freshness.toIntOrNull()?.let { expiry = purchase + it } },
                            label = { Text("Usually stays fresh for (days)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        if (isNew) {
                            ChoiceChips(ExpiryKind.entries, expiryKind, { it.label }, { expiryKind = it })
                            TextButton(onClick = { pickExpiry = true }) { Text("${expiryKind.label}: ${Dates.formatFull(expiry)} (${Dates.relative(expiry, today)})") }
                        }
                        Text(
                            "Expiry alerts are organisational reminders, not food-safety guarantees. “Best before” isn't the same as “use by” — never eat food that looks or smells spoiled.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            LoopCard {
                Text("Reminders", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = threshold, onValueChange = { threshold = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Low-stock threshold (${unit.label})") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(10.dp))
                Text("Remind me before it runs out", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(1, 3, 5, 7, 10), leadDays, { "$it days" }, { leadDays = it })
                Spacer(Modifier.height(6.dp))
                Text("Warn me before expiry", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(0, 1, 2, 3, 5, 7), warnDays, { if (it == 0) "On the day" else "$it days" }, { warnDays = it })
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Essential", style = MaterialTheme.typography.titleSmall)
                        Text("Suggest for the shopping list when low", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(essential, { essential = it })
                }
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), minLines = 2,
            )
        }
        Button(
            onClick = ::save, enabled = canSave,
            modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().height(54.dp), shape = CircleShape,
        ) { Text(if (isNew) "Add to inventory" else "Save changes", style = MaterialTheme.typography.titleSmall) }
    }
    if (pickPurchase) DatePickerSheet(purchase, { pickPurchase = false }, { purchase = it; freshness.toIntOrNull()?.let { f -> expiry = it + f } }, "Purchase date")
    if (pickExpiry) DatePickerSheet(expiry, { pickExpiry = false }, { expiry = it }, "Expiry date")
}

@Composable
fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

