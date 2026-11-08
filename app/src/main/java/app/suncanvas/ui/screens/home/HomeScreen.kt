package app.suncanvas.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.core.Durations
import app.suncanvas.core.TimeFormatter
import app.suncanvas.data.SunSnapshot
import app.suncanvas.data.db.LocationEntity
import app.suncanvas.ui.AppViewModel
import app.suncanvas.ui.components.EmptyState
import app.suncanvas.ui.components.LabeledValue
import app.suncanvas.ui.components.SectionHeader
import app.suncanvas.ui.components.StatTile
import app.suncanvas.ui.components.SunArc
import app.suncanvas.ui.components.SunCard
import app.suncanvas.ui.components.SunGlyph
import app.suncanvas.ui.components.SunGlyphIcon
import app.suncanvas.ui.components.SunPositionDial
import app.suncanvas.ui.components.rememberLocationRefresher
import app.suncanvas.ui.rememberNow
import app.suncanvas.ui.theme.ClockTextStyle
import app.suncanvas.ui.theme.LocalSunColors
import app.suncanvas.ui.theme.Spacing
import app.suncanvas.solar.DayType
import app.suncanvas.solar.SeasonEventType
import app.suncanvas.solar.Seasons
import java.time.Duration
import java.time.ZoneId

@Composable
fun HomeScreen(vm: AppViewModel, onOpenLocation: (Long) -> Unit, onPickLocation: () -> Unit) {
    val active by vm.active.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val refresh = rememberLocationRefresher(vm)
    val showSeconds = settings?.showSeconds == true
    val now = rememberNow(if (showSeconds) 1000L else 1000L)
    val fmt = remember(settings) { vm.formatter() }

    val loc = active
    if (loc == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Rounded.WbSunny, "No location yet", "Detect your location or search for a city to see today's sunrise and sunset.") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilledTonalButton(onClick = refresh) { Icon(Icons.Rounded.MyLocation, null); Spacer(Modifier.width(6.dp)); Text("Use my location") }
                    FilledTonalButton(onClick = onPickLocation) { Text("Search city") }
                }
            }
        }
        return
    }
    // Recompute solar data once a minute (memory-cached, sub-millisecond).
    val minute = now.epochSecond / 60
    val snap = remember(loc, minute) { vm.solar.snapshot(loc, now) }
    val zone = snap.zone
    val sun = LocalSunColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        (if (snap.isDay) sun.sunrise else sun.blue).copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                    ),
                    endY = 900f,
                )
            )
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Spacing.screen),
    ) {
        LocationHeader(loc, locations, locating, onRefresh = refresh, onSelect = { vm.setActive(it) }, onDetails = { onOpenLocation(loc.id) }, onAdd = onPickLocation)
        ClockBlock(snap, fmt, now, showSeconds)
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SunEventCard("Sunrise", snap.today.sunrise?.let { fmt.time(it, zone) } ?: noEventText(snap, true), SunGlyph.SUNRISE, sun.sunrise, Modifier.weight(1f))
            SunEventCard("Sunset", snap.today.sunset?.let { fmt.time(it, zone) } ?: noEventText(snap, false), SunGlyph.SUNSET, sun.sunset, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.md))
        DaylightCard(snap, fmt)
        PolarNotice(snap, fmt)
        SectionHeader("Today")
        InfoGrid(snap, fmt)
        SectionHeader("Twilight")
        TwilightCard(snap, fmt)
        SectionHeader("Sun position")
        SunCard(Modifier.fillMaxWidth()) {
            Column {
                SunPositionDial(snap.position.azimuth, snap.position.elevation)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Elevation %.1f° · Azimuth %.0f° %s".format(snap.position.elevation, snap.position.azimuth, compass(snap.position.azimuth)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Solar noon elevation %.1f°".format(snap.today.noonElevation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionHeader("Seasons")
        SeasonsCard(now, zone, fmt)
        Spacer(Modifier.height(Spacing.xxl))
    }
}

private fun noEventText(snap: SunSnapshot, rise: Boolean) = when (snap.today.dayType) {
    DayType.POLAR_DAY -> if (rise) "Up all day" else "Doesn't set"
    DayType.POLAR_NIGHT -> if (rise) "Doesn't rise" else "Down all day"
    DayType.NORMAL -> "—"
}

fun compass(az: Double): String {
    val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return dirs[(((az % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]
}

@Composable
private fun LocationHeader(
    loc: LocationEntity,
    all: List<LocationEntity>,
    locating: Boolean,
    onRefresh: () -> Unit,
    onSelect: (Long) -> Unit,
    onDetails: () -> Unit,
    onAdd: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "Choose location") { menu = true }
                .padding(vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loc.source == app.suncanvas.data.db.LocationSource.DEVICE) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Current location", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(loc.headline, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() })
                Icon(Icons.Rounded.ExpandMore, contentDescription = null)
            }
            val sub = if (loc.customLabel.isNullOrBlank()) loc.subtitle else "${loc.name} · ${loc.subtitle}"
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                all.forEach { l ->
                    DropdownMenuItem(
                        text = { Text(l.headline) },
                        leadingIcon = if (l.id == loc.id) ({ Icon(Icons.Rounded.Check, null) }) else null,
                        onClick = { menu = false; onSelect(l.id) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Location details") }, onClick = { menu = false; onDetails() })
                DropdownMenuItem(text = { Text("Add or search a city") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; onAdd() })
            }
        }
        FilledTonalButton(onClick = onRefresh, enabled = !locating, modifier = Modifier.height(48.dp)) {
            if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (locating) "Locating" else "Refresh")
        }
    }
}

@Composable
private fun ClockBlock(snap: SunSnapshot, fmt: TimeFormatter, now: java.time.Instant, showSeconds: Boolean) {
    val zone = snap.zone
    val (main, ampm) = fmt.clockParts(now, zone)
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(main, style = ClockTextStyle.copy(fontSize = 72.sp, color = MaterialTheme.colorScheme.onBackground))
            if (showSeconds) {
                Text(":%02d".format(now.atZone(zone).second), style = ClockTextStyle.copy(fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            if (ampm != null) {
                Spacer(Modifier.width(8.dp))
                Text(ampm, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 14.dp))
            }
        }
        val deviceZone = ZoneId.systemDefault()
        val tz = TimeFormatter.zoneAbbreviation(zone, now)
        Text("${fmt.date(now, zone)} · $tz", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (deviceZone.rules.getOffset(now) != zone.rules.getOffset(now)) {
            Text("Your phone: ${fmt.time(now, deviceZone)} (${TimeFormatter.zoneAbbreviation(deviceZone, now)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SunEventCard(label: String, value: String, glyph: SunGlyph, tint: Color, modifier: Modifier) {
    SunCard(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunGlyphIcon(glyph, tint, size = 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun DaylightCard(snap: SunSnapshot, fmt: TimeFormatter) {
    val sun = LocalSunColors.current
    val progress = snap.daylightProgress
    val next = snap.nextSunEvent
    SunCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (snap.isDay) Icons.Rounded.LightMode else Icons.Rounded.NightsStay, null, tint = if (snap.isDay) sun.sunrise else sun.blue)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        progress != null -> "${(progress * 100).toInt()}% of today's daylight has passed"
                        snap.today.dayType == DayType.POLAR_NIGHT -> "Polar night"
                        else -> "Night"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            SunArc(progress, if (progress == null) nightProgress(snap) else null, Modifier.fillMaxWidth().height(150.dp).padding(vertical = Spacing.sm))
            Row(Modifier.fillMaxWidth()) {
                Text(fmt.time(snap.today.sunrise, snap.zone), style = MaterialTheme.typography.labelLarge, color = sun.sunrise, modifier = Modifier.weight(1f))
                Text(fmt.time(snap.today.solarNoon, snap.zone), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt.time(snap.today.sunset, snap.zone), style = MaterialTheme.typography.labelLarge, color = sun.sunset, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (next != null) {
                Spacer(Modifier.height(Spacing.md))
                val isRise = next.type == app.suncanvas.solar.SolarEventType.SUNRISE
                Box(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .background((if (isRise) sun.sunrise else sun.sunset).copy(alpha = 0.14f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text(
                        "Next: ${if (isRise) "sunrise" else "sunset"} ${Durations.countdown(snap.now, next.instant)} · ${fmt.timeWithDay(next.instant, snap.zone, snap.localDate)}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

fun nightProgress(snap: SunSnapshot): Float? {
    val prevSet = listOfNotNull(snap.yesterday.sunset, snap.today.sunset).filter { it.isBefore(snap.now) }.maxOrNull() ?: return null
    val nextRise = snap.next.nextSunrise ?: return null
    val total = Duration.between(prevSet, nextRise).seconds.toFloat()
    if (total <= 0) return null
    return (Duration.between(prevSet, snap.now).seconds / total).coerceIn(0f, 1f)
}

@Composable
private fun PolarNotice(snap: SunSnapshot, fmt: TimeFormatter) {
    AnimatedVisibility(snap.today.dayType != DayType.NORMAL) {
        SunCard(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
            val polarDay = snap.today.dayType == DayType.POLAR_DAY
            val nextChange = if (polarDay) snap.next.nextSunset else snap.next.nextSunrise
            Text(
                (if (polarDay) "Midnight sun: the Sun stays above the horizon all day." else "Polar night: the Sun stays below the horizon all day.") +
                    (nextChange?.let { " Next ${if (polarDay) "sunset" else "sunrise"}: ${fmt.date(it, snap.zone)} at ${fmt.time(it, snap.zone)}." } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun InfoGrid(snap: SunSnapshot, fmt: TimeFormatter) {
    val sun = LocalSunColors.current
    val z = snap.zone
    val change = snap.daylightChange
    val longer = !change.isNegative
    val tiles = buildList {
        add(Triple("Daylight", Durations.hm(snap.today.daylight), "${Durations.signedMinSec(change)} vs yesterday"))
        if (snap.isDay) add(Triple("Remaining daylight", snap.remainingDaylight?.let { Durations.hm(it) } ?: "All day", "Sunset ${fmt.timeWithDay(snap.next.nextSunset, z, snap.localDate)}"))
        else add(Triple("Until sunrise", snap.next.nextSunrise?.let { Durations.countdown(snap.now, it).removePrefix("in ") } ?: "—", "Sunrise ${fmt.timeWithDay(snap.next.nextSunrise, z, snap.localDate)}"))
        add(Triple("Next sunrise", fmt.timeWithDay(snap.next.nextSunrise, z, snap.localDate), snap.next.nextSunrise?.let { Durations.countdown(snap.now, it) } ?: ""))
        add(Triple("Solar noon", fmt.time(snap.today.solarNoon, z), "Solar midnight ${fmt.time(snap.today.solarMidnight, z)}"))
        add(Triple("Golden hour · morning", window(snap.today.goldenHourMorning, fmt, z), "Sun between −4° and +6°"))
        add(Triple("Golden hour · evening", window(snap.today.goldenHourEvening, fmt, z), "Photography convention"))
        add(Triple("Blue hour · morning", window(snap.today.blueHourMorning, fmt, z), "Sun between −6° and −4°"))
        add(Triple("Blue hour · evening", window(snap.today.blueHourEvening, fmt, z), "Photography convention"))
    }
    val icons = listOf(
        if (longer) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, Icons.Rounded.HourglassBottom, Icons.Rounded.WbSunny,
        Icons.Rounded.Schedule, Icons.Rounded.PhotoCamera, Icons.Rounded.PhotoCamera, Icons.Rounded.NightsStay, Icons.Rounded.NightsStay,
    )
    val tints = listOf(if (longer) sun.sunrise else sun.blue, sun.sunset, sun.sunrise, sun.golden, sun.golden, sun.sunset, sun.blue, sun.blue)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                pair.forEachIndexed { i, (label, value, supporting) ->
                    val idx = row * 2 + i
                    StatTile(label, value, Modifier.weight(1f), icons.getOrNull(idx), tints.getOrElse(idx) { sun.sunrise }, supporting)
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun window(w: app.suncanvas.solar.TimeWindow?, fmt: TimeFormatter, z: ZoneId): String =
    w?.let { "${if (it.startClipped) "…" else fmt.time(it.start, z)}–${if (it.endClipped) "…" else fmt.time(it.end, z)}" } ?: "None today"

@Composable
private fun TwilightCard(snap: SunSnapshot, fmt: TimeFormatter) {
    val z = snap.zone
    val d = snap.today
    SunCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Text("", Modifier.weight(1.3f))
                Text("Dawn", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Dusk", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            listOf(
                Triple("Civil (−6°)", d.civilDawn, d.civilDusk),
                Triple("Nautical (−12°)", d.nauticalDawn, d.nauticalDusk),
                Triple("Astronomical (−18°)", d.astronomicalDawn, d.astronomicalDusk),
            ).forEach { (name, dawn, dusk) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(name, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium)
                    Text(fmt.time(dawn, z, "None"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(fmt.time(dusk, z, "None"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
            LabeledValue("Day length (clock)", Durations.hm(d.dayLength))
            Text(
                "Sunrise and sunset use the standard −0.833° altitude (refraction + solar radius). Golden and blue hours are photography conventions, not astronomical events.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeasonsCard(now: java.time.Instant, zone: ZoneId, fmt: TimeFormatter) {
    val events = remember(now.epochSecond / 3600) { Seasons.upcoming(now, 4) }
    SunCard(Modifier.fillMaxWidth()) {
        Column {
            events.forEach { e ->
                val name = when (e.type) {
                    SeasonEventType.MARCH_EQUINOX -> "March equinox"
                    SeasonEventType.JUNE_SOLSTICE -> "June solstice"
                    SeasonEventType.SEPTEMBER_EQUINOX -> "September equinox"
                    SeasonEventType.DECEMBER_SOLSTICE -> "December solstice"
                }
                val days = Duration.between(now, e.instant).toDays()
                LabeledValue(name, "${fmt.date(e.instant, zone)} · ${fmt.time(e.instant, zone)}  ($days d)")
            }
        }
    }
}
