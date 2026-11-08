package app.suncanvas.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.suncanvas.location.LocationResult
import app.suncanvas.ui.AppViewModel

/**
 * Returns a callback that refreshes the device location, first explaining why location is needed
 * and requesting foreground permission (approximate is enough). Background location is never requested.
 */
@Composable
fun rememberLocationRefresher(vm: AppViewModel, onResult: (LocationResult) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) vm.refreshLocation(onResult)
        else {
            vm.message("Location permission denied — you can search for a city instead")
            onResult(LocationResult.PermissionDenied)
        }
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            icon = { Icon(Icons.Rounded.MyLocation, contentDescription = null) },
            title = { Text("Use your location?") },
            text = {
                Text(
                    "SunCanvas uses your location once, when you ask, to calculate sunrise and sunset where you are. " +
                        "Approximate location is enough. It is never tracked in the background, and your coordinates are " +
                        "stored only on this phone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } },
        )
    }
    return {
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) vm.refreshLocation(onResult) else showRationale = true
    }
}
