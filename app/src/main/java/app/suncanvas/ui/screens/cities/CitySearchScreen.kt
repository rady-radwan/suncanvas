package app.suncanvas.ui.screens.cities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.EditLocation
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.container
import app.suncanvas.core.TimeFormatter
import app.suncanvas.data.cities.City
import app.suncanvas.location.LocationResult
import app.suncanvas.ui.AppViewModel
import app.suncanvas.ui.components.SectionHeader
import app.suncanvas.ui.components.rememberLocationRefresher
import app.suncanvas.ui.rememberNow
import app.suncanvas.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * mode = "add" (follow a new city), "select" (add and show on Home), "onboarding" (first run:
 * becomes the default location).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySearchScreen(vm: AppViewModel, mode: String, onBack: () -> Unit, onDone: () -> Unit, onCoordinates: () -> Unit = {}) {
    val context = LocalContext.current
    val c = context.container
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<List<City>>(emptyList()) }
    var online by remember { mutableStateOf<List<City>>(emptyList()) }
    var onlineState by remember { mutableStateOf("") }
    var popular by remember { mutableStateOf<List<City>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val now = rememberNow(30_000L)
    val fmt = remember(settings) { vm.formatter() }
    val detect = rememberLocationRefresher(vm) { if (it is LocationResult.Success) onDone() }

    LaunchedEffect(Unit) {
        popular = c.cities.popular()
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(query) {
        delay(120)
        local = if (query.isBlank()) emptyList() else c.cities.search(query)
        online = emptyList()
        onlineState = ""
        if (settings?.onlineSearch == true && query.trim().length >= 3) {
            delay(650)
            onlineState = "loading"
            val res = c.geocoder.search(query)
            online = res.getOrNull().orEmpty().filter { o -> local.none { it.geonameId == o.geonameId } }
            onlineState = if (res.isFailure) "Offline — online search unavailable" else ""
        }
    }

    fun choose(city: City) {
        scope.launch {
            when (mode) {
                "onboarding" -> vm.addCity(city, makeActive = true, makeDefault = true) { onDone() }
                "select" -> vm.addCity(city, makeActive = true) { onDone() }
                else -> vm.addCity(city) { vm.message("${city.name} added"); onDone() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == "add") "Add a city" else "Choose a location") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).focusRequester(focus),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear") } },
                placeholder = { Text("City, e.g. Paris, Texas") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.medium,
            )
            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(onClick = detect, label = { Text("Use my location") }, leadingIcon = { Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp)) })
                AssistChip(onClick = onCoordinates, label = { Text("Coordinates") }, leadingIcon = { Icon(Icons.Rounded.EditLocation, null, Modifier.size(18.dp)) })
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isBlank()) {
                    item { SectionHeader("Popular", Modifier.padding(horizontal = Spacing.lg)) }
                    items(popular, key = { "p" + it.geonameId }) { CityRow(it, now, fmt) { choose(it) } }
                } else {
                    if (local.isEmpty() && onlineState != "loading" && online.isEmpty()) {
                        item {
                            Text(
                                if (settings?.onlineSearch == true) "No offline match. Searching online…" else "No match in the offline database. Turn on online search in Settings for more places.",
                                Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(local, key = { "l" + it.geonameId }) { CityRow(it, now, fmt) { choose(it) } }
                    if (onlineState == "loading") item {
                        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(Spacing.sm)); Text("Searching online…")
                        }
                    } else if (onlineState.isNotEmpty()) item { Text(onlineState, Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (online.isNotEmpty()) {
                        item { SectionHeader("More places (online)", Modifier.padding(horizontal = Spacing.lg)) }
                        items(online, key = { "o" + it.geonameId + it.latitude }) { CityRow(it, now, fmt) { choose(it) } }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                    ListItem(
                        headlineContent = { Text("Enter coordinates instead") },
                        leadingContent = { Icon(Icons.Rounded.EditLocation, null) },
                        modifier = Modifier.clickable(onClick = onCoordinates),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    Text(
                        "City data © GeoNames (CC BY 4.0). Online search: Open-Meteo — only your search text is sent.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
fun CityRow(city: City, now: java.time.Instant, fmt: TimeFormatter, onClick: () -> Unit) {
    val zone = runCatching { ZoneId.of(city.timeZoneId) }.getOrNull()
    ListItem(
        headlineContent = { Text(city.name) },
        supportingContent = { Text(city.region.ifBlank { city.countryName }) },
        leadingContent = { Icon(if (city.fromOnline) Icons.Rounded.Cloud else Icons.Rounded.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            if (zone != null) Column(horizontalAlignment = Alignment.End) {
                Text(fmt.time(now, zone), style = MaterialTheme.typography.titleSmall)
                Text(TimeFormatter.zoneAbbreviation(zone, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}
