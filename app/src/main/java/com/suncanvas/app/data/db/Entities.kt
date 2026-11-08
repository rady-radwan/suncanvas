package com.suncanvas.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LocationSource { DEVICE, CITY, ONLINE, MANUAL }

/**
 * A place the user follows. The phone's own position is stored as a single row with
 * source = DEVICE that is updated in place whenever the user refreshes location.
 */
@Entity(tableName = "locations", indices = [Index("sortOrder"), Index("source")])
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Optional custom label such as "Home" or "Parents' City". */
    val customLabel: String? = null,
    val admin1: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val latitude: Double,
    val longitude: Double,
    /** IANA time-zone id resolved from the coordinates (never assumed to be the phone's zone). */
    val timeZoneId: String,
    val source: LocationSource,
    val geonameId: Long? = null,
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    /** Shown on the My Cities list (the device row can be hidden from it). */
    val inCityList: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayName: String get() = customLabel?.takeIf { it.isNotBlank() } ?: name

    /** "Dallas, Texas" for US cities, otherwise "Cairo, Egypt". */
    val subtitle: String
        get() = when {
            countryCode == "US" && admin1.isNotBlank() -> "$admin1, ${countryName.ifBlank { "USA" }}"
            admin1.isNotBlank() && countryName.isNotBlank() -> "$admin1, $countryName"
            countryName.isNotBlank() -> countryName
            else -> ""
        }

    val headline: String
        get() = when {
            !customLabel.isNullOrBlank() -> customLabel.orEmpty()
            countryCode == "US" && admin1.isNotBlank() -> "$name, $admin1"
            countryName.isNotBlank() -> "$name, $countryName"
            else -> name
        }
}

/** Cached computed solar day. Payload is a JSON-serialised [com.suncanvas.app.data.SolarDayDto]. */
@Entity(tableName = "solar_cache", indices = [Index("computedAt")])
data class SolarCacheEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val computedAt: Long,
)

/** A saved design in Widget Studio (not attached to a home-screen widget). */
@Entity(tableName = "widget_designs")
data class WidgetDesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** JSON-serialised [com.suncanvas.app.widget.model.WidgetSpec]. */
    val specJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * The configuration of one placed home-screen widget. Each widget owns its own copy of the spec,
 * so editing one widget never changes another.
 */
@Entity(tableName = "widget_instances")
data class WidgetInstanceEntity(
    @PrimaryKey val appWidgetId: Int,
    val specJson: String,
    /** Design this instance was created from (informational; the spec is an independent copy). */
    val sourceDesignId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class ReminderEvent(val label: String) {
    SUNRISE("Sunrise"),
    SUNSET("Sunset"),
    GOLDEN_MORNING_START("Morning golden hour starts"),
    GOLDEN_MORNING_END("Morning golden hour ends"),
    GOLDEN_EVENING_START("Evening golden hour starts"),
    GOLDEN_EVENING_END("Evening golden hour ends"),
    BLUE_EVENING_START("Evening blue hour starts"),
    CIVIL_DAWN("Civil dawn"),
    SOLAR_NOON("Solar noon"),
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val event: ReminderEvent,
    /** Minutes relative to the event: negative = before, 0 = at the event. */
    val offsetMinutes: Int,
    val enabled: Boolean = true,
    /** Null = follow the app's default location. */
    val locationId: Long? = null,
)
