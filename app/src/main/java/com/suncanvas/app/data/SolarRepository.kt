package com.suncanvas.app.data

import android.util.LruCache
import com.suncanvas.app.data.db.LocationEntity
import com.suncanvas.app.data.db.SolarCacheDao
import com.suncanvas.app.data.db.SolarCacheEntity
import com.suncanvas.solar.DayType
import com.suncanvas.solar.NextSunEvents
import com.suncanvas.solar.SolarAltitude
import com.suncanvas.solar.SolarCalculator
import com.suncanvas.solar.SolarDay
import com.suncanvas.solar.SolarEvent
import com.suncanvas.solar.SolarEventType
import com.suncanvas.solar.SunPosition
import com.suncanvas.solar.TimeWindow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the UI and widgets need about one location at one moment. */
data class SunSnapshot(
    val location: LocationEntity,
    val zone: ZoneId,
    val now: Instant,
    val localDate: LocalDate,
    val today: SolarDay,
    val yesterday: SolarDay,
    val tomorrow: SolarDay,
    val next: NextSunEvents,
    val position: SunPosition,
) {
    val isDay: Boolean get() = position.geometricElevation > SolarAltitude.HORIZON
    val daylightChange: Duration get() = today.daylight.minus(yesterday.daylight)

    /** Fraction of today's daylight that has passed (0..1), or null at night / polar night. */
    val daylightProgress: Float?
        get() {
            if (today.dayType == DayType.POLAR_DAY) {
                val start = localDate.atStartOfDay(zone).toInstant()
                return (Duration.between(start, now).seconds.toFloat() / today.dayLength.seconds).coerceIn(0f, 1f)
            }
            val rise = today.sunrise ?: return null
            val set = today.sunset ?: return null
            if (!set.isAfter(rise) || now.isBefore(rise) || now.isAfter(set)) return null
            return (Duration.between(rise, now).seconds.toFloat() / Duration.between(rise, set).seconds).coerceIn(0f, 1f)
        }

    /** Remaining daylight today, if the Sun is up. */
    val remainingDaylight: Duration?
        get() = if (isDay) next.nextSunset?.let { Duration.between(now, it) } else null

    /** The next sunrise or sunset. */
    val nextSunEvent: SolarEvent?
        get() = when (next.nextIsSunrise) {
            true -> SolarEvent(SolarEventType.SUNRISE, next.nextSunrise!!)
            false -> SolarEvent(SolarEventType.SUNSET, next.nextSunset!!)
            null -> null
        }

    /** The next golden-hour window (current one if inside it). */
    val nextGoldenHour: TimeWindow?
        get() = listOfNotNull(today.goldenHourMorning, today.goldenHourEvening, tomorrow.goldenHourMorning, tomorrow.goldenHourEvening)
            .firstOrNull { it.end.isAfter(now) }

    val inGoldenHour: Boolean get() = nextGoldenHour?.let { now in it } == true
}

class SolarRepository(private val cacheDao: SolarCacheDao) {

    private val calculator = SolarCalculator()
    private val memory = LruCache<String, SolarDay>(256)
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(lat: Double, lon: Double, zone: ZoneId, date: LocalDate) =
        "%.4f|%.4f|%s|%s".format(java.util.Locale.US, lat, lon, zone.id, date)

    /** Synchronous, memory-cached computation (safe on any thread; ~0.2 ms per day). */
    fun day(lat: Double, lon: Double, zone: ZoneId, date: LocalDate): SolarDay {
        val k = key(lat, lon, zone, date)
        memory.get(k)?.let { return it }
        return calculator.day(date, lat, lon, zone).also { memory.put(k, it) }
    }

    fun day(location: LocationEntity, date: LocalDate): SolarDay = day(location.latitude, location.longitude, location.zone, date)

    /** Read-through persistent cache, used for frequently viewed locations (e.g. calendar months). */
    suspend fun dayCached(location: LocationEntity, date: LocalDate): SolarDay {
        val zone = location.zone
        val k = key(location.latitude, location.longitude, zone, date)
        memory.get(k)?.let { return it }
        cacheDao.get(k)?.let { row ->
            runCatching { json.decodeFromString(SolarDayDto.serializer(), row.payload).toModel(zone) }.getOrNull()?.let {
                memory.put(k, it); return it
            }
        }
        val d = calculator.day(date, location.latitude, location.longitude, zone)
        memory.put(k, d)
        cacheDao.put(SolarCacheEntity(k, json.encodeToString(SolarDayDto.serializer(), SolarDayDto.from(d)), System.currentTimeMillis()))
        return d
    }

    suspend fun pruneCache() = cacheDao.prune(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)

    fun range(location: LocationEntity, from: LocalDate, days: Int): List<SolarDay> =
        (0 until days).map { day(location, from.plusDays(it.toLong())) }

