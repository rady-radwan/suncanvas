package app.suncanvas.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LocationResult {
    data class Success(val latitude: Double, val longitude: Double, val accuracyMeters: Float?) : LocationResult
    data object PermissionDenied : LocationResult
    data object Disabled : LocationResult
    data object Unavailable : LocationResult
}

/**
 * One-shot, foreground-only location. Prefers a recent cached fix (no radio use at all),
 * then a single balanced-power request, which works with approximate (coarse) permission.
 * Never requests background location and never tracks continuously.
 */
class DeviceLocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
    }

    private fun playServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(maxCacheAgeMillis: Long = 10 * 60_000L): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        if (!isLocationEnabled()) return LocationResult.Disabled

        val location: Location? = if (playServicesAvailable()) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cached = runCatching { client.lastLocation.await() }.getOrNull()
            if (cached != null && System.currentTimeMillis() - cached.time < maxCacheAgeMillis) {
                cached
            } else {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(maxCacheAgeMillis)
                    .setDurationMillis(20_000)
                    .build()
                val cts = CancellationTokenSource()
                withTimeoutOrNull(25_000) {
                    runCatching { client.getCurrentLocation(request, cts.token).await() }.getOrNull()
                }.also { if (it == null) cts.cancel() } ?: cached
            }
        } else {
            frameworkLastKnown()
        }
        return location?.let { LocationResult.Success(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) }
            ?: LocationResult.Unavailable
    }

    @SuppressLint("MissingPermission")
    private fun frameworkLastKnown(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
