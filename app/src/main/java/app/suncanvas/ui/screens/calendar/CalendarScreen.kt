package app.suncanvas.ui.screens.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.core.Durations
import app.suncanvas.core.TimeFormatter
import app.suncanvas.data.db.LocationEntity
import app.suncanvas.data.zone
import app.suncanvas.ui.AppViewModel
import app.suncanvas.ui.components.LabeledValue
import app.suncanvas.ui.components.SectionHeader
import app.suncanvas.ui.components.SunCard
import app.suncanvas.ui.theme.LocalSunColors
import app.suncanvas.ui.theme.Spacing
import app.suncanvas.solar.SolarDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class CalMode(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: AppViewModel) {
    val active by vm.active.collectAsStateWithLifecycle()
    val all by vm.locations.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = remember(settings) { vm.formatter() }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val loc = all.firstOrNull { it.id == selectedId } ?: active
    var mode by rememberSaveable { mutableStateOf(CalMode.DAY) }
    var dateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var compareEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var locMenu by remember { mutableStateOf(false) }

    if (loc == null) {
        Text("Choose a location first.", Modifier.padding(Spacing.xl))
        return
    }
    val zone = loc.zone
    val date = LocalDate.ofEpochDay(dateEpoch)
    LaunchedEffect(loc.id) {
        if (selectedId == null) dateEpoch = Instant.now().atZone(zone).toLocalDate().toEpochDay()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Calendar") },
            actions = {
                Box {
                    AssistChip(onClick = { locMenu = true }, label = { Text(loc.displayName) })
                    DropdownMenu(expanded = locMenu, onDismissRequest = { locMenu = false }) {
                        all.forEach { l -> DropdownMenuItem(text = { Text(l.headline) }, onClick = { selectedId = l.id; locMenu = false }) }
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CalMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(selected = mode == m, onClick = { mode = m }, shape = SegmentedButtonDefaults.itemShape(i, CalMode.entries.size)) { Text(m.label) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dateEpoch = step(date, mode, -1).toEpochDay() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous") }
                Text(
                    when (mode) {
                        CalMode.DAY -> fmt.date(date) + ", ${date.year}"
                        CalMode.WEEK -> "Week of ${fmt.shortDate(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))}"
                        CalMode.MONTH -> date.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()) + " ${date.year}"
                        CalMode.YEAR -> "${date.year}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable(onClickLabel = "Pick a date") { pickFor = "main" }.padding(vertical = Spacing.sm),
                )
                IconButton(onClick = { dateEpoch = step(date, mode, 1).toEpochDay() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next") }
                IconButton(onClick = { dateEpoch = Instant.now().atZone(zone).toLocalDate().toEpochDay() }) { Icon(Icons.Rounded.Today, "Today") }
            }
            when (mode) {
                CalMode.DAY -> DayView(vm, loc, date, fmt, compareEpoch?.let(LocalDate::ofEpochDay), onCompare = { pickFor = "compare" }, onClearCompare = { compareEpoch = null })
                CalMode.WEEK -> WeekView(vm, loc, date, fmt) { dateEpoch = it.toEpochDay(); mode = CalMode.DAY }
                CalMode.MONTH -> MonthView(vm, loc, date, fmt) { dateEpoch = it.toEpochDay(); mode = CalMode.DAY }
                CalMode.YEAR -> YearView(vm, loc, date, fmt) { dateEpoch = it.toEpochDay(); mode = CalMode.DAY }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    pickFor?.let { target ->
        val initial = if (target == "compare") (compareEpoch ?: dateEpoch) else dateEpoch
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(initial).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickFor = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        if (target == "compare") compareEpoch = d else dateEpoch = d
                    }
                    pickFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickFor = null }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

private fun step(d: LocalDate, mode: CalMode, dir: Long): LocalDate = when (mode) {
    CalMode.DAY -> d.plusDays(dir)
    CalMode.WEEK -> d.plusWeeks(dir)
    CalMode.MONTH -> d.plusMonths(dir)
    CalMode.YEAR -> d.plusYears(dir)
}

@Composable
private fun DayView(vm: AppViewModel, loc: LocationEntity, date: LocalDate, fmt: TimeFormatter, compare: LocalDate?, onCompare: () -> Unit, onClearCompare: () -> Unit) {
    val z = loc.zone
    val day = remember(loc, date) { vm.solar.day(loc, date) }
    val prev = remember(loc, date) { vm.solar.day(loc, date.minusDays(1)) }
    val sun = LocalSunColors.current
    SunCard(Modifier.fillMaxWidth()) {
        Column {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("SUNRISE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.time(day.sunrise, z), style = MaterialTheme.typography.headlineMedium, color = sun.sunrise)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("SUNSET", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.time(day.sunset, z), style = MaterialTheme.typography.headlineMedium, color = sun.sunset)
                }
            }
            ElevationChart(vm, loc, date, Modifier.fillMaxWidth().height(160.dp).padding(vertical = Spacing.md))
            LabeledValue("Daylight", "${Durations.hm(day.daylight)} (${Durations.signedMinSec(day.daylight.minus(prev.daylight))})")
            LabeledValue("Solar noon", "${fmt.time(day.solarNoon, z)} · %.1f°".format(day.noonElevation))
            LabeledValue("Civil twilight", "${fmt.time(day.civilDawn, z, "None")} / ${fmt.time(day.civilDusk, z, "None")}")
            LabeledValue("Nautical twilight", "${fmt.time(day.nauticalDawn, z, "None")} / ${fmt.time(day.nauticalDusk, z, "None")}")
            LabeledValue("Astronomical twilight", "${fmt.time(day.astronomicalDawn, z, "None")} / ${fmt.time(day.astronomicalDusk, z, "None")}")
            LabeledValue("Golden hour (evening)", day.goldenHourEvening?.let { "${fmt.time(it.start, z)}–${fmt.time(it.end, z)}" } ?: "None")
            LabeledValue("Blue hour (evening)", day.blueHourEvening?.let { "${fmt.time(it.start, z)}–${fmt.time(it.end, z)}" } ?: "None")
        }
    }
    SectionHeader("Compare dates", action = {
        if (compare != null) TextButton(onClick = onClearCompare) { Text("Clear") }
        else TextButton(onClick = onCompare) { Icon(Icons.AutoMirrored.Rounded.CompareArrows, null); Text(" Pick a date") }
    })
    if (compare != null) {
        val other = remember(loc, compare) { vm.solar.day(loc, compare) }
        SunCard(Modifier.fillMaxWidth()) {
            Column {
                Row {
                    Text("", Modifier.weight(1.2f))
                    Text(fmt.shortDate(date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text(fmt.shortDate(compare), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                }
                CompareRow("Sunrise", fmt.time(day.sunrise, z), fmt.time(other.sunrise, z))
                CompareRow("Sunset", fmt.time(day.sunset, z), fmt.time(other.sunset, z))
                CompareRow("Daylight", Durations.hm(day.daylight), Durations.hm(other.daylight))
                CompareRow("Solar noon", fmt.time(day.solarNoon, z), fmt.time(other.solarNoon, z))
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                val diff = other.daylight.minus(day.daylight)
                Text(
                    "${fmt.shortDate(compare)} has ${Durations.hm(diff)} ${if (diff.isNegative) "less" else "more"} daylight.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1.2f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(a, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(b, Modifier.weight(1f), fontWeight = FontWeight.Medium)
    }
}

/** Sun elevation through the selected local day with horizon and twilight bands. */
@Composable
private fun ElevationChart(vm: AppViewModel, loc: LocationEntity, date: LocalDate, modifier: Modifier) {
    val sun = LocalSunColors.current
    val scheme = MaterialTheme.colorScheme
    val curve = remember(loc, date) { vm.solar.elevationCurve(loc, date, 145) }
    val maxE = 90.0
    val minE = minOf(-25.0, (curve.minOfOrNull { it.second } ?: -40.0) - 4.0)
    val now = Instant.now()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant)
    Canvas(modifier.clipToBounds().semantics { contentDescription = "Chart of the Sun's elevation through the day" }) {
        fun y(e: Double) = ((maxE - e) / (maxE - minE) * size.height).toFloat()
        drawRect(sun.twilight.copy(alpha = 0.10f), Offset(0f, y(0.0)), Size(size.width, y(-18.0) - y(0.0)))
        drawLine(scheme.outline, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        drawLine(scheme.outlineVariant, Offset(0f, y(-6.0)), Offset(size.width, y(-6.0)), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        val path = Path()
        curve.forEachIndexed { i, (_, e) ->
            val x = i / (curve.size - 1f) * size.width
            if (i == 0) path.moveTo(x, y(e)) else path.lineTo(x, y(e))
        }
        drawPath(path, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(sun.golden, sun.sunset, sun.blue), startY = y(60.0), endY = y(-20.0)),
            style = Stroke(2.5.dp.toPx()))
        for (h in listOf(6, 12, 18)) {
            val x = h / 24f * size.width
            drawLine(scheme.outlineVariant, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5.dp.toPx())
            drawText(measurer, "%02d:00".format(h), Offset(x + 2.dp.toPx(), size.height - 14.dp.toPx()), labelStyle)
        }
        if (now.atZone(loc.zone).toLocalDate() == date) {
            val start = date.atStartOfDay(loc.zone).toInstant()
            val len = Duration.between(start, date.plusDays(1).atStartOfDay(loc.zone).toInstant()).toMillis().toFloat()
            val x = Duration.between(start, now).toMillis() / len * size.width
            drawLine(scheme.primary, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
        }
    }
}

@Composable
private fun WeekView(vm: AppViewModel, loc: LocationEntity, date: LocalDate, fmt: TimeFormatter, onPick: (LocalDate) -> Unit) {
    val z = loc.zone
    val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val today = Instant.now().atZone(z).toLocalDate()
    val days = remember(loc, start) { vm.solar.range(loc, start, 7) }
    SunCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Column {
            Row(Modifier.padding(bottom = Spacing.sm)) {
                Text("Day", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                Text("Sunrise", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Sunset", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Daylight", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
            days.forEach { d ->
                val isToday = d.date == today
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(d.date) }
                        .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    Text(d.date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()) + " " + d.date.dayOfMonth, Modifier.weight(1.3f), fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    Text(fmt.time(d.sunrise, z), Modifier.weight(1f))
                    Text(fmt.time(d.sunset, z), Modifier.weight(1f))
                    Text(Durations.hm(d.daylight), Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
    val first = days.first()
    val last = days.last()
    Text(
        "Daylight ${if (last.daylight >= first.daylight) "grows" else "shrinks"} by ${Durations.hm(last.daylight.minus(first.daylight))} this week.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md),
    )
}

@Composable
private fun MonthView(vm: AppViewModel, loc: LocationEntity, date: LocalDate, fmt: TimeFormatter, onPick: (LocalDate) -> Unit) {
    val z = loc.zone
    val first = date.withDayOfMonth(1)
    val today = Instant.now().atZone(z).toLocalDate()
    // A month is ~30 day calculations (a few milliseconds), so it is computed directly.
    val days = remember(loc, first) { vm.solar.range(loc, first, first.lengthOfMonth()) }
    val lead = (first.dayOfWeek.value - 1)
    val sun = LocalSunColors.current
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.values().forEach { d ->
            Text(d.getDisplayName(JTextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val cells = List(lead) { null } + days
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            week.forEach { d ->
                if (d == null) Spacer(Modifier.weight(1f))
                else {
                    val isToday = d.date == today
                    val isSel = d.date == date
                    Column(
                        Modifier.weight(1f).aspectRatio(0.62f)
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                            .then(if (isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                            .clickable { onPick(d.date) }
                            .padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Text(compactTime(fmt, d.sunrise, z), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = sun.sunrise, maxLines = 1)
                        Text(compactTime(fmt, d.sunset, z), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = sun.sunset, maxLines = 1)
                    }
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (days.isNotEmpty()) {
        val earliest = days.filter { it.sunrise != null }.minByOrNull { it.sunrise!!.atZone(z).toLocalTime() }
        val latest = days.filter { it.sunset != null }.maxByOrNull { it.sunset!!.atZone(z).toLocalTime() }
        SectionHeader("This month")
        SunCard(Modifier.fillMaxWidth()) {
            Column {
                earliest?.let { LabeledValue("Earliest sunrise", "${fmt.time(it.sunrise, z)} · ${fmt.shortDate(it.date)}") }
                latest?.let { LabeledValue("Latest sunset", "${fmt.time(it.sunset, z)} · ${fmt.shortDate(it.date)}") }
                LabeledValue("Daylight change", Durations.signedMinSec(days.last().daylight.minus(days.first().daylight)))
            }
        }
    }
}

private fun compactTime(fmt: TimeFormatter, i: Instant?, z: ZoneId): String =
    fmt.time(i, z, "–").replace(" AM", "a").replace(" PM", "p")

@Composable
private fun YearView(vm: AppViewModel, loc: LocationEntity, date: LocalDate, fmt: TimeFormatter, onPick: (LocalDate) -> Unit) {
    val z = loc.zone
    val year = date.year
    var days by remember(loc, year) { mutableStateOf<List<SolarDay>>(emptyList()) }
    LaunchedEffect(loc, year) {
        days = withContext(Dispatchers.Default) {
            val start = LocalDate.of(year, 1, 1)
            vm.solar.range(loc, start, start.lengthOfYear())
        }
    }
    if (days.isEmpty()) { Text("Calculating…", Modifier.padding(Spacing.lg)); return }
    SunCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Column {
            Text("Sunrise, sunset & daylight in $year", style = MaterialTheme.typography.titleSmall)
            YearChart(days, z, date, Modifier.fillMaxWidth().height(260.dp).padding(top = Spacing.sm), onPick)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                Legend(LocalSunColors.current.sunrise.copy(alpha = 0.5f), "Daylight")
                Legend(LocalSunColors.current.twilight.copy(alpha = 0.35f), "Civil twilight")
                Legend(MaterialTheme.colorScheme.tertiary, "Day length")
            }
        }
    }
    val longest = days.maxBy { it.daylight }
    val shortest = days.minBy { it.daylight }
    SectionHeader("Year at a glance")
    SunCard(Modifier.fillMaxWidth()) {
        Column {
            LabeledValue("Longest day", "${fmt.shortDate(longest.date)} · ${Durations.hm(longest.daylight)}")
            LabeledValue("Shortest day", "${fmt.shortDate(shortest.date)} · ${Durations.hm(shortest.daylight)}")
            days.filter { it.sunrise != null }.minByOrNull { it.sunrise!!.atZone(z).toLocalTime() }?.let { LabeledValue("Earliest sunrise", "${fmt.time(it.sunrise, z)} · ${fmt.shortDate(it.date)}") }
            days.filter { it.sunset != null }.maxByOrNull { it.sunset!!.atZone(z).toLocalTime() }?.let { LabeledValue("Latest sunset", "${fmt.time(it.sunset, z)} · ${fmt.shortDate(it.date)}") }
            val polar = days.count { it.dayType != app.suncanvas.solar.DayType.NORMAL }
            if (polar > 0) LabeledValue("Polar days/nights", "$polar days")
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(12.dp).height(12.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(" $label", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun YearChart(days: List<SolarDay>, z: ZoneId, selected: LocalDate, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    val sun = LocalSunColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = scheme.onSurfaceVariant)
    val density = LocalDensity.current
    val leftPad = with(density) { 30.dp.toPx() }
    fun minutes(i: Instant?): Float? = i?.atZone(z)?.toLocalTime()?.let { it.hour * 60f + it.minute + it.second / 60f }
    Canvas(
        modifier
            .semantics { contentDescription = "Year chart of sunrise, sunset and daylight. Tap a day to open it." }
            .pointerInput(days) {
                detectTapGestures { p ->
                    val w = size.width - leftPad
                    val idx = (((p.x - leftPad) / w) * (days.size - 1)).toInt().coerceIn(0, days.lastIndex)
                    onPick(days[idx].date)
                }
            },
    ) {
        val w = size.width - leftPad
        val h = size.height - 16.dp.toPx()
        fun x(i: Int) = leftPad + i / (days.size - 1f) * w
        fun y(min: Float) = min / 1440f * h
        // Hour grid.
        for (hh in 0..24 step 6) {
            drawLine(scheme.outlineVariant, Offset(leftPad, y(hh * 60f)), Offset(size.width, y(hh * 60f)), strokeWidth = 0.5.dp.toPx())
            drawText(measurer, "%02d".format(hh % 24), Offset(0f, (y(hh * 60f) - 6.dp.toPx()).coerceAtLeast(0f)), labelStyle)
        }
        // Twilight and daylight bands.
        days.forEachIndexed { i, d ->
            val x0 = x(i)
            val bw = w / days.size + 0.6f
            val cd = minutes(d.civilDawn)
            val cu = minutes(d.civilDusk)
            val sr = minutes(d.sunrise)
            val ss = minutes(d.sunset)
            if (cd != null && cu != null && cu > cd) drawRect(sun.twilight.copy(alpha = 0.30f), Offset(x0, y(cd)), Size(bw, y(cu) - y(cd)))
            when {
                sr != null && ss != null && ss > sr -> drawRect(sun.sunrise.copy(alpha = 0.55f), Offset(x0, y(sr)), Size(bw, y(ss) - y(sr)))
                d.dayType == app.suncanvas.solar.DayType.POLAR_DAY -> drawRect(sun.sunrise.copy(alpha = 0.55f), Offset(x0, 0f), Size(bw, h))
                else -> Unit
            }
        }
        // Day length curve (0–24 h scaled to the same axis).
        val path = Path()
        days.forEachIndexed { i, d ->
            val yy = h - d.daylight.toMinutes() / 1440f * h
            if (i == 0) path.moveTo(x(i), yy) else path.lineTo(x(i), yy)
        }
        drawPath(path, scheme.tertiary, style = Stroke(2.dp.toPx()))
        // Month ticks.
        days.forEachIndexed { i, d ->
            if (d.date.dayOfMonth == 1) {
                drawLine(scheme.outlineVariant, Offset(x(i), h), Offset(x(i), h + 4.dp.toPx()))
                drawText(measurer, d.date.month.getDisplayName(JTextStyle.NARROW, Locale.getDefault()), Offset(x(i) + 2.dp.toPx(), h + 2.dp.toPx()), labelStyle)
            }
        }
        val sel = days.indexOfFirst { it.date == selected }
        if (sel >= 0) drawLine(scheme.primary, Offset(x(sel), 0f), Offset(x(sel), h), strokeWidth = 1.5.dp.toPx())
    }
}
