package app.suncanvas.location

import app.suncanvas.data.cities.City
import app.suncanvas.data.cities.CityDatabase
import java.time.ZoneId
import kotlin.math.roundToInt

data class ResolvedPlace(
    val name: String,
    val admin1: String,
    val countryCode: String,
    val countryName: String,
    val timeZoneId: String,
    val nearestCity: City?,
    val distanceKm: Double?,
    /** True when the zone came from a populated place; false for open ocean / remote fallbacks. */
    val confident: Boolean,
)

/**
 * Resolves an IANA time zone and a human-readable place name for arbitrary coordinates, fully
 * offline, from the bundled GeoNames city database (each city carries its IANA zone). Over open
 * ocean or far from any town it falls back to the nautical zone (Etc/GMT±n).
 *
 * Accuracy note: within a few km of a time-zone border the nearest town may lie in the other zone;
 * the coordinate entry screen lets the user override the zone in that case.
 */
class TimeZoneResolver(private val cities: CityDatabase) {

    suspend fun resolve(latitude: Double, longitude: Double): ResolvedPlace {
        val near = cities.nearest(latitude, longitude)
        if (near != null && near.second <= MAX_CITY_DISTANCE_KM) {
            val (city, dist) = near
            val name = if (dist <= 25) city.name else "Near ${city.name}"
            return ResolvedPlace(name, city.admin1, city.countryCode, city.countryName, city.timeZoneId, city, dist, true)
        }
        return ResolvedPlace(
            name = "%.2f, %.2f".format(java.util.Locale.US, latitude, longitude),
            admin1 = "",
            countryCode = near?.first?.countryCode.orEmpty(),
            countryName = near?.first?.countryName.orEmpty(),
            timeZoneId = nauticalZone(longitude),
            nearestCity = near?.first,
            distanceKm = near?.second,
            confident = false,
        )
    }

    companion object {
        const val MAX_CITY_DISTANCE_KM = 400.0

        /** Etc/GMT zones have inverted signs: Etc/GMT-3 is UTC+3. */
        fun nauticalZone(longitude: Double): String {
            val offset = (longitude / 15.0).roundToInt().coerceIn(-12, 12)
            val id = when {
                offset == 0 -> "Etc/GMT"
                offset > 0 -> "Etc/GMT-$offset"
                else -> "Etc/GMT+${-offset}"
            }
            return runCatching { ZoneId.of(id).id }.getOrDefault("UTC")
        }
    }
}
