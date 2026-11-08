package com.suncanvas.solar

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Solar position using the NOAA Solar Calculator equations
 * (Global Monitoring Laboratory, based on Jean Meeus, "Astronomical Algorithms").
 *
 * All angles in degrees. Times are epoch milliseconds (UTC).
 * Accuracy of the underlying position is about one arc-minute for years 1800–2100.
 */
object NoaaSolarPosition {

    /** Standard altitude for sunrise/sunset: refraction (34') + solar semi-diameter (16'). */
    const val SUNRISE_ALTITUDE = -0.833

    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val JD_UNIX_EPOCH = 2440587.5
    private const val JD_J2000 = 2451545.0

    data class Ephemeris(
        /** Solar declination, degrees. */
        val declination: Double,
        /** Equation of time, minutes. */
        val equationOfTime: Double,
        /** Apparent ecliptic longitude of the Sun, degrees [0, 360). */
        val apparentLongitude: Double,
    )

    fun julianDay(epochMillis: Long): Double = epochMillis / MILLIS_PER_DAY + JD_UNIX_EPOCH

    fun ephemeris(epochMillis: Long): Ephemeris {
        val t = (julianDay(epochMillis) - JD_J2000) / 36525.0
        val l0 = normalize360(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = m.toRad()
        val c = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val appLong = trueLong - 0.00569 - 0.00478 * sin(omega.toRad())
        val meanObliq = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliqCorr = meanObliq + 0.00256 * cos(omega.toRad())
        val decl = asin(sin(obliqCorr.toRad()) * sin(appLong.toRad())).toDeg()
        val y = tan(obliqCorr.toRad() / 2).let { it * it }
        val l0r = l0.toRad()
        val eqTime = 4.0 * (
            y * sin(2 * l0r) -
                2 * e * sin(mRad) +
                4 * e * y * sin(mRad) * cos(2 * l0r) -
                0.5 * y * y * sin(4 * l0r) -
                1.25 * e * e * sin(2 * mRad)
            ).toDeg()
        return Ephemeris(decl, eqTime, normalize360(appLong))
    }

    /**
     * Local hour angle in degrees, in [-180, 180). 0 at the meridian (solar noon), negative in the morning.
     */
    fun hourAngle(epochMillis: Long, longitude: Double, eph: Ephemeris = ephemeris(epochMillis)): Double {
        val minutesOfDayUtc = floorMod(epochMillis, 86_400_000L) / 60_000.0
        val trueSolarTime = floorModD(minutesOfDayUtc + eph.equationOfTime + 4.0 * longitude, 1440.0)
        var ha = trueSolarTime / 4.0 - 180.0
        if (ha < -180.0) ha += 360.0
        if (ha >= 180.0) ha -= 360.0
        return ha
    }

    /** Geometric (unrefracted) elevation of the solar centre, degrees. */
    fun geometricElevation(epochMillis: Long, latitude: Double, longitude: Double): Double {
        val eph = ephemeris(epochMillis)
        val ha = hourAngle(epochMillis, longitude, eph)
        return elevationFrom(latitude, eph.declination, ha)
    }

    fun position(epochMillis: Long, latitude: Double, longitude: Double): SunPosition {
        val eph = ephemeris(epochMillis)
        val ha = hourAngle(epochMillis, longitude, eph)
        val elevation = elevationFrom(latitude, eph.declination, ha)
        val latR = latitude.toRad()
        val decR = eph.declination.toRad()
        val haR = ha.toRad()
        val az = normalize360(
            atan2(sin(haR), cos(haR) * sin(latR) - tan(decR) * cos(latR)).toDeg() + 180.0
        )
        return SunPosition(
            elevation = elevation + refraction(elevation),
            geometricElevation = elevation,
            azimuth = az,
            declination = eph.declination,
            equationOfTimeMinutes = eph.equationOfTime,
            hourAngle = ha,
        )
    }

    /** NOAA approximation of atmospheric refraction, degrees, for a geometric elevation. */
    fun refraction(elevation: Double): Double {
        if (elevation > 85.0) return 0.0
        val te = tan(elevation.toRad())
        val arcSec = when {
            elevation > 5.0 -> 58.1 / te - 0.07 / (te * te * te) + 0.000086 / (te * te * te * te * te)
            elevation > -0.575 -> 1735.0 + elevation * (-518.2 + elevation * (103.4 + elevation * (-12.79 + elevation * 0.711)))
            else -> -20.772 / te
        }
        return arcSec / 3600.0
    }

    private fun elevationFrom(latitude: Double, declination: Double, hourAngle: Double): Double {
        val latR = latitude.toRad()
        val decR = declination.toRad()
        val cosZ = (sin(latR) * sin(decR) + cos(latR) * cos(decR) * cos(hourAngle.toRad())).coerceIn(-1.0, 1.0)
        return 90.0 - acos(cosZ).toDeg()
    }

    internal fun Double.toRad() = this * PI / 180.0
    internal fun Double.toDeg() = this * 180.0 / PI
    internal fun normalize360(v: Double): Double = v - 360.0 * floor(v / 360.0)
    private fun floorMod(a: Long, b: Long): Long = ((a % b) + b) % b
    private fun floorModD(a: Double, b: Double): Double = a - b * floor(a / b)
}

data class SunPosition(
    /** Apparent elevation including NOAA refraction model, degrees. */
    val elevation: Double,
    /** Geometric elevation (no refraction), degrees. */
    val geometricElevation: Double,
    /** Azimuth clockwise from true north, degrees [0, 360). */
    val azimuth: Double,
    val declination: Double,
    val equationOfTimeMinutes: Double,
    val hourAngle: Double,
)
