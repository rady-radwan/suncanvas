package app.suncanvas.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** User-selectable date styles. Patterns are locale-aware where possible. */
enum class DateStyle(val label: String, val pattern: String) {
    MEDIUM("Sun, Nov 8", "EEE, MMM d"),
    LONG("Sunday, November 8", "EEEE, MMMM d"),
    NUMERIC_US("11/08/2026", "MM/dd/yyyy"),
    NUMERIC_EU("08/11/2026", "dd/MM/yyyy"),
    ISO("2026-11-08", "yyyy-MM-dd"),
}

/**
 * Formats instants in an explicit time zone — never the phone's zone unless asked —
 * honouring the 12/24-hour preference.
 */
class TimeFormatter(
    val use24h: Boolean,
    val dateStyle: DateStyle = DateStyle.MEDIUM,
    private val locale: Locale = Locale.getDefault(),
) {
    private val time12 = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val time12NoSuffix = DateTimeFormatter.ofPattern("h:mm", locale)
    private val time24 = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val time12Sec = DateTimeFormatter.ofPattern("h:mm:ss a", locale)
    private val time24Sec = DateTimeFormatter.ofPattern("HH:mm:ss", locale)
    private val ampm = DateTimeFormatter.ofPattern("a", locale)
    private val dateFmt = DateTimeFormatter.ofPattern(dateStyle.pattern, locale)
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", locale)

    fun time(instant: Instant?, zone: ZoneId, placeholder: String = "—"): String =
        instant?.let { (if (use24h) time24 else time12).format(it.atZone(zone)) } ?: placeholder

    fun timeWithSeconds(instant: Instant, zone: ZoneId): String =
        (if (use24h) time24Sec else time12Sec).format(instant.atZone(zone))

    /** Time without the AM/PM marker (for large clock faces) plus the marker separately. */
    fun clockParts(instant: Instant, zone: ZoneId): Pair<String, String?> {
        val z = instant.atZone(zone)
        return if (use24h) time24.format(z) to null else time12NoSuffix.format(z) to ampm.format(z)
    }

    fun date(date: LocalDate): String = dateFmt.format(date)
    fun date(instant: Instant, zone: ZoneId): String = dateFmt.format(instant.atZone(zone))
    fun shortDate(date: LocalDate): String = shortDate.format(date)

    /** Time of an event that may fall on a different local date than [reference]. */
    fun timeWithDay(instant: Instant?, zone: ZoneId, reference: LocalDate): String {
        if (instant == null) return "—"
        val z = instant.atZone(zone)
        val t = time(instant, zone)
        return if (z.toLocalDate() == reference) t else "$t · ${shortDate.format(z)}"
    }

    /** Clock pattern for native TextClock widgets. */
    val textClockPattern: String get() = if (use24h) "HH:mm" else "h:mm"

    companion object {
        fun zoneAbbreviation(zone: ZoneId, at: Instant = Instant.now(), locale: Locale = Locale.getDefault()): String {
            val z = ZonedDateTime.ofInstant(at, zone)
            val dst = zone.rules.isDaylightSavings(at)
            val tz = java.util.TimeZone.getTimeZone(zone)
            val name = tz.getDisplayName(dst, java.util.TimeZone.SHORT, locale)
            return if (name.startsWith("GMT") && name.length > 3) "UTC${z.offset.id.replace("Z", "")}" else name
        }

        fun utcOffset(zone: ZoneId, at: Instant = Instant.now()): String {
            val off = zone.rules.getOffset(at).id
            return if (off == "Z") "UTC" else "UTC$off"
        }

        fun zoneDisplayName(zone: ZoneId, locale: Locale = Locale.getDefault()): String =
            zone.getDisplayName(TextStyle.FULL, locale)
    }
}

object Durations {
    /** "10h 34m", "45m", "0m". */
    fun hm(d: Duration): String {
        val totalMin = abs(d.toMinutes())
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /** "+2m 14s" / "−1m 05s" for day-to-day daylight differences. */
    fun signedMinSec(d: Duration): String {
        val sign = if (d.isNegative) "−" else "+"
        val s = abs(d.seconds)
        val m = s / 60
        val sec = s % 60
        return if (m > 0) "$sign${m}m ${"%02d".format(sec)}s" else "$sign${sec}s"
    }

    /** "in 2h 05m", "in 4m", "now". */
    fun countdown(from: Instant, to: Instant): String {
        val d = Duration.between(from, to)
        if (d.isNegative || d.seconds < 30) return "now"
        val totalMin = (d.seconds + 59) / 60
        val days = totalMin / (60 * 24)
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return when {
            days > 0 -> "in ${days}d ${h}h"
            h > 0 -> "in ${h}h ${"%02d".format(m)}m"
            else -> "in ${m}m"
        }
    }

    fun clock(d: Duration): String {
        val s = abs(d.seconds)
        return "%d:%02d".format(s / 3600, (s % 3600) / 60)
    }
}

/** Haversine distance in kilometres. */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))
}

fun formatCoordinate(value: Double, isLatitude: Boolean): String {
    val hemi = if (isLatitude) (if (value >= 0) "N" else "S") else (if (value >= 0) "E" else "W")
    return "%.4f° %s".format(Locale.US, abs(value), hemi)
}
