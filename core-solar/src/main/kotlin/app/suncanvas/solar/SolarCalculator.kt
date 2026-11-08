package app.suncanvas.solar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Computes every daily solar event for a **local calendar date in the location's own time zone**.
 *
 * Strategy: the geometric solar elevation is sampled across the local day (which may be 23 h or
 * 25 h long on DST transition days) and every threshold crossing is refined by bisection to
 * well under one second. This is robust for polar day/night, days with a missing sunrise or
 * sunset, the International Date Line and DST, because events are found inside the real local
 * day instead of being derived from a UTC-day formula.
 */
class SolarCalculator(private val stepMinutes: Int = 5) {

    private val stepMillis = stepMinutes * 60_000L

    fun position(instant: Instant, latitude: Double, longitude: Double): SunPosition =
        NoaaSolarPosition.position(instant.toEpochMilli(), latitude, longitude)

    fun day(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SolarDay {
        val lat = latitude.coerceIn(-89.9999, 89.9999)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sampler = DaySampler(start, end, lat, longitude)

        val horizon = sampler.crossings(SolarAltitude.HORIZON)
        val civil = sampler.crossings(SolarAltitude.CIVIL)
        val nautical = sampler.crossings(SolarAltitude.NAUTICAL)
        val astro = sampler.crossings(SolarAltitude.ASTRONOMICAL)

        val sunrise = horizon.firstOrNull { it.rising }?.time
        val sunset = horizon.lastOrNull { !it.rising }?.time

        val dayType = when {
            horizon.isNotEmpty() -> DayType.NORMAL
            sampler.elevations.all { it > SolarAltitude.HORIZON } -> DayType.POLAR_DAY
            else -> DayType.POLAR_NIGHT
        }

        val noon = sampler.hourAngleZero(shifted = false)
        val midnight = sampler.hourAngleZero(shifted = true)
        val noonInstantMillis = noon ?: (start + end) / 2
        val noonElevation = NoaaSolarPosition.position(noonInstantMillis, lat, longitude).elevation

        val daylightMillis = sampler.bands(SolarAltitude.HORIZON, Double.POSITIVE_INFINITY).sumOf { it.second - it.first }

        val golden = sampler.bands(SolarAltitude.GOLDEN_LOWER, SolarAltitude.GOLDEN_UPPER)
        val blue = sampler.bands(SolarAltitude.BLUE_LOWER, SolarAltitude.BLUE_UPPER)

        fun pick(bands: List<Pair<Long, Long>>, morning: Boolean): TimeWindow? {
            val candidates = bands.filter { (a, b) ->
                val mid = (a + b) / 2
                if (morning) mid <= noonInstantMillis else mid > noonInstantMillis
            }
            val chosen = (if (morning) candidates.firstOrNull() else candidates.lastOrNull()) ?: return null
            return TimeWindow(
                start = Instant.ofEpochMilli(chosen.first),
                end = Instant.ofEpochMilli(chosen.second),
                startClipped = chosen.first <= start,
                endClipped = chosen.second >= end,
            )
        }

        return SolarDay(
            date = date,
            zone = zone,
            latitude = latitude,
            longitude = longitude,
            dayType = dayType,
            sunrise = sunrise?.let(Instant::ofEpochMilli),
            sunset = sunset?.let(Instant::ofEpochMilli),
            solarNoon = noon?.let(Instant::ofEpochMilli),
            noonElevation = noonElevation,
            solarMidnight = midnight?.let(Instant::ofEpochMilli),
            civilDawn = civil.firstOrNull { it.rising }?.time?.let(Instant::ofEpochMilli),
            civilDusk = civil.lastOrNull { !it.rising }?.time?.let(Instant::ofEpochMilli),
            nauticalDawn = nautical.firstOrNull { it.rising }?.time?.let(Instant::ofEpochMilli),
            nauticalDusk = nautical.lastOrNull { !it.rising }?.time?.let(Instant::ofEpochMilli),
            astronomicalDawn = astro.firstOrNull { it.rising }?.time?.let(Instant::ofEpochMilli),
            astronomicalDusk = astro.lastOrNull { !it.rising }?.time?.let(Instant::ofEpochMilli),
            daylight = Duration.ofMillis(daylightMillis),
            goldenHourMorning = pick(golden, morning = true),
            goldenHourEvening = pick(golden, morning = false),
            blueHourMorning = pick(blue, morning = true),
            blueHourEvening = pick(blue, morning = false),
            dayLength = Duration.ofMillis(end - start),
        )
    }

    /**
     * Elevation samples across a local day, for charts and arcs.
     * Returns pairs of (instant, apparent elevation in degrees).
     */
    fun elevationCurve(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        points: Int = 97,
    ): List<Pair<Instant, Double>> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val n = points.coerceAtLeast(2)
        return (0 until n).map { i ->
            val t = start + (end - start) * i / (n - 1)
            Instant.ofEpochMilli(t) to NoaaSolarPosition.position(t, latitude, longitude).elevation
        }
    }

