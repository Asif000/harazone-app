package com.areadiscovery.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.areadiscovery.util.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidLocationProvider(private val context: Context) : LocationProvider {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @Throws(SecurityException::class)
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> =
        suspendCancellableCoroutine { cont ->
            try {
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            cont.resume(Result.success(GpsCoordinates(location.latitude, location.longitude)))
                        } else {
                            cont.resume(Result.failure(Exception("Location unavailable — null result from FusedLocationProvider")))
                        }
                    }
                    .addOnFailureListener { e ->
                        AppLogger.e(e) { "FusedLocationProvider failed" }
                        cont.resume(Result.failure(e))
                    }
            } catch (e: SecurityException) {
                AppLogger.e(e) { "Location permission not granted" }
                cont.resume(Result.failure(e))
            }
        }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        try {
            if (!Geocoder.isPresent()) {
                return Result.failure(Exception("Geocoder service unavailable on this device"))
            }
            val geocoder = Geocoder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        cont.resume(extractAreaName(addresses))
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    extractAreaName(addresses ?: emptyList())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun extractAreaName(addresses: List<android.location.Address>): Result<String> {
        val address = addresses.firstOrNull()
            ?: return Result.failure(Exception("Geocoding returned no addresses"))
        val name = listOfNotNull(
            address.subLocality,
            address.locality,
            address.adminArea
        ).joinToString(", ")
        return if (name.isNotBlank()) Result.success(name)
        else Result.failure(Exception("Geocoding returned address with no locality data"))
    }
}
