package app.suncanvas.solar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Altitude thresholds (degrees, geometric elevation of the Sun's centre). */
object SolarAltitude {
    /** Official sunrise/sunset: upper limb on the horizon with standard refraction. */
    const val HORIZON = NoaaSolarPosition.SUNRISE_ALTITUDE
    const val CIVIL = -6.0
    const val NAUTICAL = -12.0
    const val ASTRONOMICAL = -18.0

    /** Photography convention (not an astronomical definition): golden hour between +6° and −4°. */
    const val GOLDEN_UPPER = 6.0
    const val GOLDEN_LOWER = -4.0

    /** Photography convention: blue hour between −4° and −6°. */
    const val BLUE_UPPER = -4.0
    const val BLUE_LOWER = -6.0
}

enum class DayType {
    /** The Sun rises and/or sets on this local date. */
    NORMAL,

    /** The Sun stays above the horizon for the whole local date (midnight sun). */
    POLAR_DAY,

    /** The Sun stays below the horizon for the whole local date. */
    POLAR_NIGHT,
}

/**
 * A time window inside a local day. When [startClipped] is true the window actually began
 * before local midnight; when [endClipped] is true it continues past the next local midnight.
 */
data class TimeWindow(
    val start: Instant,
    val end: Instant,
    val startClipped: Boolean = false,
    val endClipped: Boolean = false,
) {
    val duration: Duration get() = Duration.between(start, end)
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

enum class SolarEventType(val rising: Boolean?) {
    ASTRONOMICAL_DAWN(true),
    NAUTICAL_DAWN(true),
    CIVIL_DAWN(true),
    SUNRISE(true),
    GOLDEN_HOUR_MORNING_END(true),
    SOLAR_NOON(null),
    GOLDEN_HOUR_EVENING_START(false),
    SUNSET(false),
    CIVIL_DUSK(false),
    NAUTICAL_DUSK(false),
    ASTRONOMICAL_DUSK(false),
    SOLAR_MIDNIGHT(null),
}

data class SolarEvent(val type: SolarEventType, val instant: Instant)

data class SolarDay(
    val date: LocalDate,
    val zone: ZoneId,
    val latitude: Double,
    val longitude: Double,
    val dayType: DayType,
    val sunrise: Instant?,
    val sunset: Instant?,
    val solarNoon: Instant?,
    /** Apparent (refracted) elevation at solar noon, degrees. */
    val noonElevation: Double,
    val solarMidnight: Instant?,
    val civilDawn: Instant?,
    val civilDusk: Instant?,
    val nauticalDawn: Instant?,
    val nauticalDusk: Instant?,
    val astronomicalDawn: Instant?,
    val astronomicalDusk: Instant?,
    /** Total time with the Sun above the horizon on this local date. */
    val daylight: Duration,
    val goldenHourMorning: TimeWindow?,
    val goldenHourEvening: TimeWindow?,
    val blueHourMorning: TimeWindow?,
    val blueHourEvening: TimeWindow?,
    /** Length of this local date (23 h / 25 h on DST transition days). */
    val dayLength: Duration,
) {
    /** Chronological list of all events that occur on this date. */
    val events: List<SolarEvent>
        get() = buildList {
            astronomicalDawn?.let { add(SolarEvent(SolarEventType.ASTRONOMICAL_DAWN, it)) }
            nauticalDawn?.let { add(SolarEvent(SolarEventType.NAUTICAL_DAWN, it)) }
            civilDawn?.let { add(SolarEvent(SolarEventType.CIVIL_DAWN, it)) }
            sunrise?.let { add(SolarEvent(SolarEventType.SUNRISE, it)) }
            goldenHourMorning?.takeIf { !it.endClipped }?.let { add(SolarEvent(SolarEventType.GOLDEN_HOUR_MORNING_END, it.end)) }
            solarNoon?.let { add(SolarEvent(SolarEventType.SOLAR_NOON, it)) }
            goldenHourEvening?.takeIf { !it.startClipped }?.let { add(SolarEvent(SolarEventType.GOLDEN_HOUR_EVENING_START, it.start)) }
            sunset?.let { add(SolarEvent(SolarEventType.SUNSET, it)) }
            civilDusk?.let { add(SolarEvent(SolarEventType.CIVIL_DUSK, it)) }
            nauticalDusk?.let { add(SolarEvent(SolarEventType.NAUTICAL_DUSK, it)) }
            astronomicalDusk?.let { add(SolarEvent(SolarEventType.ASTRONOMICAL_DUSK, it)) }
            solarMidnight?.let { add(SolarEvent(SolarEventType.SOLAR_MIDNIGHT, it)) }
        }.sortedBy { it.instant }
}

enum class SeasonEventType { MARCH_EQUINOX, JUNE_SOLSTICE, SEPTEMBER_EQUINOX, DECEMBER_SOLSTICE }

data class SeasonEvent(val type: SeasonEventType, val instant: Instant)

/** The next sunrise and sunset after a given moment (may be months away in polar regions). */
data class NextSunEvents(
    val nextSunrise: Instant?,
    val nextSunset: Instant?,
) {
    val nextIsSunrise: Boolean?
        get() = when {
            nextSunrise == null && nextSunset == null -> null
            nextSunrise == null -> false
            nextSunset == null -> true
            else -> nextSunrise.isBefore(nextSunset)
        }
}
