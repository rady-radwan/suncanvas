package app.suncanvas.data.backup

import app.suncanvas.BuildConfig
import android.net.Uri
import app.suncanvas.AppContainer
import app.suncanvas.data.db.LocationEntity
import app.suncanvas.data.db.LocationSource
import app.suncanvas.data.db.ReminderEntity
import app.suncanvas.data.db.ReminderEvent
import app.suncanvas.data.db.WidgetDesignEntity
import app.suncanvas.widget.model.WidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val format: String = "dayarc-backup",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val locations: List<BackupLocation> = emptyList(),
    val designs: List<BackupDesign> = emptyList(),
    val widgets: List<BackupWidget> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
data class BackupLocation(
    val name: String, val customLabel: String? = null, val admin1: String = "", val countryCode: String = "",
    val countryName: String = "", val latitude: Double, val longitude: Double, val timeZoneId: String,
    val source: String, val geonameId: Long? = null, val isFavorite: Boolean = false, val isDefault: Boolean = false,
    val inCityList: Boolean = true, val sortOrder: Int = 0,
)

@Serializable
data class BackupDesign(val name: String, val spec: String)

/** Placed-widget designs are exported as designs (widget ids are device-specific). */
@Serializable
data class BackupWidget(val appWidgetId: Int, val spec: String)

@Serializable
data class BackupReminder(val event: String, val offsetMinutes: Int, val enabled: Boolean)

data class ImportSummary(val locations: Int, val designs: Int, val reminders: Int)

/**
 * Exports and imports saved cities, widget designs, reminders and preferences as a JSON file the
 * user picks with the system file picker (no account, nothing leaves the device unless they share it).
 */
class BackupManager(private val c: AppContainer) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val file = BackupFile(
            locations = c.locations.getAll().map {
                BackupLocation(it.name, it.customLabel, it.admin1, it.countryCode, it.countryName, it.latitude, it.longitude,
                    it.timeZoneId, it.source.name, it.geonameId, it.isFavorite, it.isDefault, it.inCityList, it.sortOrder)
            },
            designs = c.widgets.getDesigns().map { BackupDesign(it.name, it.specJson) },
            widgets = c.widgets.getInstances().map { BackupWidget(it.appWidgetId, it.specJson) },
            reminders = c.database.reminders().getAll().map { BackupReminder(it.event.name, it.offsetMinutes, it.enabled) },
            settings = c.settings.exportMap(),
        )
        c.appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(json.encodeToString(BackupFile.serializer(), file).toByteArray())
        } ?: error("Could not open file")
    }

    /** Merges a backup into the current data: existing cities (same coordinates) are not duplicated. */
    suspend fun import(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val text = c.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Could not open file")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format in setOf("dayarc-backup", "suncanvas-backup")) { "This is not a ${BuildConfig.BRAND_NAME} backup file" }
        val existing = c.locations.getAll()
        var addedLocations = 0
        file.locations.forEach { b ->
            val dup = existing.any { kotlin.math.abs(it.latitude - b.latitude) < 0.001 && kotlin.math.abs(it.longitude - b.longitude) < 0.001 }
            if (dup) return@forEach
            val source = runCatching { LocationSource.valueOf(b.source) }.getOrDefault(LocationSource.MANUAL)
            c.database.locations().insert(
                LocationEntity(
                    name = b.name, customLabel = b.customLabel, admin1 = b.admin1, countryCode = b.countryCode,
                    countryName = b.countryName, latitude = b.latitude, longitude = b.longitude, timeZoneId = b.timeZoneId,
                    source = if (source == LocationSource.DEVICE) LocationSource.MANUAL else source,
                    geonameId = b.geonameId, isFavorite = b.isFavorite, isDefault = false, inCityList = b.inCityList,
                    sortOrder = b.sortOrder + 1000,
                )
            )
            addedLocations++
        }
        if (c.locations.getAll().none { it.isDefault }) c.locations.getAll().firstOrNull()?.let { c.locations.setDefault(it.id) }
        var designs = 0
        (file.designs.map { it.name to it.spec } + file.widgets.map { "Widget ${it.appWidgetId}" to it.spec }).forEach { (name, specJson) ->
            val spec = WidgetSpec.fromJson(specJson) ?: return@forEach
            c.database.widgets().insertDesign(WidgetDesignEntity(name = spec.name.ifBlank { name }, specJson = spec.copy(locationId = null, extraLocationIds = emptyList()).toJson()))
            designs++
        }
        var reminders = 0
        file.reminders.forEach { r ->
            val ev = runCatching { ReminderEvent.valueOf(r.event) }.getOrNull() ?: return@forEach
            c.database.reminders().insert(ReminderEntity(event = ev, offsetMinutes = r.offsetMinutes, enabled = r.enabled))
            reminders++
        }
        c.settings.importMap(file.settings)
        ImportSummary(addedLocations, designs, reminders)
    }
}