    fun position(location: LocationEntity, at: Instant): SunPosition = calculator.position(at, location.latitude, location.longitude)

    fun elevationCurve(location: LocationEntity, date: LocalDate, points: Int = 97) =
        calculator.elevationCurve(date, location.latitude, location.longitude, location.zone, points)

    fun snapshot(location: LocationEntity, now: Instant = Instant.now()): SunSnapshot {
        val zone = location.zone
        val date = now.atZone(zone).toLocalDate()
        val today = day(location, date)
        val tomorrow = day(location, date.plusDays(1))
        // Fast path: use today/tomorrow events; fall back to a long search only in polar regions.
        val candidates = listOfNotNull(today.sunrise, today.sunset, tomorrow.sunrise, tomorrow.sunset)
        val rise = listOfNotNull(today.sunrise, tomorrow.sunrise).firstOrNull { it.isAfter(now) }
        val set = listOfNotNull(today.sunset, tomorrow.sunset).firstOrNull { it.isAfter(now) }
        val next = if (rise != null && set != null) NextSunEvents(rise, set)
        else if (candidates.isEmpty() && today.dayType != DayType.NORMAL && tomorrow.dayType == today.dayType) {
            calculator.nextSunEvents(now, location.latitude, location.longitude, zone)
        } else {
            val far = calculator.nextSunEvents(now, location.latitude, location.longitude, zone)
            NextSunEvents(rise ?: far.nextSunrise, set ?: far.nextSunset)
        }
        return SunSnapshot(
            location = location,
            zone = zone,
            now = now,
            localDate = date,
            today = today,
            yesterday = day(location, date.minusDays(1)),
            tomorrow = tomorrow,
            next = next,
            position = position(location, now),
        )
    }
}

val LocationEntity.zone: ZoneId get() = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.of("UTC"))

@Serializable
private data class WindowDto(val s: Long, val e: Long, val sc: Boolean, val ec: Boolean) {
    fun toModel() = TimeWindow(Instant.ofEpochMilli(s), Instant.ofEpochMilli(e), sc, ec)
    companion object {
        fun from(w: TimeWindow?) = w?.let { WindowDto(it.start.toEpochMilli(), it.end.toEpochMilli(), it.startClipped, it.endClipped) }
    }
}

@Serializable
private data class SolarDayDto(
    val date: String, val lat: Double, val lon: Double, val type: String,
    val rise: Long?, val set: Long?, val noon: Long?, val noonEl: Double, val midnight: Long?,
    val cd: Long?, val cdu: Long?, val nd: Long?, val ndu: Long?, val ad: Long?, val adu: Long?,
    val daylight: Long, val gm: WindowDto?, val ge: WindowDto?, val bm: WindowDto?, val be: WindowDto?, val len: Long,
) {
    fun toModel(zone: ZoneId) = SolarDay(
        date = LocalDate.parse(date), zone = zone, latitude = lat, longitude = lon, dayType = DayType.valueOf(type),
        sunrise = rise?.let(Instant::ofEpochMilli), sunset = set?.let(Instant::ofEpochMilli),
        solarNoon = noon?.let(Instant::ofEpochMilli), noonElevation = noonEl, solarMidnight = midnight?.let(Instant::ofEpochMilli),
        civilDawn = cd?.let(Instant::ofEpochMilli), civilDusk = cdu?.let(Instant::ofEpochMilli),
        nauticalDawn = nd?.let(Instant::ofEpochMilli), nauticalDusk = ndu?.let(Instant::ofEpochMilli),
        astronomicalDawn = ad?.let(Instant::ofEpochMilli), astronomicalDusk = adu?.let(Instant::ofEpochMilli),
        daylight = Duration.ofMillis(daylight), goldenHourMorning = gm?.toModel(), goldenHourEvening = ge?.toModel(),
        blueHourMorning = bm?.toModel(), blueHourEvening = be?.toModel(), dayLength = Duration.ofMillis(len),
    )

    companion object {
        fun from(d: SolarDay) = SolarDayDto(
            d.date.toString(), d.latitude, d.longitude, d.dayType.name,
            d.sunrise?.toEpochMilli(), d.sunset?.toEpochMilli(), d.solarNoon?.toEpochMilli(), d.noonElevation,
            d.solarMidnight?.toEpochMilli(), d.civilDawn?.toEpochMilli(), d.civilDusk?.toEpochMilli(),
            d.nauticalDawn?.toEpochMilli(), d.nauticalDusk?.toEpochMilli(), d.astronomicalDawn?.toEpochMilli(),
            d.astronomicalDusk?.toEpochMilli(), d.daylight.toMillis(), WindowDto.from(d.goldenHourMorning),
            WindowDto.from(d.goldenHourEvening), WindowDto.from(d.blueHourMorning), WindowDto.from(d.blueHourEvening),
            d.dayLength.toMillis(),
        )
    }
}
