package app.suncanvas.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.BuildConfig
import app.suncanvas.container
import app.suncanvas.core.DateStyle
import app.suncanvas.data.ThemeMode
import app.suncanvas.data.TimeFormatPref
import app.suncanvas.data.db.ReminderEntity
import app.suncanvas.data.db.ReminderEvent
import app.suncanvas.notify.ReminderNotifications
import app.suncanvas.notify.ReminderScheduler
import app.suncanvas.ui.AppViewModel
import app.suncanvas.ui.components.SectionHeader
import app.suncanvas.ui.components.rememberLocationRefresher
import app.suncanvas.ui.theme.Spacing
import app.suncanvas.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onReminders: () -> Unit, onAbout: () -> Unit, onPickDefault: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val settings by vm.settings.collectAsStateWithLifecycle()
    val all by vm.locations.collectAsStateWithLifecycle()
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val refresh = rememberLocationRefresher(vm)
    var dialog by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.backup.export(uri) }
                .onSuccess { vm.message("Backup saved") }
                .onFailure { vm.message("Export failed: ${it.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.backup.import(uri) }
                .onSuccess { vm.message("Imported ${it.locations} places, ${it.designs} widget designs, ${it.reminders} reminders"); WidgetUpdater.requestUpdateAll(context) }
                .onFailure { vm.message("Import failed: ${it.message}") }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Group("Appearance")
            Item(Icons.Rounded.Brightness6, "Theme", s.themeMode.label) { dialog = "theme" }
            Item(Icons.Rounded.Schedule, "Time format", s.timeFormat.label) { dialog = "time" }
            Item(Icons.Rounded.CalendarToday, "Date format", s.dateStyle.label) { dialog = "date" }
            ToggleItem(Icons.Rounded.Timer, "Show seconds on Home", "Larger clock with seconds", s.showSeconds) { vm.setShowSeconds(it) }

            Group("Location")
            Item(Icons.Rounded.MyLocation, "Detect my location now", "One-time foreground request. Never in the background.", onClick = refresh)
            Item(Icons.Rounded.Home, "Default location", all.firstOrNull { it.isDefault }?.headline ?: "Not set", onClick = onPickDefault)
            ToggleItem(Icons.Rounded.Cloud, "Online city search", "Find places beyond the offline database. Sends only the search text.", s.onlineSearch) { vm.setOnlineSearch(it) }

            Group("Reminders")
            Item(Icons.Rounded.Notifications, "Sunrise, sunset & golden hour reminders", if (s.notificationsEnabled) "On" else "Off", onClick = onReminders)

            Group("Widgets")
            Item(Icons.Rounded.Refresh, "Background refresh", "Every ${s.widgetRefreshMinutes} minutes (clocks are always live)") { dialog = "refresh" }
            Item(Icons.Rounded.Refresh, "Refresh widgets now", "Re-draw all home-screen widgets") {
                WidgetUpdater.requestUpdateAll(context); vm.message("Widgets refreshed")
            }

            Group("Data")
            Item(Icons.Rounded.Upload, "Export backup", "Saved cities, widget designs, reminders and preferences") {
                exporter.launch("${BuildConfig.BRAND_NAME.lowercase()}-backup-${LocalDate.now()}.json")
            }
            Item(Icons.Rounded.Download, "Import backup", "Merge a backup file into this device") { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
            Item(Icons.Rounded.Delete, "Clear calculation cache", "Cached results are recomputed on demand") {
                scope.launch { c.solar.pruneCache(); vm.message("Cache cleared") }
            }

            Group("About")
            Item(Icons.Rounded.Info, "About ${BuildConfig.BRAND_NAME}", "Version ${BuildConfig.VERSION_NAME} · accuracy, privacy and licenses", onClick = onAbout)
        }
    }

    when (dialog) {
        "theme" -> ChoiceDialog("Theme", ThemeMode.entries, s.themeMode, { it.label }, { vm.setTheme(it) }) { dialog = null }
        "time" -> ChoiceDialog("Time format", TimeFormatPref.entries, s.timeFormat, { it.label }, { vm.setTimeFormat(it) }) { dialog = null }
        "date" -> ChoiceDialog("Date format", DateStyle.entries, s.dateStyle, { it.label }, { vm.setDateStyle(it) }) { dialog = null }
        "refresh" -> ChoiceDialog("Widget refresh", listOf(15, 30, 60, 120), s.widgetRefreshMinutes, { "Every $it minutes" + if (it == 15) " (most accurate)" else if (it >= 60) " (best battery)" else "" }, { vm.setWidgetRefresh(it) }) { dialog = null }
    }
}

