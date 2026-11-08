package app.suncanvas.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.EditLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.BuildConfig
import app.suncanvas.R
import app.suncanvas.location.LocationResult
import app.suncanvas.ui.AppViewModel
import app.suncanvas.ui.components.rememberLocationRefresher
import app.suncanvas.ui.theme.LocalSunColors
import app.suncanvas.ui.theme.Spacing

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit, onSearch: () -> Unit, onCoordinates: () -> Unit) {
    val locating by vm.locating.collectAsStateWithLifecycle()
    val detect = rememberLocationRefresher(vm) { result -> if (result is LocationResult.Success) onDone() }
    val sun = LocalSunColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(sun.night, MaterialTheme.colorScheme.background)))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl))
        Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(Spacing.lg))
        Text("Welcome to ${BuildConfig.BRAND_NAME}", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Accurate sunrise, sunset and daylight for any place on Earth — calculated on your phone, even offline.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxl))
        Text("How should we find your sky?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Option(Icons.Rounded.MyLocation, "Detect my current location", "One-time, approximate location is enough", loading = locating, onClick = detect)
            Option(Icons.Rounded.Search, "Search for a city", "34,000 cities offline, more online", onClick = onSearch)
            Option(Icons.Rounded.EditLocation, "Enter coordinates", "Latitude and longitude, any place", onClick = onCoordinates)
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            "No account. Your locations stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String, loading: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}
