package com.suncanvas.app.ui.screens.cities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suncanvas.app.container
import com.suncanvas.app.core.TimeFormatter
import com.suncanvas.app.location.ResolvedPlace
import com.suncanvas.app.ui.AppViewModel
import com.suncanvas.app.ui.components.LabeledValue
import com.suncanvas.app.ui.components.SunCard
import com.suncanvas.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatesScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val c = LocalContext.current.container
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = remember(settings) { vm.formatter() }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf<ResolvedPlace?>(null) }
    var zoneOverride by remember { mutableStateOf<String?>(null) }
    var pickZone by remember { mutableStateOf(false) }

    val lat = parseCoordinate(latText, true)
    val lon = parseCoordinate(lonText, false)
    val latError = latText.isNotBlank() && lat == null
    val lonError = lonText.isNotBlank() && lon == null

    LaunchedEffect(lat, lon) {
        place = null
        if (lat != null && lon != null) {
            delay(250)
            val p = c.locations.resolvePlace(lat, lon)
            place = p
            if (!nameEdited) name = p.name
        }
    }
    val zoneId = zoneOverride ?: place?.timeZoneId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter coordinates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Decimal degrees. Use negative values (or S / W) for the southern and western hemispheres.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = latText, onValueChange = { latText = it }, label = { Text("Latitude (−90 to 90)") },
                isError = latError, supportingText = { if (latError) Text("Enter a latitude between −90 and 90") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = lonText, onValueChange = { lonText = it }, label = { Text("Longitude (−180 to 180)") },
                isError = lonError, supportingText = { if (lonError) Text("Enter a longitude between −180 and 180") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it; nameEdited = true }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val p = place
            if (lat != null && lon != null && p != null && zoneId != null) {
                val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))
                val day = remember(lat, lon, zoneId) { vm.solar.day(lat, lon, zone, Instant.now().atZone(zone).toLocalDate()) }
                SunCard(Modifier.fillMaxWidth()) {
                    Column {
                        LabeledValue("Time zone", "$zoneId (${TimeFormatter.utcOffset(zone)})")
                        if (p.nearestCity != null) LabeledValue("Nearest city", "${p.nearestCity.name}, ${p.nearestCity.countryName} · ${p.distanceKm?.toInt()} km")
                        LabeledValue("Sunrise today", fmt.time(day.sunrise, zone))
                        LabeledValue("Sunset today", fmt.time(day.sunset, zone))
                        if (!p.confident && zoneOverride == null) {
                            Text("Far from any town — using the nautical time zone. Change it if you know the local zone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedButton(onClick = { pickZone = true }) { Text("Change time zone") }
                    }
                }
            }
            Button(
                onClick = {
                    val pp = place ?: return@Button
                    vm.addManual(
                        name = name.ifBlank { pp.name }, lat = lat!!, lon = lon!!, zone = zoneId ?: pp.timeZoneId,
                        admin1 = pp.admin1, cc = pp.countryCode, country = pp.countryName, makeActive = true,
                    ) { onDone() }
                },
                enabled = lat != null && lon != null && place != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save location") }
        }
    }

    if (pickZone) ZonePickerDialog(current = zoneId, onDismiss = { pickZone = false }, onPick = { zoneOverride = it; pickZone = false })
}

/** Accepts "32.7767", "-96.79", "32.7767 N", "96.797W". */
fun parseCoordinate(text: String, isLatitude: Boolean): Double? {
    val t = text.trim().uppercase().replace(",", ".").replace("°", "")
    if (t.isEmpty()) return null
    val sign = when {
        t.endsWith("S") || t.endsWith("W") -> -1
        else -> 1
    }
    val num = t.trimEnd('N', 'S', 'E', 'W', ' ').toDoubleOrNull() ?: return null
    val v = num * sign
    val max = if (isLatitude) 90.0 else 180.0
    return v.takeIf { it in -max..max }
}

@Composable
fun ZonePickerDialog(current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val zones = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("SystemV") }.sorted() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        text = {
            Column {
                OutlinedTextField(value = filter, onValueChange = { filter = it }, placeholder = { Text("Search, e.g. Chicago") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(zones.filter { it.contains(filter.replace(' ', '_'), ignoreCase = true) }) { z ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(z) }.padding(vertical = 10.dp)) {
                            Text(z, Modifier.weight(1f), style = if (z == current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
                            Text(TimeFormatter.utcOffset(ZoneId.of(z)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
