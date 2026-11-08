package app.suncanvas.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.suncanvas.container
import app.suncanvas.core.DateStyle
import app.suncanvas.core.TimeFormatter
import app.suncanvas.data.AppSettings
import app.suncanvas.data.ClockStyle
import app.suncanvas.data.ThemeMode
import app.suncanvas.data.TimeFormatPref
import app.suncanvas.data.cities.City
import app.suncanvas.data.db.LocationEntity
import app.suncanvas.location.LocationResult
import app.suncanvas.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** App-wide state shared by every screen: settings, saved locations and the active location. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val locations: StateFlow<List<LocationEntity>> = c.locations.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val active: StateFlow<LocationEntity?> = c.locations.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _locating = MutableStateFlow(false)
    val locating = _locating.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages = _messages.asStateFlow()

    val solar get() = c.solar

    fun formatter(): TimeFormatter = (settings.value ?: AppSettings()).formatter(getApplication())

    fun consumeMessage() { _messages.value = null }
    fun message(text: String) { _messages.value = text }

    /** One-shot location request. Returns the result so screens can react (e.g. permission denied). */
    fun refreshLocation(onResult: (LocationResult) -> Unit = {}) {
        if (_locating.value) return
        _locating.value = true
        viewModelScope.launch {
            val (result, entity) = runCatching { c.locations.refreshDeviceLocation() }.getOrElse { LocationResult.Unavailable to null }
            _locating.value = false
            when (result) {
                is LocationResult.Success -> {
                    message("Location updated: ${entity?.headline ?: "current position"}")
                    afterLocationsChanged()
                }
                LocationResult.PermissionDenied -> message("Location permission is off — showing your saved location")
                LocationResult.Disabled -> message("Location services are turned off")
                LocationResult.Unavailable -> message("Couldn't get a location right now — showing your saved location")
            }
            onResult(result)
        }
    }

    fun addCity(city: City, makeActive: Boolean = false, makeDefault: Boolean = false, onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = c.locations.addCity(city, makeDefault = makeDefault, makeActive = makeActive)
            afterLocationsChanged()
            onAdded(id)
        }
    }

    fun addManual(name: String, lat: Double, lon: Double, zone: String, admin1: String, cc: String, country: String, makeActive: Boolean, onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = c.locations.addManual(name, lat, lon, zone, admin1, cc, country, makeActive = makeActive)
            afterLocationsChanged()
            onAdded(id)
        }
    }

    fun setActive(id: Long) = viewModelScope.launch { c.locations.setActive(id) }
    fun setDefault(id: Long) = viewModelScope.launch { c.locations.setDefault(id); afterLocationsChanged() }
    fun setFavorite(id: Long, fav: Boolean) = viewModelScope.launch { c.locations.setFavorite(id, fav) }
    fun rename(id: Long, label: String?) = viewModelScope.launch { c.locations.rename(id, label); afterLocationsChanged() }
    fun delete(id: Long) = viewModelScope.launch { c.locations.delete(id); afterLocationsChanged() }
    fun reorder(ids: List<Long>) = viewModelScope.launch { c.locations.reorder(ids) }
    fun updateLocation(entity: LocationEntity) = viewModelScope.launch { c.locations.update(entity); afterLocationsChanged() }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setTimeFormat(f: TimeFormatPref) = viewModelScope.launch { c.settings.setTimeFormat(f); WidgetUpdater.requestUpdateAll(getApplication()) }
    fun setDateStyle(d: DateStyle) = viewModelScope.launch { c.settings.setDateStyle(d); WidgetUpdater.requestUpdateAll(getApplication()) }
    fun setOnlineSearch(on: Boolean) = viewModelScope.launch { c.settings.setOnlineSearch(on) }
    fun setWorldClockStyle(s: ClockStyle) = viewModelScope.launch { c.settings.setWorldClockStyle(s) }
    fun setWorldClockHidden(ids: Set<Long>) = viewModelScope.launch { c.settings.setWorldClockHidden(ids) }
    fun setShowSeconds(on: Boolean) = viewModelScope.launch { c.settings.setShowSeconds(on) }
    fun setWidgetRefresh(minutes: Int) = viewModelScope.launch {
        c.settings.setWidgetRefreshMinutes(minutes)
        app.suncanvas.widget.WidgetScheduler.ensureScheduled(getApplication())
    }
    fun finishOnboarding() = viewModelScope.launch { c.settings.setOnboardingDone(true) }

    private suspend fun afterLocationsChanged() {
        WidgetUpdater.requestUpdateAll(getApplication())
        runCatching { c.reminderScheduler.rescheduleAll() }
    }
}

/** The current instant, ticking every [periodMillis] aligned to the period boundary. */
@Composable
fun rememberNow(periodMillis: Long = 1000L): Instant {
    val now by produceState(initialValue = Instant.now(), periodMillis) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(periodMillis - t % periodMillis + 5)
            value = Instant.now()
        }
    }
    return now
}
