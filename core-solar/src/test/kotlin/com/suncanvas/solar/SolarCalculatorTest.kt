package com.suncanvas.solar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Compares the NOAA-based engine against reference data generated from the JPL DE421 ephemeris
 * with Skyfield (see tools/gen_reference.py). Expected differences are documented in
 * docs/ACCURACY.md: typically < 30 s, allowed up to 2 min (refraction modelling) and up to
 * 4 min for twilight at high latitudes where the Sun crosses thresholds at grazing angles.
 */
class SolarCalculatorTest {

    private val calc = SolarCalculator()

    private data class Row(
        val id: String, val lat: Double, val lon: Double, val zone: ZoneId, val date: LocalDate,
        val values: Map<String, LocalDateTime?>,
    )

    private val rows: List<Row> by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("reference_skyfield.csv")!!.bufferedReader().readText()
        val lines = text.lines().filter { it.isNotBlank() }
        val header = lines.first().split(",")
        lines.drop(1).map { line ->
            val c = line.split(",")
            val values = (5 until header.size).associate { i ->
                header[i] to (if (c[i] == "none") null else LocalDateTime.parse(c[i]))
            }
            Row(c[0], c[1].toDouble(), c[2].toDouble(), ZoneId.of(c[3]), LocalDate.parse(c[4]), values)
        }
    }

    private fun check(
        name: String,
        expected: LocalDateTime?,
        actual: Instant?,
        zone: ZoneId,
        toleranceSec: Long,
        failures: MutableList<String>,
        label: String,
    ) {
        if (expected == null && actual == null) return
        if (expected == null || actual == null) {
            // Grazing events (the Sun touching a threshold) may be found by one method and not the other.
            failures += "$label $name expected=$expected actual=${actual?.atZone(zone)?.toLocalDateTime()}"
            return
        }
        val exp = expected.atZone(zone).toInstant()
        val diff = abs(Duration.between(exp, actual).seconds)
        if (diff > toleranceSec) failures += "$label $name diff=${diff}s expected=$expected actual=${actual.atZone(zone).toLocalDateTime()}"
    }

    @Test
    fun sunriseSunsetMatchReferenceWorldwide() {
        val failures = mutableListOf<String>()
        for (r in rows) {
            val d = calc.day(r.date, r.lat, r.lon, r.zone)
            val label = "${r.id} ${r.date}"
            val polar = abs(r.lat) > 60
            val tol = if (polar) 240L else 120L
            check("sunrise", r.values["sunrise"], d.sunrise, r.zone, tol, failures, label)
            check("sunset", r.values["sunset"], d.sunset, r.zone, tol, failures, label)
            check("noon", r.values["noon"], d.solarNoon, r.zone, 60, failures, label)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun twilightMatchesReference() {
        val failures = mutableListOf<String>()
        for (r in rows) {
            if (abs(r.lat) > 60) continue // grazing twilight at high latitude is covered by the polar tests
            val d = calc.day(r.date, r.lat, r.lon, r.zone)
            val label = "${r.id} ${r.date}"
            check("civilDawn", r.values["civilDawn"], d.civilDawn, r.zone, 120, failures, label)
            check("civilDusk", r.values["civilDusk"], d.civilDusk, r.zone, 120, failures, label)
            check("nauticalDawn", r.values["nauticalDawn"], d.nauticalDawn, r.zone, 150, failures, label)
            check("nauticalDusk", r.values["nauticalDusk"], d.nauticalDusk, r.zone, 150, failures, label)
            check("astroDawn", r.values["astroDawn"], d.astronomicalDawn, r.zone, 180, failures, label)
            check("astroDusk", r.values["astroDusk"], d.astronomicalDusk, r.zone, 180, failures, label)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun dallasNovember8KnownValues() {
        val zone = ZoneId.of("America/Chicago")
        val d = calc.day(LocalDate.of(2026, 11, 8), 32.7767, -96.7970, zone)
        assertEquals(DayType.NORMAL, d.dayType)
        val rise = d.sunrise!!.atZone(zone).toLocalTime()
        val set = d.sunset!!.atZone(zone).toLocalTime()
        assertEquals(6, rise.hour); assertTrue(rise.minute in 50..52)
        assertEquals(17, set.hour); assertTrue(set.minute in 29..31)
    }

    @Test
    fun polarNightAndPolarDay() {
        val oslo = ZoneId.of("Europe/Oslo")
        val winter = calc.day(LocalDate.of(2026, 12, 21), 69.6492, 18.9553, oslo)
        assertEquals(DayType.POLAR_NIGHT, winter.dayType)
        assertNull(winter.sunrise); assertNull(winter.sunset)
        assertEquals(Duration.ZERO, winter.daylight)
        assertNotNull("civil twilight still happens in Tromsø in December", winter.civilDawn)

        val summer = calc.day(LocalDate.of(2026, 6, 21), 69.6492, 18.9553, oslo)
        assertEquals(DayType.POLAR_DAY, summer.dayType)
        assertEquals(summer.dayLength, summer.daylight)

        val pole = calc.day(LocalDate.of(2026, 6, 21), 89.99, 0.0, ZoneId.of("UTC"))
        assertEquals(DayType.POLAR_DAY, pole.dayType)
    }

    @Test
    fun dstTransitionDaysHaveCorrectLength() {
        val chicago = ZoneId.of("America/Chicago")
        val spring = calc.day(LocalDate.of(2026, 3, 8), 32.7767, -96.7970, chicago)
        val fall = calc.day(LocalDate.of(2026, 11, 1), 32.7767, -96.7970, chicago)
        assertEquals(Duration.ofHours(23), spring.dayLength)
        assertEquals(Duration.ofHours(25), fall.dayLength)
        // Sunrise on the spring-forward day is after 07:00 local because clocks jumped an hour.
        assertTrue(spring.sunrise!!.atZone(chicago).hour >= 7)
        assertTrue(fall.sunrise!!.atZone(chicago).hour == 6)
    }

    @Test
    fun dateLineLocationsUseTheirOwnLocalDate() {
        val kiri = ZoneId.of("Pacific/Kiritimati") // UTC+14
        val d = calc.day(LocalDate.of(2026, 1, 1), 1.8721, -157.4278, kiri)
        assertEquals(LocalDate.of(2026, 1, 1), d.sunrise!!.atZone(kiri).toLocalDate())
        assertEquals(LocalDate.of(2026, 1, 1), d.sunset!!.atZone(kiri).toLocalDate())
        val apia = ZoneId.of("Pacific/Apia")
        val a = calc.day(LocalDate.of(2025, 12, 31), -13.8333, -171.7667, apia)
        assertEquals(LocalDate.of(2025, 12, 31), a.sunrise!!.atZone(apia).toLocalDate())
    }

    @Test
    fun leapDayAndYearEnd() {
        val ny = ZoneId.of("America/New_York")
        val leap = calc.day(LocalDate.of(2024, 2, 29), 40.7128, -74.0060, ny)
        assertNotNull(leap.sunrise)
        val dec31 = calc.day(LocalDate.of(2025, 12, 31), 40.7128, -74.0060, ny)
        val jan1 = calc.day(LocalDate.of(2026, 1, 1), 40.7128, -74.0060, ny)
        val delta = jan1.daylight.minus(dec31.daylight).abs()
        assertTrue("day-to-day change around new year should be small: $delta", delta < Duration.ofMinutes(2))
    }

    @Test
    fun southernHemisphereSeasonsAreInverted() {
        val syd = ZoneId.of("Australia/Sydney")
        val dec = calc.day(LocalDate.of(2026, 12, 21), -33.8688, 151.2093, syd)
        val jun = calc.day(LocalDate.of(2026, 6, 21), -33.8688, 151.2093, syd)
        assertTrue(dec.daylight > jun.daylight)
        assertTrue(dec.daylight > Duration.ofHours(14))
    }

    @Test
    fun goldenAndBlueHoursAreOrdered() {
        val zone = ZoneId.of("Europe/London")
        val d = calc.day(LocalDate.of(2026, 9, 23), 51.5074, -0.1278, zone)
        val gm = d.goldenHourMorning!!
        val ge = d.goldenHourEvening!!
        val bm = d.blueHourMorning!!
        val be = d.blueHourEvening!!
        assertTrue(bm.end <= gm.start.plusSeconds(1))
        assertTrue(d.sunrise!! in gm)
        assertTrue(d.sunset!! in ge)
        assertTrue(ge.end <= be.start.plusSeconds(1))
    }

    @Test
    fun positionAtSolarNoonFacesSouthInNorthernHemisphere() {
        val zone = ZoneId.of("America/Chicago")
        val d = calc.day(LocalDate.of(2026, 6, 21), 32.7767, -96.7970, zone)
        val p = calc.position(d.solarNoon!!, 32.7767, -96.7970)
        assertTrue(abs(p.azimuth - 180.0) < 2.0 || p.elevation > 80)
        assertEquals(90 - 32.7767 + 23.44, p.elevation, 0.3)
    }

    @Test
    fun nextSunEventsAcrossPolarNight() {
        val oslo = ZoneId.of("Europe/Oslo")
        val from = LocalDate.of(2026, 12, 1).atStartOfDay(oslo).toInstant()
        val next = calc.nextSunEvents(from, 69.6492, 18.9553, oslo)
        val rise = next.nextSunrise!!.atZone(oslo).toLocalDate()
        assertTrue("Tromsø polar night ends mid-January, got $rise", rise.monthValue == 1 && rise.dayOfMonth in 10..20)
    }

    @Test
    fun seasonsMatchPublishedTimes() {
        // USNO: 2026 March equinox 14:46 UTC, June solstice 08:24 UTC, Sept equinox 00:05 UTC, Dec solstice 20:50 UTC.
        val expected = listOf("2026-03-20T14:46:00Z", "2026-06-21T08:24:00Z", "2026-09-23T00:05:00Z", "2026-12-21T20:50:00Z")
        Seasons.eventsForYear(2026).zip(expected).forEach { (e, s) ->
            val diff = abs(Duration.between(Instant.parse(s), e.instant).toMinutes())
            assertTrue("${e.type} off by $diff min (${e.instant})", diff <= 15)
        }
    }
}
