package app.suncanvas.ui.screens.cities

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditLocation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.core.Durations
import app.suncanvas.core.TimeFormatter
import app.suncanvas.data.ClockStyle
import app.suncanvas.data.db.LocationEntity
import app.suncanvas.data.db.LocationSource
import app.suncanvas.data.zone
import app.suncanvas.ui.AppViewModel
import app.suncanvas.ui.components.AnalogClock
import app.suncanvas.ui.components.DayNightDot
import app.suncanvas.ui.components.EmptyState
import app.suncanvas.ui.components.SectionHeader
import app.suncanvas.ui.components.SunCard
import app.suncanvas.ui.components.SunGlyph
import app.suncanvas.ui.components.SunGlyphIcon
import app.suncanvas.ui.rememberNow
import app.suncanvas.ui.theme.LocalSunColors
import app.suncanvas.ui.theme.Spacing
import app.suncanvas.solar.NoaaSolarPosition
import app.suncanvas.solar.SolarAltitude
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitiesScreen(vm: AppViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit, onCoordinates: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(if (tab == 0) "My Cities" else "World Clock") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("My Cities") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("World Clock") })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add city") })
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) MyCities(vm, onAdd, onOpen, onCoordinates) else WorldClock(vm)
        }
    }
}

@Composable
private fun MyCities(vm: AppViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit, onCoordinates: () -> Unit) {
    val all by vm.locations.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = remember(settings) { vm.formatter() }
    val now = rememberNow(30_000L)
    var reorder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocationEntity?>(null) }
    val list = all.filter { it.inCityList }

    if (list.isEmpty()) {
        EmptyState(Icons.Rounded.Add, "Follow cities worldwide", "Add places to compare sunrise, sunset and local time.") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onAdd) { Text("Search cities") }
                TextButton(onClick = onCoordinates) { Text("Coordinates") }
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${list.size} places", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { reorder = !reorder }) {
                    Icon(if (reorder) Icons.Rounded.Check else Icons.Rounded.SwapVert, null, Modifier.size(18.dp))
                    Text(if (reorder) " Done" else " Reorder")
                }
            }
        }
        itemsIndexed(list, key = { _, it -> it.id }) { index, loc ->
            CityCard(
                loc = loc, isActive = active?.id == loc.id, now = now, vm = vm, fmt = fmt, reorder = reorder,
                canUp = index > 0, canDown = index < list.lastIndex,
                onMove = { delta ->
                    val ids = list.map { it.id }.toMutableList()
                    val i = ids.indexOf(loc.id)
                    val j = (i + delta).coerceIn(0, ids.lastIndex)
                    ids.removeAt(i); ids.add(j, loc.id)
                    vm.reorder(ids)
                },
                onOpen = { onOpen(loc.id) },
                onRename = { renameTarget = loc },
            )
        }
    }
    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.customLabel ?: "") }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Label for ${target.name}") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("Home, Office, Parents' City…") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(target.id, text); renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CityCard(
    loc: LocationEntity,
    isActive: Boolean,
    now: Instant,
    vm: AppViewModel,
    fmt: TimeFormatter,
    reorder: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onMove: (Int) -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
) {
    val sun = LocalSunColors.current
    val snap = remember(loc, now.epochSecond / 60) { vm.solar.snapshot(loc, now) }
    val z = snap.zone
    var menu by remember { mutableStateOf(false) }
    SunCard(Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", onClick = onOpen), contentPadding = Spacing.md) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayNightDot(snap.isDay)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (loc.source == LocationSource.DEVICE) Icon(Icons.Rounded.MyLocation, "Current location", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        if (loc.isFavorite) Icon(Icons.Rounded.Star, "Favorite", Modifier.size(14.dp), tint = sun.golden)
                        if (loc.isDefault) Icon(Icons.Rounded.Home, "Default location", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(" " + loc.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        listOf(if (loc.customLabel.isNullOrBlank()) "" else loc.name, loc.countryName.ifBlank { loc.subtitle }).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt.time(now, z), style = MaterialTheme.typography.titleLarge)
                    Text("${fmt.shortDate(snap.localDate)} · ${TimeFormatter.zoneAbbreviation(z, now)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (reorder) {
                    Column {
                        IconButton(onClick = { onMove(-1) }, enabled = canUp) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { onMove(1) }, enabled = canDown) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    }
                } else {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (isActive) "Shown on Home" else "Show on Home") }, enabled = !isActive, onClick = { menu = false; vm.setActive(loc.id) })
                            DropdownMenuItem(text = { Text(if (loc.isFavorite) "Remove favorite" else "Set as favorite") }, onClick = { menu = false; vm.setFavorite(loc.id, !loc.isFavorite) })
                            DropdownMenuItem(text = { Text("Make default") }, enabled = !loc.isDefault, onClick = { menu = false; vm.setDefault(loc.id) })
                            DropdownMenuItem(text = { Text("Rename / label") }, leadingIcon = { Icon(Icons.Rounded.EditLocation, null) }, onClick = { menu = false; onRename() })
                            DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; vm.delete(loc.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunGlyphIcon(SunGlyph.SUNRISE, sun.sunrise, size = 20.dp)
                Text(" " + fmt.time(snap.today.sunrise, z, "—"), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(Spacing.lg))
                SunGlyphIcon(SunGlyph.SUNSET, sun.sunset, size = 20.dp)
                Text(" " + fmt.time(snap.today.sunset, z, "—"), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text("${Durations.hm(snap.today.daylight)} daylight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (snap.isDay) "Daytime · sunset ${snap.next.nextSunset?.let { Durations.countdown(now, it) } ?: "—"}"
                else "Night · sunrise ${snap.next.nextSunrise?.let { Durations.countdown(now, it) } ?: "—"}",
                style = MaterialTheme.typography.labelMedium, color = if (snap.isDay) sun.sunrise else sun.blue,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldClock(vm: AppViewModel) {
    val all by vm.locations.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = remember(settings) { vm.formatter() }
    val now = rememberNow(1000L)
    val hidden = settings?.worldClockHidden ?: emptySet()
    val style = settings?.worldClockStyle ?: ClockStyle.DIGITAL
    val cities = all.filter { it.inCityList && it.id !in hidden }
    var choose by remember { mutableStateOf(false) }
    var offsetHours by remember { mutableFloatStateOf(0f) }
    val reference = active ?: cities.firstOrNull()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SegmentedButton(selected = style == ClockStyle.DIGITAL, onClick = { vm.setWorldClockStyle(ClockStyle.DIGITAL) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Digital") }
                    SegmentedButton(selected = style == ClockStyle.ANALOG, onClick = { vm.setWorldClockStyle(ClockStyle.ANALOG) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Analog") }
                }
                IconButton(onClick = { choose = true }) { Icon(Icons.Rounded.Tune, contentDescription = "Choose cities") }
            }
        }
        if (cities.isEmpty()) {
            item { Text("Add cities in My Cities to compare their time here.", Modifier.padding(vertical = Spacing.xl), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val rows = cities.chunked(2)
        rows.forEach { pair ->
            item {
                Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    pair.forEach { loc ->
                        val snap = remember(loc, now.epochSecond / 60) { vm.solar.snapshot(loc, now) }
                        SunCard(Modifier.weight(1f), contentPadding = Spacing.md) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DayNightDot(snap.isDay, size = 8.dp)
                                    Text(" " + loc.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (style == ClockStyle.ANALOG) {
                                    AnalogClock(now, loc.zone, snap.isDay, Modifier.padding(vertical = Spacing.sm).size(110.dp))
                                    Text(fmt.time(now, loc.zone), style = MaterialTheme.typography.titleMedium)
                                } else {
                                    Text(fmt.time(now, loc.zone), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = Spacing.sm))
                                }
                                Text(relativeOffset(reference, loc, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fmt.shortDate(snap.localDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (cities.isNotEmpty()) {
            item { SectionHeader("Compare across the day") }
            item {
                val at = now.plusSeconds((offsetHours * 3600).toLong())
                SunCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            if (offsetHours == 0f) "Now" else "%+.1f h from now".format(offsetHours),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Slider(value = offsetHours, onValueChange = { offsetHours = (it * 2).toInt() / 2f }, valueRange = -12f..12f,
                            modifier = Modifier.semantics { contentDescription = "Shift comparison time" })
                        cities.forEach { loc -> TimelineRow(loc, now, at, fmt) }
                        Text("Bars show day, twilight and night across ±12 hours for each city; the line marks the selected moment.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                    }
                }
            }
        }
    }

    if (choose) {
        AlertDialog(
            onDismissRequest = { choose = false },
            title = { Text("Cities in World Clock") },
            text = {
                Column {
                    all.filter { it.inCityList }.forEach { loc ->
                        Row(Modifier.fillMaxWidth().clickable {
                            vm.setWorldClockHidden(if (loc.id in hidden) hidden - loc.id else hidden + loc.id)
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = loc.id !in hidden, onCheckedChange = null)
                            Text(loc.displayName)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choose = false }) { Text("Done") } },
        )
    }
}

private fun relativeOffset(ref: LocationEntity?, loc: LocationEntity, now: Instant): String {
    if (ref == null || ref.id == loc.id) return TimeFormatter.utcOffset(loc.zone, now)
    val diff = loc.zone.rules.getOffset(now).totalSeconds - ref.zone.rules.getOffset(now).totalSeconds
    if (diff == 0) return "Same time as ${ref.displayName}"
    val h = diff / 3600
    val m = Math.abs(diff % 3600) / 60
    val sign = if (diff > 0) "+" else "−"
    return "$sign${Math.abs(h)}h${if (m > 0) " ${m}m" else ""} vs ${ref.displayName}"
}

@Composable
private fun TimelineRow(loc: LocationEntity, now: Instant, at: Instant, fmt: TimeFormatter) {
    val sun = LocalSunColors.current
    val night = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurface
    val samples = remember(loc, now.epochSecond / 600) {
        val start = now.minus(Duration.ofHours(12)).toEpochMilli()
        FloatArray(97) { i -> NoaaSolarPosition.geometricElevation(start + i * 15 * 60_000L, loc.latitude, loc.longitude).toFloat() }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(96.dp)) {
            Text(loc.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmt.time(at, loc.zone) + " " + fmt.shortDate(at.atZone(loc.zone).toLocalDate()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.weight(1f).height(22.dp).semantics { contentDescription = "Day and night timeline for ${loc.displayName}" }) {
            val segW = size.width / (samples.size - 1)
            samples.forEachIndexed { i, e ->
                if (i == samples.lastIndex) return@forEachIndexed
                val c = when {
                    e >= SolarAltitude.GOLDEN_UPPER -> sun.golden
                    e >= SolarAltitude.HORIZON -> sun.sunrise.copy(alpha = 0.8f)
                    e >= SolarAltitude.CIVIL -> sun.sunset.copy(alpha = 0.6f)
                    e >= SolarAltitude.ASTRONOMICAL -> sun.twilight.copy(alpha = 0.45f)
                    else -> night
                }
                drawRect(c, Offset(i * segW, 0f), Size(segW + 0.5f, size.height))
            }
            val frac = (Duration.between(now.minus(Duration.ofHours(12)), at).toMillis() / (24 * 3600_000f)).coerceIn(0f, 1f)
            drawRoundRect(marker, Offset(frac * size.width - 1.5.dp.toPx(), -2f), Size(3.dp.toPx(), size.height + 4f), CornerRadius(2f))
        }
    }
}
