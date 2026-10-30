package com.example.impulse.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.impulse.domain.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val onboarded: Boolean = false,
    val userName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val currency: String = "₹",
    val members: List<String> = emptyList(),
    val defaultReorderDays: Int = 5,
    val defaultExpiryWarnDays: Int = 2,
    val reminders: ReminderSettings = ReminderSettings(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lifeloop_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val userName = stringPreferencesKey("user_name")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val currency = stringPreferencesKey("currency")
        val members = stringPreferencesKey("members")
        val reorder = intPreferencesKey("default_reorder_days")
        val expiryWarn = intPreferencesKey("default_expiry_warn")
        val morning = intPreferencesKey("morning_minutes")
        val quietOn = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val nInv = booleanPreferencesKey("notify_inventory")
        val nExp = booleanPreferencesKey("notify_expiry")
        val nTask = booleanPreferencesKey("notify_tasks")
        val nHabit = booleanPreferencesKey("notify_habits")
        val digest = booleanPreferencesKey("digest_enabled")
        val digestAt = intPreferencesKey("digest_minutes")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        val d = ReminderSettings()
        UserSettings(
            onboarded = p[K.onboarded] ?: false,
            userName = p[K.userName] ?: "",
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[K.dynamic] ?: false,
            currency = p[K.currency] ?: "₹",
            members = p[K.members]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
            defaultReorderDays = p[K.reorder] ?: 5,
            defaultExpiryWarnDays = p[K.expiryWarn] ?: 2,
            reminders = ReminderSettings(
                morningMinutes = p[K.morning] ?: d.morningMinutes,
                quietEnabled = p[K.quietOn] ?: d.quietEnabled,
                quietStart = p[K.quietStart] ?: d.quietStart,
                quietEnd = p[K.quietEnd] ?: d.quietEnd,
                inventoryEnabled = p[K.nInv] ?: true,
                expiryEnabled = p[K.nExp] ?: true,
                tasksEnabled = p[K.nTask] ?: true,
                habitsEnabled = p[K.nHabit] ?: true,
                digestEnabled = p[K.digest] ?: d.digestEnabled,
                digestMinutes = p[K.digestAt] ?: d.digestMinutes,
            ),
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(current())
        context.dataStore.edit { p ->
            p[K.onboarded] = next.onboarded
            p[K.userName] = next.userName
            p[K.theme] = next.themeMode.name
            p[K.dynamic] = next.dynamicColor
            p[K.currency] = next.currency
            p[K.members] = next.members.joinToString("\n")
            p[K.reorder] = next.defaultReorderDays
            p[K.expiryWarn] = next.defaultExpiryWarnDays
            val r = next.reminders
            p[K.morning] = r.morningMinutes
            p[K.quietOn] = r.quietEnabled
            p[K.quietStart] = r.quietStart
            p[K.quietEnd] = r.quietEnd
            p[K.nInv] = r.inventoryEnabled
            p[K.nExp] = r.expiryEnabled
            p[K.nTask] = r.tasksEnabled
            p[K.nHabit] = r.habitsEnabled
            p[K.digest] = r.digestEnabled
            p[K.digestAt] = r.digestMinutes
        }
    }
}
