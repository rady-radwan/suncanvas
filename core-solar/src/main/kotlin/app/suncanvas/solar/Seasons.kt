package app.suncanvas.solar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Equinoxes and solstices: the instants at which the Sun's apparent ecliptic longitude reaches
 * 0°, 90°, 180° and 270°. Found by bisection on the NOAA apparent longitude (accurate to a few
 * minutes, which is ample for display).
 */
object Seasons {

    fun eventsForYear(year: Int): List<SeasonEvent> = listOf(
        find(year, 3, 20, 0.0, SeasonEventType.MARCH_EQUINOX),
        find(year, 6, 21, 90.0, SeasonEventType.JUNE_SOLSTICE),
        find(year, 9, 22, 180.0, SeasonEventType.SEPTEMBER_EQUINOX),
        find(year, 12, 21, 270.0, SeasonEventType.DECEMBER_SOLSTICE),
    )

    /** The next [count] season events at or after [from]. */
    fun upcoming(from: Instant, count: Int = 4): List<SeasonEvent> {
        val year = from.atZone(ZoneOffset.UTC).year
        return (eventsForYear(year) + eventsForYear(year + 1) + eventsForYear(year + 2))
            .filter { !it.instant.isBefore(from) }
            .take(count)
    }

    private fun find(year: Int, month: Int, day: Int, target: Double, type: SeasonEventType): SeasonEvent {
        val guess = LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val hour = 3_600_000L
        var lo = guess - 6 * 24 * hour
        var hi = guess + 6 * 24 * hour
        // f(t) = signed angular distance of apparent longitude from the target, increasing with time.
        fun f(t: Long): Double {
            var d = NoaaSolarPosition.ephemeris(t).apparentLongitude - target
            while (d > 180) d -= 360
            while (d <= -180) d += 360
            return d
        }
        while (hi - lo > 1000) {
            val mid = (lo + hi) / 2
            if (f(mid) >= 0) hi = mid else lo = mid
        }
        return SeasonEvent(type, Instant.ofEpochMilli((lo + hi) / 2))
    }
}
