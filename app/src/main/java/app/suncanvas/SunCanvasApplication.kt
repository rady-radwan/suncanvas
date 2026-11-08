package app.suncanvas

import android.app.Application
import android.content.Context
import app.suncanvas.data.LocationRepository
import app.suncanvas.data.SettingsRepository
import app.suncanvas.data.SolarRepository
import app.suncanvas.data.WidgetRepository
import app.suncanvas.data.backup.BackupManager
import app.suncanvas.data.cities.CityDatabase
import app.suncanvas.data.cities.OnlineGeocoder
import app.suncanvas.data.db.AppDatabase
import app.suncanvas.location.DeviceLocationProvider
import app.suncanvas.location.TimeZoneResolver
import app.suncanvas.notify.ReminderNotifications
import app.suncanvas.notify.ReminderScheduler
import app.suncanvas.widget.WidgetDataLoader
import app.suncanvas.widget.render.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. Small and explicit: no annotation processing beyond Room,
 * which keeps builds fast and the object graph easy to follow.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings by lazy { SettingsRepository(appContext) }
    val cities by lazy { CityDatabase(appContext) }
    val geocoder by lazy { OnlineGeocoder() }
    val deviceLocation by lazy { DeviceLocationProvider(appContext) }
    val timeZoneResolver by lazy { TimeZoneResolver(cities) }
    val locations by lazy { LocationRepository(database.locations(), settings, deviceLocation, timeZoneResolver) }
    val solar by lazy { SolarRepository(database.solarCache()) }
    val widgets by lazy { WidgetRepository(database.widgets(), settings) }
    val renderer by lazy { WidgetRenderer(appContext) }
    val widgetLoader by lazy { WidgetDataLoader(appContext, this) }
    val reminderScheduler by lazy { ReminderScheduler(appContext, this) }
    val backup by lazy { BackupManager(this) }
}

class SunCanvasApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifications.createChannels(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as SunCanvasApplication).container