@Composable
private fun Group(title: String) = SectionHeader(title, Modifier.padding(horizontal = Spacing.lg))

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun ToggleItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = o == selected, role = Role.RadioButton) { onSelect(o); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = null)
                        Text(label(o), Modifier.padding(start = Spacing.md))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val settings by vm.settings.collectAsStateWithLifecycle()
    val reminders by c.database.reminders().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val enabled = settings?.notificationsEnabled == true
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            c.settings.setNotificationsEnabled(granted)
            if (granted && c.database.reminders().getAll().isEmpty()) {
                c.database.reminders().insert(ReminderEntity(event = ReminderEvent.SUNSET, offsetMinutes = -30))
                c.database.reminders().insert(ReminderEntity(event = ReminderEvent.SUNRISE, offsetMinutes = -15))
            }
            c.reminderScheduler.rescheduleAll()
            if (!granted) vm.message("Notifications are blocked — allow them in system settings")
        }
    }

    fun setMaster(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33 && !ReminderNotifications.canPost(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else scope.launch {
            c.settings.setNotificationsEnabled(on)
            if (on && c.database.reminders().getAll().isEmpty()) {
                c.database.reminders().insert(ReminderEntity(event = ReminderEvent.SUNSET, offsetMinutes = -30))
                c.database.reminders().insert(ReminderEntity(event = ReminderEvent.SUNRISE, offsetMinutes = -15))
            }
            c.reminderScheduler.rescheduleAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { adding = true }, enabled = enabled) { Icon(Icons.Rounded.Add, "Add reminder") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ToggleItem(Icons.Rounded.Notifications, "Enable reminders", "Uses your default location's time zone", enabled) { setMaster(it) }
            if (enabled && !c.reminderScheduler.canUseExactAlarms()) {
                ListItem(
                    headlineContent = { Text("Allow precise timing") },
                    supportingContent = { Text("Without the \"Alarms & reminders\" permission, Android may deliver reminders up to 10 minutes late.") },
                    leadingContent = { Icon(Icons.Rounded.Alarm, null, tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= 31) runCatching {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:" + context.packageName)))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
            Group("Your reminders")
            if (reminders.isEmpty()) Text("No reminders yet. Tap + to add one.", Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
            reminders.forEach { r ->
                ListItem(
                    headlineContent = { Text(r.event.label) },
                    supportingContent = { Text(ReminderScheduler.describe(r)) },
                    leadingContent = { Icon(Icons.Rounded.Alarm, null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = r.enabled, onCheckedChange = { on ->
                                scope.launch { c.database.reminders().update(r.copy(enabled = on)); c.reminderScheduler.rescheduleAll() }
                            }, enabled = enabled)
                            IconButton(onClick = { scope.launch { c.reminderScheduler.cancel(r.id); c.database.reminders().delete(r.id) } }) { Icon(Icons.Rounded.Delete, "Delete reminder") }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
            Text(
                "Reminders are scheduled one at a time for the next occurrence only, so they use almost no battery. They follow the selected location's own time zone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg),
            )
        }
    }

    if (adding) {
        var event by remember { mutableStateOf(ReminderEvent.SUNSET) }
        var before by remember { mutableStateOf(true) }
        var minutes by remember { mutableStateOf("15") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("New reminder") },
            text = {
                Column {
                    ReminderEvent.entries.forEach { e ->
                        Row(Modifier.fillMaxWidth().selectable(selected = e == event, role = Role.RadioButton) { event = e }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = e == event, onClick = null); Text(e.label, Modifier.padding(start = Spacing.sm))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                        OutlinedTextField(value = minutes, onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(3) }, label = { Text("Minutes") }, singleLine = true, modifier = Modifier.weight(1f))
                        Row(Modifier.selectable(selected = before, role = Role.RadioButton) { before = true }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(before, null); Text("before") }
                        Row(Modifier.selectable(selected = !before, role = Role.RadioButton) { before = false }, verticalAlignment = Alignment.CenterVertically) { RadioButton(!before, null); Text("after") }
                    }
                    Text("Use 0 for \"at the event\".", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = minutes.toIntOrNull() ?: 0
                    scope.launch {
                        c.database.reminders().insert(ReminderEntity(event = event, offsetMinutes = if (before) -m else m))
                        c.reminderScheduler.rescheduleAll()
                    }
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("About") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg)) {
            Text("${BuildConfig.BRAND_NAME} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            SectionHeader("Accuracy")
            Text(
                "Sunrise, sunset, twilight and solar position are calculated on the device with the NOAA Solar Calculator equations " +
                    "(after Meeus, Astronomical Algorithms). Events are found by searching the Sun's elevation across the location's local day " +
                    "and refining each crossing to under a second, which handles daylight-saving changes, the date line and polar day/night. " +
                    "In automated tests against the JPL DE421 ephemeris, sunrise and sunset agree to within 15 seconds. Real-world times can " +
                    "differ by a minute or two because atmospheric refraction varies with weather; the standard −0.833° horizon is used.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SectionHeader("Privacy")
            Text(
                "No account and no analytics. Locations, widget designs and settings are stored only on this device. Location is requested " +
                    "only when you tap a location button, in the foreground; background location is never requested. Online city search sends " +
                    "just the text you type to Open-Meteo's geocoding service.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SectionHeader("Data & licenses")
            Text(
                "City database: GeoNames (CC BY 4.0). Time zones: IANA tz database via the Android system. Fonts: Inter, Nunito, Poppins, " +
                    "Space Grotesk, Playfair Display, Cormorant Garamond, JetBrains Mono, Orbitron, Oswald, Bebas Neue and Quicksand — all under the " +
                    "SIL Open Font License 1.1 (license texts are bundled in the app).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
