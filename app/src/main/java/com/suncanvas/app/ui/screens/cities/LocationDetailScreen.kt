package com.suncanvas.app.ui.screens.cities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suncanvas.app.core.Durations
import com.suncanvas.app.core.TimeFormatter
import com.suncanvas.app.core.formatCoordinate
import com.suncanvas.app.data.db.LocationSource
import com.suncanvas.app.ui.AppViewModel
import com.suncanvas.app.ui.components.LabeledValue
import com.suncanvas.app.ui.components.SectionHeader
import com.suncanvas.app.ui.components.SunCard
import com.suncanvas.app.ui.rememberNow
import com.suncanvas.app.ui.theme.LocalSunColors
import com.suncanvas.app.ui.theme.Spacing
import com.suncanvas.solar.SolarEventType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val all by vm.locations.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loc = all.firstOrNull { it.id == id }
    val now = rememberNow(30_000L)
    val fmt = remember(settings) { vm.formatter() }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sun = LocalSunColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loc?.headline ?: "Location") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (loc != null) {
                        IconButton(onClick = { vm.setFavorite(loc.id, !loc.isFavorite) }) {
                            Icon(if (loc.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = if (loc.isFavorite) "Remove favorite" else "Mark favorite", tint = if (loc.isFavorite) sun.golden else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { rename = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Rename") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        if (loc == null) { Text("This location was removed.", Modifier.padding(padding).padding(Spacing.lg)); return@Scaffold }
        val snap = remember(loc, now.epochSecond / 60) { vm.solar.snapshot(loc, now) }
        val z = snap.zone
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
                FilledTonalButton(onClick = { vm.setActive(loc.id); onBack() }) { Text("Show on Home") }
                OutlinedButton(onClick = { vm.setDefault(loc.id) }, enabled = !loc.isDefault) {
                    Icon(Icons.Rounded.Home, null); Text(if (loc.isDefault) " Default" else " Make default")
                }
            }
            SectionHeader("Location")
            SunCard(Modifier.fillMaxWidth()) {
                Column {
                    LabeledValue("Name", loc.name)
                    if (!loc.customLabel.isNullOrBlank()) LabeledValue("Label", loc.customLabel)
                    if (loc.subtitle.isNotBlank()) LabeledValue("Region", loc.subtitle)
                    LabeledValue("Latitude", formatCoordinate(loc.latitude, true))
                    LabeledValue("Longitude", formatCoordinate(loc.longitude, false))
                    LabeledValue("Time zone", loc.timeZoneId)
                    LabeledValue("UTC offset now", TimeFormatter.utcOffset(z, now) + " · " + TimeFormatter.zoneAbbreviation(z, now))
                    LabeledValue("Local time", fmt.time(now, z) + " · " + fmt.date(now, z))
                    LabeledValue("Source", when (loc.source) {
                        LocationSource.DEVICE -> "Device location"
                        LocationSource.CITY -> "Offline city database"
                        LocationSource.ONLINE -> "Online geocoding"
                        LocationSource.MANUAL -> "Entered manually"
                    })
                }
            }
            SectionHeader("Today")
            SunCard(Modifier.fillMaxWidth()) {
                Column {
                    LabeledValue("Daylight", "${Durations.hm(snap.today.daylight)} (${Durations.signedMinSec(snap.daylightChange)} vs yesterday)")
                    snap.today.events.forEach { e ->
                        val label = when (e.type) {
                            SolarEventType.ASTRONOMICAL_DAWN -> "Astronomical dawn"
                            SolarEventType.NAUTICAL_DAWN -> "Nautical dawn"
                            SolarEventType.CIVIL_DAWN -> "Civil dawn"
                            SolarEventType.SUNRISE -> "Sunrise"
                            SolarEventType.GOLDEN_HOUR_MORNING_END -> "Morning golden hour ends"
                            SolarEventType.SOLAR_NOON -> "Solar noon"
                            SolarEventType.GOLDEN_HOUR_EVENING_START -> "Evening golden hour starts"
                            SolarEventType.SUNSET -> "Sunset"
                            SolarEventType.CIVIL_DUSK -> "Civil dusk"
                            SolarEventType.NAUTICAL_DUSK -> "Nautical dusk"
                            SolarEventType.ASTRONOMICAL_DUSK -> "Astronomical dusk"
                            SolarEventType.SOLAR_MIDNIGHT -> "Solar midnight"
                        }
                        val color = when (e.type) {
                            SolarEventType.SUNRISE -> sun.sunrise
                            SolarEventType.SUNSET -> sun.sunset
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        LabeledValue(label, fmt.time(e.instant, z), valueColor = color)
                    }
                    LabeledValue("Sun elevation now", "%.1f°".format(snap.position.elevation))
                    LabeledValue("Sun azimuth now", "%.0f°".format(snap.position.azimuth))
                }
            }
            Text(
                "Times are computed on the device with the NOAA solar algorithm in the location's own time zone (IANA tz database), " +
                    "including daylight saving time. Expect agreement with NOAA / USNO within about a minute.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.lg),
            )
        }
    }

    if (rename && loc != null) {
        var text by remember { mutableStateOf(loc.customLabel ?: "") }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Custom label") },
            text = {
                Column {
                    Text("e.g. Home, Office, Parents' City. Leave empty to use the city name.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                }
            },
            confirmButton = { TextButton(onClick = { vm.rename(loc.id, text); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && loc != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${loc.displayName}?") },
            text = { Text("Widgets showing this place will switch to your default location.") },
            confirmButton = { TextButton(onClick = { vm.delete(loc.id); confirmDelete = false; onBack() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
