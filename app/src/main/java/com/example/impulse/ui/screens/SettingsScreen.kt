@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.impulse.ui.screens

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.example.impulse.data.ThemeMode
import com.example.impulse.data.UserSettings
import com.example.impulse.domain.Dates
import com.example.impulse.domain.ReminderSettings
import com.example.impulse.notifications.ReminderEngine
import com.example.impulse.ui.SettingsViewModel
import com.example.impulse.ui.components.ChoiceChips
import com.example.impulse.ui.components.ConfirmDialog
import com.example.impulse.ui.components.LocalSnackbar
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.components.TimePickerSheet
import com.example.impulse.ui.components.ValueRow
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.theme.LoopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TimeField { MORNING, QUIET_START, QUIET_END, DIGEST }

@Composable
fun SettingsScreen(nav: NavController) {
    val vm = loopViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val settings = s ?: UserSettings()
    val r = settings.reminders
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var notifOn by remember { mutableStateOf(true) }
    var exactOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notifOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactOn = context.getSystemService(AlarmManager::class.java)?.let { ReminderEngine.canExact(it) } ?: true
        onPauseOrDispose { }
    }
    var editName by remember { mutableStateOf(false) }
    var addMember by remember { mutableStateOf(false) }
    var timeField by remember { mutableStateOf<TimeField?>(null) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    var confirmSample by remember { mutableStateOf(false) }

    fun rem(t: (ReminderSettings) -> ReminderSettings) = vm.update { it.copy(reminders = t(it.reminders)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Group("Profile") {
                ValueRow(Icons.Rounded.Person, "Your name", settings.userName.ifBlank { "Add" }, { editName = true })
                Text("Currency", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                ChoiceChips(listOf("₹", "$", "€", "£", "¥"), settings.currency, { it }, { c -> vm.update { it.copy(currency = c) } }, Modifier.padding(horizontal = 12.dp))
            }

            Group("Appearance") {
                ChoiceChips(ThemeMode.entries, settings.themeMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, { m -> vm.update { it.copy(themeMode = m) } }, Modifier.padding(horizontal = 12.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SwitchRow("Use wallpaper colours", "Material You dynamic colour", settings.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
            }

            Group("Reminders") {
                if (!notifOn) StatusRow("Notifications are blocked", "Open settings") {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                if (!exactOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) StatusRow("Precise timing is off — reminders may arrive a few minutes late", "Allow") {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
                SwitchRow("🧺 Low stock", "When items may run out", r.inventoryEnabled) { v -> rem { it.copy(inventoryEnabled = v) } }
                SwitchRow("🥛 Expiry & freshness", "Before food reaches its date", r.expiryEnabled) { v -> rem { it.copy(expiryEnabled = v) } }
                SwitchRow("🧹 Tasks & routines", "Due chores and bills", r.tasksEnabled) { v -> rem { it.copy(tasksEnabled = v) } }
                SwitchRow("🏃 Habits", "Habit reminder times", r.habitsEnabled) { v -> rem { it.copy(habitsEnabled = v) } }
                ValueRow(Icons.Rounded.WbSunny, "Inventory alerts at", Dates.formatTime(r.morningMinutes), { timeField = TimeField.MORNING })
                SwitchRow("Daily digest", "One summary instead of many pings", r.digestEnabled) { v -> rem { it.copy(digestEnabled = v) } }
                if (r.digestEnabled) ValueRow(Icons.Rounded.Summarize, "Digest at", Dates.formatTime(r.digestMinutes), { timeField = TimeField.DIGEST })
                SwitchRow("Quiet hours", "Hold non-urgent alerts overnight", r.quietEnabled) { v -> rem { it.copy(quietEnabled = v) } }
                if (r.quietEnabled) {
                    ValueRow(Icons.Rounded.Bedtime, "Quiet from", Dates.formatTime(r.quietStart), { timeField = TimeField.QUIET_START })
                    ValueRow(Icons.Rounded.Alarm, "Quiet until", Dates.formatTime(r.quietEnd), { timeField = TimeField.QUIET_END })
                }
                ValueRow(Icons.Rounded.Notifications, "Check reminders now", "Run", { vm.testReminderNow(); scope.launch { snackbar.showSnackbar("Reminders refreshed") } })
            }

            Group("Smart defaults for new items") {
                Text("Remind before running out", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
                ChoiceChips(listOf(1, 3, 5, 7), settings.defaultReorderDays, { "$it days" }, { d -> vm.update { it.copy(defaultReorderDays = d) } }, Modifier.padding(horizontal = 12.dp))
                Text("Warn before expiry", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                ChoiceChips(listOf(1, 2, 3, 5), settings.defaultExpiryWarnDays, { "$it days" }, { d -> vm.update { it.copy(defaultExpiryWarnDays = d) } }, Modifier.padding(horizontal = 12.dp))
            }

            Group("Household") {
                Text("Add family members or roommates to assign chores.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.members.forEach { m ->
                        InputChip(selected = false, onClick = { vm.update { it.copy(members = it.members - m) } }, label = { Text("$m  ✕") }, shape = CircleShape)
                    }
                    InputChip(selected = false, onClick = { addMember = true }, label = { Text("+ Add member") }, shape = CircleShape)
                }
            }

            Group("Your data") {
                Text("Everything is stored privately on this phone and works offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                ValueRow(Icons.Rounded.FileUpload, "Export backup", "JSON", { exportLauncher.launch("lifeloop-backup-${Dates.formatFull(Dates.today()).replace(' ', '-')}.json") })
                ValueRow(Icons.Rounded.FileDownload, "Restore from backup", "Choose", { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                ValueRow(Icons.Rounded.Home, "Add example household", "Load", { confirmSample = true })
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LifeLoop 1.0", style = MaterialTheme.typography.titleSmall)
                Text("Everything in life, right on time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (editName) TextDialog("Your name", settings.userName, { editName = false }) { n -> vm.update { it.copy(userName = n.trim()) } }
    if (addMember) TextDialog("Member name", "", { addMember = false }) { n -> if (n.isNotBlank()) vm.update { it.copy(members = (it.members + n.trim()).distinct()) } }
    timeField?.let { f ->
        val init = when (f) { TimeField.MORNING -> r.morningMinutes; TimeField.QUIET_START -> r.quietStart; TimeField.QUIET_END -> r.quietEnd; TimeField.DIGEST -> r.digestMinutes }
        TimePickerSheet(init, { timeField = null }, { m ->
            when (f) {
                TimeField.MORNING -> rem { it.copy(morningMinutes = m) }
                TimeField.QUIET_START -> rem { it.copy(quietStart = m) }
                TimeField.QUIET_END -> rem { it.copy(quietEnd = m) }
                TimeField.DIGEST -> rem { it.copy(digestMinutes = m) }
            }
        })
    }
    confirmImport?.let { uri ->
        ConfirmDialog(
            "Restore backup?", "This replaces everything currently in LifeLoop with the backup's contents.", "Restore",
            onDismiss = { confirmImport = null },
            onConfirm = {
                scope.launch {
                    val result = runCatching {
                        val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("Empty file")
                        vm.importJson(json)
                    }
                    snackbar.showSnackbar(if (result.isSuccess) "Backup restored" else "Couldn't restore: ${result.exceptionOrNull()?.message ?: "invalid file"}")
                }
            },
            destructive = true,
        )
    }
    if (confirmSample) ConfirmDialog(
        "Add example household?", "Adds sample groceries, chores and habits alongside your data. You can delete them anytime.", "Add",
        { confirmSample = false }, { vm.loadSample(); scope.launch { snackbar.showSnackbar("Example household added") } },
    )
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        LoopCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun StatusRow(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠️ $text", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = LoopTheme.colors.warning)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

