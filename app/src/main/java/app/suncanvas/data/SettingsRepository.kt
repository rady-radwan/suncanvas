package app.suncanvas.data

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.suncanvas.core.DateStyle
import app.suncanvas.core.TimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    SUN("Automatic — sunrise & sunset"),
}

enum class TimeFormatPref(val label: String) { SYSTEM("Follow system"), H12("12-hour"), H24("24-hour") }

enum class ClockStyle { DIGITAL, ANALOG }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val timeFormat: TimeFormatPref = TimeFormatPref.SYSTEM,
    val dateStyle: DateStyle = DateStyle.MEDIUM,
    val activeLocationId: Long? = null,
    val onboardingDone: Boolean = false,
    val onlineSearch: Boolean = true,
    val worldClockStyle: ClockStyle = ClockStyle.DIGITAL,
    /** Ids hidden from the world clock (empty = all cities shown). */
    val worldClockHidden: Set<Long> = emptySet(),
    val notificationsEnabled: Boolean = false,
    val widgetRefreshMinutes: Int = 30,
    val showSeconds: Boolean = false,
) {
    fun use24h(context: Context): Boolean = when (timeFormat) {
        TimeFormatPref.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormatPref.H12 -> false
        TimeFormatPref.H24 -> true
    }

    fun formatter(context: Context) = TimeFormatter(use24h(context), dateStyle)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val timeFormat = stringPreferencesKey("time_format")
        val dateStyle = stringPreferencesKey("date_style")
        val activeLocation = longPreferencesKey("active_location")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val onlineSearch = booleanPreferencesKey("online_search")
        val clockStyle = stringPreferencesKey("world_clock_style")
        val worldHidden = stringPreferencesKey("world_clock_hidden")
        val notifications = booleanPreferencesKey("notifications")
        val widgetRefresh = intPreferencesKey("widget_refresh_minutes")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val pendingPinSpec = stringPreferencesKey("pending_pin_spec")
        val pendingPinAt = longPreferencesKey("pending_pin_at")
        val pendingPinDesign = longPreferencesKey("pending_pin_design")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme].enumOr(ThemeMode.SYSTEM),
            timeFormat = p[Keys.timeFormat].enumOr(TimeFormatPref.SYSTEM),
            dateStyle = p[Keys.dateStyle].enumOr(DateStyle.MEDIUM),
            activeLocationId = p[Keys.activeLocation],
            onboardingDone = p[Keys.onboarding] ?: false,
            onlineSearch = p[Keys.onlineSearch] ?: true,
            worldClockStyle = p[Keys.clockStyle].enumOr(ClockStyle.DIGITAL),
            worldClockHidden = p[Keys.worldHidden]?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
            notificationsEnabled = p[Keys.notifications] ?: false,
            widgetRefreshMinutes = p[Keys.widgetRefresh] ?: 30,
            showSeconds = p[Keys.showSeconds] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setTimeFormat(f: TimeFormatPref) = context.dataStore.edit { it[Keys.timeFormat] = f.name }
    suspend fun setDateStyle(s: DateStyle) = context.dataStore.edit { it[Keys.dateStyle] = s.name }
    suspend fun setActiveLocation(id: Long?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.activeLocation) else it[Keys.activeLocation] = id
    }
    suspend fun setOnboardingDone(done: Boolean = true) = context.dataStore.edit { it[Keys.onboarding] = done }
    suspend fun setOnlineSearch(on: Boolean) = context.dataStore.edit { it[Keys.onlineSearch] = on }
    suspend fun setWorldClockStyle(s: ClockStyle) = context.dataStore.edit { it[Keys.clockStyle] = s.name }
    suspend fun setWorldClockHidden(ids: Set<Long>) = context.dataStore.edit { it[Keys.worldHidden] = ids.joinToString(",") }
    suspend fun setNotificationsEnabled(on: Boolean) = context.dataStore.edit { it[Keys.notifications] = on }
    suspend fun setWidgetRefreshMinutes(m: Int) = context.dataStore.edit { it[Keys.widgetRefresh] = m.coerceIn(15, 120) }
    suspend fun setShowSeconds(on: Boolean) = context.dataStore.edit { it[Keys.showSeconds] = on }

    /** Remembers a design the user asked to pin so the new widget can adopt it even before the callback arrives. */
    suspend fun setPendingPin(specJson: String, designId: Long?) = context.dataStore.edit {
        it[Keys.pendingPinSpec] = specJson
        it[Keys.pendingPinAt] = System.currentTimeMillis()
        if (designId != null) it[Keys.pendingPinDesign] = designId else it.remove(Keys.pendingPinDesign)
    }

    /** Returns and clears a pending pin request if it is recent (2 minutes). */
    suspend fun takePendingPin(): Pair<String, Long?>? {
        var result: Pair<String, Long?>? = null
        context.dataStore.edit {
            val spec = it[Keys.pendingPinSpec]
            val at = it[Keys.pendingPinAt] ?: 0L
            if (spec != null && System.currentTimeMillis() - at < 120_000) result = spec to it[Keys.pendingPinDesign]
            it.remove(Keys.pendingPinSpec); it.remove(Keys.pendingPinAt); it.remove(Keys.pendingPinDesign)
        }
        return result
    }

    /** Raw export/import for backups. */
    suspend fun exportMap(): Map<String, String> {
        val s = current()
        return mapOf(
            "theme" to s.themeMode.name,
            "timeFormat" to s.timeFormat.name,
            "dateStyle" to s.dateStyle.name,
            "onlineSearch" to s.onlineSearch.toString(),
            "worldClockStyle" to s.worldClockStyle.name,
            "widgetRefreshMinutes" to s.widgetRefreshMinutes.toString(),
            "showSeconds" to s.showSeconds.toString(),
        )
    }

    suspend fun importMap(map: Map<String, String>) {
        context.dataStore.edit { p ->
            map["theme"]?.let { p[Keys.theme] = it }
            map["timeFormat"]?.let { p[Keys.timeFormat] = it }
            map["dateStyle"]?.let { p[Keys.dateStyle] = it }
            map["onlineSearch"]?.toBooleanStrictOrNull()?.let { p[Keys.onlineSearch] = it }
            map["worldClockStyle"]?.let { p[Keys.clockStyle] = it }
            map["widgetRefreshMinutes"]?.toIntOrNull()?.let { p[Keys.widgetRefresh] = it }
            map["showSeconds"]?.toBooleanStrictOrNull()?.let { p[Keys.showSeconds] = it }
        }
    }
}

private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
