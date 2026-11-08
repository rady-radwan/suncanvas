package com.suncanvas.app

import com.suncanvas.app.core.Durations
import com.suncanvas.app.core.TimeFormatter
import com.suncanvas.app.location.TimeZoneResolver
import com.suncanvas.app.ui.screens.cities.parseCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class FormattingTest {

    @Test
    fun formatsInTheLocationZoneNotThePhoneZone() {
        val instant = Instant.parse("2026-11-08T12:51:10Z")
        val f24 = TimeFormatter(true, locale = Locale.US)
        assertEquals("06:51", f24.time(instant, ZoneId.of("America/Chicago")))
        assertEquals("14:51", f24.time(instant, ZoneId.of("Africa/Cairo")))
        assertEquals("21:51", f24.time(instant, ZoneId.of("Asia/Tokyo")))
        val f12 = TimeFormatter(false, locale = Locale.US)
        assertEquals("6:51 AM", f12.time(instant, ZoneId.of("America/Chicago")))
    }

    @Test
    fun durations() {
        assertEquals("10h 34m", Durations.hm(Duration.ofMinutes(634)))
        assertEquals("45m", Durations.hm(Duration.ofMinutes(45)))
        assertEquals("+2m 05s", Durations.signedMinSec(Duration.ofSeconds(125)))
        assertEquals("−1m 00s", Durations.signedMinSec(Duration.ofSeconds(-60)))
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals("in 2h 05m", Durations.countdown(now, now.plusSeconds(2 * 3600 + 5 * 60)))
        assertEquals("now", Durations.countdown(now, now.plusSeconds(10)))
    }

    @Test
    fun parsesCoordinates() {
        assertEquals(32.7767, parseCoordinate("32.7767", true)!!, 1e-9)
        assertEquals(-96.797, parseCoordinate("96.797 W", false)!!, 1e-9)
        assertEquals(-33.86, parseCoordinate("33,86S", true)!!, 1e-9)
        assertNull(parseCoordinate("91", true))
        assertNull(parseCoordinate("abc", false))
    }

    @Test
    fun nauticalZoneSignsAreInverted() {
        assertEquals("Etc/GMT-3", TimeZoneResolver.nauticalZone(45.0))
        assertEquals("Etc/GMT+5", TimeZoneResolver.nauticalZone(-75.0))
        assertEquals("Etc/GMT", TimeZoneResolver.nauticalZone(2.0))
        assertEquals(3 * 3600, ZoneId.of(TimeZoneResolver.nauticalZone(45.0)).rules.getOffset(Instant.EPOCH).totalSeconds)
    }
}