    /** Next sunrise and next sunset strictly after [from], searching up to [maxDays] days ahead. */
    fun nextSunEvents(
        from: Instant,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        maxDays: Int = 370,
    ): NextSunEvents {
        var rise: Instant? = null
        var set: Instant? = null
        var date = ZonedDateTime.ofInstant(from, zone).toLocalDate()
        var i = 0
        while (i < maxDays && (rise == null || set == null)) {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val sampler = DaySampler(start, end, latitude.coerceIn(-89.9999, 89.9999), longitude)
            for (c in sampler.crossings(SolarAltitude.HORIZON)) {
                if (c.time <= from.toEpochMilli()) continue
                if (c.rising && rise == null) rise = Instant.ofEpochMilli(c.time)
                if (!c.rising && set == null) set = Instant.ofEpochMilli(c.time)
            }
            date = date.plusDays(1)
            i++
        }
        return NextSunEvents(rise, set)
    }

    /** The next event of any type strictly after [from] (searching today and the next few days). */
    fun nextEvent(
        from: Instant,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        types: Set<SolarEventType> = setOf(SolarEventType.SUNRISE, SolarEventType.SUNSET),
        maxDays: Int = 3,
    ): SolarEvent? {
        var date = ZonedDateTime.ofInstant(from, zone).toLocalDate()
        repeat(maxDays) {
            val e = day(date, latitude, longitude, zone).events.firstOrNull { it.type in types && it.instant.isAfter(from) }
            if (e != null) return e
            date = date.plusDays(1)
        }
        return null
    }

    private data class Crossing(val time: Long, val rising: Boolean)

    private inner class DaySampler(
        private val start: Long,
        private val end: Long,
        private val lat: Double,
        private val lon: Double,
    ) {
        val times: LongArray
        val elevations: DoubleArray

        init {
            val n = (((end - start) + stepMillis - 1) / stepMillis).toInt() + 1
            times = LongArray(n) { i -> minOf(start + i * stepMillis, end) }
            elevations = DoubleArray(n) { i -> elev(times[i]) }
        }

        private fun elev(t: Long) = NoaaSolarPosition.geometricElevation(t, lat, lon)

        fun crossings(h: Double): List<Crossing> {
            val out = ArrayList<Crossing>(4)
            for (i in 0 until times.size - 1) {
                val a = elevations[i] - h
                val b = elevations[i + 1] - h
                if (times[i + 1] == times[i]) continue
                if (a < 0 && b >= 0) out += Crossing(refine(times[i], times[i + 1], h, rising = true), true)
                else if (a >= 0 && b < 0) out += Crossing(refine(times[i], times[i + 1], h, rising = false), false)
            }
            return out
        }

        private fun refine(t0: Long, t1: Long, h: Double, rising: Boolean): Long {
            var lo = t0
            var hi = t1
            while (hi - lo > 250) {
                val mid = (lo + hi) / 2
                val above = elev(mid) >= h
                if (above == rising) hi = mid else lo = mid
            }
            return (lo + hi) / 2
        }

        /** Intervals (start, end) in which lo <= elevation < hi. */
        fun bands(lo: Double, hi: Double): List<Pair<Long, Long>> {
            val boundaries = ArrayList<Long>()
            boundaries += start
            crossings(lo).forEach { boundaries += it.time }
            if (hi.isFinite()) crossings(hi).forEach { boundaries += it.time }
            boundaries += end
            boundaries.sort()
            val result = ArrayList<Pair<Long, Long>>()
            for (i in 0 until boundaries.size - 1) {
                val a = boundaries[i]
                val b = boundaries[i + 1]
                if (b <= a) continue
                val e = elev((a + b) / 2)
                if (e >= lo && e < hi) {
                    if (result.isNotEmpty() && result.last().second == a) {
                        result[result.size - 1] = result.last().first to b
                    } else {
                        result += a to b
                    }
                }
            }
            return result
        }

        /**
         * Upward zero crossing of the hour angle (solar transit) or, when [shifted], of the hour angle
         * offset by 180° (anti-transit / solar midnight).
         */
        fun hourAngleZero(shifted: Boolean): Long? {
            fun ha(t: Long): Double {
                val raw = NoaaSolarPosition.hourAngle(t, lon)
                return if (!shifted) raw else {
                    var v = raw + 180.0
                    if (v >= 180.0) v -= 360.0
                    v
                }
            }
            var prev = ha(times[0])
            for (i in 1 until times.size) {
                val cur = ha(times[i])
                if (prev < 0 && cur >= 0 && cur - prev < 90.0) {
                    var lo = times[i - 1]
                    var hi = times[i]
                    while (hi - lo > 250) {
                        val mid = (lo + hi) / 2
                        if (ha(mid) >= 0) hi = mid else lo = mid
                    }
                    return (lo + hi) / 2
                }
                prev = cur
            }
            return null
        }
    }
}
